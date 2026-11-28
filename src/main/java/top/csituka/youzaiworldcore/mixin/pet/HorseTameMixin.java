package top.csituka.youzaiworldcore.mixin.pet;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import top.csituka.youzaiworldcore.pet.PetEventHandler;

@Mixin(AbstractHorse.class)
public abstract class HorseTameMixin {
    @Inject(method = "tameWithName", at = @At("RETURN"))
    private void yzwc$registerTamedMount(Player player, CallbackInfoReturnable<Boolean> result) {
        var mount = (AbstractHorse) (Object) this;
        if (result.getReturnValueZ() && player instanceof ServerPlayer && mount.level() instanceof ServerLevel level) {
            PetEventHandler.onTame(mount, level);
        }
    }
}
