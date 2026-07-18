package top.csituka.youzaiworldcore.mixin.skill;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import top.csituka.youzaiworldcore.skill.AttributeManager;

/**
 * 混合注入：修改 {@link LivingEntity#heal(float)} 的治疗量，
 * 应用玩家属性加点中的「生命恢复幅度」加成。
 */
@Mixin(LivingEntity.class)
public class LivingEntityHealMixin {

    @ModifyVariable(method = "heal", at = @At("HEAD"), argsOnly = true)
    private float amplifyHeal(float healAmount) {
        if (healAmount <= 0f) return healAmount;
        //noinspection ConstantValue
        if (!((Object) this instanceof ServerPlayer player)) return healAmount;
        float multiplier = AttributeManager.getHealingMultiplier(player.getUUID());
        return healAmount * multiplier;
    }
}
