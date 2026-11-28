package top.csituka.youzaiworldcore.util;

import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import org.jspecify.annotations.Nullable;

/** 只访问已经可用的服务端区块。 */
public final class LoadedChunks {
    private LoadedChunks() {
    }

    public static @Nullable LevelChunk get(ServerLevel level, BlockPos pos) {
        return level.getChunkSource().getChunkNow(
                SectionPos.blockToSectionCoord(pos.getX()), SectionPos.blockToSectionCoord(pos.getZ()));
    }

    /** 为射线碰撞提供只读视图，缺失区块视作实心边界。 */
    public static BlockGetter collisionView(ServerLevel level) {
        return new BlockGetter() {
            @SuppressWarnings("null")
            @Override
            public @Nullable BlockEntity getBlockEntity(@SuppressWarnings("null") BlockPos pos) {
                var chunk = get(level, pos);
                return chunk == null ? null : chunk.getBlockEntity(pos);
            }

            @SuppressWarnings("null")
            @Override
            public BlockState getBlockState(@SuppressWarnings("null") BlockPos pos) {
                if (level.isOutsideBuildHeight(pos))
                    return Blocks.VOID_AIR.defaultBlockState();
                var chunk = get(level, pos);
                return chunk == null ? Blocks.BARRIER.defaultBlockState() : chunk.getBlockState(pos);
            }

            @SuppressWarnings("null")
            @Override
            public FluidState getFluidState(@SuppressWarnings("null") BlockPos pos) {
                var chunk = get(level, pos);
                return chunk == null ? Fluids.EMPTY.defaultFluidState() : chunk.getFluidState(pos);
            }

            @Override
            public int getMinY() {
                return level.getMinY();
            }

            @Override
            public int getHeight() {
                return level.getHeight();
            }
        };
    }

    public static boolean covers(ServerLevel level, BlockPos min, BlockPos max, int margin) {
        int minX = SectionPos.blockToSectionCoord(min.getX() - margin);
        int minZ = SectionPos.blockToSectionCoord(min.getZ() - margin);
        int maxX = SectionPos.blockToSectionCoord(max.getX() + margin);
        int maxZ = SectionPos.blockToSectionCoord(max.getZ() + margin);
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                if (level.getChunkSource().getChunkNow(x, z) == null) {
                    return false;
                }
            }
        }
        return true;
    }
}
