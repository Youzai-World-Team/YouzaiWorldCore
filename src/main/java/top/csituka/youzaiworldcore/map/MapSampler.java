package top.csituka.youzaiworldcore.map;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.MapColor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 原生分列地形采样器。只读取调用方已取得的完整区块，不请求加载或生成区块。
 * 地表高度使用 26.2 的 ChunkAccess.getHeight 返回值，getMaxY 本身为包含式上界。
 */
public final class MapSampler {
    private MapSampler() { }

    /** 创建需在对应世界主线程上推进的采样任务。 */
    public static Job start(Level level, LevelChunk chunk, MapTileKey key) { return new Job(level, chunk, key); }

    /** 同时有列数和纳秒预算的任务；未完成的任务在下一 Tick 继续。 */
    public static final class Job {
        private final Level level;
        private final LevelChunk chunk;
        private final MapTileKey key;
        private final int[] colors = new int[256];
        private final short[] heights = new short[256];
        private final byte[] lights = new byte[256], indices = new byte[256];
        private final Map<String, Integer> palette = new LinkedHashMap<>();
        private final ArrayList<Integer> paletteColors = new ArrayList<>();
        private final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        private int cursor;

        private Job(Level level, LevelChunk chunk, MapTileKey key) {
            this.level = level; this.chunk = chunk; this.key = key;
        }

        public MapTileKey key() { return key; }
        public LevelChunk chunk() { return chunk; }
        public Level level() { return level; }

        /** @return 本次实际处理的列数 */
        public int step(int maximumColumns, long deadlineNanos) {
            int start = cursor;
            while (cursor < 256 && cursor - start < maximumColumns && System.nanoTime() < deadlineNanos) {
                sample(cursor++);
            }
            return cursor - start;
        }

        public boolean complete() { return cursor == 256; }

        /** 仅在完成后发布快照，未探索与真正的虚空不会混为一谈。 */
        public MapTile finish() {
            if (!complete()) throw new IllegalStateException("地图采样尚未完成");
            return new MapTile(key, System.nanoTime(), colors, heights, lights, indices,
                    new ArrayList<>(palette.keySet()), paletteColors.stream().mapToInt(Integer::intValue).toArray());
        }

        private void sample(int index) {
            int x = key.chunkX() * 16 + index % 16, z = key.chunkZ() * 16 + index / 16;
            int minimum = level.getMinY();
            int surface = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);
            int y = key.layer().hasHeight() ? Math.min(key.height(), surface) : surface;
            y = Math.clamp(y, minimum, level.getMaxY());
            boolean findOpening = key.layer() == MapLayer.CAVE;
            if (key.layer() == MapLayer.SURFACE && level.dimensionType().hasCeiling()) {
                // 下界地表显示基岩下方；ROOF 单独保留顶层及顶层建筑。
                y = Math.min(y, Math.min(120, level.getMaxY()));
                findOpening = true;
            }
            int searchFloor = findOpening ? Math.max(minimum, y - 256) : minimum;
            if (findOpening) {
                while (y >= searchFloor) {
                    pos.set(x, y, z);
                    BlockState state = chunk.getBlockState(pos);
                    if (state.isAir() || !state.getFluidState().isEmpty()) break;
                    y--;
                }
            }
            BlockState state = null;
            MapColor color = MapColor.NONE;
            while (y >= searchFloor) {
                pos.set(x, y, z);
                state = chunk.getBlockState(pos);
                color = state.getMapColor(chunk, pos);
                if (!state.isAir() && color != MapColor.NONE) break;
                y--;
            }
            int biomeY = Math.clamp(y, minimum, level.getMaxY());
            var holder = chunk.getNoiseBiome(Math.floorDiv(x, 4), Math.floorDiv(biomeY, 4), Math.floorDiv(z, 4));
            var biome = holder.value();
            String biomeName = holder.unwrapKey().map(value -> value.identifier().toString()).orElse("minecraft:plains");
            Integer paletteIndex = palette.get(biomeName);
            if (paletteIndex == null) {
                paletteIndex = palette.size();
                palette.put(biomeName, paletteIndex);
                int hash = biomeName.hashCode();
                paletteColors.add(0xFF000000 | ((72 + ((hash >>> 16) & 127)) << 16)
                        | ((72 + ((hash >>> 8) & 127)) << 8) | (72 + (hash & 127)));
            }
            indices[index] = (byte) (int) paletteIndex;
            if (y < searchFloor || state == null || color == MapColor.NONE) {
                heights[index] = MapTile.VOID_HEIGHT;
                return;
            }
            int rgb = color.col;
            if (color == MapColor.WATER) {
                rgb = biome.getWaterColor();
                int depth = 0;
                while (depth < 16 && y - depth - 1 >= minimum) {
                    pos.set(x, y - depth - 1, z);
                    if (chunk.getFluidState(pos).isEmpty()) break;
                    depth++;
                }
                rgb = shade(0xFF000000 | rgb, 1.0 - depth * 0.025);
            } else if (state.getBlock() instanceof LeavesBlock) {
                rgb = biome.getFoliageColor();
            } else if (color == MapColor.GRASS || color == MapColor.PLANT) {
                rgb = biome.getGrassColor(x, z);
            }
            colors[index] = 0xFF000000 | rgb;
            heights[index] = (short) y;
            pos.set(x, Math.min(y + 1, level.getMaxY()), z);
            int block = Math.max(state.getLightEmission(), level.getLightEngine().getLayerListener(LightLayer.BLOCK).getLightValue(pos));
            int sky = level.getLightEngine().getLayerListener(LightLayer.SKY).getLightValue(pos);
            lights[index] = (byte) ((Math.clamp(sky, 0, 15) << 4) | Math.clamp(block, 0, 15));
        }
    }

    /** 线性调整地形 RGB，保留透明像素。 */
    public static int shade(int argb, double factor) {
        int red = Math.clamp((int) (((argb >>> 16) & 255) * factor), 0, 255);
        int green = Math.clamp((int) (((argb >>> 8) & 255) * factor), 0, 255);
        int blue = Math.clamp((int) ((argb & 255) * factor), 0, 255);
        return (argb & 0xFF000000) | (red << 16) | (green << 8) | blue;
    }
}
