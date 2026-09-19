package top.csituka.youzaiworldcore.update;

/** 标题页正文的滚动状态；不依赖客户端类，绘制时发布实际视口供输入使用。 */
public final class TitleScreenScrollState {
    private static double scrollOffset;
    private static int contentHeight;
    private static int x, y, width, height;

    private TitleScreenScrollState() { }

    public static void reset() {
        scrollOffset = 0;
        contentHeight = width = height = 0;
    }

    /** 文本重新换行或窗口缩放后，把滚动量收敛到新的可见范围。 */
    public static void setViewport(int left, int top, int w, int h, int content) {
        x = left;
        y = top;
        width = Math.max(0, w);
        height = Math.max(0, h);
        contentHeight = Math.max(0, content);
        setScrollOffset(scrollOffset);
    }

    public static boolean contains(double mouseX, double mouseY) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    public static double getScrollOffset() { return scrollOffset; }
    public static int getContentHeight() { return contentHeight; }
    public static int getViewportHeight() { return height; }
    public static int getMaxScroll() { return Math.max(0, contentHeight - height); }

    public static void setScrollOffset(double offset) {
        scrollOffset = Double.isFinite(offset) ? Math.clamp(offset, 0, getMaxScroll()) : 0;
    }
}
