package top.csituka.youzaiworldcore.client.config;

/**
 * 界面动画作用范围。
 *
 * <p>配置值写入客户端 {@code core_module.gui_animation_mode}。</p>
 */
public enum GuiAnimationMode {
    /** 关闭所有界面动画。 */
    OFF,
    /** 保留项目原有的控件、弹窗与局部界面动画。 */
    BASIC,
    /** 在基本动画之上启用所有页面的进入、退出和切换动画。 */
    FULL
}
