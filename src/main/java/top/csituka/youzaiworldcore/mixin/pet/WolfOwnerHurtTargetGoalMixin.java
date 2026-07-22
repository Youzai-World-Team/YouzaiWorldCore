package top.csituka.youzaiworldcore.mixin.pet;

import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.entity.ai.goal.target.OwnerHurtTargetGoal;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import top.csituka.youzaiworldcore.pet.PetMode;
import top.csituka.youzaiworldcore.pet.PetModeController;
import top.csituka.youzaiworldcore.util.DebugLogger;

/**
 * Mixin — 拦截 {@link OwnerHurtTargetGoal#canUse()} 以在
 * COMPANIONSHIP / GUARD / HUNTING 模式下阻止宠物攻击主人锁定的目标。
 * <p>
 * {@code OwnerHurtTargetGoal} 会在主人主动攻击某个生物时触发狼一同攻击。
 * GUARD 和 HUNTING 模式均不应响应主人的主动攻击指令。
 * </p>
 */
@Mixin(OwnerHurtTargetGoal.class)
public abstract class WolfOwnerHurtTargetGoalMixin {

    private static final String MODULE = "WolfOwnerHurtTargetGoalMixin";

    @Shadow
    private TamableAnimal tameAnimal;

    @Inject(method = "canUse", at = @At("HEAD"), cancellable = true)
    private void onCanUse(CallbackInfoReturnable<Boolean> cir) {
        if (!(tameAnimal instanceof Wolf wolf)) {
            return; // 仅处理狼
        }
        if (wolf.level().isClientSide()) {
            return;
        }

        PetMode mode = PetModeController.getMode(wolf);
        if (mode == null) {
            return; // 非宠物，放行
        }

        // COMPANIONSHIP / GUARD / HUNTING 模式下不响应主人主动攻击
        if (!PetModeController.shouldAllowOwnerHurtTarget(mode)) {
            DebugLogger.debug(MODULE, "OwnerHurtTargetGoal 被拦截: mode=%s, wolf=%s",
                    mode, wolf.getUUID());
            cir.setReturnValue(false);
        }
    }
}
