package top.csituka.youzaiworldcore.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.NonNull;
import top.csituka.youzaiworldcore.block.DuplicateBlock;
import top.csituka.youzaiworldcore.util.DebugLogger;

import java.util.Optional;
import java.util.UUID;

/**
 * 复制方块的方块实体。
 * <p>
 * 仅持久化放置者 UUID，用于服务端在寻找复制起点时隔离不同玩家的复制方块。
 * 激活态由 {@link DuplicateBlock#ACTIVE} 和“是否已经重新允许激活”的标记共同决定。
 */
@SuppressWarnings("null")
public class DuplicateBlockEntity extends BlockEntity {

    private UUID ownerUuid;
    private boolean activationArmed = true;

    public DuplicateBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.DUPLICATE_BLOCK, pos, state);
        DebugLogger.entering("DuplicateBlockEntity", "constructor", "pos=" + pos);
        DebugLogger.exiting("DuplicateBlockEntity", "constructor");
    }

    /**
     * 设置复制方块的放置者。
     *
     * @param ownerUuid 放置者 UUID
     */
    public void setOwner(UUID ownerUuid) {
        UUID oldOwner = this.ownerUuid;
        this.ownerUuid = ownerUuid;
        setChanged();
        DebugLogger.stateChange("DuplicateBlockEntity", "duplicateBlock@" + worldPosition.toShortString(),
                "owner", oldOwner, ownerUuid);
    }

    /**
     * 初始化激活状态。
     * <p>
     * 若放置瞬间上方已有目标方块，则视为未就绪；必须先经历一次无目标状态，
     * 再重新放回目标方块时才会激活。
     *
     * @param targetPresent 放置瞬间上方是否已有目标方块
     */
    public void initializeActivation(boolean targetPresent) {
        boolean oldArmed = activationArmed;
        activationArmed = !targetPresent;
        setActive(false);
        setChanged();
        DebugLogger.stateChange("DuplicateBlockEntity", "duplicateBlock@" + worldPosition.toShortString(),
                "activationArmed", oldArmed, activationArmed);
    }

    /**
     * 根据上方是否存在目标方块刷新激活状态。
     *
     * @param targetPresent 上方是否存在可复制目标
     */
    public void refreshActivation(boolean targetPresent) {
        if (targetPresent) {
            setActive(activationArmed);
            return;
        }

        if (!activationArmed) {
            DebugLogger.stateChange("DuplicateBlockEntity", "duplicateBlock@" + worldPosition.toShortString(),
                    "activationArmed", false, true);
        }
        activationArmed = true;
        setActive(false);
        setChanged();
    }

    /**
     * 获取此复制方块的放置者 UUID。
     *
     * @return 放置者 UUID，旧存档或非玩家放置时可能为空
     */
    public Optional<UUID> getOwner() {
        return Optional.ofNullable(ownerUuid);
    }

    /**
     * 判断该复制方块是否属于指定玩家。
     *
     * @param playerUuid 玩家 UUID
     * @return 属于该玩家时返回 true
     */
    public boolean isOwnedBy(UUID playerUuid) {
        return ownerUuid != null && ownerUuid.equals(playerUuid);
    }

    /**
     * 返回复制方块当前是否已经允许因上方目标变化而重新激活。
     *
     * @return 已就绪返回 true
     */
    public boolean isActivationArmed() {
        return activationArmed;
    }

    private void setActive(boolean active) {
        Level currentLevel = this.level;
        if (currentLevel == null) {
            return;
        }

        BlockState currentState = getBlockState();
        boolean currentActive = currentState.getValue(DuplicateBlock.ACTIVE);
        if (currentActive == active) {
            return;
        }

        BlockState newState = currentState.setValue(DuplicateBlock.ACTIVE, active);
        currentLevel.setBlock(worldPosition, newState, 3);
        currentLevel.sendBlockUpdated(worldPosition, currentState, newState, 3);
        setChanged();
        DebugLogger.stateChange("DuplicateBlockEntity", "duplicateBlock@" + worldPosition.toShortString(),
                "active", currentActive, active);
    }

    @Override
    protected void saveAdditional(@NonNull ValueOutput output) {
        super.saveAdditional(output);
        if (ownerUuid != null) {
            output.putIntArray("Owner", uuidToIntArray(ownerUuid));
        }
        output.putBoolean("ActivationArmed", activationArmed);
    }

    @Override
    protected void loadAdditional(@NonNull ValueInput input) {
        super.loadAdditional(input);
        int[] owner = input.getIntArray("Owner").orElse(new int[0]);
        ownerUuid = owner.length >= 4 ? intArrayToUuid(owner) : null;
        activationArmed = input.getBooleanOr("ActivationArmed", true);
    }

    private static int[] uuidToIntArray(UUID uuid) {
        return new int[] {
                (int) (uuid.getMostSignificantBits() >> 32),
                (int) uuid.getMostSignificantBits(),
                (int) (uuid.getLeastSignificantBits() >> 32),
                (int) uuid.getLeastSignificantBits()
        };
    }

    private static UUID intArrayToUuid(int[] data) {
        long msb = ((long) data[0] << 32) | (data[1] & 0xFFFFFFFFL);
        long lsb = ((long) data[2] << 32) | (data[3] & 0xFFFFFFFFL);
        return new UUID(msb, lsb);
    }
}
