package top.csituka.youzaiworldcore.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.NonNull;
import top.csituka.youzaiworldcore.block.entity.WirelessRedstoneTransmitterBlockEntity;
import top.csituka.youzaiworldcore.redstone.WirelessRedstoneNetwork;
import top.csituka.youzaiworldcore.util.DebugLogger;

/**
 * 无线红石发射器。
 * <p>
 * <b>行为</b>：任意<b>一个侧边</b>（四个水平方向）有红石信号进入时进入激活态，
 * 并把「本坐标 + 频道」登记进 {@link WirelessRedstoneNetwork} 索引；
 * 该索引会被 {@value WirelessRedstoneNetwork#RANGE} 格内、
 * <b>频道号相同</b>的无线红石接收器读取，从而在远端产生红石信号。
 * 右键可设置频道，见 {@link WirelessRedstoneComponentBlock#useWithoutItem}。
 * <p>
 * <b>本方块自身不输出任何红石信号</b>：{@link #getSignal} 恒为 0。
 * 但 {@link #isSignalSource} 仍返回 {@code true}——这不是笔误：
 * 原版 {@code RedStoneWireBlock.shouldConnectTo} 只在目标方块
 * {@code isSignalSource()} 为真时才让红石线「连过来」，而红石线的
 * {@code getSignal} 又只对<b>已连接的方向</b>返回自身强度。
 * 若这里返回 false，红石线既不会在视觉上连到发射器，发射器也<b>永远读不到红石线的信号</b>。
 * <p>
 * <b>状态更新采用「调度 tick」模式（对齐 vanilla {@code DiodeBlock}）</b>：
 * {@code onPlace} / {@code neighborChanged} 只<b>调度</b>一次 tick，绝不同步调用
 * {@code setBlock}——否则会触发 {@code setBlock → onPlace → setBlock} 的无限递归；
 * 真正改写 {@link #POWERED} 只发生在 {@link #tick} 里。
 *
 * @see WirelessRedstoneReceiverBlock
 * @see WirelessRedstoneTransmitterBlockEntity
 */
@SuppressWarnings("null")
public class WirelessRedstoneTransmitterBlock extends WirelessRedstoneComponentBlock {

    private static final String MODULE = "WirelessRedstoneTransmitterBlock";

    public static final MapCodec<WirelessRedstoneTransmitterBlock> CODEC =
            simpleCodec(WirelessRedstoneTransmitterBlock::new);

    /** 响应延迟（tick 数）。1 = 与 vanilla 红石中继器最低档一致。 */
    private static final int TICK_DELAY = 1;

    public WirelessRedstoneTransmitterBlock(Properties properties) {
        super(properties);
    }

    @Override
    @NonNull
    protected MapCodec<? extends WirelessRedstoneTransmitterBlock> codec() {
        return CODEC;
    }

    @Override
    protected String logModule() {
        return MODULE;
    }

    @Override
    public BlockEntity newBlockEntity(@NonNull BlockPos pos, @NonNull BlockState state) {
        return new WirelessRedstoneTransmitterBlockEntity(pos, state);
    }

    // ====================================================================
    // 放置 / 邻居通知（只调度，不直接改状态）
    // ====================================================================

    @Override
    protected void onPlace(@NonNull BlockState state, @NonNull Level level, @NonNull BlockPos pos,
                           @NonNull BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);

        if (!level.isClientSide()) {
            // 首次放置：若当前 POWERED 与侧边实际信号不一致，调度一次 tick 让其收敛
            scheduleEvaluationIfNeeded(level, pos, state);
        }
    }

    @Override
    protected void onSupportedNeighborChanged(BlockState state, Level level, BlockPos pos) {
        // 任意邻居变化（含侧边红石线强度变化）→ 若状态需要翻转则调度 tick
        scheduleEvaluationIfNeeded(level, pos, state);
    }

    /**
     * 若当前 {@code POWERED} 与侧边输入推导出的目标状态不一致，且本 tick 尚无挂起的调度，
     * 则安排一次 {@link #TICK_DELAY} tick 后的评估。
     * <p>
     * 这是 {@code onPlace} / {@code neighborChanged} 与 {@link #tick} 之间的唯一桥梁：
     * 两个钩子都<b>不直接改写状态</b>，只做调度，从而避免
     * {@code setBlock → onPlace → setBlock} 递归。
     */
    private void scheduleEvaluationIfNeeded(Level level, BlockPos pos, BlockState state) {
        boolean shouldBeActive = hasHorizontalInput(level, pos);
        if (shouldBeActive != state.getValue(POWERED)
                && !level.getBlockTicks().willTickThisTick(pos, this)) {
            level.scheduleTick(pos, this, TICK_DELAY);
            if (DebugLogger.isEnabled(DebugLogger.LEVEL_DETAILED)) {
                DebugLogger.debug(MODULE,
                        "scheduleEvaluation @%s: POWERED=%s, shouldBeActive=%s -> tick=%d",
                        pos.toShortString(), state.getValue(POWERED), shouldBeActive, TICK_DELAY);
            }
        }
    }

    // ====================================================================
    // 核心：侧边输入 → 激活态 → 无线索引
    // ====================================================================

    /**
     * 调度 tick 的处理器：把 {@code POWERED} 收敛到「四个侧边是否有红石信号进入」，
     * 并同步无线索引。
     * <p>
     * 即使 {@code POWERED} 没有变化也会刷一次索引：读档后的第一次评估、
     * 或索引因异常而与现状脱节时，这一步能把两者拉回一致（{@code refreshNetworkIndex} 幂等）。
     */
    @Override
    protected void tick(@NonNull BlockState state, @NonNull ServerLevel level, @NonNull BlockPos pos,
                        @NonNull RandomSource random) {
        boolean shouldBeActive = hasHorizontalInput(level, pos);
        boolean isActive = state.getValue(POWERED);

        if (DebugLogger.isEnabled(DebugLogger.LEVEL_DETAILED)) {
            DebugLogger.debug(MODULE, "tick @%s: shouldBeActive=%s, isActive=%s",
                    pos.toShortString(), shouldBeActive, isActive);
        }

        if (shouldBeActive != isActive) {
            level.setBlock(pos, state.setValue(POWERED, shouldBeActive), Block.UPDATE_ALL);
            DebugLogger.stateChange(MODULE, "transmitter@" + pos.toShortString(),
                    "POWERED", isActive, shouldBeActive);
        }

        // setBlock 只换属性、不换方块，方块实体会原样保留，因此这里取到的仍是同一个实例
        if (level.getBlockEntity(pos) instanceof WirelessRedstoneTransmitterBlockEntity transmitter) {
            transmitter.refreshNetworkIndex();
        }
    }

    /**
     * 判断四个侧边是否有红石信号进入。
     * <p>
     * <b>方向语义</b>（由反编译 {@code SignalGetter.getBestNeighborSignal} 核实）：
     * 原版读「邻居向本方块输出多少」的写法是 {@code getSignal(pos.relative(dir), dir)}，
     * 即 {@code direction} 传的是「本方块 → 邻居」的方向，这里照搬。
     * <p>
     * 每个侧边都用<b>原版自带的两个取信号原语</b>各读一次，任一为正即视为有输入，
     * 而不自己拼判定逻辑：
     * <ol>
     *   <li>{@link Level#getSignal} —— 常规取信号。邻居是红石导体（石头 / 泥土等）时
     *       会追加 {@code getDirectSignalTo}，因此「被拉杆或中继器强充能的实心方块」
     *       也能驱动本元件，与原版红石中继器读后方输入的行为一致；</li>
     *   <li>{@link Level#getControlInputSignal}（{@code diodesOnly=false}）——
     *       原版元件读「侧边输入」的专用方法，直接认红石块、红石线的 {@code POWER}
     *       与信号源的强信号。它绕开了 26.2 红石线 {@code getSignal} 的方向性：
     *       红石线只会向「视觉上连过去」的方向报告强度，这一步保证紧贴的红石线
     *       无论连接状态如何都能被读到。</li>
     * </ol>
     * 本方块的 {@link #getSignal} 恒为 0，因此不存在「把自己的输出读成输入」的自激风险。
     * <p>
     * 复用一个 {@link BlockPos.MutableBlockPos} 遍历四个侧边：本方法在大型红石电路里
     * 会被邻居更新频繁触发，逐个 {@code pos.relative()} 会产生 4 个短命对象；
     * 两个取信号原语都只读坐标、不会把它存下来，因此复用是安全的。
     *
     * @param level 所在世界
     * @param pos   发射器坐标
     * @return 任意侧边有信号（强度 &gt; 0）时返回 true
     */
    private static boolean hasHorizontalInput(Level level, BlockPos pos) {
        BlockPos.MutableBlockPos neighborPos = new BlockPos.MutableBlockPos();
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            neighborPos.setWithOffset(pos, direction);

            if (level.getSignal(neighborPos, direction) > 0
                    || level.getControlInputSignal(neighborPos, direction, false) > 0) {
                return true;
            }
        }
        return false;
    }

    // ====================================================================
    // 红石信号方法：本元件不输出，但必须「看起来像信号源」
    // ====================================================================

    /**
     * 恒为 {@code true}。
     * <p>
     * 见类注释：这是让红石线愿意连过来、从而使发射器能读到红石线信号的前提，
     * 而非表示本方块真的会输出信号（{@link #getSignal} 恒为 0）。
     */
    @Override
    protected boolean isSignalSource(@NonNull BlockState state) {
        return true;
    }

    /** 发射器不向任何方向输出红石信号——它的「输出」是无线的。 */
    @Override
    protected int getSignal(@NonNull BlockState state, @NonNull BlockGetter level,
                            @NonNull BlockPos pos, @NonNull Direction direction) {
        return 0;
    }

    /** 发射器不向任何方向输出红石信号——它的「输出」是无线的。 */
    @Override
    protected int getDirectSignal(@NonNull BlockState state, @NonNull BlockGetter level,
                                  @NonNull BlockPos pos, @NonNull Direction direction) {
        return 0;
    }
}
