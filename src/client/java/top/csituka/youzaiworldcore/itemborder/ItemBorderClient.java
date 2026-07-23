package top.csituka.youzaiworldcore.itemborder;

import top.csituka.youzaiworldcore.util.DebugLogger;

/**
 * 物品边框功能的客户端初始化入口。
 * <p>
 * 在 {@code Client.onInitializeClient()} 中调用 {@link #initialize()}。
 * 所有配置均为硬编码常量，无需从文件加载。
 * </p>
 */
public final class ItemBorderClient {

    public static final String MODULE = "ItemBorderClient";

    private ItemBorderClient() {}

    /**
     * 初始化物品边框功能。
     * <p>
     * 所有配置均为硬编码常量，无需加载配置文件。
     * </p>
     */
    public static void initialize() {
        DebugLogger.entering(MODULE, "initialize");

        DebugLogger.info(MODULE, "物品边框功能已就绪 (enabled=%s)",
                ItemBorderConfig.ENABLED);

        DebugLogger.exiting(MODULE, "initialize");
    }
}
