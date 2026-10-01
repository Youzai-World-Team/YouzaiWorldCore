package top.csituka.youzaiworldcore.client.screen.map;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.AbstractContainerEventHandler;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import top.csituka.youzaiworldcore.client.config.MapSettings;
import top.csituka.youzaiworldcore.client.map.MapTexts;
import top.csituka.youzaiworldcore.client.render.RoundedRect;
import top.csituka.youzaiworldcore.client.render.YzuiTheme;
import top.csituka.youzaiworldcore.client.screen.widget.TransparentButton;
import top.csituka.youzaiworldcore.util.DebugLogger;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** 地图内部的左侧快捷设置覆盖层，共用当前地图画布与订阅，不创建独立屏幕。 */
@SuppressWarnings("null")
final class MapSettingsPanel extends AbstractContainerEventHandler implements Renderable, NarratableEntry {
    private static final MapSettings.Toggle[] OPTIONS = {
            MapSettings.Toggle.MARKER_ICONS, MapSettings.Toggle.WAYPOINTS, MapSettings.Toggle.RADAR_PLAYERS,
            MapSettings.Toggle.ANCHORS, MapSettings.Toggle.RADAR_FRIENDLY, MapSettings.Toggle.RADAR_HOSTILE,
            MapSettings.Toggle.RADAR_TAMED, MapSettings.Toggle.RADAR_OTHER, MapSettings.Toggle.MARKER_LABELS,
            null, MapSettings.Toggle.QUICK_LOCATE, MapSettings.Toggle.REMEMBER_VIEW
    };
    private final YzWorldMapScreen owner;
    private final Font font = Minecraft.getInstance().font;
    private final List<TransparentButton> widgets = new ArrayList<>();
    private int width, height, panelX, panelY, panelWidth, panelHeight;
    private final List<TransparentButton> dropdown = new ArrayList<>();
    private int offset, visibleRows, listTop, listBottom, choiceX, choiceY, choiceWidth, dropdownY;
    private boolean choosingPosition;

    MapSettingsPanel(YzWorldMapScreen owner) { this.owner = owner; }

    /** 地图窗口变化时重排面板，不重建地图画布。 */
    void layout(int width, int height) { this.width = width; this.height = height; init(); }

    private void init() {
        setFocused(null); setDragging(false); widgets.clear(); dropdown.clear();
        panelWidth = Math.min(width - 16, Math.max(160, width / 2));
        panelX = 8; panelY = 8; panelHeight = height - 16;
        listTop = panelY + 42; listBottom = panelY + panelHeight - 86;
        visibleRows = Math.max(1, (listBottom - listTop) / 23);
        offset = Math.clamp(offset, 0, Math.max(0, OPTIONS.length - visibleRows));
        button(panelX + panelWidth - 30, panelY + 7, 22, Component.literal("×"), owner::closeSettings)
                .setTooltip(Tooltip.create(MapTexts.text("done")));
        for (int i = 0; i < visibleRows && offset + i < OPTIONS.length; i++) {
            int index = offset + i, y = listTop + i * 23;
            var option = OPTIONS[index];
            if (option == null) {
                choiceX = panelX + 26; choiceY = y; choiceWidth = panelWidth - 42;
                var control = button(choiceX, choiceY, choiceWidth, MapTexts.text("setting_value", MapTexts.text("label_position"), positionLabel(MapSettings.labelPosition())).copy().append(choosingPosition ? "  ↑" : "  ↓"),
                        () -> { choosingPosition = !choosingPosition; init(); });
                control.setTextLeftAligned(true);
                control.setTooltip(Tooltip.create(MapTexts.text("label_position")));
            } else addWidget(new ToggleRow(panelX + 12, y, panelWidth - 28, option, index >= 1 && index <= 7));
        }
        button(panelX + 12, panelY + panelHeight - 34, panelWidth - 24, MapTexts.text("more_settings"),
                () -> Minecraft.getInstance().gui.setScreen(new MapAdvancedSettingsScreen(owner)));
        if (choosingPosition && offset <= 9 && offset + visibleRows > 9) buildDropdown();
        else choosingPosition = false;
        DebugLogger.debug("MapSettings", "显示地图左侧覆盖设置面板，滚动位置=%d", offset);
    }

    private void buildDropdown() {
        int popupHeight = MapSettings.LabelPosition.values().length * 23;
        dropdownY = choiceY + 24 + popupHeight <= panelY + panelHeight - 40 ? choiceY + 24 : choiceY - popupHeight - 2;
        dropdownY = Math.max(panelY + 34, dropdownY);
        // 背景和选项注册在其他控件之后，绘制与输入都优先于底下的开关。
        var background = new TransparentButton(choiceX - 2, dropdownY - 2, choiceWidth + 4, popupHeight + 4, Component.empty(), () -> { }) {
            @Override protected void extractWidgetRenderState(GuiGraphicsExtractor g, int x, int y, float delta) {
                g.nextStratum(); YzuiTheme.card(g, getX(), getY(), getWidth(), getHeight());
            }
        };
        dropdown.add(addWidget(background));
        for (var position : MapSettings.LabelPosition.values()) {
            var control = button(choiceX, dropdownY + position.ordinal() * 23, choiceWidth, positionLabel(position), () -> {
                MapSettings.setLabelPosition(position); choosingPosition = false; init();
            });
            control.setTextLeftAligned(true);
            if (MapSettings.labelPosition() == position) control.setStyle(YzuiTheme.ButtonStyle.FILLED);
            dropdown.add(control);
        }
    }

    private static Component positionLabel(MapSettings.LabelPosition position) {
        return MapTexts.text("label_position." + position.name().toLowerCase(Locale.ROOT));
    }

    private final class ToggleRow extends TransparentButton {
        private final MapSettings.Toggle option;
        private final boolean child;
        private ToggleRow(int x, int y, int width, MapSettings.Toggle option, boolean child) {
            super(x, y, width, 22, MapTexts.text("option." + option.key()), () -> MapSettings.toggle(option));
            this.option = option; this.child = child;
            setTooltip(Tooltip.create(getMessage()));
        }
        @Override protected void extractWidgetRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
            boolean on = MapSettings.enabled(option);
            var label = MapTexts.text("option." + option.key());
            setMessage(MapTexts.text("setting_value", label, MapTexts.text(on ? "on" : "off")));
            YzuiTheme.button(g, getX(), getY(), getWidth(), getHeight(), isMouseOver(mx, my) ? 1 : 0, isFocused(), true, 1, YzuiTheme.ButtonStyle.TEXT);
            int indent = child ? 14 : 0;
            if (child) {
                g.fill(getX() + 5, getY(), getX() + 6, getY() + 22, YzuiTheme.outlineVariant());
                g.fill(getX() + 5, getY() + 11, getX() + 11, getY() + 12, YzuiTheme.outlineVariant());
            }
            YzuiTheme.label(g, font, label, getX() + indent, getY() + 7, getWidth() - indent - 42, YzuiTheme.text(), false);
            int x = getX() + getWidth() - 34, y = getY() + 4;
            RoundedRect.fill(g, x, y, 32, 14, 7, on ? YzuiTheme.primary() : YzuiTheme.outlineVariant());
            // 蓝图约定：左侧为启用，右侧为禁用。
            RoundedRect.fill(g, x + (on ? 2 : 18), y + 2, 10, 10, 5, on ? YzuiTheme.onPrimary() : YzuiTheme.textMuted());
        }
    }

    private <T extends TransparentButton> T addWidget(T widget) { widgets.add(widget); return widget; }
    private TransparentButton button(int x, int y, int width, Component label, Runnable action) {
        return addWidget(new TransparentButton(x, y, width, 22, label, action));
    }

    @Override public List<? extends GuiEventListener> children() { return widgets; }
    @Override public boolean isMouseOver(double x, double y) {
        return x >= panelX && x < panelX + panelWidth && y >= panelY && y < panelY + panelHeight;
    }
    boolean covers(AbstractWidget widget) {
        return widget.getX() < panelX + panelWidth && widget.getX() + widget.getWidth() > panelX
                && widget.getY() < panelY + panelHeight && widget.getY() + widget.getHeight() > panelY;
    }
    @Override public ScreenRectangle getRectangle() { return new ScreenRectangle(panelX, panelY, panelWidth, panelHeight); }
    @Override public NarrationPriority narrationPriority() { return getFocused() == null ? NarrationPriority.NONE : NarrationPriority.FOCUSED; }
    @Override public void updateNarration(NarrationElementOutput output) {
        output.add(NarratedElementType.TITLE, MapTexts.text("settings"));
        if (getFocused() instanceof NarratableEntry entry) entry.updateNarration(output.nest());
    }

    @Override public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
        g.nextStratum();
        YzuiTheme.card(g, panelX, panelY, panelWidth, panelHeight);
        YzuiTheme.label(g, font, MapTexts.text("settings"), panelX + 14, panelY + 14, panelWidth - 50, YzuiTheme.text(), false);
        g.fill(panelX + 12, panelY + 33, panelX + panelWidth - 12, panelY + 34, YzuiTheme.outlineVariant());
        YzuiTheme.wrapped(g, font, MapTexts.text("quick_settings_hint"), panelX + 12, panelY + panelHeight - 75, panelWidth - 24, 2, YzuiTheme.textMuted());
        if (OPTIONS.length > visibleRows) {
            int track = listBottom - listTop, thumb = Math.max(12, track * visibleRows / OPTIONS.length);
            int y = listTop + (track - thumb) * offset / (OPTIONS.length - visibleRows);
            g.fill(panelX + panelWidth - 6, listTop, panelX + panelWidth - 4, listBottom, YzuiTheme.outlineVariant());
            g.fill(panelX + panelWidth - 6, y, panelX + panelWidth - 4, y + thumb, YzuiTheme.primary());
        }
        for (var widget : widgets) widget.extractRenderState(g, mx, my, delta);
    }

    @Override public boolean mouseClicked(MouseButtonEvent event, boolean actual) {
        if (choosingPosition) {
            for (int i = dropdown.size() - 1; i >= 0; i--) if (dropdown.get(i).mouseClicked(event, actual)) return true;
            choosingPosition = false; init(); return true;
        }
        if (!isMouseOver(event.x(), event.y())) return false;
        super.mouseClicked(event, actual); return true;
    }

    @Override public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        if (vertical == 0 || x < panelX || x >= panelX + panelWidth || y < listTop || y >= listBottom) return false;
        offset = Math.clamp(offset + (vertical > 0 ? -1 : 1), 0, Math.max(0, OPTIONS.length - visibleRows));
        choosingPosition = false; init(); return true;
    }

    @Override public boolean keyPressed(KeyEvent event) {
        if (choosingPosition && event.key() == GLFW.GLFW_KEY_ESCAPE) { choosingPosition = false; init(); return true; }
        if (event.key() == GLFW.GLFW_KEY_ESCAPE) { owner.closeSettings(); return true; }
        return super.keyPressed(event);
    }


}
