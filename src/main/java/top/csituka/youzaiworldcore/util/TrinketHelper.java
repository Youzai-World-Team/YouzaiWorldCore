package top.csituka.youzaiworldcore.util;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

        MethodHandles() throws ReflectiveOperationException {
            Class<?> apiClass = Class.forName(TRINKETS_API_CLASS);
            Class<?> attachmentClass = Class.forName("eu.pb4.trinkets.api.TrinketAttachment");
            Class<?> accessClass = Class.forName("eu.pb4.trinkets.api.TrinketSlotAccess");

            getAttachmentMethod = apiClass.getMethod("getAttachment", LivingEntity.class);
            isEquippedItemMethod = attachmentClass.getMethod("isEquipped", Item.class);
            findFirstPredicateMethod = attachmentClass.getMethod("findFirst", Predicate.class);
            // equipped(Predicate, boolean) returns List<TrinketSlotAccess>
            equippedPredicateMethod = attachmentClass.getMethod("equipped", Predicate.class, boolean.class);
            accessGetMethod = accessClass.getMethod("get");
        }
    }
}
