package top.csituka.youzaiworldcore.mixin.pet;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import top.csituka.youzaiworldcore.pet.PetMode;
import top.csituka.youzaiworldcore.pet.PetModeController;
import top.csituka.youzaiworldcore.util.DebugLogger;

/**
 * Mixin — 拦截 {@link HurtByTargetGoal#canUse()} 以在
 * COMPANIONSHIP 模式下阻止宠物反击攻击者。
 * <p>
 * {@code HurtByTargetGoal} 会在狼自己被攻击时触发反击。
 * COMPANIONSHIP 模式下狼应完全被动，不响应任何攻击。
 * </p>
 * <p>
 * {@code mob} 字段继承自父类 {@code TargetGoal}，Mixin @Shadow 无法直接引用。
 * 通过 {@link TargetGoalAccessor#getMob()} 间接获取。
 * </p>
 */
@Mixin(HurtByTargetGoal.class)
public abstract class WolfHurtByTargetGoalMixin {

    private static final String MODULE = "WolfHurtByTargetGoalMixin";

    @Inject(method = "canUse", at = @At("HEAD"), cancellable = true)
    private void onCanUse(CallbackInfoReturnable<Boolean> cir) {
        Mob mob = ((TargetGoalAccessor) this).getMob();
        if (!(mob instanceof Wolf wolf)) {
            return; // 仅处理狼
        }
        if (wolf.level().isClientSide()) {
            return;
        }

        PetMode mode = PetModeController.getMode(wolf);
        if (mode == null) {
            return; // 非宠物，放行
        }

        // COMPANIONSHIP 模式下不反击
        if (!PetModeController.shouldAllowHurtByTarget(mode)) {
            DebugLogger.debug(MODULE, "HurtByTargetGoal 被拦截: mode=%s, wolf=%s",
                    mode, wolf.getUUID());
            cir.setReturnValue(false);
        }
    }
}
