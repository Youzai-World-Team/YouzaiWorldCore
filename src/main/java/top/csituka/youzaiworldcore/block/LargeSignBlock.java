package top.csituka.youzaiworldcore.block;

import com.mojang.serialization.MapCodec;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.advancements.triggers.CriteriaTriggers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.phys.shapes.Shapes;
import org.jetbrains.annotations.Nullable;
import org.jspecify.annotations.NonNull;

import top.csituka.youzaiworldcore.block.entity.LargeSignBlockEntity;
import top.csituka.youzaiworldcore.network.LargeSignOpenEditPayload;
import top.csituka.youzaiworldcore.util.DebugLogger;

import java.util.Map;

/**
 * 大字牌方块。
 * <p>
 * 模型对齐原版墙上告示牌（同为 2 像素厚的薄板），但铺满整个侧面：
 * 16×16×2，只能贴在方块的 4 个水平侧面上。牌面渲染一个大字，
 * 具体绘制见客户端的 {@code LargeSignBlockEntityRenderer}。
 * <p>
 * 交互（整体对齐原版告示牌语义）：
 * <ul>
 *   <li><b>空手 / 普通物品右键</b> → 打开编辑界面，可反复修改；</li>
 *   <li><b>染料右键</b> → 改变文字颜色；</li>
 *   <li><b>荧光墨囊右键</b> → 文字发光；<b>墨囊右键</b> → 取消发光；</li>
 *   <li><b>蜜脾右键</b> → 涂蜡。涂蜡后编辑 / 染色 / 发光全部被拒绝，
 *       只能破坏后重新放置。</li>
 * </ul>
 * 编辑界面由服务端下发 {@link LargeSignOpenEditPayload} 打开，
 * 客户端提交的文本由 {@code ModNetworking} 复核后才写入。
 *
 * @see LargeSignBlockEntity
 * @see top.csituka.youzaiworldcore.util.LargeSignTextRules
 */
// isSolid() 被标记为 @Deprecated（原版「请通过 BlockState 调用」的约定式弃用），
// 原版 WallSignBlock.canSurvive 同样使用它，此处沿用并抑制告警。
@SuppressWarnings({ "null", "deprecation" })
public class LargeSignBlock extends BaseEntityBlock implements SimpleWaterloggedBlock {

    public static final MapCodec<LargeSignBlock> CODEC = simpleCodec(LargeSignBlock::new);

    /** 字牌朝向：牌面法线方向（背面贴着支撑方块）。 */
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;

    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;

    private static final String MODULE = "LargeSignBlock";

    /**
     * 碰撞 / 选择箱：整面 16×16、厚 2 像素，贴在支撑方块一侧。
     * <p>
     * 基准形状为 {@code FACING=NORTH}（牌面朝北，占据方块南侧的 z=14~16），
     * 其余三个朝向由 {@link Shapes#rotateHorizontal(VoxelShape)} 旋转得到，
     * 与原版 {@code WallSignBlock} 的做法一致。
     */
    private static final Map<Direction, VoxelShape> SHAPES =
            Shapes.rotateHorizontal(Block.boxZ(16.0, 0.0, 16.0, 14.0, 16.0));

    public LargeSignBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(WATERLOGGED, false));
    }

    @Override
    @NonNull
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, WATERLOGGED);
    }

    @Override
    @NonNull
    public BlockEntity newBlockEntity(@NonNull BlockPos pos, @NonNull BlockState state) {
        return new LargeSignBlockEntity(pos, state);
    }

    @Override
    @NonNull
    public RenderShape getRenderShape(@NonNull BlockState state) {
        // 牌面板本身走普通方块模型（可被区块网格烘焙，开销低），
        // 方块实体渲染器只额外绘制那一个大字。
        return RenderShape.MODEL;
    }

    // ===== 形状与放置 =====

    @Override
    @NonNull
    protected VoxelShape getShape(@NonNull BlockState state, @NonNull BlockGetter level,
            @NonNull BlockPos pos, @NonNull CollisionContext context) {
        return SHAPES.get(state.getValue(FACING));
    }

    @Override
    protected boolean canSurvive(@NonNull BlockState state, @NonNull LevelReader level, @NonNull BlockPos pos) {
        // 背面必须贴着一个实心方块面
        return level.getBlockState(pos.relative(state.getValue(FACING).getOpposite())).isSolid();
    }

    @Override
    @Nullable
    public BlockState getStateForPlacement(@NonNull BlockPlaceContext context) {
        BlockState state = this.defaultBlockState();
        LevelReader level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        FluidState fluidState = context.getLevel().getFluidState(pos);

        for (Direction direction : context.getNearestLookingDirections()) {
            if (!direction.getAxis().isHorizontal()) {
                continue;
            }
            // 玩家视线指向 direction，则牌面朝向其反方向（朝着玩家）
            BlockState candidate = state.setValue(FACING, direction.getOpposite());
            if (candidate.canSurvive(level, pos)) {
                return candidate.setValue(WATERLOGGED, fluidState.is(Fluids.WATER));
            }
        }

        return null;
    }

    @Override
    @NonNull
    protected BlockState updateShape(@NonNull BlockState state, @NonNull LevelReader level,
            @NonNull ScheduledTickAccess scheduledTickAccess, @NonNull BlockPos pos,
            @NonNull Direction direction, @NonNull BlockPos neighborPos,
            @NonNull BlockState neighborState, @NonNull RandomSource random) {
        if (state.getValue(WATERLOGGED)) {
            scheduledTickAccess.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
        }

        // 支撑方块被移走时字牌自然掉落
        if (direction == state.getValue(FACING).getOpposite() && !state.canSurvive(level, pos)) {
            return Blocks.AIR.defaultBlockState();
        }
        return super.updateShape(state, level, scheduledTickAccess, pos,
                direction, neighborPos, neighborState, random);
    }

    @Override
    @NonNull
    protected FluidState getFluidState(@NonNull BlockState state) {
        return state.getValue(WATERLOGGED)
                ? Fluids.WATER.getSource(false)
                : super.getFluidState(state);
    }

    @Override
    @NonNull
    protected BlockState rotate(@NonNull BlockState state, @NonNull Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    @NonNull
    protected BlockState mirror(@NonNull BlockState state, @NonNull Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    // ===== 交互 =====

    /**
     * 空手（或手持无法作用于字牌的物品）右键：打开编辑界面。
     * <p>
     * 已涂蜡时只播放原版「涂蜡告示牌交互失败」音效。
     */
    @Override
    @NonNull
    protected InteractionResult useWithoutItem(@NonNull BlockState state, @NonNull Level level,
            @NonNull BlockPos pos, @NonNull Player player, @NonNull BlockHitResult hitResult) {
        if (!(level.getBlockEntity(pos) instanceof LargeSignBlockEntity sign)) {
            return InteractionResult.PASS;
        }

        if (!(level instanceof ServerLevel)) {
            // 客户端只做手部动画，真正的判定在服务端
            return InteractionResult.SUCCESS;
        }

        if (sign.isWaxed()) {
            level.playSound(null, pos, SoundEvents.WAXED_SIGN_INTERACT_FAIL, SoundSource.BLOCKS, 1.0f, 1.0f);
            DebugLogger.branch(MODULE, "字牌已涂蜡，拒绝打开编辑界面", false);
            return InteractionResult.SUCCESS;
        }

        if (!player.mayBuild() || !(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.SUCCESS;
        }

        sign.setAllowedPlayerEditor(serverPlayer.getUUID());
        ServerPlayNetworking.send(serverPlayer, new LargeSignOpenEditPayload(pos, sign.getText()));
        DebugLogger.info(MODULE, "玩家 %s 打开大字牌编辑界面：pos=%s, text=%s",
                serverPlayer.getName().getString(), pos.toShortString(), sign.getText());
        return InteractionResult.SUCCESS;
    }

    /**
     * 手持物品右键：处理染料 / 荧光墨囊 / 墨囊 / 蜜脾。
     * <p>
     * 其余物品返回 {@link InteractionResult#TRY_WITH_EMPTY_HAND}，
     * 交给 {@link #useWithoutItem} 打开编辑界面（与原版告示牌一致）。
     */
    @Override
    @NonNull
    protected InteractionResult useItemOn(@NonNull ItemStack stack, @NonNull BlockState state,
            @NonNull Level level, @NonNull BlockPos pos, @NonNull Player player,
            @NonNull InteractionHand hand, @NonNull BlockHitResult hitResult) {
        if (!(level.getBlockEntity(pos) instanceof LargeSignBlockEntity sign)) {
            return InteractionResult.PASS;
        }

        Applicator applicator = Applicator.of(stack);
        boolean canApply = applicator != null && player.mayBuild();

        if (!(level instanceof ServerLevel serverLevel)) {
            // 客户端不知道涂蜡与否也无所谓：涂蜡时同样交给 useWithoutItem 播放失败音效
            return canApply && !sign.isWaxed() ? InteractionResult.SUCCESS
                    : InteractionResult.TRY_WITH_EMPTY_HAND;
        }

        if (!canApply || sign.isWaxed()) {
            return InteractionResult.TRY_WITH_EMPTY_HAND;
        }

        if (!applicator.apply(serverLevel, sign, stack, pos)) {
            // 例如重复使用同色染料 / 重复取消发光：不消耗物品，也不打开编辑界面
            return InteractionResult.CONSUME;
        }

        player.awardStat(Stats.ITEM_USED.get(stack.getItem()));
        if (player instanceof ServerPlayer serverPlayer) {
            CriteriaTriggers.ITEM_USED_ON_BLOCK.trigger(serverPlayer, pos, stack);
        }
        serverLevel.gameEvent(GameEvent.BLOCK_CHANGE, pos, GameEvent.Context.of(player, sign.getBlockState()));
        stack.consume(1, player);

        DebugLogger.info(MODULE, "玩家 %s 对大字牌使用 %s：pos=%s",
                player.getName().getString(), applicator, pos.toShortString());
        return InteractionResult.SUCCESS;
    }

    /**
     * 可作用于大字牌的物品种类，及其各自的效果与音效。
     * <p>
     * 原版靠 {@code SignApplicator} 接口分发，但那套接口只认原版的
     * {@code SignBlockEntity}，大字牌不是它的子类，因此在此自行分发。
     */
    private enum Applicator {
        /** 染料：改变文字颜色。 */
        DYE,
        /** 荧光墨囊：文字发光。 */
        GLOW,
        /** 墨囊：取消发光。 */
        UNGLOW,
        /** 蜜脾：涂蜡。 */
        WAX;

        /**
         * 识别手中物品对应的作用类型。
         *
         * @param stack 手中物品
         * @return 对应类型；该物品不能作用于字牌时返回 null
         */
        @Nullable
        static Applicator of(ItemStack stack) {
            if (stack.has(DataComponents.DYE)) {
                return DYE;
            }
            if (stack.is(Items.GLOW_INK_SAC)) {
                return GLOW;
            }
            if (stack.is(Items.INK_SAC)) {
                return UNGLOW;
            }
            if (stack.is(Items.HONEYCOMB)) {
                return WAX;
            }
            return null;
        }

        /**
         * 施加效果并播放对应音效 / 粒子。
         *
         * @param level 服务端世界
         * @param sign  目标字牌
         * @param stack 手中物品
         * @param pos   字牌坐标
         * @return 状态确实发生了变化时返回 true
         */
        boolean apply(ServerLevel level, LargeSignBlockEntity sign, ItemStack stack, BlockPos pos) {
            switch (this) {
                case DYE -> {
                    DyeColor dyeColor = stack.get(DataComponents.DYE);
                    if (dyeColor == null || !sign.setColor(dyeColor)) {
                        return false;
                    }
                    level.playSound(null, pos, SoundEvents.DYE_USE, SoundSource.BLOCKS, 1.0f, 1.0f);
                    return true;
                }
                case GLOW -> {
                    if (!sign.setGlowing(true)) {
                        return false;
                    }
                    level.playSound(null, pos, SoundEvents.GLOW_INK_SAC_USE, SoundSource.BLOCKS, 1.0f, 1.0f);
                    return true;
                }
                case UNGLOW -> {
                    if (!sign.setGlowing(false)) {
                        return false;
                    }
                    level.playSound(null, pos, SoundEvents.INK_SAC_USE, SoundSource.BLOCKS, 1.0f, 1.0f);
                    return true;
                }
                case WAX -> {
                    if (!sign.setWaxed(true)) {
                        return false;
                    }
                    // 与原版涂蜡一致：播放蜡层生成的粒子与音效
                    level.levelEvent(null, LevelEvent.PARTICLES_AND_SOUND_WAX_ON, pos, 0);
                    return true;
                }
            }
            return false;
        }
    }
}
