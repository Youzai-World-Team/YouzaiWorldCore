package top.csituka.youzaiworldcore.item.tool;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ToolMaterial;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

import java.util.function.Consumer;

@SuppressWarnings("null")
public class YzPickaxeItem extends Item {

    public YzPickaxeItem(ToolMaterial material, float attackDamageBaseline, float attackSpeedBaseline, Properties settings) {
        super(settings.pickaxe(material, attackDamageBaseline, attackSpeedBaseline));
    }

    @SuppressWarnings("deprecation")
    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip, TooltipFlag flag) {
        tooltip.accept(Component.translatable("item.youzaiworldcore.yz_pickaxe.tooltip")
                .withStyle(ChatFormatting.GRAY));
        super.appendHoverText(stack, context, display, tooltip, flag);
    }
}
