package top.csituka.youzaiworldcore.item.tool;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ToolMaterial;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import org.jspecify.annotations.NonNull;

import java.util.function.Consumer;

@SuppressWarnings("null")
public class YzShovelItem extends Item {

    public YzShovelItem(ToolMaterial material, float attackDamageBaseline, float attackSpeedBaseline, Properties settings) {
        super(settings.shovel(material, attackDamageBaseline, attackSpeedBaseline));
    }

    @SuppressWarnings("deprecation")
    @Override
    public void appendHoverText(@NonNull ItemStack stack, @NonNull TooltipContext context, @NonNull TooltipDisplay display, Consumer<Component> tooltip, @NonNull TooltipFlag flag) {
        tooltip.accept(Component.translatable("item.youzaiworldcore.yz_shovel.tooltip")
                .withStyle(ChatFormatting.GRAY));
        super.appendHoverText(stack, context, display, tooltip, flag);
    }
}
