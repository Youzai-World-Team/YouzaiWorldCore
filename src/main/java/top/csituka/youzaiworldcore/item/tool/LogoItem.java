package top.csituka.youzaiworldcore.item.tool;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

import java.util.function.Consumer;

public class LogoItem extends Item {

    public LogoItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip, TooltipFlag flag) {
        tooltip.accept(Component.translatable("item.youzaiworldcore.logo.tooltip")
                .withStyle(ChatFormatting.GRAY));
        super.appendHoverText(stack, context, display, tooltip, flag);
    }
}
