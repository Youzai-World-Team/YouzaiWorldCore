package top.csituka.youzaiworldcore.client.jade;

import net.fabricmc.loader.api.FabricLoader;
import top.csituka.youzaiworldcore.client.config.ClientExternalSettings;
import top.csituka.youzaiworldcore.util.DebugLogger;

/**
 * YZUI × Jade 样式桥接的开关守护与入口。
 *
 * <p>
 * 职责：在「Jade 已安装」的前提下，把「使用 YZUI」开关状态同步给
 * {@link JadeThemeBridge}（真正的主题切换逻辑，引用了 {@code snownee.jade.*}）。
 * </p>
 *
 * <p>
 * <b>为什么分两个类：</b>本类<b>不引用任何 Jade 类型</b>，即使玩家未安装 Jade，
 * 本类也能安全加载（避免 NoClassDefFoundError / 校验失败）；只有确认
 * {@code isModLoaded("jade")} 之后才会走到 {@link JadeThemeBridge#tick(boolean)}，
 * 此时 Jade 的类才被真正加载。
 * </p>
 */
public final class YzuiJadeStyleManager {

    private static final String MODULE = "YzuiJadeStyleManager";

    /** Jade 的 mod id（snownee.jade） */
    private static final String JADE_MOD_ID = "jade";

    /** 是否已完成 Jade 加载状态探测（只探测一次） */
    private static boolean loadedChecked;

    /** Jade 是否已安装 */
    private static boolean jadeLoaded;

    private YzuiJadeStyleManager() {
    }

    /**
     * 客户端每 tick 调用。Jade 未安装时是近乎零开销的短路返回。
     */
    public static void onClientTick() {
        if (!isJadeLoaded()) {
            return;
        }
        boolean yzuiEnabled = ClientExternalSettings.isYzuiEnabled();
        JadeThemeBridge.tick(yzuiEnabled);
    }

    /**
     * 客户端初始化时调用：探测 Jade 加载状态并输出一条诊断日志。
     * 探测结果缓存，后续 {@link #onClientTick()} 复用。
     */
    public static void initialize() {
        isJadeLoaded();
    }

    /** 探测 Jade 是否加载（结果缓存）。 */
    private static boolean isJadeLoaded() {
        if (!loadedChecked) {
            loadedChecked = true;
            jadeLoaded = FabricLoader.getInstance().isModLoaded(JADE_MOD_ID);
            DebugLogger.info(MODULE, "Jade 模组%s，YZUI 样式桥接%s",
                    jadeLoaded ? "已加载" : "未加载",
                    jadeLoaded ? "已启用" : "跳过（不影响其他功能）");
        }
        return jadeLoaded;
    }
}
