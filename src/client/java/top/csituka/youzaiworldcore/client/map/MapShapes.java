package top.csituka.youzaiworldcore.client.map;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import top.csituka.youzaiworldcore.map.MapDrawing;
import top.csituka.youzaiworldcore.map.MapVertex;

import java.util.ArrayList;
import java.util.List;

/** 地图绘图、命中检测与导出共用的几何路径。 */
public final class MapShapes {
    private MapShapes() { }

    /** 将图形转换为世界坐标折线，椭圆使用 64 段闭合路径。 */
    public static List<MapVertex> path(MapDrawing drawing) {
        var vertices = drawing.vertices();
        if (drawing.kind() != MapDrawing.Kind.RECTANGLE && drawing.kind() != MapDrawing.Kind.ELLIPSE) return vertices;
        var a = vertices.getFirst(); var b = vertices.getLast();
        if (drawing.kind() == MapDrawing.Kind.RECTANGLE) return List.of(a, new MapVertex(b.x(), a.z()), b, new MapVertex(a.x(), b.z()), a);
        var points = new ArrayList<MapVertex>(65);
        double cx = (a.x() + b.x()) / 2, cz = (a.z() + b.z()) / 2;
        for (int i = 0; i <= 64; i++) {
            double angle = Math.PI * i / 32;
            points.add(new MapVertex(cx + (b.x() - a.x()) / 2 * Math.cos(angle), cz + (b.z() - a.z()) / 2 * Math.sin(angle)));
        }
        return points;
    }

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

    /** 文本整行必须落在圆形/圆角轮廓内，防止标签越过小地图边框。 */
    public static int textWidth(MapView view, double x, double y, int radius, int textHeight) {
        double left = 2, right = view.width() - 2;
        for (double row : new double[] {y - 1, y + textHeight + 1}) {
            if (row < 2 || row >= view.height() - 2) return 0;
            double distance = row < radius ? radius - row : row > view.height() - radius ? row - (view.height() - radius) : 0;
            double inset = radius == 0 ? 0 : radius - Math.sqrt(Math.max(0, radius * radius - distance * distance));
            left = Math.max(left, inset + 2); right = Math.min(right, view.width() - inset - 2);
        }
        return x < left ? 0 : Math.max(0, (int) (right - x));
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

    /** @return 世界折线距鼠标的最短屏幕距离 */
    public static double distance(MapDrawing drawing, MapView view, double x, double y) {
        var points = path(drawing);
        var first = view.screen(points.getFirst().x(), points.getFirst().z());
        double distance = Math.hypot(first.x() - x, first.y() - y);
        for (int i = 1; i < points.size(); i++) {
            var a = view.screen(points.get(i - 1).x(), points.get(i - 1).z());
            var b = view.screen(points.get(i).x(), points.get(i).z());
            double dx = b.x() - a.x(), dy = b.y() - a.y();
            double t = dx == 0 && dy == 0 ? 0 : Math.clamp(((x - a.x()) * dx + (y - a.y()) * dy) / (dx * dx + dy * dy), 0, 1);
            distance = Math.min(distance, Math.hypot(a.x() + t * dx - x, a.y() + t * dy - y));
        }
        return distance;
    }
}
