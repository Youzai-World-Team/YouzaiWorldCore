package top.csituka.youzaiworldcore.event;

import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SugarCaneBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.NonNull;
import top.csituka.youzaiworldcore.util.DebugLogger;

/**
 * 骨粉催熟甘蔗事件处理器。
 * <p>
 * 当玩家手持骨粉右键点击甘蔗时，将甘蔗催熟一格（最多三格高）。
 * 逻辑：找到当前甘蔗柱的顶部，若总高度 < 3 且顶部上方为空气，则在上方放置一截新甘蔗。
 * </p>
 */
@SuppressWarnings("null")
public class BoneMealSugarCaneHandler implements UseBlockCallback {

    private static final BoneMealSugarCaneHandler INSTANCE = new BoneMealSugarCaneHandler();
    /** 甘蔗最大高度 */
    private static final int MAX_CANE_HEIGHT = 3;

    private BoneMealSugarCaneHandler() {
    }

    @Override
    public @NonNull InteractionResult interact(
            Player player,
            @NonNull Level level,
            @NonNull InteractionHand hand,
            @NonNull BlockHitResult hitResult
    ) {
        DebugLogger.entering("BoneMealSugarCaneHandler", "interact",
                "player=" + player.getName().getString());

        // ===== 条件 1：仅处理主手 =====
        if (hand != InteractionHand.MAIN_HAND) {
            DebugLogger.branch("BoneMealSugarCaneHandler", "hand == MAIN_HAND", false, "副手忽略");
            DebugLogger.exiting("BoneMealSugarCaneHandler", "interact", "PASS (not main hand)");
            return InteractionResult.PASS;
        }

        // ===== 条件 2：手持物品必须是骨粉 =====
        ItemStack stack = player.getItemInHand(hand);
        if (stack.getItem() != Items.BONE_MEAL) {
            DebugLogger.branch("BoneMealSugarCaneHandler", "holding bone meal", false,
                    "item=" + stack.getItem());
            DebugLogger.exiting("BoneMealSugarCaneHandler", "interact", "PASS (not bone meal)");
            return InteractionResult.PASS;
        }

        // ===== 条件 3：目标方块必须是甘蔗 =====
        BlockPos clickedPos = hitResult.getBlockPos();
        BlockState clickedState = level.getBlockState(clickedPos);

        if (!(clickedState.getBlock() instanceof SugarCaneBlock)) {
            DebugLogger.branch("BoneMealSugarCaneHandler", "block is sugar cane", false,
                    "block=" + clickedState.getBlock());
            DebugLogger.exiting("BoneMealSugarCaneHandler", "interact", "PASS (not sugar cane)");
            return InteractionResult.PASS;
        }

        DebugLogger.branch("BoneMealSugarCaneHandler", "block is sugar cane", true,
                "pos=" + clickedPos);

        // 客户端返回 SUCCESS 以让交互数据包发送到服务端
        if (level.isClientSide()) {
            DebugLogger.branch("BoneMealSugarCaneHandler", "is server side", false, "客户端 PASS");
            return InteractionResult.SUCCESS;
        }

        // ===== 服务端：执行催熟逻辑 =====
        ServerLevel serverLevel = (ServerLevel) level;
        ServerPlayer serverPlayer = (ServerPlayer) player;

        // 找到甘蔗柱顶部
        BlockPos topPos = clickedPos;
        int caneHeight = 1;
        while (true) {
            BlockPos abovePos = topPos.above();
            BlockState aboveState = serverLevel.getBlockState(abovePos);
            if (aboveState.getBlock() instanceof SugarCaneBlock) {
                topPos = abovePos;
                caneHeight++;
            } else {
                break;
            }
        }

        DebugLogger.info("BoneMealSugarCaneHandler",
                "甘蔗柱底部=%s, 顶部=%s, 高度=%d", clickedPos, topPos, caneHeight);

        // ===== 条件 4：检查是否已达到最大高度 =====
        if (caneHeight >= MAX_CANE_HEIGHT) {
            DebugLogger.branch("BoneMealSugarCaneHandler", "cane height < max", false,
                    "height=" + caneHeight + ", max=" + MAX_CANE_HEIGHT);
            DebugLogger.exiting("BoneMealSugarCaneHandler", "interact", "FAIL (max height reached)");
            return InteractionResult.FAIL;
        }

        // ===== 条件 5：顶部上方必须有空气 =====
        BlockPos aboveTop = topPos.above();
        if (!serverLevel.getBlockState(aboveTop).isAir()) {
            DebugLogger.branch("BoneMealSugarCaneHandler", "space above is air", false,
                    "block=" + serverLevel.getBlockState(aboveTop).getBlock());
            DebugLogger.exiting("BoneMealSugarCaneHandler", "interact", "FAIL (blocked above)");
            return InteractionResult.FAIL;
        }

        // ===== 执行催熟：放置新甘蔗 =====
        BlockState newCaneState = Blocks.SUGAR_CANE.defaultBlockState();
        serverLevel.setBlockAndUpdate(aboveTop, newCaneState);

        // 播放骨粉粒子效果
        serverLevel.levelEvent(1505, aboveTop, 15);

        // ===== 消耗骨粉（非创造模式）=====
        if (!serverPlayer.isCreative()) {
            stack.shrink(1);
            serverPlayer.getInventory().setChanged();
        }

        DebugLogger.info("BoneMealSugarCaneHandler",
                "甘蔗催熟成功：玩家=%s, 新位置=%s", player.getName().getString(), aboveTop);
        DebugLogger.exiting("BoneMealSugarCaneHandler", "interact", "SUCCESS");
        return InteractionResult.SUCCESS;
    }

    /**
     * 向 Fabric 事件总线注册此处理器。
     */
    public static void register() {
        DebugLogger.entering("BoneMealSugarCaneHandler", "register");
        UseBlockCallback.EVENT.register(INSTANCE);
        DebugLogger.exiting("BoneMealSugarCaneHandler", "register");
    }
}
