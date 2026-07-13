package top.csituka.youzaiworldcore.mixin.skill;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import top.csituka.youzaiworldcore.skill.AdventureLevelManager;

/**
 * 混合注入 {@link LivingEntity#checkTotemDeathProtection}，
 * 当玩家被不死图腾救下时发放冒险经验。
 */
@Mixin(LivingEntity.class)
public class TotemExpMixin {

    /**
     * 在不死图腾成功保护后发放经验。
     * 注入于 RETURN 后，仅在返回 true（图腾生效）时为 ServerPlayer 发放经验。
     */
    @Inject(method = "checkTotemDeathProtection", at = @At("RETURN"))
    private void onTotemProtect(DamageSource damageSource, CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValue() != null && cir.getReturnValue()) {
            // noinspection ConstantValue
            if ((Object) this instanceof ServerPlayer player) {
                AdventureLevelManager.grantExp(player, AdventureLevelManager.EXP_TOTEM_OF_UNDYING);
            }
        }
    }
}
