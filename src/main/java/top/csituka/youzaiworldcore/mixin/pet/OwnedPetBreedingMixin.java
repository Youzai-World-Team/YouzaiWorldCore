package top.csituka.youzaiworldcore.mixin.pet;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.wolf.Wolf;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.csituka.youzaiworldcore.pet.PetEventHandler;

@Mixin(Animal.class)
public abstract class OwnedPetBreedingMixin {
    @Inject(method = "finalizeSpawnChildFromBreeding", at = @At("RETURN"))
    private void yzwc$registerOwnedOffspring(ServerLevel level, Animal partner, @Nullable AgeableMob child,
            CallbackInfo ci) {
        if (child != null && !(child instanceof Wolf)) {
            PetEventHandler.onTame(child, level);
        }
    }
}
