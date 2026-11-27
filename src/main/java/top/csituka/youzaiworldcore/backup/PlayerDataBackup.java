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
import java.nio.file.attribute.FileTime;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
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
    private boolean stopping;
    private boolean crashed;

    private PlayerDataBackup(MinecraftServer server) {
        this.server = server;
    }

    public static void register() {
        ServerLifecycleEvents.SERVER_STARTING.register(server -> {
            recoverInterruptedSession(server);
            applyPendingRestore(server);
        });
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            var backup = new PlayerDataBackup(server);
            active = backup;
            try {
                BackupFiles.atomicWrite(sessionFile(server), path -> Files.writeString(path, ""));
            } catch (IOException | RuntimeException e) {
                LOGGER.error("无法记录备份会话", e);
            }
            backup.writer.scheduleWithFixedDelay(backup::requestCheck, 0, 1, TimeUnit.SECONDS);
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            if (active == null || active.server != server || active.stopping) {
                return;
            }
            active.stopping = true;
            try {
                BackupModule.flushSkills();
            } catch (RuntimeException e) {
                LOGGER.error("关服时保存技能数据失败", e);
            }
        });
    }

    /**
     * Vanilla is entering its exception handler. Its normal final save still runs
     * afterward.
     */
    public static void markCrashed(MinecraftServer server) {
        if (active != null && active.server == server) {
            active.crashed = true;
            active.stopping = true;
        }
    }

    /**
     * Archive the files after the final save, or the last readable files if the
     * save failed.
     */
    public static void backupOnExit(MinecraftServer server, boolean shutdownFailed) {
        if (active == null || active.server != server) {
            return;
        }
        var backup = active;
        active = null;
        try {
            backup.writer.close();
            BackupPoint point = nextExitPoint(server, backup.crashed || shutdownFailed);
            writeBatch(server, point, capture(server, false));
            Files.deleteIfExists(sessionFile(server));
        } catch (IOException | RuntimeException e) {
            LOGGER.error("关闭服务器时备份玩家数据失败", e);
        }
    }

    private static void recoverInterruptedSession(MinecraftServer server) {
        Path session = sessionFile(server);
        if (!Files.isRegularFile(session)) {
            return;
        }
        try {
            String saved = Files.readString(session);
            BackupPoint point;
            if (saved.isEmpty()) {
                point = nextExitPoint(server, true);
                BackupFiles.atomicWrite(session, path -> Files.writeString(path, point.fileStem()));
            } else {
                point = BackupPoint.parseFileStem(saved);
            }
            if (!batchComplete(server, point)) {
                writeBatch(server, point, capture(server, false));
            }
            Files.deleteIfExists(session);
        } catch (IOException | RuntimeException e) {
            LOGGER.error("上次服务器异常退出，补存玩家数据备份失败", e);
        }
    }

    private static BackupPoint nextExitPoint(MinecraftServer server, boolean crashed) {
        LocalDateTime time = LocalDateTime.now();
        while (true) {
            BackupPoint point = BackupPoint.exit(crashed, time);
            boolean exists = Files.exists(batchFile(server, point));
            for (BackupModule module : BackupModule.values()) {
                exists |= Files.exists(archiveFile(server, module, point));
            }
            if (!exists) {
                return point;
            }
            time = time.plusNanos(1_000_000);
        }
    }

    // Server wall-clock tasks keep running when the empty-server tick loop is
    // paused.
    private void requestCheck() {
        if (!Instant.now().isBefore(nextCheck) && checkQueued.compareAndSet(false, true)) {
            server.execute(() -> {
                try {
                    if (active == this && !stopping && server.isRunning()) {
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
        BackupPoint point = BackupPoint.daily(date);
        if (batchComplete(server, point)) {
            nextCheck = date.plusDays(1).atTime(START_TIME).atZone(now.getZone()).toInstant();
            return;
        }
        nextCheck = now.toInstant().plusSeconds(300);

        // Freeze the server-owned state at one point. Only ZIP creation runs on the
        // writer thread.
        server.getPlayerList().saveAll();
        server.overworld().getDataStorage().saveAndJoin();
        Map<BackupModule, JsonObject> snapshots = capture(server, true);
        writing = CompletableFuture.runAsync(() -> writeBatch(server, point, snapshots), writer);
    }

    private static Map<BackupModule, JsonObject> capture(MinecraftServer server, boolean live) {
        @SuppressWarnings("null")
        Map<BackupModule, JsonObject> snapshots = new EnumMap<>(BackupModule.class);
        for (BackupModule module : BackupModule.values()) {
            try {
                snapshots.put(module, module.capture(server, live));
            } catch (IOException | RuntimeException e) {
                LOGGER.error("采集玩家数据备份失败: {}", module.id, e);
            }
        }
        return snapshots;
    }

    private static void writeBatch(MinecraftServer server, BackupPoint point, Map<BackupModule, JsonObject> snapshots) {
        String batch = UUID.randomUUID().toString();
        int written = 0;
        for (var entry : snapshots.entrySet()) {
            BackupModule module = entry.getKey();
            JsonObject archive = new JsonObject();
            archive.addProperty("version", VERSION);
            archive.addProperty("date", point.date().toString());
            archive.addProperty("id", point.id());
            archive.addProperty("module", module.id);
            archive.addProperty("batch", batch);
            archive.add("data", entry.getValue());
            try {
                BackupFiles.writeArchive(archiveFile(server, module, point), archive);
                written++;
            } catch (IOException | RuntimeException e) {
                LOGGER.error("写入玩家数据备份失败: {}", module.id, e);
            }
        }
        if (written == BackupModule.values().length) {
            try {
                BackupFiles.atomicWrite(batchFile(server, point), file -> Files.writeString(file, batch));
                LOGGER.notice("已完成 {} 玩家数据备份，共 {} 个模块", point.id(), written);
            } catch (IOException e) {
                LOGGER.error("写入玩家数据备份完成标记失败: {}", point.id(), e);
            }
        }
    }

    @SuppressWarnings("null")
    static List<BackupPoint> points(MinecraftServer server, BackupModule module) throws IOException {
        Path directory = ModPaths.playerDataBackups(server, module == null ? GlobalSettings.BACKUP_MODULE : module.id);
        String suffix = module == null ? ".complete" : ".zip";
        List<AvailableBackup> backups = new ArrayList<>();
        for (Path file : BackupFiles.files(directory, 1, name -> name.endsWith(suffix))) {
            String name = file.getFileName().toString();
            try {
                BackupPoint point = BackupPoint.parseFileStem(name.substring(0, name.length() - suffix.length()));
                if (module != null || batchComplete(server, point)) {
                    backups.add(new AvailableBackup(point, Files.getLastModifiedTime(file)));
                }
            } catch (DateTimeException | IllegalArgumentException ignored) {
                // Other files aren't backups created by this scheduler.
            }
        }
        backups.sort(Comparator.comparing(AvailableBackup::writtenAt).reversed()
                .thenComparing(backup -> backup.point().id()));
        return backups.stream().map(AvailableBackup::point).toList();
    }

    private static boolean batchComplete(MinecraftServer server, BackupPoint point) {
        if (!Files.isRegularFile(batchFile(server, point))) {
            return false;
        }
        for (BackupModule module : BackupModule.values()) {
            if (!Files.isRegularFile(archiveFile(server, module, point))) {
                return false;
            }
        }
        return true;
    }

    static void queueRestore(MinecraftServer server, BackupModule module, BackupPoint point) throws IOException {
        Path pending = pendingFile(server);
        if (Files.exists(pending)) {
            throw new IllegalStateException("A restore is already queued");
        }
        JsonObject modules = new JsonObject();
        String batch = module == null ? Files.readString(batchFile(server, point)) : null;
        for (BackupModule candidate : module == null ? BackupModule.values() : new BackupModule[] { module }) {
            JsonObject archive = readArchive(archiveFile(server, candidate, point));
            String id = point.daily() && !archive.has("id")
                    ? BackupFiles.string(archive.get("date"))
                    : BackupFiles.string(archive.get("id"));
            if (!point.id().equals(id) || !point.date().toString().equals(BackupFiles.string(archive.get("date")))
                    || !candidate.id.equals(BackupFiles.string(archive.get("module")))
                    || (batch != null && !batch.equals(BackupFiles.string(archive.get("batch"))))) {
                throw new IOException("Backup metadata does not match " + candidate.id + " for " + point.id());
            }
            modules.add(candidate.id, BackupFiles.object(archive.get("data")));
        }
        prepareRestore(server, modules);
        JsonObject request = new JsonObject();
        request.addProperty("version", VERSION);
        request.addProperty("date", point.id());
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

    private static Path sessionFile(MinecraftServer server) {
        return ModPaths.worldBackup(server, GlobalSettings.BACKUP_MODULE).resolve("backup-session");
    }

    private static Path archiveFile(MinecraftServer server, BackupModule module, BackupPoint point) {
        return ModPaths.playerDataBackups(server, module.id).resolve(point.fileStem() + ".zip");
    }

    private static Path batchFile(MinecraftServer server, BackupPoint point) {
        return ModPaths.playerDataBackups(server, GlobalSettings.BACKUP_MODULE).resolve(point.fileStem() + ".complete");
    }

    private record AvailableBackup(BackupPoint point, FileTime writtenAt) {
    }

    private record RestorePlan(BackupFiles.Restore files, List<Runnable> databases) {
    }
}
