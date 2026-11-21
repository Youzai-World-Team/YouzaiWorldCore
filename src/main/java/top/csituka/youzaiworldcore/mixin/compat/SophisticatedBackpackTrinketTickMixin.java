package top.csituka.youzaiworldcore.mixin.compat;

import eu.pb4.trinkets.api.TrinketSlotAccess;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Trinkets 会 tick 所有非空有效库存槽；原生背包回调还须排除不能生效的展示槽。 */
@Pseudo
@Mixin(targets = "net.p3pp3rf1y.sophisticatedbackpacks.compat.trinkets.BackpackTrinket", remap = false)
public abstract class SophisticatedBackpackTrinketTickMixin {
    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void youzaiworldcore$onlyActiveTrinket(ItemStack stack, TrinketSlotAccess slot,
            LivingEntity entity, CallbackInfo ci) {
        if (slot == null || !slot.canApplyEffects()) ci.cancel();
    }
}
