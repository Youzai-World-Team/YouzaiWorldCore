package top.csituka.youzaiworldcore.util;

import eu.pb4.trinkets.api.SlotGroup;
import eu.pb4.trinkets.api.SlotType;
import eu.pb4.trinkets.api.TrinketAttachment;
import eu.pb4.trinkets.api.TrinketInventory;
import eu.pb4.trinkets.api.TrinketSlotAccess;
import eu.pb4.trinkets.api.TrinketsApi;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Trinkets 模组辅助工具类，用于检测和操作玩家饰品槽中的物品。
 * <p>
 * 使用直接 Trinkets API 调用。
 * </p>
 */
@SuppressWarnings("null")
public final class TrinketHelper {

    private static final Logger LOGGER = LoggerFactory.getLogger("YouzaiWorldCore/TrinketHelper");

    private TrinketHelper() {
    }

    public static boolean isLoaded() {
        return true;
    }

    /** 饰品槽位信息 */
    public record TrinketSlotInfo(String groupKey, int slotIndex, @org.jspecify.annotations.NonNull ItemStack stack,
            Object access) {
    }

    public static List<TrinketSlotInfo> getAllVisibleSlots(LivingEntity entity) {
        List<TrinketSlotInfo> result = new ArrayList<>();
        try {
            TrinketAttachment attachment = TrinketsApi.getAttachment(entity);
            if (attachment == null)
                return result;
            for (Map.Entry<String, TrinketInventory> entry : attachment.getInventories().entrySet()) {
                String groupKey = entry.getKey();
                TrinketInventory inv = entry.getValue();
                for (int i = 0; i < inv.getContainerSize(); i++) {
                    if (!inv.isVisible(i))
                        continue;
                    TrinketSlotAccess access = inv.getSlotAccess(i);
                    if (access == null)
                        continue;
                    result.add(new TrinketSlotInfo(groupKey, i, access.get(), access));
                }
            }
        } catch (Exception e) {
            LOGGER.warn("获取饰品槽位失败", e);
        }
        return result;
    }

    public static List<TrinketSlotInfo> getSlotsAttachedTo(LivingEntity entity, Slot slot) {
        List<TrinketSlotInfo> result = new ArrayList<>();
        if (slot == null)
            return result;
        try {
            TrinketAttachment attachment = TrinketsApi.getAttachment(entity);
            if (attachment == null)
                return result;
            for (Map.Entry<String, SlotGroup> groupEntry : SlotGroup.getEntityGroups(entity).entrySet()) {
                SlotGroup group = groupEntry.getValue();
                if (!group.isAttachedToSlot(slot))
                    continue;
                for (SlotType st : group.getSlots()) {
                    // getId() 返回完整槽位 id（如 "offhand/totem"），
                    // 与 attachment.getInventories() 的 map key
                    // 一致（LivingEntityTrinketAttachment:155）。
                    // 注意：不能用 groupName + "/" + getId()（会变成 offhand/offhand/totem），
                    // 也不能用已弃用的 name()（仅返回 "totem"）。
                    String invKey = st.getId();
                    TrinketInventory inv = attachment.getInventories().get(invKey);
                    if (inv == null)
                        continue;
                    for (int i = 0; i < inv.getContainerSize(); i++) {
                        if (!inv.isVisible(i))
                            continue;
                        TrinketSlotAccess access = inv.getSlotAccess(i);
                        if (access == null)
                            continue;
                        result.add(new TrinketSlotInfo(invKey, i, access.get(), access));
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.warn("获取关联饰品槽位失败", e);
        }
        return result;
    }

    public static ItemStack getSlotStack(TrinketSlotInfo slotInfo) {
        if (slotInfo.access() instanceof TrinketSlotAccess access) {
            return access.get();
        }
        return ItemStack.EMPTY;
    }

    public static boolean setSlotStack(TrinketSlotInfo slotInfo, ItemStack stack) {
        if (slotInfo.access() instanceof TrinketSlotAccess access) {
            boolean result = access.set(stack);
            access.inventory().setChanged();
            return result;
        }
        return false;
    }

    /** 返回饰品槽对指定物品允许的最大堆叠数。 */
    public static int getSlotMaxStackSize(TrinketSlotInfo slotInfo, ItemStack stack) {
        if (slotInfo.access() instanceof TrinketSlotAccess access) {
            return access.maxStackSize(stack);
        }
        return 0;
    }

    /** 检查指定物品是否通过饰品槽类型的校验器。 */
    public static boolean canPlaceInSlot(TrinketSlotInfo slotInfo, ItemStack stack, LivingEntity entity) {
        if (slotInfo.access() instanceof TrinketSlotAccess access) {
            return access.slotType().validatorCheck(stack, access, entity);
        }
        return false;
    }

    public static Identifier getSlotIcon(TrinketSlotInfo slotInfo) {
        if (slotInfo.access() instanceof TrinketSlotAccess access) {
            return access.slotType().icon();
        }
        return null;
    }

    public static boolean isItemEquipped(LivingEntity entity, Item item) {
        TrinketAttachment attachment = TrinketsApi.getAttachment(entity);
        return attachment != null && attachment.isEquipped(item);
    }

    public static int countItem(LivingEntity entity, Item item) {
        TrinketAttachment attachment = TrinketsApi.getAttachment(entity);
        if (attachment == null)
            return 0;
        try {
            int total = 0;
            for (Map.Entry<String, TrinketInventory> entry : attachment.getInventories().entrySet()) {
                TrinketInventory inv = entry.getValue();
                for (int i = 0; i < inv.getContainerSize(); i++) {
                    ItemStack stack = inv.getItem(i);
                    if (stack.is(item))
                        total += stack.getCount();
                }
            }
            return total;
        } catch (Exception e) {
            LOGGER.warn("统计饰品槽物品数量失败", e);
            return 0;
        }
    }

    public static boolean consumeOne(LivingEntity entity, Item item) {
        TrinketAttachment attachment = TrinketsApi.getAttachment(entity);
        if (attachment == null)
            return false;
        try {
            Optional<TrinketSlotAccess> opt = attachment.findFirst(stack -> stack.is(item));
            if (opt.isPresent()) {
                ItemStack stack = opt.get().get();
                stack.shrink(1);
                return true;
            }
            return false;
        } catch (Exception e) {
            LOGGER.warn("消耗饰品槽物品失败", e);
            return false;
        }
    }
}
