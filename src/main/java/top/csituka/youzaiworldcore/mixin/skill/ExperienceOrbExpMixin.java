package top.csituka.youzaiworldcore.mixin.skill;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.csituka.youzaiworldcore.skill.AdventureLevelManager;

/**
 * 拾取原版经验球 → +1 冒险经验
 */
@Mixin(ExperienceOrb.class)
public class ExperienceOrbExpMixin {

    @Inject(method = "playerTouch", at = @At("HEAD"))
    private void onPlayerTouch(Player player, CallbackInfo ci) {
        if (player instanceof ServerPlayer sp) {
            AdventureLevelManager.grantExp(sp, AdventureLevelManager.EXP_PICKUP_XP_ORB);
        }
    }
}
