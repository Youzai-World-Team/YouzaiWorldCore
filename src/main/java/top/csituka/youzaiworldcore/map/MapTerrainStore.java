package top.csituka.youzaiworldcore.map;

import net.minecraft.server.MinecraftServer;
import top.csituka.youzaiworldcore.config.GlobalSettings;
import top.csituka.youzaiworldcore.config.ModPaths;
import top.csituka.youzaiworldcore.config.TempManager;
import top.csituka.youzaiworldcore.util.DebugLogger;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * 随存档保存的真实地形库。文件位于 ModPaths.worldData(map_module)/tiles；
 * 瓦片压缩、磁盘读写均在单独线程执行，世界对象永不跨线程读取。
 */
public final class MapTerrainStore implements AutoCloseable {
    private static final int MAGIC = 0x595A4D31;
    private final Path directory, temporary;
    private final ExecutorService io = Executors.newSingleThreadExecutor(Thread.ofPlatform().daemon().name("yzwc-map-io").factory());
    private final LinkedHashMap<MapTileKey, MapTile> cache = new LinkedHashMap<>(128, 0.75f, true);
    private final LinkedHashMap<MapTileKey, Long> missing = new LinkedHashMap<>();
    private final Set<MapTileKey> reading = ConcurrentHashMap.newKeySet();
    private final ConcurrentLinkedQueue<ReadResult> completed = new ConcurrentLinkedQueue<>();
    private final ConcurrentHashMap<MapTileKey, MapTile> dirty = new ConcurrentHashMap<>();
    private final AtomicBoolean writing = new AtomicBoolean();
    private volatile boolean closed;
    private volatile long nextWriteAttempt;
    private record ReadResult(MapTileKey key, MapTile tile) { }

    public MapTerrainStore(MinecraftServer server) {
        directory = ModPaths.ensureDir(ModPaths.worldData(server, GlobalSettings.MAP_MODULE).resolve("tiles"));
        temporary = TempManager.worldTempDir(server, GlobalSettings.MAP_MODULE);
    }

    /** 主线程吸收已完成的后台读取。刚采样的新数据优先于旧磁盘快照。 */
    public void tick() {
        ReadResult result;
        while ((result = completed.poll()) != null) {
            reading.remove(result.key());
            if (result.tile() != null) {
                if (!cache.containsKey(result.key())) remember(dirty.getOrDefault(result.key(), result.tile()));
            } else if (!cache.containsKey(result.key()) && !dirty.containsKey(result.key())) {
                missing.put(result.key(), System.currentTimeMillis() + 30000);
            }
        }
        while (missing.size() > MapServerSettings.cacheTiles) missing.remove(missing.keySet().iterator().next());
        if (!dirty.isEmpty() && !closed && System.currentTimeMillis() >= nextWriteAttempt && writing.compareAndSet(false, true)) io.execute(this::drainWrites);
    }

    /** 非阻塞取瓦片，不在服务器 Tick 内等待磁盘。 */
    public MapTile get(MapTileKey key) {
        MapTile result = cache.get(key);
        if (result == null) { result = dirty.get(key); if (result != null) remember(result); }
        if (result != null || closed || reading.size() >= 32) return result;
        if (missing.getOrDefault(key, 0L) > System.currentTimeMillis()) return null;
        if (reading.add(key)) io.execute(() -> {
            MapTile tile = null;
            try {
                tile = read(path(key));
                if (!tile.key().equals(key)) throw new IOException("地形文件坐标与文件名不匹配");
            } catch (NoSuchFileException ignored) {
                // 尚未探索的区域保持空白，不为其生成区块。
            } catch (Exception error) {
                tile = null;
                DebugLogger.exception("MapTerrainStore", "读取地形瓦片 " + key, error);
            }
            completed.add(new ReadResult(key, tile));
        });
        return null;
    }

    /** 新采样的快照进入缓存与合并写队列，同一个坐标只保留最新待写版本。 */
    public void put(MapTile tile) {
        if (closed || tile.sameContents(cache.get(tile.key()))) return;
        remember(tile);
        missing.remove(tile.key());
        dirty.put(tile.key(), tile);
    }

    /** 磁盘积压时暂停采样，保留尚未写出的地形并给队列设置硬上限。 */
    public boolean acceptsSamples() { return !closed && dirty.size() < 2048; }

    private void remember(MapTile tile) {
        cache.put(tile.key(), tile);
        while (cache.size() > MapServerSettings.cacheTiles) cache.remove(cache.keySet().iterator().next());
    }

    private void drainWrites() {
        try {
            // 一批最多 64 个文件，留出队列时间处理浏览旧区域的读请求。
            int count = 0;
            for (var entry : dirty.entrySet()) {
                if (count++ >= 64 && !closed) break;
                if (!dirty.remove(entry.getKey(), entry.getValue())) continue;
                try { write(entry.getValue()); }
                catch (Exception error) {
                    dirty.putIfAbsent(entry.getKey(), entry.getValue());
                    nextWriteAttempt = System.currentTimeMillis() + 5000;
                    DebugLogger.exception("MapTerrainStore", "保存地形瓦片", error);
                    break;
                }
            }
        } finally { writing.set(false); }
    }

    private Path path(MapTileKey key) {
        return directory.resolve(dimensionToken(key.dimension())).resolve(key.layer().name().toLowerCase(java.util.Locale.ROOT))
                .resolve(Integer.toString(key.height())).resolve(key.chunkX() + "_" + key.chunkZ() + ".gz");
    }

    /** 标识只影响文件名，不能被网络输入解释为相对路径。 */
    public static String dimensionToken(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))).substring(0, 24);
        } catch (NoSuchAlgorithmException error) { throw new IllegalStateException(error); }
    }

    private void write(MapTile tile) throws IOException {
        Path target = path(tile.key());
        Files.createDirectories(target.getParent());
        Path staging = temporary.resolve("tile-" + UUID.randomUUID() + ".tmp");
        try {
            try (var out = new DataOutputStream(new BufferedOutputStream(new GZIPOutputStream(Files.newOutputStream(staging))))) {
                var key = tile.key();
                out.writeInt(MAGIC); out.writeUTF(key.dimension()); out.writeUTF(key.layer().name());
                out.writeInt(key.height()); out.writeInt(key.chunkX()); out.writeInt(key.chunkZ()); out.writeLong(tile.revision());
                out.writeShort(tile.biomes().size());
                for (int i = 0; i < tile.biomes().size(); i++) { out.writeUTF(tile.biomes().get(i)); out.writeInt(tile.biomeColors()[i]); }
                for (int color : tile.colors()) out.writeInt(color);
                for (short height : tile.heights()) out.writeShort(height);
                out.write(tile.lights()); out.write(tile.biomeIndices());
            }
            try { Files.move(staging, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
            catch (AtomicMoveNotSupportedException ignored) { Files.move(staging, target, StandardCopyOption.REPLACE_EXISTING); }
        } finally { Files.deleteIfExists(staging); }
    }

    private static MapTile read(Path file) throws IOException {
        try (var in = new DataInputStream(new BufferedInputStream(new GZIPInputStream(Files.newInputStream(file))))) {
            if (in.readInt() != MAGIC) throw new IOException("地图文件版本无效");
            var key = new MapTileKey(in.readUTF(), MapLayer.valueOf(in.readUTF()), in.readInt(), in.readInt(), in.readInt());
            long revision = in.readLong();
            int size = in.readUnsignedShort();
            if (size < 1 || size > 256) throw new IOException("群系调色板长度无效");
            var biomes = new ArrayList<String>(size);
            int[] biomeColors = new int[size];
            for (int i = 0; i < size; i++) { biomes.add(in.readUTF()); biomeColors[i] = in.readInt(); }
            int[] colors = new int[256]; short[] heights = new short[256]; byte[] lights = new byte[256], indices = new byte[256];
            for (int i = 0; i < 256; i++) colors[i] = in.readInt();
            for (int i = 0; i < 256; i++) heights[i] = in.readShort();
            in.readFully(lights); in.readFully(indices);
            if (in.read() != -1) throw new IOException("地图文件包含多余数据");
            return new MapTile(key, revision, colors, heights, lights, indices, biomes, biomeColors);
        }
    }

    /** 后台上传线程流式读取已保存瓦片，不触碰主线程缓存或世界对象。 */
    public void visitSaved(long modifiedSince, java.util.function.Consumer<MapTile> visitor) throws IOException {
        try (var files = Files.walk(directory)) {
            var iterator = files.filter(file -> file.getFileName().toString().endsWith(".gz")
                    && Files.isRegularFile(file, java.nio.file.LinkOption.NOFOLLOW_LINKS)).iterator();
            while (iterator.hasNext()) {
                if (closed || Thread.currentThread().isInterrupted()) throw new IOException("地图服务已停止");
                Path file = iterator.next();
                if (Files.getLastModifiedTime(file).toMillis() < modifiedSince) continue;
                MapTile tile = read(file);
                if (!path(tile.key()).equals(file)) throw new IOException("地形文件坐标与保存路径不一致");
                visitor.accept(tile);
            }
        }
    }

    /** 关服时排空最后一批地形写入；此时没有正在运行的游戏 Tick。 */
    @Override public void close() {
        closed = true;
        io.execute(this::drainWrites);
        io.shutdown();
        try {
            if (!io.awaitTermination(30, TimeUnit.SECONDS) || !dirty.isEmpty()) {
                DebugLogger.warn("MapTerrainStore", "地图写盘尚未完全结束，剩余 %d 个瓦片", dirty.size());
            }
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            DebugLogger.exception("MapTerrainStore", "等待地图写盘", error);
        }
    }
}
