package top.csituka.youzaiworldcore.client.jade;

import net.minecraft.resources.Identifier;
import snownee.jade.api.config.IWailaConfig;
import snownee.jade.api.theme.IThemeHelper;
import snownee.jade.api.theme.Theme;
import top.csituka.youzaiworldcore.util.DebugLogger;

/**
 * Jade 主题桥接：通过 Jade 官方主题 API 注入 YZUI 样式，不修改 Jade 任何源码。
 *
 * <h3>机制</h3>
 * <ol>
 * <li>本模组在 {@code assets/youzaiworldcore/jade_themes/yzui.json} 注册了一个
 * 名为 {@code youzaiworldcore:yzui} 的 Jade 数据驱动主题（纯资源注入，无代码）；</li>
 * <li>本类在「使用 YZUI」开关开启时调用
 * {@link IWailaConfig.Overlay#applyTheme(Identifier)} 把 Jade 的活动主题
 * 切换为该主题；关闭时调用 {@link IWailaConfig#save()} 前先恢复用户原主题。</li>
 * </ol>
 *
 * <p>
 * 为什么用配置级 {@code applyTheme} 而不是 {@link IThemeHelper#setThemeOverride(Theme)}：
 * Jade 的 {@code WailaTickHandler} 每 tick 构建提示框时都会主动 set/clear
 * themeOverride（先置为内容声明的主题、再在方法末尾清空），override 是瞬态机制；
 * 而根提示框与文字渲染分别读取 {@code IThemeHelper.get().theme().tooltipStyle} 与
 * {@code IWailaConfig.get().overlay().getTheme().text.shadow()}——只有把活动主题
 * 写进配置（{@code applyTheme}）才能同时覆盖两条读取路径，且能跨 tick 持续生效。
 * </p>
 *
 * <p>
 * 本类引用了 {@code snownee.jade.*} 类型，仅允许在确认 Jade 已加载后由
 * {@link YzuiJadeStyleManager} 调用，避免未安装 Jade 时触发类加载失败。
 * </p>
 */
public final class JadeThemeBridge {

    private static final String MODULE = "JadeThemeBridge";

    /** 本模组注册的 YZUI 主题 ID（与 jade_themes/yzui.json 对应） */
    public static final Identifier YZUI_THEME_ID = Identifier.fromNamespaceAndPath("youzaiworldcore", "yzui");

    /** Jade 自带默认主题（暗色），用于恢复兜底 */
    private static final Identifier DEFAULT_THEME_ID = Identifier.fromNamespaceAndPath("jade", "dark");

    /** 是否正处于「由本模组强制 YZUI 主题」状态 */
    private static boolean forcing;

    /** 首次强制时捕获的用户原主题 ID（关闭时恢复；null 视为未捕获） */
    private static Identifier userThemeId;

    private JadeThemeBridge() {
    }

    /**
     * 每 tick 调用：按 YZUI 开关状态维持/切换 Jade 活动主题。
     *
     * @param yzuiEnabled 是否启用 YZUI（来自 {@code ClientExternalSettings}）
     */
    @SuppressWarnings("null")
    public static void tick(boolean yzuiEnabled) {
        IThemeHelper helper = IThemeHelper.get();
        // 主题数据尚未加载（资源重载未完成 / 未进入存档）时直接跳过，
        // 等资源重载完成后下一次 tick 自然生效
        if (!helper.hasTheme(YZUI_THEME_ID)) {
            return;
        }
        IWailaConfig.Overlay overlay = IWailaConfig.get().overlay();
        if (yzuiEnabled) {
            // —— YZUI 开启：确保活动主题为 YZUI ——
            if (!forcing) {
                forcing = true;
                userThemeId = captureUserTheme(helper);
                DebugLogger.info(MODULE, "YZUI 开关开启，捕获用户原主题 %s，即将切换到 YZUI 主题",
                        userThemeId);
            }
            if (!YZUI_THEME_ID.equals(effectiveThemeId(helper))) {
                overlay.applyTheme(YZUI_THEME_ID);
                DebugLogger.info(MODULE, "已把 Jade 活动主题切换为 YZUI 主题");
            }
        } else {
            // —— YZUI 关闭：恢复原状 / 自愈残留 ——
            if (forcing) {
                forcing = false;
                Identifier restore = (userThemeId != null) ? userThemeId : DEFAULT_THEME_ID;
                overlay.applyTheme(restore);
                IWailaConfig.get().save();
                DebugLogger.stateChange(MODULE, "jade_theme", "active_theme",
                        YZUI_THEME_ID, restore);
                DebugLogger.info(MODULE, "YZUI 开关关闭，已恢复用户原主题 %s", restore);
                userThemeId = null;
            } else if (YZUI_THEME_ID.equals(effectiveThemeId(helper))) {
                // 自愈：活动主题残留为 YZUI（上次强制期间若发生主题集变更，Jade 会
                // 把当前活动主题写回配置文件），此处恢复默认主题并落盘
                overlay.applyTheme(DEFAULT_THEME_ID);
                IWailaConfig.get().save();
                DebugLogger.info(MODULE, "检测到活动主题残留为 YZUI，已恢复 Jade 默认主题并保存");
            }
        }
    }

    /**
     * 捕获用户当前主题。若当前恰为 YZUI（可能是上次残留），则视为用户未自定义，
     * 兜底为 Jade 默认主题。
     */
    @SuppressWarnings("null")
    private static Identifier captureUserTheme(IThemeHelper helper) {
        Identifier id;
        try {
            id = effectiveThemeId(helper);
        } catch (Exception e) {
            return DEFAULT_THEME_ID;
        }
        return YZUI_THEME_ID.equals(id) ? DEFAULT_THEME_ID : id;
    }

    /** 读取当前生效主题 ID；未就绪时兜底默认主题。 */
    private static Identifier effectiveThemeId(IThemeHelper helper) {
        Theme theme = helper.theme();
        return theme == null ? DEFAULT_THEME_ID : theme.fullId();
    }
}
