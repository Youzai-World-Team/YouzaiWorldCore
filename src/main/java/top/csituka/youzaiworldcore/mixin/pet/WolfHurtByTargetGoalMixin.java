package top.csituka.youzaiworldcore.mixin.pet;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.animal.wolf.Wolf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.csituka.youzaiworldcore.pet.PetInternalTags;

@Mixin(HurtByTargetGoal.class)
public abstract class WolfHurtByTargetGoalMixin {
    @Inject(method = "alertOther", at = @At("HEAD"), cancellable = true)
    private void yzwc$skipManagedPet(Mob other, LivingEntity attacker, CallbackInfo ci) {
        if (other instanceof Wolf wolf && wolf.entityTags().contains(PetInternalTags.TAG_PET_MARKER)) {
            ci.cancel();
        }
    }
}
