package top.csituka.youzaiworldcore.mixin.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 替换原版输入框样式，使其与项目自定义 {@code TransparentButton} 视觉一致。
 *
 * <h3>设计</h3>
 * 白底圆角矩形 (6px)，透明度与按钮同步 (50%→69% lerp)。
 * 与按钮、滑动条形成统一的白色半透明组件体系。
 */
@Mixin(EditBox.class)
public class EditBoxMixin {

    private static final int CORNER_RADIUS = 6;
    private static final float NORMAL_ALPHA = 0.50f;
    private static final float FOCUSED_ALPHA = 0.69f;
    private static final float DISABLED_ALPHA = 0.25f;
    private static final float LERP_SPEED = 0.15f;

    @Unique
    private float yzwc$currentAlpha = NORMAL_ALPHA;

    @Shadow
    private boolean bordered;

    @Inject(method = "extractWidgetRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIF)V",
            at = @At("HEAD"))
    private void yzwc$drawCustomBackground(
            GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTick,
            CallbackInfo ci
    ) {
        EditBox self = (EditBox) (Object) this;
        if (!self.isVisible()) return;

        float target;
        if (!self.isActive()) {
            target = DISABLED_ALPHA;
        } else if (self.isFocused()) {
            target = FOCUSED_ALPHA;
        } else {
            target = NORMAL_ALPHA;
        }

        yzwc$currentAlpha = yzwc$lerp(yzwc$currentAlpha, target);

        int x = self.getX();
        int y = self.getY();
        int w = self.getWidth();
        int h = self.getHeight();

        int bgColor = yzwc$color(yzwc$currentAlpha * self.getAlpha());
        yzwc$fillRoundedRect(guiGraphics, x, y, w, h, CORNER_RADIUS, bgColor);

        this.bordered = false;
    }

    @Unique
    private static float yzwc$lerp(float current, float target) {
        if (Math.abs(current - target) < 0.001f) return target;
        return current + (target - current) * LERP_SPEED;
    }

    @Unique
    private static int yzwc$color(float alpha) {
        int a = (int) (Math.max(0, Math.min(255, alpha * 255)));
        return (a << 24) | 0x00FFFFFF;
    }

    @Unique
    private static void yzwc$fillRoundedRect(
            GuiGraphicsExtractor g, int x, int y, int w, int h, int r, int color
    ) {
        r = Math.min(r, Math.min(w / 2, h / 2));
        if (r <= 0) { g.fill(x, y, x + w, y + h, color); return; }

        g.fill(x + r, y, x + w - r, y + h, color);
        g.fill(x, y + r, x + r, y + h - r, color);
        g.fill(x + w - r, y + r, x + w, y + h - r, color);

        for (int i = 0; i < r; i++) {
            for (int j = 0; j < r; j++) {
                int dx = r - 1 - i;
                int dy = r - 1 - j;
                if (dx * dx + dy * dy < r * r) {
                    g.fill(x + i, y + j, x + i + 1, y + j + 1, color);
                    g.fill(x + w - 1 - i, y + j, x + w - i, y + j + 1, color);
                    g.fill(x + i, y + h - 1 - j, x + i + 1, y + h - j, color);
                    g.fill(x + w - 1 - i, y + h - 1 - j, x + w - i, y + h - j, color);
                }
            }
        }
    }
}
