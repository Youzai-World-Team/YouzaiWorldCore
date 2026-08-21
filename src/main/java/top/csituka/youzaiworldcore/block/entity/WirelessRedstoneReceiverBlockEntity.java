package top.csituka.youzaiworldcore.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import top.csituka.youzaiworldcore.redstone.WirelessRedstoneNetwork;
import top.csituka.youzaiworldcore.util.DebugLogger;

/**
 * 无线红石接收器的方块实体：每 tick 把自身方块状态的 {@code powered} 收敛到
 * 「{@link WirelessRedstoneNetwork} 索引里，我这个频道 32 格内有没有激活的发射器」。
 * <p>
 * <b>为什么用「接收器轮询」而不是「发射器推送」</b>：
 * 推送要在发射器状态变化时反向找出范围内的所有接收器，还得额外处理
 * 「发射器所在区块卸载时怎么通知别人断电」——而区块卸载途中去改写其他区块的方块状态
 * 是件危险的事。轮询把判定收敛到接收器自己身上：发射器只要从索引里消失
 * （破坏 / 卸载 / 断电 / 改频道），接收器下一 tick 自然断电，没有任何跨区块写入。
 * <p>
 * <b>轮询开销</b>：靠 {@link WirelessRedstoneNetwork#generation()} 做版本号缓存，
 * 稳态下每 tick 只有两次基本类型比较，既不查哈希表也不算距离，更不分配对象；
 * 只有当真的有发射器进出索引 / 通断 / 改频道之后，才会重新查一次索引。
 * 因此「摆一屋子接收器」的静置开销可以忽略，响应速度仍与有线红石相当。
 *
 * @see top.csituka.youzaiworldcore.block.WirelessRedstoneReceiverBlock
 */
@SuppressWarnings("null")
public class WirelessRedstoneReceiverBlockEntity extends WirelessRedstoneBlockEntity {

    private static final String MODULE = "WirelessRedstoneReceiverBlockEntity";

    /**
     * 上次真正查询索引时的索引版本号；{@link Long#MIN_VALUE} 表示「缓存无效，必须重查」。
     * <p>
     * 与 {@link #cachedInRange} 一起构成每 tick 的快速路径，见
     * {@link WirelessRedstoneNetwork#generation()}。不持久化：方块实体新建时天然为无效值。
     */
    private long cachedGeneration = Long.MIN_VALUE;

    /** 上次查询的结果：范围内是否存在同频道的激活发射器。 */
    private boolean cachedInRange;

    public WirelessRedstoneReceiverBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.WIRELESS_REDSTONE_RECEIVER, pos, state);
    }

    @Override
    protected String logModule() {
        return MODULE;
    }

    @Override
    public boolean isTransmitter() {
        return false;
    }

    /**
     * {@inheritDoc}
     * <p>
     * 频道变了，缓存的查询结果就不再适用（版本号没变也一样），必须强制重查一次。
     */
    @Override
    protected void onChannelChanged(int oldChannel, int newChannel) {
        cachedGeneration = Long.MIN_VALUE;
    }

    /**
     * 服务端每 tick 回调：把方块状态的 {@code powered} 收敛到「索引查询结果」。
     * <p>
     * <b>稳态开销</b>：只要没有任何发射器发生变化，索引版本号就不动，本方法只做
     * 一次 {@code long} 比较和一次 {@code boolean} 比较后返回——不查哈希表、不算距离、
     * 不分配对象。真正的索引查询只在「有发射器进出索引 / 通断 / 改频道」之后的
     * 那一 tick 发生一次。
     * <p>
     * 只在结果与现状不一致时才 {@code setBlock}，因此稳态下不产生任何方块更新。
     * 写入用 {@code UPDATE_ALL}，其中的 {@code UPDATE_NEIGHBORS} 位会向六向邻居广播，
     * 让四周的红石线在同一时刻刷新——这一点对接收器是<b>必需</b>的，
     * 否则旁边的红石线不会跟着亮灭。
     * <p>
     * 注意缓存的是「索引查询结果」而不是「最终决定」：因此即便有人用 {@code /setblock}
     * 把 {@code powered} 改成错的值，下一 tick 的对比仍会把它纠正回来。
     *
     * @param level       所在世界
     * @param pos         接收器坐标
     * @param state       当前方块状态
     * @param blockEntity 接收器方块实体
     */
    public static void serverTick(Level level, BlockPos pos, BlockState state,
                                  WirelessRedstoneReceiverBlockEntity blockEntity) {
        if (level.isClientSide()) {
            return;
        }

        long generation = WirelessRedstoneNetwork.generation();
        if (generation != blockEntity.cachedGeneration) {
            blockEntity.cachedInRange = WirelessRedstoneNetwork.hasActiveTransmitterInRange(
                    level, pos, blockEntity.getChannel());
            blockEntity.cachedGeneration = generation;
        }

        boolean shouldBePowered = blockEntity.cachedInRange;
        boolean isPowered = state.getValue(BlockStateProperties.POWERED);

        if (shouldBePowered == isPowered) {
            return;
        }

        level.setBlock(pos, state.setValue(BlockStateProperties.POWERED, shouldBePowered),
                Block.UPDATE_ALL);
        DebugLogger.stateChange(MODULE, "receiver@" + pos.toShortString(),
                "powered", isPowered, shouldBePowered);
    }
}
