package top.csituka.youzaiworldcore.item.tool;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ToolMaterial;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import org.jspecify.annotations.NonNull;

import java.util.Random;
import java.util.function.Consumer;

public class YzSwordItem extends Item {

    private static final float CRITICAL_CHANCE = 0.04F;
    private static final Random RANDOM = new Random();

    private final float attackDamage;

    public YzSwordItem(ToolMaterial material, float attackDamageBaseline, float attackSpeedBaseline, Properties settings) {
        super(settings.sword(material, attackDamageBaseline, attackSpeedBaseline));
        this.attackDamage = attackDamageBaseline + material.attackDamageBonus();
    }

    @Override
    public void appendHoverText(@NonNull ItemStack stack, @NonNull TooltipContext context, @NonNull TooltipDisplay display, Consumer<Component> tooltip, @NonNull TooltipFlag flag) {
        tooltip.accept(Component.translatable("item.youzaiworldcore.yz_sword.tooltip")
                .withStyle(ChatFormatting.GRAY));
        super.appendHoverText(stack, context, display, tooltip, flag);
    }

    @Override
    public void postHurtEnemy(@NonNull ItemStack stack, LivingEntity target, @NonNull LivingEntity attacker) {
        if (!target.level().isClientSide() && attacker.level() instanceof ServerLevel serverLevel) {
            if (RANDOM.nextFloat() < CRITICAL_CHANCE) {
                DamageSource damageSource = attacker.damageSources().mobAttack(attacker);
                target.hurtServer(serverLevel, damageSource, attackDamage + 1.0F);
            }
        }
        super.postHurtEnemy(stack, target, attacker);
    }
}
