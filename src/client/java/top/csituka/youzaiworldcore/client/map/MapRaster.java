package top.csituka.youzaiworldcore.client.map;

import top.csituka.youzaiworldcore.client.config.MapSettings;
import top.csituka.youzaiworldcore.map.MapSampler;
import top.csituka.youzaiworldcore.map.MapTile;
import top.csituka.youzaiworldcore.map.MapTileKey;

import java.util.Map;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;

/** 地图纹理与 PNG 导出共用的纯快照着色逻辑，不访问 Minecraft 世界或 GPU。 */
public final class MapRaster {
    /** 可安全传入后台导出线程的着色参数。 */
    public record Style(MapSettings.Overlay overlay, boolean lighting, int skyDarken, double gamma,
                        int unknown, int empty, Map<Long, Integer> loadLevels) { }
    private MapRaster() { }

    /** 读取地图中的一个方块像素，未知区域与已探索虚空使用不同底色。 */
    public static int color(Long2ObjectMap<MapTile> tiles, int x, int z, Style style) {
        long position = MapTileCache.position(Math.floorDiv(x, 16), Math.floorDiv(z, 16));
        if (style.overlay() == MapSettings.Overlay.LOAD_STATE) {
            Integer level = style.loadLevels().get(position);
            if (level == null) return style.unknown();
            if (level <= 31) return 0xFF59B98B;
            if (level == 32) return 0xFF5D9BC5;
            if (level == 33) return 0xFFE1BB6B;
            return 0xFF957FAA;
        }
        MapTile tile = tiles.get(position);
        if (tile == null) return ((Math.floorDiv(x, 16) + Math.floorDiv(z, 16)) & 1) == 0
                ? style.unknown() : MapSampler.shade(style.unknown(), 0.95);
        int pixel = MapTileKey.pixelIndex(x, z);
        if (style.overlay() == MapSettings.Overlay.BIOME) return tile.biomeColor(pixel);
        int color = tile.colors()[pixel];
        if ((color >>> 24) == 0) return style.empty();
        int elevation = tile.heights()[pixel];
        int west = height(tiles, x - 1, z, elevation), north = height(tiles, x, z - 1, elevation);
        double shade = 0.93 + Math.clamp(west + north - 2 * elevation, -6, 6) * 0.022;
        if (style.lighting()) {
            int light = Byte.toUnsignedInt(tile.lights()[pixel]);
            int brightness = Math.max(light & 15, Math.max(0, (light >>> 4) - style.skyDarken()));
            double ambient = tile.key().dimension().equals("minecraft:the_nether") ? 0.48 : 0.25;
            shade *= Math.min(1, ambient + brightness / 15.0 * (1 - ambient) + style.gamma() * 0.3);
        }
        return MapSampler.shade(color, shade);
    }

    private static int height(Long2ObjectMap<MapTile> tiles, int x, int z, int fallback) {
        MapTile tile = tiles.get(MapTileCache.position(Math.floorDiv(x, 16), Math.floorDiv(z, 16)));
        if (tile == null) return fallback;
        int height = tile.heights()[MapTileKey.pixelIndex(x, z)];
        return height == MapTile.VOID_HEIGHT ? fallback : height;
    }
}
