package top.csituka.youzaiworldcore.event;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.enchantment.Enchantment;
import top.csituka.youzaiworldcore.enchantment.ModEnchantments;
import top.csituka.youzaiworldcore.util.DebugLogger;

/**
 * 风之充能 (Wind Charge) 附魔: 装备鞘翅滑翔时获得冲刺加速。
 * 等级 1: 基础加速；等级 2: 更强加速。
 * <p>
 * 每 tick 朝视角方向施加推力，但水平速度达到上限后停止加速，避免无限加速飞行。
 */
public class WindChargeHandler {

    private static final String MODULE = "WindChargeHandler";
    /** 水平速度上限（blocks/tick），超过则不再加速 */
    private static final double MAX_HORIZONTAL_SPEED = 1.5;

    /** 缓存的风之充能附魔 Holder，首次 tick 时延迟初始化，避免每 tick 注册表查询。 */
    private static Holder<Enchantment> cachedHolder = null;
    /** 缓存失效标记：当资源重载时由外部重置。 */
    private static volatile boolean holderDirty = true;

    /**
     * 当资源重载（如 /reload）时调用，使缓存失效，下次 tick 重新查询注册表。
     */
    public static void markHolderDirty() {
        holderDirty = true;
        DebugLogger.info(MODULE, "附魔注册表缓存已标记失效，下次 tick 将重新查询");
    }

    @SuppressWarnings("null")
    public static void register() {
        DebugLogger.entering(MODULE, "register");

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            // 延迟初始化 + 失效检测：仅在首次使用或数据包重载后查询注册表
            if (holderDirty || cachedHolder == null) {
                var reg = server.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
                cachedHolder = reg.getOrThrow(ModEnchantments.WIND_CHARGE_KEY);
                holderDirty = false;
                DebugLogger.info(MODULE, "附魔注册表缓存已初始化/刷新");
            }
            Holder<Enchantment> holder = cachedHolder;

            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                if (!player.isFallFlying())
                    continue;

                @SuppressWarnings("null")
                int level = player.getItemBySlot(EquipmentSlot.CHEST).getEnchantments()
                        .getLevel(holder);
                if (level <= 0)
                    continue;

                // 速度上限钳制：水平速度达到上限后不再加速，避免无限叠加
                var delta = player.getDeltaMovement();
                double hSpeed = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
                if (hSpeed >= MAX_HORIZONTAL_SPEED)
                    continue;

                var look = player.getLookAngle();
                double boost = 0.08 + (level - 1) * 0.04;
                player.push(look.x * boost, Math.max(look.y * boost, -0.05), look.z * boost);
                player.hurtMarked = true;
            }
        });

        // 数据包重载时（/reload）使缓存失效，下次 tick 重新查询注册表
        ServerLifecycleEvents.END_DATA_PACK_RELOAD.register((server, resourceManager, success) -> {
            if (success) {
                markHolderDirty();
            }
        });

        DebugLogger.info(MODULE, "风之充能附魔 Tick 事件处理器已注册 (maxHorizontalSpeed=%.2f)",
                MAX_HORIZONTAL_SPEED);
        DebugLogger.exiting(MODULE, "register");
    }
}
