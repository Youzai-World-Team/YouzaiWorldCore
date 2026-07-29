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
import java.util.function.Predicate;

/**
 * Trinkets 模组辅助工具类，用于检测和操作玩家饰品槽中的物品。
 * <p>
 * 使用直接 Trinkets API 调用，不再使用反射。
 * Trinkets 自 v4.1.0-beta.2+26.2 起为必选依赖项。
 * </p>
 */
public final class TrinketHelper {

    private static final Logger LOGGER = LoggerFactory.getLogger("YouzaiWorldCore/TrinketHelper");

    private TrinketHelper() {}

    /** 返回 Trinkets 是否已加载（现在为必选依赖，始终返回 true）。 */
    public static boolean isLoaded() {
        return true;
    }

    /** 饰品槽位信息 */
    public record TrinketSlotInfo(String groupKey, int slotIndex, ItemStack stack, TrinketSlotAccess access) {}

    // ========== 可见槽位枚举 ==========

    /**
     * 获取玩家所有可见饰品槽位的物品信息。
     * 遍历 TrinketAttachment.getInventories()，对每个 Inventory 中可见的槽位提取 ItemStack。
     */
    public static List<TrinketSlotInfo> getAllVisibleSlots(LivingEntity entity) {
        List<TrinketSlotInfo> result = new ArrayList<>();
        try {
            TrinketAttachment attachment = TrinketsApi.getAttachment(entity);
            if (attachment == null) return result;

            for (Map.Entry<String, TrinketInventory> entry : attachment.getInventories().entrySet()) {
                String groupKey = entry.getKey();
                TrinketInventory inv = entry.getValue();
                for (int i = 0; i < inv.getContainerSize(); i++) {
                    if (!inv.isVisible(i)) continue;
                    TrinketSlotAccess access = inv.getSlotAccess(i);
                    if (access == null) continue;
                    result.add(new TrinketSlotInfo(groupKey, i, access.get(), access));
                }
            }
        } catch (Exception e) {
            LOGGER.warn("获取饰品槽位失败", e);
        }
        return result;
    }

    /**
     * 获取与指定容器槽位关联的所有可见 Trinket 饰品槽信息。
     * 利用 SlotGroup.isAttachedToSlot(Slot) 判断组归属。
     */
    public static List<TrinketSlotInfo> getSlotsAttachedTo(LivingEntity entity, Slot slot) {
        List<TrinketSlotInfo> result = new ArrayList<>();
        if (slot == null) return result;
        try {
            TrinketAttachment attachment = TrinketsApi.getAttachment(entity);
            if (attachment == null) return result;

            for (Map.Entry<String, SlotGroup> groupEntry : SlotGroup.getEntityGroups(entity).entrySet()) {
                String groupName = groupEntry.getKey();
                SlotGroup group = groupEntry.getValue();
                if (!group.isAttachedToSlot(slot)) continue;

                for (SlotType st : group.getSlots()) {
                    String slotName = st.name();
                    String invKey = groupName + "/" + slotName;
                    TrinketInventory inv = attachment.getInventories().get(invKey);
                    if (inv == null) continue;

                    for (int i = 0; i < inv.getContainerSize(); i++) {
                        if (!inv.isVisible(i)) continue;
                        TrinketSlotAccess access = inv.getSlotAccess(i);
                        if (access == null) continue;
                        result.add(new TrinketSlotInfo(invKey, i, access.get(), access));
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.warn("获取关联饰品槽位失败", e);
        }
        return result;
    }

    // ========== 物品读写 ==========

    /** 获取饰品槽位的 ItemStack（实时读取）。 */
    public static ItemStack getSlotStack(TrinketSlotInfo slotInfo) {
        return slotInfo.access().get();
    }

    /** 设置饰品槽位的 ItemStack，并标记容器已变更。 */
    public static boolean setSlotStack(TrinketSlotInfo slotInfo, ItemStack stack) {
        try {
            boolean result = slotInfo.access().set(stack);
            slotInfo.access().inventory().setChanged();
            return result;
        } catch (Exception e) {
            LOGGER.warn("设置饰品槽物品失败", e);
            return false;
        }
    }

    // ========== 槽位图标 ==========

    /** 获取饰品槽位的类型图标（SlotType.icon()），用于空槽自定义占位贴图。 */
    public static Identifier getSlotIcon(TrinketSlotInfo slotInfo) {
        try {
            return slotInfo.access().slotType().icon();
        } catch (Exception e) {
            LOGGER.warn("读取饰品槽类型图标失败", e);
            return null;
        }
    }

    // ========== 服务端直接操作 API ==========

    /** 获取玩家实体的 TrinketAttachment。 */
    public static TrinketAttachment getAttachment(LivingEntity entity) {
        return TrinketsApi.getAttachment(entity);
    }

    /** 从 TrinketAttachment 中获取指定 groupKey 的 TrinketInventory。 */
    public static TrinketInventory getInventory(TrinketAttachment attachment, String groupKey) {
        return attachment.getInventories().get(groupKey);
    }

    /** 从 TrinketInventory 中获取指定索引的 TrinketSlotAccess。 */
    public static TrinketSlotAccess getSlotAccess(TrinketInventory inventory, int slotIndex) {
        return inventory.getSlotAccess(slotIndex);
    }

    /** 从 TrinketSlotAccess 获得 ItemStack。 */
    public static ItemStack getStack(TrinketSlotAccess access) {
        return access.get();
    }

    /** 通过 TrinketSlotAccess 设置 ItemStack 并标记变更。 */
    public static boolean setStack(TrinketSlotAccess access, ItemStack stack) {
        try {
            boolean result = access.set(stack);
            access.inventory().setChanged();
            return result;
        } catch (Exception e) {
            LOGGER.warn("设置饰品槽物品失败", e);
            return false;
        }
    }

    /** 标记 TrinketInventory 为已变更，触发持久化保存。 */
    public static void markInventoryChanged(TrinketInventory inventory) {
        if (inventory != null) inventory.setChanged();
    }

    // ========== 检查 & 消耗 ==========

    /** 检查玩家是否在任意饰品槽中装备了指定物品。 */
    public static boolean isItemEquipped(LivingEntity entity, Item item) {
        TrinketAttachment attachment = TrinketsApi.getAttachment(entity);
        return attachment != null && attachment.isEquipped(item);
    }

    /** 统计饰品槽中指定物品的总数量。 */
    public static int countItem(LivingEntity entity, Item item) {
        TrinketAttachment attachment = TrinketsApi.getAttachment(entity);
        if (attachment == null) return 0;
        try {
            java.util.List<TrinketSlotAccess> slots = attachment.equipped(stack -> stack.is(item), false);
            int total = 0;
            for (TrinketSlotAccess access : slots) {
                total += access.get().getCount();
            }
            return total;
        } catch (Exception e) {
            LOGGER.warn("统计饰品槽物品数量失败", e);
            return 0;
        }
    }

    /** 从饰品槽中消耗一个指定物品。 */
    public static boolean consumeOne(LivingEntity entity, Item item) {
        TrinketAttachment attachment = TrinketsApi.getAttachment(entity);
        if (attachment == null) return false;
        try {
            Optional<TrinketSlotAccess> opt = attachment.findFirst(stack -> stack.is(item));
            if (opt.isPresent()) {
                TrinketSlotAccess access = opt.get();
                ItemStack stack = access.get();
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
