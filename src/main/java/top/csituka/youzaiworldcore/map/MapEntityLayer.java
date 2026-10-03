package top.csituka.youzaiworldcore.map;

import net.fabricmc.fabric.api.tag.convention.v2.ConventionalBlockTags;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StainedGlassBlock;
import net.minecraft.world.level.block.StainedGlassPaneBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;

/** 实体实际所在地图层：地表共用一层，地下按脚部 Y 每八格分层；两端共用，不受浏览设置影响。 */
public final class MapEntityLayer {
    public static final int SURFACE = Integer.MAX_VALUE, UNKNOWN = Integer.MIN_VALUE;
    private MapEntityLayer() { }

    /** 仅使用已加载区块；未知位置不推定为地表。 */
    public static int of(Entity entity) {
        var level = entity.level();
        int x = entity.getBlockX(), z = entity.getBlockZ();
        var chunk = level.getChunk(Math.floorDiv(x, 16), Math.floorDiv(z, 16), ChunkStatus.FULL, false);
        if (chunk == null) return UNKNOWN;
        return beneathSurface(level, chunk, x, z, entity.getBoundingBox().maxY)
                ? Math.floorDiv(entity.getBlockY(), 8) : SURFACE;
    }

    public static boolean same(int first, int second) { return first != UNKNOWN && first == second; }

    /** 自动切层、探索记录和雷达共用遮挡判定：头顶累计达到五格实体遮挡才算地下。 */
    public static boolean beneathSurface(Level level, ChunkAccess chunk, int x, int z, double headY) {
        int surface = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);
        int minimum = Math.max(level.getMinY(), (int) Math.floor(headY)), obstructionCount = 0;
        var pos = new BlockPos.MutableBlockPos();
        for (int y = surface; y >= minimum; y--) {
            pos.set(x, y, z);
            if (!surfaceCover(level, chunk.getBlockState(pos), pos) && ++obstructionCount > 4) return true;
        }
        return false;
    }

    private static boolean surfaceCover(Level level, BlockState state, BlockPos pos) {
        if (state.isAir() || state.is(BlockTags.LEAVES) || state.is(Blocks.LILY_PAD)) return true;
        if (state.is(Blocks.GLASS) || state.is(Blocks.GLASS_PANE) || state.is(Blocks.TINTED_GLASS)
                || state.getBlock() instanceof StainedGlassBlock || state.getBlock() instanceof StainedGlassPaneBlock
                || state.is(ConventionalBlockTags.GLASS_BLOCKS) || state.is(ConventionalBlockTags.GLASS_PANES)) return true;
        // 含水台阶／楼梯仍计入实体遮挡，只有水和无碰撞水草作为地表覆盖放行。
        return state.getFluidState().is(FluidTags.WATER) && state.getCollisionShape(level, pos).isEmpty();
    }
}
