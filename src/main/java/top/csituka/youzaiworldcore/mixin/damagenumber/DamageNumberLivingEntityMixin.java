package top.csituka.youzaiworldcore.mixin.damagenumber;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import top.csituka.youzaiworldcore.damagenumber.DamageNumberHandler;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * 记录 {@link LivingEntity#hurtServer(ServerLevel, DamageSource, float)} 前后的有效生命值，
 * 以获得护甲、附魔、抗性与吸收生命结算后的实际伤害。
 */
@Mixin(LivingEntity.class)
public class DamageNumberLivingEntityMixin {

    @Unique
    private Deque<Float> youzaiworldcore$effectiveHealthBeforeDamage;

    @Inject(method = "hurtServer", at = @At("HEAD"))
    private void youzaiworldcore$captureEffectiveHealth(ServerLevel level, DamageSource source, float amount,
                                                        CallbackInfoReturnable<Boolean> cir) {
        LivingEntity entity = (LivingEntity) (Object) this;
        if (youzaiworldcore$effectiveHealthBeforeDamage == null) {
            youzaiworldcore$effectiveHealthBeforeDamage = new ArrayDeque<>();
        }
        youzaiworldcore$effectiveHealthBeforeDamage.addLast(
                entity.getHealth() + entity.getAbsorptionAmount());
    }

    @Inject(method = "hurtServer", at = @At("RETURN"))
    private void youzaiworldcore$broadcastDamageNumber(ServerLevel level, DamageSource source, float amount,
                                                       CallbackInfoReturnable<Boolean> cir) {
        if (youzaiworldcore$effectiveHealthBeforeDamage == null
                || youzaiworldcore$effectiveHealthBeforeDamage.isEmpty()) {
            return;
        }

        float effectiveHealthBefore = youzaiworldcore$effectiveHealthBeforeDamage.removeLast();
        if (!cir.getReturnValue()) {
            return;
        }

        LivingEntity entity = (LivingEntity) (Object) this;
        float effectiveHealthAfter = entity.getHealth() + entity.getAbsorptionAmount();
        float actualDamage = effectiveHealthBefore - effectiveHealthAfter;
        if (actualDamage >= 0.005F) {
            DamageNumberHandler.broadcast(entity, source, actualDamage);
        }
    }
}
