package top.csituka.youzaiworldcore.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.NonNull;

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
     * 添加一个玩家到激活者集合。
     *
     * @return true 如果此操作使集合从空变为非空
     */
    public boolean addActivator(UUID playerUuid) {
        boolean wasEmpty = activators.isEmpty();
        activators.add(playerUuid);
        setChanged();
        return wasEmpty && !activators.isEmpty();
    }

    /**
     * 从激活者集合中移除一个玩家。
     *
     * @return true 如果此操作使集合变为空
     */
    public boolean removeActivator(UUID playerUuid) {
        activators.remove(playerUuid);
        setChanged();
        return activators.isEmpty();
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
