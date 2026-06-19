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
        // 只处理主手
        if (hand != InteractionHand.MAIN_HAND) {
            return InteractionResult.PASS;
        }

        BlockPos pos = hitResult.getBlockPos();
        BlockState state = level.getBlockState(pos);

        // 仅当目标是铁砧方块时才处理
        if (!(state.getBlock() instanceof AnvilBlock)) {
            return InteractionResult.PASS;
        }

        ItemStack stack = player.getMainHandItem();

        // 检查玩家是否手持铁锭并下蹲
        if (!stack.is(Items.IRON_INGOT) || !player.isSteppingCarefully()) {
            return InteractionResult.PASS;
        }

        // 确定要修复到的目标方块状态
        BlockState newState = null;

        if (state.is(Blocks.DAMAGED_ANVIL)) {
            // 严重破损 → 损坏
            newState = Blocks.CHIPPED_ANVIL.defaultBlockState()
                    .setValue(AnvilBlock.FACING, state.getValue(AnvilBlock.FACING));
        } else if (state.is(Blocks.CHIPPED_ANVIL)) {
            // 损坏 → 正常
            newState = Blocks.ANVIL.defaultBlockState()
                    .setValue(AnvilBlock.FACING, state.getValue(AnvilBlock.FACING));
        }

        if (newState != null) {
            // 只在服务端执行方块更新和物品消耗
            if (!level.isClientSide()) {
                level.setBlock(pos, newState, 3);
                stack.shrink(1);
            }
            return InteractionResult.SUCCESS;
        }

        // 如果铁砧是正常的（已满耐久），不做任何事，继续原逻辑打开GUI
        return InteractionResult.PASS;
    }

    /**
     * 向 Fabric 事件总线注册此处理器。
     */
    public static void register() {
        UseBlockCallback.EVENT.register(INSTANCE);
    }
}