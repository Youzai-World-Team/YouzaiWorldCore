package top.csituka.youzaiworldcore.client.map;

import top.csituka.youzaiworldcore.client.config.MapSettings;
import top.csituka.youzaiworldcore.map.MapLayer;
import top.csituka.youzaiworldcore.map.MapTile;
import top.csituka.youzaiworldcore.map.MapTileKey;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMaps;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;

/** 有容量上限的客户端内存瓦片缓存；持久地形由服务器存档保存，不另建客户端缓存目录。 */
public final class MapTileCache {
    private record Layer(String dimension, MapLayer layer, int height) { }
    private final LinkedHashMap<MapTileKey, MapTile> tiles = new LinkedHashMap<>(128, 0.75f, true);
    private final Map<Layer, Long2ObjectMap<MapTile>> layers = new HashMap<>();
    private long revision;

    /** @return 区块位置的无碰撞压缩键 */
    public static long position(int x, int z) { return (x & 0xFFFFFFFFL) | ((long) z << 32); }

    /** 缓存新快照，重复内容不触发纹理刷新。 */
    public void put(MapTile tile) {
        MapTile previous = tiles.get(tile.key());
        if (tile.sameContents(previous)) return;
        tiles.put(tile.key(), tile);
        layers.computeIfAbsent(layer(tile.key()), ignored -> new Long2ObjectOpenHashMap<>())
                .put(position(tile.key().chunkX(), tile.key().chunkZ()), tile);
        while (tiles.size() > MapSettings.cacheTiles()) {
            var iterator = tiles.entrySet().iterator();
            var oldest = iterator.next();
            var index = layers.get(layer(oldest.getKey()));
            index.remove(position(oldest.getKey().chunkX(), oldest.getKey().chunkZ()));
            if (index.isEmpty()) layers.remove(layer(oldest.getKey()));
            iterator.remove();
        }
        revision++;
    }

    /** @return 当前图层索引；仅在客户端线程内读取 */
    public Long2ObjectMap<MapTile> view(String dimension, MapLayer layer, int height) {
        return layers.getOrDefault(new Layer(dimension, layer, layer.hasHeight() ? height : 0), Long2ObjectMaps.emptyMap());
    }

    /** @return 后台导出可安全使用的不可变索引快照 */
    public Long2ObjectMap<MapTile> snapshot(String dimension, MapLayer layer, int height) {
        return Long2ObjectMaps.unmodifiable(new Long2ObjectOpenHashMap<>(view(dimension, layer, height)));
    }

    public MapTile get(MapTileKey key) { return tiles.get(key); }
    public int size() { return tiles.size(); }
    public long revision() { return revision; }

    /** 切服与断线时释放所有旧世界数据。 */
    public void clear() { tiles.clear(); layers.clear(); revision++; }

    private static Layer layer(MapTileKey key) { return new Layer(key.dimension(), key.layer(), key.height()); }
}
