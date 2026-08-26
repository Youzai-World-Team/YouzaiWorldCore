package top.csituka.youzaiworldcore.event;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.phys.Vec3;
import top.csituka.youzaiworldcore.enchantment.ModEnchantments;
import top.csituka.youzaiworldcore.util.DebugLogger;

/**
 * 弹跳 (Bounce) 附魔: 盾牌附魔（副手），玩家受击时将攻击者弹开。
 * 等级 1: 基础弹开；等级 2: 更强弹开。
 * <p>
 * 监听 {@link ServerLivingEntityEvents#AFTER_DAMAGE}，受击后对攻击者施加远离玩家的速度。
 */
public class BounceHandler {

    private static final String MODULE = "BounceHandler";

    public static void register() {
        DebugLogger.entering(MODULE, "register");

        ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamage, damageTaken, blocked) -> {
            if (!(entity instanceof ServerPlayer player))
                return;
            if (player.level().isClientSide())
                return;

            var reg = player.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
            @SuppressWarnings("null")
            Holder<Enchantment> holder = reg.getOrThrow(ModEnchantments.BOUNCE_KEY);

            // 检查副手盾牌的弹跳附魔等级
            int level = player.getItemBySlot(EquipmentSlot.OFFHAND).getEnchantments().getLevel(holder);
            if (level <= 0)
                return;

            Entity attacker = source.getEntity();
            if (attacker == null || attacker == player)
                return;

            // 计算弹开方向：从玩家水平指向攻击者
            Vec3 pushDir = new Vec3(attacker.getX() - player.getX(), 0, attacker.getZ() - player.getZ());
            double len = pushDir.length();
            if (len < 0.0001)
                return;
            pushDir = pushDir.scale(1.0 / len);

            double strength = (level == 1) ? 0.6 : 0.9;
            Vec3 delta = new Vec3(pushDir.x * strength, 0.35, pushDir.z * strength);
            attacker.setDeltaMovement(attacker.getDeltaMovement().add(delta));
            attacker.hurtMarked = true;

            DebugLogger.info(MODULE, "Bounce Lv%d 弹开攻击者: player=%s, attacker=%s, strength=%.2f",
                    level, player.getName().getString(), attacker.getName().getString(), strength);
        });

        DebugLogger.info(MODULE, "弹跳附魔事件处理器已注册 (ServerLivingEntityEvents.AFTER_DAMAGE)");
        DebugLogger.exiting(MODULE, "register");
    }
}
