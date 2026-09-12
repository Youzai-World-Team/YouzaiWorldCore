package top.csituka.youzaiworldcore.client.config;

/**
 * 界面动画作用范围。
 *
 * <p>配置值写入客户端 {@code core_module.gui_animation_mode}。</p>
 */
public enum GuiAnimationMode {
    /** 关闭所有界面动画。 */
    OFF,
    /** 模组页面、控件与弹窗的统一动画。 */
    BASIC,
    /** 在基础范围之上，为原版页面也启用进入、退出和切换动画。 */
    FULL
}
