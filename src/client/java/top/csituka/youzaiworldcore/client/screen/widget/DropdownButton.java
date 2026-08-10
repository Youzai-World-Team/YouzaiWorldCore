package top.csituka.youzaiworldcore.client.screen.widget;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.function.IntConsumer;
import top.csituka.youzaiworldcore.client.render.RoundedRect;

@SuppressWarnings("null")
public class DropdownButton extends AbstractWidget {

    private static final int TEXT_COLOR = 0x00FFFFFF;
    private static final int ARROW_COLOR = 0x00AAAAAA;

    // ===== 下拉弹窗样式（浅色主题） =====
    /** 弹窗边框色 */
    private static final int POPUP_BORDER = 0xFFB0B0B0;
    /** 弹窗投影偏移 (px) */
    private static final int SHADOW_OFFSET = 3;
    /** 投影颜色：半透明黑 */
    private static final int POPUP_SHADOW = 0x40000000;
    /** 选项悬停高亮 */
    private static final int HOVER_BG = 0x60D0D0D0;
    /** 选项文字色（深色，配合浅色背景） */
    private static final int OPTION_TEXT_COLOR = 0xFF333333;
    /** 被选中项文字色 */
    private static final int OPTION_SELECTED_COLOR = 0xFF111111;
    /** 弹窗圆角半径 */
    private static final int CORNER_RADIUS = 6;

    private final List<String> options;
    private int selectedIndex;
    private final int closedHeight;
    private final int popupWidth;
    private boolean open;
    private float externalAlpha = 1f;
    private final IntConsumer onSelectionChanged;
    private final Runnable onToggleOpen;
    private int hoveredOption = -1;

    // ===== 弹窗淡入淡出动画 =====
    /** 当前动画进度 0.0 ~ 1.0 */
    private float popupAnimAlpha = 0f;
    /** 动画 lerp 速度（×2.5 加速） */
    private static final float POPUP_ANIM_SPEED = 0.3f;

    /**
     * @param x           按钮左上角 X
     * @param y           按钮左上角 Y
     * @param width       按钮宽度
     * @param popupWidth  下拉弹窗宽度（可与按钮不同宽）
     * @param height      单行高度
     */
    public DropdownButton(int x, int y, int width, int popupWidth, int height, Component message,
                          List<String> options, int selectedIndex, boolean open,
                          IntConsumer onSelectionChanged, Runnable onToggleOpen) {
        super(x, y, width, open ? height + options.size() * height : height, message);
        this.options = options;
        this.selectedIndex = selectedIndex;
        this.closedHeight = height;
        this.popupWidth = popupWidth;
        this.open = open;
        this.onSelectionChanged = onSelectionChanged;
        this.onToggleOpen = onToggleOpen;
    }

    public int getSelectedIndex() {
        return selectedIndex;
    }

    /** 下拉弹窗是否打开 */
    public boolean isOpen() {
        return open;
    }

    /** 关闭下拉弹窗 */
    public void closePopup() {
        if (open) {
            open = false;
            this.height = closedHeight;
        }
    }

    /**
     * 判断坐标是否在弹窗区域（按钮 + 下拉选项）内。
     * 用于 Screen 检测外部点击收回弹窗。
     */
    public boolean isPositionInsidePopup(double mouseX, double mouseY) {
        if (!open) return false;
        int bx = this.getX();
        int by = this.getY();
        int totalH = closedHeight + options.size() * closedHeight;
        return mouseX >= bx && mouseX < bx + this.width
                && mouseY >= by && mouseY < by + totalH;
    }

    public void setExternalAlpha(float alpha) {
        this.externalAlpha = alpha;
    }

    // ========== 按钮渲染（始终调用） ==========

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTick) {
        int alpha = (int) (externalAlpha * 255);
        int textColor = (alpha << 24) | TEXT_COLOR;
        int arrowColor = (alpha << 24) | ARROW_COLOR;

        var font = Minecraft.getInstance().font;
        int x = this.getX();
        int y = this.getY();
        int w = this.width;
        int h = closedHeight;

        int textY = y + (h - 8) / 2;

        // 渲染按钮标签（左对齐）
        guiGraphics.text(font, this.getMessage(), x + 4, textY, textColor, false);

        // 渲染当前值 + 箭头（右对齐）
        String currentValue = selectedIndex >= 0 && selectedIndex < options.size() ? options.get(selectedIndex) : "";
        String arrow = open ? "▲" : "▼";
        String displayText = currentValue + " " + arrow;
        int displayWidth = font.width(displayText);
        guiGraphics.text(font, Component.literal(displayText), x + w - displayWidth - 4, textY, arrowColor, false);

        // 注意：弹窗渲染不在此处进行（由上层 Screen 在更高 stratum 上调用 renderPopup）
    }

    // ========== 弹窗渲染（由 Screen 在全部 widgets 渲染完毕后，在更高 stratum 调用） ==========

    /**
     * 在后置阶段渲染下拉弹窗，确保弹窗覆盖在所有页面元素之上。
     * 由 {@code YouzaiWorldCoreSettingsScreen} 在 {@code super.extractRenderState()} 之后调用。
     * 同一 stratum 后渲染即在上层，无需切换 stratum。
     */
    public void renderPopup(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTick) {
        // 更新淡入淡出动画
        popupAnimAlpha = lerp(popupAnimAlpha, open ? 1f : 0f, POPUP_ANIM_SPEED);
        // 淡出接近完成时直接归零，避免残留渲染
        if (!open && popupAnimAlpha < 0.1f) popupAnimAlpha = 0f;
        if (popupAnimAlpha < 0.005f || options.isEmpty()) return;

        var font = Minecraft.getInstance().font;
        int bx = this.getX();         // 按钮 X
        int y = this.getY();
        int bw = this.width;          // 按钮宽度
        int pw = this.popupWidth;     // 弹窗宽度
        int h = closedHeight;
        int dropdownY = y + h;
        int popupH = options.size() * h;
        int r = CORNER_RADIUS;

        // 弹窗靠右对齐于按钮下方（对齐倒三角箭头）
        int px = bx + bw - pw;

        // 插值：背景色从深色 (0x282828) 到白色 (0xE8E8E8)
        int interpR = lerpInt(0x28, 0xE8, popupAnimAlpha);
        int interpG = lerpInt(0x28, 0xE8, popupAnimAlpha);
        int interpB = lerpInt(0x28, 0xE8, popupAnimAlpha);
        int bgColor = 0xFF000000 | (interpR << 16) | (interpG << 8) | interpB;

        // 投影、边框同步淡入
        int shadowColor = lerpColor(0x00000000, POPUP_SHADOW, popupAnimAlpha);
        int borderColor = lerpColor(0x00B0B0B0, POPUP_BORDER, popupAnimAlpha);
        int hoverColor = lerpColor(0x00000000, HOVER_BG, popupAnimAlpha);
        int sepColor = lerpColor(0x00000000, 0x20A0A0A0, popupAnimAlpha);

        // 1. 投影
        fillRoundedRect(guiGraphics,
                px + SHADOW_OFFSET, dropdownY + SHADOW_OFFSET,
                pw, popupH, r, shadowColor);

        // 2. 弹窗背景
        fillRoundedRect(guiGraphics, px, dropdownY, pw, popupH, r, bgColor);

        // 3. 圆角边框
        fillRoundedRect(guiGraphics, px - 1, dropdownY - 1,
                pw + 2, popupH + 2, r + 1, borderColor);
        fillRoundedRect(guiGraphics, px, dropdownY, pw, popupH, r, bgColor);

        // 4. 计算悬停项
        hoveredOption = -1;
        if (mouseX >= px && mouseX < px + pw && mouseY >= dropdownY && mouseY < dropdownY + popupH) {
            hoveredOption = (mouseY - dropdownY) / h;
        }

        // 5. 逐项渲染
        int textAlpha = Math.round(0xFF * popupAnimAlpha);
        for (int i = 0; i < options.size(); i++) {
            int optY = dropdownY + i * h;
            int optTextY = optY + (h - 8) / 2;

            if (i == hoveredOption) {
                guiGraphics.fill(px + 1, optY, px + pw - 1, optY + h, hoverColor);
            }

            if (i > 0) {
                guiGraphics.fill(px + 8, optY, px + pw - 8, optY + 1, sepColor);
            }

            int optColor = (i == selectedIndex) ? OPTION_SELECTED_COLOR : OPTION_TEXT_COLOR;
            int fadedColor = (textAlpha << 24) | (optColor & 0x00FFFFFF);
            guiGraphics.text(font, Component.literal(options.get(i)), px + 10, optTextY, fadedColor, false);
        }
    }

    // ========== 动画工具 ==========

    private static float lerp(float a, float b, float t) {
        if (Math.abs(a - b) < 0.001f) return b;
        return a + (b - a) * t;
    }

    /** 在 [from, to] 之间按 t (0~1) 插值整数 */
    private static int lerpInt(int from, int to, float t) {
        return Math.round(from + (to - from) * t);
    }

    /** 在两种 ARGB 颜色之间插值（按 Alpha 混合） */
    private static int lerpColor(int from, int to, float t) {
        int a = lerpInt(from >>> 24, to >>> 24, t);
        int r = lerpInt((from >> 16) & 0xFF, (to >> 16) & 0xFF, t);
        int g = lerpInt((from >> 8) & 0xFF, (to >> 8) & 0xFF, t);
        int b = lerpInt(from & 0xFF, to & 0xFF, t);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    // ========== 圆角矩形填充工具 ==========

    // 圆角矩形绘制统一走 RoundedRect（行扫描），点亮像素与原逐像素实现一致。
    private static void fillRoundedRect(GuiGraphicsExtractor g, int x, int y, int w, int h, int r, int color) {
        RoundedRect.fillOrSquare(g, x, y, w, h, r, color);
    }

    // ========== 旧版兼容 ==========

    public void render(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.extractWidgetRenderState(guiGraphics, mouseX, mouseY, partialTick);
    }

    // ========== 交互 ==========

    @Override
    public void onClick(MouseButtonEvent event, boolean isActuallyClick) {
        if (!open) {
            open = true;
            this.height = closedHeight + options.size() * closedHeight;
            if (onToggleOpen != null) onToggleOpen.run();
            return;
        }

        if (hoveredOption >= 0 && hoveredOption < options.size()) {
            selectedIndex = hoveredOption;
            if (onSelectionChanged != null) onSelectionChanged.accept(selectedIndex);
        }

        open = false;
        this.height = closedHeight;
        if (onToggleOpen != null) onToggleOpen.run();
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput narrationElementOutput) {
        this.defaultButtonNarrationText(narrationElementOutput);
    }
}
