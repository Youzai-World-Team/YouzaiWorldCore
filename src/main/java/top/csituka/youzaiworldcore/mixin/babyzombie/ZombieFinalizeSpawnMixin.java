package top.csituka.youzaiworldcore.mixin.babyzombie;

import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.DifficultyInstance;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import top.csituka.youzaiworldcore.config.EventSettings;
import top.csituka.youzaiworldcore.util.DebugLogger;

/**
 * 幼年僵尸弱化 Mixin。
 * <p>
 * 在僵尸（及其子类：尸壳、溺尸、僵尸村民）生成完成时，
 * 若为幼年状态，将最大生命值从 20 降至 10。
 * </p>
 */
@SuppressWarnings("null")
@Mixin(Zombie.class)
public abstract class ZombieFinalizeSpawnMixin {

    @Inject(method = "finalizeSpawn", at = @At("RETURN"))
    private void weakenBabyZombie(ServerLevelAccessor level, DifficultyInstance difficulty,
            EntitySpawnReason spawnReason, SpawnGroupData groupData,
            CallbackInfoReturnable<SpawnGroupData> cir) {
        if (!EventSettings.isBabyZombieWeakEnabled()) return;

        Zombie self = (Zombie) (Object) this;

        if (!self.isBaby()) {
            return;
        }

        var maxHealthAttr = self.getAttribute(Attributes.MAX_HEALTH);
        if (maxHealthAttr != null && maxHealthAttr.getBaseValue() > 10.0) {
            double oldMax = maxHealthAttr.getBaseValue();
            maxHealthAttr.setBaseValue(10.0);
            self.setHealth(10.0f);
            DebugLogger.debug("BabyZombieWeak", "幼年僵尸弱化: %s 最大生命 %.1f -> 10",
                    self.getName().getString(), oldMax);
        }
    }
}
