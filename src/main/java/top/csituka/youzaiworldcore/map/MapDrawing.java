package top.csituka.youzaiworldcore.map;

import net.minecraft.resources.Identifier;

import java.util.List;
import java.util.UUID;

/** 地图标注；保存世界坐标，缩放与窗口尺寸变化不会改变标注位置。 */
public record MapDrawing(UUID id, String dimension, Kind kind, List<MapVertex> vertices,
                         int color, String label) {
    public enum Kind { PEN, LINE, RECTANGLE, ELLIPSE, LABEL }
    public static final int MAX_VERTICES = 512;

    public MapDrawing {
        if (id == null || dimension == null || dimension.length() > 128
                || Identifier.tryParse(dimension) == null || kind == null || label == null || label.length() > 96
                || vertices == null || vertices.isEmpty() || vertices.size() > MAX_VERTICES
                || (kind != Kind.LABEL && vertices.size() < 2)) {
            throw new IllegalArgumentException("地图标注内容无效");
        }
        vertices = List.copyOf(vertices);
        color |= 0xFF000000;
    }
}
