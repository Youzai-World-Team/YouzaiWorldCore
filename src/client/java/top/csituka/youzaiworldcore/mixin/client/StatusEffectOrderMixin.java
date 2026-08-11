package top.csituka.youzaiworldcore.mixin.client;

import net.minecraft.client.Minecraft;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import top.csituka.youzaiworldcore.client.hud.StatusEffectHudRenderer;

/**
 * 监听本地玩家新增状态效果的时机，为 YZUI 状态效果 HUD 保留获得顺序。
 */
@SuppressWarnings("null")
@Mixin(LivingEntity.class)
public abstract class StatusEffectOrderMixin {

    @Inject(method = "onEffectAdded", at = @At("TAIL"))
    private void youzaiworldcore$recordEffectOrder(MobEffectInstance instance,
            Entity source, CallbackInfo ci) {
        Object self = this;
        Minecraft client = Minecraft.getInstance();
        if (self == client.player && self instanceof Player player) {
            StatusEffectHudRenderer.onEffectAdded(player, instance);
        }
    }

    @Inject(method = "removeEffectNoUpdate", at = @At("RETURN"))
    private void youzaiworldcore$removeEffectOrder(
            net.minecraft.core.Holder<net.minecraft.world.effect.MobEffect> effect,
            CallbackInfoReturnable<MobEffectInstance> cir) {
        MobEffectInstance removed = cir.getReturnValue();
        Object self = this;
        Minecraft client = Minecraft.getInstance();
        if (removed != null && self == client.player && self instanceof Player player) {
            StatusEffectHudRenderer.onEffectRemoved(player, removed);
        }
    }
}
