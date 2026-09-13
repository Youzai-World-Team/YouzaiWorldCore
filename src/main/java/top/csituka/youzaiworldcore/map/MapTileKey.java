package top.csituka.youzaiworldcore.map;

import net.minecraft.resources.Identifier;

/** 地图瓦片唯一标识。一个瓦片对应一个区块，负坐标必须使用 floorDiv/floorMod。 */
public record MapTileKey(String dimension, MapLayer layer, int height, int chunkX, int chunkZ) {
    public static final int WORLD_LIMIT = 29_999_984;
    public static final int CHUNK_LIMIT = WORLD_LIMIT / 16;

    public MapTileKey {
        if (dimension == null || dimension.length() > 128 || Identifier.tryParse(dimension) == null
                || layer == null || layer == MapLayer.AUTO
                || height < -4096 || height > 4095
                || Math.abs((long) chunkX) > CHUNK_LIMIT || Math.abs((long) chunkZ) > CHUNK_LIMIT) {
            throw new IllegalArgumentException("地图瓦片坐标或图层无效");
        }
        if (!layer.hasHeight()) height = 0;
    }

    /** @return 区块内像素索引，兼容原点以西、以北的坐标 */
    public static int pixelIndex(int blockX, int blockZ) {
        return Math.floorMod(blockX, 16) + Math.floorMod(blockZ, 16) * 16;
    }
}
