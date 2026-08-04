package top.csituka.youzaiworldcore.mixin.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractTextAreaWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 将 MultiLineEditBox 的装饰边框替换为 YZUI 风格圆角矩形，
 * 并将光标颜色调整为与 YZUI 输入框一致。
 * <p>仅对包名以 {@code top.csituka.youzaiworldcore} 开头的屏幕生效。</p>
 */
@Mixin(AbstractTextAreaWidget.class)
@SuppressWarnings("null")
public class MultiLineEditBoxYzuiMixin {

    @Unique private static final int CORNER_RADIUS = 6;
    @Unique private static final float NORMAL_BG_ALPHA = 0.50f;
    @Unique private static final float FOCUSED_BG_ALPHA = 0.69f;

    /**
     * 替换原版精灵边框为 YZUI 风格半透明白色圆角矩形。
     */
    @Inject(method = "extractBorder(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIII)V",
            at = @At("HEAD"), cancellable = true)
    private void yzwc$replaceBorder(
            GuiGraphicsExtractor graphics, int x, int y, int width, int height, CallbackInfo ci
    ) {
        if (!yzwc$shouldApply()) {
            return;
        }
        ci.cancel();

        AbstractTextAreaWidget self = (AbstractTextAreaWidget) (Object) this;
        float targetAlpha = self.isFocused() ? FOCUSED_BG_ALPHA : NORMAL_BG_ALPHA;
        int alpha = (int) (targetAlpha * 255f);
        int color = (alpha << 24) | 0x00FFFFFF;

        yzwc$fillRoundedRect(graphics, x, y, width, height, CORNER_RADIUS, color);
    }

    /**
     * 仅对悠哉模组自定义屏幕生效。
     */
    @Unique
    private static boolean yzwc$shouldApply() {
        net.minecraft.client.gui.screens.Screen screen = net.minecraft.client.Minecraft.getInstance().gui.screen();
        if (screen == null) {
            return false;
        }
        return screen.getClass().getName().startsWith("top.csituka.youzaiworldcore");
    }

    /** 绘制圆角矩形（逐像素填充）。 */
    @Unique
    private static void yzwc$fillRoundedRect(
            GuiGraphicsExtractor graphics, int x, int y, int w, int h, int r, int color
    ) {
        if (w <= 0 || h <= 0) return;
        int radius = Math.max(0, Math.min(r, Math.min(w, h) / 2));
        graphics.fill(x + radius, y, x + w - radius, y + h, color);
        graphics.fill(x, y + radius, x + w, y + h - radius, color);
        for (int ix = 0; ix < radius; ix++) {
            for (int iy = 0; iy < radius; iy++) {
                int dx = radius - 1 - ix;
                int dy = radius - 1 - iy;
                if (dx * dx + dy * dy < radius * radius) {
                    graphics.fill(x + ix, y + iy, x + ix + 1, y + iy + 1, color);
                    graphics.fill(x + w - 1 - ix, y + iy, x + w - ix, y + iy + 1, color);
                    graphics.fill(x + ix, y + h - 1 - iy, x + ix + 1, y + h - iy, color);
                    graphics.fill(x + w - 1 - ix, y + h - 1 - iy, x + w - ix, y + h - iy, color);
                }
            }
        }
    }
}
