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
import top.csituka.youzaiworldcore.client.config.ClientExternalSettings;
import top.csituka.youzaiworldcore.client.render.RoundedRect;

/**
 * 替换原版滑动条样式，使其与项目自定义 {@code TransparentButton} 视觉一致。
 *
 * <h3>设计原则</h3>
 * 整个按钮区域即为轨道，无需独立容器背景。透明度和颜色源自 {@code TransparentButton}。
 *
 * <h3>组件构成</h3>
 * <ol>
 *   <li><b>轨道</b>：占满整个组件区域，左为已填充部分，右为未填充部分</li>
 *   <li><b>滑块 (Thumb)</b>：8px 宽的通高圆角矩形 (3px)，随 value 移动</li>
 *   <li><b>文字</b>：黑色居中，与按钮文字一致</li>
 * </ol>
 *
 * <h3>交互状态</h3>
 * <ul>
 *   <li><b>正常</b>：填充 45%, 未填充 20%, 滑块 85%</li>
 *   <li><b>悬停/聚焦</b>：填充 55%, 未填充 25%, 滑块 100% (平滑 lerp)</li>
 *   <li><b>禁用</b>：填充 22%, 未填充 12%, 滑块 35%, 文字 #666</li>
 * </ul>
 */
@Mixin(AbstractSliderButton.class)
public class AbstractSliderButtonMixin {

    // ==================== 样式常量 ====================

    private static final int COLOR_WHITE = 0xFFFFFF;

    // 轨道
    private static final int TRACK_CORNER = 6;
    private static final float TRACK_FILLED_NORMAL = 0.45f;
    private static final float TRACK_FILLED_HOVER = 0.55f;
    private static final float TRACK_FILLED_DISABLED = 0.22f;
    private static final float TRACK_UNFILLED_NORMAL = 0.20f;
    private static final float TRACK_UNFILLED_HOVER = 0.25f;
    private static final float TRACK_UNFILLED_DISABLED = 0.12f;

    // 滑块 — 明显高于轨道以建立清晰层次
    private static final int HANDLE_WIDTH = 8;
    private static final int HANDLE_CORNER = 4;
    private static final int HANDLE_OVERHANG = 2;
    private static final float HANDLE_NORMAL = 1.00f;
    private static final float HANDLE_HOVER = 1.00f;
    private static final float HANDLE_DISABLED = 0.35f;

    // 文字
    private static final int TEXT_COLOR = 0xFF000000;
    private static final int TEXT_DISABLED = 0xFF666666;

    // 动画
    private static final float LERP_SPEED = 0.15f;

    // ==================== Shadow 字段 ====================

    @Shadow
    protected double value;

    @Shadow
    protected boolean canChangeValue;

    // ==================== 动画状态 ====================

    @Unique
    private float yzwc$currentHandleAlpha = HANDLE_NORMAL;

    // ==================== 注入：替换 extractWidgetRenderState ====================

    @Inject(method = "extractWidgetRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIF)V",
            at = @At("HEAD"),
            cancellable = true)
    private void yzwc$replaceSliderRender(
            GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTick,
            CallbackInfo ci
    ) {
        // YZUI 禁用时回退到原版渲染，让资源包可以替换 UI（模组自定义屏幕除外）
        if (!yzwc$shouldApplyYzui()) {
            return;
        }
        AbstractSliderButton self = (AbstractSliderButton) (Object) this;

        boolean hovered = self.isHoveredOrFocused() || canChangeValue;
        boolean disabled = !self.active;

        // ---- 1. 确定目标透明度 ----
        float targetFilled, targetUnfilled, targetHandle;
        if (disabled) {
            targetFilled = TRACK_FILLED_DISABLED;
            targetUnfilled = TRACK_UNFILLED_DISABLED;
            targetHandle = HANDLE_DISABLED;
        } else if (hovered) {
            targetFilled = TRACK_FILLED_HOVER;
            targetUnfilled = TRACK_UNFILLED_HOVER;
            targetHandle = HANDLE_HOVER;
        } else {
            targetFilled = TRACK_FILLED_NORMAL;
            targetUnfilled = TRACK_UNFILLED_NORMAL;
            targetHandle = HANDLE_NORMAL;
        }

        // ---- 2. 平滑 lerp 动画 ----
        yzwc$currentHandleAlpha = yzwc$lerp(yzwc$currentHandleAlpha, targetHandle);

        int x = self.getX();
        int y = self.getY();
        int w = self.getWidth();
        int h = self.getHeight();

        // ---- 3. 轨道（占满整个组件区域） ----
        double handleCenterX = x + value * (w - HANDLE_WIDTH) + HANDLE_WIDTH / 2.0;
        int splitX = (int) handleCenterX;

        if (w > 0) {
            // 未填充部分（全宽圆角矩形）
            int unfilledColor = yzwc$color(targetUnfilled * self.getAlpha());
            yzwc$fillRoundedRect(guiGraphics, x, y, w, h, TRACK_CORNER, unfilledColor);

            // 已填充部分覆盖左侧（圆角矩形，右侧被滑块覆盖无影响）
            if (splitX > x) {
                int fillEnd = Math.min(splitX, x + w);
                int fillW = fillEnd - x;
                int filledColor = yzwc$color(targetFilled * self.getAlpha());
                if (fillW > 0) {
                    yzwc$fillRoundedRect(guiGraphics, x, y, fillW, h, TRACK_CORNER, filledColor);
                }
            }
        }

        // ---- 4. 滑块 (Thumb)：圆角矩形，上下微微突出轨道 ----
        int handleX = x + (int) (value * (w - HANDLE_WIDTH));
        int handleY = y - HANDLE_OVERHANG;
        int handleH = h + HANDLE_OVERHANG * 2;
        int handleColor = yzwc$color(yzwc$currentHandleAlpha * self.getAlpha());
        yzwc$fillRoundedRect(guiGraphics, handleX, handleY, HANDLE_WIDTH, handleH, HANDLE_CORNER, handleColor);

        // ---- 5. 文字 ----
        var font = Minecraft.getInstance().font;
        var message = self.getMessage();
        int textWidth = font.width(message);
        int textX = x + (w - textWidth) / 2;
        int textY = y + (h - 8) / 2;

        int fgColor = disabled ? TEXT_DISABLED : TEXT_COLOR;
        int a = (int) (self.getAlpha() * 255);
        int textColor = (a << 24) | (fgColor & 0x00FFFFFF);

        guiGraphics.text(font, message, textX, textY, textColor, false);

        // ---- 6. 光标处理 ----
        if (self.isHovered()) {
            guiGraphics.requestCursor(
                    self.isActive()
                            ? com.mojang.blaze3d.platform.cursor.CursorTypes.POINTING_HAND
                            : com.mojang.blaze3d.platform.cursor.CursorTypes.NOT_ALLOWED
            );
        }

        ci.cancel();
    }

    // ==================== 辅助方法 ====================

    @Unique
    private static float yzwc$lerp(float current, float target) {
        if (Math.abs(current - target) < 0.001f) {
            return target;
        }
        return current + (target - current) * LERP_SPEED;
    }

    @Unique
    private static int yzwc$color(float alpha) {
        int a = (int) (Math.max(0, Math.min(255, alpha * 255)));
        return (a << 24) | (COLOR_WHITE & 0x00FFFFFF);
    }

    /**
     * 绘制圆角矩形（与 {@code TransparentButton.fillRoundedRect} 完全一致）。
     */
    @Unique
    private static void yzwc$fillRoundedRect(
            GuiGraphicsExtractor g, int x, int y, int w, int h, int r, int color
    ) {
        // 圆角绘制统一走 RoundedRect（行扫描：r=6 时 135 次 fill -> 13 次）。
        // 点亮像素与原逐像素实现一致（45253 组尺寸/半径已逐一比对）；
        // 原实现未做尺寸校验，r > min(w,h)/2 时会画出坐标反转/重叠的结果，此处会钳制半径。
        RoundedRect.fill(g, x, y, w, h, r, color);
    }

    /**
     * 判断当前是否应应用 YZUI 自定义 UI 渲染。
     * <p>当用户关闭了 YZUI 全局开关时，原版屏幕回退到原版渲染以允许资源包替换；
     * 但模组自定义屏幕（包名以 {@code top.csituka.youzaiworldcore} 开头）始终使用 YZUI。</p>
     */
    @Unique
    private static boolean yzwc$shouldApplyYzui() {
        if (ClientExternalSettings.isYzuiEnabled()) return true;
        var screen = Minecraft.getInstance().gui.screen();
        return screen != null && screen.getClass().getName().startsWith("top.csituka.youzaiworldcore");
    }
}
