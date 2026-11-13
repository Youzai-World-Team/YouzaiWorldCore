package top.csituka.youzaiworldcore.client.map;

import net.minecraft.client.gui.GuiGraphicsExtractor;

/** 地图视口裁切、标记与导航共用的几何工具。 */
public final class MapShapes {
    private MapShapes() { }

    /** 测试圆角视口，标记与输入共用同一边界。 */
    public static boolean contains(MapView view, double x, double y, int radius, double margin) {
        double w = view.width(), h = view.height();
        if (x < margin || y < margin || x >= w - margin || y >= h - margin) return false;
        double r = Math.max(0, radius - margin);
        double dx = x - Math.clamp(x, margin + r, w - margin - r);
        double dy = y - Math.clamp(y, margin + r, h - margin - r);
        return dx * dx + dy * dy <= r * r;
    }

    /** 提交一条裁切后的线段；斜线只产生一个 GUI 绘制条目。 */
    public static void line(GuiGraphicsExtractor g, MapView view, int left, int top, int radius,
                            double x1, double y1, double x2, double y2, int color, int width) {
        double dx = x2 - x1, dy = y2 - y1;
        double[] range = {0, 1};
        double margin = Math.max(2, width);
        if (!clip(-dx, x1 - margin, range) || !clip(dx, view.width() - margin - x1, range)
                || !clip(-dy, y1 - margin, range) || !clip(dy, view.height() - margin - y1, range)) return;
        double lo = range[0], hi = range[1];
        if (radius > 0) {
            // 圆角矩形是凸集：先找到内部点，再二分求两端与轮廓的交点。
            double middle = Double.NaN;
            for (int i = 0; i <= 16; i++) {
                double t = lo + (hi - lo) * i / 16;
                if (contains(view, x1 + dx * t, y1 + dy * t, radius, margin)) { middle = t; break; }
            }
            if (Double.isNaN(middle)) return;
            if (!contains(view, x1 + dx * lo, y1 + dy * lo, radius, margin)) {
                double outer = lo, inner = middle;
                for (int i = 0; i < 12; i++) { double t = (outer + inner) / 2; if (contains(view, x1 + dx * t, y1 + dy * t, radius, margin)) inner = t; else outer = t; }
                lo = inner;
            }
            if (!contains(view, x1 + dx * hi, y1 + dy * hi, radius, margin)) {
                double outer = hi, inner = middle;
                for (int i = 0; i < 12; i++) { double t = (outer + inner) / 2; if (contains(view, x1 + dx * t, y1 + dy * t, radius, margin)) inner = t; else outer = t; }
                hi = inner;
            }
        }
        rawLine(g, left + x1 + dx * lo, top + y1 + dy * lo, left + x1 + dx * hi, top + y1 + dy * hi, color, width);
    }

    private static boolean clip(double p, double q, double[] range) {
        if (p == 0) return q >= 0;
        double r = q / p;
        if (p < 0) { if (r > range[1]) return false; range[0] = Math.max(range[0], r); }
        else { if (r < range[0]) return false; range[1] = Math.min(range[1], r); }
        return true;
    }

    /** 已裁切的屏幕线段，用于边界内标记与导航箭头。 */
    public static void rawLine(GuiGraphicsExtractor g, double x1, double y1, double x2, double y2, int color, int width) {
        double length = Math.hypot(x2 - x1, y2 - y1);
        if (length < 0.1 || !Double.isFinite(length)) return;
        g.pose().pushMatrix();
        g.pose().translate((float) x1, (float) y1);
        g.pose().rotate((float) Math.atan2(y2 - y1, x2 - x1));
        g.fill(0, -width / 2, (int) Math.ceil(length), (width + 1) / 2, color);
        g.pose().popMatrix();
    }
}
