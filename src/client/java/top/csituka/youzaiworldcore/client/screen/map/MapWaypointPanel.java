package top.csituka.youzaiworldcore.client.screen.map;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import top.csituka.youzaiworldcore.client.map.MapClient;
import top.csituka.youzaiworldcore.client.map.MapCoordinates;
import top.csituka.youzaiworldcore.client.map.MapGroupIcons;
import top.csituka.youzaiworldcore.client.map.MapPersonalData;
import top.csituka.youzaiworldcore.client.map.MapTexts;
import top.csituka.youzaiworldcore.client.render.YzuiTheme;
import top.csituka.youzaiworldcore.client.screen.widget.TransparentButton;
import top.csituka.youzaiworldcore.map.MapWaypoint;
import top.csituka.youzaiworldcore.map.MapWaypointGroup;
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

/** 路径点管理：同页维度树、固定图标分组与卡片列表，私人分组选择存入客户端地图 SQLite。 */
@SuppressWarnings("null")
final class MapWaypointPanel extends MapOverlayPanel {
    private enum Filter { ALL, ANCHORS, SHARED, GROUP }
    private enum View { LIST, ACTIONS, CATEGORY, RENAME }
    private record Branch(String dimension, Filter filter, MapWaypointGroup group, Component label, boolean root, boolean last) { }
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private final Set<String> expanded = new HashSet<>();
    private final List<TransparentButton> rows = new ArrayList<>(), branches = new ArrayList<>(), actions = new ArrayList<>();
    private List<MapWaypoint> snapshot = List.of(), matches = List.of(), visiblePoints = List.of();
    private String query = "", dimension, groupNameValue = "";
    private MapWaypointGroup group = MapWaypointGroup.OTHER, categoryValue = MapWaypointGroup.OTHER;
    private Filter filter = Filter.ALL;
    private View view = View.LIST;
    private int page, capacity, treeOffset, treeCapacity, treeCount, sidebar, treeX, treeTop, treeBottom,
            listX, listWidth, listTop, listBottom, rowHeight, footer, actionTop;
    private UUID selected;
    private boolean compact, narrow, dirty, pending, deleteArmed;
    private EditBox searchBox;
    private TransparentButton previous, next, renameGroupButton;

    public MapWaypointPanel(YzWorldMapScreen owner) {
        super(owner, "waypoints"); dimension = owner.mapDimension(); expanded.add(dimension);
        DebugLogger.debug("MapWaypoints", "打开路径点分组树管理器：%s", dimension);
    }

    @Override protected void init() {
        boolean searchFocused = searchBox != null && getFocused() == searchBox;
        super.init(); rows.clear(); branches.clear(); actions.clear(); searchBox = null;
        panelWidth = Math.min(width - 16, Math.max(520, Math.min(1040, width * 7 / 8)));
        compact = panelHeight < 340; narrow = panelWidth < 480;
        footer = panelY + panelHeight - 32; snapshot = MapClient.waypoints();
        if (view == View.ACTIONS && selectedPoint() == null) view = View.LIST;
        control(panelX + panelWidth - 32, panelY + 7, 22, Component.literal("×"), "done", this::onClose);
        if (view == View.CATEGORY) { buildCategory(); return; }
        if (view == View.RENAME) { buildRenameGroup(); return; }
        if (view == View.ACTIONS) { buildActions(); return; }
        sidebar = Math.clamp((panelWidth - 24) / 3, 116, 260);
        treeX = panelX + 12;
        listX = treeX + sidebar + 14; listWidth = panelWidth - 24 - sidebar - 14;
        int searchY = panelY + (compact ? 36 : 48);
        int newWidth = narrow ? 24 : 76;
        renameGroupButton = control(treeX + sidebar - 24, searchY - 1, 24, Component.literal("✎"),
                "rename_group_name", this::renameGroup);
        searchBox = field(listX, searchY, listWidth - newWidth - 6, "search", query, 128, value -> { query = value; page = 0; dirty = true; });
        searchBox.setHint(MapTexts.text("search"));
        control(listX + listWidth - newWidth, searchY - 1, newWidth, narrow ? Component.literal("+") : MapTexts.text("new_point"), "new_point", this::newPoint);
        listTop = searchY + 30; actionTop = footer - 50; listBottom = actionTop - 6;
        treeTop = listTop; treeBottom = footer - 38;
        rowHeight = compact ? 34 : 58; capacity = Math.max(1, (listBottom - listTop) / rowHeight);
        previous = control(listX, footer, 24, Component.literal("‹"), "previous", () -> { page--; refresh(); });
        next = control(listX + listWidth - 24, footer, 24, Component.literal("›"), "next", () -> { page++; refresh(); });
        categoryButtons(treeX, footer - 28, sidebar);
        refresh(); if (searchFocused) setFocused(searchBox);
    }

    private TransparentButton control(int x, int y, int width, Component label, String key, Runnable action) {
        var button = button(x, y, width, label, action); button.setTooltip(Tooltip.create(MapTexts.text(key))); return button;
    }
    @Override protected void renderError(GuiGraphicsExtractor g) {
        if (view == View.LIST) label(g, error, listX, footer - 12, listWidth);
        else super.renderError(g);
    }
    private boolean matches(MapWaypoint point, String dim, Filter kind, MapWaypointGroup category) {
        if (!point.dimension().equals(dim)) return false;
        return switch (kind) {
            case ALL -> true;
            case ANCHORS -> MapClient.isAnchor(point);
            case SHARED -> point.shared() && !MapClient.isAnchor(point);
            case GROUP -> MapWaypointGroup.of(point) == category && !MapClient.isAnchor(point);
        };
    }
    private List<MapWaypoint> filtered() {
        String search = query.strip().toLowerCase(Locale.ROOT);
        return snapshot.stream().filter(point -> matches(point, dimension, filter, group))
                .filter(point -> (MapTexts.waypoint(point).getString() + " " + MapGroupIcons.label(MapWaypointGroup.of(point)).getString() + " "
                        + point.dimension() + " " + point.x() + " " + point.y() + " " + point.z()).toLowerCase(Locale.ROOT).contains(search)).toList();
    }
    private List<MapWaypoint> privateMatches() { return filtered().stream().filter(p -> !p.shared() && !MapClient.isAnchor(p)).toList(); }
    private MapWaypoint selectedPoint() { return snapshot.stream().filter(point -> point.id().equals(selected)).findFirst().orElse(null); }
    private int maxPage() { return Math.max(0, (matches.size() - 1) / capacity); }

    private void refresh() {
        snapshot = MapClient.waypoints(); dirty = false; matches = filtered(); page = Math.clamp(page, 0, maxPage());
        if (selected != null && matches.stream().noneMatch(point -> point.id().equals(selected))) selected = null;
        for (var row : rows) removeWidget(row); rows.clear();
        visiblePoints = matches.subList(Math.min(page * capacity, matches.size()), Math.min((page + 1) * capacity, matches.size()));
        for (int i = 0; i < visiblePoints.size(); i++) rows.add(addRenderableWidget(new PointRow(visiblePoints.get(i), listTop + i * rowHeight)));
        previous.active = page > 0; next.active = page < maxPage();
        renameGroupButton.active = filter == Filter.GROUP;
        buildTree();
        refreshSelection();
    }
    private void refreshSelection() {
        for (var action : actions) removeWidget(action); actions.clear();
        var point = selectedPoint(); if (point == null) return;
        int cell = (listWidth - 12) / 4;
        String[] keys = {"locate", point.id().equals(MapClient.navigation()) ? "stop_navigation" : "navigate", "point_actions", "manager_more"};
        Runnable[] runs = {() -> owner.locatePoint(point), () -> { MapClient.navigate(point.id().equals(MapClient.navigation()) ? null : point.id()); refreshSelection(); },
                () -> owner.openPoint(point, false), () -> { view = View.ACTIONS; deleteArmed = false; init(); }};
        String[] symbols = {"◎", "➜", "≡", "⋯"};
        for (int i = 0; i < keys.length; i++) actions.add(control(listX + i * (cell + 4), actionTop + 14, cell,
                narrow ? Component.literal(symbols[i]) : MapTexts.text(keys[i]), keys[i], runs[i]));
    }
    private void buildTree() {
        for (var button : branches) removeWidget(button); branches.clear();
        var entries = new ArrayList<Branch>();
        var dimensions = new LinkedHashSet<>(MapClient.dimensions()); dimensions.add(dimension); snapshot.forEach(point -> dimensions.add(point.dimension()));
        for (String dim : dimensions) {
            entries.add(new Branch(dim, Filter.ALL, MapWaypointGroup.OTHER, MapTexts.dimension(dim), true, false));
            if (!expanded.contains(dim)) continue;
            entries.add(new Branch(dim, Filter.ANCHORS, MapWaypointGroup.OTHER, MapTexts.text("activated_anchors"), false, false));
            entries.add(new Branch(dim, Filter.SHARED, MapWaypointGroup.OTHER, MapTexts.text("shared_point"), false, false));
            for (var value : MapWaypointGroup.ALL) entries.add(new Branch(dim, Filter.GROUP, value,
                    MapGroupIcons.label(value), false, value == MapWaypointGroup.ALL.getLast()));
        }
        treeCount = entries.size(); treeCapacity = Math.max(1, (treeBottom - treeTop) / 24);
        treeOffset = Math.clamp(treeOffset, 0, Math.max(0, treeCount - treeCapacity));
        for (int i = 0; i < treeCapacity && treeOffset + i < entries.size(); i++) {
            var branch = entries.get(treeOffset + i);
            long count = snapshot.stream().filter(p -> matches(p, branch.dimension(), branch.filter(), branch.group())).count();
            branches.add(addRenderableWidget(new BranchRow(branch, count, treeTop + i * 24)));
        }
    }
    private void selectBranch(Branch branch) {
        dimension = branch.dimension(); filter = branch.filter(); group = branch.group(); selected = null; page = 0;
        DebugLogger.debug("MapWaypoints", "选择分组：%s / %s / %s", dimension, filter, group);
        refresh();
    }
    private void toggleBranch(Branch branch) {
        if (!expanded.add(branch.dimension())) expanded.remove(branch.dimension());
        DebugLogger.debug("MapWaypoints", "分组展开：%s = %s", branch.dimension(), expanded.contains(branch.dimension()));
        buildTree();
        for (var row : branches) if (row instanceof BranchRow treeRow && treeRow.branch.equals(branch)) { setFocused(row); break; }
    }
    private void categoryButtons(int x, int y, int width) {
        control(x, y, width, MapTexts.text("rename_group"), "rename_group", this::editCategory);
        control(x, y + 28, width, MapTexts.text("toggle_group"), "toggle_group", () -> {
            var points = privateMatches();
            MapPersonalData.setEnabled(points.stream().map(MapWaypoint::id).collect(Collectors.toSet()), points.stream().anyMatch(p -> !p.enabled())); dirty = true;
        });
    }
    private void editCategory() { categoryValue = filter == Filter.GROUP ? group : MapWaypointGroup.OTHER; view = View.CATEGORY; error = Component.empty(); init(); }
    private void buildCategory() {
        MapGroupButton.grid(panelX + 12, panelY + 82, panelWidth - 24, () -> categoryValue,
                value -> categoryValue = value, this::addRenderableWidget, !pending);
        int half = (panelWidth - 30) / 2;
        button(panelX + 12, footer, half, MapTexts.text("save"), () -> {
            MapPersonalData.group(privateMatches().stream().map(MapWaypoint::id).collect(Collectors.toSet()), categoryValue);
            group = categoryValue; filter = Filter.GROUP; expanded.add(dimension); page = 0; view = View.LIST; error = Component.empty(); init();
        }).setStyle(YzuiTheme.ButtonStyle.FILLED);
        button(panelX + 18 + half, footer, half, MapTexts.text("cancel"), () -> { view = View.LIST; init(); });
    }
    private void renameGroup() {
        if (filter != Filter.GROUP) return;
        groupNameValue = MapGroupIcons.label(group).getString(); view = View.RENAME; error = Component.empty(); init();
    }
    private void buildRenameGroup() {
        var input = field(panelX + 12, panelY + 82, panelWidth - 24, "group_name", groupNameValue, 32,
                value -> groupNameValue = value);
        setFocused(input);
        button(panelX + 12, panelY + 112, panelWidth - 24, MapTexts.text("reset_group_name"), () -> saveGroupName(""));
        int half = (panelWidth - 30) / 2;
        button(panelX + 12, footer, half, MapTexts.text("save"), () -> {
            if (groupNameValue.isBlank()) { error = MapTexts.text("invalid_text"); return; }
            saveGroupName(groupNameValue);
        }).setStyle(YzuiTheme.ButtonStyle.FILLED);
        button(panelX + 18 + half, footer, half, MapTexts.text("cancel"), () -> { view = View.LIST; error = Component.empty(); init(); });
    }
    private void saveGroupName(String value) {
        if (!MapPersonalData.setGroupName(group, value)) { error = MapTexts.text("invalid_text"); return; }
        view = View.LIST; error = MapTexts.text("saved"); init();
    }
    private void newPoint() {
        var player = Minecraft.getInstance().player; if (player == null) return;
        var point = MapClient.newPoint(MapClient.dimension(), player.getBlockX(), player.getBlockY(), player.getBlockZ());
        if (dimension.equals(MapClient.dimension()) && filter == Filter.GROUP) point = point.withGroup(group);
        owner.openPoint(point, true);
    }
    private void buildActions() {
        var point = selectedPoint(); int cell = (panelWidth - 30) / 2, y = panelY + 60;
        var privatePoints = privateMatches(); int index = privatePoints.indexOf(point); var session = MapClient.session();
        action(0, y, cell, "locate", () -> owner.locatePoint(point), true);
        action(1, y, cell, point.id().equals(MapClient.navigation()) ? "stop_navigation" : "navigate", () -> { MapClient.navigate(point.id().equals(MapClient.navigation()) ? null : point.id()); init(); }, true);
        action(2, y, cell, "teleport", () -> request(MapActionPayload.Action.TELEPORT, point), session != null && session.allows(MapSessionPayload.TELEPORT));
        action(3, y, cell, "copy", () -> { Minecraft.getInstance().keyboardHandler.setClipboard(MapCoordinates.coordinate(point)); error = MapTexts.text("copied"); }, true);
        action(4, y, cell, "share_chat", () -> MapCoordinates.share(point), true);
        action(5, y, cell, point.enabled() ? "hide_point" : "show_point", () -> { MapPersonalData.put(point.withEnabled(!point.enabled())); init(); }, !point.shared() && !MapClient.isAnchor(point));
        action(6, y, cell, "move_up", () -> { MapPersonalData.swap(point.id(), privatePoints.get(index - 1).id()); init(); }, index > 0);
        action(7, y, cell, "move_down", () -> { MapPersonalData.swap(point.id(), privatePoints.get(index + 1).id()); init(); }, index >= 0 && index + 1 < privatePoints.size());
        action(8, y, cell, "edit_point", () -> owner.openPoint(point, false), editable(point));
        action(9, y, cell, deleteArmed ? "confirm_delete" : "delete", () -> {
            if (!deleteArmed) { deleteArmed = true; init(); return; }
            if (point.shared()) request(MapActionPayload.Action.DELETE, point); else { MapPersonalData.remove(point.id()); selected = null; view = View.LIST; init(); }
        }, editable(point));
        button(panelX + 12, footer, panelWidth - 24, MapTexts.text("manager_back"), () -> { view = View.LIST; init(); });
    }
    private void action(int index, int y, int width, String key, Runnable run, boolean enabled) {
        var button = control(panelX + 12 + index % 2 * (width + 6), y + index / 2 * 23, width, MapTexts.text(key), key, run); button.active = enabled && !pending;
    }
    private boolean editable(MapWaypoint point) {
        if (MapClient.isAnchor(point)) return false;
        if (!point.shared()) return true;
        var session = MapClient.session(); var player = Minecraft.getInstance().player;
        return session != null && (session.allows(MapSessionPayload.MANAGE) || session.allows(MapSessionPayload.PUBLISH)
                && player != null && point.owner().equals(player.getUUID()) && point.kind() == MapWaypoint.Kind.NORMAL);
    }
    private void request(MapActionPayload.Action action, MapWaypoint point) {
        if (pending) return;
        pending = MapClient.send(action, point, (success, key) -> { pending = false; error = MapTexts.text(key); dirty = true; });
        if (pending) error = MapTexts.text("request_sent"); init();
    }
    @Override public void onClose() { if (view != View.LIST) { view = View.LIST; error = Component.empty(); init(); } else super.onClose(); }
    @Override public void tick() {
        if (view != View.CATEGORY && view != View.RENAME && (dirty || !snapshot.equals(MapClient.waypoints()))) {
            if (view == View.LIST) refresh(); else { dirty = false; init(); }
        }
    }
    @Override protected void content(GuiGraphicsExtractor g, int mx, int my, float delta) {
        if (view == View.RENAME) {
            MapGroupIcons.render(g, group, panelX + 12, panelY + 42, 24, YzuiTheme.text());
            label(g, MapTexts.text("rename_group_name"), panelX + 44, panelY + 49, panelWidth - 56);
            label(g, MapTexts.text("group_name"), panelX + 12, panelY + 68, panelWidth - 24); return;
        }
        if (view == View.CATEGORY) {
            label(g, MapTexts.text("rename_group"), panelX + 12, panelY + 42, panelWidth - 24);
            label(g, MapTexts.text("waypoints_group_scope", privateMatches().size()), panelX + 12, panelY + 58, panelWidth - 24); return;
        }
        if (view == View.ACTIONS) { var point = selectedPoint(); if (point != null) label(g, MapTexts.waypoint(point), panelX + 12, panelY + 42, panelWidth - 24); return; }
        label(g, MapTexts.text("group"), treeX + 4, listTop - 24, sidebar - 32);
        g.fill(listX - 8, panelY + 36, listX - 7, footer + 22, YzuiTheme.outlineVariant());
        if (treeCount > treeCapacity) {
            int trackHeight = treeCapacity * 24, thumbHeight = Math.max(12, trackHeight * treeCapacity / treeCount);
            int thumbY = treeTop + (trackHeight - thumbHeight) * treeOffset / (treeCount - treeCapacity);
            g.fill(treeX + sidebar - 3, treeTop, treeX + sidebar - 1, treeTop + trackHeight, YzuiTheme.outlineVariant());
            g.fill(treeX + sidebar - 3, thumbY, treeX + sidebar - 1, thumbY + thumbHeight, YzuiTheme.primary());
        }
        if (visiblePoints.isEmpty()) label(g, MapTexts.text("no_points"), listX + 6, listTop + 12, listWidth - 12);
        var point = selectedPoint(); label(g, point == null ? MapTexts.text("manager_select") : MapTexts.waypoint(point), listX, actionTop, listWidth);
        var pageLabel = narrow ? Component.literal((page + 1) + " / " + (maxPage() + 1)) : MapTexts.text("page", page + 1, maxPage() + 1).copy().append(" · " + matches.size());
        YzuiTheme.label(g, font, pageLabel, listX + 28, footer + 7, listWidth - 56, YzuiTheme.textMuted(), true);
    }
    /** 连接线用几何绘制，不依赖字体的树形字符；滚动截断后仍保留连续的父级竖线。 */
    private final class BranchRow extends TransparentButton {
        private final Branch branch;
        private final String count;
        BranchRow(Branch branch, long count, int y) {
            super(treeX, y, sidebar - (treeCount > treeCapacity ? 6 : 0), 24, branch.label(), () -> selectBranch(branch));
            this.branch = branch; this.count = Long.toString(count);
            setTooltip(Tooltip.create(MapTexts.dimension(branch.dimension()).copy().append(" / ").append(branch.label()).append(" · " + count)));
        }
        @Override public void onClick(MouseButtonEvent event, boolean actual) {
            if (branch.root() && event.x() < getX() + 18) toggleBranch(branch);
            else super.onClick(event, actual);
        }
        @Override public boolean keyPressed(KeyEvent event) {
            if (isFocused() && branch.root() && (event.key() == InputConstants.KEY_LEFT || event.key() == InputConstants.KEY_RIGHT)) {
                if (expanded.contains(branch.dimension()) != (event.key() == InputConstants.KEY_RIGHT)) toggleBranch(branch);
                return true;
            }
            return super.keyPressed(event);
        }
        @Override protected void extractWidgetRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
            boolean chosen = dimension.equals(branch.dimension()) && filter == branch.filter() && group.equals(branch.group());
            var style = chosen ? YzuiTheme.ButtonStyle.FILLED : YzuiTheme.ButtonStyle.TEXT;
            int x = getX(), y = getY(), indent = branch.root() ? 0 : 20;
            YzuiTheme.button(g, x + indent, y + 1, getWidth() - indent, 22, isMouseOver(mx, my) ? 1 : 0, isFocused(), true, 1, style);
            int lineColor = YzuiTheme.textMuted(), textColor = chosen ? YzuiTheme.onPrimary() : YzuiTheme.text();
            if (branch.root()) {
                boolean open = expanded.contains(branch.dimension());
                int arrowColor = chosen ? YzuiTheme.onPrimary() : YzuiTheme.text();
                for (int i = 0; i < 4; i++) {
                    if (open) g.fill(x + 4 + i, y + 8 + i, x + 12 - i, y + 9 + i, arrowColor);
                    else g.fill(x + 5 + i, y + 7 + i, x + 6 + i, y + 15 - i, arrowColor);
                }
                if (open) g.fill(x + 8, y + 17, x + 10, y + 24, lineColor);
            } else {
                // 中间节点为 ├，最后一个子节点为 └；竖线与相邻行无缝连接。
                g.fill(x + 8, y, x + 10, y + (branch.last() ? 13 : 24), lineColor);
                g.fill(x + 8, y + 11, x + 20, y + 13, lineColor);
            }
            int textX = x + (branch.root() ? 19 : 24), countWidth = font.width(count);
            var name = branch.root() && branch.dimension().equals(MapClient.dimension()) && !narrow
                    ? Component.literal("◆ ").append(branch.label()) : branch.label();
            if (branch.filter() == Filter.GROUP) {
                MapGroupIcons.render(g, branch.group(), textX + 2, y + 4, 16, textColor);
                textX += 22;
            }
            YzuiTheme.label(g, font, name, textX, y + 8, getWidth() - (textX - x) - countWidth - 10, textColor, false);
            YzuiTheme.label(g, font, Component.literal(count), x + getWidth() - countWidth - 4, y + 8, countWidth, textColor, false);
        }
    }
    private final class PointRow extends TransparentButton {
        private final MapWaypoint point;
        PointRow(MapWaypoint point, int y) {
            super(listX, y, listWidth, rowHeight - 4, MapTexts.waypoint(point), () -> { selected = point.id(); refreshSelection(); }); this.point = point;
            setTooltip(Tooltip.create(MapTexts.waypoint(point).copy().append("\n").append(MapGroupIcons.label(MapWaypointGroup.of(point)))
                    .append("\n" + point.x() + " / " + point.y() + " / " + point.z() + "\n" + date(point))));
        }
        @Override protected void extractWidgetRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
            boolean chosen = point.id().equals(selected); var style = chosen ? YzuiTheme.ButtonStyle.FILLED : YzuiTheme.ButtonStyle.TONAL;
            YzuiTheme.button(g, getX(), getY(), getWidth(), getHeight(), isMouseOver(mx, my) ? 1 : 0, isFocused(), true, 1, style);
            int color = chosen ? YzuiTheme.onPrimary() : point.enabled() ? YzuiTheme.text() : YzuiTheme.textMuted();
            g.fill(getX() + 6, getY() + 7, getX() + 9, getY() + getHeight() - 7, point.color());
            YzuiTheme.label(g, font, MapTexts.waypoint(point), getX() + 16, getY() + (compact ? 4 : 7), getWidth() - (compact ? 44 : 24), color, false);
            YzuiTheme.label(g, font, Component.literal("X " + point.x() + " · Y " + point.y() + " · Z " + point.z()), getX() + 16, getY() + (compact ? 17 : 23), getWidth() - 24, color, false);
            if (compact) MapGroupIcons.render(g, MapWaypointGroup.of(point), getX() + getWidth() - 21, getY() + 2, 14, color);
            else {
                MapGroupIcons.render(g, MapWaypointGroup.of(point), getX() + 16, getY() + 35, 16, color);
                YzuiTheme.label(g, font, MapGroupIcons.label(MapWaypointGroup.of(point)).copy().append(" · " + date(point)),
                        getX() + 38, getY() + 39, getWidth() - 46, color, false);
            }
        }
    }
    private static String date(MapWaypoint point) { return point.createdAt() <= 0 ? "—" : DATE.format(Instant.ofEpochMilli(point.createdAt()).atZone(ZoneId.systemDefault())); }
    @Override public boolean mouseClicked(MouseButtonEvent event, boolean actual) {
        if (view == View.LIST && event.button() == InputConstants.MOUSE_BUTTON_RIGHT && event.x() >= listX && event.x() < listX + listWidth && event.y() >= listTop && event.y() < listBottom) {
            int row = (int) (event.y() - listTop) / rowHeight;
            if (row < visiblePoints.size()) { selected = visiblePoints.get(row).id(); view = View.ACTIONS; deleteArmed = false; init(); return true; }
        }
        return super.mouseClicked(event, actual);
    }
    @Override public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        if (!isMouseOver(x, y) || vertical == 0) return false;
        if (view == View.LIST && x >= treeX && x < treeX + sidebar && y >= treeTop && y < treeBottom) {
            treeOffset = Math.clamp(treeOffset + (vertical > 0 ? -1 : 1), 0, Math.max(0, treeCount - treeCapacity));
            buildTree(); return true;
        }
        if (view == View.LIST && x >= listX && y >= listTop && y < listBottom) { page += vertical > 0 ? -1 : 1; refresh(); return true; }
        return true;
    }
}
