package top.csituka.youzaiworldcore.client.map;

import net.minecraft.client.Minecraft;
import top.csituka.youzaiworldcore.client.config.MapSettings;
import top.csituka.youzaiworldcore.map.*;
import top.csituka.youzaiworldcore.util.DebugLogger;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import it.unimi.dsi.fastutil.longs.*;

/** 客户端地形按存档存入 SQLite；不按容量删除任何已收到或采样的瓦片。 */
public final class MapTileCache {
    private record Layer(String dimension, MapLayer layer, int height) { }
    private record Saved(String world, MapTileKey key) { }
    private final Map<MapTileKey, MapTile> tiles = new HashMap<>();
    private final Map<Layer, Long2ObjectMap<MapTile>> layers = new HashMap<>();
    private final ExecutorService io = Executors.newSingleThreadExecutor(Thread.ofPlatform().daemon().name("yzwc-client-map-db").factory());
    private final ConcurrentHashMap<Saved, MapTile> dirty = new ConcurrentHashMap<>();
    private final AtomicBoolean writing = new AtomicBoolean();
    private volatile RuntimeException failure;
    private volatile long epoch;
    private String world = "";
    private long revision;

    public static long position(int x, int z) { return (x & 0xFFFFFFFFL) | ((long) z << 32); }
    public void put(MapTile tile) {
        checkFailure();
        if (tile.sameContents(tiles.get(tile.key()))) return;
        remember(tile);
        if (!world.isEmpty()) { dirty.put(new Saved(world, tile.key()), tile); flush(); }
    }
    private void remember(MapTile tile) {
        tiles.put(tile.key(), tile);
        layers.computeIfAbsent(layer(tile.key()), ignored -> new Long2ObjectOpenHashMap<>())
                .put(position(tile.key().chunkX(), tile.key().chunkZ()), tile);
        revision++;
    }
    /** 只切换内存视图，旧世界数据仍保留在数据库。后台回调按代号隔离。 */
    public void selectWorld(String identity) {
        clear(); world = identity;
        if (world.isEmpty()) return;
        long ticket = epoch;
        var database = MapSettings.database();
        io.execute(() -> {
            try {
                long after = 0;
                while (epoch == ticket) {
                    long cursor = after;
                    var batch = new ArrayList<MapTile>();
                    long next = database.read(db -> {
                        long last = cursor;
                        try (var sql = db.prepareStatement("SELECT rowid,dimension,layer,height,x,z,revision,data FROM client_tiles WHERE world=? AND rowid>? ORDER BY rowid LIMIT 256")) {
                            sql.setString(1, identity); sql.setLong(2, cursor);
                            try (var rows = sql.executeQuery()) {
                                while (rows.next()) {
                                    last = rows.getLong(1);
                                    var key = new MapTileKey(rows.getString(2), MapLayer.valueOf(rows.getString(3)), rows.getInt(4), rows.getInt(5), rows.getInt(6));
                                    batch.add(MapBinaryCodec.tile(key, rows.getLong(7), rows.getBytes(8)));
                                }
                            }
                        }
                        return last;
                    });
                    if (batch.isEmpty()) break;
                    Minecraft.getInstance().execute(() -> {
                        if (epoch == ticket) for (var tile : batch) if (!tiles.containsKey(tile.key())) remember(tile);
                    });
                    after = next;
                }
            } catch (RuntimeException error) { fail(error); }
        });
    }
    private void flush() {
        if (dirty.isEmpty() || !writing.compareAndSet(false, true)) return;
        var database = MapSettings.database();
        io.execute(() -> {
            try {
                while (!dirty.isEmpty()) {
                    var batch = dirty.entrySet().stream().limit(128).map(e -> Map.entry(e.getKey(), e.getValue())).toList();
                    database.transaction(db -> {
                        try (var sql = db.prepareStatement("INSERT INTO client_tiles VALUES(?,?,?,?,?,?,?,?) ON CONFLICT(world,dimension,layer,height,x,z) DO UPDATE SET revision=excluded.revision,data=excluded.data")) {
                            for (var entry : batch) {
                                var key = entry.getKey(); var tile = entry.getValue();
                                sql.setString(1, key.world()); sql.setString(2, key.key().dimension()); sql.setString(3, key.key().layer().name());
                                sql.setInt(4, key.key().height()); sql.setInt(5, key.key().chunkX()); sql.setInt(6, key.key().chunkZ());
                                sql.setLong(7, tile.revision()); sql.setBytes(8, MapBinaryCodec.tile(tile)); sql.addBatch();
                            }
                            sql.executeBatch();
                        }
                        return null;
                    });
                    batch.forEach(entry -> dirty.remove(entry.getKey(), entry.getValue()));
                }
            } catch (RuntimeException error) { fail(error); }
            finally { writing.set(false); if (failure == null && !dirty.isEmpty()) flush(); }
        });
    }
    public Long2ObjectMap<MapTile> view(String dimension, MapLayer layer, int height) {
        return layers.getOrDefault(new Layer(dimension, layer, layer.hasHeight() ? height : 0), Long2ObjectMaps.emptyMap());
    }
    public Long2ObjectMap<MapTile> snapshot(String dimension, MapLayer layer, int height) {
        return Long2ObjectMaps.unmodifiable(new Long2ObjectOpenHashMap<>(view(dimension, layer, height)));
    }
    public MapTile get(MapTileKey key) { return tiles.get(key); }
    public int size() { return tiles.size(); }
    public long revision() { return revision; }
    public void clear() { epoch++; tiles.clear(); layers.clear(); revision++; }
    public void checkFailure() { if (failure != null) throw failure; }
    private void fail(RuntimeException error) { failure = error; DebugLogger.exception("MapTileCache", "读写客户端地图 SQLite", error); }
    /** 退出游戏时等待已接收瓦片落盘；断线不停止存储线程。 */
    public void close() {
        flush(); io.shutdown();
        try { if (!io.awaitTermination(30, TimeUnit.SECONDS)) throw new IllegalStateException("地图 SQLite 写入未完成"); }
        catch (InterruptedException error) { Thread.currentThread().interrupt(); throw new IllegalStateException(error); }
        checkFailure();
    }
    private static Layer layer(MapTileKey key) { return new Layer(key.dimension(), key.layer(), key.height()); }
}
