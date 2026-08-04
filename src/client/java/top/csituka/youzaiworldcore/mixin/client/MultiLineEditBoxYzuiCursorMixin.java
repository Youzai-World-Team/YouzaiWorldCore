package top.csituka.youzaiworldcore.mixin.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.MultiLineEditBox;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;
import top.csituka.youzaiworldcore.client.config.ClientExternalSettings;

/**
 * 将 {@link MultiLineEditBox} 的光标样式替换为 YZUI 风格。
 * <p>包含两处修改：</p>
 * <ol>
 *   <li><b>光标高度</b>：通过 {@code @ModifyArg} 将 {@code TextCursorUtils.extractInsertCursor}
 *   的高度参数从 {@code font.lineHeight + 1}（约 11px）改为 8（对应 9px），与 YZUI EditBoxMixin 一致。</li>
 *   <li><b>光标闪烁</b>：通过 {@code @Redirect} 将 {@code TextCursorUtils.isCursorVisible}
 *   的闪烁周期从 300ms 改为 500ms，与 YZUI EditBoxMixin 一致。</li>
 * </ol>
 * <p>仅对包名以 {@code top.csituka.youzaiworldcore} 开头的屏幕生效。</p>
 *
 * <h3>修复说明</h3>
 * <p>原版 {@code MultiLineEditBox.extractContents} 调用
 * {@code TextCursorUtils.extractInsertCursor(graphics, x, y, color, font.lineHeight + 1)}
 * 绘制光标。对于 9px 字体，光标高度为 11px，且闪烁周期为 300ms。</p>
 * <p>YZUI EditBoxMixin 使用 9px 高度 + 500ms 闪烁周期（详见 {@code EditBoxMixin.cursorVisible}）。
 * 为保持视觉一致，本 Mixin 将这两个参数统一到 YZUI 标准。</p>
 *
 * <h3>为什么单独 Mixin</h3>
 * <p>{@code extractContents} 在 {@code AbstractTextAreaWidget} 中为抽象方法，
 * {@code @Redirect} 与 {@code @ModifyArg} 需要具体方法体才能注入。
 * 故本 Mixin 单独作用于 {@link MultiLineEditBox}，背景修复仍由
 * {@code MultiLineEditBoxYzuiMixin}（作用于父类）处理。</p>
 */
@Mixin(MultiLineEditBox.class)
@SuppressWarnings("null")
public class MultiLineEditBoxYzuiCursorMixin {

    /** YZUI 光标高度：{@code extractInsertCursor} 内部 {@code g.fill(x, y-1, x+1, y+lineHeight)}，
     *  实际填充像素数 = {@code lineHeight + 1}。设为 8 使实际高度为 9px，与 EditBoxMixin 一致。 */
    @Unique private static final int YZUI_CURSOR_LINE_HEIGHT = 8;

    /**
     * 将 {@code TextCursorUtils.extractInsertCursor} 的 {@code lineHeight} 参数从
     * {@code font.lineHeight + 1}（约 11px）改为 8（实际 9px），与 YZUI EditBoxMixin 一致。
     * <p>参数索引 3 对应方法签名的第 4 个参数 {@code int lineHeight}。</p>
     */
    @ModifyArg(method = "extractContents(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIF)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/components/TextCursorUtils;extractInsertCursor(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIII)V"),
            index = 3)
    private int yzwc$modifyCursorLineHeight(int originalLineHeight) {
        return yzwc$shouldApply() ? YZUI_CURSOR_LINE_HEIGHT : originalLineHeight;
    }

    /**
     * 将 {@code TextCursorUtils.isCursorVisible} 的闪烁周期从 300ms 改为 500ms，与 YZUI EditBoxMixin 一致。
     * <p>原版实现：{@code (millis / 300L) % 2L == 0L}（600ms 周期：300ms 亮 + 300ms 灭）。</p>
     * <p>YZUI 实现：{@code (millis / 500L) % 2L == 0L}（1000ms 周期：500ms 亮 + 500ms 灭）。</p>
     */
    @Redirect(method = "extractContents(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIF)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/components/TextCursorUtils;isCursorVisible(J)Z"))
    private boolean yzwc$redirectCursorBlink(long millis) {
        if (yzwc$shouldApply()) {
            return (millis / 500L) % 2L == 0L;
        }
        return (millis / 300L) % 2L == 0L;
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
}