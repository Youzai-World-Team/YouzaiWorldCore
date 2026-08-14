package top.csituka.youzaiworldcore.pet.config;

import top.csituka.youzaiworldcore.config.ConfigSection;
import top.csituka.youzaiworldcore.config.GlobalSettings;
import top.csituka.youzaiworldcore.util.DebugLogger;

/**
 * 宠物模块配置 — 备份间隔及相关设置。
 * <p>
 * 存放位置：{@code yzwc/server/config/global_settings.json} 的
 * {@code pet_module} 分节。
 * </p>
 * <p>
 * 宠物的备份压缩包落在 {@code yzwc/server/backup/pet_module/}，
 * 见 {@link top.csituka.youzaiworldcore.pet.PetBackupManager}。
 * </p>
 */
public final class PetModuleConfig {

    private static final String MODULE = "PetModuleConfig";

    /** 备份间隔下限（秒） */
    public static final int MIN_BACKUP_INTERVAL_SECONDS = 60;

    /** 默认值：每 600 秒（10 分钟）备份一次 */
    private static final int DEFAULT_BACKUP_INTERVAL_SECONDS = 600;
    /** 默认值：保留 50 份备份 */
    private static final int DEFAULT_BACKUP_RETENTION_COUNT = 50;
    /** 默认值：启用自动备份 */
    private static final boolean DEFAULT_AUTO_BACKUP_ENABLED = true;

    /** 备份间隔（秒），默认 600 秒（10 分钟） */
    private static int backupIntervalSeconds = DEFAULT_BACKUP_INTERVAL_SECONDS;

    /** 备份保留数量，默认 50 份 */
    private static int backupRetentionCount = DEFAULT_BACKUP_RETENTION_COUNT;

    /** 是否启用自动备份 */
    private static boolean autoBackupEnabled = DEFAULT_AUTO_BACKUP_ENABLED;

    private PetModuleConfig() {
    }

    /** 从全局配置的 {@code pet_module} 分节加载（分节缺失则写入默认配置） */
    public static void load() {
        DebugLogger.entering(MODULE, "load");
        ConfigSection section = GlobalSettings.section(GlobalSettings.PET_MODULE);
        if (section.isEmpty()) {
            save();
            DebugLogger.info(MODULE, "pet_module 分节不存在，已写入默认配置");
            DebugLogger.exiting(MODULE, "load", "created default");
            return;
        }

        backupIntervalSeconds = section.getInt("backup_interval_seconds", backupIntervalSeconds,
                MIN_BACKUP_INTERVAL_SECONDS, Integer.MAX_VALUE);
        backupRetentionCount = section.getInt("backup_retention_count", backupRetentionCount,
                1, Integer.MAX_VALUE);
        autoBackupEnabled = section.getBoolean("auto_backup_enabled", autoBackupEnabled);

        DebugLogger.info(MODULE, "宠物模块配置已加载: interval=%ds, retention=%d, autoBackup=%s",
                backupIntervalSeconds, backupRetentionCount, autoBackupEnabled);
        DebugLogger.exiting(MODULE, "load");
    }

    /** 重置为默认值并写入 {@code pet_module} 分节（新开服 / 坏文件恢复用） */
    public static void writeDefaults() {
        backupIntervalSeconds = DEFAULT_BACKUP_INTERVAL_SECONDS;
        backupRetentionCount = DEFAULT_BACKUP_RETENTION_COUNT;
        autoBackupEnabled = DEFAULT_AUTO_BACKUP_ENABLED;
        save();
    }

    /** 保存当前配置到全局配置文件的 {@code pet_module} 分节 */
    public static void save() {
        ConfigSection section = GlobalSettings.section(GlobalSettings.PET_MODULE);
        section.set("backup_interval_seconds", backupIntervalSeconds);
        section.set("backup_retention_count", backupRetentionCount);
        section.set("auto_backup_enabled", autoBackupEnabled);
        GlobalSettings.save();
        DebugLogger.info(MODULE, "宠物模块配置已保存");
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
        backupIntervalSeconds = Math.max(MIN_BACKUP_INTERVAL_SECONDS, seconds);
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
}
