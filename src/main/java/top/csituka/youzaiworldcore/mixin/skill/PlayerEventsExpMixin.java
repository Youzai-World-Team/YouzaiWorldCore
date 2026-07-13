package top.csituka.youzaiworldcore.mixin.skill;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import top.csituka.youzaiworldcore.skill.AdventureLevelManager;

/**
 * 玩家事件 → 冒险经验。
 * 原版等级升级 / 进食 / 获得 buff / 伤害结算
 */
@Mixin(ServerPlayer.class)
public class PlayerEventsExpMixin {

    @Shadow
    private int lastRecordedExperienceLevel;

    // ─── 原版等级升级检测 ───

    @Inject(method = "tick", at = @At("TAIL"))
    private void onTickCheckLevelUp(CallbackInfo ci) {
        ServerPlayer self = (ServerPlayer) (Object) this;
        int current = self.experienceLevel;
        if (current > lastRecordedExperienceLevel) {
            int gained = current - lastRecordedExperienceLevel;
            for (int i = 0; i < gained; i++) {
                AdventureLevelManager.grantExp(self, AdventureLevelManager.EXP_VANILLA_LEVEL_UP);
            }
        }
        lastRecordedExperienceLevel = current;
    }

    // ─── 进食 ───

    @Inject(method = "eat", at = @At("HEAD"))
    private void onEat(Level level, ItemStack food, CallbackInfoReturnable<ItemStack> cir) {
        ServerPlayer self = (ServerPlayer) (Object) this;
        AdventureLevelManager.grantExp(self, AdventureLevelManager.EXP_EAT_FOOD);
    }

    // ─── 睡觉跳过夜晚 ───

    @Inject(method = "startSleepInBed", at = @At("RETURN"))
    private void onStartSleep(CallbackInfoReturnable<?> cir) {
        ServerPlayer self = (ServerPlayer) (Object) this;
        if (cir.getReturnValue() != null) {
            AdventureLevelManager.grantExp(self, AdventureLevelManager.EXP_SLEEP);
        }
    }

    // ─── 获得状态 buff ───

    @Inject(method = "addEffect(Lnet/minecraft/world/effect/MobEffectInstance;Lnet/minecraft/world/entity/Entity;)Z",
            at = @At("RETURN"))
    private void onAddEffect(MobEffectInstance effectInstance, net.minecraft.world.entity.Entity entity,
                             CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValue() != null && cir.getReturnValue()) {
            ServerPlayer self = (ServerPlayer) (Object) this;
            AdventureLevelManager.grantExp(self, AdventureLevelManager.EXP_STATUS_EFFECT);
        }
    }

    // ─── 近战标记（由 DamageExpMixin 处理伤害结算，此处仅保留接口）───
}
