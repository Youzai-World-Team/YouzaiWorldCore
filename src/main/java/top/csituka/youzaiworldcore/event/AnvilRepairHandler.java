package top.csituka.youzaiworldcore.event;

import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.AnvilBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.NonNull;
import top.csituka.youzaiworldcore.util.DebugLogger;

/**
 * 铁砧修复事件处理器。
 * 当玩家手持铁锭并下蹲时对准损坏/严重破损的铁砧右键使用，
 * 可消耗一个铁锭将铁砧修复到上一等级。
 */
public class AnvilRepairHandler implements UseBlockCallback {

    private static final AnvilRepairHandler INSTANCE = new AnvilRepairHandler();

    private AnvilRepairHandler() {
    }

    @Override
    public @NonNull InteractionResult interact(Player player, @NonNull Level level, @NonNull InteractionHand hand, @NonNull BlockHitResult hitResult) {
        DebugLogger.entering("AnvilRepairHandler", "interact", "player=" + player.getName().getString());

        // 只处理主手
        boolean isMainHand = hand == InteractionHand.MAIN_HAND;
        DebugLogger.branch("AnvilRepairHandler", "hand == MAIN_HAND", isMainHand);
        if (!isMainHand) {
            DebugLogger.exiting("AnvilRepairHandler", "interact", "PASS (not main hand)");
            return InteractionResult.PASS;
        }

        BlockPos pos = hitResult.getBlockPos();
        BlockState state = level.getBlockState(pos);

        // 仅当目标是铁砧方块时才处理
        boolean isAnvil = state.getBlock() instanceof AnvilBlock;
        DebugLogger.branch("AnvilRepairHandler", "block instanceof AnvilBlock", isAnvil, "pos=" + pos);
        if (!isAnvil) {
            DebugLogger.exiting("AnvilRepairHandler", "interact", "PASS (not anvil)");
            return InteractionResult.PASS;
        }

        ItemStack stack = player.getMainHandItem();

        // 检查玩家是否手持铁锭并下蹲
        boolean hasIronIngot = stack.is(Items.IRON_INGOT);
        boolean isCrouching = player.isSteppingCarefully();
        DebugLogger.branch("AnvilRepairHandler", "holding IRON_INGOT", hasIronIngot);
        DebugLogger.branch("AnvilRepairHandler", "player isSteppingCarefully", isCrouching);
        if (!hasIronIngot || !isCrouching) {
            DebugLogger.exiting("AnvilRepairHandler", "interact", "PASS (not holding iron ingot or not crouching)");
            return InteractionResult.PASS;
        }

        // 确定要修复到的目标方块状态
        BlockState newState = null;

        boolean isDamagedAnvil = state.is(Blocks.DAMAGED_ANVIL);
        DebugLogger.branch("AnvilRepairHandler", "state is DAMAGED_ANVIL", isDamagedAnvil);
        if (isDamagedAnvil) {
            // 严重破损 → 损坏
            newState = Blocks.CHIPPED_ANVIL.defaultBlockState()
                    .setValue(AnvilBlock.FACING, state.getValue(AnvilBlock.FACING));
        }

        boolean isChippedAnvil = !isDamagedAnvil && state.is(Blocks.CHIPPED_ANVIL);
        DebugLogger.branch("AnvilRepairHandler", "state is CHIPPED_ANVIL", isChippedAnvil);
        if (isChippedAnvil) {
            // 损坏 → 正常
            newState = Blocks.ANVIL.defaultBlockState()
                    .setValue(AnvilBlock.FACING, state.getValue(AnvilBlock.FACING));
        }

        boolean canRepair = newState != null;
        DebugLogger.branch("AnvilRepairHandler", "newState != null (can repair)", canRepair);
        if (canRepair) {
            // 只在服务端执行方块更新和物品消耗
            boolean isServer = !level.isClientSide();
            DebugLogger.branch("AnvilRepairHandler", "is server side", isServer);
            if (isServer) {
                level.setBlock(pos, newState, 3);
                stack.shrink(1);
                DebugLogger.info("AnvilRepairHandler", "Repaired anvil at " + pos + ", consumed 1 iron ingot");
            }
            DebugLogger.exiting("AnvilRepairHandler", "interact", "SUCCESS");
            return InteractionResult.SUCCESS;
        }

        // 如果铁砧是正常的（已满耐久），不做任何事，继续原逻辑打开GUI
        DebugLogger.exiting("AnvilRepairHandler", "interact", "PASS (anvil already full durability)");
        return InteractionResult.PASS;
    }

    /**
     * 向 Fabric 事件总线注册此处理器。
     */
    public static void register() {
        DebugLogger.entering("AnvilRepairHandler", "register");
        UseBlockCallback.EVENT.register(INSTANCE);
        DebugLogger.exiting("AnvilRepairHandler", "register");
    }
}