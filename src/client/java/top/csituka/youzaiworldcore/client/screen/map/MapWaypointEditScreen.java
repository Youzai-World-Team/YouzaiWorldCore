package top.csituka.youzaiworldcore.client.screen.map;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import top.csituka.youzaiworldcore.client.map.MapClient;
import top.csituka.youzaiworldcore.client.map.MapPersonalData;
import top.csituka.youzaiworldcore.client.map.MapTexts;
import top.csituka.youzaiworldcore.client.map.MapTransfer;
import top.csituka.youzaiworldcore.client.render.YzuiTheme;
import top.csituka.youzaiworldcore.map.MapWaypoint;
import top.csituka.youzaiworldcore.network.MapActionPayload;
import top.csituka.youzaiworldcore.network.MapSessionPayload;

import java.util.LinkedHashMap;
import java.util.Map;

/** 私人/公共路径点编辑。所有公共操作经服务端鉴权，拒绝时保留输入。 */
public final class MapWaypointEditScreen extends MapScreen {
    private final MapWaypoint original;
    private final boolean existing;
    private final Map<String, String> values = new LinkedHashMap<>();
    private boolean shared, pending;
    private int rowHeight;

    public MapWaypointEditScreen(Screen parent, MapWaypoint point, boolean existing) {
        super(parent, existing ? "edit_point" : "new_point"); original = point; this.existing = existing; shared = point.shared();
        values.put("name", point.name()); values.put("group", point.group()); values.put("dimension", point.dimension());
        values.put("x", Integer.toString(point.x())); values.put("y", Integer.toString(point.y())); values.put("z", Integer.toString(point.z()));
        values.put("color", String.format(java.util.Locale.ROOT, "%06X", point.color() & 0xFFFFFF));
    }

    @Override protected void init() {
        super.init(); rowHeight = Math.max(25, Math.min(38, (panelHeight - 120) / 5));
        int x = panelX + 18, y = panelY + 42, w = panelWidth - 36;
        edit("name", x, y, w, 64); edit("group", x, y + rowHeight, w, 32);
        edit("dimension", x, y + rowHeight * 2, w, 128);
        int cw = (w - 12) / 3;
        edit("x", x, y + rowHeight * 3, cw, 10); edit("y", x + cw + 6, y + rowHeight * 3, cw, 10); edit("z", x + (cw + 6) * 2, y + rowHeight * 3, cw, 10);
        edit("color", x, y + rowHeight * 4, w / 2 - 4, 7);
        button(x + w / 2 + 4, y + rowHeight * 4, w / 2 - 4, MapTexts.text(shared ? "shared_point" : "private_point"), () -> { shared = !shared; init(); }).active = !original.shared() && !pending;
        int bw = (w - 12) / 3, footer = panelY + panelHeight - 34;
        button(x, footer, bw, MapTexts.text("save"), this::savePoint).setStyle(YzuiTheme.ButtonStyle.FILLED).active = !pending && editable();
        button(x + bw + 6, footer, bw, MapTexts.text("copy"), () -> {
            try { Minecraft.getInstance().keyboardHandler.setClipboard(MapTransfer.coordinate(readPoint())); error = MapTexts.text("copied"); }
            catch (IllegalArgumentException ignored) { error = MapTexts.text("invalid_position"); }
        });
        button(x + (bw + 6) * 2, footer, bw, MapTexts.text("cancel"), this::onClose);
    }

    private void edit(String key, int x, int y, int width, int limit) {
        int label = key.length() == 1 ? 15 : Math.min(74, width / 3);
        var field = field(x + label, y, width - label, key, values.get(key), limit, value -> values.put(key, value));
        field.setEditable(editable() && !pending);
    }

    private boolean editable() {
        if (!original.shared()) return true;
        var session = MapClient.session(); var player = Minecraft.getInstance().player;
        return session != null && (session.allows(MapSessionPayload.MANAGE)
                || player != null && original.owner().equals(player.getUUID()) && original.kind() == MapWaypoint.Kind.NORMAL && session.allows(MapSessionPayload.PUBLISH));
    }

    private MapWaypoint readPoint() {
        if (!values.get("color").matches("#?[0-9a-fA-F]{6}")) throw new IllegalArgumentException("颜色必须为六位十六进制");
        int color = Integer.parseInt(values.get("color").replace("#", ""), 16);
        return new MapWaypoint(original.id(), original.owner(), values.get("name"), values.get("group"), values.get("dimension"),
                Integer.parseInt(values.get("x")), Integer.parseInt(values.get("y")), Integer.parseInt(values.get("z")), color,
                original.enabled(), shared, original.kind(), original.createdAt());
    }

    private void savePoint() {
        try {
            MapWaypoint point = readPoint();
            if (!shared) {
                if (!MapPersonalData.put(point)) { error = MapTexts.text("limit"); return; }
                onClose(); return;
            }
            var session = MapClient.session();
            if (session == null || !session.allows(MapSessionPayload.PUBLISH)) { error = MapTexts.text("denied"); return; }
            pending = MapClient.send(original.shared() ? MapActionPayload.Action.UPDATE : MapActionPayload.Action.ADD, point, (success, message) -> {
                pending = false;
                if (success) {
                    if (existing && !original.shared()) MapPersonalData.remove(original.id());
                    if (Minecraft.getInstance().gui.screen() == this) onClose();
                } else { error = MapTexts.text(message); if (Minecraft.getInstance().gui.screen() == this) init(); }
            });
            if (pending) { error = MapTexts.text("request_sent"); init(); }
        } catch (IllegalArgumentException error) { this.error = MapTexts.text("invalid_point"); }
    }

    @Override protected void content(GuiGraphicsExtractor g, int mx, int my, float delta) {
        int x = panelX + 18, y = panelY + 49, w = panelWidth - 36;
        label(g, MapTexts.text("name"), x, y, 70); label(g, MapTexts.text("group"), x, y + rowHeight, 70);
        label(g, MapTexts.text("dimension"), x, y + rowHeight * 2, 70); label(g, MapTexts.text("color"), x, y + rowHeight * 4, 70);
        int cw = (w - 12) / 3;
        label(g, net.minecraft.network.chat.Component.literal("X"), x, y + rowHeight * 3, 13);
        label(g, net.minecraft.network.chat.Component.literal("Y"), x + cw + 6, y + rowHeight * 3, 13);
        label(g, net.minecraft.network.chat.Component.literal("Z"), x + (cw + 6) * 2, y + rowHeight * 3, 13);
        if (!editable()) label(g, MapTexts.text("locked_point"), x, panelY + panelHeight - 64, w);
    }
}
