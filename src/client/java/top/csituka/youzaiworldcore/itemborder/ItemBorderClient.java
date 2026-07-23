package top.csituka.youzaiworldcore.itemborder;

import top.csituka.youzaiworldcore.util.DebugLogger;

/**
 * 物品边框功能的客户端初始化入口。
 * <p>
 * 在 {@code Client.onInitializeClient()} 中调用 {@link #initialize()}，
 * 完成配置加载。
 * </p>
 */
public final class ItemBorderClient {

    public static final String MODULE = "ItemBorderClient";

    private ItemBorderClient() {}

    /**
     * 初始化物品边框功能。
     * <p>
     * 执行步骤：
     * <ol>
     *   <li>加载 {@code config/youzaiworldcore/item_borders.json} 配置</li>
     * </ol>
     * </p>
     */
    public static void initialize() {
        DebugLogger.entering(MODULE, "initialize");

        DebugLogger.info(MODULE, "加载物品边框配置...");
        ItemBorderConfig.load();

        DebugLogger.info(MODULE, "物品边框功能初始化完成 (enabled=%s)",
                ItemBorderConfig.ENABLED);

        DebugLogger.exiting(MODULE, "initialize");
    }
}
