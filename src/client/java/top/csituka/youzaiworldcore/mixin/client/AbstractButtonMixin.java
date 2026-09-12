package top.csituka.youzaiworldcore.mixin.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ActiveTextCollector;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.csituka.youzaiworldcore.client.config.ClientExternalSettings;
import top.csituka.youzaiworldcore.client.animation.YzuiHover;
import top.csituka.youzaiworldcore.client.render.YzuiTheme;
import top.csituka.youzaiworldcore.client.screen.widget.YzuiStyleOverride;

/** 原版按钮共用 MD3 外观，保留原屏幕布局、输入处理和 YZUI 回退开关。 */
@Mixin(AbstractButton.class)
public class AbstractButtonMixin {
    @Unique private GuiGraphicsExtractor yzwc$buttonGraphics;
    @Unique private final YzuiHover youzaiworldcore$hover = new YzuiHover();

    @Inject(method = "extractDefaultSprite", at = @At("HEAD"), cancellable = true)
    private void yzwc$drawButton(GuiGraphicsExtractor g, CallbackInfo ci) {
        yzwc$buttonGraphics = null;
        if (!yzwc$applyTheme()) return;
        AbstractButton button = (AbstractButton) (Object) this;
        yzwc$buttonGraphics = g;
        YzuiTheme.button(g, button.getX(), button.getY(), button.getWidth(), button.getHeight(),
                youzaiworldcore$hover.sample(button.active && button.isHoveredOrFocused()), button.isFocused(), button.active,
                button.getAlpha(), YzuiTheme.ButtonStyle.TONAL);
        ci.cancel();
    }

    @Inject(method = "extractDefaultLabel", at = @At("HEAD"), cancellable = true)
    private void yzwc$drawLabel(ActiveTextCollector collector, CallbackInfo ci) {
        if (!yzwc$applyTheme() || yzwc$buttonGraphics == null) return;
        AbstractButton button = (AbstractButton) (Object) this;
        var font = Minecraft.getInstance().font;
        int padding = Math.min(6, button.getWidth() / 4);
        YzuiTheme.label(yzwc$buttonGraphics, font, button.getMessage(), button.getX() + padding,
                button.getY() + (button.getHeight() - font.lineHeight) / 2,
                button.getWidth() - padding * 2,
                YzuiTheme.alpha(YzuiTheme.buttonText(YzuiTheme.ButtonStyle.TONAL, button.active),
                        button.getAlpha() * (button.active ? 1f : 0.68f)), true);
        ci.cancel();
    }

    @Unique
    private boolean yzwc$applyTheme() {
        int override = YzuiStyleOverride.get((AbstractButton) (Object) this);
        if (override == YzuiStyleOverride.STYLE_VANILLA) return false;
        return override == YzuiStyleOverride.STYLE_YZUI || ClientExternalSettings.isYzuiEnabled()
                || YzuiTheme.isCustomScreen(Minecraft.getInstance().gui.screen());
    }
}
