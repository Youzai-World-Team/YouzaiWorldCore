package top.csituka.youzaiworldcore.item.tool;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ToolMaterial;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.NonNull;

import java.util.List;
import java.util.function.Consumer;

public class YzAxeItem extends Item {

    private static final float SWEEP_RADIUS = 3.0F;
    private static final float SWEEP_DAMAGE_RATIO = 0.5F;

    private final float attackDamage;

    public YzAxeItem(ToolMaterial material, float attackDamageBaseline, float attackSpeedBaseline, Properties settings) {
        super(settings.axe(material, attackDamageBaseline, attackSpeedBaseline));
        this.attackDamage = attackDamageBaseline + material.attackDamageBonus();
    }

    @Override
    public void appendHoverText(@NonNull ItemStack stack, @NonNull TooltipContext context, @NonNull TooltipDisplay display, Consumer<Component> tooltip, @NonNull TooltipFlag flag) {
        tooltip.accept(Component.translatable("item.youzaiworldcore.yz_axe.tooltip")
                .withStyle(ChatFormatting.GRAY));
        super.appendHoverText(stack, context, display, tooltip, flag);
    }

    @Override
    public void postHurtEnemy(@NonNull ItemStack stack, LivingEntity target, @NonNull LivingEntity attacker) {
        if (!target.level().isClientSide() && attacker.level() instanceof ServerLevel serverLevel) {
            if (attacker instanceof Player player && isCriticalAttack(player)) {
                performSweepAttack(serverLevel, player, target);
            }
        }
        super.postHurtEnemy(stack, target, attacker);
    }

    private boolean isCriticalAttack(Player player) {
        return !player.onGround() && player.getDeltaMovement().y < 0;
    }

    private void performSweepAttack(ServerLevel level, Player attacker, LivingEntity mainTarget) {
        AABB area = new AABB(
                mainTarget.getX() - SWEEP_RADIUS,
                mainTarget.getY() - 1.0,
                mainTarget.getZ() - SWEEP_RADIUS,
                mainTarget.getX() + SWEEP_RADIUS,
                mainTarget.getY() + 1.0,
                mainTarget.getZ() + SWEEP_RADIUS
        );

        List<LivingEntity> nearbyEntities = level.getNearbyEntities(
                LivingEntity.class,
                TargetingConditions.forCombat().range(SWEEP_RADIUS),
                attacker,
                area
        );

        DamageSource sweepDamageSource = attacker.damageSources().mobAttack(attacker);
        float sweepDamage = attackDamage * SWEEP_DAMAGE_RATIO;

        for (LivingEntity entity : nearbyEntities) {
            if (entity != mainTarget && entity != attacker) {
                entity.hurtServer(level, sweepDamageSource, sweepDamage);
            }
        }
    }
}
