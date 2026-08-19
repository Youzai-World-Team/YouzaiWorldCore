package top.csituka.youzaiworldcore.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.SupportType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition.Builder;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.redstone.ExperimentalRedstoneUtils;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.NonNull;
import top.csituka.youzaiworldcore.util.DebugLogger;

/**
 * 非门红石中继器（NOT Gate Redstone Repeater）。
 * <p>
 * 一个<b>水平朝向</b>（跟随贴图方向）的逻辑反转元件，行为与原版红石中继器一致的方向约定：
 * <ul>
 *   <li>方向：输入 / 输出<b>相对贴图</b>而非世界坐标——贴图<b>上方</b>是输出端（{@link #FACING} 方向），
 *       贴图<b>下方</b>是输入端（{@code FACING.getOpposite()} 方向）。</li>
 *   <li>输入：贴图下方（{@code pos.relative(facing.getOpposite())}）的红石信号（任意强度 &gt; 0 即视为真）。</li>
 *   <li>输出：贴图上方（{@code pos.relative(facing)}）的满强度（{@code 15}）红石信号，且<b>仅在输入为 0 时输出</b>。</li>
 *   <li>状态（{@link #POWERED}）：{@code true} 表示<b>正在输出信号</b>（即输入侧无信号）；
 *       {@code false} 表示<b>未输出</b>（即输入侧有信号）。</li>
 * </ul>
 * <p>
 * 与原版红石中继器（{@code RepeaterBlock}）不同：
 * <ul>
 *   <li>无延迟（{@link #tick} 不调度）；输出几乎在输入变化的同一 tick 完成。</li>
 *   <li>逻辑被反相（NOT）。</li>
 * </ul>
 * <p>
 * <b>状态更新采用「调度 tick」模式（对齐 vanilla {@code DiodeBlock}）</b>：
 * <ol>
 *   <li>{@link #onPlace} / {@link #neighborChanged} 只负责<b>调度</b>一次 tick；
 *       绝不在 {@code onPlace} 内同步调用 {@code setBlock}——否则会触发
 *       {@code setBlock → LevelChunk.setBlockState → onPlace} 的无限递归（StackOverflowError）。</li>
 *   <li>{@link #tick} 中才真正改写 {@code POWERED}（仅当与当前输入不一致时），
 *       随后由 {@code setBlock} 的 {@code UPDATE_ALL} 位自动向六向邻居广播。</li>
 * </ol>
 *
 * @see HorizontalDirectionalBlock
 * @see BlockStateProperties#POWERED
 * @see Orientation
 */
@SuppressWarnings("null")
public class NotGateRedstoneRepeaterBlock extends HorizontalDirectionalBlock {

    /**
     * 数据驱动的红石信号属性（{@code powered=true} 时向贴图上方输出强度 15）。
     * <p>
     * 命名遵循原版 {@link BlockStateProperties#POWERED}，使得
     * {@code blockstates/} JSON 中的 {@code "powered=true/false"} 变体能被自动消费。
     */
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;

    public static final MapCodec<NotGateRedstoneRepeaterBlock> CODEC = simpleCodec(NotGateRedstoneRepeaterBlock::new);

    /**
     * 薄板碰撞体积（{@code 16x16x2}），与原版红石中继器一致。
     * <p>
     * 视觉模型同样只有 2px 厚（见 {@code assets/youzaiworldcore/models/block/not_gate_redstone_repeater*.json}），
     * 因此碰撞与渲染对齐。
     */
    private static final VoxelShape SHAPE = Block.column(16.0, 0.0, 2.0);

    /** 响应延迟（tick 数）。1 = 与 vanilla 红石中继器最低档一致。 */
    private static final int TICK_DELAY = 1;

    public NotGateRedstoneRepeaterBlock(BlockBehaviour.Properties properties) {
        super(properties);
        DebugLogger.entering("NotGateRedstoneRepeaterBlock", "constructor");
        // 默认 POWERED=true（激活输出）；放置后若输入侧实际有信号，下一次 tick 会翻到 false。
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(POWERED, Boolean.TRUE));
        DebugLogger.exiting("NotGateRedstoneRepeaterBlock", "constructor",
                "defaultState=facing=north,powered=true");
    }

    @Override
    @NonNull
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    @NonNull
    protected VoxelShape getShape(@NonNull BlockState state, @NonNull BlockGetter level,
                                  @NonNull BlockPos pos, @NonNull CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected void createBlockStateDefinition(@NonNull Builder<Block, BlockState> builder) {
        builder.add(FACING, POWERED);
    }

    @Override
    @NonNull
    public BlockState getStateForPlacement(@NonNull BlockPlaceContext context) {
        // FACING = 玩家水平朝向（不取反）：输出端（贴图上方）朝玩家面向的方向，
        // 输入端（贴图下方 = FACING 的反方向）朝向放置者自己。
        return this.defaultBlockState()
                .setValue(FACING, context.getHorizontalDirection());
    }

    // ====================================================================
    // 物理支撑：仅当下方为完整上表面时允许保留
    // ====================================================================

    /**
     * 仅当正下方为刚性支撑时该元件可存活。
     * <p>
     * 与原版红石中继器 / 比较器一致：下方必须是固体方块、可放置方块的顶面，
     * 否则该元件悬空，应当自动掉落。
     */
    @Override
    protected boolean canSurvive(@NonNull BlockState state, @NonNull LevelReader level, @NonNull BlockPos pos) {
        BlockPos below = pos.below();
        return level.getBlockState(below).isFaceSturdy(level, below, Direction.UP, SupportType.RIGID);
    }

    // ====================================================================
    // 放置 / 移除 / 邻居通知（只调度，不直接改状态）
    // ====================================================================

    @Override
    protected void onPlace(@NonNull BlockState state, @NonNull Level level, @NonNull BlockPos pos,
                           @NonNull BlockState oldState, boolean movedByPiston) {
        DebugLogger.entering("NotGateRedstoneRepeaterBlock", "onPlace", "pos=" + pos);

        super.onPlace(state, level, pos, oldState, movedByPiston);

        if (!level.isClientSide()) {
            // 首次放置：若当前 POWERED 与输入侧信号不一致，调度一次 tick 让其收敛。
            scheduleEvaluationIfNeeded(level, pos, state);
        }

        DebugLogger.exiting("NotGateRedstoneRepeaterBlock", "onPlace");
    }

    @Override
    protected void affectNeighborsAfterRemoval(@NonNull BlockState state, @NonNull ServerLevel level,
                                               @NonNull BlockPos pos, boolean moved) {
        DebugLogger.entering("NotGateRedstoneRepeaterBlock", "affectNeighborsAfterRemoval", "pos=" + pos);
        // 移除后向六向邻居广播；输出端邻居失去信号源需重新评估。
        broadcastNeighborUpdates(level, pos);
        DebugLogger.exiting("NotGateRedstoneRepeaterBlock", "affectNeighborsAfterRemoval");
    }

    @Override
    protected void neighborChanged(@NonNull BlockState state, @NonNull Level level, @NonNull BlockPos pos,
                                   @NonNull Block block, @NonNull Orientation orientation, boolean movedByPiston) {
        DebugLogger.entering("NotGateRedstoneRepeaterBlock", "neighborChanged", "pos=" + pos);

        super.neighborChanged(state, level, pos, block, orientation, movedByPiston);

        if (level.isClientSide()) {
            DebugLogger.exiting("NotGateRedstoneRepeaterBlock", "neighborChanged", "clientSide, skip");
            return;
        }

        // 1. 下方支撑方块被拆除 → 实例掉落
        if (!state.canSurvive(level, pos)) {
            Block.dropResources(state, level, pos);
            level.removeBlock(pos, false);
            broadcastNeighborUpdates(level, pos);
            DebugLogger.info("NotGateRedstoneRepeaterBlock",
                    "支撑方块消失，于 %s 处掉落本元件", pos.toShortString());
            DebugLogger.exiting("NotGateRedstoneRepeaterBlock", "neighborChanged", "dropped (no support)");
            return;
        }

        // 2. 任意邻居变化（含输入侧红石线信号强度变化）→ 若状态需要翻转则调度 tick
        scheduleEvaluationIfNeeded(level, pos, state);
        DebugLogger.exiting("NotGateRedstoneRepeaterBlock", "neighborChanged");
    }

    // ====================================================================
    // 核心：NOT 逻辑的延迟执行（vanilla 调度 tick 模式）
    // ====================================================================

    /**
     * 若当前 {@code POWERED} 与输入侧信号推导出的目标状态不一致，且本 tick 尚无
     * 挂起的调度，则安排一次 {@link #TICK_DELAY} tick 后的评估。
     * <p>
     * 这是 {@code onPlace} / {@code neighborChanged} 与 {@link #tick} 之间的唯一桥梁：
     * 两个钩子都<b>不直接改写状态</b>，只做调度，从而避免
     * {@code setBlock → onPlace → setBlock} 递归。
     */
    private void scheduleEvaluationIfNeeded(Level level, BlockPos pos, BlockState state) {
        boolean shouldBeActive = readInputSignal(level, pos, state) <= 0;
        if (shouldBeActive != state.getValue(POWERED)
                && !level.getBlockTicks().willTickThisTick(pos, this)) {
            level.scheduleTick(pos, this, TICK_DELAY);
            if (DebugLogger.isEnabled(DebugLogger.LEVEL_DETAILED)) {
                DebugLogger.debug("NotGateRedstoneRepeaterBlock",
                        "scheduleEvaluation @%s: POWERED=%s, shouldBeActive=%s -> tick=%d",
                        pos.toShortString(), state.getValue(POWERED), shouldBeActive, TICK_DELAY);
            }
        }
    }

    /**
     * 调度 tick 的处理器：读取输入侧信号并按"反相"规则设置 {@code POWERED}（仅当与当前状态不同时）。
     * <p>
     * {@code setBlock} 使用 {@code UPDATE_ALL} flags，会自动向六向邻居广播，
     * 因此输出端红石线 / 接收方块会在同一时刻刷新——与 vanilla {@code DiodeBlock.tick} 一致。
     * <p>
     * 注意：这里允许 {@code setBlock} 触发 {@code onPlace}（onPlace 只做调度、不递归），
     * 所以不会再次进入本方法，天然收敛。
     */
    @Override
    protected void tick(@NonNull BlockState state, @NonNull ServerLevel level, @NonNull BlockPos pos,
                        @NonNull RandomSource random) {
        DebugLogger.entering("NotGateRedstoneRepeaterBlock", "tick", "pos=" + pos);

        int inputSignal = readInputSignal(level, pos, state);
        boolean shouldBeActive = inputSignal <= 0;            // NOT 逻辑
        boolean isActive = state.getValue(POWERED);

        if (DebugLogger.isEnabled(DebugLogger.LEVEL_DETAILED)) {
            DebugLogger.debug("NotGateRedstoneRepeaterBlock",
                    "tick @%s: facing=%s, inputSignal=%d, shouldBeActive=%s, isActive=%s",
                    pos.toShortString(), state.getValue(FACING), inputSignal, shouldBeActive, isActive);
        }

        if (shouldBeActive != isActive) {
            BlockState newState = state.setValue(POWERED, shouldBeActive);
            level.setBlock(pos, newState, Block.UPDATE_ALL);
            DebugLogger.stateChange("NotGateRedstoneRepeaterBlock", pos.toShortString(), "POWERED",
                    String.valueOf(isActive), String.valueOf(shouldBeActive));
        }

        DebugLogger.exiting("NotGateRedstoneRepeaterBlock", "tick");
    }

    /**
     * 向六个方向广播一次邻居更新，让邻居方块重新评估它们的红石信号。
     * <p>
     * 使用 {@link ExperimentalRedstoneUtils#initialOrientation} 派生一个"面朝被通知邻居"的
     * {@link Orientation}，符合 26.2 引入的新红石信号传播契约。
     */
    private void broadcastNeighborUpdates(Level level, BlockPos pos) {
        for (Direction direction : Direction.values()) {
            BlockPos neighborPos = pos.relative(direction);
            Orientation orientation = ExperimentalRedstoneUtils.initialOrientation(level, Direction.UP, direction);
            level.updateNeighborsAt(neighborPos, this, orientation);
        }
    }

    /**
     * 读取<b>贴图下方</b>（{@code pos.relative(facing.getOpposite())}）的红石输入信号强度。
     * <p>
     * <b>⚠️ 为什么不用 {@code level.getSignal(inputPos, facing)}：</b>
     * 26.2 的 {@code SignalGetter.getSignal} 在输入方块是<b>红石导体</b>
     * （{@code isRedstoneConductor=true}，如石头/泥土）时，会追加
     * {@code getDirectSignalTo(inputPos)} —— 它遍历 inputPos 的全部 6 个邻居，
     * 其中包含<b>本元件自身</b>，并以元件所在方向读取
     * {@code getDirectSignal(元件, 元件所在方向)}。
     * 若元件向该方向输出（贴图上方恰好是 inputPos 所在方向时），会把「自己的输出」
     * 读成「输入」，形成<b>自激振荡</b>（日志中每 tick 的 POWERED true↔false 无限翻转）。
     * <p>
     * 因此这里<b>直接读取输入方块自身的 {@code getSignal(inputPos, 朝向元件的方向)}</b>，
     * 绕过导体分支；红石线再以 {@code POWER} 属性无条件补正
     * （26.2 红石线 getSignal 可能因方向性返回 0，必须兜底）。
     *
     * @param level 所在 Level
     * @param pos   元件位置
     * @param state 元件当前方块状态（用于取 {@link #FACING}）
     * @return 信号强度 0..15
     */
    private static int readInputSignal(Level level, BlockPos pos, BlockState state) {
        Direction facing = state.getValue(FACING);          // 贴图上方（输出端）
        Direction inputDir = facing.getOpposite();          // 贴图下方（输入端）
        BlockPos inputPos = pos.relative(inputDir);

        BlockState inputState = level.getBlockState(inputPos);
        // 26.2 语义：BlockState.getSignal(level, pos, direction) 返回的是
        // "该方块向 direction.getOpposite()（即询问方所在方向）输出的信号"。
        // 输入方块位于元件的 inputDir 方向，元件位于输入方块的 facing 方向，
        // 因此要读"输入方块向 facing（朝向元件）输出"必须传 direction = inputDir（facing 的反方向）。
        int signal = inputState.getSignal(level, inputPos, inputDir);

        if (signal >= 15) {
            return signal;
        }

        if (inputState.is(net.minecraft.world.level.block.Blocks.REDSTONE_WIRE)) {
            int wirePower = inputState.getValue(net.minecraft.world.level.block.RedStoneWireBlock.POWER);
            return Math.max(signal, wirePower);
        }
        return signal;
    }

    // ====================================================================
    // 红石信号方法：作为"信号源"仅向贴图上方（FACING）输出
    // ====================================================================

    @Override
    protected boolean isSignalSource(@NonNull BlockState state) {
        return true;
    }

    /**
     * 输出信号：仅当 {@code POWERED=true} 时向贴图上方（{@link #FACING}）输出 15；
     * 其他方向输出 0。
     * <p>
     * <b>⚠️ 26.2 方向语义（反编译火把 / 红石线 / getBestNeighborSignal 确认）</b>：
     * {@code BlockState.getSignal(level, pos, direction)} 返回的是该方块向
     * {@code direction.getOpposite()}（即询问方所在方向）输出的信号——
     * 红石线通过 {@code getBestNeighborSignal} 以"邻居相对红石线的方向"调用
     * {@code getSignal(邻居, dir)}，实际收到的是"邻居向 dir 反方向（朝红石线）的输出"。
     * <p>
     * 因此元件的输出端（贴图上方 = {@code FACING}）邻居读取本元件时，传来的
     * {@code direction} 是 {@code FACING.getOpposite()}（元件相对该邻居的方向），
     * 判断条件必须是 {@code direction.getOpposite() == FACING}
     * （即 {@code direction == FACING.getOpposite()}）。
     * <p>
     * 若按旧版习惯写成 {@code direction == FACING}，元件会把 15 输出到贴图<b>下方</b>
     * （输入端方向），导致：输出端红石线不亮 + 输入端红石线被点亮形成反馈振荡。
     */
    @Override
    protected int getSignal(@NonNull BlockState state, @NonNull BlockGetter level,
                            @NonNull BlockPos pos, @NonNull Direction direction) {
        return direction == state.getValue(FACING).getOpposite() && state.getValue(POWERED) ? 15 : 0;
    }

    /**
     * 直接信号：与 {@link #getSignal} 同语义，因为本元件的"信号输出"不依赖任何中间方块
     * （不像原版的红石中继器需要通过自己的方块模型判断"是否穿过"）。
     */
    @Override
    protected int getDirectSignal(@NonNull BlockState state, @NonNull BlockGetter level,
                                  @NonNull BlockPos pos, @NonNull Direction direction) {
        return getSignal(state, level, pos, direction);
    }
}
