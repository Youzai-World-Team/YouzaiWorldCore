package top.csituka.youzaiworldcore.update;

/**
 * 标题界面右面板更新信息块的滚动状态，在 {@code MouseHandlerScrollMixin} 和
 * {@code TitleScreenMixin} 之间共享。
 * <p>
 * {@code MouseHandlerScrollMixin} 在滚轮事件中更新 {@code scrollOffset}，
 * {@code TitleScreenMixin} 在每帧渲染中读取并应用。
 * </p>
 */
public final class TitleScreenScrollState {

    private TitleScreenScrollState() {}

    /** 当前垂直滚动偏移量（像素） */
    private static double scrollOffset = 0.0;

    /** 内容总高度（由 TitleScreenMixin.drawPanelContent 计算并更新） */
    private static int contentHeight = 130;

    /** 面板可视高度（固定 130） */
    public static final int PANEL_HEIGHT = 130;

    public static double getScrollOffset() {
        return scrollOffset;
    }

    public static void setScrollOffset(double offset) {
        scrollOffset = Math.max(0, offset);
    }

    public static int getContentHeight() {
        return contentHeight;
    }

    public static void setContentHeight(int height) {
        contentHeight = height;
    }

    /** @return 最大滚动量（0 表示无需滚动） */
    public static int getMaxScroll() {
        return Math.max(0, contentHeight - PANEL_HEIGHT);
    }
}
