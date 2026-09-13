package top.csituka.youzaiworldcore.client.screen.map;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import top.csituka.youzaiworldcore.client.map.MapClient;
import top.csituka.youzaiworldcore.client.map.MapPersonalData;
import top.csituka.youzaiworldcore.client.map.MapTexts;
import top.csituka.youzaiworldcore.client.map.MapTransfer;
import top.csituka.youzaiworldcore.client.render.YzuiTheme;
import top.csituka.youzaiworldcore.map.MapWaypoint;

import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/** 可搜索、按组筛选的私人/死亡/公共路径点列表。 */
public final class MapWaypointListScreen extends MapScreen {
    private String search = "", group;
    private int page, rows;
    public MapWaypointListScreen(Screen parent) { super(parent, "waypoints"); }
    @Override protected void init() {
        super.init(); rows = Math.max(1, (panelHeight - 150) / 34);
        int x = panelX + 14, w = panelWidth - 28;
        field(x, panelY + 37, w / 2, "search", search, 128, value -> { search = value; page = 0; });
        button(x + w / 2 + 6, panelY + 36, w / 2 - 6, group == null ? MapTexts.text("all_groups") : group.isEmpty() ? MapTexts.text("ungrouped") : net.minecraft.network.chat.Component.literal(group), () -> {
            List<String> groups = MapClient.waypoints().stream().map(MapWaypoint::group).distinct().sorted().toList();
            int index = group == null ? -1 : groups.indexOf(group); group = index + 1 >= groups.size() ? null : groups.get(index + 1); page = 0; init();
        });
        int bw = (w - 18) / 4, controls = panelY + panelHeight - 84;
        button(x, controls, bw, MapTexts.text("previous"), () -> page = Math.max(0, page - 1));
        button(x + bw + 6, controls, bw, MapTexts.text("rename_group"), () -> {
            var ids = filtered().stream().filter(p -> !p.shared()).map(MapWaypoint::id).collect(Collectors.toSet());
            Minecraft.getInstance().gui.setScreen(new MapTextScreen(this, "rename_group", group == null ? "" : group, 32, value -> { MapPersonalData.group(ids, value); group = value; }));
        });
        button(x + (bw + 6) * 2, controls, bw, MapTexts.text("toggle_group"), () -> {
            var points = filtered().stream().filter(p -> !p.shared()).toList();
            MapPersonalData.setEnabled(points.stream().map(MapWaypoint::id).collect(Collectors.toSet()), points.stream().anyMatch(p -> !p.enabled()));
        });
        button(x + (bw + 6) * 3, controls, bw, MapTexts.text("next"), () -> page = Math.min(maxPage(), page + 1));
        int footer = panelY + panelHeight - 34;
        button(x, footer, bw, MapTexts.text("add"), () -> {
            var player = Minecraft.getInstance().player;
            if (player != null) Minecraft.getInstance().gui.setScreen(new MapWaypointEditScreen(this, MapClient.newPoint(MapClient.dimension(), player.getBlockX(), player.getBlockY(), player.getBlockZ()), false));
        });
        button(x + bw + 6, footer, bw, MapTexts.text("import"), () -> Minecraft.getInstance().gui.setScreen(new MapImportScreen(this)));
        button(x + (bw + 6) * 2, footer, bw, MapTexts.text("copy_list"), () -> { Minecraft.getInstance().keyboardHandler.setClipboard(MapTransfer.encode(filtered())); error = MapTexts.text("copied"); });
        button(x + (bw + 6) * 3, footer, bw, MapTexts.text("done"), this::onClose);
    }
    private List<MapWaypoint> filtered() {
        String query = search.toLowerCase(Locale.ROOT);
        return MapClient.waypoints().stream().filter(p -> group == null || group.equals(p.group()))
                .filter(p -> (p.name() + " " + p.group() + " " + p.dimension()).toLowerCase(Locale.ROOT).contains(query))
                .sorted(java.util.Comparator.comparing(MapWaypoint::group).thenComparing(MapWaypoint::name)).toList();
    }
    private int maxPage() { return Math.max(0, (filtered().size() - 1) / rows); }
    @Override protected void content(GuiGraphicsExtractor g, int mx, int my, float delta) {
        var points = filtered(); page = Math.min(page, maxPage());
        for (int row = 0; row < rows && page * rows + row < points.size(); row++) {
            var point = points.get(page * rows + row); int x = panelX + 14, y = panelY + 65 + row * 34, w = panelWidth - 28;
            YzuiTheme.button(g, x, y, w, 30, mx >= x && mx < x + w && my >= y && my < y + 30 ? 1 : 0, false, true, 1,
                    YzuiTheme.ButtonStyle.TONAL);
            g.fill(x + 5, y + 6, x + 8, y + 24, point.color());
            label(g, MapTexts.waypoint(point).copy().append(point.shared() ? "  ▣" : point.enabled() ? "  ●" : "  ○"), x + 14, y + 4, w - 24);
            String detail = point.x() + " / " + point.y() + " / " + point.z() + " · " + MapTexts.dimension(point.dimension()).getString()
                    + (point.group().isEmpty() ? "" : " · " + point.group());
            YzuiTheme.label(g, font, net.minecraft.network.chat.Component.literal(detail), x + 14, y + 17, w - 24, YzuiTheme.textMuted(), false);
        }
        if (points.isEmpty()) label(g, MapTexts.text("no_points"), panelX + 16, panelY + 77, panelWidth - 32);
        label(g, MapTexts.text("page", page + 1, maxPage() + 1), panelX + panelWidth - 82, panelY + 14, 68);
    }
    @Override public boolean mouseClicked(MouseButtonEvent event, boolean actual) {
        if (super.mouseClicked(event, actual)) return true;
        if (event.button() != 0 || event.x() < panelX + 14 || event.x() >= panelX + panelWidth - 14) return false;
        int row = (int) (event.y() - panelY - 65) / 34;
        if (event.y() < panelY + 65 || row < 0 || row >= rows) return false;
        var points = filtered(); int index = page * rows + row;
        if (index < points.size()) { Minecraft.getInstance().gui.setScreen(new MapWaypointActionsScreen(this, points.get(index))); return true; }
        return false;
    }
    @Override public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        page = Math.clamp(page + (vertical > 0 ? -1 : 1), 0, maxPage()); return true;
    }
}
