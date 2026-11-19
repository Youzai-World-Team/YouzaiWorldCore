package top.csituka.youzaiworldcore.config;

import top.csituka.youzaiworldcore.util.DebugLogger;

/**
 * 服务端核心设置：yzwc/server/config/global_settings.json 的 core_module 分节。
 * dev_mode_enabled 与 log_level（0 关闭、1 基本、2 详细、3 调试）只控制服务端诊断日志。
 * 普通运维日志始终输出，/yzwc reload 会即时重新读取本端配置。
 */
public final class ServerExternalSettings {
    public static final String MODULE = "ServerExternalSettings";
    private static final boolean DEFAULT_DEV_MODE_ENABLED = false;
    private static final int DEFAULT_LOG_LEVEL = DebugLogger.LEVEL_OFF;
    private static boolean devModeEnabled = DEFAULT_DEV_MODE_ENABLED;
    private static int logLevel = DEFAULT_LOG_LEVEL;

    private ServerExternalSettings() { }

    public static boolean isDevModeEnabled() { return devModeEnabled; }
    public static int getLogLevel() { return logLevel; }

    /** 强类型读取并原子应用，只更新服务端；旧布尔配置不参与新级别计算。 */
    public static void load() {
        ConfigSection section = GlobalSettings.section(GlobalSettings.CORE_MODULE);
        devModeEnabled = section.getBoolean("dev_mode_enabled", DEFAULT_DEV_MODE_ENABLED);
        logLevel = section.getIntExact("log_level", DEFAULT_LOG_LEVEL, DebugLogger.LEVEL_OFF, DebugLogger.LEVEL_DEBUG);
        if (!section.has("dev_mode_enabled") || !section.has("log_level") || section.has("log_to_file")) save();
        syncToDebugLogger();
        DebugLogger.SERVER.debug(MODULE, "核心配置已加载：devMode={}, logLevel={}", devModeEnabled, logLevel);
    }

    /** 新配置与错误恢复都写出完整四档配置。 */
    public static void writeDefaults() {
        devModeEnabled = DEFAULT_DEV_MODE_ENABLED;
        logLevel = DEFAULT_LOG_LEVEL;
        save();
        syncToDebugLogger();
    }

    public static void save() {
        ConfigSection section = GlobalSettings.section(GlobalSettings.CORE_MODULE);
        section.set("dev_mode_enabled", devModeEnabled);
        section.set("log_level", logLevel);
        section.remove("log_to_file");
        GlobalSettings.save();
    }

    private static void syncToDebugLogger() {
        DebugLogger.SERVER.configure(devModeEnabled, logLevel);
    }
}
