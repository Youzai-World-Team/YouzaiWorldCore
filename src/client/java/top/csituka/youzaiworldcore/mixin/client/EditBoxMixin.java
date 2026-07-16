package top.csituka.youzaiworldcore.mixin.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.util.Mth;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 完全替换原版输入框渲染，使其与 {@code TransparentButton} 视觉一致。
 * 自行绘制背景 + 文字 + 光标，避免原版精灵/textY 依赖问题。
 */
@Mixin(EditBox.class)
public class EditBoxMixin {

    private static final int CORNER_RADIUS = 6;
    private static final float NORMAL_ALPHA = 0.50f;
    private static final float FOCUSED_ALPHA = 0.69f;
    private static final float DISABLED_ALPHA = 0.25f;
    private static final float LERP_SPEED = 0.15f;
    private static final int TEXT_COLOR = 0x404040;
    private static final int TEXT_COLOR_DISABLED = 0x808080;
    private static final int CURSOR_COLOR = 0xFF000000;
    private static final int PADDING = 4;

    @Unique private float yzwc$bgAlpha = NORMAL_ALPHA;

    @Shadow private String value;
    @Shadow private net.minecraft.client.gui.Font font;
    @Shadow private boolean isEditable;
    @Shadow private int cursorPos;
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

        // 保留原版工作方块输入框的样式（容器/告示牌/聊天/创造搜索等）
        net.minecraft.client.gui.screens.Screen screen = Minecraft.getInstance().gui.screen();
        if (screen instanceof ChatScreen
                || screen instanceof CreativeModeInventoryScreen
                || screen instanceof AbstractContainerScreen) {
            return;
        }

        float target = !self.isActive() ? DISABLED_ALPHA
                : self.isFocused() ? FOCUSED_ALPHA : NORMAL_ALPHA;
        yzwc$bgAlpha = yzwc$lerp(yzwc$bgAlpha, target);

        int x = self.getX(), y = self.getY(), w = self.getWidth(), h = self.getHeight();
        int bg = yzwc$color(yzwc$bgAlpha * self.getAlpha());
        yzwc$fillRoundedRect(gfx, x, y, w, h, CORNER_RADIUS, bg);

        int fg = self.isActive() ? TEXT_COLOR : TEXT_COLOR_DISABLED;
        int a = (int) (self.getAlpha() * 255);
        int textColor = (a << 24) | (fg & 0x00FFFFFF);

        String text = this.value != null ? this.value : "";
        int textY = y + (h - 8) / 2;

        // ---- 文字 ----
        int maxW = w - PADDING * 2;
        String clipped = this.font.plainSubstrByWidth(
                text.length() > this.displayPos ? text.substring(this.displayPos) : "", maxW);
        int textX = x + PADDING;

        if (!clipped.isEmpty()) {
            gfx.text(this.font, clipped, textX, textY, textColor, false);
        }

        // ---- 占位提示 ----
        if (clipped.isEmpty() && this.hint != null && !self.isFocused()) {
            int hintColor = (a << 24) | 0x808080;
            String hintStr = this.hint.getString();
            String hintClipped = this.font.plainSubstrByWidth(hintStr, maxW);
            gfx.text(this.font, hintClipped, textX, textY, hintColor, false);
        }

        // ---- 光标 ----
        if (self.isFocused() && cursorVisible()) {
            int relCursor = Mth.clamp(this.cursorPos - this.displayPos, 0, clipped.length());
            int cursorX = textX + (relCursor > 0 ? this.font.width(clipped.substring(0, relCursor)) : 0);
            gfx.fill(cursorX, textY - 1, cursorX + 1, textY + 8, CURSOR_COLOR);
        }

        // ---- 补全建议 ----
        if (this.suggestion != null && !this.suggestion.isEmpty() && !text.isEmpty()) {
            int sugX = textX + this.font.width(clipped);
            int sugColor = (a << 24) | 0x808080;
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

    @Unique private static float yzwc$lerp(float c, float t) {
        if (Math.abs(c - t) < 0.001f) return t;
        return c + (t - c) * LERP_SPEED;
    }

    @Unique private static int yzwc$color(float a) {
        return ((int) (Mth.clamp(a, 0, 1) * 255) << 24) | 0x00FFFFFF;
    }

    @Unique
    private static void yzwc$fillRoundedRect(GuiGraphicsExtractor g, int x, int y, int w, int h, int r, int c) {
        r = Math.min(r, Math.min(w / 2, h / 2));
        if (r <= 0) { g.fill(x, y, x + w, y + h, c); return; }
        g.fill(x + r, y, x + w - r, y + h, c);
        g.fill(x, y + r, x + r, y + h - r, c);
        g.fill(x + w - r, y + r, x + w, y + h - r, c);
        for (int i = 0; i < r; i++)
            for (int j = 0; j < r; j++) {
                int dx = r - 1 - i, dy = r - 1 - j;
                if (dx * dx + dy * dy < r * r) {
                    g.fill(x + i, y + j, x + i + 1, y + j + 1, c);
                    g.fill(x + w - 1 - i, y + j, x + w - i, y + j + 1, c);
                    g.fill(x + i, y + h - 1 - j, x + i + 1, y + h - j, c);
                    g.fill(x + w - 1 - i, y + h - 1 - j, x + w - i, y + h - j, c);
                }
            }
    }
}
