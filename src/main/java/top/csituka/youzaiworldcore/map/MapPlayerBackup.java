package top.csituka.youzaiworldcore.map;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.util.HashSet;
import java.util.List;
import java.util.UUID;

/** 备份游戏内玩家的位置共享规则。 */
public final class MapPlayerBackup {
    private static final Gson GSON = new Gson();

    private MapPlayerBackup() {
    }

    public static JsonObject capture() {
        List<Rule> rules = MapServerSettings.database().read(db -> {
            var values = new java.util.ArrayList<Rule>();
            try (var sql = db.prepareStatement(
                    "SELECT scope,key,value FROM settings WHERE scope LIKE 'player:%' ORDER BY scope,key");
                    var rows = sql.executeQuery()) {
                while (rows.next()) {
                    values.add(new Rule(rows.getString(1), rows.getString(2), rows.getString(3)));
                }
            }
            return values;
        });
        return GSON.toJsonTree(new Snapshot(rules)).getAsJsonObject();
    }

    /** 启动时先校验共享规则，再应用恢复；旧备份中的位置快照会被忽略。 */
    public static Runnable prepareRestore(JsonObject data) {
        @SuppressWarnings("null")
        Snapshot snapshot = GSON.fromJson(data, Snapshot.class);
        var rules = new HashSet<List<String>>();
        for (Rule rule : snapshot.rules()) {
            if (!rule.scope().equals("player:" + UUID.fromString(rule.scope().substring("player:".length())))
                    || rule.key() == null || rule.value() == null || !rules.add(List.of(rule.scope(), rule.key()))) {
                throw new IllegalArgumentException("Invalid map sharing rule");
            }
        }
        return () -> restore(snapshot);
    }

    private static void restore(Snapshot snapshot) {
        MapServerSettings.database().transaction(db -> {
            try (var clear = db.prepareStatement("DELETE FROM settings WHERE scope LIKE 'player:%'");
                    var insert = db.prepareStatement("INSERT INTO settings(scope,key,value) VALUES(?,?,?)")) {
                clear.executeUpdate();
                for (Rule rule : snapshot.rules()) {
                    insert.setString(1, rule.scope());
                    insert.setString(2, rule.key());
                    insert.setString(3, rule.value());
                    insert.addBatch();
                }
                insert.executeBatch();
            }
            return null;
        });
    }

    private record Rule(String scope, String key, String value) {
    }

    private record Snapshot(List<Rule> rules) {
    }
}
