package top.csituka.youzaiworldcore.mixin.pet;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import top.csituka.youzaiworldcore.pet.PetEventHandler;
import top.csituka.youzaiworldcore.pet.PetModeController;
import top.csituka.youzaiworldcore.pet.PetStationaryAccess;

/**
 * Mixin — 拦截 {@link TamableAnimal#tame(Player)} 以在驯服成功后注册宠物。
 * <p>
 * 战斗权限仅作用于已登记的狼。
 * </p>
 */
@Mixin(TamableAnimal.class)
public abstract class TamableAnimalPetMixin {

    @Inject(method = "unableToMoveToOwner", at = @At("HEAD"), cancellable = true)
    private void yzwc$disableStationaryFollowing(CallbackInfoReturnable<Boolean> cir) {
        if (((PetStationaryAccess) this).yzwc$isStationary()) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "tryToTeleportToOwner", at = @At("HEAD"), cancellable = true)
    private void yzwc$disableStationaryOwnerTeleport(CallbackInfo ci) {
        if (((PetStationaryAccess) this).yzwc$isStationary()) {
            ci.cancel();
        }
    }

    @Inject(method = "canAttack", at = @At("HEAD"), cancellable = true)
    private void yzwc$protectPetAllies(LivingEntity target, CallbackInfoReturnable<Boolean> cir) {
        if ((Object) this instanceof Wolf wolf && !PetModeController.allowsTarget(wolf, target)) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "tame", at = @At("RETURN"))
    private void onPostTame(Player player, CallbackInfo ci) {
        TamableAnimal self = (TamableAnimal) (Object) this;
        if (self.level() instanceof ServerLevel level && player instanceof ServerPlayer) {
            PetEventHandler.onTame(self, level);
        }
    }
}
