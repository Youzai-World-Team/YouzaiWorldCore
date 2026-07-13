package top.csituka.youzaiworldcore.mixin.skill;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.projectile.Projectile;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.csituka.youzaiworldcore.skill.AdventureLevelManager;

/**
 * 投掷弹射物 → +2 冒险经验
 */
@Mixin(Projectile.class)
public class ProjectileExpMixin {

    @Inject(method = "shoot", at = @At("TAIL"))
    private void onShoot(double x, double y, double z, float power, float uncertainty, CallbackInfo ci) {
        Projectile self = (Projectile) (Object) this;
        if (!self.level().isClientSide() && self.getOwner() instanceof ServerPlayer player) {
            AdventureLevelManager.grantExp(player, AdventureLevelManager.EXP_SHOOT_PROJECTILE);
        }
    }
}
