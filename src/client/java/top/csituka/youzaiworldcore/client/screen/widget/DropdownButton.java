package top.csituka.youzaiworldcore.client.screen.widget;

import java.util.List;
import java.util.function.IntConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import top.csituka.youzaiworldcore.client.animation.GuiAnimationController;
import top.csituka.youzaiworldcore.client.render.RoundedRect;
import top.csituka.youzaiworldcore.client.render.YzuiTheme;
import top.csituka.youzaiworldcore.client.animation.YzuiHover;

/** MD3 下拉选择：逻辑视口边界、向上展开、滚动长列表和键盘操作共用同一命中区域。 */
public class DropdownButton extends AbstractWidget {
    private final List<String> options;
    private final int rowHeight;
    private final int requestedPopupWidth;
    private final IntConsumer onSelectionChanged;
    private final Runnable onToggleOpen;
    private int selectedIndex;
    private int keyboardIndex;
    private int firstVisible;
    private int visibleRows = 1;
    private int popupLeft, popupTop, popupRenderWidth, popupRenderHeight;
    private boolean open;
    private float externalAlpha = 1f;
    private final YzuiHover hoverState = new YzuiHover();
    private float popupAlpha;
    private long lastFrame;
    private int canvasWidth, canvasHeight;

    public DropdownButton(int x, int y, int width, int popupWidth, int height, Component message,
            List<String> options, int selectedIndex, boolean open,
            IntConsumer onSelectionChanged, Runnable onToggleOpen) {
        super(x, y, width, height, message);
        this.options = List.copyOf(options);
        this.rowHeight = height;
        this.requestedPopupWidth = popupWidth;
        this.selectedIndex = selectedIndex;
        this.keyboardIndex = Math.max(0, selectedIndex);
        this.open = open;
        this.onSelectionChanged = onSelectionChanged;
        this.onToggleOpen = onToggleOpen;
    }

    public int getSelectedIndex() { return selectedIndex; }
    public boolean isOpen() { return open; }
    public void setExternalAlpha(float value) { externalAlpha = Math.clamp(value, 0f, 1f); }
    /** 邮件等独立设计视口显式提供边界，避免把物理宽高误用为设计坐标。 */
    public void setCanvasSize(int width, int height) {
        canvasWidth = Math.max(0, width);
        canvasHeight = Math.max(0, height);
    }
    public void closePopup() {
        if (open) {
            open = false;
            if (onToggleOpen != null) onToggleOpen.run();
        }
    }

    private void openPopup() {
        if (!active || options.isEmpty()) return;
        open = true;
        keyboardIndex = Math.clamp(selectedIndex, 0, options.size() - 1);
        keepVisible();
        if (onToggleOpen != null) onToggleOpen.run();
    }

    private void choose(int index) {
        if (index < 0 || index >= options.size()) return;
        selectedIndex = index;
        closePopup();
        if (onSelectionChanged != null) onSelectionChanged.accept(index);
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float tick) {
        if (!visible) return;
        var font = Minecraft.getInstance().font;
        int x = getX(), y = getY();
        boolean hover = mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + rowHeight;
        float opacity = externalAlpha * getAlpha();
        YzuiTheme.button(g, x, y, width, rowHeight, hoverState.sample(active && hover), isFocused() || open,
                active, opacity, YzuiTheme.ButtonStyle.TONAL);
        int textY = y + (rowHeight - font.lineHeight) / 2;
        int foreground = YzuiTheme.alpha(active ? YzuiTheme.onPrimaryContainer() : YzuiTheme.textMuted(), opacity);
        int arrowX = x + width - 12;
        g.text(font, open ? "▴" : "▾", arrowX, textY, foreground, false);
        String value = selectedIndex >= 0 && selectedIndex < options.size() ? options.get(selectedIndex) : "";
        boolean hasLabel = !getMessage().getString().isEmpty();
        int valueWidth = hasLabel ? Math.max(32, (width - 36) / 2) : Math.max(0, width - 30);
        if (hasLabel) {
            YzuiTheme.label(g, font, getMessage(), x + 8, textY, width - valueWidth - 34, foreground, false);
        }
        YzuiTheme.label(g, font, Component.literal(value), x + width - valueWidth - 20,
                textY, valueWidth, foreground, false);
    }

    public void render(GuiGraphicsExtractor g, int mouseX, int mouseY, float tick) {
        extractWidgetRenderState(g, mouseX, mouseY, tick);
    }

    public void renderPopup(GuiGraphicsExtractor g, int mouseX, int mouseY, float tick) {
        renderPopup(g, mouseX, mouseY, tick, 4, (canvasHeight > 0 ? canvasHeight : g.guiHeight()) - 4, 0);
    }

    /** 坐标与调用方的滚动内容一致，边界则使用页面坐标。 */
    public void renderPopup(GuiGraphicsExtractor g, int mouseX, int mouseY, float tick,
            int viewportTop, int viewportBottom, double scrollOffset) {
        long now = System.nanoTime();
        float duration = YzuiTheme.frosted() ? 160_000_000f : 90_000_000f;
        float amount = lastFrame == 0 ? 1f : Math.min(1f, (now - lastFrame) / duration);
        lastFrame = now;
        float target = open ? 1f : 0f;
        popupAlpha = GuiAnimationController.isEnabled() ? popupAlpha + (target - popupAlpha) * amount : target;
        if (popupAlpha < 0.01f || options.isEmpty() || !visible) return;

        var font = Minecraft.getInstance().font;
        int naturalWidth = requestedPopupWidth;
        for (String option : options) naturalWidth = Math.max(naturalWidth, font.width(option) + 32);
        int viewportWidth = canvasWidth > 0 ? canvasWidth : g.guiWidth();
        int pw = Math.min(naturalWidth, Math.max(1, viewportWidth - 16));
        int top = Math.max(4, viewportTop), bottom = Math.max(top + rowHeight, viewportBottom - 4);
        visibleRows = Math.max(1, Math.min(options.size(), (bottom - top) / rowHeight));
        keepVisible();
        int ph = visibleRows * rowHeight + 4;
        int px = Math.clamp(getX() + width - pw, 8, Math.max(8, viewportWidth - pw - 8));
        int offset = (int) Math.round(scrollOffset);
        int buttonY = getY() - offset;
        boolean upward = buttonY + rowHeight + ph > bottom && buttonY - ph >= top;
        int desired = upward ? buttonY - ph - 2 : buttonY + rowHeight + 2;
        int py = Math.clamp(desired, top, Math.max(top, bottom - ph)) + offset;
        popupLeft = px;
        popupTop = py;
        popupRenderWidth = pw;
        popupRenderHeight = ph;

        g.nextStratum();
        float opacity = popupAlpha * externalAlpha * getAlpha();
        if (!YzuiTheme.minimal()) RoundedRect.fill(g, px, py + 2, pw, ph, 8,
                YzuiTheme.alpha(0xFF000000, 0.14f * opacity));
        RoundedRect.fill(g, px, py, pw, ph, 8, YzuiTheme.multiplyAlpha(YzuiTheme.surface(), opacity));
        YzuiTheme.border(g, px, py, pw, ph, 8, YzuiTheme.alpha(YzuiTheme.outlineVariant(), opacity));
        int hovered = optionAt(mouseX, mouseY);
        g.enableScissor(px + 1, py + 1, px + pw - 1, py + ph - 1);
        for (int row = 0; row < visibleRows; row++) {
            int index = firstVisible + row;
            int rowY = py + 2 + row * rowHeight;
            boolean selected = index == selectedIndex;
            if (selected || index == hovered || (hovered < 0 && index == keyboardIndex && isFocused())) {
                RoundedRect.fill(g, px + 3, rowY, pw - 6, rowHeight, 5,
                        YzuiTheme.multiplyAlpha(selected ? YzuiTheme.alpha(YzuiTheme.primaryContainer(), 0.7f) : YzuiTheme.slotHover(), opacity));
            }
            int color = selected ? YzuiTheme.onPrimaryContainer() : YzuiTheme.text();
            YzuiTheme.label(g, font, Component.literal(options.get(index)), px + 12,
                    rowY + (rowHeight - font.lineHeight) / 2, pw - 32, YzuiTheme.alpha(color, opacity), false);
            if (selected) g.text(font, "✓", px + pw - 16, rowY + (rowHeight - font.lineHeight) / 2,
                    YzuiTheme.alpha(YzuiTheme.primary(), opacity), false);
        }
        g.disableScissor();
    }

    private int optionAt(double x, double y) {
        if (x < popupLeft || x >= popupLeft + popupRenderWidth
                || y < popupTop + 2 || y >= popupTop + popupRenderHeight - 2) return -1;
        int index = firstVisible + (int) ((y - popupTop - 2) / rowHeight);
        return index < options.size() ? index : -1;
    }

    private void keepVisible() {
        firstVisible = Math.clamp(firstVisible, 0, Math.max(0, options.size() - visibleRows));
        if (keyboardIndex < firstVisible) firstVisible = keyboardIndex;
        if (keyboardIndex >= firstVisible + visibleRows) firstVisible = keyboardIndex - visibleRows + 1;
    }

    public boolean isPositionInsidePopup(double x, double y) {
        return open && isMouseOver(x, y);
    }

    @Override
    public boolean isMouseOver(double x, double y) {
        if (!active || !visible) return false;
        return x >= getX() && x < getX() + width && y >= getY() && y < getY() + rowHeight
                || open && x >= popupLeft && x < popupLeft + popupRenderWidth
                && y >= popupTop && y < popupTop + popupRenderHeight;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (!isMouseOver(event.x(), event.y()) || event.button() != 0) return false;
        setFocused(true);
        playDownSound(Minecraft.getInstance().getSoundManager());
        onClick(event, doubleClick);
        return true;
    }

    @Override
    public void onClick(MouseButtonEvent event, boolean doubleClick) {
        if (!active || !visible) return;
        if (!open) openPopup();
        else {
            int index = optionAt(event.x(), event.y());
            if (index >= 0) choose(index);
            else closePopup();
        }
    }

    @Override
    public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        if (!open || !isMouseOver(x, y)) return false;
        firstVisible = Math.clamp(firstVisible - (int) Math.signum(vertical), 0,
                Math.max(0, options.size() - visibleRows));
        keyboardIndex = Math.clamp(keyboardIndex, firstVisible,
                Math.min(options.size() - 1, firstVisible + visibleRows - 1));
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (!active || !visible || (!isFocused() && !open)) return false;
        if (event.key() == 256 && open) { closePopup(); return true; }
        if (event.key() == 257 || event.key() == 335 || event.key() == 32) {
            if (open) choose(keyboardIndex); else openPopup();
            return true;
        }
        if (event.key() == 264 || event.key() == 265) {
            if (!open) openPopup();
            else keyboardIndex = Math.clamp(keyboardIndex + (event.key() == 264 ? 1 : -1), 0, options.size() - 1);
            keepVisible();
            return true;
        }
        return false;
    }

    @Override
    public void setFocused(boolean focused) {
        super.setFocused(focused);
        if (!focused) closePopup();
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }
}
