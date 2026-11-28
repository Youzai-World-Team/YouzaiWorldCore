package top.csituka.youzaiworldcore.mixin.pet;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.NameTagItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import top.csituka.youzaiworldcore.pet.PetEventHandler;

@Mixin(NameTagItem.class)
public abstract class PetNameTagMixin {
    @Inject(method = "interactLivingEntity", at = @At("RETURN"))
    private void yzwc$syncVanillaPetName(ItemStack stack, Player player, LivingEntity target, InteractionHand hand,
            CallbackInfoReturnable<InteractionResult> result) {
        if (result.getReturnValue() == InteractionResult.SUCCESS && target instanceof Mob pet
                && pet.isAlive() && pet.level() instanceof ServerLevel level) {
            PetEventHandler.onVanillaNameChange(pet, level);
        }
    }
}
