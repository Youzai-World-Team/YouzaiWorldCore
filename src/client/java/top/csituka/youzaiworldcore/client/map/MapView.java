package top.csituka.youzaiworldcore.client.map;

/** 统一的小地图、全屏地图、鼠标交互和导出坐标变换；缩放以光标所在世界坐标为锚点。 */
public record MapView(double centerX, double centerZ, double scale, double angle, int width, int height) {
    public record Point(double x, double y) { }
    public MapView {
        if (!Double.isFinite(centerX) || !Double.isFinite(centerZ) || !Double.isFinite(scale)
                || !Double.isFinite(angle) || scale <= 0 || width <= 0 || height <= 0) {
            throw new IllegalArgumentException("地图视口无效");
        }
    }

    /** 世界坐标转为视口内坐标。 */
    public Point screen(double worldX, double worldZ) {
        double dx = worldX - centerX, dz = worldZ - centerZ;
        double cos = Math.cos(angle), sin = Math.sin(angle);
        return new Point(width / 2.0 + (dx * cos + dz * sin) * scale,
                height / 2.0 + (-dx * sin + dz * cos) * scale);
    }

    /** 视口坐标转为世界坐标，返回值 y 分量表示世界 Z。 */
    public Point world(double screenX, double screenY) {
        double dx = (screenX - width / 2.0) / scale, dz = (screenY - height / 2.0) / scale;
        double cos = Math.cos(angle), sin = Math.sin(angle);
        return new Point(centerX + dx * cos - dz * sin, centerZ + dx * sin + dz * cos);
    }

    /** 变更缩放后保持光标下的世界位置不动。 */
    public MapView zoomAt(double x, double y, double newScale) {
        Point before = world(x, y);
        MapView changed = new MapView(centerX, centerZ, newScale, angle, width, height);
        Point after = changed.world(x, y);
        return new MapView(centerX + before.x - after.x, centerZ + before.y - after.y,
                newScale, angle, width, height);
    }

    /** @return 屏幕上的点是否位于可绘制区域，圆形小地图使用同一个裁切判断 */
    public boolean contains(double x, double y, boolean circle, double margin) {
        if (x < margin || y < margin || x >= width - margin || y >= height - margin) return false;
        if (!circle) return true;
        double radius = Math.min(width, height) / 2.0 - margin;
        return Math.hypot(x - width / 2.0, y - height / 2.0) <= radius;
    }
}
