package top.csituka.youzaiworldcore.map;

import java.util.Arrays;
import java.util.List;

/**
 * 已完成采样的区块快照。发布后数组只读，后台存盘、导出不再访问世界对象。
 * 颜色为 ARGB；光照低四位为方块光，高四位为天空光；透明像素表示虚空。
 */
public record MapTile(MapTileKey key, long revision, int[] colors, short[] heights,
                      byte[] lights, byte[] biomeIndices, List<String> biomes, int[] biomeColors) {
    public static final int PIXELS = 256;
    public static final short VOID_HEIGHT = Short.MIN_VALUE;

    public MapTile {
        if (key == null || colors.length != PIXELS || heights.length != PIXELS
                || lights.length != PIXELS || biomeIndices.length != PIXELS
                || biomes.isEmpty() || biomes.size() > PIXELS || biomeColors.length != biomes.size()) {
            throw new IllegalArgumentException("地图瓦片数组长度无效");
        }
        biomes = List.copyOf(biomes);
        for (String biome : biomes) {
            if (biome == null || biome.length() > 128) throw new IllegalArgumentException("群系名称无效");
        }
        for (byte index : biomeIndices) {
            if (Byte.toUnsignedInt(index) >= biomes.size()) throw new IllegalArgumentException("群系索引无效");
        }
    }

    /** @return 指定像素的群系标识 */
    public String biome(int pixel) { return biomes.get(Byte.toUnsignedInt(biomeIndices[pixel])); }

    /** @return 指定像素的群系图层颜色 */
    public int biomeColor(int pixel) { return biomeColors[Byte.toUnsignedInt(biomeIndices[pixel])]; }

    /** 只比较有效内容，未变化的区块不重复写盘、上传纹理或占用同步带宽。 */
    public boolean sameContents(MapTile other) {
        return other != null && key.equals(other.key) && biomes.equals(other.biomes)
                && Arrays.equals(colors, other.colors) && Arrays.equals(heights, other.heights)
                && Arrays.equals(lights, other.lights) && Arrays.equals(biomeIndices, other.biomeIndices)
                && Arrays.equals(biomeColors, other.biomeColors);
    }
}
