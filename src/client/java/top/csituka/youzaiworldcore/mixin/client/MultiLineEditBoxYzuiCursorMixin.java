package top.csituka.youzaiworldcore.mixin.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.components.TextCursorUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.csituka.youzaiworldcore.client.render.YzuiTheme;
import top.csituka.youzaiworldcore.client.screen.YzuAnvilScreen;

/** 动态主题文字、占位符、选区与竖线光标；保留输入法和原版编辑逻辑。 */
@Mixin(MultiLineEditBox.class)
public class MultiLineEditBoxYzuiCursorMixin {
    @Shadow @Final private int textColor;
    @Shadow @Final private int cursorColor;
    @Shadow @Final private boolean textShadow;

    @Redirect(method = "extractContents", at = @At(value = "FIELD",
            target = "Lnet/minecraft/client/gui/components/MultiLineEditBox;textColor:I", opcode = 180))
    private int youzaiworldcore$textColor(MultiLineEditBox self) {
        return YzuiTheme.enabled() ? YzuiTheme.text() : textColor;
    }

    @Redirect(method = "extractContents", at = @At(value = "FIELD",
            target = "Lnet/minecraft/client/gui/components/MultiLineEditBox;cursorColor:I", opcode = 180))
    private int youzaiworldcore$cursorColor(MultiLineEditBox self) {
        return YzuiTheme.enabled() ? YzuiTheme.primary() : cursorColor;
    }

    @Redirect(method = "extractContents", at = @At(value = "FIELD",
            target = "Lnet/minecraft/client/gui/components/MultiLineEditBox;textShadow:Z", opcode = 180))
    private boolean youzaiworldcore$textShadow(MultiLineEditBox self) {
        return !YzuiTheme.enabled() && textShadow;
    }

    @ModifyArg(method = "extractContents", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/components/TextCursorUtils;extractInsertCursor(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIII)V"), index = 4)
    private int youzaiworldcore$cursorHeight(int height) {
        return YzuiTheme.enabled() ? 8 : height;
    }

    @Redirect(method = "extractContents", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/components/TextCursorUtils;isCursorVisible(J)Z"))
    private boolean youzaiworldcore$cursorBlink(long millis) {
        return YzuiTheme.enabled() ? (millis / 500L) % 2L == 0L : TextCursorUtils.isCursorVisible(millis);
    }

    @Redirect(method = "extractContents", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/components/TextCursorUtils;extractAppendCursor(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/gui/Font;IIIZ)V"))
    private void youzaiworldcore$appendCursor(GuiGraphicsExtractor g, Font font, int x, int y, int color, boolean shadow) {
        if (YzuiTheme.enabled()) g.fill(x, y - 1, x + 1, y + 8, color);
        else TextCursorUtils.extractAppendCursor(g, font, x, y, color, shadow);
    }

    @Redirect(method = "extractContents", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;textWithWordWrap(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/FormattedText;IIII)V"))
    private void youzaiworldcore$placeholder(GuiGraphicsExtractor g, Font font, FormattedText text,
            int x, int y, int width, int color) {
        if (YzuiTheme.enabled()) g.textWithWordWrap(font, text, x, y, width, YzuiTheme.textMuted(), false);
        else g.textWithWordWrap(font, text, x, y, width, color);
    }

    @Redirect(method = "extractContents", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;textHighlight(IIIIZ)V"))
    private void youzaiworldcore$selection(GuiGraphicsExtractor g, int left, int top, int right, int bottom, boolean invert) {
        if (YzuiTheme.enabled()) g.fill(left, top, right, bottom, YzuiTheme.selection());
        else g.textHighlight(left, top, right, bottom, invert);
    }

    @Inject(method = "extractDecorations", at = @At("HEAD"), cancellable = true)
    private void youzaiworldcore$hideAnvilCounter(GuiGraphicsExtractor g, CallbackInfo ci) {
        if (YzuiTheme.enabled() && Minecraft.getInstance().gui.screen() instanceof YzuAnvilScreen) ci.cancel();
    }

    @Redirect(method = "extractDecorations", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;text(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;III)V"))
    private void youzaiworldcore$counter(GuiGraphicsExtractor g, Font font, Component text, int x, int y, int color) {
        if (YzuiTheme.enabled()) g.text(font, text, x, y, YzuiTheme.textMuted(), false);
        else g.text(font, text, x, y, color);
    }
}
