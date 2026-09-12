package top.csituka.youzaiworldcore.mixin.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.csituka.youzaiworldcore.client.animation.YzuiHover;
import top.csituka.youzaiworldcore.client.render.RoundedRect;
import top.csituka.youzaiworldcore.client.render.YzuiTheme;
import top.csituka.youzaiworldcore.client.screen.widget.YzuiStyleOverride;

/** 半透明 MD3 滑块：共用悬停状态层，保留原版数值、拖动和键盘操作。 */
@Mixin(AbstractSliderButton.class)
public class AbstractSliderButtonMixin {
    @Shadow protected double value;
    @Shadow protected boolean canChangeValue;
    @Unique private final YzuiHover youzaiworldcore$hover = new YzuiHover();

    @Inject(method = "extractWidgetRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIF)V",
            at = @At("HEAD"), cancellable = true)
    private void youzaiworldcore$renderSlider(GuiGraphicsExtractor g, int mouseX, int mouseY,
            float partialTick, CallbackInfo ci) {
        AbstractSliderButton self = (AbstractSliderButton) (Object) this;
        int override = YzuiStyleOverride.get(self);
        if (override == YzuiStyleOverride.STYLE_VANILLA
                || override != YzuiStyleOverride.STYLE_YZUI && !YzuiTheme.enabled()) return;
        if (!self.visible) return;

        int x = self.getX(), y = self.getY(), w = self.getWidth(), h = self.getHeight();
        float opacity = self.getAlpha() * (self.active ? 1f : 0.55f);
        float hover = youzaiworldcore$hover.sample(self.active && self.isMouseOver(mouseX, mouseY));
        YzuiTheme.field(g, x, y, w, h, hover, self.isFocused() || canChangeValue, self.active, opacity);
        int handleX = x + 4 + (int) (Math.clamp(value, 0d, 1d) * Math.max(0, w - 8));
        int trackY = y + h - 5;
        RoundedRect.fill(g, x + 4, trackY, w - 8, 3, 1, YzuiTheme.alpha(YzuiTheme.outlineVariant(), opacity));
        RoundedRect.fill(g, x + 4, trackY, handleX - x - 4, 3, 1, YzuiTheme.alpha(YzuiTheme.primary(), opacity));
        if (hover > 0f && self.active) {
            RoundedRect.fill(g, handleX - 5, trackY - 4, 10, 10, 5,
                    YzuiTheme.alpha(YzuiTheme.primary(), opacity * hover * 0.12f));
        }
        RoundedRect.fill(g, handleX - 2, trackY - 2, 4, 7, 2, YzuiTheme.alpha(YzuiTheme.primary(), opacity));
        YzuiTheme.label(g, Minecraft.getInstance().font, self.getMessage(), x + 6, y + 2, w - 12,
                YzuiTheme.alpha(self.active ? YzuiTheme.text() : YzuiTheme.textMuted(), opacity), true);
        ci.cancel();
    }
}