package top.csituka.youzaiworldcore.backup;

import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import net.minecraft.server.MinecraftServer;
import top.csituka.youzaiworldcore.config.GlobalSettings;
import top.csituka.youzaiworldcore.config.ModPaths;
import top.csituka.youzaiworldcore.data.TeleportAnchorManager;
import top.csituka.youzaiworldcore.map.MapPlayerBackup;
import top.csituka.youzaiworldcore.pet.PetGlobalState;
import top.csituka.youzaiworldcore.skill.PlayerAttributeStorage;
import top.csituka.youzaiworldcore.skill.PlayerLevelStorage;
import top.csituka.youzaiworldcore.status.StatsManager;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

enum BackupModule {
    PET(GlobalSettings.PET_MODULE),
    SKILL(GlobalSettings.SKILL_MODULE),
    DIMENSIONAL_INVENTORIES(GlobalSettings.DIMENSIONAL_INVENTORIES_MODULE),
    STATUS(GlobalSettings.STATUS_MODULE),
    DOUBLE_DOORS(GlobalSettings.DOUBLE_DOORS_MODULE),
    FUNCTION(GlobalSettings.FUNCTION_MODULE),
    TELEPORT_ANCHOR("teleport_anchor_module"),
    MAP("map_module");

    private static final String UUID_PATTERN = "[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}";
    private static final Pattern PLAYER_JSON = Pattern.compile(UUID_PATTERN + "\\.json");
    private static final Pattern PLAYER_NBT = Pattern.compile(UUID_PATTERN + "\\.dat(?:_old)?");
    private static final Pattern POOL_STATE = Pattern.compile(
            "[A-Za-z0-9_-][A-Za-z0-9_.-]*/" + UUID_PATTERN + "\\.json|_recovery/" + UUID_PATTERN + "\\.return");

    final String id;

    BackupModule(String id) {
        this.id = id;
    }

    JsonObject capture(MinecraftServer server, boolean live) throws IOException {
        JsonObject data = new JsonObject();
        switch (this) {
            case PET, TELEPORT_ANCHOR -> data.add("saved_data", BackupFiles.readFile(savedData(server)));
            case SKILL -> {
                if (live) {
                    flushSkills();
                }
                data.add("skill", BackupFiles.readFile(ModPaths.serverDataFile(id)));
            }
            case STATUS -> {
                JsonObject status = live ? StatsManager.backupSnapshot(server)
                        : BackupFiles.readObject(ModPaths.worldDataFile(server, id));
                if (!(status.get("players") instanceof JsonObject)) {
                    throw new IOException("No saved player statistics available");
                }
                data.add("status", status);
                data.add("vanilla_stats", BackupFiles.readFiles(ModPaths.vanillaStatsDir(server), 1,
                        PLAYER_JSON.asMatchPredicate()));
            }
            case DIMENSIONAL_INVENTORIES -> {
                data.add("pools", BackupFiles.readFiles(ModPaths.worldData(server, id), 2, POOL_STATE.asMatchPredicate()));
                data.add("players", BackupFiles.readFiles(ModPaths.vanillaPlayerDataDir(server), 1,
                        PLAYER_NBT.asMatchPredicate()));
            }
            case DOUBLE_DOORS, FUNCTION -> {
                for (Path file : settingsFiles()) {
                    var section = BackupFiles.readObject(file).get(id);
                    if (section != null) {
                        data.add(playerId(file), BackupFiles.object(section));
                    }
                }
            }
            case MAP -> {
                if (!live && !Files.isRegularFile(ModPaths.worldMapDatabase(server))) {
                    throw new IOException("No saved world map database available");
                }
                data = MapPlayerBackup.capture(server);
            }
        }
        return data;
    }

    static void flushSkills() {
        PlayerLevelStorage.forceSave();
        PlayerAttributeStorage.forceSave();
    }

    void prepareRestore(MinecraftServer server, JsonObject data, BackupFiles.Restore restore,
                        List<Runnable> databases) throws IOException {
        switch (this) {
            case PET, TELEPORT_ANCHOR -> restore.file(savedData(server), data.get("saved_data"));
            case SKILL -> restore.file(ModPaths.serverDataFile(id), data.get("skill"));
            case DIMENSIONAL_INVENTORIES -> {
                restore.files(ModPaths.worldData(server, id), 2, POOL_STATE.asMatchPredicate(),
                        BackupFiles.object(data.get("pools")));
                restore.files(ModPaths.vanillaPlayerDataDir(server), 1, PLAYER_NBT.asMatchPredicate(),
                        BackupFiles.object(data.get("players")));
            }
            case STATUS -> {
                Path file = ModPaths.worldDataFile(server, id);
                JsonObject current;
                try {
                    current = restore.readObject(file);
                } catch (JsonParseException | IllegalArgumentException e) {
                    current = new JsonObject();
                }
                restore.object(file, StatsManager.prepareBackupRestore(BackupFiles.object(data.get("status")), current));
                restore.files(ModPaths.vanillaStatsDir(server), 1, PLAYER_JSON.asMatchPredicate(),
                        BackupFiles.object(data.get("vanilla_stats")));
            }
            case DOUBLE_DOORS, FUNCTION -> restoreSettings(data, restore);
            case MAP -> databases.add(MapPlayerBackup.prepareRestore(server, data));
        }
    }

    private Path savedData(MinecraftServer server) {
        return ModPaths.overworldSavedData(server, this == PET ? PetGlobalState.TYPE.id() : TeleportAnchorManager.TYPE.id());
    }

    private static List<Path> settingsFiles() throws IOException {
        return BackupFiles.files(ModPaths.userSettingsDir(), 1, PLAYER_JSON.asMatchPredicate());
    }

    private static String playerId(Path file) {
        return file.getFileName().toString().substring(0, 36);
    }

    private void restoreSettings(JsonObject data, BackupFiles.Restore restore) throws IOException {
        var players = new LinkedHashSet<String>();
        for (Path file : settingsFiles()) {
            players.add(playerId(file));
        }
        for (var entry : data.entrySet()) {
            if (!PLAYER_JSON.matcher(entry.getKey() + ".json").matches()) {
                throw new IllegalArgumentException("Invalid player UUID: " + entry.getKey());
            }
            BackupFiles.object(entry.getValue());
            players.add(entry.getKey());
        }
        for (String player : players) {
            Path file = ModPaths.userSettingsFile(UUID.fromString(player));
            JsonObject settings = restore.readObject(file);
            if (data.has(player)) {
                settings.add(id, data.get(player));
            } else if (settings.remove(id) == null) {
                continue;
            }
            restore.object(file, settings);
        }
    }
}
