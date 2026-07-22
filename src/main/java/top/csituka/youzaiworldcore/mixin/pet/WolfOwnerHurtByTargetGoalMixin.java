package top.csituka.youzaiworldcore.mixin.pet;

import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.entity.ai.goal.target.OwnerHurtByTargetGoal;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import top.csituka.youzaiworldcore.pet.PetMode;
import top.csituka.youzaiworldcore.pet.PetModeController;
import top.csituka.youzaiworldcore.util.DebugLogger;

/**
 * Mixin — 拦截 {@link OwnerHurtByTargetGoal#canUse()} 以在
 * COMPANIONSHIP / HUNTING 模式下阻止宠物反击攻击主人的目标。
 * <p>
 * {@code OwnerHurtByTargetGoal} 会在主人被某个生物攻击时触发狼反击该生物。
 * HUNTING 模式仅关心主人攻击了谁，不关心谁攻击了主人。
 * COMPANIONSHIP 模式完全不攻击。
 * </p>
 */
@Mixin(OwnerHurtByTargetGoal.class)
public abstract class WolfOwnerHurtByTargetGoalMixin {

    private static final String MODULE = "WolfOwnerHurtByTargetGoalMixin";

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

        // HUNTING 和 COMPANIONSHIP 模式下不响应主人被攻击
        if (!PetModeController.shouldAllowOwnerHurtByTarget(mode)) {
            DebugLogger.debug(MODULE, "OwnerHurtByTargetGoal 被拦截: mode=%s, wolf=%s",
                    mode, wolf.getUUID());
            cir.setReturnValue(false);
        }
    }
}
