package top.csituka.youzaiworldcore.map;

import top.csituka.youzaiworldcore.util.DebugLogger;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;

/** 世界地形 SQLite；区块数据与处理游标在同一事务提交，上传按单调修订号读取。 */
public final class MapTerrainStore implements AutoCloseable {
    private final MapDatabase database;
    private final ExecutorService io = Executors.newSingleThreadExecutor(Thread.ofPlatform().daemon().name("yzwc-map-read").factory());
    private final LinkedHashMap<MapTileKey, MapTile> cache = new LinkedHashMap<>(128, .75f, true);
    private final Map<MapTileKey, Long> missing = new HashMap<>();
    private final Set<MapTileKey> interested = ConcurrentHashMap.newKeySet();
    private final Set<MapTileKey> reading = ConcurrentHashMap.newKeySet();
    private final ConcurrentHashMap<MapTileKey, ReadResult> completed = new ConcurrentHashMap<>();
    private volatile boolean closed;
    private record ReadResult(MapTileKey key, MapTile tile) { }
    public MapTerrainStore(MapDatabase database) { this.database = database; }
    public MapDatabase database() { return database; }
    public void tick() {
        for (var entry : completed.entrySet()) {
            if (!completed.remove(entry.getKey(), entry.getValue())) continue;
            var result = entry.getValue();
            interested.add(result.key());
            if (result.tile() == null) { if (!cache.containsKey(result.key())) missing.put(result.key(), System.currentTimeMillis() + 10000); }
            else {
                missing.remove(result.key());
                var old = cache.get(result.key());
                if (old == null || old.revision() <= result.tile().revision()) cache.put(result.key(), result.tile());
            }
        }
        while (cache.size() > MapServerSettings.cacheTiles) { var key = cache.keySet().iterator().next(); cache.remove(key); interested.remove(key); }
        if (missing.size() > MapServerSettings.cacheTiles) { missing.keySet().forEach(interested::remove); missing.clear(); }
        missing.entrySet().removeIf(entry -> {
            if (entry.getValue() > System.currentTimeMillis()) return false;
            interested.remove(entry.getKey()); return true;
        });
    }
    /** Tick 不等待磁盘，服务端缓存上限不删除 SQLite 地形。 */
    public MapTile get(MapTileKey key) {
        MapTile value = cache.get(key);
        if (value != null || closed || reading.size() >= 32 || missing.getOrDefault(key, 0L) > System.currentTimeMillis()) return value;
        interested.add(key);
        if (reading.add(key)) io.execute(() -> {
            MapTile tile = null;
            try {
                tile = database.read(db -> {
                    try (var sql = db.prepareStatement("SELECT revision,data FROM tiles WHERE dimension=? AND layer=? AND height=? AND x=? AND z=?")) {
                        bind(sql, key, 1);
                        try (var rows = sql.executeQuery()) { return rows.next() ? MapBinaryCodec.tile(key, rows.getLong(1), rows.getBytes(2)) : null; }
                    }
                });
            } catch (RuntimeException error) { DebugLogger.exception("MapTerrainStore", "读取地图 SQLite", error); }
            complete(new ReadResult(key, tile));
            reading.remove(key);
        });
        return null;
    }
    private void complete(ReadResult result) {
        completed.merge(result.key(), result, (old, next) -> old.tile() != null
                && (next.tile() == null || old.tile().revision() > next.tile().revision()) ? old : next);
    }
    public List<MapTileKey> savedSlices(String dimension, int x, int z) {
        return database.read(db -> {
            var keys = new ArrayList<MapTileKey>();
            try (var sql = db.prepareStatement("SELECT layer,height FROM tiles WHERE dimension=? AND x=? AND z=? AND layer IN ('CAVE','FIXED')")) {
                sql.setString(1, dimension); sql.setInt(2, x); sql.setInt(3, z);
                try (var rows = sql.executeQuery()) { while (rows.next()) keys.add(new MapTileKey(dimension, MapLayer.valueOf(rows.getString(1)), rows.getInt(2), x, z)); }
            }
            return keys;
        });
    }
    public String sourceStamp(String dimension, int x, int z) {
        return database.read(db -> {
            try (var sql = db.prepareStatement("SELECT stamp FROM source_chunks WHERE dimension=? AND x=? AND z=?")) {
                sql.setString(1, dimension); sql.setInt(2, x); sql.setInt(3, z);
                try (var rows = sql.executeQuery()) { return rows.next() ? rows.getString(1) : ""; }
            }
        });
    }
    /** 仅由磁盘区块采集线程调用；失败不推进区块处理标记。 */
    public void putChunk(String dimension, int x, int z, String stamp, List<MapTile> tiles) {
        var changed = database.transaction(db -> {
            var result = new ArrayList<MapTile>();
            long revision;
            try (var sql = db.createStatement(); var rows = sql.executeQuery("SELECT value FROM sequence WHERE id=1")) { rows.next(); revision = rows.getLong(1); }
            try (var query = db.prepareStatement("SELECT data FROM tiles WHERE dimension=? AND layer=? AND height=? AND x=? AND z=?");
                 var insert = db.prepareStatement("INSERT INTO tiles VALUES(?,?,?,?,?,?,?) ON CONFLICT(dimension,layer,height,x,z) DO UPDATE SET revision=excluded.revision,data=excluded.data")) {
                for (var tile : tiles) {
                    byte[] data = MapBinaryCodec.tile(tile); bind(query, tile.key(), 1);
                    try (var rows = query.executeQuery()) { if (rows.next() && Arrays.equals(data, rows.getBytes(1))) continue; }
                    bind(insert, tile.key(), 1); insert.setLong(6, ++revision); insert.setBytes(7, data); insert.executeUpdate();
                    result.add(new MapTile(tile.key(), revision, tile.colors(), tile.heights(), tile.lights(), tile.biomeIndices(), tile.biomes(), tile.biomeColors()));
                }
            }
            try (var sql = db.prepareStatement("UPDATE sequence SET value=? WHERE id=1")) { sql.setLong(1, revision); sql.executeUpdate(); }
            try (var sql = db.prepareStatement("INSERT INTO source_chunks VALUES(?,?,?,?) ON CONFLICT(dimension,x,z) DO UPDATE SET stamp=excluded.stamp")) {
                sql.setString(1, dimension); sql.setInt(2, x); sql.setInt(3, z); sql.setString(4, stamp); sql.executeUpdate();
            }
            return result;
        });
        changed.forEach(tile -> { if (interested.contains(tile.key())) complete(new ReadResult(tile.key(), tile)); });
    }
    public long revision() {
        return database.read(db -> { try (var sql = db.createStatement(); var rows = sql.executeQuery("SELECT value FROM sequence WHERE id=1")) { rows.next(); return rows.getLong(1); } });
    }
    /** 有界增量读取；上传期间新写入的修订号留给下次同步，不依赖文件时间戳。 */
    public void visitSaved(long since, long through, java.util.function.Consumer<MapTile> visitor) throws java.io.IOException {
        try (var db = DriverManager.getConnection("jdbc:sqlite:" + database.path())) {
            try (var sql = db.createStatement()) { sql.execute("PRAGMA busy_timeout=10000"); sql.execute("PRAGMA query_only=ON"); }
            long cursor = since;
            while (!closed) {
                var batch = new ArrayList<MapTile>();
                try (var sql = db.prepareStatement("SELECT dimension,layer,height,x,z,revision,data FROM tiles WHERE revision>? AND revision<=? ORDER BY revision LIMIT 128")) {
                    sql.setLong(1, cursor); sql.setLong(2, through);
                    try (var rows = sql.executeQuery()) { while (rows.next()) {
                        var key = new MapTileKey(rows.getString(1), MapLayer.valueOf(rows.getString(2)), rows.getInt(3), rows.getInt(4), rows.getInt(5));
                        batch.add(MapBinaryCodec.tile(key, rows.getLong(6), rows.getBytes(7)));
                    } }
                }
                if (batch.isEmpty()) return;
                for (var tile : batch) { if (closed || Thread.currentThread().isInterrupted()) throw new java.io.IOException("地图服务已停止"); visitor.accept(tile); cursor = tile.revision(); }
            }
            throw new java.io.IOException("地图服务已停止");
        } catch (SQLException error) { throw new java.io.IOException("读取地图上传修订失败", error); }
    }
    private static void bind(PreparedStatement sql, MapTileKey key, int index) throws SQLException {
        sql.setString(index, key.dimension()); sql.setString(index + 1, key.layer().name()); sql.setInt(index + 2, key.height());
        sql.setInt(index + 3, key.chunkX()); sql.setInt(index + 4, key.chunkZ());
    }
    @Override public void close() {
        closed = true; io.shutdown();
        try { if (!io.awaitTermination(30, TimeUnit.SECONDS)) throw new IllegalStateException("地图读取线程未结束"); }
        catch (InterruptedException error) { Thread.currentThread().interrupt(); throw new IllegalStateException(error); }
    }
}
