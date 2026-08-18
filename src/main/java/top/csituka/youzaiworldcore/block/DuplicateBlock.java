package top.csituka.youzaiworldcore.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import org.jspecify.annotations.NonNull;
import org.jetbrains.annotations.Nullable;
import top.csituka.youzaiworldcore.block.entity.DuplicateBlockEntity;
import top.csituka.youzaiworldcore.util.DebugLogger;

import java.util.UUID;

/**
 * 复制方块。
 * <p>
 * 放置后默认为普通态；若放置瞬间正上方已有可复制方块，也会先按普通态处理，
 * 直到上方经历一次移除并重新放回目标方块后才进入激活态。
 * 玩家再在 16 格范围内放置另一个复制方块时，会寻找同玩家最近的激活复制方块，
 * 并用该起点上方方块填充两点之间的矩形区域。起点和终点本身也会一并替换。
 */
@SuppressWarnings("null")
public class DuplicateBlock extends BaseEntityBlock {

    public static final BooleanProperty ACTIVE = BooleanProperty.create("active");
    public static final MapCodec<DuplicateBlock> CODEC = simpleCodec(DuplicateBlock::new);

    private static final int RANGE = 16;
    private static final DustParticleOptions NORMAL_PARTICLE = new DustParticleOptions(0x4DA3FF, 0.85f);

    public DuplicateBlock(BlockBehaviour.Properties properties) {
        super(properties);
        DebugLogger.entering("DuplicateBlock", "constructor");
        this.registerDefaultState(this.stateDefinition.any().setValue(ACTIVE, false));
        DebugLogger.exiting("DuplicateBlock", "constructor");
    }

    @Override
    @NonNull
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(ACTIVE);
    }

    @Override
    @NonNull
    public BlockEntity newBlockEntity(@NonNull BlockPos pos, @NonNull BlockState state) {
        return new DuplicateBlockEntity(pos, state);
    }

    @Override
    @NonNull
    public RenderShape getRenderShape(@NonNull BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public void animateTick(@NonNull BlockState state, @NonNull Level level, @NonNull BlockPos pos,
                            @NonNull RandomSource random) {
        if (state.getValue(ACTIVE)) {
            return;
        }

        if (random.nextFloat() > 0.35f) {
            return;
        }

        double x = pos.getX() + 0.5 + (random.nextDouble() - 0.5) * 0.45;
        double y = pos.getY() + 1.03 + random.nextDouble() * 0.08;
        double z = pos.getZ() + 0.5 + (random.nextDouble() - 0.5) * 0.45;
        level.addParticle(NORMAL_PARTICLE, x, y, z, 0.0, 0.025, 0.0);
    }

    @Override
    @Nullable
    public BlockState getStateForPlacement(@NonNull BlockPlaceContext context) {
        return this.defaultBlockState().setValue(ACTIVE, false);
    }

    @Override
    public void setPlacedBy(@NonNull Level level, @NonNull BlockPos pos, @NonNull BlockState state,
            LivingEntity placer, @NonNull ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        DebugLogger.entering("DuplicateBlock", "setPlacedBy", "pos=" + pos);

        if (level.isClientSide()) {
            DebugLogger.exiting("DuplicateBlock", "setPlacedBy", "client side");
            return;
        }

        if (level.getBlockEntity(pos) instanceof DuplicateBlockEntity blockEntity
                && placer instanceof ServerPlayer player) {
            UUID playerUuid = player.getUUID();
            blockEntity.setOwner(playerUuid);
            blockEntity.initializeActivation(isCopyableTarget(level.getBlockState(pos.above())));

            DuplicateBlockEntity source = findNearestActiveSource(level, pos, playerUuid);
            if (source != null) {
                BlockPos sourcePos = source.getBlockPos();
                BlockState copyState = level.getBlockState(sourcePos.above());
                if (isCopyableTarget(copyState)) {
                    int replaced = fillBetween(level, sourcePos, pos, copyState);
                    DebugLogger.info("DuplicateBlock",
                            "玩家 %s 使用复制方块填充 %d 个方块：起点=%s, 终点=%s, 方块=%s",
                            player.getName().getString(), replaced,
                            sourcePos.toShortString(), pos.toShortString(), copyState.getBlock());
                }
            }
        }

        DebugLogger.exiting("DuplicateBlock", "setPlacedBy");
    }

    @Override
    @NonNull
    protected BlockState updateShape(@NonNull BlockState state, @NonNull LevelReader level,
            @NonNull ScheduledTickAccess scheduledTickAccess,
            @NonNull BlockPos pos, @NonNull Direction direction,
            @NonNull BlockPos neighborPos, @NonNull BlockState neighborState,
            @NonNull RandomSource random) {
        return super.updateShape(state, level, scheduledTickAccess, pos,
                direction, neighborPos, neighborState, random);
    }

    @Override
    protected void neighborChanged(@NonNull BlockState state, @NonNull Level level, @NonNull BlockPos pos,
            @NonNull Block block, net.minecraft.world.level.redstone.Orientation orientation,
            boolean movedByPiston) {
        super.neighborChanged(state, level, pos, block, orientation, movedByPiston);
        if (level.isClientSide()) {
            return;
        }

        if (level.getBlockEntity(pos) instanceof DuplicateBlockEntity blockEntity) {
            blockEntity.refreshActivation(isCopyableTarget(level.getBlockState(pos.above())));
        }
    }

    private static DuplicateBlockEntity findNearestActiveSource(Level level, BlockPos targetPos, UUID ownerUuid) {
        DuplicateBlockEntity nearest = null;
        long nearestDistance = Long.MAX_VALUE;

        for (BlockPos candidate : BlockPos.betweenClosed(
                targetPos.offset(-RANGE, -RANGE, -RANGE),
                targetPos.offset(RANGE, RANGE, RANGE))) {
            if (candidate.equals(targetPos)) {
                continue;
            }

            BlockState candidateState = level.getBlockState(candidate);
            if (!candidateState.is(ModBlocks.DUPLICATE_BLOCK)
                    || !candidateState.getValue(ACTIVE)
                    || !isWithinRange(candidate, targetPos)) {
                continue;
            }

            if (!(level.getBlockEntity(candidate) instanceof DuplicateBlockEntity candidateEntity)
                    || !candidateEntity.isOwnedBy(ownerUuid)
                    || !isCopyableTarget(level.getBlockState(candidate.above()))) {
                continue;
            }

            long distance = distanceSqr(candidate, targetPos);
            if (distance < nearestDistance) {
                nearest = candidateEntity;
                nearestDistance = distance;
            }
        }

        return nearest;
    }

    private static int fillBetween(Level level, BlockPos sourcePos, BlockPos targetPos, BlockState copyState) {
        int minX = Math.min(sourcePos.getX(), targetPos.getX());
        int maxX = Math.max(sourcePos.getX(), targetPos.getX());
        int minY = Math.min(sourcePos.getY(), targetPos.getY());
        int maxY = Math.max(sourcePos.getY(), targetPos.getY());
        int minZ = Math.min(sourcePos.getZ(), targetPos.getZ());
        int maxZ = Math.max(sourcePos.getZ(), targetPos.getZ());
        int replaced = 0;

        for (BlockPos fillPos : BlockPos.betweenClosed(minX, minY, minZ, maxX, maxY, maxZ)) {
            level.setBlock(fillPos, copyState, Block.UPDATE_ALL | Block.UPDATE_SUPPRESS_DROPS);
            replaced++;
        }

        return replaced;
    }

    private static boolean isCopyableTarget(BlockState state) {
        return !state.isAir() && !state.is(ModBlocks.DUPLICATE_BLOCK);
    }

    private static boolean isWithinRange(BlockPos a, BlockPos b) {
        return Math.abs(a.getX() - b.getX()) <= RANGE
                && Math.abs(a.getY() - b.getY()) <= RANGE
                && Math.abs(a.getZ() - b.getZ()) <= RANGE;
    }

    private static long distanceSqr(BlockPos a, BlockPos b) {
        long dx = a.getX() - b.getX();
        long dy = a.getY() - b.getY();
        long dz = a.getZ() - b.getZ();
        return dx * dx + dy * dy + dz * dz;
    }
}
