package top.csituka.youzaiworldcore.pet;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.mojang.serialization.JsonOps;
import net.minecraft.server.MinecraftServer;
import top.csituka.youzaiworldcore.config.GlobalSettings;
import top.csituka.youzaiworldcore.config.ModPaths;
import top.csituka.youzaiworldcore.pet.config.PetModuleConfig;
import top.csituka.youzaiworldcore.util.BackupArchive;
import top.csituka.youzaiworldcore.util.DebugLogger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * 宠物注册表异步定时备份管理器。
 * <p>
 * 每 {@link PetModuleConfig#getBackupIntervalSeconds()} 秒执行一次备份，
 * 备份产物为 {@code yzwc/server/backup/pet_module/pet_backup_<时间戳>.zip}
 * （压缩包内是同名的 {@code .json}）。
 * </p>
 * <p>
 * 线程安全：通过 {@code server.submit()} 向游戏主线程提交 DTO 快照构建任务，
 * 然后将快照传递给异步线程进行 JSON 序列化与文件 IO，避免 {@code ConcurrentModificationException}。
 * </p>
 */
public final class PetBackupManager {

    private static final String MODULE = "PetBackupManager";
    private static final DebugLogger.ModuleLogger LOGGER = DebugLogger.SERVER.module("PetBackup");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final DateTimeFormatter TIMESTAMP_FORMATTER =
            DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    private static final String BACKUP_PREFIX = "pet_backup_";
    private static final String BACKUP_SUFFIX = ".zip";

    private static final Path BACKUP_DIR = ModPaths.serverBackup(GlobalSettings.PET_MODULE);

    private static ScheduledExecutorService scheduler;
    private static MinecraftServer currentServer;
    private static boolean initialized = false;

    private PetBackupManager() {
    }

    public static synchronized void initialize(MinecraftServer server) {
        if (initialized) {
            return;
        }
        currentServer = server;
        DebugLogger.SERVER.entering(MODULE, "initialize");

        ModPaths.ensureDir(BACKUP_DIR);
        DebugLogger.SERVER.debug(MODULE, "备份目录已创建: {}", BACKUP_DIR.toAbsolutePath());

        startScheduler();
        initialized = true;
        DebugLogger.SERVER.exiting(MODULE, "initialize");
    }

    /**
     * 关闭备份管理器（释放线程池）。
     */
    public static synchronized void shutdown() {
        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdown();
            DebugLogger.SERVER.debug(MODULE, "备份调度器已关闭");
        }
        initialized = false;
    }

    private static void startScheduler() {
        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdown();
        }

        int interval = PetModuleConfig.getBackupIntervalSeconds();
        if (!PetModuleConfig.isAutoBackupEnabled() || interval <= 0) {
            DebugLogger.SERVER.debug(MODULE, "自动备份已禁用，跳过调度");
            return;
        }

        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "PetBackupScheduler");
            t.setDaemon(true);
            return t;
        });

        scheduler.scheduleWithFixedDelay(
                PetBackupManager::executeBackup,
                interval,
                interval,
                TimeUnit.SECONDS
        );

        DebugLogger.SERVER.debug(MODULE, "备份调度器已启动，间隔 {} 秒", interval);
    }

    /**
     * 执行备份（异步线程入口）。
     * <p>
     * 通过 {@code server.submit()} 在主线程获取快照，然后在异步线程序列化到磁盘。
     * </p>
     */
    public static void executeBackup() {
        if (currentServer == null || !currentServer.isRunning()) {
            DebugLogger.SERVER.debug(MODULE, "服务器未运行，跳过备份");
            return;
        }

        if (!PetModuleConfig.isAutoBackupEnabled()) {
            return;
        }

        // 步骤 1：在主线程获取深拷贝快照（DTO）
        currentServer.submit(() -> {
            try {
                PetGlobalState state = PetGlobalState.get(currentServer);
                Map<UUID, PetEntry> snapshot = state.getSnapshot();
                // 传递给异步线程序列化
                serializeBackupAsync(snapshot);
            } catch (Exception e) {
                LOGGER.error("备份：主线程快照获取失败", e);
            }
        });
    }

    /**
     * 在异步线程中序列化备份数据并写入磁盘。
     *
     * @param snapshot 不可变的 DTO 快照
     */
    private static void serializeBackupAsync(Map<UUID, PetEntry> snapshot) {
        scheduler.submit(() -> {
            try {
                String timestamp = LocalDateTime.now().format(TIMESTAMP_FORMATTER);
                Path backupFile = BACKUP_DIR.resolve(BACKUP_PREFIX + timestamp + BACKUP_SUFFIX);

                JsonObject pets = new JsonObject();
                for (Map.Entry<UUID, PetEntry> entry : snapshot.entrySet()) {
                    JsonElement encoded = PetEntry.CODEC.encodeStart(JsonOps.INSTANCE, entry.getValue()).getOrThrow();
                    pets.add(entry.getKey().toString(), encoded);
                }

                String json = GSON.toJson(pets);
                BackupArchive.writeJson(backupFile, BACKUP_PREFIX + timestamp + ".json", json);

                DebugLogger.SERVER.info(MODULE, "备份已保存: {} ({} 条记录, {} 字节)", backupFile.getFileName(), snapshot.size(), json.length());

                cleanupOldBackups();

            } catch (Exception e) {
                LOGGER.error("备份：异步序列化失败", e);
            }
        });
    }

    private static void cleanupOldBackups() {
        int retention = PetModuleConfig.getBackupRetentionCount();
        try (Stream<Path> files = Files.list(BACKUP_DIR)) {
            List<Path> backups = files
                    .filter(PetBackupManager::isBackupFile)
                    .sorted()
                    .toList();

            if (backups.size() <= retention) {
                return;
            }

            int toDelete = backups.size() - retention;
            for (int i = 0; i < toDelete; i++) {
                try {
                    Files.deleteIfExists(backups.get(i));
                    DebugLogger.SERVER.debug(MODULE, "已清理旧备份: {}", backups.get(i).getFileName());
                } catch (IOException e) {
                    LOGGER.warn("清理旧备份失败: {}", backups.get(i).getFileName());
                }
            }
        } catch (IOException e) {
            LOGGER.warn("备份目录遍历失败", e);
        }
    }

    /**
     * 列出所有可用备份文件。
     *
     * @return 按时间排序的备份压缩包路径列表
     */
    public static List<Path> listBackups() {
        if (!Files.isDirectory(BACKUP_DIR)) {
            return List.of();
        }
        try (Stream<Path> files = Files.list(BACKUP_DIR)) {
            return files
                    .filter(PetBackupManager::isBackupFile)
                    .sorted()
                    .toList();
        } catch (IOException e) {
            LOGGER.warn("备份目录读取失败", e);
            return List.of();
        }
    }

    private static boolean isBackupFile(Path path) {
        String name = path.getFileName().toString();
        return name.startsWith(BACKUP_PREFIX) && name.endsWith(BACKUP_SUFFIX);
    }

    /**
     * 从备份压缩包恢复数据。
     *
     * @param backupFile 备份压缩包路径
     * @return 恢复的宠物条目映射；如果失败返回空映射
     */
    public static Map<UUID, PetEntry> restoreFromBackup(Path backupFile) {
        DebugLogger.SERVER.entering(MODULE, "restoreFromBackup", "file=" + backupFile);
        try {
            String json = BackupArchive.readFirstJson(backupFile);
            if (json == null) {
                LOGGER.error("备份压缩包内没有 .json 条目: {}", backupFile);
                return Map.of();
            }
            if (!(JsonParser.parseString(json) instanceof JsonObject pets)) {
                LOGGER.error("宠物备份 JSON 的根节点必须是对象: {}", backupFile);
                return Map.of();
            }

            Map<UUID, PetEntry> result = new HashMap<>();
            for (Map.Entry<String, JsonElement> entry : pets.entrySet()) {
                try {
                    UUID uuid = UUID.fromString(entry.getKey());
                    JsonElement value = entry.getValue();
                    if (value instanceof JsonObject pet && pet.get("mode") instanceof JsonPrimitive mode
                            && mode.isString()) {
                        // 旧备份由 Gson 写入 Java 枚举常量名，Codec 使用小写模式名。
                        pet.addProperty("mode", PetMode.fromString(mode.getAsString()).getSerializedName());
                    }
                    PetEntry petEntry = PetEntry.CODEC.parse(JsonOps.INSTANCE, value)
                            .result().orElse(null);
                    if (petEntry == null) {
                        LOGGER.warn("反序列化 PetEntry 失败: {}", entry.getKey());
                        continue;
                    }
                    result.put(uuid, petEntry);
                } catch (Exception e) {
                    LOGGER.warn("跳过无法解析的备份条目: {}", entry.getKey());
                }
            }

            DebugLogger.SERVER.info(MODULE, "从备份恢复 {} / {} 条记录", result.size(), pets.size());
            DebugLogger.SERVER.exiting(MODULE, "restoreFromBackup", "count=" + result.size());
            return result;
        } catch (IOException | JsonParseException e) {
            LOGGER.error("读取备份文件失败", e);
            return Map.of();
        }
    }
}
