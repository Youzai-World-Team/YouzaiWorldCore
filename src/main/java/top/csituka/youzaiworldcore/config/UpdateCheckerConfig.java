package top.csituka.youzaiworldcore.config;


import top.csituka.youzaiworldcore.util.DebugLogger;

/**
 * 更新检查器配置（专用服务端生效，通过配置文件）。
 * <p>
 * 存放位置：{@code yzwc/server/config/global_settings.json} 的
 * {@code update_module} 分节。
 * </p>
 * <p>检查地址固定为 {@code https://api.mcyzw.top/api/update/core} 与
 * {@code core_force} 两个端点（见 {@code UpdateChecker}），不再支持自定义地址。</p>
 */
@SuppressWarnings({"null", "unused"})
public final class UpdateCheckerConfig {

    public static final String MODULE = "UpdateCheckerConfig";

    private static final DebugLogger.ModuleLogger LOGGER = DebugLogger.SERVER.module("UpdateCheckerConfig");

    private static final boolean DEFAULT_ENABLED = true;
    /** 默认值：各处检查 / 展示开关一律开启 */
    private static final boolean DEFAULT_TOGGLE = true;

    private static boolean enabled = DEFAULT_ENABLED;

    /** 服务器启动后是否检查（控制台日志） */
    private static boolean checkOnStartupServer = DEFAULT_TOGGLE;

    /** 客户端启动后是否检查（标题界面公告） */
    private static boolean checkOnStartupClient = DEFAULT_TOGGLE;

    /** 是否在服务端控制台输出更新横幅 */
    private static boolean announceToConsole = DEFAULT_TOGGLE;

    /** 是否在客户端标题界面右侧面板显示更新信息 */
    private static boolean showOnTitleScreen = DEFAULT_TOGGLE;

    private UpdateCheckerConfig() {
    }

    // ===== 读取 =====

    public static boolean isEnabled() {
        return enabled;
    }

    public static boolean isCheckOnStartupServer() {
        return checkOnStartupServer;
    }

    public static boolean isCheckOnStartupClient() {
        return checkOnStartupClient;
    }

    public static boolean isAnnounceToConsole() {
        return announceToConsole;
    }

    public static boolean isShowOnTitleScreen() {
        return showOnTitleScreen;
    }

    // ===== 写入 =====

    public static void setEnabled(boolean value) {
        DebugLogger.SERVER.entering(MODULE, "setEnabled", "value=" + value);
        if (enabled == value) {
            DebugLogger.SERVER.exiting(MODULE, "setEnabled", "no-change");
            return;
        }
        enabled = value;
        save();
        DebugLogger.SERVER.exiting(MODULE, "setEnabled", "1");
    }

    // ===== 持久化 =====

    /** 从全局配置的 {@code update_module} 分节加载（分节缺失则写入默认配置） */
    public static void load() {
        DebugLogger.SERVER.entering(MODULE, "load");
        ConfigSection section = GlobalSettings.section(GlobalSettings.UPDATE_MODULE);
        if (section.isEmpty()) {
            DebugLogger.SERVER.info(MODULE, "update_module 分节不存在，写入默认配置");
            save();
            DebugLogger.SERVER.exiting(MODULE, "load", "created default");
            return;
        }
        enabled = section.getBoolean("enabled", enabled);
        checkOnStartupServer = section.getBoolean("check_on_startup_server", checkOnStartupServer);
        checkOnStartupClient = section.getBoolean("check_on_startup_client", checkOnStartupClient);
        announceToConsole = section.getBoolean("announce_to_console", announceToConsole);
        showOnTitleScreen = section.getBoolean("show_on_title_screen", showOnTitleScreen);
        DebugLogger.SERVER.info(MODULE, "已加载配置: enabled={}, check_on_startup_server={}, check_on_startup_client={}", enabled, checkOnStartupServer, checkOnStartupClient);
        DebugLogger.SERVER.exiting(MODULE, "load");
    }

    /** 重新加载配置（供 /yzwc reload 调用） */
    public static void reload() {
        DebugLogger.SERVER.entering(MODULE, "reload");
        load();
        DebugLogger.SERVER.exiting(MODULE, "reload");
    }

    /** 重置为默认值并写入 {@code update_module} 分节（新开服 / 坏文件恢复用） */
    public static void writeDefaults() {
        enabled = DEFAULT_ENABLED;
        checkOnStartupServer = DEFAULT_TOGGLE;
        checkOnStartupClient = DEFAULT_TOGGLE;
        announceToConsole = DEFAULT_TOGGLE;
        showOnTitleScreen = DEFAULT_TOGGLE;
        save();
    }

    /** 保存当前配置到全局配置文件的 {@code update_module} 分节 */
    public static void save() {
        ConfigSection section = GlobalSettings.section(GlobalSettings.UPDATE_MODULE);
        section.set("enabled", enabled);
        section.set("check_on_startup_server", checkOnStartupServer);
        section.set("check_on_startup_client", checkOnStartupClient);
        section.set("announce_to_console", announceToConsole);
        section.set("show_on_title_screen", showOnTitleScreen);
        GlobalSettings.save();
    }
}
