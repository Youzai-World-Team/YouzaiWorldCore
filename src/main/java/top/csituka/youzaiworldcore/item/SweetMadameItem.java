package top.csituka.youzaiworldcore.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;

import org.jspecify.annotations.NonNull;

import top.csituka.youzaiworldcore.util.DebugLogger;

import java.util.function.Consumer;

/**
 * 甜甜花酿鸡——特殊食物物品。
 *
 * <p>
 * 基础营养效果等同熟鸡肉（nutrition=6, saturation=0.6），
 * 额外附加 24% 最大生命值恢复，通过 {@link #finishUsingItem} 覆盖实现。
 * </p>
 *
 * <p>
 * Hover 文本通过 {@link #appendHoverText} 注入自定义描述行。
 * </p>
 */
@SuppressWarnings("null")
public class SweetMadameItem extends Item {

    /** HP 恢复百分比（24% = 0.24） */
    private static final float HEAL_PERCENT = 0.24f;

    public SweetMadameItem(Properties settings) {
        super(settings);
        DebugLogger.entering("SweetMadameItem", "constructor",
                "healPercent=%.0f%%".formatted(HEAL_PERCENT * 100));
    }

    /**
     * 食用完成：恢复 24% 最大生命值（服务端执行）。
     */
    @Override
    @NonNull
    public ItemStack finishUsingItem(@NonNull ItemStack stack, @NonNull Level level,
                                     @NonNull LivingEntity entity) {
        ItemStack result = super.finishUsingItem(stack, level, entity);

        if (!level.isClientSide() && entity instanceof Player player) {
            float healAmount = player.getMaxHealth() * HEAL_PERCENT;
            player.heal(healAmount);
            DebugLogger.info("SweetMadameItem",
                    "玩家 %s 食用甜甜花酿鸡，恢复生命值 %.1f (24%% of %.1f)".formatted(
                            player.getName().getString(),
                            healAmount,
                            player.getMaxHealth()));
        }

        return result;
    }

    /**
     * Hover 描述——自定义物品说明。
     */
    @SuppressWarnings("deprecation")
    @Override
    public void appendHoverText(@NonNull ItemStack stack, @NonNull TooltipContext context,
                                @NonNull TooltipDisplay display, Consumer<Component> tooltip,
                                @NonNull TooltipFlag flag) {
        tooltip.accept(Component.translatable("item.youzaiworldcore.sweet_madame.tooltip")
                .withStyle(ChatFormatting.GRAY));
        tooltip.accept(Component.translatable("item.youzaiworldcore.sweet_madame.effect")
                .withStyle(ChatFormatting.DARK_PURPLE));
        super.appendHoverText(stack, context, display, tooltip, flag);
    }
}
