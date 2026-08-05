package top.csituka.youzaiworldcore.mixin.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractTextAreaWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.csituka.youzaiworldcore.client.config.ClientExternalSettings;

/**
 * 将 MultiLineEditBox（父类 {@link AbstractTextAreaWidget}）的背景替换为 YZUI 风格。
 * <p>在 {@code extractWidgetRenderState} 头部强制关闭原版背景（{@code widget/text_field} 精灵），
 * 并绘制 YZUI 半透明白色圆角矩形。仅对包名以 {@code top.csituka.youzaiworldcore} 开头的屏幕生效。</p>
 *
 * <h3>修复说明</h3>
 * <p>原版 {@code AbstractTextAreaWidget.extractWidgetRenderState} 会在 {@code showBackground} 为真时
 * 经 {@code extractBackground → extractBorder} 绘制 {@code widget/text_field} 九宫格精灵（黑底 + 1px 灰边）。
 * 本实现在 {@code extractWidgetRenderState} 头部直接置 {@code showBackground = false}，
 * 从源头确保原版精灵不会被绘制；{@code extractBackground} 的取消注入作为第二道保险。</p>
 * <p>另有一处曾表现为「超大灰色圆角边框、边框内偏白」的问题，根因不在原版精灵，而在
 * {@code yzwc$fillRoundedRect} 自身：旧写法用「整宽横条 + 竖条」两块矩形拼圆角，二者在中央重叠，
 * 半透明白被混合两次（0.50 → 约 0.75），于是中心偏白、四周仅混合一次而透出深色面板显灰，
 * 形成一圈宽度恰为 {@code CORNER_RADIUS} 的假边框。现改为与 {@code EditBoxMixin} 一致的
 * 「中间列 + 左右侧条」三块互不重叠写法，整块背景透明度均匀。</p>
 *
 * <h3>光标说明</h3>
 * <p>光标样式（高度 9px + 闪烁周期 500ms）由 {@code MultiLineEditBoxYzuiCursorMixin} 处理。</p>
 */
@Mixin(AbstractTextAreaWidget.class)
@SuppressWarnings("null")
public class MultiLineEditBoxYzuiMixin {

    @Unique private static final int CORNER_RADIUS = 6;
    @Unique private static final float NORMAL_BG_ALPHA = 0.50f;
    @Unique private static final float FOCUSED_BG_ALPHA = 0.69f;

    @Mutable
    @Shadow private boolean showBackground;

    /**
     * 在 {@code extractWidgetRenderState} 头部：强制关闭原版背景，绘制 YZUI 半透明白色圆角矩形。
     * <p>无论原版 {@code showBackground} 是 true 还是 false，{@code widget/text_field} 精灵都不会被绘制。</p>
     */
    @Inject(method = "extractWidgetRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIF)V",
            at = @At("HEAD"))
    private void yzwc$drawYzuiBackground(GuiGraphicsExtractor graphics, int mx, int my, float pt, CallbackInfo ci) {
        if (!yzwc$shouldApply()) {
            return;
        }
        // 从源头关闭原版背景，防止 widget/text_field 精灵边框（顶部/底部灰色渐变）被绘制
        this.showBackground = false;

        AbstractTextAreaWidget self = (AbstractTextAreaWidget) (Object) this;
        float targetAlpha = self.isFocused() ? FOCUSED_BG_ALPHA : NORMAL_BG_ALPHA;
        int alpha = (int) (targetAlpha * 255f);
        int color = (alpha << 24) | 0x00FFFFFF;

        yzwc$fillRoundedRect(graphics, self.getX(), self.getY(), self.getWidth(), self.getHeight(),
                CORNER_RADIUS, color);
    }

    /**
     * 保留此注入作为安全网：即使 {@code showBackground} 已被设为 false，
     * 若有其他路径调用 {@code extractBackground}，此处仍能阻止原版精灵绘制。
     */
    @Inject(method = "extractBackground(Lnet/minecraft/client/gui/GuiGraphicsExtractor;)V",
            at = @At("HEAD"), cancellable = true)
    private void yzwc$cancelVanillaBackground(GuiGraphicsExtractor graphics, CallbackInfo ci) {
        if (!yzwc$shouldApply()) {
            return;
        }
        ci.cancel();
    }

    /**
     * 判断当前是否应应用 YZUI 自定义 UI 渲染。
     * <p>当用户关闭了 YZUI 全局开关时，原版屏幕回退到原版渲染以允许资源包替换；
     * 但模组自定义屏幕（包名以 {@code top.csituka.youzaiworldcore} 开头）始终使用 YZUI。</p>
     */
    @Unique
    private static boolean yzwc$shouldApply() {
        if (ClientExternalSettings.isYzuiEnabled()) return true;
        var screen = Minecraft.getInstance().gui.screen();
        return screen != null && screen.getClass().getName().startsWith("top.csituka.youzaiworldcore");
    }

    @Unique
    private static void yzwc$fillRoundedRect(
            GuiGraphicsExtractor graphics, int x, int y, int w, int h, int r, int color
    ) {
        if (w <= 0 || h <= 0) return;
        int radius = Math.max(0, Math.min(r, Math.min(w, h) / 2));
        if (radius <= 0) {
            graphics.fill(x, y, x + w, y + h, color);
            return;
        }
        // 三块矩形互不重叠：中间列 + 左右侧条。
        // 若改用「整宽横条 + 竖条」两块写法，中央会被半透明色混合两次，
        // 呈现「中心偏白、四周偏灰」的假边框（宽度恰为 radius）。
        graphics.fill(x + radius, y, x + w - radius, y + h, color);
        graphics.fill(x, y + radius, x + radius, y + h - radius, color);
        graphics.fill(x + w - radius, y + radius, x + w, y + h - radius, color);
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