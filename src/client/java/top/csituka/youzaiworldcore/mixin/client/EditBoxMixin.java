package top.csituka.youzaiworldcore.mixin.client;

import top.csituka.youzaiworldcore.client.render.YzuiTheme;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.util.Mth;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.csituka.youzaiworldcore.client.config.ClientExternalSettings;
import top.csituka.youzaiworldcore.client.animation.GuiAnimationController;
import top.csituka.youzaiworldcore.client.render.RoundedRect;
import top.csituka.youzaiworldcore.client.animation.YzuiHover;

/**
 * 完全替换原版输入框渲染，使其与 {@code TransparentButton} 视觉一致。
 * 自行绘制背景 + 文字 + 光标，避免原版精灵/textY 依赖问题。
 */
@Mixin(EditBox.class)
@SuppressWarnings("null")
public class EditBoxMixin {

    private static int textColor() { return YzuiTheme.text(); }
    private static int textColorDisabled() { return YzuiTheme.textMuted(); }
    private static int cursorColor() { return YzuiTheme.primary(); }
    /** 选中文本高亮色：半透明蓝（ARGB），适配 YZUI 白色背景（原版 0xFF0000FF 不透明蓝会完全盖住白底） */
    private static int highlightColor() { return YzuiTheme.selection(); }
    private static final int PADDING = 4;

    @Unique private final YzuiHover yzwc$hover = new YzuiHover();

    @Shadow private String value;
    @Shadow private net.minecraft.client.gui.Font font;
    @Shadow private boolean isEditable;
    @Shadow private int cursorPos;
    @Shadow private int highlightPos;
    @Shadow private int displayPos;
    @Shadow private long focusedTime;
    @Shadow private String suggestion;
    @Shadow private net.minecraft.network.chat.Component hint;

    @Inject(method = "extractWidgetRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIF)V",
            at = @At("HEAD"), cancellable = true)
    private void yzwc$replaceAll(
            GuiGraphicsExtractor gfx, int mx, int my, float pt, CallbackInfo ci
    ) {
        EditBox self = (EditBox) (Object) this;
        if (!self.isVisible()) return;

        // YZUI 禁用时回退到原版渲染，让资源包可以替换 UI（模组自定义屏幕除外）
        if (!yzwc$shouldApplyYzui()) {
            return;
        }

        // 保留原版工作方块输入框的样式（告示牌/聊天/创造搜索/铁砧等），
        // 但容许 YZUI 自定义物品栏屏幕中的搜索框（配方书搜索）应用 YZUI 样式
        net.minecraft.client.gui.screens.Screen screen = Minecraft.getInstance().gui.screen();
        if (screen instanceof ChatScreen
                || screen instanceof CreativeModeInventoryScreen
                || (screen instanceof AbstractContainerScreen
                    && !screen.getClass().getName().startsWith("top.csituka.youzaiworldcore"))) {
            return;
        }

        int x = self.getX(), y = self.getY(), w = self.getWidth(), h = self.getHeight();
        YzuiTheme.field(gfx, x, y, w, h, yzwc$hover.sample(self.isActive() && self.isMouseOver(mx, my)),
                self.isFocused(), self.isActive(), self.getAlpha());

        int fg = self.isActive() ? textColor() : textColorDisabled();
        int a = (int) (self.getAlpha() * 255);
        int textColor = (a << 24) | (fg & 0x00FFFFFF);

        String text = this.value != null ? this.value : "";
        int textY = y + (h - 8) / 2;

        // ---- 文字 ----
        int maxW = w - PADDING * 2;
        String clipped = this.font.plainSubstrByWidth(
                text.length() > this.displayPos ? text.substring(this.displayPos) : "", maxW);
        int textX = x + PADDING;

        // ---- 选中文本高亮（复刻原版 renderHighlight：选中范围 = cursorPos..highlightPos，
        //      裁剪到可视区；半透明蓝适配 YZUI 白底，绘制在文本下层） ----
        if (!text.isEmpty() && !clipped.isEmpty()) {
            int selStart = Math.min(this.cursorPos, this.highlightPos);
            int selEnd = Math.max(this.cursorPos, this.highlightPos);
            int visStart = Math.max(selStart, this.displayPos);
            int visEnd = Math.min(selEnd, this.displayPos + clipped.length());
            if (visStart < visEnd) {
                int hlX1 = textX + this.font.width(text.substring(this.displayPos, visStart));
                int hlX2 = textX + this.font.width(text.substring(this.displayPos, visEnd));
                int hx1 = Math.min(hlX1, textX + w);
                int hx2 = Math.min(hlX2, textX + w);
                gfx.fill(RenderPipelines.GUI_TEXT_HIGHLIGHT, hx1, textY - 1, hx2 - 1, textY + 9, highlightColor());
            }
        }

        if (!clipped.isEmpty()) {
            gfx.text(this.font, clipped, textX, textY, textColor, false);
        }

        // ---- 占位提示 ----
        if (clipped.isEmpty() && this.hint != null && !self.isFocused()) {
            int hintColor = (a << 24) | (YzuiTheme.textMuted() & 0xFFFFFF); // 深灰（避免与白底背景混色）
            String hintStr = this.hint.getString();
            String hintClipped = this.font.plainSubstrByWidth(hintStr, maxW);
            gfx.text(this.font, hintClipped, textX, textY, hintColor, false);
        }

        // ---- 光标 ----
        if (self.isFocused() && cursorVisible()) {
            int relCursor = Mth.clamp(this.cursorPos - this.displayPos, 0, clipped.length());
            int cursorX = textX + (relCursor > 0 ? this.font.width(clipped.substring(0, relCursor)) : 0);
            gfx.fill(cursorX, textY - 1, cursorX + 1, textY + 8, cursorColor());
        }

        // ---- 补全建议 ----
        if (this.suggestion != null && !this.suggestion.isEmpty() && !text.isEmpty()) {
            int sugX = textX + this.font.width(clipped);
            int sugColor = (a << 24) | (YzuiTheme.textMuted() & 0xFFFFFF);
            String sugClipped = this.font.plainSubstrByWidth(this.suggestion, maxW - this.font.width(clipped));
            if (!sugClipped.isEmpty()) {
                gfx.text(this.font, sugClipped, sugX, textY, sugColor, false);
            }
        }

        // ---- 光标样式 ----
        if (self.isHovered()) {
            gfx.requestCursor(self.isActive()
                    ? com.mojang.blaze3d.platform.cursor.CursorTypes.IBEAM
                    : com.mojang.blaze3d.platform.cursor.CursorTypes.NOT_ALLOWED);
        }

        ci.cancel();
    }

    @Unique
    private boolean cursorVisible() {
        return (net.minecraft.util.Util.getMillis() - this.focusedTime) / 500L % 2L == 0L;
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
