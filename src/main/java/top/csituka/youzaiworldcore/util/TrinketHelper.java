package top.csituka.youzaiworldcore.util;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
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
 * Trinkets 在 {@code fabric.mod.json} 中为 {@code suggests}（可选依赖），
 * 因此本工具全部通过反射调用 Trinkets API，避免编译时或类加载时硬依赖。
 * <p>
 * 使用示例：
 * <pre>{@code
 * if (TrinketHelper.isLoaded()) {
 *     boolean hasHeart = TrinketHelper.isItemEquipped(player, ModItems.HEART_OF_GUARDIANSHIP);
 * }
 * }</pre>
 */
public final class TrinketHelper {

    private static final Logger LOGGER = LoggerFactory.getLogger("YouzaiWorldCore/TrinketHelper");

    private static final boolean TRINKETS_LOADED;
    private static final String TRINKETS_API_CLASS = "eu.pb4.trinkets.api.TrinketsApi";

    // 缓存反射方法句柄（惰性初始化，LazyHolder 模式避免早期类加载）
    private static volatile MethodHandles handles = null;

    static {
        boolean loaded = false;
        try {
            Class.forName(TRINKETS_API_CLASS);
            loaded = true;
        } catch (ClassNotFoundException e) {
            LOGGER.info("Trinkets 模组未加载，饰品槽位功能不可用");
        }
        TRINKETS_LOADED = loaded;
    }

    private TrinketHelper() {
    }

    /**
     * @return Trinkets 模组是否已加载
     */
    public static boolean isLoaded() {
        return TRINKETS_LOADED;
    }

    // ========== 高层 API ==========

    /** 饰品槽位信息，用于 UI 渲染 */
    public record TrinketSlotInfo(String groupKey, int slotIndex, ItemStack stack, Object access) {
    }

    /**
     * 获取玩家所有可见饰品槽位的物品信息。
     * <p>
     * 遍历 TrinketAttachment.getInventories() 返回的 {@code Map<String, TrinketInventory>}，
     * 对每个 Inventory 中可见的槽位提取 ItemStack。
     *
     * @return 可见槽位列表（含空槽位）
     */
    @SuppressWarnings("unchecked")
    public static List<TrinketSlotInfo> getAllVisibleSlots(LivingEntity entity) {
        List<TrinketSlotInfo> result = new ArrayList<>();
        if (!TRINKETS_LOADED)
            return result;
        try {
            MethodHandles h = getHandles();
            Object attachment = h.getAttachmentMethod.invoke(null, entity);
            if (attachment == null)
                return result;

            // TrinketAttachment.getInventories() → Map<String, TrinketInventory>
            Map<String, Object> inventories = (Map<String, Object>) h.getInventoriesMethod.invoke(attachment);
            for (Map.Entry<String, Object> entry : inventories.entrySet()) {
                String groupKey = entry.getKey(); // e.g., "chest/elytra"
                Object inv = entry.getValue();    // TrinketInventory
                int size = (int) h.invContainerSizeMethod.invoke(inv);
                for (int i = 0; i < size; i++) {
                    boolean visible = (boolean) h.invIsVisibleMethod.invoke(inv, i);
                    if (!visible)
                        continue;
                    Object access = h.invGetSlotAccessMethod.invoke(inv, i);
                    ItemStack stack = (ItemStack) h.accessGetMethod.invoke(access);
                    result.add(new TrinketSlotInfo(groupKey, i, stack, access));
                }
            }
        } catch (Exception e) {
            LOGGER.warn("获取饰品槽位失败", e);
        }
        return result;
    }

    /**
     * 获取与指定容器槽位关联的所有可见 Trinket 饰品槽信息。
     * <p>
     * 利用 {@code SlotGroup.isAttachedToSlot(Slot)} 判断组归属，
     * 若匹配则返回该组所有可见槽位。
     *
     * @param entity  玩家实体
     * @param slot    容器槽位（如盔甲槽、副手槽）
     * @return 关联的可见饰品槽列表（含空槽）
     */
    @SuppressWarnings("unchecked")
    public static List<TrinketSlotInfo> getSlotsAttachedTo(LivingEntity entity, Slot slot) {
        List<TrinketSlotInfo> result = new ArrayList<>();
        if (!TRINKETS_LOADED || slot == null)
            return result;
        try {
            MethodHandles h = getHandles();
            Object attachment = h.getAttachmentMethod.invoke(null, entity);
            if (attachment == null)
                return result;

            // SlotGroup.getEntityGroups(Entity) → Map<String, SlotGroup>
            Map<String, Object> groups = (Map<String, Object>) h.getEntityGroupsMethod.invoke(null, entity);
            for (Map.Entry<String, Object> entry : groups.entrySet()) {
                String groupName = entry.getKey();
                Object group = entry.getValue(); // SlotGroup
                // SlotGroup.isAttachedToSlot(Slot) → boolean
                boolean attached = (boolean) h.groupIsAttachedMethod.invoke(group, slot);
                if (!attached)
                    continue;
                // SlotGroup.getSlots() → Collection<SlotType>
                java.util.Collection<Object> slotTypes = (java.util.Collection<Object>) h.groupGetSlotsMethod.invoke(group);
                for (Object st : slotTypes) {
                    // SlotType.name() → String (slot name like "elytra")
                    String slotName = (String) h.slotTypeNameMethod.invoke(st);
                    String invKey = groupName + "/" + slotName;
                    // TrinketAttachment.getInventories() → Map<String, TrinketInventory>
                    Map<String, Object> inventories = (Map<String, Object>) h.getInventoriesMethod.invoke(attachment);
                    Object inv = inventories.get(invKey);
                    if (inv == null)
                        continue;
                    int size = (int) h.invContainerSizeMethod.invoke(inv);
                    for (int i = 0; i < size; i++) {
                        boolean visible = (boolean) h.invIsVisibleMethod.invoke(inv, i);
                        if (!visible)
                            continue;
                        Object access = h.invGetSlotAccessMethod.invoke(inv, i);
                        ItemStack stack = (ItemStack) h.accessGetMethod.invoke(access);
                        result.add(new TrinketSlotInfo(invKey, i, stack, access));
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.warn("获取关联饰品槽位失败", e);
        }
        return result;
    }

    /**
     * 获取饰品槽位的 ItemStack（实际调用 TrinketSlotAccess.get()）。
     *
     * @param slotInfo 从 {@link #getAllVisibleSlots} 获取的槽位信息
     * @return 当前的 ItemStack
     */
    public static ItemStack getSlotStack(TrinketSlotInfo slotInfo) {
        try {
            MethodHandles h = getHandles();
            return (ItemStack) h.accessGetMethod.invoke(slotInfo.access());
        } catch (Exception e) {
            LOGGER.warn("读取饰品槽物品失败", e);
            return ItemStack.EMPTY;
        }
    }

    /**
     * 设置饰品槽位的 ItemStack（实际调用 TrinketSlotAccess.set()）。
     *
     * @param slotInfo 从 {@link #getAllVisibleSlots} 获取的槽位信息
     * @param stack    要设置的物品
     * @return 是否成功
     */
    public static boolean setSlotStack(TrinketSlotInfo slotInfo, ItemStack stack) {
        try {
            MethodHandles h = getHandles();
            // TrinketSlotAccess.set(ItemStack)
            // The method is "set" with single ItemStack param
            return (boolean) h.accessSetMethod.invoke(slotInfo.access(), stack);
        } catch (Exception e) {
            LOGGER.warn("设置饰品槽物品失败", e);
            return false;
        }
    }

    /**
     * 检查玩家是否在任意饰品槽中装备了指定物品。
     */
    public static boolean isItemEquipped(LivingEntity entity, Item item) {
        if (!TRINKETS_LOADED) {
            return false;
        }
        try {
            MethodHandles h = getHandles();
            Object attachment = h.getAttachmentMethod.invoke(null, entity);
            if (attachment == null) {
                return false;
            }
            return (boolean) h.isEquippedItemMethod.invoke(attachment, item);
        } catch (Exception e) {
            LOGGER.warn("检查饰品槽装备失败", e);
            return false;
        }
    }

    /**
     * 统计饰品槽中指定物品的总数量。
     * <p>
     * 若有多个分堆，会累加各堆的 count。
     */
    public static int countItem(LivingEntity entity, Item item) {
        if (!TRINKETS_LOADED) {
            return 0;
        }
        try {
            MethodHandles h = getHandles();
            Object attachment = h.getAttachmentMethod.invoke(null, entity);
            if (attachment == null) {
                return 0;
            }

            // TrinketAttachment.equipped(Predicate) → List<TrinketSlotAccess>
            Predicate<ItemStack> predicate = stack -> stack.is(item);
            @SuppressWarnings("unchecked")
            java.util.List<Object> slots = (java.util.List<Object>) h.equippedPredicateMethod.invoke(attachment, predicate, false);

            int total = 0;
            for (Object access : slots) {
                ItemStack stack = (ItemStack) h.accessGetMethod.invoke(access);
                total += stack.getCount();
            }
            return total;
        } catch (Exception e) {
            LOGGER.warn("统计饰品槽物品数量失败", e);
            return 0;
        }
    }

    /**
     * 从饰品槽中消耗一个指定物品。
     *
     * @return 是否成功消耗
     */
    public static boolean consumeOne(LivingEntity entity, Item item) {
        if (!TRINKETS_LOADED) {
            return false;
        }
        try {
            MethodHandles h = getHandles();
            Object attachment = h.getAttachmentMethod.invoke(null, entity);
            if (attachment == null) {
                return false;
            }

            // TrinketAttachment.findFirst(Predicate) → Optional<TrinketSlotAccess>
            Predicate<ItemStack> predicate = stack -> stack.is(item);
            @SuppressWarnings("unchecked")
            Optional<Object> opt = (Optional<Object>) h.findFirstPredicateMethod.invoke(attachment, predicate);

            if (opt.isPresent()) {
                Object access = opt.get();
                ItemStack stack = (ItemStack) h.accessGetMethod.invoke(access);
                stack.shrink(1);
                return true;
            }
            return false;
        } catch (Exception e) {
            LOGGER.warn("消耗饰品槽物品失败", e);
            return false;
        }
    }

    // ========== 反射句柄 ==========

    private static MethodHandles getHandles() throws ReflectiveOperationException {
        if (handles == null) {
            synchronized (TrinketHelper.class) {
                if (handles == null) {
                    handles = new MethodHandles();
                }
            }
        }
        return handles;
    }

    /**
     * 缓存反射获取的 Method 句柄，避免重复查找。
     */
    private static final class MethodHandles {
        final java.lang.reflect.Method getAttachmentMethod;
        final java.lang.reflect.Method isEquippedItemMethod;
        final java.lang.reflect.Method findFirstPredicateMethod;
        final java.lang.reflect.Method equippedPredicateMethod;
        final java.lang.reflect.Method accessGetMethod;
        final java.lang.reflect.Method accessSetMethod;
        final java.lang.reflect.Method getInventoriesMethod;
        final java.lang.reflect.Method invContainerSizeMethod;
        final java.lang.reflect.Method invIsVisibleMethod;
        final java.lang.reflect.Method invGetSlotAccessMethod;
        final java.lang.reflect.Method getEntityGroupsMethod;
        final java.lang.reflect.Method groupIsAttachedMethod;
        final java.lang.reflect.Method groupGetSlotsMethod;
        final java.lang.reflect.Method slotTypeNameMethod;

        @SuppressWarnings("unchecked")
        MethodHandles() throws ReflectiveOperationException {
            Class<?> apiClass = Class.forName(TRINKETS_API_CLASS);
            Class<?> attachmentClass = Class.forName("eu.pb4.trinkets.api.TrinketAttachment");
            Class<?> accessClass = Class.forName("eu.pb4.trinkets.api.TrinketSlotAccess");
            Class<?> inventoryClass = Class.forName("eu.pb4.trinkets.api.TrinketInventory");
            Class<?> groupClass = Class.forName("eu.pb4.trinkets.api.SlotGroup");
            Class<?> slotTypeClass = Class.forName("eu.pb4.trinkets.api.SlotType");

            getAttachmentMethod = apiClass.getMethod("getAttachment", LivingEntity.class);
            isEquippedItemMethod = attachmentClass.getMethod("isEquipped", Item.class);
            findFirstPredicateMethod = attachmentClass.getMethod("findFirst", Predicate.class);
            // equipped(Predicate, boolean) returns List<TrinketSlotAccess>
            equippedPredicateMethod = attachmentClass.getMethod("equipped", Predicate.class, boolean.class);
            accessGetMethod = accessClass.getMethod("get");
            accessSetMethod = accessClass.getMethod("set", ItemStack.class);
            getInventoriesMethod = attachmentClass.getMethod("getInventories");
            invContainerSizeMethod = inventoryClass.getMethod("getContainerSize");
            invIsVisibleMethod = inventoryClass.getMethod("isVisible", int.class);
            invGetSlotAccessMethod = inventoryClass.getMethod("getSlotAccess", int.class);
            getEntityGroupsMethod = groupClass.getMethod("getEntityGroups", LivingEntity.class);
            groupIsAttachedMethod = groupClass.getMethod("isAttachedToSlot", Slot.class);
            groupGetSlotsMethod = groupClass.getMethod("getSlots");
            slotTypeNameMethod = slotTypeClass.getMethod("name");
        }
    }
}
