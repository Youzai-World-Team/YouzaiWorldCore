package top.csituka.youzaiworldcore.mixin.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTextTooltip;
import net.minecraft.util.FormattedCharSequence;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import top.csituka.youzaiworldcore.client.render.YzuiTheme;

/** 提示正文随主题调整；保留稀有度、格式、翻译及 Unicode 字符。 */
@Mixin(ClientTextTooltip.class)
public class ClientTextTooltipYzuiMixin {
    @Redirect(method = "extractText", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;text(Lnet/minecraft/client/gui/Font;Lnet/minecraft/util/FormattedCharSequence;IIIZ)V"))
    private void youzaiworldcore$text(GuiGraphicsExtractor g, Font font, FormattedCharSequence text,
            int x, int y, int color, boolean shadow) {
        if (!YzuiTheme.enabled()) {
            g.text(font, text, x, y, color, shadow);
            return;
        }
        FormattedCharSequence themed = sink -> text.accept((index, style, codepoint) ->
                sink.accept(index, style.getColor() == null ? style
                        : style.withColor(YzuiTheme.tooltipText(style.getColor().getValue())), codepoint));
        g.text(font, themed, x, y, YzuiTheme.text(), false);
    }
}
