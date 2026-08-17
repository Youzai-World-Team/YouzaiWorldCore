package top.csituka.youzaiworldcore.client.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import top.csituka.youzaiworldcore.config.ConfigSection;
import top.csituka.youzaiworldcore.util.DebugLogger;

/**
 * 客户端外部设置持久化配置。
 * <p>
 * 存放位置：{@code yzwc/client/global_settings.json} 的 {@code core_module} 分节。
 * <p>
 * 保存设置：
 * <ul>
 *   <li>{@code dev_mode_enabled} — 启用开发者模式</li>
 *   <li>{@code log_level} — 日志输出丰富度（0=关闭, 1=基本, 2=详细, 3=调试）</li>
 *   <li>{@code debug_mode_type} — 调试方式 ("embedded" 内嵌服务端 / "dedicated" 专用服务端)</li>
 *   <li>{@code debug_address} — 调试服务器地址（专用服务端）</li>
 *   <li>{@code debug_port} — 调试服务器端口（专用服务端）</li>
 *   <li>{@code ignored_update_version} — 已忽略提示的更新版本号</li>
 *   <li>{@code yzui_enabled} — 是否启用 YZUI 自定义 UI 样式</li>
 *   <li>{@code cosmetic_enabled} — 是否启用自定义皮肤与披风</li>
 *   <li>{@code auto_skip_experimental_warning} — 是否自动跳过实验性设置警告屏幕</li>
 *   <li>{@code experimental_warning_skip_action} — 自动跳过时的操作（skip / backup）</li>
 * </ul>
 */
public final class ClientExternalSettings {

    private static final String MODULE = "ClientExternalSettings";

    private static final Logger LOGGER = LoggerFactory.getLogger("YouzaiWorldCore/ClientExternalSettings");

    /** 日志级别上限（3=调试） */
    private static final int MAX_LOG_LEVEL = 3;

    // ===== 默认值 =====
    private static final boolean DEFAULT_DEV_MODE_ENABLED = false;
    private static final int DEFAULT_LOG_LEVEL = 0;
    private static final String DEFAULT_DEBUG_MODE_TYPE = "embedded";
    private static final String DEFAULT_DEBUG_ADDRESS = "localhost";
    private static final String DEFAULT_DEBUG_PORT = "25565";
    private static final String DEFAULT_IGNORED_UPDATE_VERSION = "";
    private static final boolean DEFAULT_YZUI_ENABLED = true;
    private static final boolean DEFAULT_COSMETIC_ENABLED = true;
    private static final boolean DEFAULT_AUTO_SKIP_EXPERIMENTAL_WARNING = false;
    private static final String DEFAULT_EXPERIMENTAL_WARNING_SKIP_ACTION = "skip";

    // ===== 运行时状态 =====
    private static boolean devModeEnabled = DEFAULT_DEV_MODE_ENABLED;
    /** 日志输出丰富度：0=关闭, 1=基本, 2=详细, 3=调试 */
    private static int logLevel = DEFAULT_LOG_LEVEL;
    private static String debugModeType = DEFAULT_DEBUG_MODE_TYPE; // "embedded" 或 "dedicated"
    private static String debugAddress = DEFAULT_DEBUG_ADDRESS;
    private static String debugPort = DEFAULT_DEBUG_PORT;

    /** 已忽略提示的更新版本号（非空表示不再在标题界面提示该版本）；仅非强制更新时可忽略 */
    private static String ignoredUpdateVersion = DEFAULT_IGNORED_UPDATE_VERSION;

    /** 是否启用 YZUI（自定义 UI 样式），关闭则回退到原版 UI 供资源包替换 */
    private static boolean yzuiEnabled = DEFAULT_YZUI_ENABLED;
    private static boolean cosmeticEnabled = DEFAULT_COSMETIC_ENABLED;

    /** 是否自动跳过"使用实验性设置的世界不受支持"屏幕 */
    private static boolean autoSkipExperimentalWarning = DEFAULT_AUTO_SKIP_EXPERIMENTAL_WARNING;

    /** 自动跳过时执行的操作：{@code "skip"} = 我知道我在做什么（不备份），{@code "backup"} = 创建备份并进入 */
    private static String experimentalWarningSkipAction = DEFAULT_EXPERIMENTAL_WARNING_SKIP_ACTION;

    private ClientExternalSettings() {}

    // ===== 读取 =====

    public static boolean isDevModeEnabled() {
        return devModeEnabled;
    }

    /** @deprecated 请改用 {@link #getLogLevel()} */
    @Deprecated
    public static boolean isLogToFile() {
        return logLevel > 0;
    }

    /** 获取日志输出丰富度等级（0=关闭, 1=基本, 2=详细, 3=调试） */
    public static int getLogLevel() {
        return logLevel;
    }

    public static String getDebugModeType() {
        return debugModeType;
    }

    public static String getDebugAddress() {
        return debugAddress;
    }

    public static String getDebugPort() {
        return debugPort;
    }

    /** @return 已忽略提示的更新版本号（空字符串表示未忽略任何版本） */
    public static String getIgnoredUpdateVersion() {
        return ignoredUpdateVersion;
    }

    /** @return 是否启用 YZUI 自定义 UI 样式 */
    public static boolean isYzuiEnabled() {
        return yzuiEnabled;
    }

    /** @return 是否启用自定义皮肤与披风 */
    public static boolean isCosmeticEnabled() {
        return cosmeticEnabled;
    }

    /** @return 是否自动跳过实验性设置警告屏幕 */
    public static boolean isAutoSkipExperimentalWarning() {
        return autoSkipExperimentalWarning;
    }

    /** @return 自动跳过时执行的操作（"skip" 或 "backup"） */
    public static String getExperimentalWarningSkipAction() {
        return experimentalWarningSkipAction;
    }

    /** @return 自动跳过时是否应创建备份 */
    public static boolean isExperimentalWarningSkipBackup() {
        return "backup".equals(experimentalWarningSkipAction);
    }

    /** 设置被忽略的更新版本号（空值将忽略为 ""）并持久化 */
    public static void setIgnoredUpdateVersion(String value) {
        ignoredUpdateVersion = (value == null) ? "" : value;
        save();
    }

    // ===== 写入 =====

    public static void setDevModeEnabled(boolean value) {
        devModeEnabled = value;
        DebugLogger.setDevModeEnabled(value);
        save();
    }

    /** @deprecated 请改用 {@link #setLogLevel(int)} */
    @Deprecated
    public static void setLogToFile(boolean value) {
        setLogLevel(value ? 1 : 0);
    }

    /** 设置日志输出丰富度等级（0=关闭, 1=基本, 2=详细, 3=调试） */
    public static void setLogLevel(int level) {
        logLevel = Math.max(0, Math.min(MAX_LOG_LEVEL, level));
        DebugLogger.setLogLevel(logLevel);
        // 单人模式集成服务器：只要日志级别 > 0 就开启服务端日志输出
        top.csituka.youzaiworldcore.YouzaiworldCore.logToFile = (logLevel > 0);
        save();
    }

    public static void setDebugModeType(String value) {
        debugModeType = value;
        save();
    }

    public static void setDebugAddress(String value) {
        debugAddress = value;
        save();
    }

    public static void setDebugPort(String value) {
        debugPort = value;
        save();
    }

    /** 设置 YZUI 启用状态并持久化 */
    public static void setYzuiEnabled(boolean value) {
        yzuiEnabled = value;
        DebugLogger.info(MODULE, "YZUI 已" + (value ? "启用" : "禁用"));
        save();
    }

    /** 设置自定义皮肤与披风启用状态并持久化。 */
    public static void setCosmeticEnabled(boolean value) {
        cosmeticEnabled = value;
        DebugLogger.info(MODULE, "自定义皮肤与披风已" + (value ? "启用" : "禁用"));
        save();
    }

    /** 设置是否自动跳过实验性设置警告并持久化 */
    public static void setAutoSkipExperimentalWarning(boolean value) {
        autoSkipExperimentalWarning = value;
        DebugLogger.info(MODULE, "自动跳过实验性设置警告已" + (value ? "启用" : "禁用"));
        save();
    }

    /** 设置自动跳过时的操作（"skip" 或 "backup"）并持久化 */
    public static void setExperimentalWarningSkipAction(String value) {
        experimentalWarningSkipAction = ("backup".equals(value)) ? "backup" : "skip";
        DebugLogger.info(MODULE, "自动跳过操作已设为：" + experimentalWarningSkipAction);
        save();
    }

    // ===== 持久化 =====

    /** 从客户端配置的 {@code core_module} 分节加载（分节缺失则写入默认值） */
    public static void load() {
        ConfigSection section = ClientGlobalSettings.section(ClientGlobalSettings.CORE_MODULE);
        if (section.isEmpty()) {
            save();
            syncRuntimeFlags();
            return;
        }

        devModeEnabled = section.getBoolean("dev_mode_enabled", devModeEnabled);
        logLevel = section.getInt("log_level", logLevel, 0, MAX_LOG_LEVEL);
        debugModeType = section.getString("debug_mode_type", debugModeType);
        debugAddress = section.getString("debug_address", debugAddress);
        debugPort = section.getString("debug_port", debugPort);
        ignoredUpdateVersion = section.getString("ignored_update_version", ignoredUpdateVersion);
        yzuiEnabled = section.getBoolean("yzui_enabled", yzuiEnabled);
        cosmeticEnabled = section.getBoolean("cosmetic_enabled", cosmeticEnabled);
        autoSkipExperimentalWarning =
                section.getBoolean("auto_skip_experimental_warning", autoSkipExperimentalWarning);

        String action = section.getString("experimental_warning_skip_action", experimentalWarningSkipAction);
        if (!"skip".equals(action) && !"backup".equals(action)) {
            section.fail("experimental_warning_skip_action",
                    "取值 \"" + action + "\" 不是合法选项，允许的取值：skip / backup");
        }
        experimentalWarningSkipAction = action;

        if (logLevel > 0) {
            LOGGER.info("已从 {} 加载客户端外部设置", ClientGlobalSettings.file());
        }
        syncRuntimeFlags();
    }

    /** 重置为默认值并写入 {@code core_module} 分节（首次安装 / 坏文件恢复用） */
    public static void writeDefaults() {
        devModeEnabled = DEFAULT_DEV_MODE_ENABLED;
        logLevel = DEFAULT_LOG_LEVEL;
        debugModeType = DEFAULT_DEBUG_MODE_TYPE;
        debugAddress = DEFAULT_DEBUG_ADDRESS;
        debugPort = DEFAULT_DEBUG_PORT;
        ignoredUpdateVersion = DEFAULT_IGNORED_UPDATE_VERSION;
        yzuiEnabled = DEFAULT_YZUI_ENABLED;
        cosmeticEnabled = DEFAULT_COSMETIC_ENABLED;
        autoSkipExperimentalWarning = DEFAULT_AUTO_SKIP_EXPERIMENTAL_WARNING;
        experimentalWarningSkipAction = DEFAULT_EXPERIMENTAL_WARNING_SKIP_ACTION;
        save();
        syncRuntimeFlags();
    }

    /** 保存配置到客户端配置文件的 {@code core_module} 分节 */
    public static void save() {
        ConfigSection section = ClientGlobalSettings.section(ClientGlobalSettings.CORE_MODULE);
        section.set("dev_mode_enabled", devModeEnabled);
        section.set("log_level", logLevel);
        section.set("debug_mode_type", debugModeType);
        section.set("debug_address", debugAddress);
        section.set("debug_port", debugPort);
        section.set("ignored_update_version", ignoredUpdateVersion);
        section.set("yzui_enabled", yzuiEnabled);
        section.set("cosmetic_enabled", cosmeticEnabled);
        section.set("auto_skip_experimental_warning", autoSkipExperimentalWarning);
        section.set("experimental_warning_skip_action", experimentalWarningSkipAction);
        ClientGlobalSettings.save();
    }

    /** 把当前设置同步到 DebugLogger 与内嵌服务端的全局标志 */
    private static void syncRuntimeFlags() {
        DebugLogger.setDevModeEnabled(devModeEnabled);
        DebugLogger.setLogLevel(logLevel);
        top.csituka.youzaiworldcore.YouzaiworldCore.logToFile = (logLevel > 0);
    }
}
