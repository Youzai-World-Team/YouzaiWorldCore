package top.csituka.youzaiworldcore.mixin.pet;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.animal.parrot.ShoulderRidingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import top.csituka.youzaiworldcore.pet.PetStationaryAccess;

@Mixin(ShoulderRidingEntity.class)
public abstract class StationaryShoulderPetMixin {
    @Inject(method = "setEntityOnShoulder", at = @At("HEAD"), cancellable = true)
    private void yzwc$keepStationaryPetOffShoulder(ServerPlayer player, CallbackInfoReturnable<Boolean> cir) {
        if (((PetStationaryAccess) this).yzwc$isStationary()) {
            cir.setReturnValue(false);
        }
    }
}
