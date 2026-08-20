package top.csituka.youzaiworldcore.mixin.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.csituka.youzaiworldcore.client.config.ClientExternalSettings;
import top.csituka.youzaiworldcore.client.animation.GuiAnimationController;
import top.csituka.youzaiworldcore.client.render.RoundedRect;
import top.csituka.youzaiworldcore.client.screen.widget.YzuiStyleOverride;

/**
 * 替换原版按钮样式，使其与项目自定义 {@code TransparentButton} 视觉一致。
 *
 * <h3>Mixin 目标</h3>
 * {@link AbstractButton}，影响所有子类：
 * <ul>
 *   <li>{@code Button.Plain} — 标准按钮（暂停菜单、确认对话框等）</li>
 *   <li>{@code CycleButton} — 选项切换按钮（选项子页面中的各项设置）</li>
 *   <li>{@code SpriteIconButton.CenteredIcon} — 纯图标按钮（仅背景受影响）</li>
 *   <li>{@code SpriteIconButton.TextAndIcon} — 文字+图标按钮（仅背景受影响，
 *       文字由子类自行渲染，不做修改）</li>
 * </ul>
 *
 * <h3>双注入策略</h3>
 * <ol>
 *   <li><b>注入点 1</b>：{@link AbstractButton#extractDefaultSprite}
 *       — 替换精灵背景为白底圆角矩形，同时缓存 {@code GuiGraphicsExtractor} 引用</li>
 *   <li><b>注入点 2</b>：{@link AbstractButton#extractDefaultLabel}
 *       — 使用注入点 1 缓存的引用，将文字替换为黑色居中文字</li>
 * </ol>
 *
 * <h3>视觉效果（与 TransparentButton 一致）</h3>
 * <ul>
 *   <li>正常状态：白底 50% 不透明度 + 黑色居中文字</li>
 *   <li>悬停/聚焦状态：白底 69% 不透明度 + 黑色文字（平滑 lerp 过渡）</li>
 *   <li>禁用状态：白底 25% 不透明度 + 灰色文字</li>
 *   <li>圆角半径：6px</li>
 * </ul>
 */
@Mixin(AbstractButton.class)
public class AbstractButtonMixin {

    // ==================== 样式常量 ====================

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

    // ==================== 注入点 1：背景精灵替换 ====================

    /**
     * 缓存的 {@code GuiGraphicsExtractor} 引用。
     * <p>在注入点 1 设置，在注入点 2 使用。满足以下前提：
     * <ul>
     *   <li>两个方法均在同一渲染帧、同一线程内顺序调用；</li>
     *   <li>{@code extractDefaultSprite} 总在 {@code extractDefaultLabel} 之前执行；</li>
     *   <li>Minecraft 渲染为单线程模型。</li>
     * </ul>
     * 若注入点 2 中此引用为 null（例如子类跳过了 {@code extractDefaultSprite}），
     * 则回退到原版逻辑，不做替换。</p>
     */
    @Unique
    private GuiGraphicsExtractor youzaiworldcore$cachedGuiGraphics;

    /**
     * 在 {@code extractDefaultSprite} 执行前注入，绘制自定义白底圆角矩形背景，
     * 缓存 {@code GuiGraphicsExtractor} 供后续文字渲染使用，并取消原版精灵渲染。
     */
    @Inject(method = "extractDefaultSprite", at = @At("HEAD"), cancellable = true)
    private void youzaiworldcore$replaceDefaultSprite(GuiGraphicsExtractor guiGraphics, CallbackInfo ci) {
        // YZUI 禁用时回退到原版渲染，让资源包可以替换 UI（模组自定义屏幕除外）
        if (!youzaiworldcore$shouldApplyYzui()) {
            return;
        }
        this.youzaiworldcore$cachedGuiGraphics = guiGraphics;
        AbstractButton self = this$youzaiworldcore$self();

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
        if (!GuiAnimationController.isEnabled()
                || Math.abs(youzaiworldcore$currentHoverAlpha - targetAlpha) < 0.001f) {
            youzaiworldcore$currentHoverAlpha = targetAlpha;
        } else {
            youzaiworldcore$currentHoverAlpha +=
                    (targetAlpha - youzaiworldcore$currentHoverAlpha) * LERP_SPEED;
        }

        // 叠加 AbstractWidget.alpha（用于上层淡入淡出动效）
        float finalAlpha = youzaiworldcore$currentHoverAlpha * self.getAlpha();
        int backgroundColor = youzaiworldcore$colorWithAlpha(BACKGROUND_COLOR, finalAlpha);

        youzaiworldcore$fillRoundedRect(
                guiGraphics,
                self.getX(), self.getY(),
                self.getWidth(), self.getHeight(),
                CORNER_RADIUS,
                backgroundColor
        );

        ci.cancel();
    }

    // ==================== 注入点 2：文字渲染替换 ====================

    /**
     * 在 {@code extractDefaultLabel} 执行前注入，使用注入点 1 缓存的
     * {@code GuiGraphicsExtractor} 绘制黑色居中文字。
     *
     * <p>若缓存为 null（调用方未经过 {@code extractDefaultSprite}），
     * 则不做替换，回退到原版 {@code ActiveTextCollector} 文字渲染。
     * 这在 {@code CycleButton} 使用自定义精灵时正确——自定义精灵需搭配原版浅色文字。</p>
     */
    @Inject(method = "extractDefaultLabel", at = @At("HEAD"), cancellable = true)
    private void youzaiworldcore$replaceDefaultLabel(
            net.minecraft.client.gui.ActiveTextCollector collector, CallbackInfo ci
    ) {
        GuiGraphicsExtractor guiGraphics = this.youzaiworldcore$cachedGuiGraphics;
        if (!youzaiworldcore$shouldApplyYzui() || guiGraphics == null) {
            return; // 回退到原版
        }

        AbstractButton self = this$youzaiworldcore$self();
        var font = Minecraft.getInstance().font;
        var message = self.getMessage();

        // 居中绘制
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
     * 将 {@code this} 转换为目标类型 {@link AbstractButton}，消除 IDE 误报。
     * <p>Mixin 运行时 this 即 AbstractButton 子类实例，此转换是安全的。</p>
     */
    @Unique
    private AbstractButton this$youzaiworldcore$self() {
        return (AbstractButton) (Object) this;
    }

    /**
     * 绘制圆角矩形（与 {@code TransparentButton.fillRoundedRect} 完全一致）。
     */
    @Unique
    private static void youzaiworldcore$fillRoundedRect(
            GuiGraphicsExtractor g, int x, int y, int w, int h, int r, int color
    ) {
        // 圆角绘制统一走 RoundedRect（行扫描：r=6 时 135 次 fill -> 13 次）。
        // 点亮像素与原逐像素实现一致（45253 组尺寸/半径已逐一比对）；
        // 原实现未做尺寸校验，r > min(w,h)/2 时会画出坐标反转/重叠的结果，此处会钳制半径。
        RoundedRect.fill(g, x, y, w, h, r, color);
    }

    /**
     * 将 RGB 颜色值与 alpha 通道组合为 ARGB 整数。
     */
    @Unique
    private static int youzaiworldcore$colorWithAlpha(int color, float alpha) {
        int a = (int) (Math.max(0, Math.min(255, alpha * 255)));
        return (a << 24) | (color & 0x00FFFFFF);
    }

    /**
     * 判断当前是否应应用 YZUI 自定义 UI 渲染。
     * <p>当用户关闭了 YZUI 全局开关时，原版屏幕（Vanilla）回退到原版渲染以允许资源包替换；
     * 但模组自定义屏幕（包名以 {@code top.csituka.youzaiworldcore} 开头）始终使用 YZUI，
     * 因为这些屏幕的视觉设计以 YZUI 白底样式为前提。</p>
     */
    @Unique
    private boolean youzaiworldcore$shouldApplyYzui() {
        int styleOverride = YzuiStyleOverride.get(this$youzaiworldcore$self());
        if (styleOverride == YzuiStyleOverride.STYLE_VANILLA) return false;
        if (styleOverride == YzuiStyleOverride.STYLE_YZUI) return true;
        if (ClientExternalSettings.isYzuiEnabled()) return true;
        var screen = Minecraft.getInstance().gui.screen();
        return screen != null && screen.getClass().getName().startsWith("top.csituka.youzaiworldcore");
    }
}
