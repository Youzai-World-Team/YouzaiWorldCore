package top.csituka.youzaiworldcore.client.screen.map;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import top.csituka.youzaiworldcore.client.map.MapTexts;
import top.csituka.youzaiworldcore.client.render.YzuiTheme;
import top.csituka.youzaiworldcore.client.screen.widget.TransparentButton;

/** 地图工具页的统一主题外壳；控件沿用 YZUI，窗口尺寸变化时重新布局。 */
@SuppressWarnings("null")
public abstract class MapScreen extends Screen {
    protected final Screen parent;
    protected int panelX, panelY, panelWidth, panelHeight;
    protected Component error = Component.empty();

    protected MapScreen(Screen parent, String title) { super(MapTexts.text(title)); this.parent = parent; }

    @Override protected void init() {
        clearWidgets();
        panelWidth = Math.max(160, Math.min(680, width - 16)); panelHeight = Math.max(140, height - 16);
        panelX = (width - panelWidth) / 2; panelY = 8;
    }

    protected TransparentButton button(int x, int y, int width, Component text, Runnable action) {
        return addRenderableWidget(new TransparentButton(x, y, Math.max(16, width), 22, text, action));
    }

    protected EditBox field(int x, int y, int width, String key, String value, int maxLength, java.util.function.Consumer<String> changed) {
        var box = new EditBox(font, x, y, Math.max(24, width), 20, MapTexts.text(key));
        box.setMaxLength(maxLength); box.setValue(value); box.setResponder(changed);
        return addRenderableWidget(box);
    }

    protected void label(GuiGraphicsExtractor g, Component text, int x, int y, int available) {
        YzuiTheme.label(g, font, text, x, y, available, YzuiTheme.text(), false);
    }

    @Override public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        YzuiTheme.backdrop(g);
    }

    @Override public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        YzuiTheme.card(g, panelX, panelY, panelWidth, panelHeight);
        label(g, title, panelX + 14, panelY + 14, panelWidth - 28);
        content(g, mouseX, mouseY, delta);
        if (!error.getString().isEmpty()) YzuiTheme.label(g, font, error, panelX + 12, panelY + panelHeight - 49,
                panelWidth - 24, YzuiTheme.error(), false);
        super.extractRenderState(g, mouseX, mouseY, delta);
    }

    protected void content(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) { }
    @Override public boolean isPauseScreen() { return false; }
    @Override public void onClose() { Minecraft.getInstance().gui.setScreen(parent); }
}
