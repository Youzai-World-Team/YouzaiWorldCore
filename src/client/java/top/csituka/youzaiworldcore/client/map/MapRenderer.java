package top.csituka.youzaiworldcore.client.map;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import top.csituka.youzaiworldcore.client.config.MapSettings;
import top.csituka.youzaiworldcore.client.config.YzHudComponent;
import top.csituka.youzaiworldcore.client.config.YzHudSettings;
import top.csituka.youzaiworldcore.client.hud.YzHudLayout;
import top.csituka.youzaiworldcore.client.render.RoundedRect;
import top.csituka.youzaiworldcore.client.render.YzuiTheme;
import top.csituka.youzaiworldcore.map.MapDrawing;
import top.csituka.youzaiworldcore.map.MapTileKey;
import top.csituka.youzaiworldcore.map.MapVertex;
import top.csituka.youzaiworldcore.map.MapWaypoint;

import java.util.Comparator;
import java.util.ArrayList;
import java.util.List;

/** 悠哉地图的 HUD、覆盖标记与世界导航。参考 Conflux 的功能说明独立设计，不使用其代码或资源。 */
@SuppressWarnings("null")
public final class MapRenderer {
    private static final MapCanvas MINIMAP = new MapCanvas();
    private MapRenderer() { }

    /** @return 小地图底部信息栏高度，YZHUD 拖拽预览使用同一尺寸 */
    public static int informationHeight() {
        int rows = 0;
        for (var option : new MapSettings.Toggle[] {MapSettings.Toggle.COORDINATES, MapSettings.Toggle.BIOME_LABEL, MapSettings.Toggle.LAYER_LABEL}) if (MapSettings.enabled(option)) rows++;
        return rows == 0 ? 0 : rows * 11 + 4;
    }

    /** 当前 GUI 缩放下能完整容纳的地图边长，渲染与布局编辑器共用。 */
    public static int layoutSize() {
        var window = Minecraft.getInstance().getWindow();
        return Math.max(1, Math.min(MapSettings.size(), Math.min(window.getGuiScaledWidth() - 12,
                window.getGuiScaledHeight() - informationHeight() - 12)));
    }

    /** 由 HUD 提取尾部调用，尊重 F1、死亡界面、服务器地图开关和 YZHUD 透明度。 */
    public static void hud(GuiGraphicsExtractor g) {
        var client = Minecraft.getInstance();
        if (client.player == null || client.level == null || !client.player.isAlive() || client.gui.hud.isHidden()
                || client.gui.screen() != null || MapClient.dimension().equals("youzaiworldcore:login_hall")) return;
        float opacity = YzHudSettings.getOpacity();
        if (opacity <= 0) return;
        if (MapSettings.enabled(MapSettings.Toggle.WAYPOINT_HUD) && MapSettings.enabled(MapSettings.Toggle.WAYPOINTS)) navigation(g, opacity);
        if (!MapSettings.enabled(MapSettings.Toggle.MINIMAP)) return;
        int size = layoutSize();
        if (size < 40) return;
        int w = size + 8, h = size + informationHeight() + 8;
        int left = Math.clamp(YzHudLayout.componentLeft(YzHudComponent.MINIMAP, g.guiWidth(), w), 2, g.guiWidth() - w - 2);
        int top = Math.clamp(YzHudLayout.componentTop(YzHudComponent.MINIMAP, g.guiHeight(), h), 2, g.guiHeight() - h - 2);
        if (MapSettings.enabled(MapSettings.Toggle.AVOID_HUD) && YzHudSettings.getPositionX(YzHudComponent.MINIMAP) == 0
                && YzHudSettings.getPositionY(YzHudComponent.MINIMAP) == 0) {
            int[] occupied = top.csituka.youzaiworldcore.client.hud.ScoreboardSidebarRenderer.mapAvoidanceBounds();
            if (occupied != null && left < occupied[0] + occupied[2] && left + w > occupied[0]
                    && top < occupied[1] + occupied[3] && top + h > occupied[1]) {
                if (occupied[1] - h - 4 >= 2) top = occupied[1] - h - 4;
                else if (occupied[0] - w - 4 >= 2) left = occupied[0] - w - 4;
            }
        }
        YzuiTheme.hudCard(g, left, top, w, h, opacity);
        int radius = switch (MapSettings.shape()) { case CIRCLE -> size / 2; case SQUARE -> 0; case ROUNDED -> 10; };
        double angle = MapSettings.enabled(MapSettings.Toggle.ROTATE) ? Math.toRadians(client.player.getYRot()) + Math.PI : 0;
        MapView view = new MapView(client.player.getX(), client.player.getZ(), MapSettings.zoom(), angle, size, size);
        String dimension = MapClient.dimension(); var layer = MapClient.layer(dimension);
        MapView displayed = MINIMAP.draw(g, view, dimension, layer, MapClient.height(layer), left + 4, top + 4, radius, opacity);
        overlay(g, displayed, dimension, left + 4, top + 4, radius, opacity, false);
        YzuiTheme.border(g, left + 3, top + 3, size + 2, size + 2, radius == 0 ? 0 : radius + 1,
                YzuiTheme.alpha(YzuiTheme.primary(), 0.75f * opacity));
        int y = top + size + 8;
        if (MapSettings.enabled(MapSettings.Toggle.COORDINATES)) {
            info(g, client.player.getBlockX() + "  /  " + client.player.getBlockY() + "  /  " + client.player.getBlockZ(), left + 7, y, w - 14, opacity); y += 11;
        }
        if (MapSettings.enabled(MapSettings.Toggle.BIOME_LABEL)) {
            int cx = Math.floorDiv(client.player.getBlockX(), 16), cz = Math.floorDiv(client.player.getBlockZ(), 16);
            var tile = Math.abs((long) cx) > MapTileKey.CHUNK_LIMIT || Math.abs((long) cz) > MapTileKey.CHUNK_LIMIT ? null
                    : MapClient.cache().get(new MapTileKey(dimension, layer, MapClient.height(layer), cx, cz));
            String name = tile == null ? MapTexts.text("unknown").getString() : MapTexts.biome(tile.biome(MapTileKey.pixelIndex(client.player.getBlockX(), client.player.getBlockZ()))).getString();
            info(g, name, left + 7, y, w - 14, opacity); y += 11;
        }
        if (MapSettings.enabled(MapSettings.Toggle.LAYER_LABEL)) info(g, MapTexts.text("layer." + layer.name().toLowerCase(java.util.Locale.ROOT)).getString()
                + (layer.hasHeight() ? " Y " + MapClient.height(layer) : "") + "  ×" + String.format(java.util.Locale.ROOT, "%.2g", MapSettings.zoom()), left + 7, y, w - 14, opacity);
    }

    private static void info(GuiGraphicsExtractor g, String value, int x, int y, int width, float opacity) {
        if (width <= 0) return;
        var font = Minecraft.getInstance().font;
        YzuiTheme.hudText(g, font, font.plainSubstrByWidth(value, width), x, y, YzuiTheme.text(), opacity);
    }

    /** 绘制地形之上的网格、足迹、绘图、路径点、雷达和自己的方向。 */
    public static void overlay(GuiGraphicsExtractor g, MapView view, String dimension, int left, int top, int radius, float opacity, boolean labels) {
        g.enableScissor(left, top, left + view.width(), top + view.height());
        if (MapSettings.enabled(MapSettings.Toggle.GRID) && view.scale() >= 0.5) {
            double reach = Math.hypot(view.width(), view.height()) / view.scale();
            int minX = (int) Math.floor((view.centerX() - reach) / 16), maxX = (int) Math.ceil((view.centerX() + reach) / 16);
            int minZ = (int) Math.floor((view.centerZ() - reach) / 16), maxZ = (int) Math.ceil((view.centerZ() + reach) / 16);
            int color = YzuiTheme.alpha(YzuiTheme.text(), opacity * 0.25f);
            if (maxX - minX < 256 && maxZ - minZ < 256) {
                for (int x = minX; x <= maxX; x++) worldLine(g, view, left, top, radius, x * 16.0, minZ * 16.0, x * 16.0, maxZ * 16.0, color, 1);
                for (int z = minZ; z <= maxZ; z++) worldLine(g, view, left, top, radius, minX * 16.0, z * 16.0, maxX * 16.0, z * 16.0, color, 1);
            }
        }
        if (MapSettings.enabled(MapSettings.Toggle.EXACT_LOAD_LEVEL) && MapSettings.overlay() == MapSettings.Overlay.LOAD_STATE
                && view.scale() >= 2 && MapClient.loadDimension().equals(dimension)) {
            MapClient.liveLoadLevels().forEach((key, level) -> {
                var p = view.screen((int) (long) key * 16.0 + 8, (int) (key >> 32) * 16.0 + 8);
                if (MapShapes.contains(view, p.x(), p.y(), radius, 12)) info(g, Integer.toString(level), left + (int) p.x() - 6, top + (int) p.y() - 4, 30, opacity);
            });
        }
        if (MapSettings.enabled(MapSettings.Toggle.TRAIL) && dimension.equals(MapClient.dimension())) path(g, view, left, top, radius, MapClient.trail(), YzuiTheme.alpha(YzuiTheme.primary(), 0.7f * opacity), 1);
        if (MapSettings.enabled(MapSettings.Toggle.DRAWINGS)) for (var drawing : MapPersonalData.drawings()) if (drawing.dimension().equals(dimension)) drawing(g, view, left, top, radius, drawing, opacity, false);
        if (MapSettings.enabled(MapSettings.Toggle.WAYPOINTS)) for (var point : MapClient.waypoints()) {
            if (!point.enabled()) continue;
            var projected = point.projected(dimension, MapSettings.enabled(MapSettings.Toggle.PORTAL_PROJECTION));
            if (projected == null) continue;
            var p = view.screen(projected.x(), projected.z());
            if (!MapShapes.contains(view, p.x(), p.y(), radius, 7)) continue;
            int x = left + (int) p.x(), y = top + (int) p.y();
            int color = YzuiTheme.alpha(point.color(), opacity);
            String symbol = point.kind() == MapWaypoint.Kind.DEATH ? "×" : point.kind() == MapWaypoint.Kind.STRUCTURE ? "◆" : point.shared() ? "▣" : "●";
            infoColored(g, symbol, x - 3, y - 4, color);
            if (point.id().equals(MapClient.navigation())) g.outline(x - 6, y - 6, 13, 13, color);
            if (labels && MapShapes.contains(view, p.x(), p.y(), radius, 18)) info(g, MapTexts.waypoint(point).getString()
                    + (point.dimension().equals(dimension) ? "" : " ↔"), x + 8, y - 4, Math.max(0, view.width() - (int) p.x() - 20), opacity);
        }
        for (var radar : MapClient.radar()) {
            if (!radar.dimension().equals(dimension)) continue;
            var p = view.screen(radar.x(), radar.z());
            if (!MapShapes.contains(view, p.x(), p.y(), radius, 8)) continue;
            int x = left + (int) p.x(), y = top + (int) p.y();
            boolean head = false;
            if (radar.player() && MapSettings.enabled(MapSettings.Toggle.RADAR_ICONS)) {
                var client = Minecraft.getInstance();
                var info = client.getConnection() == null ? null : client.getConnection().getPlayerInfo(radar.id());
                var skin = radar.entity() instanceof AbstractClientPlayer player ? player.getSkin() : info == null ? null : info.getSkin();
                if (skin != null) {
                    var texture = skin.body().texturePath();
                    int tint = YzuiTheme.alpha(0xFFFFFFFF, opacity);
                    g.blit(RenderPipelines.GUI_TEXTURED, texture, x - 4, y - 4, 8, 8, 8, 8, 64, 64, tint);
                    g.blit(RenderPipelines.GUI_TEXTURED, texture, x - 4, y - 4, 40, 8, 8, 8, 64, 64, tint); head = true;
                }
            }
            if (!head) RoundedRect.fill(g, x - 2, y - 2, 5, 5, radar.player() ? 1 : 2, YzuiTheme.alpha(radar.color(), opacity));
            if (radar.id().equals(MapClient.trackedId())) g.outline(x - 6, y - 6, 13, 13, YzuiTheme.alpha(YzuiTheme.primary(), opacity));
            if (labels && MapShapes.contains(view, p.x(), p.y(), radius, 20)) info(g, radar.name(), x + 8, y - 4, Math.max(0, view.width() - (int) p.x() - 20), opacity);
        }
        var player = Minecraft.getInstance().player;
        if (player != null && dimension.equals(MapClient.dimension())) {
            var p = view.screen(player.getX(), player.getZ());
            if (MapShapes.contains(view, p.x(), p.y(), radius, 8)) arrow(g, left + p.x(), top + p.y(), Math.toRadians(player.getYRot()) + Math.PI - view.angle(), YzuiTheme.alpha(0xFFFFFFFF, opacity));
        }
        // 方位字母位于地图轮廓内部，旋转模式下同步旋转方位。
        String[] directions = {"N", "E", "S", "W"};
        for (int i = 0; i < 4; i++) {
            double a = i * Math.PI / 2 - view.angle();
            double x = view.width() / 2.0 + Math.sin(a) * (Math.min(view.width(), view.height()) / 2.0 - 12);
            double y = view.height() / 2.0 - Math.cos(a) * (Math.min(view.width(), view.height()) / 2.0 - 12);
            info(g, directions[i], left + (int) x - 3, top + (int) y - 4, 10, opacity);
        }
        g.disableScissor();
    }

    private static void infoColored(GuiGraphicsExtractor g, String text, int x, int y, int color) {
        g.text(Minecraft.getInstance().font, text, x, y, color, true);
    }

    /** 绘图预览与已保存标注使用相同绘制逻辑。 */
    public static void drawing(GuiGraphicsExtractor g, MapView view, int left, int top, int radius, MapDrawing drawing, float opacity, boolean selected) {
        int color = YzuiTheme.alpha(drawing.color(), opacity);
        if (drawing.kind() == MapDrawing.Kind.LABEL) {
            var vertex = drawing.vertices().getFirst(); var p = view.screen(vertex.x(), vertex.z());
            if (MapShapes.contains(view, p.x(), p.y(), radius, 10)) {
                String text = Minecraft.getInstance().font.plainSubstrByWidth(drawing.label(),
                        MapShapes.textWidth(view, p.x(), p.y(), radius, Minecraft.getInstance().font.lineHeight));
                infoColored(g, text, left + (int) p.x(), top + (int) p.y(), color);
                if (selected) g.outline(left + (int) p.x() - 2, top + (int) p.y() - 2, Minecraft.getInstance().font.width(text) + 4, 13, color);
            }
        } else path(g, view, left, top, radius, MapShapes.path(drawing), color, selected ? 3 : 2);
    }

    private static void path(GuiGraphicsExtractor g, MapView view, int left, int top, int radius, List<MapVertex> points, int color, int width) {
        for (int i = 1; i < points.size(); i++) worldLine(g, view, left, top, radius, points.get(i - 1).x(), points.get(i - 1).z(), points.get(i).x(), points.get(i).z(), color, width);
    }

    private static void worldLine(GuiGraphicsExtractor g, MapView view, int left, int top, int radius, double x1, double z1, double x2, double z2, int color, int width) {
        var a = view.screen(x1, z1); var b = view.screen(x2, z2);
        MapShapes.line(g, view, left, top, radius, a.x(), a.y(), b.x(), b.y(), color, width);
    }

    private static void arrow(GuiGraphicsExtractor g, double x, double y, double angle, int color) {
        double fx = Math.sin(angle), fy = -Math.cos(angle);
        MapShapes.rawLine(g, x + fx * 6, y + fy * 6, x - fx * 4 + fy * 4, y - fy * 4 - fx * 4, color, 2);
        MapShapes.rawLine(g, x + fx * 6, y + fy * 6, x - fx * 4 - fy * 4, y - fy * 4 + fx * 4, color, 2);
    }

    private static void navigation(GuiGraphicsExtractor g, float opacity) {
        var client = Minecraft.getInstance(); var player = client.player;
        var camera = client.gameRenderer.mainCamera(); var origin = camera.position();
        var matrix = camera.getViewRotationProjectionMatrix(new Matrix4f());
        record Target(MapWaypoint point, MapVertex position, double distance) { }
        var targets = new ArrayList<Target>();
        for (var point : MapClient.waypoints()) {
            if (!point.enabled() || MapClient.navigation() != null && !point.id().equals(MapClient.navigation())) continue;
            var target = point.projected(MapClient.dimension(), MapSettings.enabled(MapSettings.Toggle.PORTAL_PROJECTION));
            if (target == null) continue;
            double distance = Math.hypot(target.x() - player.getX(), target.z() - player.getZ());
            if (distance > MapSettings.waypointDistance()) continue;
            targets.add(new Target(point, target, distance));
        }
        // 先换算维度并排除不可显示的点，再选择最近八个，避免其他维度挤占名额。
        targets.sort(Comparator.comparingDouble(Target::distance));
        int edgeIndex = 0;
        for (var candidate : targets.subList(0, Math.min(8, targets.size()))) {
            var point = candidate.point(); var target = candidate.position(); double distance = candidate.distance();
            float targetY = point.dimension().equals(MapClient.dimension()) ? point.y() + 2 : (float) player.getY() + 2;
            var clip = matrix.transform(new Vector4f((float) (target.x() - origin.x), targetY - (float) origin.y, (float) (target.z() - origin.z), 1));
            boolean visible = clip.w > 0.01f && Math.abs(clip.x) < clip.w * 0.9f && Math.abs(clip.y) < clip.w * 0.8f;
            if (!visible && MapClient.navigation() == null) continue;
            int x, y;
            if (visible) {
                x = (int) ((clip.x / clip.w * 0.5 + 0.5) * g.guiWidth());
                y = (int) ((0.5 - clip.y / clip.w * 0.5) * g.guiHeight());
            } else {
                x = clip.x < 0 ? 20 : g.guiWidth() - 20; y = g.guiHeight() / 2 + edgeIndex++ * 24;
                arrow(g, x, y, clip.x < 0 ? -Math.PI / 2 : Math.PI / 2, YzuiTheme.alpha(point.color(), opacity));
            }
            String text = MapTexts.waypoint(point).getString() + " · " + (int) distance + "m" + (point.dimension().equals(MapClient.dimension()) ? "" : " ↔");
            text = client.font.plainSubstrByWidth(text, Math.max(50, g.guiWidth() / 3));
            int tx = Math.clamp(x - client.font.width(text) / 2, 6, Math.max(6, g.guiWidth() - client.font.width(text) - 6));
            YzuiTheme.hudLabel(g, client.font, text, tx, y + 9, point.color(), opacity);
            if (visible) { g.outline(x - 3, y - 3, 7, 7, YzuiTheme.alpha(point.color(), opacity)); }
        }
    }

    /** 切服时释放小地图纹理。 */
    public static void reset() { MINIMAP.close(); }
}
