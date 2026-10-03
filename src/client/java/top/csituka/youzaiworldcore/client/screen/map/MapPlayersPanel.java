package top.csituka.youzaiworldcore.client.screen.map;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import top.csituka.youzaiworldcore.client.map.MapClient;
import top.csituka.youzaiworldcore.client.map.MapTexts;
import top.csituka.youzaiworldcore.client.render.RoundedRect;
import top.csituka.youzaiworldcore.client.render.YzuiTheme;
import top.csituka.youzaiworldcore.client.screen.widget.TransparentButton;
import top.csituka.youzaiworldcore.network.MapSessionPayload;
import top.csituka.youzaiworldcore.network.MapSocialPayload;
import top.csituka.youzaiworldcore.util.DebugLogger;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** 玩家位置共享管理：独立分页、共享范围选择、在线玩家选择器及服务端确认。 */
final class MapPlayersPanel extends MapOverlayPanel {
    private enum Tab {
        VISIBLE("players_visible", "players_visible_hint"), ALLOWED("players_allowed", "players_allowed_hint"),
        INCOMING("players_incoming", "players_incoming_hint"), BLOCKED("players_blocked", "players_blocked_hint");
        final String label, description;
        Tab(String label, String description) { this.label = label; this.description = description; }
    }
    private record Row(UUID id, int y, MapClient.Radar location) { }
    private record Pending(MapSocialPayload.Action action, long started) { }
    private static final UUID SELF = new UUID(0, 0);
    private final Map<UUID, String> names = new HashMap<>();
    private final Map<Tab, TransparentButton> tabs = new EnumMap<>(Tab.class);
    private final Map<Tab, Integer> totals = new EnumMap<>(Tab.class);
    private final Map<String, TransparentButton> rowWidgets = new LinkedHashMap<>();
    private final List<TransparentButton> mutations = new ArrayList<>();
    private final List<Row> rows = new ArrayList<>();
    private MapSocialPayload state;
    private Tab tab = Tab.VISIBLE;
    private MapSocialPayload.Action picker;
    private Pending pending;
    private boolean choosingMode, compact, dirty, refreshRequested, focusSearch;
    private String query = "", pickerQuery = "";
    private int page, pages = 1, count, capacity, listTop, listBottom, rowHeight, footer, searchY, ticks, lastSendTick = -5;
    private Component notice = Component.empty();
    private long noticeUntil;
    private boolean noticeFailure;
    private EditBox search;
    private TransparentButton scope, previous, next;

    MapPlayersPanel(YzWorldMapScreen owner) {
        super(owner, "players_title"); state = MapClient.social();
        fetch();
        DebugLogger.debug("MapPlayers", "打开玩家位置共享管理面板");
    }

    private boolean available() {
        var session = MapClient.session();
        return Minecraft.getInstance().getConnection() != null && session != null && session.allows(MapSessionPayload.RADAR)
                && ClientPlayNetworking.canSend(MapSocialPayload.ID);
    }
    // 服务端限制两次操作至少间隔 4 tick；按钮短暂冷却，已点击的操作立即发出，不依赖面板继续打开。
    private boolean editable() { return available() && state != null && pending == null && ticks - lastSendTick >= 5; }
    private void fetch() {
        if (!available()) return;
        MapClient.social(MapSocialPayload.Action.FETCH, SELF, false); lastSendTick = ticks; refreshRequested = false;
    }

    @Override protected void init() {
        boolean restoreSearchFocus = search != null && getFocused() == search;
        super.init();
        panelWidth = Math.min(width - 16, Math.max(280, Math.min(440, width / 2)));
        compact = panelHeight < 340;
        rowWidgets.clear(); rows.clear(); mutations.clear(); tabs.clear(); search = null;
        scope = null; previous = null; next = null;
        button(panelX + panelWidth - 32, panelY + 7, 22, Component.literal("×"), this::onClose);
        if (picker != null || choosingMode) button(panelX + panelWidth - 58, panelY + 7, 22, Component.literal("←"), this::back)
                .setTooltip(Tooltip.create(MapTexts.text("players_back")));
        footer = panelY + panelHeight - 32;
        if (choosingMode) {
            buildModes(); refreshEnabled(); return;
        }
        int y = panelY + (compact ? 36 : 48);
        if (picker == null) {
            scope = button(panelX + 12, y, panelWidth - 24, scopeLabel(), () -> { choosingMode = true; init(); });
            scope.setTextLeftAligned(true); scope.setTooltip(Tooltip.create(MapTexts.text("players_scope")));
            y += 28;
            int cell = (panelWidth - 36) / 4;
            for (var value : Tab.values()) {
                var control = addRenderableWidget(new TransparentButton(panelX + 12 + value.ordinal() * (cell + 4), y, cell, 32,
                        MapTexts.text(value.label), () -> { tab = value; page = 0; refreshRows(); }) {
                    @Override protected void extractWidgetRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
                        var style = value == tab ? YzuiTheme.ButtonStyle.FILLED : YzuiTheme.ButtonStyle.TONAL;
                        YzuiTheme.button(g, getX(), getY(), getWidth(), getHeight(), isMouseOver(mx, my) ? 1 : 0, isFocused(), true, 1, style);
                        int color = YzuiTheme.buttonText(style, true);
                        YzuiTheme.label(g, font, MapTexts.text(value.label), getX() + 3, getY() + 5, getWidth() - 6, color, true);
                        YzuiTheme.label(g, font, Component.literal(Integer.toString(totals.getOrDefault(value, 0))), getX() + 3, getY() + 19, getWidth() - 6, color, true);
                    }
                });
                tabs.put(value, control);
            }
            y += 40;
        } else {
            y += 26;
        }
        searchY = y;
        // 切换分页时仅刷新列表，因此保留统一的添加入口并动态更新其行为。
        int actionWidth = picker == null ? 74 : 0;
        search = field(panelX + 12, y, panelWidth - 24 - (actionWidth == 0 ? 0 : actionWidth + 6), "players_search",
                picker == null ? query : pickerQuery, 64, value -> {
                    if (picker == null) query = value; else pickerQuery = value;
                    page = 0; dirty = true;
                });
        search.setHint(MapTexts.text("players_search"));
        if (picker == null) {
            var add = button(panelX + panelWidth - 12 - actionWidth, y - 1, actionWidth, MapTexts.text("share_add"), () -> {
                var selected = switch (tab) {
                    case VISIBLE -> MapSocialPayload.Action.REQUEST;
                    case ALLOWED -> MapSocialPayload.Action.ALLOW;
                    case BLOCKED -> MapSocialPayload.Action.BLOCK;
                    case INCOMING -> null;
                };
                if (selected != null) { picker = selected; pickerQuery = ""; page = 0; init(); focusSearch = true; }
            });
            rowWidgets.put("add", add);
        }
        listTop = searchY + (compact ? 28 : 48);
        rowHeight = compact ? 52 : 62;
        previous = button(panelX + 12, footer, 24, Component.literal("‹"), () -> { page--; refreshRows(); });
        next = button(panelX + panelWidth - 36, footer, 24, Component.literal("›"), () -> { page++; refreshRows(); });
        previous.setTooltip(Tooltip.create(MapTexts.text("previous"))); next.setTooltip(Tooltip.create(MapTexts.text("next")));
        refreshRows();
        if (restoreSearchFocus) setFocused(search);
    }

    private Component sectionHint() {
        return MapTexts.text(picker != null ? "players_pick_hint" : switch (tab) {
            case VISIBLE -> "players_visible_hint";
            case ALLOWED -> state != null && state.blacklist() ? "players_all_hint" : "players_allowed_hint";
            case INCOMING -> "players_incoming_hint";
            case BLOCKED -> "players_blocked_hint";
        });
    }
    private Component scopeLabel() {
        String key = !available() ? "players_unavailable" : state == null ? "players_loading" : state.blacklist() ? "players_scope_all" : "players_scope_list";
        return MapTexts.text(key).copy().append("  ›");
    }
    private void buildModes() {
        int y = panelY + 58;
        for (boolean blacklist : new boolean[] { false, true }) {
            String key = blacklist ? "players_scope_all" : "players_scope_list";
            var option = button(panelX + 12, y, panelWidth - 24, MapTexts.text(key), () -> submit(MapSocialPayload.Action.MODE, SELF, blacklist));
            option.setTextLeftAligned(true); option.setTextInsets(12, 12); option.setHeight(28);
            if (state != null && state.blacklist() == blacklist) option.setStyle(YzuiTheme.ButtonStyle.FILLED);
            option.setTooltip(Tooltip.create(MapTexts.text(key + "_hint")));
            mutations.add(option); y += compact ? 66 : 82;
        }
        button(panelX + 12, footer, panelWidth - 24, MapTexts.text("players_back"), this::back);
    }
    private void back() { picker = null; choosingMode = false; focusSearch = false; page = 0; init(); }

    private String name(UUID id) {
        var connection = Minecraft.getInstance().getConnection();
        var info = connection == null ? null : connection.getPlayerInfo(id);
        if (info != null) names.put(id, info.getProfile().name());
        return names.getOrDefault(id, id.toString());
    }
    private boolean online(UUID id) {
        var connection = Minecraft.getInstance().getConnection();
        return connection != null && connection.getPlayerInfo(id) != null;
    }
    private List<UUID> ids(Tab value, Map<UUID, MapClient.Radar> locations) {
        return switch (value) {
            case VISIBLE -> List.copyOf(locations.keySet());
            case ALLOWED -> state == null ? List.of() : state.allowed();
            case INCOMING -> state == null ? List.of() : state.incoming();
            case BLOCKED -> state == null ? List.of() : state.blocked();
        };
    }
    private void refreshRows() {
        dirty = false;
        if (choosingMode || search == null) return;
        String focused = rowWidgets.entrySet().stream().filter(e -> e.getValue() == getFocused()).map(Map.Entry::getKey).findFirst().orElse(null);
        var add = rowWidgets.remove("add");
        rowWidgets.values().forEach(this::removeWidget); rowWidgets.clear(); rows.clear(); mutations.clear();
        if (add != null) rowWidgets.put("add", add);
        var locations = new LinkedHashMap<UUID, MapClient.Radar>();
        MapClient.visiblePlayers().forEach(player -> { locations.put(player.id(), player); names.put(player.id(), player.name()); });
        for (var value : Tab.values()) if (tabs.containsKey(value)) {
            var control = tabs.get(value);
            totals.put(value, available() ? ids(value, locations).size() : 0);
            control.setMessage(MapTexts.text(value.label).copy().append(" · " + totals.get(value)));
            control.setTooltip(Tooltip.create(MapTexts.text(value.label).copy().append(" · " + totals.get(value)).append("\n").append(MapTexts.text(value.description))));
        }
        if (scope != null) scope.setMessage(scopeLabel());
        search.setTooltip(Tooltip.create(sectionHint()));
        if (add != null) {
            String key = switch (tab) { case VISIBLE -> "share_request"; case ALLOWED -> "share_add"; case BLOCKED -> "share_block"; case INCOMING -> "share_accept"; };
            add.setMessage(MapTexts.text(key)); add.setTooltip(Tooltip.create(MapTexts.text(key)));
            add.visible = tab != Tab.INCOMING; add.active = editable() && add.visible;
            search.setWidth(panelWidth - 24 - (add.visible ? 80 : 0));
        }
        var entries = new ArrayList<UUID>();
        if (picker != null) {
            var client = Minecraft.getInstance();
            if (client.getConnection() != null) for (var info : client.getConnection().getOnlinePlayers()) {
                UUID id = info.getProfile().id();
                if (client.player != null && id.equals(client.player.getUUID())) continue;
                if (state != null && (picker == MapSocialPayload.Action.ALLOW && state.allowed().contains(id)
                        || picker == MapSocialPayload.Action.BLOCK && state.blocked().contains(id))) continue;
                if (picker == MapSocialPayload.Action.REQUEST && locations.containsKey(id)) continue;
                entries.add(id);
            }
        } else entries.addAll(ids(tab, locations));
        String filter = (picker == null ? query : pickerQuery).strip().toLowerCase(Locale.ROOT);
        entries.removeIf(id -> !name(id).toLowerCase(Locale.ROOT).contains(filter) && !id.toString().contains(filter));
        entries.sort(Comparator.comparing((UUID id) -> !online(id)).thenComparing(this::name, String.CASE_INSENSITIVE_ORDER).thenComparing(UUID::toString));
        if (!available()) entries.clear();
        count = entries.size(); listBottom = footer - 6;
        capacity = Math.max(0, (listBottom - listTop) / rowHeight);
        pages = Math.max(1, Math.ceilDiv(count, Math.max(1, capacity))); page = Math.clamp(page, 0, pages - 1);
        previous.active = capacity > 0 && page > 0; next.active = capacity > 0 && page + 1 < pages;
        for (int i = 0; i < capacity && page * capacity + i < count; i++) buildRow(entries.get(page * capacity + i), listTop + i * rowHeight, locations);
        if (focused != null && rowWidgets.containsKey(focused)) setFocused(rowWidgets.get(focused));
        refreshEnabled();
    }

    private void buildRow(UUID id, int y, Map<UUID, MapClient.Radar> locations) {
        var target = locations.get(id); rows.add(new Row(id, y, target));
        var player = new TransparentButton(panelX + 12, y, panelWidth - 24, 26, Component.literal(name(id)), () -> {
            if (picker != null) submit(picker, id, false); else if (tab == Tab.VISIBLE) locate(id, false);
        }) {
            @Override protected void extractWidgetRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
                YzuiTheme.button(g, getX(), getY(), getWidth(), getHeight(), isMouseOver(mx, my) ? 1 : 0, isFocused(), active, 1, YzuiTheme.ButtonStyle.TEXT);
                var connection = Minecraft.getInstance().getConnection(); var info = connection == null ? null : connection.getPlayerInfo(id);
                int x = getX() + 6, cy = getY() + 5;
                RoundedRect.fill(g, x, cy, 18, 18, 4, YzuiTheme.secondaryContainer());
                if (info != null) {
                    var texture = info.getSkin().body().texturePath();
                    g.blit(RenderPipelines.GUI_TEXTURED, texture, x + 1, cy + 1, 8, 8, 16, 16, 8, 8, 64, 64);
                    g.blit(RenderPipelines.GUI_TEXTURED, texture, x + 1, cy + 1, 40, 8, 16, 16, 8, 8, 64, 64);
                } else YzuiTheme.label(g, font, Component.literal("?"), x, cy + 5, 18, YzuiTheme.textMuted(), true);
                String statusKey = info == null ? "players_offline" : "players_online";
                int statusWidth = Math.min(54, font.width(MapTexts.text(statusKey)) + 8);
                YzuiTheme.label(g, font, getMessage(), x + 26, cy + 4, getWidth() - 44 - statusWidth, YzuiTheme.text(), false);
                YzuiTheme.label(g, font, MapTexts.text(statusKey), getX() + getWidth() - statusWidth - 5, cy + 4, statusWidth, info == null ? YzuiTheme.textMuted() : YzuiTheme.success(), true);
                if (isFocused()) g.outline(getX(), getY(), getWidth(), getHeight(), YzuiTheme.primary());
            }
        };
        Component detail = Component.literal(name(id)).append("\n" + id);
        if (target != null) detail = detail.copy().append("\n").append(locationText(target));
        player.setTooltip(Tooltip.create(detail)); addRowWidget(id + "/name", player);
        int actions = picker != null ? 1 : tab == Tab.VISIBLE || tab == Tab.INCOMING ? 3 : tab == Tab.ALLOWED ? 2 : 1;
        int w = (panelWidth - 40 - (actions - 1) * 4) / actions, x = panelX + 20, ay = y + rowHeight - 26;
        if (picker != null) {
            action(id, x, ay, w, picker == MapSocialPayload.Action.ALLOW ? "share_add" : picker == MapSocialPayload.Action.REQUEST ? "share_request" : "share_block", picker, true);
            mutations.add(player);
        } else switch (tab) {
            case VISIBLE -> {
                rowButton(id + "/locate", x, ay, w, "locate", () -> locate(id, false));
                rowButton(id + "/track", x + w + 4, ay, w, "players_track", () -> locate(id, true));
                action(id, x + (w + 4) * 2, ay, w, "share_block", MapSocialPayload.Action.BLOCK, false);
            }
            case ALLOWED -> {
                action(id, x, ay, w, "players_remove", MapSocialPayload.Action.REMOVE, false);
                action(id, x + w + 4, ay, w, "share_block", MapSocialPayload.Action.BLOCK, false);
            }
            case INCOMING -> {
                action(id, x, ay, w, "share_accept", MapSocialPayload.Action.ACCEPT, true);
                action(id, x + w + 4, ay, w, "share_reject", MapSocialPayload.Action.REJECT, false);
                action(id, x + (w + 4) * 2, ay, w, "share_block", MapSocialPayload.Action.BLOCK, false);
            }
            case BLOCKED -> action(id, x, ay, w, "players_unblock", MapSocialPayload.Action.UNBLOCK, false);
        }
    }
    private void addRowWidget(String key, TransparentButton widget) { rowWidgets.put(key, addRenderableWidget(widget)); }
    private TransparentButton rowButton(String key, int x, int y, int width, String label, Runnable run) {
        var button = new TransparentButton(x, y, width, 20, MapTexts.text(label), run);
        button.setTextInsets(3, 3); button.setTooltip(Tooltip.create(MapTexts.text(label))); addRowWidget(key, button); return button;
    }
    private void action(UUID id, int x, int y, int width, String key, MapSocialPayload.Action action, boolean primary) {
        var control = rowButton(id + "/" + action, x, y, width, key, () -> submit(action, id, false));
        control.setStyle(primary ? YzuiTheme.ButtonStyle.FILLED : YzuiTheme.ButtonStyle.TEXT);
        if (action == MapSocialPayload.Action.BLOCK || action == MapSocialPayload.Action.REMOVE) control.setTextColor(YzuiTheme::error);
        if (action == MapSocialPayload.Action.BLOCK) control.setTooltip(Tooltip.create(MapTexts.text("players_blocked_hint")));
        mutations.add(control);
    }
    private Component locationText(MapClient.Radar target) {
        return MapTexts.dimension(target.dimension()).copy().append(" · " + (int) Math.floor(target.x()) + ", " + (int) Math.floor(target.y()) + ", " + (int) Math.floor(target.z()));
    }
    private void locate(UUID id, boolean track) {
        var target = MapClient.visiblePlayers().stream().filter(p -> p.id().equals(id)).findFirst().orElse(null);
        if (target == null) { showNotice("no_visible_players", true); return; }
        owner.locateRadar(target, track);
    }
    private void submit(MapSocialPayload.Action action, UUID id, boolean blacklist) {
        if (!editable()) return;
        pending = new Pending(action, System.currentTimeMillis());
        MapClient.social(action, id, blacklist); lastSendTick = ticks;
        showNotice("players_pending", false); refreshEnabled();
        DebugLogger.debug("MapPlayers", "提交位置共享操作：%s / %s", action, id);
    }
    private void refreshEnabled() {
        mutations.forEach(control -> control.active = editable());
        if (choosingMode && state != null && mutations.size() == 2) for (int i = 0; i < 2; i++)
            mutations.get(i).setStyle(state.blacklist() == (i == 1) ? YzuiTheme.ButtonStyle.FILLED : YzuiTheme.ButtonStyle.TONAL);
        if (scope != null) scope.active = editable();
        var add = rowWidgets.get("add"); if (add != null) add.active = editable() && add.visible;
    }
    private void showNotice(String key, boolean failure) {
        notice = MapTexts.text(key); noticeFailure = failure; noticeUntil = System.currentTimeMillis() + 6000; dirty = true;
    }
    @Override public void tick() {
        ticks++;
        var latest = MapClient.social();
        if (latest != state) {
            state = latest;
            if (pending != null && state != null && List.of("saved", "request_sent", "denied", "limit").contains(state.result())) {
                var action = pending.action(); boolean success = state.result().equals("saved") || state.result().equals("request_sent");
                pending = null;
                if (success && (picker != null || choosingMode)) { picker = null; choosingMode = false; page = 0; init(); }
                showNotice(success ? action == MapSocialPayload.Action.REQUEST ? "players_request_sent" : "players_saved"
                        : state.result().equals("limit") ? "players_limit" : "players_denied", !success);
            } else refreshRows();
        }
        if (pending != null) {
            if (System.currentTimeMillis() - pending.started() > 8000 || !available()) {
                pending = null; refreshRequested = true; showNotice("players_timeout", true);
            }
        } else if ((state == null || refreshRequested) && ticks - lastSendTick >= 40) {
            // 规则修改和收到申请均由服务端主动推送；只在初次加载或超时后补取，避免轮询占用操作限流。
            fetch();
        }
        if (pending == null && !notice.getString().isEmpty() && System.currentTimeMillis() >= noticeUntil) { notice = Component.empty(); dirty = true; }
        if (dirty || ticks % 20 == 0) refreshRows();
        if (focusSearch && search != null) { setFocused(search); focusSearch = false; }
        refreshEnabled();
    }

    @Override protected void content(GuiGraphicsExtractor g, int mx, int my, float delta) {
        if (choosingMode) {
            label(g, MapTexts.text("players_scope"), panelX + 12, panelY + 39, panelWidth - 24);
            int y = panelY + 91;
            for (String key : List.of("players_scope_list_hint", "players_scope_all_hint")) {
                YzuiTheme.wrapped(g, font, MapTexts.text(key), panelX + 20, y, panelWidth - 40, 2, YzuiTheme.textMuted());
                y += compact ? 66 : 82;
            }
        } else {
            if (!compact) label(g, MapTexts.text(picker == null ? "players_subtitle" : picker == MapSocialPayload.Action.ALLOW ? "share_add" : picker == MapSocialPayload.Action.REQUEST ? "share_request" : "share_block"), panelX + 12, panelY + 33, panelWidth - 24);
            if (picker != null && compact) label(g, MapTexts.text(picker == MapSocialPayload.Action.ALLOW ? "share_add" : picker == MapSocialPayload.Action.REQUEST ? "share_request" : "share_block"), panelX + 12, panelY + 42, panelWidth - 24);
            if (!compact) label(g, sectionHint(), panelX + 12, searchY + 28, panelWidth - 24);
            for (var row : rows) {
                RoundedRect.fill(g, panelX + 12, row.y(), panelWidth - 24, rowHeight - 4, 6, YzuiTheme.surfaceLow());
                if (!compact && row.location() != null) YzuiTheme.label(g, font, locationText(row.location()), panelX + 20, row.y() + 27, panelWidth - 40, YzuiTheme.textMuted(), false);
            }
            if (count == 0 || !available()) {
                String key = !available() ? "players_unavailable" : state == null ? "players_loading"
                        : !(picker == null ? query : pickerQuery).isBlank() || picker != null ? "players_no_match"
                        : "players_empty_" + tab.name().toLowerCase(Locale.ROOT);
                YzuiTheme.wrapped(g, font, MapTexts.text(key), panelX + 20, listTop + 10, panelWidth - 40, 3, YzuiTheme.textMuted());
            }
            var status = notice.getString().isEmpty() ? MapTexts.text("page", page + 1, pages) : notice;
            int color = notice.getString().isEmpty() ? YzuiTheme.textMuted() : noticeFailure ? YzuiTheme.error() : YzuiTheme.primary();
            YzuiTheme.label(g, font, status, panelX + 42, footer + 7, panelWidth - 84, color, true);
        }
        if (choosingMode && !notice.getString().isEmpty()) YzuiTheme.label(g, font, notice, panelX + 12, footer - 12, panelWidth - 24, noticeFailure ? YzuiTheme.error() : YzuiTheme.primary(), false);
    }
    @Override public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        if (!isMouseOver(x, y)) return false;
        if (!choosingMode && vertical != 0 && capacity > 0) { page += vertical > 0 ? -1 : 1; refreshRows(); }
        return true;
    }
    @Override public boolean keyPressed(KeyEvent event) {
        if (event.key() == GLFW.GLFW_KEY_ESCAPE && (picker != null || choosingMode)) { back(); return true; }
        return super.keyPressed(event);
    }
}
