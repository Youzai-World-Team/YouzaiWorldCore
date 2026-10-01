package top.csituka.youzaiworldcore.client.screen.map;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import top.csituka.youzaiworldcore.client.map.MapClient;
import top.csituka.youzaiworldcore.client.map.MapPersonalData;
import top.csituka.youzaiworldcore.client.map.MapTexts;
import top.csituka.youzaiworldcore.client.map.MapTransfer;
import top.csituka.youzaiworldcore.client.render.YzuiTheme;
import top.csituka.youzaiworldcore.client.screen.widget.TransparentButton;
import top.csituka.youzaiworldcore.map.MapWaypoint;
import top.csituka.youzaiworldcore.network.MapActionPayload;
import top.csituka.youzaiworldcore.network.MapSessionPayload;
import top.csituka.youzaiworldcore.util.DebugLogger;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/** 全屏路径点管理：维度分类树、创建时间和坐标表格，以及悬停操作栏。私人分类存于客户端 map_module。 */
@SuppressWarnings("null")
final class MapWaypointPanel extends MapOverlayPanel {
    private enum Filter { ALL, ANCHORS, DEATH, STRUCTURE, SHARED, GROUP }
    private record Branch(String dimension, Filter filter, String group, Component label, boolean root) { }
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final String[] GROUPS = {"矿产", "战斗", "遗迹", "星标", "收集物", "建筑", "其他"};
    private static final String[] GROUP_KEYS = {"minerals", "combat", "ruins", "starred", "collectibles", "buildings", "other"};
    private final Set<String> expanded = new HashSet<>();
    private final List<TransparentButton> tableControls = new ArrayList<>(), treeControls = new ArrayList<>(), actions = new ArrayList<>();
    private List<MapWaypoint> snapshot = List.of(), visiblePoints = List.of();
    private String search = "", dimension = MapClient.dimension(), group = "";
    private Filter filter = Filter.ALL;
    private int page, rows, treeOffset, sidebar, tableX, tableWidth, listY, listBottom, rowHeight;
    private int actionX, actionY, actionWidth, actionSize;
    private UUID selected, actionId;
    private boolean dirty, pending, categoryEditing, categoryMove;
    private String categoryValue = "";
    private TransparentButton previous, next;

    /** 从地图或命令打开当前服务器存档的路径点管理页。 */
    public MapWaypointPanel(YzWorldMapScreen owner) {
        super(owner, "waypoints"); expanded.add(dimension);
    }

    @Override protected void init() {
        clearFocus(); super.init(); tableControls.clear(); treeControls.clear(); actions.clear(); actionId = null;
        panelX = 8; panelWidth = Math.min(width - 16, Math.max(304, width * 3 / 4)); panelY = 8; panelHeight = height - 16;
        if (categoryEditing) {
            field(panelX + 12, panelY + 52, panelWidth - 24, "new_category", categoryValue, 32, value -> categoryValue = value);
            int half = (panelWidth - 30) / 2;
            button(panelX + 12, panelY + panelHeight - 34, half, MapTexts.text("save"), () -> {
                if (!MapPersonalData.addCategory(dimension, categoryValue)) { error = MapTexts.text("limit"); return; }
                if (categoryMove) MapPersonalData.group(filtered().stream().filter(point -> !point.shared()).map(MapWaypoint::id).collect(Collectors.toSet()), categoryValue);
                group = categoryValue.strip(); filter = Filter.GROUP; expanded.add(dimension); page = 0; categoryEditing = false; init();
            });
            button(panelX + 18 + half, panelY + panelHeight - 34, half, MapTexts.text("cancel"), () -> { categoryEditing = false; init(); });
            return;
        }
        sidebar = Math.clamp(panelWidth / 3, 94, 260);
        tableX = panelX + sidebar + 12; tableWidth = panelWidth - sidebar - 22;
        listY = panelY + 65; listBottom = panelY + panelHeight - 58;
        rowHeight = tableWidth < 340 ? 34 : 24;
        rows = Math.max(1, (listBottom - listY) / rowHeight);
        field(tableX, panelY + 11, tableWidth - 30, "search", search, 128, value -> { search = value; page = 0; dirty = true; });
        button(panelX + panelWidth - 30, panelY + 10, 22, Component.literal("×"), this::onClose).setTooltip(Tooltip.create(MapTexts.text("done")));
        int footer = panelY + panelHeight - 32;
        int small = (sidebar - 14) / 3;
        control(panelX + 6, footer, small, "+", "new_category", this::newCategory);
        control(panelX + 8 + small, footer, small, "✎", "rename_group", this::moveCategory);
        control(panelX + 10 + small * 2, footer, small, "◉", "toggle_group", () -> {
            var points = filtered().stream().filter(point -> !point.shared()).toList();
            MapPersonalData.setEnabled(points.stream().map(MapWaypoint::id).collect(Collectors.toSet()), points.stream().anyMatch(point -> !point.enabled()));
            dirty = true;
        });
        int bw = (tableWidth - 16) / 5;
        previous = control(tableX, footer, bw, "←", "previous", () -> { page--; dirty = true; });
        control(tableX + bw + 4, footer, bw, "+", "new_point", () -> {
            var player = Minecraft.getInstance().player;
            if (player == null) return;
            var point = MapClient.newPoint(MapClient.dimension(), player.getBlockX(), player.getBlockY(), player.getBlockZ());
            if (dimension.equals(MapClient.dimension()) && filter == Filter.GROUP) point = point.withGroup(group);
            owner.openPoint(point, true);
        });
        control(tableX + (bw + 4) * 2, footer, bw, "↓", "import", () -> Minecraft.getInstance().gui.setScreen(new MapImportScreen(owner)));
        control(tableX + (bw + 4) * 3, footer, bw, "▣", "copy_list", () -> {
            Minecraft.getInstance().keyboardHandler.setClipboard(MapTransfer.encode(filtered())); error = MapTexts.text("copied");
        });
        next = control(tableX + (bw + 4) * 4, footer, bw, "→", "next", () -> { page++; dirty = true; });
        refresh();
        DebugLogger.debug("MapWaypoints", "打开路径点分类表：%s", dimension);
    }

    private TransparentButton control(int x, int y, int width, String symbol, String key, Runnable action) {
        var button = button(x, y, width, Component.literal(symbol), action);
        button.setTooltip(Tooltip.create(MapTexts.text(key))); return button;
    }

    private static Component groupLabel(String name) {
        for (int i = 0; i < GROUPS.length; i++) if (GROUPS[i].equals(name)) return MapTexts.text("category." + GROUP_KEYS[i]);
        return name.isEmpty() ? MapTexts.text("ungrouped") : Component.literal(name);
    }

    private boolean matches(MapWaypoint point, String dimension, Filter filter, String group) {
        if (!point.dimension().equals(dimension)) return false;
        return switch (filter) {
            case ALL -> true;
            case ANCHORS -> MapClient.isAnchor(point);
            case DEATH -> point.kind() == MapWaypoint.Kind.DEATH;
            case STRUCTURE -> point.kind() == MapWaypoint.Kind.STRUCTURE;
            case SHARED -> point.shared() && !MapClient.isAnchor(point);
            case GROUP -> point.group().equals(group) && !MapClient.isAnchor(point);
        };
    }

    private List<MapWaypoint> filtered() {
        String query = search.toLowerCase(Locale.ROOT);
        return snapshot.stream().filter(point -> matches(point, dimension, filter, group))
                .filter(point -> (MapTexts.waypoint(point).getString() + " " + groupLabel(point.group()).getString() + " " + point.dimension()).toLowerCase(Locale.ROOT).contains(query)).toList();
    }

    private int maxPage() { return Math.max(0, (filtered().size() - 1) / rows); }

    private void refresh() {
        snapshot = MapClient.waypoints(); dirty = false;
        for (var button : tableControls) removeWidget(button); tableControls.clear();
        clearActions(); rebuildTree();
        var points = filtered(); page = Math.clamp(page, 0, maxPage());
        visiblePoints = points.subList(Math.min(page * rows, points.size()), Math.min((page + 1) * rows, points.size()));
        for (int i = 0; i < visiblePoints.size(); i++) tableControls.add(addRenderableWidget(new PointRow(visiblePoints.get(i), listY + i * rowHeight)));
        previous.active = page > 0; next.active = page < maxPage();
        if (selected != null && visiblePoints.stream().anyMatch(point -> point.id().equals(selected))) showActions(selected);
    }

    private void rebuildTree() {
        for (var button : treeControls) removeWidget(button); treeControls.clear();
        var branches = new ArrayList<Branch>();
        var dimensions = new LinkedHashSet<>(MapClient.dimensions()); dimensions.add(dimension);
        snapshot.forEach(point -> dimensions.add(point.dimension()));
        for (String dim : dimensions) {
            branches.add(new Branch(dim, Filter.ALL, "", MapTexts.dimension(dim), true));
            if (!expanded.contains(dim)) continue;
            branches.add(new Branch(dim, Filter.ANCHORS, "", MapTexts.text("activated_anchors"), false));
            branches.add(new Branch(dim, Filter.DEATH, "", MapTexts.text("deaths"), false));
            branches.add(new Branch(dim, Filter.STRUCTURE, "", MapTexts.text("category.structures"), false));
            branches.add(new Branch(dim, Filter.SHARED, "", MapTexts.text("shared_point"), false));
            var groups = new LinkedHashSet<>(List.of(GROUPS));
            groups.addAll(MapPersonalData.categories(dim));
            snapshot.stream().filter(point -> point.dimension().equals(dim) && !MapClient.isAnchor(point)).forEach(point -> groups.add(point.group()));
            for (String name : groups) branches.add(new Branch(dim, Filter.GROUP, name, groupLabel(name), false));
        }
        int capacity = Math.max(1, (listBottom - (panelY + 43)) / 22);
        treeOffset = Math.clamp(treeOffset, 0, Math.max(0, branches.size() - capacity));
        for (int i = 0; i < capacity && treeOffset + i < branches.size(); i++) {
            var branch = branches.get(treeOffset + i);
            long count = snapshot.stream().filter(point -> matches(point, branch.dimension(), branch.filter(), branch.group())).count();
            String prefix = branch.root() ? (expanded.contains(branch.dimension()) ? "⌃ " : "> ") : "  ";
            if (branch.root() && branch.dimension().equals(MapClient.dimension())) prefix += "● ";
            var label = Component.literal(prefix).append(branch.label()).append(" (" + count + ")");
            var button = button(panelX + 6, panelY + 43 + i * 22, sidebar - 8, label, () -> {
                dimension = branch.dimension(); filter = branch.filter(); group = branch.group(); page = 0; selected = null;
                if (branch.root()) { if (!expanded.add(dimension)) expanded.remove(dimension); }
                dirty = true;
            });
            button.setHeight(21); button.setTextLeftAligned(true); button.setTextInsets(4, 4); button.setTooltip(Tooltip.create(label));
            if (dimension.equals(branch.dimension()) && filter == branch.filter() && group.equals(branch.group())) button.setStyle(YzuiTheme.ButtonStyle.FILLED);
            treeControls.add(button);
        }
    }

    private void newCategory() { categoryEditing = true; categoryMove = false; categoryValue = ""; init(); }
    private void moveCategory() { categoryEditing = true; categoryMove = true; categoryValue = filter == Filter.GROUP ? group : ""; init(); }
    @Override public void onClose() { if (categoryEditing) { categoryEditing = false; init(); } else super.onClose(); }

    @Override public void tick() { if (!categoryEditing && (dirty || !snapshot.equals(MapClient.waypoints()))) refresh(); }

    @Override protected void content(GuiGraphicsExtractor g, int mx, int my, float delta) {
        if (categoryEditing) { label(g, MapTexts.text(categoryMove ? "rename_group" : "new_category"), panelX + 12, panelY + 36, panelWidth - 24); return; }
        g.fill(panelX + sidebar + 3, panelY + 40, panelX + sidebar + 4, listBottom, YzuiTheme.outlineVariant());
        label(g, MapTexts.text("name"), tableX + 6, panelY + 45, nameWidth());
        if (tableWidth >= 340) label(g, MapTexts.text("created_at"), tableX + nameWidth(), panelY + 45, dateWidth());
        for (int i = 0; i < 3; i++) label(g, Component.literal("XYZ".substring(i, i + 1)), tableX + tableWidth - coordinateWidth() * (3 - i), panelY + 45, coordinateWidth());
        g.fill(tableX, listY - 4, tableX + tableWidth, listY - 3, YzuiTheme.outlineVariant());
        if (visiblePoints.isEmpty()) label(g, MapTexts.text("no_points"), tableX + 5, listY + 8, tableWidth - 10);
        if (error.getString().isEmpty()) label(g, MapTexts.text("page", page + 1, maxPage() + 1), tableX, panelY + panelHeight - 48, tableWidth);
        if (!overActions(mx, my)) {
            int row = (my - listY) / rowHeight;
            if (mx >= tableX && mx < tableX + tableWidth && my >= listY && row >= 0 && row < visiblePoints.size()) showActions(visiblePoints.get(row).id());
        }
    }

    private int coordinateWidth() { return Math.clamp(tableWidth / 9, 28, 60); }
    private int dateWidth() { return tableWidth >= 340 ? 126 : 0; }
    private int nameWidth() { return tableWidth - coordinateWidth() * 3 - dateWidth(); }
    private static String date(MapWaypoint point) {
        return point.createdAt() <= 0 ? "—" : DATE.format(Instant.ofEpochMilli(point.createdAt()).atZone(ZoneId.systemDefault()));
    }

    private final class PointRow extends TransparentButton {
        private final MapWaypoint point;
        private PointRow(MapWaypoint point, int y) {
            super(tableX, y, tableWidth, rowHeight - 2, MapTexts.waypoint(point), () -> { selected = point.id(); showActions(point.id()); });
            this.point = point;
            setTooltip(Tooltip.create(MapTexts.waypoint(point).copy().append(" · " + date(point) + "\n" + point.x() + " / " + point.y() + " / " + point.z())));
        }
        @Override protected void extractWidgetRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
            boolean chosen = point.id().equals(selected);
            YzuiTheme.button(g, getX(), getY(), getWidth(), getHeight(), isMouseOver(mx, my) ? 1 : 0, isFocused(), true, 1,
                    chosen ? YzuiTheme.ButtonStyle.FILLED : YzuiTheme.ButtonStyle.TEXT);
            int color = chosen ? YzuiTheme.onPrimary() : point.enabled() ? YzuiTheme.text() : YzuiTheme.textMuted();
            g.fill(getX() + 2, getY() + 5, getX() + 4, getY() + 14, point.color());
            YzuiTheme.label(g, font, MapTexts.waypoint(point), getX() + 8, getY() + 6, nameWidth() - 12, color, false);
            if (dateWidth() > 0) YzuiTheme.label(g, font, Component.literal(date(point)), getX() + nameWidth(), getY() + 6, dateWidth() - 4, color, false);
            else YzuiTheme.label(g, font, Component.literal(date(point)), getX() + 8, getY() + 21, getWidth() - 16, color, false);
            int[] coordinates = {point.x(), point.y(), point.z()};
            for (int i = 0; i < 3; i++) YzuiTheme.label(g, font, Component.literal(Integer.toString(coordinates[i])),
                    getX() + getWidth() - coordinateWidth() * (3 - i), getY() + 6, coordinateWidth() - 3, color, false);
        }
    }

    private void clearActions() {
        for (var button : actions) removeWidget(button); actions.clear(); actionId = null;
    }

    private void showActions(UUID id) {
        if (id.equals(actionId)) return;
        clearActions();
        var point = visiblePoints.stream().filter(value -> value.id().equals(id)).findFirst().orElse(null);
        if (point == null) return;
        actionId = id; actionSize = Math.min(22, tableWidth / 9); actionWidth = actionSize * 9;
        actionX = tableX + tableWidth - actionWidth;
        int row = visiblePoints.indexOf(point);
        actionY = listY + row * rowHeight + (rowHeight >= 34 ? 10 : 0);
        var background = new TransparentButton(actionX - 2, actionY - 2, actionWidth + 4, 26, Component.empty(), () -> { }) {
            @Override protected void extractWidgetRenderState(GuiGraphicsExtractor g, int x, int y, float delta) {
                g.nextStratum(); YzuiTheme.card(g, getX(), getY(), getWidth(), getHeight());
            }
        };
        actions.add(addRenderableWidget(background));
        action(0, "⌖", "locate", () -> owner.locatePoint(point), true);
        var session = MapClient.session();
        action(1, "↗", "teleport", () -> request(MapActionPayload.Action.TELEPORT, point), session != null && session.allows(MapSessionPayload.TELEPORT));
        action(2, "↪", "share_chat", () -> MapTransfer.share(point), true);
        action(3, "▣", "copy", () -> { Minecraft.getInstance().keyboardHandler.setClipboard(MapTransfer.coordinate(point)); error = MapTexts.text("copied"); }, true);
        var privatePoints = filtered().stream().filter(value -> !value.shared()).toList(); int index = privatePoints.indexOf(point);
        action(4, "↑", "move_up", () -> { MapPersonalData.swap(point.id(), privatePoints.get(index - 1).id()); dirty = true; }, index > 0);
        action(5, "↓", "move_down", () -> { MapPersonalData.swap(point.id(), privatePoints.get(index + 1).id()); dirty = true; }, index >= 0 && index + 1 < privatePoints.size());
        action(6, "◉", point.enabled() ? "hide_point" : "show_point", () -> { MapPersonalData.put(point.withEnabled(!point.enabled())); dirty = true; }, !point.shared());
        action(7, "✎", "edit_point", () -> owner.openPoint(point, false), editable(point));
        action(8, "×", "delete", () -> { if (point.shared()) request(MapActionPayload.Action.DELETE, point); else { MapPersonalData.remove(point.id()); selected = null; dirty = true; } }, editable(point));
    }

    private void action(int index, String symbol, String key, Runnable run, boolean enabled) {
        var button = control(actionX + index * actionSize, actionY, actionSize, symbol, key, run);
        button.active = enabled && !pending; button.setTextInsets(0, 0); actions.add(button);
    }

    private boolean editable(MapWaypoint point) {
        if (MapClient.isAnchor(point)) return false;
        if (!point.shared()) return true;
        var session = MapClient.session(); var player = Minecraft.getInstance().player;
        return session != null && (session.allows(MapSessionPayload.MANAGE) || session.allows(MapSessionPayload.PUBLISH)
                && player != null && point.owner().equals(player.getUUID()) && point.kind() == MapWaypoint.Kind.NORMAL);
    }

    private void request(MapActionPayload.Action action, MapWaypoint point) {
        pending = MapClient.send(action, point, (success, key) -> { pending = false; error = MapTexts.text(key); dirty = true; });
        if (pending) error = MapTexts.text("request_sent");
        dirty = true;
    }

    private boolean overActions(double x, double y) {
        return actionId != null && x >= actionX - 2 && x < actionX + actionWidth + 2 && y >= actionY - 2 && y < actionY + 24;
    }

    @Override public boolean mouseClicked(MouseButtonEvent event, boolean actual) {
        // 操作栏浮在当前行内容之上，输入也必须优先分发给它。
        if (overActions(event.x(), event.y())) {
            for (int i = actions.size() - 1; i >= 0; i--) if (actions.get(i).mouseClicked(event, actual)) return true;
            return true;
        }
        if (!categoryEditing && event.button() == 1 && event.x() >= tableX && event.x() < tableX + tableWidth && event.y() >= listY) {
            int row = (int) (event.y() - listY) / rowHeight;
            if (row >= 0 && row < visiblePoints.size()) {
                owner.openPoint(visiblePoints.get(row), false); return true;
            }
        }
        return super.mouseClicked(event, actual);
    }

    @Override public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        if (categoryEditing || vertical == 0 || y < panelY + 40 || y >= listBottom) return false;
        if (x >= panelX && x < panelX + sidebar) { treeOffset = Math.max(0, treeOffset + (vertical > 0 ? -1 : 1)); dirty = true; return true; }
        if (x >= tableX && x < tableX + tableWidth) { page = Math.clamp(page + (vertical > 0 ? -1 : 1), 0, maxPage()); dirty = true; return true; }
        return false;
    }
}
