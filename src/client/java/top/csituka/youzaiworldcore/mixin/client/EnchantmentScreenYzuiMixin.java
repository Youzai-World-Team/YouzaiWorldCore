package top.csituka.youzaiworldcore.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.EnchantmentScreen;
import net.minecraft.network.chat.FormattedText;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import top.csituka.youzaiworldcore.client.render.YzuiTheme;
import top.csituka.youzaiworldcore.client.screen.YzuiWorkstationScreen;

/** 将原版附魔文案和等级颜色映射到主题，保留可用、悬停和材料不足状态。 */
@Mixin(EnchantmentScreen.class)
public abstract class EnchantmentScreenYzuiMixin {
    @WrapOperation(method = "extractBackground", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;textWithWordWrap(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/FormattedText;IIIIZ)I"))
    private int youzaiworldcore$themeEnchantmentName(GuiGraphicsExtractor g, Font font, FormattedText text,
            int x, int y, int width, int color, boolean shadow, Operation<Integer> original) {
        if ((Object) this instanceof YzuiWorkstationScreen) {
            color = color == -128 ? YzuiTheme.primary()
                    : color == -9937334 ? YzuiTheme.text() : YzuiTheme.textMuted();
        }
        return original.call(g, font, text, x, y, width, color, shadow);
    }

    @WrapOperation(method = "extractBackground", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;text(Lnet/minecraft/client/gui/Font;Ljava/lang/String;III)V"))
    private void youzaiworldcore$themeEnchantmentCost(GuiGraphicsExtractor g, Font font, String text,
            int x, int y, int color, Operation<Void> original) {
        if ((Object) this instanceof YzuiWorkstationScreen) {
            color = color == -8323296 ? YzuiTheme.success() : YzuiTheme.textMuted();
            g.text(font, text, x, y, color, false);
        } else {
            original.call(g, font, text, x, y, color);
        }
    }
}
