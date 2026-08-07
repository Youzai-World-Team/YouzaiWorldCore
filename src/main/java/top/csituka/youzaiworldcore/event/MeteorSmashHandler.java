package top.csituka.youzaiworldcore.event;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.enchantment.Enchantment;
import top.csituka.youzaiworldcore.enchantment.ModEnchantments;
import top.csituka.youzaiworldcore.util.DebugLogger;

/**
 * 流星猛击 (Meteor Smash) 附魔: 重锤附魔，猛击（mace_smash）时点燃被击目标周围的生物。
 * 等级 1: 点燃范围 3 格内生物 10 秒。
 * <p>
 * 监听 {@link ServerLivingEntityEvents#AFTER_DAMAGE}：伤害源带 {@link DamageTypeTags#IS_MACE_SMASH}
 * 标签且攻击者主手重锤带流星猛击附魔时，以被击目标为中心 AoE 点燃（含被击目标本身）。
 */
public class MeteorSmashHandler {

    private static final String MODULE = "MeteorSmashHandler";
    private static final double RADIUS = 3.0;
    private static final int FIRE_DURATION = 200; // 10 秒

    public static void register() {
        DebugLogger.entering(MODULE, "register");

        ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamage, damageTaken, blocked) -> {
            if (entity.level().isClientSide())
                return;
            // 仅在重锤猛击（下砸）时触发
            if (!source.is(DamageTypeTags.IS_MACE_SMASH))
                return;

            Entity attacker = source.getEntity();
            if (!(attacker instanceof ServerPlayer player))
                return;

            ServerLevel level = (ServerLevel) entity.level();
            var reg = level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
            Holder<Enchantment> holder = reg.getOrThrow(ModEnchantments.METEOR_SMASH_KEY);
            int enchantLevel = player.getMainHandItem().getEnchantments().getLevel(holder);
            if (enchantLevel <= 0)
                return;

            // 以被击目标为中心 AoE 点燃（含被击目标本身，排除攻击者）
            var nearby = level.getEntitiesOfClass(LivingEntity.class,
                    entity.getBoundingBox().inflate(RADIUS),
                    e -> e.isAlive() && e != player);
            for (LivingEntity target : nearby) {
                target.igniteForTicks(FIRE_DURATION);
            }

            DebugLogger.info(MODULE, "Meteor Smash 点燃 %d 个实体 (player=%s, center=%s, radius=%.1f)",
                    nearby.size(), player.getName().getString(), entity.getName().getString(), RADIUS);
        });

        DebugLogger.info(MODULE, "流星猛击附魔事件处理器已注册 (ServerLivingEntityEvents.AFTER_DAMAGE)");
        DebugLogger.exiting(MODULE, "register");
    }
}
