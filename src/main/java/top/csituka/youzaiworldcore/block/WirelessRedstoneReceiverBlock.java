package top.csituka.youzaiworldcore.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.NonNull;
import top.csituka.youzaiworldcore.block.entity.ModBlockEntities;
import top.csituka.youzaiworldcore.block.entity.WirelessRedstoneReceiverBlockEntity;
import top.csituka.youzaiworldcore.redstone.WirelessRedstoneNetwork;

/**
 * 无线红石接收器。
 * <p>
 * <b>行为</b>：完全无视自身周围的有线红石输入，只每 tick 向
 * {@link WirelessRedstoneNetwork} 索引查询「{@value WirelessRedstoneNetwork#RANGE} 格内
 * 有没有<b>频道号相同</b>的激活发射器」；有则进入激活态，并向<b>四个侧边</b>
 * 输出强度 15 的红石信号。右键可设置频道，见
 * {@link WirelessRedstoneComponentBlock#useWithoutItem}。
 * <p>
 * 轮询逻辑在 {@link WirelessRedstoneReceiverBlockEntity#serverTick} 里，
 * 那里也解释了为什么采用「接收器轮询」而不是「发射器推送」。
 * <p>
 * 因为接收器不读有线输入、发射器不输出有线信号，两者之间不存在
 * 「把自己的输出读成输入」的通路，所以不会出现自激振荡。
 *
 * @see WirelessRedstoneTransmitterBlock
 * @see WirelessRedstoneReceiverBlockEntity
 */
@SuppressWarnings("null")
public class WirelessRedstoneReceiverBlock extends WirelessRedstoneComponentBlock {

    private static final String MODULE = "WirelessRedstoneReceiverBlock";

    public static final MapCodec<WirelessRedstoneReceiverBlock> CODEC =
            simpleCodec(WirelessRedstoneReceiverBlock::new);

    public WirelessRedstoneReceiverBlock(Properties properties) {
        super(properties);
    }

    @Override
    @NonNull
    protected MapCodec<? extends WirelessRedstoneReceiverBlock> codec() {
        return CODEC;
    }

    @Override
    protected String logModule() {
        return MODULE;
    }

    @Override
    public BlockEntity newBlockEntity(@NonNull BlockPos pos, @NonNull BlockState state) {
        return new WirelessRedstoneReceiverBlockEntity(pos, state);
    }

    /**
     * 只在服务端挂 ticker：通电判定是权威逻辑，客户端只需接收方块状态同步。
     */
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(@NonNull Level level,
                                                                  @NonNull BlockState state,
                                                                  @NonNull BlockEntityType<T> blockEntityType) {
        if (level.isClientSide()) {
            return null;
        }
        return createTickerHelper(blockEntityType, ModBlockEntities.WIRELESS_REDSTONE_RECEIVER,
                WirelessRedstoneReceiverBlockEntity::serverTick);
    }

    // ====================================================================
    // 移除后的收尾
    // ====================================================================

    /**
     * 被破坏后向六向邻居广播一次更新。
     * <p>
     * 否则接收器在通电状态下被拆除时，四周的红石线不会立刻失去信号。
     */
    @Override
    protected void affectNeighborsAfterRemoval(@NonNull BlockState state, @NonNull ServerLevel level,
                                               @NonNull BlockPos pos, boolean moved) {
        broadcastNeighborUpdates(level, pos);
    }

    // ====================================================================
    // 红石信号方法：作为信号源仅向四个侧边输出
    // ====================================================================

    @Override
    protected boolean isSignalSource(@NonNull BlockState state) {
        return true;
    }

    /**
     * 输出信号：{@code POWERED=true} 时向四个<b>侧边</b>输出 15，上下两面输出 0。
     * <p>
     * <b>⚠️ 26.2 方向语义</b>（由反编译 {@code SignalGetter.getBestNeighborSignal} 核实）：
     * {@code BlockState.getSignal(level, pos, direction)} 返回的是本方块向
     * {@code direction.getOpposite()}（即<b>询问方所在的方向</b>）输出的信号——
     * 邻居是通过 {@code getSignal(pos.relative(dir), dir)} 来询问的。
     * <p>
     * 因此「向四个侧边输出」的判定条件是 {@code direction} 为水平方向：
     * {@code direction} 水平 ⟺ {@code direction.getOpposite()}（询问方所在方向）水平。
     * 询问方在上方或下方时 {@code direction} 为 {@code UP} / {@code DOWN}，返回 0。
     */
    @Override
    protected int getSignal(@NonNull BlockState state, @NonNull BlockGetter level,
                            @NonNull BlockPos pos, @NonNull Direction direction) {
        return state.getValue(POWERED) && direction.getAxis().isHorizontal() ? 15 : 0;
    }

    /**
     * 直接（强）信号：与 {@link #getSignal} 同语义。
     * <p>
     * 于是紧贴四个侧边的实心方块会被强充能，其上的红石线也能被点亮——
     * 相当于四个侧边各接了一个原版红石中继器的输出端。
     */
    @Override
    protected int getDirectSignal(@NonNull BlockState state, @NonNull BlockGetter level,
                                  @NonNull BlockPos pos, @NonNull Direction direction) {
        return getSignal(state, level, pos, direction);
    }
}
