package top.csituka.youzaiworldcore.event;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.enchantment.Enchantment;
import top.csituka.youzaiworldcore.enchantment.ModEnchantments;
import top.csituka.youzaiworldcore.util.DebugLogger;

/**
 * 发光光环 (Glowing Aura) 附魔: 头盔附魔，持续给予玩家发光效果，
 * 使自身及周围实体可见。
 * 等级 1: 持续发光；等级 2: 每 10 秒对周围生物施加短暂发光。
 */
public class GlowingAuraHandler {

    private static final String MODULE = "GlowingAuraHandler";
    private static int tickCounter = 0;

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            tickCounter++;
            var reg = server.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
            Holder<Enchantment> holder = reg.getOrThrow(ModEnchantments.GLOWING_AURA_KEY);

            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                int level = player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD)
                        .getEnchantments().getLevel(holder);
                if (level <= 0) continue;

                // 自身持续发光
                if (!player.hasEffect(MobEffects.GLOWING)) {
                    player.addEffect(new MobEffectInstance(MobEffects.GLOWING, 200, 0, false, false, true));
                }

                // 等级 2: 每 10 秒对周围 8 格范围内的生物施加短暂发光
                if (level >= 2 && tickCounter % 200 == 0) {
                    var nearby = player.level().getEntitiesOfClass(
                            net.minecraft.world.entity.LivingEntity.class,
                            player.getBoundingBox().inflate(8.0),
                            e -> e != player && e.isAlive()
                    );
                    for (var entity : nearby) {
                        entity.addEffect(new MobEffectInstance(MobEffects.GLOWING, 100, 0, false, true));
                    }
                    DebugLogger.info(MODULE, "Glowing Aura II highlighted %d entities near %s",
                            nearby.size(), player.getName().getString());
                }
            }
        });
    }
}
