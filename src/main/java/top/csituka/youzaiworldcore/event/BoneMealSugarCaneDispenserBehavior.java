package top.csituka.youzaiworldcore.event;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.dispenser.BlockSource;
import net.minecraft.core.dispenser.DefaultDispenseItemBehavior;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.block.SugarCaneBlock;
import net.minecraft.world.level.block.state.BlockState;
import top.csituka.youzaiworldcore.util.DebugLogger;

/**
 * 发射器骨粉催熟甘蔗行为。
 * <p>
 * 当发射器朝向甘蔗时发射骨粉，执行催熟逻辑（与玩家手动右键一致）。
 * 非甘蔗方向则回退为默认发射行为（弹出物品）。
 * 若甘蔗已满三格或上方无空间，不消耗骨粉，直接返还物品堆。
 * </p>
 */
@SuppressWarnings("null")
public class BoneMealSugarCaneDispenserBehavior extends DefaultDispenseItemBehavior {

    @Override
    protected ItemStack execute(BlockSource pointer, ItemStack stack) {
        DebugLogger.entering("BoneMealSugarCaneDispenserBehavior", "execute");

        ServerLevel level = pointer.level();
        Direction facing = pointer.state().getValue(DispenserBlock.FACING);
        BlockPos frontPos = pointer.pos().relative(facing);
        BlockState frontState = level.getBlockState(frontPos);

        // ===== 条件：目标方块必须是甘蔗 =====
        if (!(frontState.getBlock() instanceof SugarCaneBlock)) {
            DebugLogger.branch("BoneMealSugarCaneDispenserBehavior", "front is sugar cane",
                    false, "block=" + frontState.getBlock());
            // 不是甘蔗，回退到默认行为（弹出物品）
            DebugLogger.exiting("BoneMealSugarCaneDispenserBehavior", "execute", "FALLBACK to default");
            return super.execute(pointer, stack);
        }

        DebugLogger.branch("BoneMealSugarCaneDispenserBehavior", "front is sugar cane",
                true, "pos=" + frontPos);

        // ===== 尝试催熟甘蔗 =====
        boolean grew = BoneMealSugarCaneHandler.tryGrowSugarCane(level, frontPos);

        if (!grew) {
            DebugLogger.branch("BoneMealSugarCaneDispenserBehavior", "cane grown",
                    false, "催熟失败（已满三格或无空间），不消耗骨粉");
            // 不消耗骨粉，原样返还物品堆
            DebugLogger.exiting("BoneMealSugarCaneDispenserBehavior", "execute", "SKIP (no consumption)");
            return stack;
        }

        // ===== 催熟成功：消耗骨粉 =====
        stack.shrink(1);

        DebugLogger.info("BoneMealSugarCaneDispenserBehavior",
                "发射器催熟甘蔗成功：位置=%s, 方向=%s", frontPos, facing);
        DebugLogger.exiting("BoneMealSugarCaneDispenserBehavior", "execute", "SUCCESS");
        return stack;
    }

    /**
     * 注册此行为到发射器骨粉条目。
     */
    public static void register() {
        DebugLogger.entering("BoneMealSugarCaneDispenserBehavior", "register");
        DispenserBlock.registerBehavior(Items.BONE_MEAL, new BoneMealSugarCaneDispenserBehavior());
        DebugLogger.info("BoneMealSugarCaneDispenserBehavior", "发射器骨粉催熟甘蔗行为已注册");
        DebugLogger.exiting("BoneMealSugarCaneDispenserBehavior", "register");
    }
}
