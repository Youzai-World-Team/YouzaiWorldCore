package top.csituka.youzaiworldcore.highlightitem;

import top.csituka.youzaiworldcore.util.DebugLogger;


/**
 * 高亮物品功能客户端入口类（参考 HighLightItem）。
 * <p>
 * 仅持有配置单例与日志器；真正的初始化与 Tick 逻辑见 {@link HighlightItemClient}。
 */
public class HighlightItem {
    public static final DebugLogger.ModuleLogger LOGGER = DebugLogger.CLIENT.module("HighlightItem");

    /** 配置单例，由 {@link HighlightItemClient#initialize()} 初始化。 */
    public static Configurator configurator;
}
