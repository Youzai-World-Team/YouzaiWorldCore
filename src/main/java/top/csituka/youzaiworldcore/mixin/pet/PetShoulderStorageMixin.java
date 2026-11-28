package top.csituka.youzaiworldcore.mixin.pet;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.csituka.youzaiworldcore.dimensionalinventories.PoolPets;

@Mixin(ServerPlayer.class)
public abstract class PetShoulderStorageMixin {
    @Inject(method = "respawnEntityOnShoulder", at = @At("HEAD"), cancellable = true)
    private void yzwc$skipStaleShoulderPet(CompoundTag data, CallbackInfo ci) {
        if (!PoolPets.canLoadShoulder(((ServerPlayer) (Object) this).level(), data)) {
            ci.cancel();
        }
    }
}
