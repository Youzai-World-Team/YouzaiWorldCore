package top.csituka.youzaiworldcore.client.render;

/** 小窗口的等比坐标变换；布局和鼠标使用同一个比例，避免点击位置漂移。 */
public record YzuiLayout(int width, int height, float scale) {
    public static YzuiLayout fit(int width, int height, int minimumWidth, int minimumHeight) {
        int w = Math.max(1, width), h = Math.max(1, height);
        float scale = Math.min(1f, Math.min(w / (float) minimumWidth, h / (float) minimumHeight));
        return new YzuiLayout(Math.round(w / scale), Math.round(h / scale), scale);
    }

    public double toLogical(double coordinate) { return coordinate / scale; }
}
