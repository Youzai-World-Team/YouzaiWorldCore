package top.csituka.youzaiworldcore.client.screen.map;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import top.csituka.youzaiworldcore.client.map.MapClient;
import top.csituka.youzaiworldcore.client.map.MapTexts;
import top.csituka.youzaiworldcore.client.render.YzuiTheme;
import top.csituka.youzaiworldcore.client.screen.widget.TransparentButton;
import top.csituka.youzaiworldcore.network.MapSocialPayload;
import top.csituka.youzaiworldcore.util.DebugLogger;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/** 玩家位置共享左侧覆盖面板；只提交动作，不在客户端伪造同意或共享状态。 */
final class MapPlayersPanel extends MapOverlayPanel {
    private record Row(String section, UUID id, String name, boolean heading) { }
    private final Set<String> collapsed = new HashSet<>();
    private final List<TransparentButton> rows = new ArrayList<>();
    private MapSocialPayload state;
    private MapSocialPayload.Action picker;
    private String query = "";
    private int offset, count, capacity, top, ticks;
    private boolean dirty;
    MapPlayersPanel(YzWorldMapScreen owner) {
        super(owner, "players"); state = MapClient.social();
        send(MapSocialPayload.Action.FETCH, new UUID(0, 0));
        DebugLogger.debug("MapPlayers", "打开位置共享覆盖面板");
    }
    private void send(MapSocialPayload.Action action, UUID id) { MapClient.social(action, id, state == null || state.blacklist()); }
    @Override protected void init() {
        super.init(); rows.clear();
        button(panelX + panelWidth - 32, panelY + 7, 22, Component.literal("×"), this::onClose);
        top = panelY + 66; capacity = Math.max(1, (panelHeight - 144) / 23);
        if (picker != null) {
            field(panelX + 12, panelY + 38, panelWidth - 62, "search", query, 64, value -> { query = value; offset = 0; dirty = true; });
            button(panelX + panelWidth - 46, panelY + 37, 34, Component.literal("←"), () -> { picker = null; query = ""; offset = 0; init(); });
        } else {
            int w = (panelWidth - 30) / 2;
            button(panelX + 12, panelY + 37, w, MapTexts.text("share_add"), () -> choose(MapSocialPayload.Action.ALLOW));
            button(panelX + 18 + w, panelY + 37, w, MapTexts.text("share_request"), () -> choose(MapSocialPayload.Action.REQUEST));
        }
        button(panelX + 12, panelY + panelHeight - 32, panelWidth - 24,
                MapTexts.text("blacklist_mode").copy().append(" · ").append(MapTexts.text(state == null || state.blacklist() ? "on" : "off")), () -> {
                    MapClient.social(MapSocialPayload.Action.MODE, new UUID(0, 0), state == null || !state.blacklist());
                }).active = state != null;
        refreshRows();
    }
    private void choose(MapSocialPayload.Action action) { picker = action; query = ""; offset = 0; init(); }
    private String name(UUID id) {
        var connection = Minecraft.getInstance().getConnection(); var info = connection == null ? null : connection.getPlayerInfo(id);
        return info == null ? id.toString() : info.getProfile().name();
    }
    private void section(List<Row> result, String section, List<UUID> ids) {
        result.add(new Row(section, null, MapTexts.text(section).getString() + " (" + ids.size() + ")", true));
        if (!collapsed.contains(section)) for (UUID id : ids) result.add(new Row(section, id, name(id), false));
    }
    private void refreshRows() {
        dirty = false; for (var row : rows) removeWidget(row); rows.clear();
        List<Row> entries = new ArrayList<>();
        var client = Minecraft.getInstance();
        if (picker != null) {
            if (client.getConnection() != null) for (var info : client.getConnection().getOnlinePlayers()) {
                if (client.player != null && info.getProfile().id().equals(client.player.getUUID())) continue;
                if (info.getProfile().name().toLowerCase(Locale.ROOT).contains(query.toLowerCase(Locale.ROOT)))
                    entries.add(new Row("picker", info.getProfile().id(), info.getProfile().name(), false));
            }
        } else {
            var nearby = MapClient.radar().stream().filter(MapClient.Radar::player).map(MapClient.Radar::id)
                    .filter(id -> client.player == null || !id.equals(client.player.getUUID())).distinct().toList();
            section(entries, "share_visible", nearby);
            section(entries, "share_outgoing", state == null ? List.of() : state.allowed());
            section(entries, "share_incoming", state == null ? List.of() : state.incoming());
            section(entries, "share_blocked", state == null ? List.of() : state.blocked());
        }
        count = entries.size(); offset = Math.clamp(offset, 0, Math.max(0, count - capacity));
        for (int i = 0; i < capacity && offset + i < entries.size(); i++) {
            Row row = entries.get(offset + i); int y = top + i * 23;
            if (row.heading) {
                rows.add(button(panelX + 12, y, panelWidth - 24, Component.literal((collapsed.contains(row.section) ? "> " : "⌄ ") + row.name), () -> {
                    if (!collapsed.add(row.section)) collapsed.remove(row.section); refreshRows();
                })); continue;
            }
            int actions = row.section.equals("share_incoming") ? 3 : row.section.equals("share_visible") ? 3 : 1;
            var playerButton = new TransparentButton(panelX + 12, y, panelWidth - 28 - actions * 24, 22, Component.literal(row.name), () -> {
                if (picker != null) { send(picker, row.id); picker = null; init(); }
                else locate(row.id, false);
            }) {
                @Override protected void extractWidgetRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
                    super.extractWidgetRenderState(g, mx, my, delta);
                    var connection = Minecraft.getInstance().getConnection(); var info = connection == null ? null : connection.getPlayerInfo(row.id);
                    if (info != null) {
                        var texture = info.getSkin().body().texturePath();
                        g.blit(RenderPipelines.GUI_TEXTURED, texture, getX() + 3, getY() + 7, 8, 8, 8, 8, 64, 64);
                        g.blit(RenderPipelines.GUI_TEXTURED, texture, getX() + 3, getY() + 7, 40, 8, 8, 8, 64, 64);
                    }
                }
            };
            playerButton.setTextLeftAligned(true); playerButton.setTextInsets(16, 2);
            rows.add(addRenderableWidget(playerButton));
            int x = panelX + panelWidth - 12 - actions * 24;
            switch (row.section) {
                case "share_visible" -> {
                    action(x, y, "⌖", "locate", () -> locate(row.id, false));
                    action(x + 24, y, "◎", "navigate", () -> locate(row.id, true));
                    action(x + 48, y, "⊘", "share_block", () -> send(MapSocialPayload.Action.BLOCK, row.id));
                }
                case "share_incoming" -> {
                    action(x, y, "✓", "share_accept", () -> send(MapSocialPayload.Action.ACCEPT, row.id));
                    action(x + 24, y, "×", "share_reject", () -> send(MapSocialPayload.Action.REJECT, row.id));
                    action(x + 48, y, "⊘", "share_block", () -> send(MapSocialPayload.Action.BLOCK, row.id));
                }
                case "share_outgoing" -> action(x, y, "×", "delete", () -> send(MapSocialPayload.Action.REMOVE, row.id));
                case "share_blocked" -> action(x, y, "×", "delete", () -> send(MapSocialPayload.Action.UNBLOCK, row.id));
                default -> action(x, y, "+", "share_add", () -> { send(picker, row.id); picker = null; init(); });
            }
        }
    }
    private void action(int x, int y, String icon, String key, Runnable action) {
        var control = button(x, y, 22, Component.literal(icon), action);
        control.setTooltip(net.minecraft.client.gui.components.Tooltip.create(MapTexts.text(key))); rows.add(control);
    }
    private void locate(UUID id, boolean track) {
        var target = MapClient.radar().stream().filter(p -> p.id().equals(id)).findFirst().orElse(null);
        if (target == null) { error = MapTexts.text("no_visible_players"); return; }
        owner.locatePlayer(target, track); onClose();
    }
    @Override public void tick() {
        ticks++;
        if (ticks % 40 == 0) send(MapSocialPayload.Action.FETCH, new UUID(0, 0));
        if (!java.util.Objects.equals(state, MapClient.social())) {
            state = MapClient.social(); if (state != null && !state.result().isBlank()) error = MapTexts.text(state.result());
            if (picker == null) init(); else dirty = true;
        }
        if (dirty || ticks % 20 == 0) refreshRows();
    }
    @Override protected void content(GuiGraphicsExtractor g, int mx, int my, float delta) {
        if (picker != null && count == 0) label(g, MapTexts.text("share_no_match"), panelX + 12, top + 4, panelWidth - 24);
        if (picker == null) YzuiTheme.wrapped(g, font, MapTexts.text("sharing_mode_hint"), panelX + 12, panelY + panelHeight - 76, panelWidth - 24, 2, YzuiTheme.textMuted());
    }
    @Override public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        if (!isMouseOver(x, y)) return false;
        offset = Math.clamp(offset - (int) vertical, 0, Math.max(0, count - capacity)); refreshRows(); return true;
    }
}
