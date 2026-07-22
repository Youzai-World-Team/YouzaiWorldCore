package top.csituka.youzaiworldcore.mixin.pet;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.wolf.Wolf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import top.csituka.youzaiworldcore.pet.PetMode;
import top.csituka.youzaiworldcore.pet.PetModeController;
import top.csituka.youzaiworldcore.util.DebugLogger;

/**
 * Mixin — 拦截 {@link Wolf#wantsToAttack(LivingEntity, LivingEntity)} 以根据宠物模式控制攻击行为。
 * <p>
 * {@code OwnerHurtByTargetGoal} 和 {@code OwnerHurtTargetGoal} 均会调用此方法。
 * 对于 COMPANIONSHIP 模式，直接返回 false 阻止所有攻击行为。
 * 对于 GUARD/HUNTING/ATTACK 模式，放行（精细控制在单独的目标 Goal Mixin 中处理）。
 * </p>
 */
@Mixin(Wolf.class)
public abstract class WolfModeAttackMixin {

    private static final String MODULE = "WolfModeAttackMixin";

    @Inject(method = "wantsToAttack", at = @At("HEAD"), cancellable = true)
    private void onWantsToAttack(LivingEntity target, LivingEntity owner,
                                  CallbackInfoReturnable<Boolean> cir) {
        Wolf wolf = (Wolf) (Object) this;

        // 非服务端不处理
        if (wolf.level().isClientSide()) {
            return;
        }

        PetMode mode = PetModeController.getMode(wolf);
        if (mode == null) {
            return; // 非宠物，放行原版
        }

        // COMPANIONSHIP 模式：不攻击任何目标
        if (mode == PetMode.COMPANIONSHIP) {
            DebugLogger.debug(MODULE, "COMPANIONSHIP 模式阻止攻击: wolf=%s, target=%s",
                    wolf.getUUID(), target != null ? target.getUUID() : "null");
            cir.setReturnValue(false);
        }

        // 其他模式放行 — 精细控制由 OwnerHurtTargetGoal / HurtByTargetGoal 的 Mixin 处理
    }
}
