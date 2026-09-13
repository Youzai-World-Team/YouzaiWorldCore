package top.csituka.youzaiworldcore.client.screen.map;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import top.csituka.youzaiworldcore.client.map.MapClient;
import top.csituka.youzaiworldcore.client.map.MapPersonalData;
import top.csituka.youzaiworldcore.client.map.MapTexts;
import top.csituka.youzaiworldcore.client.map.MapTransfer;
import top.csituka.youzaiworldcore.map.MapWaypoint;
import top.csituka.youzaiworldcore.network.MapActionPayload;
import top.csituka.youzaiworldcore.network.MapSessionPayload;

/** 路径点动作页，明确区分本机导航、公共编辑和受权限约束的传送。 */
public final class MapWaypointActionsScreen extends MapScreen {
    private MapWaypoint point;
    private boolean pending;
    public MapWaypointActionsScreen(Screen parent, MapWaypoint point) { super(parent, "point_actions"); this.point = point; }
    @Override protected void init() {
        point = MapClient.waypoints().stream().filter(value -> value.id().equals(point.id())).findFirst().orElse(point);
        super.init(); int x = panelX + 14, y = panelY + 68, w = (panelWidth - 36) / 2;
        button(x, y, w, MapTexts.text("edit_point"), () -> Minecraft.getInstance().gui.setScreen(new MapWaypointEditScreen(this, point, true)));
        button(x + w + 8, y, w, MapTexts.text("navigate"), () -> { MapClient.navigate(point.id()); Minecraft.getInstance().gui.setScreen(null); });
        button(x, y + 28, w, MapTexts.text("locate"), () -> Minecraft.getInstance().gui.setScreen(new YzWorldMapScreen(parent, point.dimension(), point.x(), point.z())));
        button(x + w + 8, y + 28, w, MapTexts.text("copy"), () -> { Minecraft.getInstance().keyboardHandler.setClipboard(MapTransfer.coordinate(point)); error = MapTexts.text("copied"); });
        button(x, y + 56, w, MapTexts.text("share_chat"), () -> MapTransfer.share(point));
        button(x + w + 8, y + 56, w, MapTexts.text(point.enabled() ? "hide_point" : "show_point"), () -> {
            MapPersonalData.put(point.withEnabled(!point.enabled())); onClose();
        }).active = !point.shared();
        var session = MapClient.session(); boolean manage = session != null && session.allows(MapSessionPayload.MANAGE);
        button(x, y + 84, w, MapTexts.text(point.kind() == MapWaypoint.Kind.SERVER ? "unlock_point" : "lock_point"), () ->
                action(point.kind() == MapWaypoint.Kind.SERVER ? MapActionPayload.Action.UNLOCK : MapActionPayload.Action.LOCK)).active = point.shared() && manage && !pending;
        button(x + w + 8, y + 84, w, MapTexts.text("teleport"), () -> action(MapActionPayload.Action.TELEPORT)).active = session != null && session.allows(MapSessionPayload.TELEPORT) && !pending;
        button(x, panelY + panelHeight - 34, w, MapTexts.text("delete"), () -> {
            if (point.shared()) action(MapActionPayload.Action.DELETE); else { MapPersonalData.remove(point.id()); onClose(); }
        }).active = !pending && (!point.shared() || manage || Minecraft.getInstance().player != null
                && point.owner().equals(Minecraft.getInstance().player.getUUID()) && point.kind() == MapWaypoint.Kind.NORMAL);
        button(x + w + 8, panelY + panelHeight - 34, w, MapTexts.text("done"), this::onClose);
    }
    private void action(MapActionPayload.Action action) {
        pending = MapClient.send(action, point, (ok, key) -> {
            pending = false; error = MapTexts.text(key);
            if (Minecraft.getInstance().gui.screen() == this) { if (ok) onClose(); else init(); }
        });
        if (pending) { error = MapTexts.text("request_sent"); init(); }
    }
    @Override protected void content(GuiGraphicsExtractor g, int x, int y, float delta) {
        label(g, MapTexts.waypoint(point).copy().append(" · " + point.x() + " / " + point.y() + " / " + point.z()), panelX + 14, panelY + 44, panelWidth - 28);
    }
}
