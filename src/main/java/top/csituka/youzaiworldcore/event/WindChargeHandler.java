package top.csituka.youzaiworldcore.event;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.enchantment.Enchantment;
import top.csituka.youzaiworldcore.enchantment.ModEnchantments;

/**
 * 风之充能 (Wind Charge) 附魔: 装备鞘翅滑翔时获得冲刺加速。
 * 等级 1: 基础加速；等级 2: 更强加速。
 */
public class WindChargeHandler {

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            var reg = server.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
            Holder<Enchantment> holder = reg.getOrThrow(ModEnchantments.WIND_CHARGE_KEY);

            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                if (!player.isFallFlying())
                    continue;

                int level = player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.CHEST).getEnchantments()
                        .getLevel(holder);
                if (level <= 0)
                    continue;

                // 向前冲刺
                var look = player.getLookAngle();
                double boost = 0.08 + (level - 1) * 0.04;
                player.push(look.x * boost, Math.max(look.y * boost, -0.05), look.z * boost);
                player.hurtMarked = true;
            }
        });
    }
}
