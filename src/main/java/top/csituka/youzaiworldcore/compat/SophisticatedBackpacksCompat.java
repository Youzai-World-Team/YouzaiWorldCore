package top.csituka.youzaiworldcore.compat;

import eu.pb4.trinkets.api.TrinketAttachment;
import eu.pb4.trinkets.api.TrinketInventory;
import eu.pb4.trinkets.api.TrinketSlotAccess;
import eu.pb4.trinkets.api.TrinketsApi;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import top.csituka.youzaiworldcore.util.DebugLogger;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * 为精妙背包 Fabric 26.3 版统一饰品库存读取；缺少原生适配时补注册库存来源。
 * 仅通过反射访问可选模组，未安装时不加载；不保存数据、不修改背包内容。
 */
public final class SophisticatedBackpacksCompat {
    private static final String MODULE = "SophisticatedBackpacksCompat";
    private static final String API_PACKAGE = "net.p3pp3rf1y.sophisticatedbackpacks.util.";
    private static final String NATIVE_COMPAT_CLASS = "net.p3pp3rf1y.sophisticatedbackpacks.compat.trinkets.TrinketsCompat";
    private static final String HANDLER_NAME = "trinkets";
    private static final ThreadLocal<Boolean> RENDER_QUERY = ThreadLocal.withInitial(() -> false);

    private SophisticatedBackpacksCompat() { }

    /**
     * 在每个库存提供器构造结束后注册，覆盖客户端、专用服和单人集成服使用的实例。
     * 已有原生适配时跳过补注册，改由原生适配 Mixin 复用相同的读取规则。
     */
    public static void registerProvider(Object provider) {
        ClassLoader loader = provider.getClass().getClassLoader();
        try {
            Class.forName(NATIVE_COMPAT_CLASS, false, loader);
            return;
        } catch (ClassNotFoundException ignored) {
            // 3.24.1.0 移植版未提供原生 TrinketsCompat，按已核验的八参数接口补齐。
        }
        try {
            Class<?> countType = Class.forName(API_PACKAGE + "PlayerInventoryHandler$SlotCountGetter", false, loader);
            Class<?> stackType = Class.forName(API_PACKAGE + "PlayerInventoryHandler$SlotStackGetter", false, loader);
            Method register = provider.getClass().getMethod("addPlayerInventoryHandler",
                    String.class, BiFunction.class, countType, stackType,
                    boolean.class, boolean.class, boolean.class, boolean.class);
            BiFunction<Player, Long, Set<String>> identifiers = (player, tick) -> getTrinketInventoryIds(player);
            Object count = callback(countType, args -> {
                TrinketInventory inventory = getTrinketInventory((Player) args[0], (String) args[1]);
                return inventory == null ? 0 : inventory.getContainerSize();
            });
            Object stack = callback(stackType, args -> stack((Player) args[0], (String) args[1], (int) args[2]));
            // 可开启、可渲染，不作为主物品栏，不使用独立渲染器；沿用背包自身的渲染与升级处理。
            register.invoke(provider, HANDLER_NAME, identifiers, count, stack, false, true, false, true);
            DebugLogger.SERVER.debug(MODULE, "已补充精妙背包 Trinkets 库存处理器");
        } catch (ReflectiveOperationException | LinkageError error) {
            DebugLogger.SERVER.exception(MODULE, "registerProvider", error);
        }
    }

    /** 渲染查询尊重隐藏与外观槽；隐藏的有效背包仍能快捷打开并执行升级功能。 */
    public static <T> T withRenderVisibility(Supplier<T> query) {
        boolean previous = RENDER_QUERY.get();
        RENDER_QUERY.set(true);
        try {
            return query.get();
        } finally {
            if (previous) RENDER_QUERY.set(true);
            else RENDER_QUERY.remove();
        }
    }

    /** 当前玩家的独立槽位快照；涵盖只放了外观背包的库存，不共享其他玩家的槽位缓存。 */
    public static Set<String> getTrinketInventoryIds(Player player) {
        TrinketAttachment attachment = TrinketsApi.getAttachment(player);
        return attachment == null ? Set.of() : new LinkedHashSet<>(attachment.getInventories().keySet());
    }

    /** 原生联动使用作者声明的处理器名，旧版补注册仍使用 trinkets；只在打开快捷键时查询。 */
    public static String getTrinketHandlerName() throws ReflectiveOperationException {
        ClassLoader loader = SophisticatedBackpacksCompat.class.getClassLoader();
        try {
            Class.forName(NATIVE_COMPAT_CLASS, false, loader);
        } catch (ClassNotFoundException ignored) {
            return HANDLER_NAME;
        }
        Class<?> ids = Class.forName("net.p3pp3rf1y.sophisticatedbackpacks.compat.CompatModIds", false, loader);
        return (String) ids.getField("TRINKETS").get(null);
    }

    /** 读取饰品库存；缺少附件或槽位时返回 null，交给原生处理器的默认值逻辑处理。 */
    public static TrinketInventory getTrinketInventory(Player player, String identifier) {
        TrinketAttachment attachment = TrinketsApi.getAttachment(player);
        return attachment == null ? null : attachment.getInventories().get(identifier);
    }

    private static ItemStack stack(Player player, String identifier, int slot) {
        return getTrinketStack(getTrinketInventory(player, identifier), slot);
    }

    /** 原生与补注册处理器共用的槽位读取：功能只读有效槽，渲染尊重隐藏与外观覆盖。 */
    public static ItemStack getTrinketStack(TrinketInventory inventory, int slot) {
        if (inventory == null || slot < 0 || slot >= inventory.getContainerSize()
                || (RENDER_QUERY.get() && !inventory.isVisible(slot))) return ItemStack.EMPTY;
        if (RENDER_QUERY.get() && inventory.hasCosmeticItems()) {
            ItemStack cosmetic = inventory.getCosmeticItem(slot);
            if (!cosmetic.isEmpty()) return cosmetic;
        }
        TrinketSlotAccess access = inventory.getSlotAccess(slot);
        // 新版 Trinkets 的展示槽不能执行背包升级；是否隐藏只影响渲染。
        return access == null || (!RENDER_QUERY.get() && !access.canApplyEffects())
                ? ItemStack.EMPTY : access.get();
    }

    private static Object callback(Class<?> contract, Function<Object[], Object> function) {
        return Proxy.newProxyInstance(contract.getClassLoader(), new Class<?>[]{contract}, (proxy, method, args) -> {
            if (method.getDeclaringClass() == Object.class) {
                return switch (method.getName()) {
                    case "toString" -> MODULE + ":" + contract.getSimpleName();
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                };
            }
            return function.apply(args);
        });
    }
}
