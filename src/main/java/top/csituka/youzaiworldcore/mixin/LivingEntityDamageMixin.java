package top.csituka.youzaiworldcore.mixin;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import top.csituka.youzaiworldcore.skill.AttributeManager;

import java.util.UUID;

/**
 * 混合注入：修改 {@link LivingEntity#hurtServer(net.minecraft.server.level.ServerLevel, DamageSource, float)} 的伤害值。
 * <ul>
 *   <li>伤害抗性：对所有来源伤害按比例减免</li>
 *   <li>远程伤害：当伤害来源为弹射物时，按比例增伤</li>
 * </ul>
 */
@Mixin(LivingEntity.class)
public class LivingEntityDamageMixin {

    @ModifyVariable(method = "hurtServer", at = @At("HEAD"), argsOnly = true, index = 3)
    private float modifyDamage(float amount, net.minecraft.server.level.ServerLevel level, DamageSource source, float _unused) {
        // 熔断器1：无效伤害量无需计算（如无敌/创造模式，amount 为 0）
        if (amount <= 0f) return amount;

        //noinspection ConstantValue
        if (!((Object) this instanceof ServerPlayer player)) return amount;
        UUID uuid = player.getUUID();

        // 熔断器2：玩家无任何属性数据时直接返回，避免不必要的存储查询
        if (!top.csituka.youzaiworldcore.skill.PlayerAttributeStorage.hasAttributes(uuid)) {
            return amount;
        }

        // 1. 远程伤害增幅：弹射物攻击增伤
        if (source.getDirectEntity() != null && source.getDirectEntity() != source.getEntity()) {
            // 检查是谁发射的弹射物
            if (source.getEntity() instanceof Player shooter && shooter.getUUID().equals(uuid)) {
                float multiplier = AttributeManager.getRangedDamageMultiplier(uuid);
                amount *= multiplier;
            }
        }

        // 2. 伤害抗性减免
        float reduction = AttributeManager.getDamageReduction(uuid);
        amount *= (1f - reduction);

        return Math.max(0f, amount);
    }
}
