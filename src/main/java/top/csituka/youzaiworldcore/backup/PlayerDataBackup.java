package top.csituka.youzaiworldcore.backup;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.MinecraftServer;
import top.csituka.youzaiworldcore.config.GlobalSettings;
import top.csituka.youzaiworldcore.config.ModPaths;
import top.csituka.youzaiworldcore.config.UserSettings;
import top.csituka.youzaiworldcore.util.BackupArchive;
import top.csituka.youzaiworldcore.util.DebugLogger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public final class PlayerDataBackup {
    private static final DebugLogger.ModuleLogger LOGGER = DebugLogger.SERVER.module("PlayerDataBackup");
    private static final LocalTime START_TIME = LocalTime.of(4, 0);
    private static final String DAILY_PREFIX = "daily-";
    private static final int VERSION = 1;
    private static PlayerDataBackup active;

    private final MinecraftServer server;
    private final ScheduledExecutorService writer = Executors.newSingleThreadScheduledExecutor(task -> {
        Thread thread = new Thread(task, "PlayerDataBackup");
        thread.setDaemon(true);
        return thread;
    });
    private final AtomicBoolean checkQueued = new AtomicBoolean();
    private volatile Instant nextCheck = Instant.MIN;
    private CompletableFuture<Void> writing = CompletableFuture.completedFuture(null);

    private PlayerDataBackup(MinecraftServer server) {
        this.server = server;
    }

    public static void register() {
        ServerLifecycleEvents.SERVER_STARTING.register(PlayerDataBackup::applyPendingRestore);
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            var backup = new PlayerDataBackup(server);
            active = backup;
            backup.writer.scheduleWithFixedDelay(backup::requestCheck, 0, 1, TimeUnit.SECONDS);
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            if (active != null && active.server == server) {
                var backup = active;
                active = null;
                backup.writer.close();
            }
        });
    }

    // Server wall-clock tasks keep running when the empty-server tick loop is
    // paused.
    private void requestCheck() {
        if (!Instant.now().isBefore(nextCheck) && checkQueued.compareAndSet(false, true)) {
            server.execute(() -> {
                try {
                    if (active == this && server.isRunning()) {
                        tick(ZonedDateTime.now());
                    }
                } catch (RuntimeException e) {
                    LOGGER.error("准备每日玩家数据备份失败", e);
                } finally {
                    checkQueued.set(false);
                }
            });
        }
    }

    static LocalDate dueDate(ZonedDateTime now) {
        return now.toLocalTime().isBefore(START_TIME) ? null : now.toLocalDate();
    }

    private void tick(ZonedDateTime now) {
        if (now.toInstant().isBefore(nextCheck) || !writing.isDone()) {
            return;
        }
        nextCheck = now.toInstant().plusSeconds(1);
        LocalDate date = dueDate(now);
        if (date == null) {
            return;
        }
        if (Files.isRegularFile(batchFile(server, date)) && complete(server, date)) {
            nextCheck = date.plusDays(1).atTime(START_TIME).atZone(now.getZone()).toInstant();
            return;
        }
        nextCheck = now.toInstant().plusSeconds(300);

        // Freeze the server-owned state at one point. Only ZIP creation runs on the
        // writer thread.
        server.getPlayerList().saveAll();
        server.overworld().getDataStorage().saveAndJoin();
        @SuppressWarnings("null")
        Map<BackupModule, JsonObject> snapshots = new EnumMap<>(BackupModule.class);
        for (BackupModule module : BackupModule.values()) {
            try {
                snapshots.put(module, module.capture(server));
            } catch (IOException | RuntimeException e) {
                LOGGER.error("采集玩家数据备份失败: {}", module.id, e);
            }
        }
        writing = CompletableFuture.runAsync(() -> writeBatch(server, date, snapshots), writer);
    }

    private static void writeBatch(MinecraftServer server, LocalDate date, Map<BackupModule, JsonObject> snapshots) {
        String batch = UUID.randomUUID().toString();
        int written = 0;
        for (var entry : snapshots.entrySet()) {
            BackupModule module = entry.getKey();
            JsonObject archive = new JsonObject();
            archive.addProperty("version", VERSION);
            archive.addProperty("date", date.toString());
            archive.addProperty("module", module.id);
            archive.addProperty("batch", batch);
            archive.add("data", entry.getValue());
            try {
                BackupFiles.writeArchive(archiveFile(server, module, date), archive);
                written++;
            } catch (IOException | RuntimeException e) {
                LOGGER.error("写入玩家数据备份失败: {}", module.id, e);
            }
        }
        if (written == BackupModule.values().length) {
            try {
                BackupFiles.atomicWrite(batchFile(server, date), file -> Files.writeString(file, batch));
                LOGGER.notice("已完成 {} 的玩家数据备份，共 {} 个模块", date, written);
            } catch (IOException e) {
                LOGGER.error("写入玩家数据备份完成标记失败: {}", date, e);
            }
        }
    }

    static List<LocalDate> dates(MinecraftServer server, BackupModule module) throws IOException {
        Path directory = ModPaths.dailyBackups(server, module == null ? GlobalSettings.BACKUP_MODULE : module.id);
        String suffix = module == null ? ".complete" : ".zip";
        List<LocalDate> dates = new ArrayList<>();
        for (Path file : BackupFiles.files(directory, 1,
                name -> name.startsWith(DAILY_PREFIX) && name.endsWith(suffix))) {
            String name = file.getFileName().toString();
            try {
                LocalDate date = LocalDate
                        .parse(name.substring(DAILY_PREFIX.length(), name.length() - suffix.length()));
                if (module != null || complete(server, date)) {
                    dates.add(date);
                }
            } catch (DateTimeException ignored) {
                // Other files aren't backups created by this scheduler.
            }
        }
        dates.sort(Comparator.reverseOrder());
        return dates;
    }

    private static boolean complete(MinecraftServer server, LocalDate date) {
        for (BackupModule module : BackupModule.values()) {
            if (!Files.isRegularFile(archiveFile(server, module, date))) {
                return false;
            }
        }
        return true;
    }

    static void queueRestore(MinecraftServer server, BackupModule module, LocalDate date) throws IOException {
        Path pending = pendingFile(server);
        if (Files.exists(pending)) {
            throw new IllegalStateException("A restore is already queued");
        }
        JsonObject modules = new JsonObject();
        String batch = module == null ? Files.readString(batchFile(server, date)) : null;
        for (BackupModule candidate : module == null ? BackupModule.values() : new BackupModule[] { module }) {
            JsonObject archive = readArchive(archiveFile(server, candidate, date));
            if (!date.toString().equals(BackupFiles.string(archive.get("date")))
                    || !candidate.id.equals(BackupFiles.string(archive.get("module")))
                    || (batch != null && !batch.equals(BackupFiles.string(archive.get("batch"))))) {
                throw new IOException("Backup metadata does not match " + candidate.id + " on " + date);
            }
            modules.add(candidate.id, BackupFiles.object(archive.get("data")));
        }
        prepareRestore(server, modules);
        JsonObject request = new JsonObject();
        request.addProperty("version", VERSION);
        request.addProperty("date", date.toString());
        request.add("modules", modules);
        BackupFiles.writeArchive(pending, request);
    }

    @SuppressWarnings("null")
    private static void applyPendingRestore(MinecraftServer server) {
        Path pending = pendingFile(server);
        if (!Files.exists(pending)) {
            return;
        }
        try {
            JsonObject request = readArchive(pending);
            JsonObject modules = BackupFiles.object(request.get("modules"));
            RestorePlan plan = prepareRestore(server, modules);
            plan.files().apply();
            plan.databases().forEach(Runnable::run);
            if (modules.has(GlobalSettings.DOUBLE_DOORS_MODULE) || modules.has(GlobalSettings.FUNCTION_MODULE)) {
                UserSettings.invalidateAll();
            }
            Files.delete(pending);
            LOGGER.notice("已恢复 {} 的玩家数据备份（{} 个模块）", request.get("date").getAsString(), modules.size());
        } catch (IOException | RuntimeException e) {
            // Leave the request in place. A partial restore must complete before players
            // can join.
            throw new IllegalStateException("Player data restore failed: " + pending, e);
        }
    }

    private static RestorePlan prepareRestore(MinecraftServer server, JsonObject modules) throws IOException {
        var files = new BackupFiles.Restore();
        List<Runnable> databases = new ArrayList<>();
        int count = 0;
        for (BackupModule module : BackupModule.values()) {
            if (modules.has(module.id)) {
                module.prepareRestore(server, BackupFiles.object(modules.get(module.id)), files, databases);
                count++;
            }
        }
        if (count == 0 || count != modules.size()) {
            throw new IOException("Unknown module in player data restore");
        }
        return new RestorePlan(files, databases);
    }

    private static JsonObject readArchive(Path file) throws IOException {
        String json = BackupArchive.readFirstJson(file);
        if (json == null) {
            throw new IOException("No JSON file in " + file.getFileName());
        }
        JsonObject result = BackupFiles.object(JsonParser.parseString(json));
        if (!result.has("version") || result.get("version").getAsInt() != VERSION) {
            throw new IOException("Unsupported backup version in " + file.getFileName());
        }
        return result;
    }

    private static Path pendingFile(MinecraftServer server) {
        return ModPaths.worldBackup(server, GlobalSettings.BACKUP_MODULE).resolve("restore-pending.zip");
    }

    private static Path archiveFile(MinecraftServer server, BackupModule module, LocalDate date) {
        return ModPaths.dailyBackups(server, module.id).resolve(DAILY_PREFIX + date + ".zip");
    }

    private static Path batchFile(MinecraftServer server, LocalDate date) {
        return ModPaths.dailyBackups(server, GlobalSettings.BACKUP_MODULE).resolve(DAILY_PREFIX + date + ".complete");
    }

    private record RestorePlan(BackupFiles.Restore files, List<Runnable> databases) {
    }
}
