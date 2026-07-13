package top.csituka.youzaiworldcore.mixin.skill;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import top.csituka.youzaiworldcore.skill.AdventureLevelManager;

/**
 * 伤害事件 → 冒险经验。
 * 近战每点 +0.5 / 远程每点 +0.5 / 承受每点 +0.5
 */
@Mixin(LivingEntity.class)
public class DamageExpMixin {

    @Inject(method = "hurtServer", at = @At("TAIL"))
    private void onHurt(net.minecraft.server.level.ServerLevel serverLevel, DamageSource source, float amount,
                        CallbackInfoReturnable<?> cir) {
        if (amount <= 0) return;
        LivingEntity self = (LivingEntity) (Object) this;

        // 受害者是玩家 → 承受伤害
        if (self instanceof ServerPlayer victim) {
            AdventureLevelManager.onTakeDamage(victim, amount);
        }

        // 伤害来源是玩家 → 造成伤害
        if (source.getEntity() instanceof ServerPlayer attacker) {
            boolean isMelee = source.is(DamageTypes.PLAYER_ATTACK)
                    || source.is(DamageTypes.MOB_ATTACK_NO_AGGRO);
            if (isMelee) {
                AdventureLevelManager.onMeleeDamage(attacker, amount);
            } else {
                AdventureLevelManager.onRangedDamage(attacker, amount);
            }
        }
    }
}
