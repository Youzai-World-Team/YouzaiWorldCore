package top.csituka.youzaiworldcore.client.screen.map;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Tooltip;
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
import top.csituka.youzaiworldcore.client.config.MapSettings;
import top.csituka.youzaiworldcore.client.map.MapTexts;
import top.csituka.youzaiworldcore.client.render.RoundedRect;
import top.csituka.youzaiworldcore.client.render.YzuiTheme;
import top.csituka.youzaiworldcore.client.render.MenuIcon;
import top.csituka.youzaiworldcore.client.screen.widget.TransparentButton;
import top.csituka.youzaiworldcore.client.screen.widget.DropdownButton;
import top.csituka.youzaiworldcore.util.DebugLogger;
import java.util.ArrayList;
import java.util.List;

/** 日常地图设置只保留十个入口；外观、信息和生物分类分别在小菜单调整。 */
@SuppressWarnings("null")
final class MapSettingsPanel extends AbstractContainerEventHandler implements Renderable, NarratableEntry {
    private enum Entry {
        MINIMAP(MapSettings.Toggle.MINIMAP), APPEARANCE("map_appearance"), INFORMATION("map_information"),
        MARKERS("marker_display"), WAYPOINTS(MapSettings.Toggle.WAYPOINTS), ANCHORS(MapSettings.Toggle.ANCHORS),
        PLAYERS(MapSettings.Toggle.RADAR_PLAYERS), MOBS("map_mobs"), NAVIGATION(MapSettings.Toggle.WAYPOINT_HUD),
        REMEMBER(MapSettings.Toggle.REMEMBER_VIEW);
        final MapSettings.Toggle toggle;
        final String key;
        Entry(MapSettings.Toggle toggle) { this.toggle = toggle; key = "option." + toggle.key(); }
        Entry(String key) { this.key = key; toggle = null; }
    }
    private final YzWorldMapScreen owner;
    private final List<AbstractWidget> widgets = new ArrayList<>();
    private int panelX, panelY, panelWidth, panelHeight, offset, visibleRows, listTop, listBottom;
    MapSettingsPanel(YzWorldMapScreen owner) { this.owner = owner; }

    /** 窗口变化时重排设置，不重建地图数据。 */
    void layout(int width, int height) {
        panelX = 8; panelY = 8; panelWidth = Math.min(width - 16, Math.max(160, width / 2));
        panelHeight = height - 16; init();
    }
    private void init() {
        setFocused(null); setDragging(false); widgets.clear();
        listTop = panelY + 42; listBottom = panelY + panelHeight - 74;
        visibleRows = Math.max(1, (listBottom - listTop) / 23);
        offset = Math.clamp(offset, 0, Math.max(0, Entry.values().length - visibleRows));
        button(panelX + panelWidth - 30, panelY + 7, 22, Component.literal("×"), owner::closeSettings)
                .setTooltip(Tooltip.create(MapTexts.text("done")));
        for (int i = 0; i < visibleRows && offset + i < Entry.values().length; i++) {
            var entry = Entry.values()[offset + i];
            if (entry == Entry.MARKERS) {
                var values = MapSettings.MarkerDisplay.values();
                var labels = java.util.Arrays.stream(values).map(value -> MapTexts.text("marker_display."
                        + value.name().toLowerCase(java.util.Locale.ROOT)).getString()).toList();
                widgets.add(new DropdownButton(panelX + 12, listTop + i * 23, panelWidth - 28, panelWidth - 28, 22,
                        MapTexts.text(entry.key), labels, MapSettings.markerDisplay().ordinal(), false,
                        selected -> MapSettings.setMarkerDisplay(values[selected]), null) {
                    @Override protected void extractWidgetRenderState(GuiGraphicsExtractor g, int x, int y, float tick) {
                        updateOptions(labels, MapSettings.markerDisplay().ordinal(), MapSettingsCategoryScreen.markerDisplayLabel().getString());
                        super.extractWidgetRenderState(g, x, y, tick);
                    }
                });
                continue;
            }
            var control = new TransparentButton(panelX + 12, listTop + i * 23, panelWidth - 28, 22,
                    MapTexts.text(entry.key), () -> activate(entry)) {
                @Override protected void extractWidgetRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
                    boolean toggle = entry.toggle != null, on = toggle && MapSettings.enabled(entry.toggle);
                    Component value = entry == Entry.MARKERS ? MapSettingsCategoryScreen.markerDisplayLabel()
                            : toggle ? MapTexts.text(on ? "on" : "off") : Component.empty();
                    setMessage(toggle ? MapTexts.text("setting_value", MapTexts.text(entry.key), value) : MapTexts.text(entry.key));
                    YzuiTheme.button(g, getX(), getY(), getWidth(), getHeight(), isMouseOver(mx, my) ? 1 : 0,
                            isFocused(), true, 1, YzuiTheme.ButtonStyle.TEXT);
                    int valueWidth = toggle ? 38 : entry == Entry.MARKERS ? Math.min(70, getWidth() / 2) : 14;
                    YzuiTheme.label(g, Minecraft.getInstance().font, MapTexts.text(entry.key), getX(), getY() + 7,
                            getWidth() - valueWidth - 6, YzuiTheme.text(), false);
                    if (toggle) {
                        int x = getX() + getWidth() - 34, y = getY() + 4;
                        RoundedRect.fill(g, x, y, 32, 14, 7, on ? YzuiTheme.primary() : YzuiTheme.outlineVariant());
                        RoundedRect.fill(g, x + (on ? 18 : 2), y + 2, 10, 10, 5, on ? YzuiTheme.onPrimary() : YzuiTheme.textMuted());
                    } else MenuIcon.CHEVRON_RIGHT.renderCentered(g, getX() + getWidth() - valueWidth,
                            getY(), valueWidth, getHeight(), YzuiTheme.textMuted());
                }
            };
            control.setTooltip(Tooltip.create(MapTexts.text(entry.key))); widgets.add(control);
        }
        button(panelX + 12, panelY + panelHeight - 34, panelWidth - 24, MapTexts.text("more_settings"),
                () -> Minecraft.getInstance().gui.setScreen(new MapAdvancedSettingsScreen(owner)));
        DebugLogger.debug("MapSettings", "显示十项地图日常设置，滚动位置=%d", offset);
    }
    private void activate(Entry entry) {
        if (entry.toggle != null) { MapSettings.toggle(entry.toggle); return; }
        var category = switch (entry) {
            case APPEARANCE -> MapSettingsCategoryScreen.Category.APPEARANCE;
            case INFORMATION -> MapSettingsCategoryScreen.Category.INFORMATION;
            case MOBS -> MapSettingsCategoryScreen.Category.MOBS;
            default -> throw new IllegalStateException("地图设置入口无效");
        };
        Minecraft.getInstance().gui.setScreen(new MapSettingsCategoryScreen(owner, category));
    }
    private TransparentButton button(int x, int y, int width, Component label, Runnable action) {
        var widget = new TransparentButton(x, y, width, 22, label, action); widgets.add(widget); return widget;
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
        g.nextStratum(); YzuiTheme.card(g, panelX, panelY, panelWidth, panelHeight);
        var font = Minecraft.getInstance().font;
        YzuiTheme.label(g, font, MapTexts.text("settings"), panelX + 14, panelY + 14, panelWidth - 50, YzuiTheme.text(), false);
        g.fill(panelX + 12, panelY + 33, panelX + panelWidth - 12, panelY + 34, YzuiTheme.outlineVariant());
        YzuiTheme.wrapped(g, font, MapTexts.text("quick_settings_hint"), panelX + 12, panelY + panelHeight - 63, panelWidth - 24, 2, YzuiTheme.textMuted());
        if (Entry.values().length > visibleRows) {
            int track = listBottom - listTop, thumb = Math.max(12, track * visibleRows / Entry.values().length);
            int y = listTop + (track - thumb) * offset / (Entry.values().length - visibleRows);
            g.fill(panelX + panelWidth - 6, listTop, panelX + panelWidth - 4, listBottom, YzuiTheme.outlineVariant());
            g.fill(panelX + panelWidth - 6, y, panelX + panelWidth - 4, y + thumb, YzuiTheme.primary());
        }
        for (var widget : widgets) widget.extractRenderState(g, mx, my, delta);
        for (var widget : widgets) if (widget instanceof DropdownButton dropdown) {
            dropdown.renderPopup(g, mx, my, delta, listTop, listBottom, 0);
        }
    }
    @Override public boolean mouseClicked(MouseButtonEvent event, boolean actual) {
        for (var widget : widgets) if (widget instanceof DropdownButton dropdown && dropdown.isOpen()) {
            if (dropdown.isMouseOver(event.x(), event.y())) return dropdown.mouseClicked(event, actual);
            dropdown.closePopup();
        }
        if (!isMouseOver(event.x(), event.y())) return false;
        super.mouseClicked(event, actual); return true;
    }
    @Override public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        for (var widget : widgets) if (widget instanceof DropdownButton dropdown && dropdown.isOpen()) {
            if (dropdown.mouseScrolled(x, y, horizontal, vertical)) return true;
            dropdown.closePopup();
        }
        if (vertical == 0 || x < panelX || x >= panelX + panelWidth || y < listTop || y >= listBottom) return false;
        offset = Math.clamp(offset + (vertical > 0 ? -1 : 1), 0, Math.max(0, Entry.values().length - visibleRows)); init(); return true;
    }
    @Override public boolean keyPressed(KeyEvent event) {
        for (var widget : widgets) if (widget instanceof DropdownButton dropdown && dropdown.isOpen() && dropdown.keyPressed(event)) return true;
        if (event.key() == InputConstants.KEY_ESCAPE) { owner.closeSettings(); return true; }
        return super.keyPressed(event);
    }
}
