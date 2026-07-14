package top.csituka.youzaiworldcore.event;

import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.NonNull;
import top.csituka.youzaiworldcore.entity.seat.SeatEntity;
import top.csituka.youzaiworldcore.util.DebugLogger;

/**
 * 坐姿交互事件处理器。
 * <p>
 * 当玩家满足以下条件时，右键单击楼梯或台阶方块可坐在该方块上：
 * <ul>
 *   <li>主手为空（无任何物品）</li>
 *   <li>目标方块是楼梯（{@link StairBlock}）或台阶（{@link SlabBlock}）</li>
 *   <li>玩家当前没有骑乘任何实体</li>
 *   <li>该方块位置尚未有其他座椅实体存在</li>
 * </ul>
 * </p>
 * <p>
 * 离开座位方式：按下潜行键（Shift），Minecraft 原版的骑乘下马逻辑会自动处理。
 * </p>
 */
@SuppressWarnings("null")
public class SitHandler implements UseBlockCallback {

    private static final SitHandler INSTANCE = new SitHandler();

    private SitHandler() {
    }

    @Override
    public @NonNull InteractionResult interact(
            Player player,
            @NonNull Level level,
            @NonNull InteractionHand hand,
            @NonNull BlockHitResult hitResult
    ) {
        DebugLogger.entering("SitHandler", "interact", "player=" + player.getName().getString());

        // ===== 条件 1：仅处理主手 =====
        if (hand != InteractionHand.MAIN_HAND) {
            DebugLogger.branch("SitHandler", "hand == MAIN_HAND", false, "副手忽略");
            DebugLogger.exiting("SitHandler", "interact", "PASS (not main hand)");
            return InteractionResult.PASS;
        }

        // ===== 条件 2：主手必须为空 =====
        if (!player.getMainHandItem().isEmpty()) {
            DebugLogger.branch("SitHandler", "main hand empty", false, "持有物品");
            DebugLogger.exiting("SitHandler", "interact", "PASS (holding item)");
            return InteractionResult.PASS;
        }

        // ===== 条件 3：目标方块必须是楼梯或台阶 =====
        BlockPos pos = hitResult.getBlockPos();
        BlockState state = level.getBlockState(pos);

        boolean isStairOrSlab = state.getBlock() instanceof StairBlock
                || state.getBlock() instanceof SlabBlock;
        if (!isStairOrSlab) {
            DebugLogger.branch("SitHandler", "block is stair or slab", false, String.valueOf(pos));
            DebugLogger.exiting("SitHandler", "interact", "PASS (not stair/slab)");
            return InteractionResult.PASS;
        }
        DebugLogger.branch("SitHandler", "block is stair or slab", true,
                "block=" + state.getBlock() + ", pos=" + pos);

        // ===== 条件 4：玩家不能已经在骑乘实体 =====
        if (player.isPassenger()) {
            DebugLogger.branch("SitHandler", "player already seated", true);
            DebugLogger.exiting("SitHandler", "interact", "PASS (already riding)");
            return InteractionResult.PASS;
        }

        // ===== 仅在服务端执行实体创建与骑乘 =====
        // 客户端返回 PASS 以让右键交互数据包正常发送到服务端；
        // 由于楼梯/台阶方块没有原版右键行为，此 PASS 是安全的。
        if (level.isClientSide()) {
            DebugLogger.branch("SitHandler", "is server side", false, "客户端 PASS，等待服务端处理");
            return InteractionResult.PASS;
        }

        // 此时 level 一定是 ServerLevel，player 一定是 ServerPlayer
        ServerLevel serverLevel = (ServerLevel) level;
        ServerPlayer serverPlayer = (ServerPlayer) player;

        // ===== 条件 5：该位置不能已有座椅实体 =====
        if (hasExistingSeat(serverLevel, pos)) {
            DebugLogger.branch("SitHandler", "seat already exists at", false, String.valueOf(pos));
            DebugLogger.exiting("SitHandler", "interact", "PASS (seat exists)");
            return InteractionResult.PASS;
        }

        // ===== 创建座椅实体并让玩家骑乘 =====
        SeatEntity seat = SeatEntity.create(serverLevel, pos);
        if (seat == null) {
            DebugLogger.warn("SitHandler", "Failed to create seat entity at " + pos);
            DebugLogger.exiting("SitHandler", "interact", "FAIL (seat creation failed)");
            return InteractionResult.FAIL;
        }

        // 将座椅添加到世界
        boolean added = serverLevel.addFreshEntity(seat);
        if (!added) {
            DebugLogger.warn("SitHandler", "Failed to add seat entity to world at " + pos);
            DebugLogger.exiting("SitHandler", "interact", "FAIL (add entity failed)");
            return InteractionResult.FAIL;
        }

        // 让玩家骑乘座椅实体（通过 SeatEntity.mountPlayer 绕过 canSerialize 校验）
        seat.mountPlayer(serverPlayer);

        DebugLogger.info("SitHandler", "Player %s is now sitting at %s", player.getName().getString(), pos);
        DebugLogger.exiting("SitHandler", "interact", "SUCCESS");
        return InteractionResult.SUCCESS;
    }

    /**
     * 检查指定方块位置是否已存在有效的座椅实体。
     *
     * @param level 世界实例（必须是 ServerLevel）
     * @param pos   目标方块坐标
     * @return 如果已存在座椅实体则返回 true
     */
    private static boolean hasExistingSeat(ServerLevel level, BlockPos pos) {
        AABB searchBox = new AABB(pos.getX(), pos.getY(), pos.getZ(),
                pos.getX() + 1.0, pos.getY() + 1.5, pos.getZ() + 1.0);
        return !level.getEntities(
                EntityTypeTest.forClass(SeatEntity.class),
                searchBox,
                seat -> !seat.isRemoved()
        ).isEmpty();
    }

    /**
     * 向 Fabric 事件总线注册此处理器。
     */
    public static void register() {
        DebugLogger.entering("SitHandler", "register");
        UseBlockCallback.EVENT.register(INSTANCE);
        DebugLogger.exiting("SitHandler", "register");
    }
}
