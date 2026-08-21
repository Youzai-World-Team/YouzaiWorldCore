package top.csituka.youzaiworldcore.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.jspecify.annotations.NonNull;
import top.csituka.youzaiworldcore.redstone.WirelessRedstoneChannel;
import top.csituka.youzaiworldcore.util.DebugLogger;

import java.util.UUID;

/**
 * 无线红石元件方块实体的公共基类：保存频道号，并管理「打开频道界面 → 提交频道」的授权。
 * <p>
 * 发射器与接收器唯一的共同数据就是频道号，两边的持久化字段、同步方式、
 * 授权校验完全一致，因此集中在这里，子类只负责各自的行为差异：
 * <ul>
 *   <li>{@link WirelessRedstoneTransmitterBlockEntity} —— 把自己登记进
 *       {@link top.csituka.youzaiworldcore.redstone.WirelessRedstoneNetwork} 索引；</li>
 *   <li>{@link WirelessRedstoneReceiverBlockEntity} —— 每 tick 查询索引并改写自身通电状态。</li>
 * </ul>
 * 持久化字段只有 {@code Channel} 一个；通电状态存在方块状态的
 * {@code powered} 属性里（贴图要靠它切换激活态），不重复存进方块实体。
 * <p>
 * {@code playerWhoMayEdit} <b>不持久化</b>：与原版告示牌、本模组大字牌一致，
 * 仅在本次「服务端下发界面 → 客户端提交频道」的往返中有效，
 * 用来挡掉伪造 C2S 包去改别人元件频道的行为。
 *
 * @see WirelessRedstoneChannel
 */
@SuppressWarnings("null")
public abstract class WirelessRedstoneBlockEntity extends BlockEntity {

    /** 允许设置频道的最远距离（平方值），与原版告示牌编辑距离一致（8 格）。 */
    private static final double MAX_EDIT_DISTANCE_SQR = 64.0;

    /** 存档字段名。 */
    private static final String TAG_CHANNEL = "Channel";

    private int channel = WirelessRedstoneChannel.DEFAULT;

    /** 当前被允许提交频道的玩家；不持久化，随方块实体卸载而失效。 */
    @Nullable
    private UUID playerWhoMayEdit;

    protected WirelessRedstoneBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    /**
     * 供日志使用的模块名，由子类给出各自的类名。
     *
     * @return 模块名
     */
    protected abstract String logModule();

    /**
     * @return true 表示这是发射器，false 表示接收器。
     *         用于让频道设置界面显示对应的标题，也便于日志区分两者。
     */
    public abstract boolean isTransmitter();

    // ===== 频道 =====

    /**
     * @return 当前频道号，恒定落在 {@code [MIN, MAX]} 内
     */
    public int getChannel() {
        return channel;
    }

    /**
     * 设置频道号。
     * <p>
     * 非法值与「和当前值相同」都会被拒绝（返回 false 且不产生任何同步）。
     *
     * @param newChannel 新频道号
     * @return 实际发生改动时返回 true
     */
    public boolean setChannel(int newChannel) {
        if (!WirelessRedstoneChannel.isValid(newChannel)) {
            DebugLogger.warn(logModule(), "setChannel 被拒绝：频道号越界 channel=%d", newChannel);
            return false;
        }
        if (newChannel == channel) {
            return false;
        }

        int oldChannel = channel;
        channel = newChannel;
        markUpdated();
        onChannelChanged(oldChannel, newChannel);
        DebugLogger.stateChange(logModule(),
                (isTransmitter() ? "transmitter@" : "receiver@") + worldPosition.toShortString(),
                "channel", oldChannel, newChannel);
        return true;
    }

    /**
     * 频道号已改变的回调，默认什么都不做。
     * <p>
     * 发射器覆写它来刷新无线索引（旧频道下的登记必须立刻失效，
     * 否则改频道后旧频道的接收器还会继续通电）。
     *
     * @param oldChannel 改动前的频道
     * @param newChannel 改动后的频道
     */
    protected void onChannelChanged(int oldChannel, int newChannel) {
    }

    // ===== 频道设置授权 =====

    /**
     * 授权某位玩家提交本元件的频道（在服务端下发频道界面时调用）。
     *
     * @param playerUuid 玩家 UUID，传 null 表示撤销授权
     */
    public void setAllowedPlayerEditor(@Nullable UUID playerUuid) {
        playerWhoMayEdit = playerUuid;
    }

    /**
     * 校验玩家是否有权提交本元件的频道。
     * <p>
     * 需同时满足：是当前被授权者、且距离元件不超过 8 格。
     *
     * @param player 提交频道的玩家
     * @return 允许提交时返回 true
     */
    public boolean mayEdit(Player player) {
        if (player == null || playerWhoMayEdit == null || !playerWhoMayEdit.equals(player.getUUID())) {
            return false;
        }
        return player.distanceToSqr(Vec3.atCenterOf(worldPosition)) <= MAX_EDIT_DISTANCE_SQR;
    }

    // ===== 同步与持久化 =====

    /**
     * 标记数据变更并把方块实体数据推送给所有跟踪该区块的客户端。
     * <p>
     * 频道号本身不影响渲染，但同步过去后客户端侧的信息展示
     * （如 Jade 面板、再次右键时的预填值）才能拿到真实值。
     */
    protected void markUpdated() {
        setChanged();

        Level currentLevel = this.level;
        if (currentLevel == null || currentLevel.isClientSide()) {
            return;
        }

        BlockState state = getBlockState();
        currentLevel.sendBlockUpdated(worldPosition, state, state, Block.UPDATE_ALL);
    }

    @Override
    protected void saveAdditional(@NonNull ValueOutput output) {
        super.saveAdditional(output);
        output.putInt(TAG_CHANNEL, channel);
    }

    @Override
    protected void loadAdditional(@NonNull ValueInput input) {
        super.loadAdditional(input);
        // 存档可能被外部工具改坏，读入时夹一次而不是直接信任
        channel = WirelessRedstoneChannel.clamp(
                input.getIntOr(TAG_CHANNEL, WirelessRedstoneChannel.DEFAULT));
        playerWhoMayEdit = null;
    }

    @Override
    @NonNull
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    /**
     * {@inheritDoc}
     * <p>
     * 直接复用 {@link #saveAdditional(ValueOutput)} 的结果（{@code saveCustomOnly}），
     * 保证「存档字段」与「同步字段」永远一致，新增字段时不会漏同步。
     */
    @Override
    @NonNull
    public CompoundTag getUpdateTag(HolderLookup.@NonNull Provider registries) {
        return saveCustomOnly(registries);
    }
}
