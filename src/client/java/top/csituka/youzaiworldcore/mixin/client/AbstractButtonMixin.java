package top.csituka.youzaiworldcore.mixin.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 替换原版标准按钮 {@link Button.Plain} 的渲染样式，
 * 使其与项目自定义 {@code TransparentButton} 视觉一致。
 *
 * <h3>注入策略</h3>
 * 直接 mixin {@link Button.Plain#extractContents} 方法，
 * 替换全部背景 + 文字渲染逻辑。此方案仅影响 {@code Button.builder().build()} 创建的
 * 标准按钮（暂停菜单、选项界面等），不影响 {@code SpriteIconButton} 等图标按钮。
 *
 * <h3>视觉效果（与 TransparentButton 一致）</h3>
 * <ul>
 *   <li>正常状态：白底 50% 不透明度 + 黑色居中文字</li>
 *   <li>悬停/聚焦状态：白底 69% 不透明度 + 黑色文字（平滑 lerp 过渡）</li>
 *   <li>禁用状态：白底 25% 不透明度 + 灰色文字</li>
 *   <li>圆角半径：6px</li>
 * </ul>
 */
@Mixin(Button.Plain.class)
public class AbstractButtonMixin {

    private static final int BACKGROUND_COLOR = 0xFFFFFF;
    private static final int CORNER_RADIUS = 6;
    private static final float NORMAL_ALPHA = 0.5f;
    private static final float HOVER_ALPHA = 0.69f;
    private static final float DISABLED_ALPHA = 0.25f;
    private static final float LERP_SPEED = 0.15f;

    private static final int TEXT_COLOR = 0xFF000000;
    private static final int TEXT_COLOR_DISABLED = 0xFF666666;

    @Unique
    private float youzaiworldcore$currentHoverAlpha = NORMAL_ALPHA;

    /**
     * 替换 {@code Button.Plain.extractContents} 的全部渲染逻辑。
     *
     * <p>原版调用链：
     * <pre>{@code
     *   extractContents:
     *     extractDefaultSprite(guiGraphics)        // → 精灵背景
     *     collector = textRendererForWidget(...)     // → ActiveTextCollector
     *     extractDefaultLabel(collector)             // → 浅色文字
     * }</pre>
     *
     * <p>替换后：白底圆角矩形背景 + 黑色居中文字，风格与 {@code TransparentButton} 一致。</p>
     */
    @Inject(method = "extractContents(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIF)V",
            at = @At("HEAD"),
            cancellable = true)
    private void youzaiworldcore$replaceContents(
            GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTick,
            CallbackInfo ci
    ) {
        // 转换为目标类型以消除 IDE 误报（Mixin 运行时 this 即 Button.Plain 实例）
        Button.Plain self = (Button.Plain) (Object) this;

        // ============ 1. 背景：白底圆角矩形 ============

        // 确定目标不透明度
        float targetAlpha;
        if (!self.active) {
            targetAlpha = DISABLED_ALPHA;
        } else if (self.isHoveredOrFocused()) {
            targetAlpha = HOVER_ALPHA;
        } else {
            targetAlpha = NORMAL_ALPHA;
        }

        // 平滑过渡（与 TransparentButton 一致的 lerp 逻辑）
        if (Math.abs(youzaiworldcore$currentHoverAlpha - targetAlpha) < 0.001f) {
            youzaiworldcore$currentHoverAlpha = targetAlpha;
        } else {
            youzaiworldcore$currentHoverAlpha +=
                    (targetAlpha - youzaiworldcore$currentHoverAlpha) * LERP_SPEED;
        }

        // 叠加 AbstractWidget.alpha（用于上层淡入淡出动效）
        // 使用 getAlpha() 而非 .alpha：后者为 protected 字段，跨包不可见
        float finalAlpha = youzaiworldcore$currentHoverAlpha * self.getAlpha();
        int backgroundColor = youzaiworldcore$colorWithAlpha(BACKGROUND_COLOR, finalAlpha);

        youzaiworldcore$fillRoundedRect(
                guiGraphics,
                self.getX(), self.getY(),
                self.getWidth(), self.getHeight(),
                CORNER_RADIUS,
                backgroundColor
        );

        // ============ 2. 文字：黑色居中 ============

        var font = Minecraft.getInstance().font;
        var message = self.getMessage();
        int textWidth = font.width(message);
        int textX = self.getX() + (self.getWidth() - textWidth) / 2;
        int textY = self.getY() + (self.getHeight() - 8) / 2;

        int finalColor = self.active ? TEXT_COLOR : TEXT_COLOR_DISABLED;
        int a = (int) (self.getAlpha() * 255);
        int textColor = (a << 24) | (finalColor & 0x00FFFFFF);

        guiGraphics.text(font, message, textX, textY, textColor, false);

        ci.cancel();
    }

    // ==================== 辅助方法 ====================

    /**
     * 绘制圆角矩形（与 {@code TransparentButton.fillRoundedRect} 完全一致）。
     */
    @Unique
    private static void youzaiworldcore$fillRoundedRect(
            GuiGraphicsExtractor g, int x, int y, int w, int h, int r, int color
    ) {
        // Body center
        g.fill(x + r, y, x + w - r, y + h, color);
        // Left/right edge strips
        g.fill(x, y + r, x + r, y + h - r, color);
        g.fill(x + w - r, y + r, x + w, y + h - r, color);

        // Fill quarter-circle interior pixels
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

    /**
     * 将 RGB 颜色值与 alpha 通道组合为 ARGB 整数。
     */
    @Unique
    private static int youzaiworldcore$colorWithAlpha(int color, float alpha) {
        int a = (int) (Math.max(0, Math.min(255, alpha * 255)));
        return (a << 24) | (color & 0x00FFFFFF);
    }
}
