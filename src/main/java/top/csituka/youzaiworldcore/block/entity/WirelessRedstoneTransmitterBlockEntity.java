package top.csituka.youzaiworldcore.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import top.csituka.youzaiworldcore.redstone.WirelessRedstoneNetwork;
import top.csituka.youzaiworldcore.util.DebugLogger;

/**
 * 无线红石发射器的方块实体：只存频道号，并负责把自己在
 * {@link WirelessRedstoneNetwork} 索引里的登记状态维持正确。
 * <p>
 * <b>索引同步的三个触发点</b>（缺一个就会出现「接收器该亮不亮 / 该灭不灭」）：
 * <ol>
 *   <li>{@link #setLevel(Level)} —— 方块被放置、或所在区块被加载。原版在此之前
 *       已完成 NBT 读档，所以这里读到的频道号就是存档里的值；</li>
 *   <li>{@link #setRemoved()} —— 方块被破坏，<b>以及所在区块被卸载</b>
 *       （{@code LevelChunk.clearAllBlockEntities} 会逐个调用它）；</li>
 *   <li>{@link #onChannelChanged(int, int)} 与
 *       {@link top.csituka.youzaiworldcore.block.WirelessRedstoneTransmitterBlock#tick}
 *       —— 改频道、通电/断电。</li>
 * </ol>
 * 通电状态本身不存在这里，而是读方块状态的 {@code powered} 属性——
 * 贴图切换本来就要靠它，再存一份只会有两个真相。
 *
 * @see top.csituka.youzaiworldcore.block.WirelessRedstoneTransmitterBlock
 */
@SuppressWarnings("null")
public class WirelessRedstoneTransmitterBlockEntity extends WirelessRedstoneBlockEntity {

    private static final String MODULE = "WirelessRedstoneTransmitterBlockEntity";

    public WirelessRedstoneTransmitterBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.WIRELESS_REDSTONE_TRANSMITTER, pos, state);
    }

    @Override
    protected String logModule() {
        return MODULE;
    }

    @Override
    public boolean isTransmitter() {
        return true;
    }

    // ===== 索引同步 =====

    /**
     * {@inheritDoc}
     * <p>
     * 放置与区块加载都会走到这里，是发射器「入索引」的唯一时机。
     */
    @Override
    public void setLevel(Level level) {
        super.setLevel(level);
        refreshNetworkIndex();
    }

    /**
     * {@inheritDoc}
     * <p>
     * 破坏方块与区块卸载共用此钩子，因此这里就是发射器「出索引」的唯一时机。
     */
    @Override
    public void setRemoved() {
        WirelessRedstoneNetwork.removeTransmitter(this.level, worldPosition);
        DebugLogger.exiting(MODULE, "setRemoved", "pos=" + worldPosition.toShortString());
        super.setRemoved();
    }

    @Override
    protected void onChannelChanged(int oldChannel, int newChannel) {
        // 改频道后旧频道的登记必须立刻失效，否则旧频道的接收器会继续通电
        refreshNetworkIndex();
    }

    /**
     * 按「当前频道 + 当前方块状态的通电情况」重写索引里的登记。
     * <p>
     * 幂等：重复调用不会产生重复条目，因此发射器方块的 {@code tick}
     * 可以无脑在每次状态收敛后调一次。
     */
    public void refreshNetworkIndex() {
        Level currentLevel = this.level;
        if (currentLevel == null || currentLevel.isClientSide() || isRemoved()) {
            return;
        }

        BlockState state = getBlockState();
        // 防御性判断：区块加载早期理论上可能读到还没换成本方块的状态
        boolean active = state.hasProperty(BlockStateProperties.POWERED)
                && state.getValue(BlockStateProperties.POWERED);

        WirelessRedstoneNetwork.setTransmitterActive(currentLevel, worldPosition, getChannel(), active);
    }
}
