package top.csituka.youzaiworldcore.map;

import top.csituka.youzaiworldcore.config.ModPaths;
import top.csituka.youzaiworldcore.util.DebugLogger;
import java.nio.file.Path;
import java.sql.*;
import java.util.LinkedHashMap;
import java.util.Map;

/** 地图 SQLite 建表入口；直接建新库，不读取或转换旧 JSON、gzip 地形。 */
public final class MapDatabase implements AutoCloseable {
    @FunctionalInterface public interface Work<T> { T run(Connection connection) throws SQLException; }
    private final Path path;
    private final Connection connection;
    public MapDatabase(Path path) {
        this.path = ModPaths.ensureParentDir(path);
        try {
            Class.forName("org.sqlite.JDBC");
            connection = DriverManager.getConnection("jdbc:sqlite:" + path);
            try (var sql = connection.createStatement()) {
                sql.execute("PRAGMA busy_timeout=10000");
                sql.execute("PRAGMA journal_mode=WAL");
                sql.execute("PRAGMA synchronous=FULL");
                sql.execute("CREATE TABLE IF NOT EXISTS settings (scope TEXT NOT NULL, key TEXT NOT NULL, value TEXT NOT NULL, PRIMARY KEY(scope,key))");
                sql.execute("CREATE TABLE IF NOT EXISTS records (scope TEXT NOT NULL, kind TEXT NOT NULL, id TEXT NOT NULL, data BLOB NOT NULL, PRIMARY KEY(scope,kind,id))");
                sql.execute("CREATE TABLE IF NOT EXISTS explored (world TEXT NOT NULL, dimension TEXT NOT NULL, x INTEGER NOT NULL, z INTEGER NOT NULL, height INTEGER NOT NULL, PRIMARY KEY(world,dimension,x,z,height))");
                sql.execute("CREATE TABLE IF NOT EXISTS tiles (dimension TEXT NOT NULL, layer TEXT NOT NULL, height INTEGER NOT NULL, x INTEGER NOT NULL, z INTEGER NOT NULL, revision INTEGER NOT NULL, data BLOB NOT NULL, PRIMARY KEY(dimension,layer,height,x,z))");
                sql.execute("CREATE INDEX IF NOT EXISTS tiles_revision ON tiles(revision)");
                sql.execute("CREATE INDEX IF NOT EXISTS tiles_chunk ON tiles(dimension,x,z)");
                sql.execute("CREATE TABLE IF NOT EXISTS client_tiles (world TEXT NOT NULL, dimension TEXT NOT NULL, layer TEXT NOT NULL, height INTEGER NOT NULL, x INTEGER NOT NULL, z INTEGER NOT NULL, revision INTEGER NOT NULL, data BLOB NOT NULL, PRIMARY KEY(world,dimension,layer,height,x,z))");
                sql.execute("CREATE TABLE IF NOT EXISTS sequence (id INTEGER PRIMARY KEY CHECK(id=1), value INTEGER NOT NULL)");
                sql.execute("INSERT OR IGNORE INTO sequence VALUES(1,0)");
                sql.execute("CREATE TABLE IF NOT EXISTS source_chunks (dimension TEXT NOT NULL, x INTEGER NOT NULL, z INTEGER NOT NULL, stamp TEXT NOT NULL, PRIMARY KEY(dimension,x,z))");
            }
            DebugLogger.info("MapDatabase", "已打开地图 SQLite：%s", path);
        } catch (Exception error) { throw failure(error); }
    }
    public Path path() { return path; }
    public synchronized <T> T read(Work<T> work) {
        try { return work.run(connection); } catch (SQLException error) { throw failure(error); }
    }
    public synchronized <T> T transaction(Work<T> work) {
        try {
            connection.setAutoCommit(false);
            try { T value = work.run(connection); connection.commit(); return value; }
            catch (SQLException | RuntimeException error) { connection.rollback(); throw error; }
            finally { connection.setAutoCommit(true); }
        } catch (SQLException error) { throw failure(error); }
    }
    public Map<String, String> settings(String scope) {
        return read(db -> {
            var values = new LinkedHashMap<String, String>();
            try (var sql = db.prepareStatement("SELECT key,value FROM settings WHERE scope=?")) {
                sql.setString(1, scope);
                try (var rows = sql.executeQuery()) { while (rows.next()) values.put(rows.getString(1), rows.getString(2)); }
            }
            return values;
        });
    }
    public void settings(String scope, Map<String, String> values) {
        transaction(db -> {
            try (var sql = db.prepareStatement("INSERT INTO settings VALUES(?,?,?) ON CONFLICT(scope,key) DO UPDATE SET value=excluded.value")) {
                for (var entry : values.entrySet()) {
                    sql.setString(1, scope); sql.setString(2, entry.getKey()); sql.setString(3, entry.getValue()); sql.addBatch();
                }
                sql.executeBatch();
            }
            return null;
        });
    }
    public Map<String, byte[]> records(String scope, String kind) {
        return read(db -> {
            var values = new LinkedHashMap<String, byte[]>();
            try (var sql = db.prepareStatement("SELECT id,data FROM records WHERE scope=? AND kind=? ORDER BY id")) {
                sql.setString(1, scope); sql.setString(2, kind);
                try (var rows = sql.executeQuery()) { while (rows.next()) values.put(rows.getString(1), rows.getBytes(2)); }
            }
            return values;
        });
    }
    /** 允许多个集合在同一个事务内替换，避免半份私人或共享数据。 */
    public static void records(Connection db, String scope, String kind, Map<String, byte[]> values) throws SQLException {
        try (var sql = db.prepareStatement("DELETE FROM records WHERE scope=? AND kind=?")) {
            sql.setString(1, scope); sql.setString(2, kind); sql.executeUpdate();
        }
        try (var sql = db.prepareStatement("INSERT INTO records VALUES(?,?,?,?)")) {
            for (var entry : values.entrySet()) {
                sql.setString(1, scope); sql.setString(2, kind); sql.setString(3, entry.getKey()); sql.setBytes(4, entry.getValue()); sql.addBatch();
            }
            sql.executeBatch();
        }
    }
    public static Map<String, byte[]> points(java.util.List<MapWaypoint> points) {
        var values = new LinkedHashMap<String, byte[]>();
        for (int i = 0; i < points.size(); i++) values.put(String.format(java.util.Locale.ROOT, "%04d", i), MapBinaryCodec.waypoint(points.get(i)));
        return values;
    }
    private IllegalStateException failure(Exception error) {
        DebugLogger.exception("MapDatabase", "地图数据库操作失败：" + path, error);
        return new IllegalStateException("无法读写地图数据库：" + path, error);
    }
    @Override public synchronized void close() {
        try { connection.close(); } catch (SQLException error) { throw failure(error); }
    }
}
