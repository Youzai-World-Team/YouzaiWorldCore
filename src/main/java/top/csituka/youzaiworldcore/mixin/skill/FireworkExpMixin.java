package top.csituka.youzaiworldcore.mixin.skill;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.FireworkRocketItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import top.csituka.youzaiworldcore.skill.AdventureLevelManager;

/**
 * 烟花火箭燃放 → 首次 +1000
 */
@Mixin(FireworkRocketItem.class)
public class FireworkExpMixin {

    @Inject(method = "use", at = @At("RETURN"))
    private void onFireworkUse(Level level, Player player, InteractionHand hand,
                               CallbackInfoReturnable<InteractionResult> cir) {
        if (player instanceof ServerPlayer sp
                && cir.getReturnValue() == InteractionResult.CONSUME) {
            AdventureLevelManager.checkFirstFirework(sp);
        }
    }
}
