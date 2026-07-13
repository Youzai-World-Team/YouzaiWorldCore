package top.csituka.youzaiworldcore.entity.seat;

import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundSetPassengersPacket;
import net.minecraft.network.syncher.SynchedEntityData.Builder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import top.csituka.youzaiworldcore.util.DebugLogger;

/**
 * 座椅实体——当玩家坐在楼梯或台阶方块上时作为载具使用的不可见实体。
 * <p>
 * 该实体利用 Minecraft 原版的骑乘系统来实现坐姿：
 * <ul>
 *   <li>玩家右键单击楼梯/台阶时自动上马（mount）此实体</li>
 *   <li>玩家按下潜行键（Shift）时按照原版骑乘逻辑自动下马（dismount）</li>
 *   <li>所有乘客离开后，实体在下一 tick 自动移除</li>
 * </ul>
 */
public class SeatEntity extends Entity {

    public SeatEntity(EntityType<?> entityType, Level level) {
        super(entityType, level);
    }

    // ======================== 构造工厂方法 ========================

    /**
     * 在指定的楼梯/台阶方块位置创建座椅实体，并返回其实例。
     * 实体位置会自动根据楼梯/台阶类型调整 Y 轴高度。
     *
     * @param level   世界实例
     * @param pos     楼梯/台阶方块的坐标
     * @return 若方块状态合法则返回 SeatEntity，否则返回 null
     */
    @Nullable
    public static SeatEntity create(Level level, @NonNull BlockPos pos) {
        BlockState state = level.getBlockState(pos);

        double yOffset;
        if (state.getBlock() instanceof StairBlock) {
            yOffset = state.getValue(StairBlock.HALF) == Half.BOTTOM ? 0.5 : 1.0;
        } else if (state.getBlock() instanceof SlabBlock) {
            SlabType type = state.getValue(SlabBlock.TYPE);
            yOffset = switch (type) {
                case BOTTOM -> 0.5;
                case TOP -> 1.0;
                case DOUBLE -> 1.0;
            };
        } else {
            DebugLogger.warn("SeatEntity", "Unsupported block for seat: " + state.getBlock());
            return null;
        }

        SeatEntity seat = new SeatEntity(ModSeatEntities.SEAT, level);
        seat.setPos(pos.getX() + 0.5, pos.getY() + yOffset, pos.getZ() + 0.5);

        DebugLogger.info("SeatEntity", "Created seat entity at position: " + seat.blockPosition());
        return seat;
    }

    // ======================== 行为覆写 ========================

    /**
     * 让指定玩家骑乘到本座椅实体上。
     * <p>
     * 绕过 {@code Entity.startRiding()} 中
     * {@code canSerialize()} 校验（因本实体使用了 {@code noSave()}），
     * 直接设置玩家的 {@code vehicle} 字段并调用 {@code addPassenger()}，
     * 随后手动发送 {@link ClientboundSetPassengersPacket} 以同步客户端。
     * 玩家若正在骑乘其他实体会自动先下马。
     *
     * @param player 要骑乘上来的玩家
     */
    public void mountPlayer(ServerPlayer player) {
        if (player.isPassenger()) {
            player.stopRiding();
        }
        player.setPose(net.minecraft.world.entity.Pose.STANDING);
        // 通过 accessor 写入 vehicle 私用字段
        ((top.csituka.youzaiworldcore.mixin.seat.EntityVehicleAccessor) player)
                .youzaiworldcore$setVehicle(this);
        addPassenger(player);
        // 手动同步乘客数据到客户端（addPassenger 不发送网络包）
        ((ServerLevel) player.level()).getChunkSource()
                .sendToTrackingPlayersAndSelf(this,
                        new ClientboundSetPassengersPacket(this));
        DebugLogger.info("SeatEntity", "Player {} mounted on seat at {}",
                player.getName().getString(), blockPosition());
    }

    @Override
    public void tick() {
        super.tick();

        // 如果没有乘客，直接移除
        // getPassengers() 返回所有乘客的不可变列表，为空则表示无人乘坐
        if (!isRemoved() && getPassengers().isEmpty()) {
            DebugLogger.info("SeatEntity", "No passengers, removing seat entity at " + blockPosition());
            discard();
        }
    }

    @Override
    protected void removeAfterChangingDimensions() {
        // 不执行任何操作——座椅实体在维度变化/复活时不保留
    }

    /**
     * 座椅实体不受任何伤害。
     */
    @Override
    public boolean hurtServer(@NonNull ServerLevel level, @NonNull DamageSource damageSource, float amount) {
        return false;
    }

    // ======================== 乘客定位 ========================

    /**
     * 返回乘客相对于本实体位置的偏移量。
     * 返回 (0, 0.2, 0) 使玩家略微高于方块表面，呈现自然坐姿。
     */
    @Override
    @NonNull
    protected Vec3 getPassengerAttachmentPoint(
            @NonNull Entity passenger,
            @NonNull EntityDimensions dimensions,
            float partialTick
    ) {
        return new Vec3(0.0, 0.2, 0.0);
    }

    // ======================== 物理 / 碰撞 ========================

    @Override
    public boolean isInvisible() {
        return true;
    }

    @Override
    public boolean isInvisibleTo(@NonNull Player player) {
        return true;
    }

    @Override
    public boolean isNoGravity() {
        return true;
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean isAttackable() {
        return false;
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return false; // 完全不可见
    }

    @Override
    public boolean isColliding(@NonNull BlockPos pos, @NonNull BlockState state) {
        return false; // 不产生碰撞
    }

    // ======================== 数据持久化（不需要） ========================

    @Override
    protected void defineSynchedData(@NonNull Builder builder) {
        // 无需同步数据
    }

    /**
     * 从 NBT 读取数据——无操作，座椅实体不需要持久化。
     */
    @Override
    protected void readAdditionalSaveData(@NonNull ValueInput input) {
        // 无需操作
    }

    /**
     * 写入 NBT 数据——无操作，座椅实体不需要持久化。
     */
    @Override
    protected void addAdditionalSaveData(@NonNull ValueOutput output) {
        // 无需操作
    }
}
