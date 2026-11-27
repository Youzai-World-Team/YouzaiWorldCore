package top.csituka.youzaiworldcore.map;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import net.minecraft.server.MinecraftServer;
import top.csituka.youzaiworldcore.config.ModPaths;

import java.util.HashSet;
import java.util.List;
import java.util.UUID;

/** Snapshot the player-owned rows without copying the terrain database or its WAL. */
public final class MapPlayerBackup {
    private static final Gson GSON = new Gson();

    private MapPlayerBackup() {
    }

    public static JsonObject capture(MinecraftServer server) {
        List<Rule> rules = MapServerSettings.database().read(db -> {
            var values = new java.util.ArrayList<Rule>();
            try (var sql = db.prepareStatement("SELECT scope,key,value FROM settings WHERE scope LIKE 'player:%' ORDER BY scope,key");
                 var rows = sql.executeQuery()) {
                while (rows.next()) {
                    values.add(new Rule(rows.getString(1), rows.getString(2), rows.getString(3)));
                }
            }
            return values;
        });
        try (var world = new MapDatabase(ModPaths.worldMapDatabase(server))) {
            var positions = new MapPlayerPositionStore(world).read(true);
            return GSON.toJsonTree(new Snapshot(rules, positions)).getAsJsonObject();
        }
    }

    /** Decode everything before any module is changed. Apply on startup before map sessions are opened. */
    public static Runnable prepareRestore(MinecraftServer server, JsonObject data) {
        Snapshot snapshot = GSON.fromJson(data, Snapshot.class);
        var rules = new HashSet<List<String>>();
        for (Rule rule : snapshot.rules()) {
            if (!rule.scope().equals("player:" + UUID.fromString(rule.scope().substring("player:".length())))
                    || rule.key() == null || rule.value() == null || !rules.add(List.of(rule.scope(), rule.key()))) {
                throw new IllegalArgumentException("Invalid map sharing rule");
            }
        }
        var players = new HashSet<UUID>();
        for (var position : snapshot.positions()) {
            if (!players.add(position.uuid())) {
                throw new IllegalArgumentException("Duplicate map player position");
            }
        }
        return () -> restore(server, snapshot);
    }

    private static void restore(MinecraftServer server, Snapshot snapshot) {
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
        try (var world = new MapDatabase(ModPaths.worldMapDatabase(server))) {
            world.transaction(db -> {
                try (var clear = db.prepareStatement("DELETE FROM player_positions");
                     var insert = db.prepareStatement("""
                             INSERT INTO player_positions (uuid,name,dimension,x,y,z,recorded_at,source,uploaded_at)
                             VALUES (?,?,?,?,?,?,?,?,?)
                             """)) {
                    clear.executeUpdate();
                    for (var position : snapshot.positions()) {
                        insert.setString(1, position.uuid().toString());
                        insert.setString(2, position.name());
                        insert.setString(3, position.dimension());
                        insert.setDouble(4, position.x());
                        insert.setDouble(5, position.y());
                        insert.setDouble(6, position.z());
                        insert.setLong(7, position.recordedAt());
                        insert.setString(8, position.source());
                        // Historical positions are for local recovery. Fresh observations are uploaded normally.
                        insert.setLong(9, position.recordedAt());
                        insert.addBatch();
                    }
                    insert.executeBatch();
                }
                return null;
            });
        }
    }

    private record Rule(String scope, String key, String value) {
    }

    private record Snapshot(List<Rule> rules, List<MapPlayerPositionStore.Snapshot> positions) {
    }
}
