package top.csituka.youzaiworldcore.map;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import top.csituka.youzaiworldcore.util.DebugLogger;

import java.util.LinkedHashMap;

/** 已加载区块轮询队列；在地图既有 Tick 预算内保存地表、顶层及每八格地下切片。 */
public final class MapLoadedCapture {
    private static final int REFRESH_TICKS = 6000;
    private static final LinkedHashMap<Position, Cursor> LOADED = new LinkedHashMap<>();
    private record Position(String dimension, int x, int z) { }
    private MapLoadedCapture() { }

    /** 监听真实区块加载，不创建加载票，也不从地图视口加载远处区块。 */
    public static void initialize() {
        ServerLifecycleEvents.SERVER_STARTING.register(server -> clear());
        ServerChunkEvents.CHUNK_LOAD.register((level, chunk, generated) -> {
            if (!level.dimension().identifier().toString().equals("youzaiworldcore:login_hall")) {
                LOADED.put(position(level, chunk), new Cursor(level, chunk));
            }
        });
        ServerChunkEvents.CHUNK_UNLOAD.register((level, chunk) -> LOADED.remove(position(level, chunk)));
        DebugLogger.info("MapLoadedCapture", "已注册地表与地下切片的增量采样");
    }

    /** 每次从队头取一个图层并移到队尾，避免一个高维度区块独占预算。 */
    public static MapSampler.Job next(int tick) {
        for (int attempt = 0, count = Math.min(32, LOADED.size()); attempt < count; attempt++) {
            var first = LOADED.pollFirstEntry();
            if (first == null) return null;
            var key = first.getKey(); var cursor = first.getValue();
            LOADED.put(key, cursor);
            if (tick < cursor.nextTick || cursor.level.getChunkSource().getChunkNow(key.x(), key.z()) != cursor.chunk) continue;
            if (Math.abs((long) key.x()) > MapTileKey.CHUNK_LIMIT || Math.abs((long) key.z()) > MapTileKey.CHUNK_LIMIT) continue;
            int min = cursor.level.getMinY(), max = cursor.level.getMaxY();
            int slices = MapServerSettings.captureUnderground ? Math.ceilDiv(max - min + 1, 8) : 0;
            int index = cursor.layer++;
            MapLayer layer = index == 0 ? MapLayer.SURFACE : index == 1 ? MapLayer.ROOF : MapLayer.FIXED;
            int height = index < 2 ? 0 : Math.min(max, min + (index - 2) * 8 + 7);
            if (cursor.layer >= slices + 2) { cursor.layer = 0; cursor.nextTick = tick + REFRESH_TICKS; }
            // 重载关闭地下采样时，丢弃上一轮尚未完成的地下层游标。
            if (index >= slices + 2) continue;
            return MapSampler.start(cursor.level, cursor.chunk, new MapTileKey(key.dimension(), layer, height, key.x(), key.z()));
        }
        return null;
    }

    /** 关服时释放区块引用。 */
    public static void clear() { LOADED.clear(); }
    private static Position position(ServerLevel level, LevelChunk chunk) {
        return new Position(level.dimension().identifier().toString(), chunk.getPos().x(), chunk.getPos().z());
    }
    private static final class Cursor {
        private final ServerLevel level;
        private final LevelChunk chunk;
        private int layer, nextTick;
        private Cursor(ServerLevel level, LevelChunk chunk) { this.level = level; this.chunk = chunk; }
    }
}
