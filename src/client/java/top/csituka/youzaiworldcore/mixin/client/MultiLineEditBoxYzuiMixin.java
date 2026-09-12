package top.csituka.youzaiworldcore.mixin.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractTextAreaWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.csituka.youzaiworldcore.client.render.YzuiTheme;
import top.csituka.youzaiworldcore.client.animation.YzuiHover;

/** 多行输入区域使用圆角描边与悬停状态层，保留原版编辑行为。 */
@Mixin(AbstractTextAreaWidget.class)
public class MultiLineEditBoxYzuiMixin {
    @Unique private final YzuiHover youzaiworldcore$hover = new YzuiHover();
    @Inject(method = "extractWidgetRenderState", at = @At("HEAD"))
    private void youzaiworldcore$drawField(GuiGraphicsExtractor g, int x, int y, float tick, CallbackInfo ci) {
        AbstractTextAreaWidget self = (AbstractTextAreaWidget) (Object) this;
        if (YzuiTheme.enabled() && self.visible) {
            YzuiTheme.field(g, self.getX(), self.getY(), self.getWidth(), self.getHeight(),
                    youzaiworldcore$hover.sample(self.isActive() && self.isMouseOver(x, y)),
                    self.isFocused(), self.isActive(), self.getAlpha());
        }
    }

    @Inject(method = "extractBackground", at = @At("HEAD"), cancellable = true)
    private void youzaiworldcore$skipTexture(GuiGraphicsExtractor g, CallbackInfo ci) {
        if (YzuiTheme.enabled()) ci.cancel();
    }
}
