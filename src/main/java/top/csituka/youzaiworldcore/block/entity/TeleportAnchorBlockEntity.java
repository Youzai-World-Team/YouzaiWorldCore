package top.csituka.youzaiworldcore.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.NonNull;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * 传送锚点的方块实体。
 * <p>
 * 存储已激活此锚点的玩家 UUID 集合。
 * 当集合从空变为非空时，方块进入 ACTIVE 状态并发光；
 * 当集合从非空变为空时，方块回到非激活状态。
 */
@SuppressWarnings("null")
public class TeleportAnchorBlockEntity extends BlockEntity {

    private final Set<UUID> activators = new HashSet<>();

    public TeleportAnchorBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.TELEPORT_ANCHOR, pos, state);
    }

    /**
     * 检查指定玩家是否已激活此锚点。
     */
    public boolean isActivatedBy(UUID playerUuid) {
        return activators.contains(playerUuid);
    }

    /**
     * 获取当前激活此锚点的玩家数量。
     */
    public int getActivatorCount() {
        return activators.size();
    }

    /**
     * 添加一个玩家到激活者集合。当集合变化时，向客户端同步更新。
     *
     * @return true 如果此操作使集合从空变为非空
     */
    public boolean addActivator(UUID playerUuid) {
        boolean wasEmpty = activators.isEmpty();
        if (activators.add(playerUuid)) {
            setChanged();
            syncToClient();
        }
        return wasEmpty && !activators.isEmpty();
    }

    /**
     * 从激活者集合中移除一个玩家。当集合变化时，向客户端同步更新。
     *
     * @return true 如果此操作使集合变为空
     */
    public boolean removeActivator(UUID playerUuid) {
        if (activators.remove(playerUuid)) {
            setChanged();
            syncToClient();
        }
        return activators.isEmpty();
    }

    /**
     * 将方块实体数据同步到所有客户端（逐玩家纹理需要实时同步）。
     */
    private void syncToClient() {
        if (level != null && !level.isClientSide()) {
            level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), 3);
        }
    }

    /**
     * 返回激活者集合的只读视图，供 BER 在客户端检查。
     */
    @NonNull
    public Set<UUID> getActivators() {
        return Collections.unmodifiableSet(activators);
    }

    @Override
    @NonNull
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    @NonNull
    public CompoundTag getUpdateTag(HolderLookup.@NonNull Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        int[] uuids = new int[activators.size() * 4];
        int i = 0;
        for (UUID uuid : activators) {
            uuids[i++] = (int) (uuid.getMostSignificantBits() >> 32);
            uuids[i++] = (int) uuid.getMostSignificantBits();
            uuids[i++] = (int) (uuid.getLeastSignificantBits() >> 32);
            uuids[i++] = (int) uuid.getLeastSignificantBits();
        }
        tag.putIntArray("Activators", uuids);
        return tag;
    }

    @Override
    protected void saveAdditional(@NonNull ValueOutput output) {
        super.saveAdditional(output);
        int[] uuids = new int[activators.size() * 4];
        int i = 0;
        for (UUID uuid : activators) {
            uuids[i++] = (int) (uuid.getMostSignificantBits() >> 32);
            uuids[i++] = (int) uuid.getMostSignificantBits();
            uuids[i++] = (int) (uuid.getLeastSignificantBits() >> 32);
            uuids[i++] = (int) uuid.getLeastSignificantBits();
        }
        output.putIntArray("Activators", uuids);
    }

    @Override
    protected void loadAdditional(@NonNull ValueInput input) {
        super.loadAdditional(input);
        activators.clear();
        int[] uuids = input.getIntArray("Activators").orElse(new int[0]);
        if (uuids.length >= 4) {
            for (int i = 0; i + 3 < uuids.length; i += 4) {
                long msb = ((long) uuids[i] << 32) | (uuids[i + 1] & 0xFFFFFFFFL);
                long lsb = ((long) uuids[i + 2] << 32) | (uuids[i + 3] & 0xFFFFFFFFL);
                activators.add(new UUID(msb, lsb));
            }
        }
    }
}
