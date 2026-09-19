package top.csituka.youzaiworldcore.client.screen;

/** 屏幕级弹窗约定：弹窗保留前一窗口作为背景，普通页面直接使用全景图或游戏画面。 */
public interface YzuiPopupScreen {
    /** 同一个屏幕类承载普通页面和确认框时，可按当前内容覆盖此判定。 */
    default boolean isPopup() { return true; }
}
