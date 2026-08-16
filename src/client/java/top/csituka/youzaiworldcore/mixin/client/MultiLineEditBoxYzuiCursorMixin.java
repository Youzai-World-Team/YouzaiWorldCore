package top.csituka.youzaiworldcore.mixin.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.components.TextCursorUtils;
import net.minecraft.network.chat.FormattedText;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.csituka.youzaiworldcore.client.config.ClientExternalSettings;
import top.csituka.youzaiworldcore.client.screen.YzuAnvilScreen;

/**
 * 将 {@link MultiLineEditBox} 的光标与占位符样式替换为 YZUI 风格。
 * <p>
 * 包含四处修改：
 * </p>
 * <ol>
 * <li><b>光标高度</b>：通过 {@code @ModifyArg} 将
 * {@code TextCursorUtils.extractInsertCursor}
 * 的高度参数从 {@code font.lineHeight + 1}（约 11px）改为 8（对应 9px），与 YZUI EditBoxMixin
 * 一致。</li>
 * <li><b>末尾光标形状</b>：通过 {@code @Redirect} 把
 * {@code TextCursorUtils.extractAppendCursor}
 * 绘制的下划线字符 {@code "_"} 换成 1px 竖线——光标位于文本末尾时原版走的是这条分支。</li>
 * <li><b>光标闪烁</b>：通过 {@code @Redirect} 将
 * {@code TextCursorUtils.isCursorVisible}
 * 的闪烁周期从 300ms 改为 500ms，与 YZUI EditBoxMixin 一致。</li>
 * <li><b>占位符</b>：改为深灰 {@code 0xFF666666} 并去掉文字阴影。原版
 * {@code PLACEHOLDER_TEXT_COLOR} 为
 * {@code ARGB.color(204, 0xFFE0E0E0)}（浅灰，为黑底设计），
 * 在 YZUI 半透明白背景上几乎不可见；阴影则因原版调用固定 {@code shadow = true} 的重载而无法关闭。</li>
 * <li><b>字符计数标签</b>：铁砧屏（{@link YzuAnvilScreen}）隐藏
 * {@code gui.multiLineEditBox.character_limit} 计数——其绘制位置与结果槽重叠，
 * 且原版铁砧（{@code EditBox}）本无此标签（见 {@code yzwc$hideCharLimitOnAnvil}）。</li>
 * </ol>
 * <p>
 * 仅对包名以 {@code top.csituka.youzaiworldcore} 开头的屏幕生效。
 * </p>
 *
 */
@Mixin(MultiLineEditBox.class)
@SuppressWarnings("null")
public class MultiLineEditBoxYzuiCursorMixin {

    /**
     * YZUI 光标高度：{@code extractInsertCursor} 内部
     * {@code g.fill(x, y-1, x+1, y+lineHeight)}，
     * 实际填充像素数 = {@code lineHeight + 1}。设为 8 使实际高度为 9px，与 EditBoxMixin 一致。
     */
    @Unique
    private static final int YZUI_CURSOR_LINE_HEIGHT = 8;

    /** YZUI 占位符颜色：深灰，适配半透明白背景（与 EditBoxMixin 的 hint 配色一致）。 */
    @Unique
    private static final int YZUI_PLACEHOLDER_COLOR = 0xFF666666;

    @ModifyArg(method = "extractContents(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIF)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/components/TextCursorUtils;extractInsertCursor(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIII)V"), index = 4)
    private int yzwc$modifyCursorLineHeight(int originalLineHeight) {
        return yzwc$shouldApply() ? YZUI_CURSOR_LINE_HEIGHT : originalLineHeight;
    }

    /**
     * 将 {@code TextCursorUtils.isCursorVisible} 的闪烁周期从 300ms 改为 500ms，与 YZUI
     * EditBoxMixin 一致。
     * <p>
     * 原版实现：{@code (millis / 300L) % 2L == 0L}（600ms 周期：300ms 亮 + 300ms 灭）。
     * </p>
     * <p>
     * YZUI 实现：{@code (millis / 500L) % 2L == 0L}（1000ms 周期：500ms 亮 + 500ms 灭）。
     * </p>
     */
    @Redirect(method = "extractContents(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIF)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/components/TextCursorUtils;isCursorVisible(J)Z"))
    private boolean yzwc$redirectCursorBlink(long millis) {
        if (yzwc$shouldApply()) {
            return (millis / 500L) % 2L == 0L;
        }
        return (millis / 300L) % 2L == 0L;
    }

    /**
     * 末尾光标：原版画的是下划线字符 {@code "_"}，改为与插入光标一致的 1px 竖线。
     * <p>
     * 原版 {@code TextCursorUtils.extractAppendCursor} 实现为
     * {@code g.text(font, "_", x, y, color, shadow)}——当光标位于文本末尾（含空文本、
     * 右键点到行尾等情形）时走这条分支，于是显示成一个小方块状的下划线，与 YZUI 的竖线不符。
     * </p>
     * <p>
     * 字节码显示该调用与 {@code extractInsertCursor} 复用同一对局部变量（x、y），
     * 故此处直接沿用插入光标的绘制方式：{@code fill(x, y - 1, x + 1, y + 8)}，
     * 得到 1px 宽、9px 高的竖线，与 {@code EditBoxMixin} 完全一致。
     * </p>
     */
    @Redirect(method = "extractContents(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIF)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/components/TextCursorUtils;extractAppendCursor(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/gui/Font;IIIZ)V"))
    private void yzwc$redirectAppendCursor(GuiGraphicsExtractor graphics, Font font,
            int x, int y, int color, boolean shadow) {
        if (yzwc$shouldApply()) {
            graphics.fill(x, y - 1, x + 1, y + YZUI_CURSOR_LINE_HEIGHT, color);
            return;
        }
        TextCursorUtils.extractAppendCursor(graphics, font, x, y, color, shadow);
    }

    /**
     * 占位符文本：改为 YZUI 深灰并去掉文字阴影。
     * <p>
     * 原版调用的是 6 参重载 {@code textWithWordWrap(font, text, x, y, width, color)}，
     * 它内部固定以 {@code shadow = true} 委托给 7 参重载，故无法通过
     * {@code setTextShadow(false)} 关闭——那个开关只作用于正文本身。
     * 这里改调 7 参重载并显式传入 {@code false}。
     * </p>
     */
    @Redirect(method = "extractContents(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIF)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;textWithWordWrap(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/FormattedText;IIII)V"))
    private void yzwc$redirectPlaceholder(GuiGraphicsExtractor graphics, Font font, FormattedText text,
            int x, int y, int width, int color) {
        if (yzwc$shouldApply()) {
            graphics.textWithWordWrap(font, text, x, y, width, YZUI_PLACEHOLDER_COLOR, false);
            return;
        }
        graphics.textWithWordWrap(font, text, x, y, width, color);
    }

    /**
     * 铁砧屏隐藏字符计数标签（"当前 / 上限"）。
     * <p>
     * {@code MultiLineEditBox.extractDecorations} 在调用了 {@code setCharacterLimit} 后
     * 会绘制 {@code gui.multiLineEditBox.character_limit}（浅灰文本），位置在
     * {@code (getX()+width-文本宽, getY()+height+4)} —— 铁砧面板坐标约 (129..139, 50)，
     * 与结果槽 (134..150, 47..63) 重叠，属视觉缺陷。原版铁砧用 {@code EditBox}（无计数标签），
     * YZUI 亦不应显示；字符输入上限本身仍由 {@code setCharacterLimit} 保留。
     * </p>
     */
    @Inject(method = "extractDecorations(Lnet/minecraft/client/gui/GuiGraphicsExtractor;)V",
            at = @At("HEAD"), cancellable = true)
    private void yzwc$hideCharLimitOnAnvil(GuiGraphicsExtractor graphics, CallbackInfo ci) {
        if (yzwc$shouldApply() && Minecraft.getInstance().gui.screen() instanceof YzuAnvilScreen) {
            ci.cancel();
        }
    }

    /**
     * 判断当前是否应应用 YZUI 自定义 UI 渲染。
     * <p>
     * 当用户关闭了 YZUI 全局开关时，原版屏幕回退到原版渲染以允许资源包替换；
     * 但模组自定义屏幕（包名以 {@code top.csituka.youzaiworldcore} 开头）始终使用 YZUI。
     * </p>
     */
    @Unique
    private static boolean yzwc$shouldApply() {
        if (ClientExternalSettings.isYzuiEnabled())
            return true;
        var screen = Minecraft.getInstance().gui.screen();
        return screen != null && screen.getClass().getName().startsWith("top.csituka.youzaiworldcore");
    }
}