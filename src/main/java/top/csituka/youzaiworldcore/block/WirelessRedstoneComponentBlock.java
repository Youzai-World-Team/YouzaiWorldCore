package top.csituka.youzaiworldcore.block;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SupportType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.redstone.ExperimentalRedstoneUtils;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.NonNull;
import top.csituka.youzaiworldcore.block.entity.WirelessRedstoneBlockEntity;
import top.csituka.youzaiworldcore.network.WirelessRedstoneOpenChannelPayload;
import top.csituka.youzaiworldcore.util.DebugLogger;

/**
 * 无线红石元件（发射器 / 接收器）的公共基类。
 * <p>
 * 两种元件外观与物理特性完全相同，只有信号流向相反，因此把共性收在这里：
 * <ul>
 *   <li>{@link #POWERED} 状态属性 —— 驱动 {@code blockstates/} JSON 在
 *       「正常态 / 激活态」两套贴图之间切换；</li>
 *   <li>2 像素厚的薄板形状，与原版红石中继器一致（视觉模型也只有 2px 厚，碰撞与渲染对齐）；</li>
 *   <li>必须有刚性支撑：与原版红石中继器 / 比较器一样，下方方块消失就掉落；</li>
 *   <li>右键打开频道设置界面（服务端校验后下发 S2C 包，见
 *       {@link WirelessRedstoneOpenChannelPayload}）。</li>
 * </ul>
 * <b>没有 {@code FACING} 属性</b>：两种元件的输入与输出都作用于<b>四个侧边</b>，
 * 朝向在功能上毫无意义，贴图本身也是四向对称的。加一个不影响任何行为的朝向属性
 * 只会让方块状态翻四倍，还会误导玩家以为「摆放方向有讲究」。
 *
 * @see WirelessRedstoneTransmitterBlock
 * @see WirelessRedstoneReceiverBlock
 * @see top.csituka.youzaiworldcore.redstone.WirelessRedstoneNetwork
 */
@SuppressWarnings("null")
public abstract class WirelessRedstoneComponentBlock extends BaseEntityBlock {

    /**
     * 激活状态：发射器表示「侧边有红石信号进入」，接收器表示「正在向侧边输出信号」。
     * <p>
     * 命名沿用原版 {@link BlockStateProperties#POWERED}，使
     * {@code blockstates/} JSON 里的 {@code "powered=true/false"} 变体能被自动消费。
     */
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;

    /** 薄板形状（{@code 16x2x16}），与原版红石中继器一致。 */
    protected static final VoxelShape SHAPE = Block.column(16.0, 0.0, 2.0);

    protected WirelessRedstoneComponentBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(POWERED, Boolean.FALSE));
    }

    /**
     * 供日志使用的模块名，由子类给出各自的类名。
     *
     * @return 模块名
     */
    protected abstract String logModule();

    @Override
    protected void createBlockStateDefinition(StateDefinition.@NonNull Builder<Block, BlockState> builder) {
        builder.add(POWERED);
    }

    @Override
    @NonNull
    protected VoxelShape getShape(@NonNull BlockState state, @NonNull BlockGetter level,
                                  @NonNull BlockPos pos, @NonNull CollisionContext context) {
        return SHAPE;
    }

    @Override
    @NonNull
    public RenderShape getRenderShape(@NonNull BlockState state) {
        // 有方块实体但没有自定义渲染器，仍按普通方块模型渲染
        return RenderShape.MODEL;
    }

    // ====================================================================
    // 物理支撑：仅当下方为完整上表面时允许保留
    // ====================================================================

    /**
     * 仅当正下方为刚性支撑时该元件可存活。
     * <p>
     * 与原版红石中继器 / 比较器一致：下方必须是固体方块可放置的顶面，
     * 否则该元件悬空，应当自动掉落。
     */
    @Override
    protected boolean canSurvive(@NonNull BlockState state, @NonNull LevelReader level, @NonNull BlockPos pos) {
        BlockPos below = pos.below();
        return level.getBlockState(below).isFaceSturdy(level, below, Direction.UP, SupportType.RIGID);
    }

    @Override
    protected void neighborChanged(@NonNull BlockState state, @NonNull Level level, @NonNull BlockPos pos,
                                   @NonNull Block block, @NonNull Orientation orientation, boolean movedByPiston) {
        super.neighborChanged(state, level, pos, block, orientation, movedByPiston);

        if (level.isClientSide()) {
            return;
        }

        // 下方支撑方块被拆除 → 本元件掉落
        if (!state.canSurvive(level, pos)) {
            Block.dropResources(state, level, pos);
            level.removeBlock(pos, false);
            broadcastNeighborUpdates(level, pos);
            DebugLogger.info(logModule(), "支撑方块消失，于 %s 处掉落本元件", pos.toShortString());
            return;
        }

        onSupportedNeighborChanged(state, level, pos);
    }

    /**
     * 支撑仍然完好时的邻居变化回调。
     * <p>
     * 发射器覆写它来重新评估侧边输入；接收器只听无线信号，因此不需要理会邻居变化。
     *
     * @param state 当前方块状态
     * @param level 所在世界（服务端）
     * @param pos   元件坐标
     */
    protected void onSupportedNeighborChanged(BlockState state, Level level, BlockPos pos) {
    }

    /**
     * 向六个方向广播一次邻居更新，让邻居方块重新评估它们的红石信号。
     * <p>
     * 使用 {@link ExperimentalRedstoneUtils#initialOrientation} 派生一个「面朝被通知邻居」的
     * {@link Orientation}，符合 26.2 引入的新红石信号传播契约。
     *
     * @param level 所在世界
     * @param pos   元件坐标
     */
    protected void broadcastNeighborUpdates(Level level, BlockPos pos) {
        for (Direction direction : Direction.values()) {
            BlockPos neighborPos = pos.relative(direction);
            Orientation orientation = ExperimentalRedstoneUtils.initialOrientation(level, Direction.UP, direction);
            level.updateNeighborsAt(neighborPos, this, orientation);
        }
    }

    // ====================================================================
    // 交互：右键设置频道
    // ====================================================================

    /**
     * 右键打开频道设置界面。
     * <p>
     * 与大字牌同一套流程：服务端确认「方块实体存在 + 玩家有建造权限」后，
     * 在方块实体上登记该玩家为本次唯一有权提交频道者，再下发
     * {@link WirelessRedstoneOpenChannelPayload} 让客户端弹出界面。
     * 真正的写入由 {@code ModNetworking} 里的 C2S 接收器复核后执行。
     * <p>
     * 潜行右键仍会走原版的「放置手中方块」逻辑，因此不影响正常搭建。
     */
    @Override
    @NonNull
    protected InteractionResult useWithoutItem(@NonNull BlockState state, @NonNull Level level,
                                              @NonNull BlockPos pos, @NonNull Player player,
                                              @NonNull BlockHitResult hitResult) {
        if (!(level.getBlockEntity(pos) instanceof WirelessRedstoneBlockEntity component)) {
            return InteractionResult.PASS;
        }

        if (!(level instanceof ServerLevel)) {
            // 客户端只做手部动画，真正的判定在服务端
            return InteractionResult.SUCCESS;
        }

        if (!player.mayBuild() || !(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.SUCCESS;
        }

        component.setAllowedPlayerEditor(serverPlayer.getUUID());
        ServerPlayNetworking.send(serverPlayer, new WirelessRedstoneOpenChannelPayload(
                pos, component.getChannel(), component.isTransmitter()));
        DebugLogger.info(logModule(), "玩家 %s 打开无线红石频道界面：pos=%s, channel=%d",
                serverPlayer.getName().getString(), pos.toShortString(), component.getChannel());
        return InteractionResult.SUCCESS;
    }
}
