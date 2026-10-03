package top.csituka.youzaiworldcore.client.screen.map;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import top.csituka.youzaiworldcore.client.map.MapMobIcons;
import top.csituka.youzaiworldcore.client.map.MapMobRadar;
import top.csituka.youzaiworldcore.client.map.MapTexts;
import top.csituka.youzaiworldcore.client.render.YzuiTheme;
import top.csituka.youzaiworldcore.client.screen.widget.TransparentButton;
import top.csituka.youzaiworldcore.util.DebugLogger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** 按生物种类选择的图标目录；选择持续作用于小地图及全屏地图，未发现的种类也可选择。 */
final class MapRadarPanel extends MapOverlayPanel {
    private final List<TransparentButton> cells = new ArrayList<>();
    private final Map<EntityType<?>, Integer> counts = new HashMap<>();
    private String query = "";
    private int page, pages = 1, count, capacity, columns, cellWidth, gridTop, footer, ticks;
    private boolean dirty;
    private EditBox search;
    private TransparentButton previous, next, clear, view;

    MapRadarPanel(YzWorldMapScreen owner) {
        super(owner, "radar");
        DebugLogger.debug("MapRadar", "打开生物种类图标目录");
    }
    @Override protected void init() {
        boolean focused = search != null && getFocused() == search;
        super.init(); cells.clear();
        panelWidth = Math.min(width - 16, Math.max(280, Math.min(440, width / 2)));
        button(panelX + panelWidth - 32, panelY + 7, 22, Component.literal("×"), this::onClose);
        search = field(panelX + 12, panelY + 38, panelWidth - 24, "radar_search", query, 64, value -> {
            query = value; page = 0; dirty = true;
        });
        search.setHint(MapTexts.text("radar_search"));
        gridTop = panelY + 86; footer = panelY + panelHeight - 32;
        columns = Math.max(1, (panelWidth - 20) / 60); cellWidth = (panelWidth - 24 - (columns - 1) * 4) / columns;
        capacity = Math.max(0, (footer - 46 - gridTop) / 58) * columns;
        int actionWidth = (panelWidth - 28) / 2;
        clear = button(panelX + 12, footer - 26, actionWidth, MapTexts.text("radar_clear"), () -> { MapMobRadar.select(null); refreshCounts(); });
        view = button(panelX + 16 + actionWidth, footer - 26, actionWidth, MapTexts.text("radar_view"), owner::showMobRadar);
        view.setStyle(YzuiTheme.ButtonStyle.FILLED);
        previous = button(panelX + 12, footer, 24, Component.literal("‹"), () -> { page--; refreshGrid(); });
        next = button(panelX + panelWidth - 36, footer, 24, Component.literal("›"), () -> { page++; refreshGrid(); });
        previous.setTooltip(Tooltip.create(MapTexts.text("previous"))); next.setTooltip(Tooltip.create(MapTexts.text("next")));
        refreshGrid(); refreshCounts();
        if (focused) setFocused(search);
    }
    private void refreshGrid() {
        dirty = false;
        cells.forEach(this::removeWidget); cells.clear();
        String filter = query.strip().toLowerCase(Locale.ROOT);
        var matches = MapMobIcons.types().stream().filter(type -> type.getDescription().getString().toLowerCase(Locale.ROOT).contains(filter)
                || EntityType.getKey(type).toString().contains(filter)).toList();
        count = matches.size(); pages = Math.max(1, Math.ceilDiv(count, Math.max(1, capacity))); page = Math.clamp(page, 0, pages - 1);
        previous.active = capacity > 0 && page > 0; next.active = capacity > 0 && page + 1 < pages;
        for (int i = 0; i < capacity && page * capacity + i < count; i++) {
            var type = matches.get(page * capacity + i);
            int x = panelX + 12 + (i % columns) * (cellWidth + 4), y = gridTop + (i / columns) * 58;
            var cell = new TransparentButton(x, y, cellWidth, 54, type.getDescription(), () -> {
                MapMobRadar.select(MapMobRadar.selected() == type ? null : type); refreshCounts();
            }) {
                @Override protected void extractWidgetRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
                    boolean selected = type == MapMobRadar.selected();
                    var style = selected ? YzuiTheme.ButtonStyle.FILLED : YzuiTheme.ButtonStyle.TONAL;
                    YzuiTheme.button(g, getX(), getY(), getWidth(), getHeight(), isMouseOver(mx, my) ? 1 : 0, isFocused(), active, 1, style);
                    MapMobIcons.draw(g, type, getX() + (getWidth() - 28) / 2, getY() + 5, 28, 1);
                    int color = YzuiTheme.buttonText(style, true);
                    YzuiTheme.label(g, font, type.getDescription(), getX() + 3, getY() + 40, getWidth() - 6, color, true);
                    if (selected) g.outline(getX() + 1, getY() + 1, getWidth() - 2, getHeight() - 2, YzuiTheme.primary());
                    int found = counts.getOrDefault(type, 0);
                    if (found > 0) YzuiTheme.label(g, font, Component.literal(Integer.toString(found)), getX() + getWidth() - 22, getY() + 3, 20, color, true);
                }
            };
            cell.setTooltip(Tooltip.create(type.getDescription().copy().append("\n" + EntityType.getKey(type)).append("\n").append(MapTexts.text("radar_hint"))));
            cells.add(addRenderableWidget(cell));
        }
    }
    private void refreshCounts() {
        counts.clear();
        var client = Minecraft.getInstance();
        if (client.level != null) for (var entity : client.level.entitiesForRendering())
            if (MapMobRadar.visible(entity)) counts.merge(entity.getType(), 1, Integer::sum);
        if (MapMobRadar.selected() != null) counts.put(MapMobRadar.selected(), MapMobRadar.targets().size());
        clear.active = view.active = MapMobRadar.selected() != null;
        search.setTooltip(Tooltip.create(MapTexts.text(MapMobRadar.serverSearch() ? "radar_hint" : "radar_local_only")));
    }
    @Override public void tick() { if (dirty) refreshGrid(); if (++ticks % 10 == 0) refreshCounts(); }
    @Override protected void content(GuiGraphicsExtractor g, int mx, int my, float delta) {
        label(g, MapTexts.text(MapMobRadar.serverSearch() ? "radar_range" : "radar_local_range", MapMobRadar.radius(), count), panelX + 12, panelY + 67, panelWidth - 24);
        if (count == 0) YzuiTheme.wrapped(g, font, MapTexts.text("radar_empty"), panelX + 12, gridTop + 4, panelWidth - 24, 2, YzuiTheme.textMuted());
        var selected = MapMobRadar.selected();
        var status = selected == null ? MapTexts.text("radar_hint") : MapMobRadar.waiting() ? MapTexts.text("radar_waiting", selected.getDescription())
                : MapTexts.text("radar_selected", selected.getDescription(), counts.getOrDefault(selected, 0));
        label(g, status, panelX + 12, footer - 43, panelWidth - 24);
        YzuiTheme.label(g, font, MapTexts.text("page", page + 1, pages), panelX + 42, footer + 7, panelWidth - 84, YzuiTheme.textMuted(), true);
    }
    @Override public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        if (!isMouseOver(x, y)) return false;
        if (vertical != 0 && capacity > 0) { page += vertical > 0 ? -1 : 1; refreshGrid(); }
        return true;
    }
}
