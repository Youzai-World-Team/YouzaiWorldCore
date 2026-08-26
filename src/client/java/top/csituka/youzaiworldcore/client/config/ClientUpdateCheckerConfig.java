package top.csituka.youzaiworldcore.client.config;

import top.csituka.youzaiworldcore.config.ConfigSection;
import top.csituka.youzaiworldcore.util.DebugLogger;

/**
 * 客户端更新检查配置。
 * <p>只读写 {@code yzwc/client/global_settings.json}，不依赖服务端配置或 Api 密钥。</p>
 */
public final class ClientUpdateCheckerConfig {

    private static final String MODULE = "ClientUpdateCheckerConfig";
    private static final boolean DEFAULT_ENABLED = true;
    private static final boolean DEFAULT_CHECK_ON_STARTUP = true;
    private static final boolean DEFAULT_SHOW_ON_TITLE_SCREEN = true;

    private static boolean enabled = DEFAULT_ENABLED;
    private static boolean checkOnStartup = DEFAULT_CHECK_ON_STARTUP;
    private static boolean showOnTitleScreen = DEFAULT_SHOW_ON_TITLE_SCREEN;

    private ClientUpdateCheckerConfig() {
    }

    public static boolean isEnabled() {
        return enabled;
    }

    public static boolean isCheckOnStartup() {
        return checkOnStartup;
    }

    public static boolean isShowOnTitleScreen() {
        return showOnTitleScreen;
    }

    /** 从客户端全局配置加载。 */
    public static void load() {
        DebugLogger.entering(MODULE, "load");
        ConfigSection section = ClientGlobalSettings.section(ClientGlobalSettings.UPDATE_MODULE);
        if (section.isEmpty()) {
            writeDefaults();
            DebugLogger.exiting(MODULE, "load", "created default");
            return;
        }
        enabled = section.getBoolean("enabled", DEFAULT_ENABLED);
        checkOnStartup = section.getBoolean("check_on_startup", DEFAULT_CHECK_ON_STARTUP);
        showOnTitleScreen = section.getBoolean("show_on_title_screen", DEFAULT_SHOW_ON_TITLE_SCREEN);
        DebugLogger.info(MODULE, "客户端更新检查配置已加载: enabled=%s, startup=%s, title=%s",
                enabled, checkOnStartup, showOnTitleScreen);
        DebugLogger.exiting(MODULE, "load");
    }

    /** 重置为默认值并写入客户端配置。 */
    public static void writeDefaults() {
        enabled = DEFAULT_ENABLED;
        checkOnStartup = DEFAULT_CHECK_ON_STARTUP;
        showOnTitleScreen = DEFAULT_SHOW_ON_TITLE_SCREEN;
        save();
    }

    /** 保存到客户端全局配置。 */
    public static void save() {
        ConfigSection section = ClientGlobalSettings.section(ClientGlobalSettings.UPDATE_MODULE);
        section.set("enabled", enabled);
        section.set("check_on_startup", checkOnStartup);
        section.set("show_on_title_screen", showOnTitleScreen);
        ClientGlobalSettings.save();
    }
}
