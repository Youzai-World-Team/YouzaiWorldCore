package top.csituka.youzaiworldcore.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import top.csituka.youzaiworldcore.util.DebugLogger;

/**
 * 服务端外部设置持久化配置。
 * <p>
 * 存放位置：{@code yzwc/server/config/global_settings.json} 的
 * {@code core_module} 分节。
 * <p>
 * 当前支持设置：
 * <ul>
 *   <li>{@code dev_mode_enabled} — 启用开发者模式（服务端专用）</li>
 *   <li>{@code log_to_file} — 输出详细日志到 latest.log</li>
 * </ul>
 */
@SuppressWarnings({"null", "unused"})
public final class ServerExternalSettings {

    public static final String MODULE = "ServerExternalSettings";

    private static final Logger LOGGER = LoggerFactory.getLogger("YouzaiWorldCore/ServerExternalSettings");

    /** 默认值：关闭开发者模式 */
    private static final boolean DEFAULT_DEV_MODE_ENABLED = false;
    /** 默认值：不输出详细日志 */
    private static final boolean DEFAULT_LOG_TO_FILE = false;

    private static boolean devModeEnabled = DEFAULT_DEV_MODE_ENABLED;
    private static boolean logToFile = DEFAULT_LOG_TO_FILE;

    private ServerExternalSettings() {}

    // ===== 读取 =====

    public static boolean isDevModeEnabled() {
        return devModeEnabled;
    }

    public static boolean isLogToFile() {
        return logToFile;
    }

    // ===== 持久化 =====

    /** 从全局配置加载并同步到 DebugLogger（分节缺失则写入默认值） */
    public static void load() {
        ConfigSection section = GlobalSettings.section(GlobalSettings.CORE_MODULE);
        if (section.isEmpty()) {
            save();
            syncToDebugLogger();
            return;
        }
        devModeEnabled = section.getBoolean("dev_mode_enabled", devModeEnabled);
        logToFile = section.getBoolean("log_to_file", logToFile);
        syncToDebugLogger();
        LOGGER.debug("核心模块配置已加载: devMode={}, logToFile={}", devModeEnabled, logToFile);
    }

    /** 重置为默认值并写入 {@code core_module} 分节（新开服 / 坏文件恢复用） */
    public static void writeDefaults() {
        devModeEnabled = DEFAULT_DEV_MODE_ENABLED;
        logToFile = DEFAULT_LOG_TO_FILE;
        save();
        syncToDebugLogger();
    }

    /** 保存配置到全局配置文件 */
    public static void save() {
        ConfigSection section = GlobalSettings.section(GlobalSettings.CORE_MODULE);
        section.set("dev_mode_enabled", devModeEnabled);
        section.set("log_to_file", logToFile);
        GlobalSettings.save();
    }

    /** 将当前设置同步到 DebugLogger */
    private static void syncToDebugLogger() {
        DebugLogger.setDevModeEnabled(devModeEnabled);
        DebugLogger.setLogLevel(logToFile ? 1 : 0);
    }
}
