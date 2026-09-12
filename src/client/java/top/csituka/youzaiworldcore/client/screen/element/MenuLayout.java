package top.csituka.youzaiworldcore.client.screen.element;

/** Shift+F 菜单共用的内容边界与整图网格；不依赖游戏类，便于验证缩放后的布局。 */
public record MenuLayout(int width, int height) {
    public static final int CONTENT_TOP = 76;
    public static final int FOOTER_MARGIN = 28;
    public static final int NAVIGATION_SIZE = 24;
    public static final int TILE_ASPECT = 2;
    private static final int GRID_GAP = 10;

    public int shellLeft() { return (width - Math.min(720, width - 20)) / 2; }
    public int backX() { return shellLeft() + 14; }
    public int closeX() { return width - shellLeft() - 14 - NAVIGATION_SIZE; }
    public int titleX() { return backX() + NAVIGATION_SIZE + 12; }
    public int titleWidth() { return closeX() - titleX() - 12; }
    public int contentBottom() { return height - FOOTER_MARGIN; }
    public int contentHeight() { return contentBottom() - CONTENT_TOP; }
    public int contentWidth(int maximum) { return Math.min(maximum, width - 64); }
    public int left(int maximum) { return (width - contentWidth(maximum)) / 2; }
    public int centeredTop(int contentHeight) {
        return CONTENT_TOP + Math.max(0, (contentHeight() - contentHeight) / 2);
    }

    /** 所有导航图片保持 2:1；同时约束行宽和总高度，末行不足时居中。 */
    public Grid navigation(int count) {
        int columns = Math.min(count, width >= 620 ? 4 : 3);
        int rows = (count + columns - 1) / columns;
        int tileHeight = Math.max(1, Math.min(76, Math.min(
                (contentWidth(640) - (columns - 1) * GRID_GAP) / (columns * TILE_ASPECT),
                (contentHeight() - (rows - 1) * GRID_GAP) / rows)));
        int gridHeight = rows * tileHeight + (rows - 1) * GRID_GAP;
        return new Grid(width, centeredTop(gridHeight), count, columns, tileHeight * TILE_ASPECT,
                tileHeight, GRID_GAP);
    }

    public record Rect(int x, int y, int width, int height) {
        public int right() { return x + width; }
        public int bottom() { return y + height; }
    }

    public record Grid(int canvasWidth, int top, int count, int columns, int tileWidth, int tileHeight, int gap) {
        public Rect tile(int index) {
            if (index < 0 || index >= count) throw new IndexOutOfBoundsException(index);
            int row = index / columns;
            int rowCount = Math.min(columns, count - row * columns);
            int left = (canvasWidth - (rowCount * tileWidth + (rowCount - 1) * gap)) / 2;
            return new Rect(left + index % columns * (tileWidth + gap),
                    top + row * (tileHeight + gap), tileWidth, tileHeight);
        }
    }
}
