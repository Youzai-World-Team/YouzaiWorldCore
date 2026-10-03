package top.csituka.youzaiworldcore.client.map;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import top.csituka.youzaiworldcore.map.MapEntityLayer;
import top.csituka.youzaiworldcore.network.MapMobQueryPayload;
import top.csituka.youzaiworldcore.network.MapMobSnapshotPayload;
import top.csituka.youzaiworldcore.network.MapSessionPayload;
import top.csituka.youzaiworldcore.util.DebugLogger;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** 生物种类选择及模拟距离查询；关闭面板后继续更新，两张地图共享同一个短时快照。 */
public final class MapMobRadar {
    private static EntityType<?> selected;
    private static UUID request;
    private static String dimension = "";
    private static int radius, ticks, sentTick = -20, parts;
    private static int playerLayer = MapEntityLayer.UNKNOWN;
    private static long sentAt, receivedAt;
    private static final Map<Integer, List<MapMobSnapshotPayload.Target>> pending = new HashMap<>();
    private static List<MapMobSnapshotPayload.Target> remote = List.of();
    private MapMobRadar() { }

    public static EntityType<?> selected() { return selected; }
    public static void select(EntityType<?> type) {
        selected = type; clearSnapshot();
        MapClient.track(null);
        DebugLogger.debug("MapMobRadar", "选择定位种类：%s", type == null ? "清除" : EntityType.getKey(type));
        MapClient.refreshRadar();
    }
    public static int radius() {
        var client = Minecraft.getInstance();
        int setting = client.options.simulationDistance().get();
        if (client.level != null) setting = Math.min(setting, client.level.getServerSimulationDistance());
        return Math.clamp(setting, 1, 32);
    }
    public static boolean serverSearch() {
        var session = MapClient.session();
        return Minecraft.getInstance().getConnection() != null && session != null && session.allows(MapSessionPayload.ENABLED)
                && ClientPlayNetworking.canSend(MapMobQueryPayload.ID);
    }
    public static boolean waiting() { return selected != null && serverSearch() && System.currentTimeMillis() - receivedAt > 3000; }
    public static boolean withinRange(double x, double z) {
        var player = Minecraft.getInstance().player;
        if (player == null) return false;
        int reach = radius();
        return Math.abs(Math.floor(x / 16) - player.chunkPosition().x()) <= reach
                && Math.abs(Math.floor(z / 16) - player.chunkPosition().z()) <= reach;
    }
    public static boolean visible(Entity entity) {
        var player = Minecraft.getInstance().player;
        return player != null && entity instanceof Mob && entity.isAlive() && !entity.isInvisibleTo(player)
                && withinRange(entity.getX(), entity.getZ()) && MapEntityLayer.same(MapEntityLayer.of(entity), playerLayer);
    }
    /** 绘制、点击和跟踪共用过滤；共享玩家与非生物标记不受生物分层规则影响。 */
    public static boolean visibleMarker(MapClient.Radar marker) {
        boolean mob = marker.entity() instanceof Mob || !marker.player() && marker.entity() == null && marker.entityType() != null;
        return !mob || marker.dimension().equals(MapClient.dimension()) && withinRange(marker.x(), marker.z())
                && MapEntityLayer.same(marker.layer(), playerLayer);
    }
    public static boolean selected(MapClient.Radar marker) {
        return selected != null && marker.entityType() == selected && marker.dimension().equals(MapClient.dimension())
                && visibleMarker(marker);
    }
    /** 可见性优先使用当前客户端实体；未被原版同步的实体由服务端快照补充。 */
    public static List<MapClient.Radar> targets() {
        if (selected == null) return List.of();
        var client = Minecraft.getInstance();
        if (client.level == null || client.player == null) return List.of();
        var result = new LinkedHashMap<UUID, MapClient.Radar>();
        if (serverSearch() && dimension.equals(MapClient.dimension()) && System.currentTimeMillis() - receivedAt <= 3000)
            for (var target : remote) if (withinRange(target.x(), target.z()) && MapEntityLayer.same(target.layer(), playerLayer))
                result.put(target.id(), new MapClient.Radar(target.id(), selected.getDescription().getString(), dimension,
                        target.x(), target.y(), target.z(), 0, 0xFFFFFFFF, false, null, selected, target.layer()));
        for (var entity : client.level.entitiesForRendering()) if (entity.getType() == selected) {
            // 死亡、隐身等本地状态立即撤下，不能被稍旧的服务端快照重新加回。
            result.remove(entity.getUUID());
            if (visible(entity)) result.put(entity.getUUID(), MapClient.entityMarker(entity));
        }
        return List.copyOf(result.values());
    }
    public static void tick() {
        ticks++;
        var player = Minecraft.getInstance().player;
        int actualLayer = player == null ? MapEntityLayer.UNKNOWN : MapEntityLayer.of(player);
        if (actualLayer != playerLayer) {
            playerLayer = actualLayer;
            MapClient.refreshRadar();
            DebugLogger.debug("MapMobRadar", "玩家雷达图层更新：%d", playerLayer);
        }
        if (selected == null) return;
        if (!dimension.equals(MapClient.dimension()) || radius != radius()) {
            clearSnapshot(); dimension = MapClient.dimension(); radius = radius();
        }
        if (!serverSearch()) { remote = List.of(); request = null; pending.clear(); return; }
        if (request != null && System.currentTimeMillis() - sentAt > 3000) { request = null; pending.clear(); }
        if (request == null && ticks - sentTick >= 20) {
            request = UUID.randomUUID(); sentTick = ticks; sentAt = System.currentTimeMillis(); parts = 0; pending.clear();
            ClientPlayNetworking.send(new MapMobQueryPayload(request, EntityType.getKey(selected), radius));
        }
    }
    public static void receive(MapMobSnapshotPayload value) {
        if (request == null || System.currentTimeMillis() - sentAt > 3000 || !request.equals(value.request()) || selected == null
                || !EntityType.getKey(selected).equals(value.entityType()) || !MapClient.dimension().equals(value.dimension())
                || !dimension.equals(value.dimension()) || radius != radius()) return;
        if (parts != 0 && parts != value.parts()) return;
        parts = value.parts(); pending.put(value.part(), value.targets());
        if (pending.size() == parts) {
            var complete = new java.util.ArrayList<MapMobSnapshotPayload.Target>();
            for (int i = 0; i < parts; i++) complete.addAll(pending.get(i));
            remote = List.copyOf(complete); receivedAt = System.currentTimeMillis(); pending.clear(); request = null;
            MapClient.refreshRadar();
        }
    }
    private static void clearSnapshot() { request = null; pending.clear(); remote = List.of(); receivedAt = 0; parts = 0; }
    public static void reset() { selected = null; dimension = ""; playerLayer = MapEntityLayer.UNKNOWN; ticks = 0; sentTick = -20; clearSnapshot(); MapMobIcons.clear(); }
}
