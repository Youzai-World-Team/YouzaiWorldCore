package top.csituka.youzaiworldcore.map;

import com.google.gson.JsonObject;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import top.csituka.youzaiworldcore.util.DebugLogger;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** 玩家最近位置随存档保存在 worldMapDatabase；HTTP 失败后保留待上传记录，不读取旧账户退出坐标。 */
public final class MapPlayerPositionStore {
    private static final int MAX_PLAYERS = 4096;
    private final MapDatabase database;

    /** 使用当前世界已经打开的地图 SQLite，不创建全局账户位置文件。 */
    public MapPlayerPositionStore(MapDatabase database) {
        this.database = database;
    }

    /** 主线程读取实体后立即保存；同一玩家的记录时间严格递增，避免并发上传覆盖新位置。 */
    public void capture(ServerPlayer player, String source) {
        database.transaction(db -> {
            long previous = 0;
            try (var sql = db.prepareStatement("SELECT recorded_at FROM player_positions WHERE uuid=?")) {
                sql.setString(1, player.getUUID().toString());
                try (var rows = sql.executeQuery()) {
                    if (rows.next()) previous = rows.getLong(1);
                }
            }
            var position = new Snapshot(player.getUUID(), player.getScoreboardName(),
                    player.level().dimension().identifier().toString(), player.getX(), player.getY(), player.getZ(),
                    Math.max(System.currentTimeMillis(), previous + 1), source);
            try (var sql = db.prepareStatement("""
                    INSERT INTO player_positions (uuid,name,dimension,x,y,z,recorded_at,source,uploaded_at)
                    VALUES(?,?,?,?,?,?,?,?,0) ON CONFLICT(uuid) DO UPDATE SET
                    name=excluded.name,dimension=excluded.dimension,x=excluded.x,y=excluded.y,z=excluded.z,
                    recorded_at=excluded.recorded_at,source=excluded.source
                    """)) {
                sql.setString(1, position.uuid().toString()); sql.setString(2, position.name());
                sql.setString(3, position.dimension()); sql.setDouble(4, position.x());
                sql.setDouble(5, position.y()); sql.setDouble(6, position.z());
                sql.setLong(7, position.recordedAt()); sql.setString(8, position.source());
                sql.executeUpdate();
            }
            try (var sql = db.prepareStatement("""
                    DELETE FROM player_positions WHERE uuid IN
                    (SELECT uuid FROM player_positions ORDER BY recorded_at DESC,uuid LIMIT -1 OFFSET ?)
                    """)) {
                sql.setInt(1, MAX_PLAYERS); sql.executeUpdate();
            }
            return null;
        });
        DebugLogger.SERVER.debug("MapPlayerPositionStore", "已记录玩家位置：{}，时机={}", player.getUUID(), source);
    }

    /** 上传线程读取有界快照；全量上传包含离线玩家，增量只读取尚未确认的记录。 */
    public List<Snapshot> read(boolean all) {
        return database.read(db -> {
            var result = new ArrayList<Snapshot>();
            String filter = all ? "" : " WHERE recorded_at>uploaded_at";
            try (var sql = db.prepareStatement("SELECT uuid,name,dimension,x,y,z,recorded_at,source FROM player_positions"
                    + filter + " ORDER BY recorded_at DESC,uuid LIMIT ?")) {
                sql.setInt(1, MAX_PLAYERS);
                try (var rows = sql.executeQuery()) {
                    while (rows.next()) result.add(new Snapshot(UUID.fromString(rows.getString(1)), rows.getString(2),
                            rows.getString(3), rows.getDouble(4), rows.getDouble(5), rows.getDouble(6),
                            rows.getLong(7), rows.getString(8)));
                }
            }
            return List.copyOf(result);
        });
    }

    /** 仅确认实际发出的版本；HTTP 期间新采集的记录继续保留为待上传。 */
    public void acknowledge(List<Snapshot> positions) {
        database.transaction(db -> {
            try (var sql = db.prepareStatement("UPDATE player_positions SET uploaded_at=? WHERE uuid=? AND recorded_at=?")) {
                for (var position : positions) {
                    sql.setLong(1, position.recordedAt()); sql.setString(2, position.uuid().toString());
                    sql.setLong(3, position.recordedAt()); sql.addBatch();
                }
                sql.executeBatch();
            }
            return null;
        });
    }

    /** 不可变实体快照，供后台签名上传；不包含会话令牌和其他账户数据。 */
    public record Snapshot(UUID uuid, String name, String dimension, double x, double y, double z,
                           long recordedAt, String source) {
        public Snapshot {
            if (uuid == null || name == null || name.isBlank() || name.length() > 64
                    || name.chars().anyMatch(value -> value < 32 || value == 127)
                    || dimension == null || dimension.length() > 128 || Identifier.tryParse(dimension) == null
                    || "youzaiworldcore:login_hall".equals(dimension)
                    || !Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)
                    || Math.abs(x) > MapTileKey.WORLD_LIMIT || Math.abs(z) > MapTileKey.WORLD_LIMIT
                    || y < -4096 || y > 4095 || recordedAt <= 0
                    || !List.of("login", "logout", "upload").contains(source)) {
                throw new IllegalArgumentException("玩家地图位置快照无效");
            }
        }

        /** 与 Api 的玩家快照协议一致，时间使用 Unix 毫秒。 */
        public JsonObject json() {
            var value = new JsonObject();
            value.addProperty("uuid", uuid.toString()); value.addProperty("name", name);
            value.addProperty("dimension", dimension); value.addProperty("x", x);
            value.addProperty("y", y); value.addProperty("z", z);
            value.addProperty("recorded_at", recordedAt); value.addProperty("source", source);
            return value;
        }
    }
}
