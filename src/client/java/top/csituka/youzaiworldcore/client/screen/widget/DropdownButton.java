package top.csituka.youzaiworldcore.client.screen.widget;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.function.IntConsumer;

public class DropdownButton extends AbstractWidget {

    private static final int TEXT_COLOR = 0x00FFFFFF;
    private static final int ARROW_COLOR = 0x00AAAAAA;

    // ===== 下拉弹窗样式（浅色主题） =====
    /** 弹窗背景色：全不透明白色偏灰，在深色背景上呈现柔和感 */
    private static final int POPUP_BG = 0xFFE8E8E8;
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
     * 由 {@code YouzaiWorldCoreSettingsScreen} 在 {@code super.extractRenderState()} 之后、
     * 新的 stratum 上调用。
     */
    public void renderPopup(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTick) {
        if (!open || options.isEmpty()) return;

        int alpha = (int) (externalAlpha * 255);

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

        // 1. 投影（偏移 +3,+3）
        fillRoundedRect(guiGraphics,
                px + SHADOW_OFFSET, dropdownY + SHADOW_OFFSET,
                pw, popupH, r, POPUP_SHADOW);

        // 2. 弹窗背景（白色圆角矩形）
        fillRoundedRect(guiGraphics, px, dropdownY, pw, popupH, r, POPUP_BG);

        // 3. 圆角边框: 先绘制一个比背景大 1px 的圆角矩形（边框色），
        //    再在内部绘制一个缩小 1px 的圆角矩形（背景色）覆盖中间，只留下 1px 边框
        fillRoundedRect(guiGraphics, px - 1, dropdownY - 1,
                pw + 2, popupH + 2, r + 1, POPUP_BORDER);
        fillRoundedRect(guiGraphics, px, dropdownY, pw, popupH, r, POPUP_BG);

        // 4. 计算悬停项
        hoveredOption = -1;
        if (mouseX >= px && mouseX < px + pw && mouseY >= dropdownY && mouseY < dropdownY + popupH) {
            hoveredOption = (mouseY - dropdownY) / h;
        }

        // 5. 逐项渲染
        for (int i = 0; i < options.size(); i++) {
            int optY = dropdownY + i * h;
            int optTextY = optY + (h - 8) / 2;

            // 悬停高亮（圆角遮罩内绘制）
            if (i == hoveredOption) {
                guiGraphics.fill(px + 1, optY, px + pw - 1, optY + h, HOVER_BG);
            }

            // 分割线（项之间）
            if (i > 0) {
                guiGraphics.fill(px + 8, optY, px + pw - 8, optY + 1, 0x20A0A0A0);
            }

            // 选项文字（无阴影、无加粗）
            int optColor = (i == selectedIndex) ? OPTION_SELECTED_COLOR : OPTION_TEXT_COLOR;
            guiGraphics.text(font, Component.literal(options.get(i)), px + 10, optTextY, optColor, false);
        }
    }

    // ========== 圆角矩形填充工具 ==========

    private static void fillRoundedRect(GuiGraphicsExtractor g, int x, int y, int w, int h, int r, int color) {
        // 主体
        g.fill(x + r, y, x + w - r, y + h, color);
        // 左右边条
        g.fill(x, y + r, x + r, y + h - r, color);
        g.fill(x + w - r, y + r, x + w, y + h - r, color);
        // 四角
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
