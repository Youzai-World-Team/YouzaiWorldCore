package top.csituka.youzaiworldcore.mixin.skill;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.csituka.youzaiworldcore.skill.AdventureLevelManager;

/**
 * 生物死亡 → 冒险经验。
 * 普通生物 +2 / PVP +5 / BOSS +500
 */
@Mixin(LivingEntity.class)
public class EntityDeathExpMixin {

    @Inject(method = "die", at = @At("HEAD"))
    private void onEntityDie(DamageSource source, CallbackInfo ci) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (self.level().isClientSide()) return;

        // 获取击杀者
        if (self.getKillCredit() instanceof ServerPlayer killer) {
            if (self instanceof Player) {
                // PVP 击杀
                AdventureLevelManager.grantExp(killer, AdventureLevelManager.EXP_KILL_PVP);
            } else if (isBoss(self)) {
                AdventureLevelManager.grantExp(killer, AdventureLevelManager.EXP_KILL_BOSS);
            } else {
                AdventureLevelManager.grantExp(killer, AdventureLevelManager.EXP_KILL_NORMAL);
            }
        }
    }

    private static boolean isBoss(LivingEntity entity) {
        return entity instanceof EnderDragon || entity instanceof WitherBoss;
    }
}
