package top.csituka.youzaiworldcore.mixin.pet;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import top.csituka.youzaiworldcore.pet.PetCombatAccess;
import top.csituka.youzaiworldcore.pet.PetModeController;

@Mixin(Wolf.class)
public abstract class WolfModeAttackMixin extends TamableAnimal implements PetCombatAccess {
    @Unique
    private List<WrappedGoal> yzwc$vanillaTargets;
    @Unique
    private PetModeController yzwc$petTarget;

    protected WolfModeAttackMixin(EntityType<? extends TamableAnimal> type, Level level) {
        super(type, level);
    }

    @SuppressWarnings("null")
    @WrapOperation(method = "registerGoals", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/ai/goal/GoalSelector;addGoal(ILnet/minecraft/world/entity/ai/goal/Goal;)V"))
    private void yzwc$rememberVanillaTargets(GoalSelector selector, int priority, Goal goal, Operation<Void> original) {
        original.call(selector, priority, goal);
        if (selector == targetSelector) {
            if (yzwc$vanillaTargets == null) {
                yzwc$vanillaTargets = new ArrayList<>();
            }
            yzwc$vanillaTargets.add(new WrappedGoal(priority, goal));
        }
    }

    @Override
    public void yzwc$setManagedPet(boolean managed) {
        if (managed && yzwc$petTarget == null) {
            yzwc$vanillaTargets.forEach(target -> targetSelector.removeGoal(target.getGoal()));
            Wolf wolf = (Wolf) (Object) this;
            PetModeController.stopCombat(wolf);
            yzwc$petTarget = new PetModeController(wolf);
            targetSelector.addGoal(1, yzwc$petTarget);
        } else if (!managed && yzwc$petTarget != null) {
            targetSelector.removeGoal(yzwc$petTarget);
            yzwc$petTarget = null;
            yzwc$vanillaTargets.forEach(target -> targetSelector.addGoal(target.getPriority(), target.getGoal()));
        }
    }
}
