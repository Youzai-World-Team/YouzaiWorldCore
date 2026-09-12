package top.csituka.youzaiworldcore.client.jade;

import net.minecraft.resources.Identifier;
import snownee.jade.api.JadeIds;
import snownee.jade.api.config.IWailaConfig;
import snownee.jade.api.theme.IThemeHelper;
import snownee.jade.api.theme.Theme;
import top.csituka.youzaiworldcore.client.config.ClientExternalSettings;
import top.csituka.youzaiworldcore.client.config.YzuiThemeMode;
import top.csituka.youzaiworldcore.util.DebugLogger;

/**
 * 已确认 Jade 安装后才由 YzuiJadeStyleManager 加载。
 * 通过官方主题 API 同步明暗与效果预设，关闭 YZUI 时恢复此前的用户主题。
 */
public final class JadeThemeBridge {
    private static final String MODULE = "JadeThemeBridge";
    public static final Identifier YZUI_THEME_ID = id("yzui");
    private static final Identifier DEFAULT_THEME_ID = JadeIds.DEFAULT_THEME;
    private static boolean forcing;
    private static Identifier userThemeId;

    private JadeThemeBridge() { }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath("youzaiworldcore", path);
    }

    private static Identifier desiredThemeId() {
        String name = ClientExternalSettings.getYzuiTheme() == YzuiThemeMode.DARK ? "yzui_dark" : "yzui";
        return id(name + switch (ClientExternalSettings.getYzuiVisualStyle()) {
            case STANDARD -> "";
            case FROSTED -> "_frosted";
            case MINIMAL -> "_minimal";
        });
    }

    private static boolean isManagedTheme(Identifier theme) {
        return theme != null && theme.getNamespace().equals("youzaiworldcore")
                && (theme.getPath().equals("yzui") || theme.getPath().startsWith("yzui_"));
    }

    /** 资源就绪后即时切换；效果预设变化不会覆盖首次记录的原主题。 */
    public static void tick(boolean yzuiEnabled) {
        IThemeHelper helper = IThemeHelper.get();
        // 首个客户端 tick 可能早于资源加载完成。getTheme() 会触发 Jade 懒初始化，
        // 默认主题尚未就绪时其内部会抛空指针；开启、关闭及重载期间都先安全等待。
        // hasTheme() 只查询主题注册表，不会读取或应用主题。
        if (!helper.hasTheme(DEFAULT_THEME_ID)) return;
        IWailaConfig.Overlay overlay = IWailaConfig.get().overlay();
        Identifier active = effectiveThemeId(overlay);
        if (yzuiEnabled) {
            Identifier desired = desiredThemeId();
            if (!helper.hasTheme(desired)) return;
            if (!forcing) {
                userThemeId = isManagedTheme(active) ? DEFAULT_THEME_ID : active;
                forcing = true;
                DebugLogger.info(MODULE, "启用 YZUI Jade 主题，保留用户主题 %s", userThemeId);
            }
            if (!desired.equals(active)) {
                overlay.applyTheme(desired);
                DebugLogger.stateChange(MODULE, "jade_theme", "active_theme", active, desired);
            }
        } else if (forcing || isManagedTheme(active)) {
            Identifier restore = userThemeId == null ? DEFAULT_THEME_ID : userThemeId;
            if (!helper.hasTheme(restore)) restore = DEFAULT_THEME_ID;
            if (!helper.hasTheme(restore)) return;
            overlay.applyTheme(restore);
            IWailaConfig.get().save();
            forcing = false;
            userThemeId = null;
            DebugLogger.stateChange(MODULE, "jade_theme", "active_theme", active, restore);
        }
    }

    private static Identifier effectiveThemeId(IWailaConfig.Overlay overlay) {
        Theme theme = overlay.getTheme();
        return theme == null ? DEFAULT_THEME_ID : theme.fullId();
    }
}
