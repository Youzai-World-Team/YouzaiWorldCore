package top.csituka.youzaiworldcore.client.screen;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import top.csituka.youzaiworldcore.util.DebugLogger;

/**
 * YZUI 物品栏的 Mouse Tweaks 共用逻辑。
 * <p>
 * 滚轮操作仅在玩家主背包（InventoryMenu 9-35）与快捷栏（36-44）之间逐个移动，
 * 不接触合成、装备、副手和 Trinkets 槽位，避免绕过槽位校验或触发合成副作用。
 */
final class MouseTweaksSupport {

    private static final int MAIN_START = 9;
    private static final int MAIN_END = 35;
    private static final int HOTBAR_START = 36;
    private static final int HOTBAR_END = 44;

    private MouseTweaksSupport() {
    }

    /** 执行一次槽位点击；调用方负责选择生存容器协议或创造模式同步协议。 */
    @FunctionalInterface
    interface SlotClicker {
        void click(int slotIndex, int button, ContainerInput input);
    }

    /** 判断槽位是否属于支持滚轮手势的主背包或快捷栏。 */
    static boolean isStorageSlot(int slotIndex) {
        return slotIndex >= MAIN_START && slotIndex <= HOTBAR_END;
    }

    /**
     * 执行一次滚轮手势。
     *
     * @param scrollDelta 正数为向上拉入悬停槽位，负数为向下从悬停槽位推出
     * @return 是否成功移动了一个物品
     */
    @SuppressWarnings("null")
    static boolean scrollOne(InventoryMenu menu, Player player, int hoveredSlot,
            double scrollDelta, SlotClicker clicker) {
        if (!isStorageSlot(hoveredSlot) || scrollDelta == 0.0 || !menu.getCarried().isEmpty()) {
            return false;
        }

        boolean hoveredInMain = hoveredSlot <= MAIN_END;
        int oppositeStart = hoveredInMain ? HOTBAR_START : MAIN_START;
        int oppositeEnd = hoveredInMain ? HOTBAR_END : MAIN_END;

        int sourceSlot;
        int targetSlot;
        if (scrollDelta < 0.0) {
            // 向下：从当前槽位推出一个，目标区域先合并同类，再从后向前找空槽。
            sourceSlot = hoveredSlot;
            Slot source = menu.getSlot(sourceSlot);
            if (!source.hasItem() || !source.mayPickup(player)) {
                return false;
            }
            targetSlot = findTargetSlot(menu, source.getItem(), oppositeStart, oppositeEnd);
        } else {
            // 向上：从另一区域拉入一个同类物品；空槽没有目标类型，因此不处理。
            targetSlot = hoveredSlot;
            Slot target = menu.getSlot(targetSlot);
            if (!target.hasItem() || target.getItem().getCount() >= target.getMaxStackSize(target.getItem())) {
                return false;
            }
            sourceSlot = findMatchingSource(menu, player, target.getItem(), oppositeStart, oppositeEnd);
        }

        if (sourceSlot < 0 || targetSlot < 0 || sourceSlot == targetSlot) {
            return false;
        }
        return moveOne(menu, player, sourceSlot, targetSlot, clicker);
    }

    /** 默认按 Mouse Tweaks 的 WheelSearchOrder=1，从最后一个槽位向前查找。 */
    @SuppressWarnings("null")
    private static int findTargetSlot(InventoryMenu menu, ItemStack source, int start, int end) {
        int emptySlot = -1;
        for (int i = end; i >= start; i--) {
            Slot target = menu.getSlot(i);
            if (!target.isActive() || !target.mayPlace(source)) {
                continue;
            }
            ItemStack existing = target.getItem();
            if (existing.isEmpty()) {
                if (emptySlot < 0) {
                    emptySlot = i;
                }
            } else if (ItemStack.isSameItemSameComponents(existing, source)
                    && existing.getCount() < target.getMaxStackSize(source)) {
                return i;
            }
        }
        return emptySlot;
    }

    @SuppressWarnings("null")
    private static int findMatchingSource(InventoryMenu menu, Player player, ItemStack target, int start, int end) {
        for (int i = end; i >= start; i--) {
            Slot source = menu.getSlot(i);
            if (source.isActive() && source.mayPickup(player) && source.hasItem()
                    && ItemStack.isSameItemSameComponents(source.getItem(), target)) {
                return i;
            }
        }
        return -1;
    }

    /** 通过标准左取、右放一件、左键归还余量的序列移动一个物品。 */
    @SuppressWarnings("null")
    private static boolean moveOne(InventoryMenu menu, Player player, int sourceIndex, int targetIndex,
            SlotClicker clicker) {
        Slot source = menu.getSlot(sourceIndex);
        Slot target = menu.getSlot(targetIndex);
        ItemStack sourceBefore = source.getItem().copy();
        ItemStack targetBefore = target.getItem().copy();
        if (sourceBefore.isEmpty() || !source.mayPickup(player) || !target.mayPlace(sourceBefore)) {
            return false;
        }

        clicker.click(sourceIndex, 0, ContainerInput.PICKUP);
        if (menu.getCarried().isEmpty()) {
            return false;
        }
        clicker.click(targetIndex, 1, ContainerInput.PICKUP);

        boolean moved = source.getItem().getCount() < sourceBefore.getCount()
                && (targetBefore.isEmpty() || target.getItem().getCount() > targetBefore.getCount());
        if (!menu.getCarried().isEmpty()) {
            clicker.click(sourceIndex, 0, ContainerInput.PICKUP);
        }

        if (moved) {
            DebugLogger.info("MouseTweaks", "滚轮移动 1 个物品：slot %d -> slot %d", sourceIndex, targetIndex);
        }
        return moved;
    }
}
