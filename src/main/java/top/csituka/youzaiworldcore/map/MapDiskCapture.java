package top.csituka.youzaiworldcore.map;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.chunk.PalettedContainerFactory;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.chunk.storage.SerializableChunkData;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.storage.LevelResource;
import top.csituka.youzaiworldcore.util.DebugLogger;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;

/** 区块文件扫描与保存事件驱动的后台采集；从不加载、生成游戏区块或接收客户端地形。 */
public final class MapDiskCapture implements AutoCloseable {
    private record Dimension(String id, Path directory, ChunkMap storage, LevelHeightAccessor height,
                             PalettedContainerFactory factory, boolean ceiling, boolean sky) { }
    private record Position(String dimension, int x, int z) { }
    private record Region(Dimension dimension, Path file, int x, int z) { }
    private record Candidate(Position position, String stamp) { }
    private static final Pattern REGION = Pattern.compile("r\\.(-?\\d+)\\.(-?\\d+)\\.mca");
    private final Map<String, Dimension> dimensions = new LinkedHashMap<>();
    private final MapTerrainStore terrain;
    private final ScheduledExecutorService worker = Executors.newSingleThreadScheduledExecutor(Thread.ofPlatform().daemon().name("yzwc-map-capture").factory());
    private final ConcurrentHashMap<Position, Long> dirty = new ConcurrentHashMap<>();
    private final Set<MapTileKey> requested = ConcurrentHashMap.newKeySet();
    private final AtomicLong generation = new AtomicLong();
    private Iterator<Region> regions = Collections.emptyIterator();
    private Iterator<Candidate> candidates = Collections.emptyIterator();
    private long nextScan;
    private volatile boolean closed;
    private volatile boolean enabled, underground;
    private volatile int chunksPerPass, scanInterval;

    public MapDiskCapture(MinecraftServer server, MapTerrainStore terrain) {
        this.terrain = terrain;
        for (var level : server.getAllLevels()) {
            String id = level.dimension().identifier().toString();
            if (id.equals("youzaiworldcore:login_hall")) continue;
            dimensions.put(id, new Dimension(id, DimensionType.getStorageFolder(level.dimension(), server.getWorldPath(LevelResource.ROOT)).resolve("region"),
                    level.getChunkSource().chunkMap, LevelHeightAccessor.create(level.getMinY(), level.getHeight()),
                    level.palettedContainerFactory(), level.dimensionType().hasCeiling(), level.dimensionType().hasSkyLight()));
        }
        configure();
        worker.scheduleWithFixedDelay(this::run, 1, 1, TimeUnit.SECONDS);
        DebugLogger.info("MapDiskCapture", "已启动区块文件采集，维度=%d", dimensions.size());
    }
    /** 主线程重载时发布配置快照，不从工作线程访问游戏配置或世界。 */
    public void configure() {
        enabled = MapServerSettings.enabled && (MapServerSettings.shareTerrain || MapServerSettings.apiUpload);
        underground = MapServerSettings.captureUnderground;
        chunksPerPass = MapServerSettings.chunksPerPass; scanInterval = MapServerSettings.scanIntervalSeconds;
    }
    /** 保存通知按坐标合并版本，不丢弃同一秒内再次保存的修改。 */
    public void dirty(String dimension, int x, int z) {
        if (!closed && enabled && dimensions.containsKey(dimension)) dirty.put(new Position(dimension, x, z), generation.incrementAndGet());
    }
    /** 非标准高度按需从现有区块文件补绘，同样不会创建加载票。 */
    public void request(MapTileKey key) { if (!closed && enabled && requested.size() < 1024 && dimensions.containsKey(key.dimension())) requested.add(key); }
    private void run() {
        if (closed || !enabled) return;
        try {
            int budget = chunksPerPass;
            for (var entry : dirty.entrySet()) {
                if (budget <= chunksPerPass / 2 || closed) break;
                var p = entry.getKey();
                if (capture(p, "saved:" + entry.getValue(), null)) dirty.remove(p, entry.getValue());
                budget--;
            }
            for (var key : requested) {
                if (budget <= chunksPerPass / 4 || closed) break;
                if (capture(new Position(key.dimension(), key.chunkX(), key.chunkZ()), null, key)) requested.remove(key);
                budget--;
            }
            if (!regions.hasNext() && !candidates.hasNext() && System.currentTimeMillis() >= nextScan) beginScan();
            int inspected = 0;
            while (budget > 0 && !closed && inspected++ < chunksPerPass * 32) {
                Candidate candidate = next();
                if (candidate == null) break;
                var p = candidate.position();
                String stamp = candidate.stamp() + (underground ? ":underground" : ":surface");
                if (stamp.equals(terrain.sourceStamp(p.dimension(), p.x(), p.z()))) continue;
                capture(p, stamp, null); budget--;
            }
        } catch (Exception error) { if (!closed) DebugLogger.exception("MapDiskCapture", "采集区块文件，下轮继续重试", error); }
    }
    private void beginScan() throws IOException {
        var found = new ArrayList<Region>();
        for (var dimension : dimensions.values()) {
            if (!Files.isDirectory(dimension.directory())) continue;
            try (var files = Files.list(dimension.directory())) {
                for (Path path : files.sorted().toList()) {
                    var match = REGION.matcher(path.getFileName().toString());
                    if (match.matches() && Files.isRegularFile(path)) found.add(new Region(dimension, path,
                            Integer.parseInt(match.group(1)), Integer.parseInt(match.group(2))));
                }
            }
        }
        regions = found.iterator(); nextScan = System.currentTimeMillis() + scanInterval * 1000L;
    }
    private Candidate next() throws IOException {
        while (!candidates.hasNext()) {
            if (!regions.hasNext()) return null;
            var region = regions.next();
            // 只读 8KiB 位置/时间戳表；内容由 Minecraft 自己的区块 IO 队列读取，避免与写区块竞态。
            ByteBuffer header = ByteBuffer.allocate(8192);
            try (var file = FileChannel.open(region.file(), StandardOpenOption.READ)) {
                while (header.hasRemaining()) if (file.read(header) < 0) throw new IOException("区域文件头不完整：" + region.file());
            }
            var entries = new ArrayList<Candidate>();
            for (int i = 0; i < 1024; i++) {
                int offset = header.getInt(i * 4); if (offset == 0) continue;
                long x = (long) region.x() * 32 + i % 32, z = (long) region.z() * 32 + i / 32;
                if (Math.abs(x) > MapTileKey.CHUNK_LIMIT || Math.abs(z) > MapTileKey.CHUNK_LIMIT) continue;
                entries.add(new Candidate(new Position(region.dimension().id(), (int) x, (int) z),
                        Integer.toUnsignedString(header.getInt(4096 + i * 4)) + ":" + Integer.toUnsignedString(offset)));
            }
            candidates = entries.iterator();
        }
        return candidates.next();
    }
    private boolean capture(Position position, String stamp, MapTileKey requestedKey) throws Exception {
        var dimension = dimensions.get(position.dimension());
        if (dimension == null || Math.abs((long) position.x()) > MapTileKey.CHUNK_LIMIT || Math.abs((long) position.z()) > MapTileKey.CHUNK_LIMIT) return true;
        // RegionFileStorage 的读取也可能创建文件；浏览请求必须先确认文件已有此区块。
        // 保存事件可以读取 IO 队列中的待写快照，不受此检查限制。
        if (requestedKey != null && !stored(dimension, position)) return true;
        // SimpleRegionStorage.read 只返回磁盘或正在保存的 NBT，不进行区块加载/生成。
        var saved = dimension.storage().read(new ChunkPos(position.x(), position.z())).get(30, TimeUnit.SECONDS);
        if (saved.isEmpty() || closed) return true;
        var data = SerializableChunkData.parse(dimension.height(), dimension.factory(), saved.get());
        if (data == null || data.chunkStatus() != ChunkStatus.FULL) return true;
        if (data.chunkPos().x() != position.x() || data.chunkPos().z() != position.z()) throw new IOException("区块文件坐标不一致");
        var source = new MapSavedChunk(data, dimension.height().getMinY(), dimension.height().getHeight(), dimension.ceiling(), dimension.sky());
        var tiles = new ArrayList<MapTile>();
        if (requestedKey != null) sample(source, requestedKey, tiles);
        else {
            sample(source, new MapTileKey(position.dimension(), MapLayer.SURFACE, 0, position.x(), position.z()), tiles);
            sample(source, new MapTileKey(position.dimension(), MapLayer.ROOF, 0, position.x(), position.z()), tiles);
            var slices = new LinkedHashSet<>(terrain.savedSlices(position.dimension(), position.x(), position.z()));
            if (underground) for (int y = source.getMinY(); y <= source.getMaxY(); y += 8) {
                int height = Math.min(y + 7, source.getMaxY());
                for (var layer : List.of(MapLayer.FIXED, MapLayer.CAVE)) slices.add(new MapTileKey(position.dimension(), layer, height, position.x(), position.z()));
            }
            // 任意高度切片一旦被查看并保存，后续区块修改也必须更新它。
            for (var key : slices) if (key.height() >= source.getMinY() && key.height() <= source.getMaxY()) sample(source, key, tiles);
        }
        if (closed) return false;
        String sourceStamp = stamp == null ? terrain.sourceStamp(position.dimension(), position.x(), position.z()) : stamp;
        terrain.putChunk(position.dimension(), position.x(), position.z(), sourceStamp, tiles);
        return true;
    }
    private boolean stored(Dimension dimension, Position position) throws IOException {
        Path path = dimension.directory().resolve("r." + Math.floorDiv(position.x(), 32) + "." + Math.floorDiv(position.z(), 32) + ".mca");
        if (!Files.isRegularFile(path)) return false;
        int index = Math.floorMod(position.x(), 32) + Math.floorMod(position.z(), 32) * 32;
        ByteBuffer offset = ByteBuffer.allocate(4);
        try (var file = FileChannel.open(path, StandardOpenOption.READ)) {
            file.position(index * 4L);
            while (offset.hasRemaining()) if (file.read(offset) < 0) return false;
        }
        return offset.getInt(0) != 0;
    }
    private void sample(MapSavedChunk source, MapTileKey key, List<MapTile> tiles) throws InterruptedException {
        if (closed || Thread.currentThread().isInterrupted()) throw new InterruptedException();
        var job = MapSampler.startSaved(source, key); job.step(256, Long.MAX_VALUE); tiles.add(job.finish());
    }
    @Override public void close() {
        closed = true; worker.shutdownNow();
        try { if (!worker.awaitTermination(30, TimeUnit.SECONDS)) throw new IllegalStateException("地图区块采集线程未结束"); }
        catch (InterruptedException error) { Thread.currentThread().interrupt(); throw new IllegalStateException(error); }
    }
}
