package top.csituka.youzaiworldcore.client.screen.map;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.events.AbstractContainerEventHandler;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.input.KeyEvent;
import org.lwjgl.glfw.GLFW;
import net.minecraft.network.chat.Component;
import top.csituka.youzaiworldcore.client.map.MapTexts;
import top.csituka.youzaiworldcore.client.render.YzuiTheme;
import top.csituka.youzaiworldcore.client.screen.widget.TransparentButton;
import java.util.ArrayList;
import java.util.List;

/** 地图内覆盖面板的控件容器；不创建 Screen，也不替换地图画布。 */
abstract class MapOverlayPanel extends AbstractContainerEventHandler implements Renderable, NarratableEntry {
    protected final YzWorldMapScreen owner;
    protected final Font font = Minecraft.getInstance().font;
    protected final Component title;
    protected Component error = Component.empty();
    protected int width, height, panelX, panelY, panelWidth, panelHeight;
    private final List<AbstractWidget> widgets = new ArrayList<>();
    MapOverlayPanel(YzWorldMapScreen owner, String title) { this.owner = owner; this.title = MapTexts.text(title); }
    final void layout(int width, int height) { this.width = width; this.height = height; init(); }
    protected void init() { clearWidgets(); panelX = 8; panelY = 8; panelWidth = Math.min(width - 16, Math.max(180, width / 2)); panelHeight = height - 16; }
    protected void clearWidgets() { clearFocus(); widgets.clear(); }
    protected void clearFocus() { setFocused(null); }
    protected <T extends AbstractWidget> T addRenderableWidget(T widget) { widgets.add(widget); return widget; }
    protected void removeWidget(AbstractWidget widget) { if (getFocused() == widget) clearFocus(); widgets.remove(widget); }
    protected TransparentButton button(int x, int y, int width, Component label, Runnable action) {
        return addRenderableWidget(new TransparentButton(x, y, Math.max(16, width), 22, label, action));
    }
    protected EditBox field(int x, int y, int width, String key, String value, int limit, java.util.function.Consumer<String> changed) {
        var box = new EditBox(font, x, y, Math.max(24, width), 20, MapTexts.text(key));
        box.setMaxLength(limit); box.setValue(value); box.setResponder(changed); return addRenderableWidget(box);
    }
    protected void label(GuiGraphicsExtractor g, Component text, int x, int y, int available) { YzuiTheme.label(g, font, text, x, y, available, YzuiTheme.text(), false); }
    protected void content(GuiGraphicsExtractor g, int mx, int my, float delta) { }
    public void tick() { }
    public void onClose() { owner.closeOverlay(this); }
    @Override public List<? extends GuiEventListener> children() { return List.copyOf(widgets); }
    @Override public ScreenRectangle getRectangle() { return new ScreenRectangle(panelX, panelY, panelWidth, panelHeight); }
    @Override public boolean isMouseOver(double x, double y) { return x >= panelX && x < panelX + panelWidth && y >= panelY && y < panelY + panelHeight; }
    boolean covers(AbstractWidget w) { return w.getX() < panelX + panelWidth && w.getX() + w.getWidth() > panelX && w.getY() < panelY + panelHeight && w.getY() + w.getHeight() > panelY; }
    @Override public NarrationPriority narrationPriority() { return getFocused() == null ? NarrationPriority.NONE : NarrationPriority.FOCUSED; }
    @Override public void updateNarration(NarrationElementOutput output) {
        output.add(NarratedElementType.TITLE, title);
        if (getFocused() instanceof NarratableEntry entry) entry.updateNarration(output.nest());
    }
    @Override public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
        g.nextStratum(); YzuiTheme.card(g, panelX, panelY, panelWidth, panelHeight);
        label(g, title, panelX + 12, panelY + 14, panelWidth - 44);
        content(g, mx, my, delta);
        if (!error.getString().isEmpty()) label(g, error, panelX + 12, panelY + panelHeight - 48, panelWidth - 24);
        for (var widget : List.copyOf(widgets)) widget.extractRenderState(g, mx, my, delta);
    }
    @Override public boolean keyPressed(KeyEvent event) { if (event.key() == GLFW.GLFW_KEY_ESCAPE) { onClose(); return true; } return super.keyPressed(event); }
}
