package top.csituka.youzaiworldcore.pet.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.csituka.youzaiworldcore.util.DebugLogger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 宠物模块配置 — 备份间隔及相关设置。
 * <p>
 * 配置存储于 {@code config/youzaiworldcore/pet_module/settings.json}。
 * </p>
 */
public final class PetModuleConfig {

    private static final String MODULE = "PetModuleConfig";
    private static final Logger LOGGER = LoggerFactory.getLogger("YouzaiWorldCore/PetCfg");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static final Path CONFIG_DIR = FabricLoader.getInstance()
            .getConfigDir().resolve("youzaiworldcore").resolve("pet_module");

    private static final Path CONFIG_FILE = CONFIG_DIR.resolve("settings.json");

    /** 备份间隔（秒），默认 600 秒（10 分钟） */
    private static int backupIntervalSeconds = 600;

    /** 备份保留数量，默认 50 份 */
    private static int backupRetentionCount = 50;

    /** 是否启用自动备份 */
    private static boolean autoBackupEnabled = true;

    private PetModuleConfig() {
    }

    public static void load() {
        DebugLogger.entering(MODULE, "load");
        try {
            if (Files.notExists(CONFIG_FILE)) {
                save();
                DebugLogger.info(MODULE, "宠物模块配置文件不存在，已创建默认配置: %s", CONFIG_FILE);
                return;
            }
            String json = Files.readString(CONFIG_FILE);
            @SuppressWarnings("null")
            ConfigData data = GSON.fromJson(json, ConfigData.class);
            if (data != null) {
                backupIntervalSeconds = data.backupIntervalSeconds;
                backupRetentionCount = data.backupRetentionCount;
                autoBackupEnabled = data.autoBackupEnabled;
            }
            DebugLogger.info(MODULE, "宠物模块配置已加载: interval=%ds, retention=%d, autoBackup=%s",
                    backupIntervalSeconds, backupRetentionCount, autoBackupEnabled);
        } catch (IOException e) {
            LOGGER.error("加载宠物模块配置失败", e);
        }
        DebugLogger.exiting(MODULE, "load");
    }

    public static void save() {
        DebugLogger.entering(MODULE, "save");
        try {
            Files.createDirectories(CONFIG_DIR);
            ConfigData data = new ConfigData(backupIntervalSeconds, backupRetentionCount, autoBackupEnabled);
            String json = GSON.toJson(data);
            Files.writeString(CONFIG_FILE, json);
            DebugLogger.info(MODULE, "宠物模块配置已保存");
        } catch (IOException e) {
            LOGGER.error("保存宠物模块配置失败", e);
        }
        DebugLogger.exiting(MODULE, "save");
    }

    // ===== Getters =====

    public static int getBackupIntervalSeconds() {
        return backupIntervalSeconds;
    }

    public static int getBackupRetentionCount() {
        return backupRetentionCount;
    }

    public static boolean isAutoBackupEnabled() {
        return autoBackupEnabled;
    }

    // ===== Setters =====

    public static void setBackupIntervalSeconds(int seconds) {
        backupIntervalSeconds = Math.max(60, seconds);
        save();
    }

    public static void setBackupRetentionCount(int count) {
        backupRetentionCount = Math.max(1, count);
        save();
    }

    public static void setAutoBackupEnabled(boolean enabled) {
        autoBackupEnabled = enabled;
        save();
    }

    // ===== 内部数据类 =====

    @SuppressWarnings("FieldMayBeFinal")
    private static class ConfigData {
        private int backupIntervalSeconds = 600;
        private int backupRetentionCount = 50;
        private boolean autoBackupEnabled = true;

        ConfigData(int backupIntervalSeconds, int backupRetentionCount, boolean autoBackupEnabled) {
            this.backupIntervalSeconds = backupIntervalSeconds;
            this.backupRetentionCount = backupRetentionCount;
            this.autoBackupEnabled = autoBackupEnabled;
        }
    }
}
