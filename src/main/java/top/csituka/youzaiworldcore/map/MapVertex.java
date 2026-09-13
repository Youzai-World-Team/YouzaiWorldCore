package top.csituka.youzaiworldcore.map;

/** 地图平面上的世界坐标，禁止非有限值进入绘图、缩放或导出。 */
public record MapVertex(double x, double z) {
    public MapVertex {
        if (!Double.isFinite(x) || !Double.isFinite(z)
                || Math.abs(x) > MapTileKey.WORLD_LIMIT || Math.abs(z) > MapTileKey.WORLD_LIMIT) {
            throw new IllegalArgumentException("地图坐标超出范围");
        }
    }
}
