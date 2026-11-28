package top.csituka.youzaiworldcore.backup;

import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import net.minecraft.server.MinecraftServer;
import top.csituka.youzaiworldcore.config.GlobalSettings;
import top.csituka.youzaiworldcore.config.ModPaths;
import top.csituka.youzaiworldcore.data.TeleportAnchorManager;
import top.csituka.youzaiworldcore.dimensionalinventories.PoolPets;
import top.csituka.youzaiworldcore.dimensionalinventories.PoolScoreboards;
import top.csituka.youzaiworldcore.map.MapPlayerBackup;
import top.csituka.youzaiworldcore.pet.PetGlobalState;
import top.csituka.youzaiworldcore.skill.PlayerAttributeStorage;
import top.csituka.youzaiworldcore.skill.PlayerLevelStorage;
import top.csituka.youzaiworldcore.status.StatsManager;
import top.csituka.youzaiworldcore.util.DebugLogger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
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

    private static final DebugLogger.ModuleLogger LOGGER = DebugLogger.SERVER.module("PlayerDataBackup");
    private static final String UUID_PATTERN = "[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}";
    private static final Pattern PLAYER_JSON = Pattern.compile(UUID_PATTERN + "\\.json");
    private static final Pattern PLAYER_NBT = Pattern.compile(UUID_PATTERN + "\\.dat(?:_old)?");
    private static final Pattern PARKED_PET = Pattern.compile(UUID_PATTERN + "\\.dat");
    private static final Pattern POOL_STATE = Pattern.compile(
            "[A-Za-z0-9_-][A-Za-z0-9_.-]*/" + UUID_PATTERN + "\\.json|_recovery/" + UUID_PATTERN + "\\.return");
    private static final Pattern POOL_SCOREBOARD = Pattern.compile("[A-Za-z0-9_-][A-Za-z0-9_.-]*/scoreboard\\.dat");

    final String id;

    BackupModule(String id) {
        this.id = id;
    }

    @SuppressWarnings("null")
    JsonObject capture(MinecraftServer server, boolean live) throws IOException {
        JsonObject data = new JsonObject();
        switch (this) {
            case PET -> {
                data.add("saved_data", BackupFiles.readFile(savedData(server)));
                JsonObject parked = new JsonObject();
                for (Path file : BackupFiles.files(ModPaths.parkedPets(server), 1, PARKED_PET.asMatchPredicate())) {
                    // 活跃实体的版本标记不包含狼的快照，不能用旧标记覆盖区块里的现行实体。
                    var archive = NbtIo.readCompressed(file, NbtAccounter.uncompressedQuota());
                    if (archive.contains("pet") || archive.contains("wolf")) {
                        parked.add(file.getFileName().toString(), BackupFiles.readFile(file));
                    }
                }
                data.add("parked_pets", parked);
            }
            case TELEPORT_ANCHOR -> data.add("saved_data", BackupFiles.readFile(savedData(server)));
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
                if (live) {
                    PoolScoreboards.flushForBackup(server);
                }
                data.add("pools",
                        BackupFiles.readFiles(ModPaths.worldData(server, id), 2, POOL_STATE.asMatchPredicate()));
                data.add("scoreboards",
                        BackupFiles.readFiles(ModPaths.worldData(server, id), 2, POOL_SCOREBOARD.asMatchPredicate()));
                data.add("vanilla_scoreboard", BackupFiles.readFile(ModPaths.vanillaScoreboard(server)));
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
            case MAP -> data = MapPlayerBackup.capture();
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
            case PET -> preparePetRestore(server, data, restore);
            case TELEPORT_ANCHOR -> restore.file(savedData(server), data.get("saved_data"));
            case SKILL -> restore.file(ModPaths.serverDataFile(id), data.get("skill"));
            case DIMENSIONAL_INVENTORIES -> {
                restore.files(ModPaths.worldData(server, id), 2, POOL_STATE.asMatchPredicate(),
                        BackupFiles.object(data.get("pools")));
                restore.files(ModPaths.vanillaPlayerDataDir(server), 1, PLAYER_NBT.asMatchPredicate(),
                        BackupFiles.object(data.get("players")));
                if (data.has("scoreboards")) {
                    restore.files(ModPaths.worldData(server, id), 2, POOL_SCOREBOARD.asMatchPredicate(),
                            BackupFiles.object(data.get("scoreboards")));
                }
                if (data.has("vanilla_scoreboard")) {
                    restore.file(ModPaths.vanillaScoreboard(server), data.get("vanilla_scoreboard"));
                }
            }
            case STATUS -> {
                Path file = ModPaths.worldDataFile(server, id);
                JsonObject current;
                try {
                    current = restore.readObject(file);
                } catch (JsonParseException | IllegalArgumentException e) {
                    current = new JsonObject();
                }
                restore.object(file,
                        StatsManager.prepareBackupRestore(BackupFiles.object(data.get("status")), current));
                restore.files(ModPaths.vanillaStatsDir(server), 1, PLAYER_JSON.asMatchPredicate(),
                        BackupFiles.object(data.get("vanilla_stats")));
            }
            case DOUBLE_DOORS, FUNCTION -> restoreSettings(data, restore);
            case MAP -> databases.add(MapPlayerBackup.prepareRestore(data));
        }
    }

    @SuppressWarnings("null")
    private void preparePetRestore(MinecraftServer server, JsonObject data, BackupFiles.Restore restore)
            throws IOException {
        var registry = restore.file(savedData(server), data.get("saved_data"));
        var parked = data.has("parked_pets") ? BackupFiles.object(data.get("parked_pets")) : new JsonObject();
        for (var entry : parked.entrySet()) {
            if (!PARKED_PET.matcher(entry.getKey()).matches()) {
                throw new IOException("无效的暂存宠物备份文件名：" + entry.getKey());
            }
            restore.file(ModPaths.parkedPets(server).resolve(entry.getKey()), entry.getValue());
        }
        if (registry == null || !registry.contains("data")) {
            return;
        }
        var state = PetGlobalState.TYPE.codec().parse(NbtOps.INSTANCE, registry.get("data")).resultOrPartial()
                .orElse(null);
        if (state == null) {
            return;
        }
        for (var pet : state.getAllPets()) {
            Path path = ModPaths.parkedPet(server, pet.entityUUID());
            if (parked.has(path.getFileName().toString()) || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
                continue;
            }
            CompoundTag current;
            try {
                current = NbtIo.readCompressed(path, NbtAccounter.uncompressedQuota());
            } catch (IOException | RuntimeException error) {
                LOGGER.warn("无法检查宠物 {} 的放生凭据，保留原档", pet.entityUUID(), error);
                continue;
            }
            if (PoolPets.clearRestoredRelease(current, pet)) {
                restore.nbt(path, current);
            }
        }
    }

    private Path savedData(MinecraftServer server) {
        return ModPaths.overworldSavedData(server,
                this == PET ? PetGlobalState.TYPE.id() : TeleportAnchorManager.TYPE.id());
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
