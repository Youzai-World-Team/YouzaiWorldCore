package top.csituka.youzaiworldcore.client.map;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.network.chat.Component;
import top.csituka.youzaiworldcore.client.config.ClientGlobalSettings;
import top.csituka.youzaiworldcore.client.config.MapSettings;
import top.csituka.youzaiworldcore.map.MapDataCodec;
import top.csituka.youzaiworldcore.map.MapDrawing;
import top.csituka.youzaiworldcore.map.MapWaypoint;
import top.csituka.youzaiworldcore.util.DebugLogger;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * 按服务器存档身份隔离的私人路径点和标注，存入客户端唯一配置文件。
 * 历史操作只保留内存中最近 32 步；移动足迹不落盘。
 */
public final class MapPersonalData {
    public static final int MAX_POINTS = 512, MAX_DRAWINGS = 128;
    private static final List<MapWaypoint> POINTS = new ArrayList<>();
    private static final List<MapDrawing> DRAWINGS = new ArrayList<>();
    private static final ArrayDeque<List<MapDrawing>> UNDO = new ArrayDeque<>(), REDO = new ArrayDeque<>();
    private static String world = "", worldName = "";
    private static boolean writableWorld;
    private MapPersonalData() { }

    /** 切换存档时仅载入对应记录，服务器地址相同但存档 UUID 不同也不混用。 */
    public static void selectWorld(String identity, String label) {
        if (world.equals(identity)) return;
        world = identity; worldName = label;
        POINTS.clear(); DRAWINGS.clear(); UNDO.clear(); REDO.clear();
        var section = ClientGlobalSettings.section(ClientGlobalSettings.MAP_MODULE);
        var worlds = section.getObjectList("worlds");
        writableWorld = worlds == null || worlds.size() < 512;
        if (worlds != null) {
            if (worlds.size() > 512) section.fail("worlds", "世界记录数量不可超过 512");
            boolean found = false;
            Set<UUID> worldIds = new HashSet<>();
            for (var entry : worlds) {
                UUID worldId = MapDataCodec.uuid(entry, "id");
                if (!worldIds.add(worldId)) entry.fail("id", "世界身份重复");
                if (!identity.equals(worldId.toString())) continue;
                if (found) entry.fail("id", "世界身份重复");
                found = true;
                writableWorld = true;
                var points = entry.getObjectList("waypoints");
                var drawings = entry.getObjectList("drawings");
                if (points != null) {
                    if (points.size() > MAX_POINTS) entry.fail("waypoints", "私人路径点数量不可超过 512");
                    Set<UUID> ids = new HashSet<>();
                    for (var point : points) {
                        var value = MapDataCodec.readWaypoint(point);
                        if (value.shared() || !ids.add(value.id())) point.fail("id", "私人路径点身份重复或错误地标记为公共点");
                        POINTS.add(value);
                    }
                }
                if (drawings != null) {
                    if (drawings.size() > MAX_DRAWINGS) entry.fail("drawings", "绘图数量不可超过 128");
                    Set<UUID> ids = new HashSet<>();
                    for (var drawing : drawings) {
                        var value = MapDataCodec.readDrawing(drawing);
                        if (!ids.add(value.id())) drawing.fail("id", "绘图身份重复");
                        DRAWINGS.add(value);
                    }
                }
            }
        }
        DebugLogger.info("MapPersonalData", "地图私人数据已切换：%s，路径点=%d，绘图=%d", world, POINTS.size(), DRAWINGS.size());
    }

    public static List<MapWaypoint> points() { return List.copyOf(POINTS); }
    public static List<MapDrawing> drawings() { return List.copyOf(DRAWINGS); }
    public static String worldId() { return world; }

    /** 新增或修改私人点；达到容量时返回 false，原记录保持不变。 */
    public static boolean put(MapWaypoint point) {
        if (point.shared() || world.isEmpty() || !writableWorld) return false;
        int index = -1;
        for (int i = 0; i < POINTS.size(); i++) if (POINTS.get(i).id().equals(point.id())) index = i;
        if (index < 0 && POINTS.size() >= MAX_POINTS) return false;
        if (index < 0) POINTS.add(point); else POINTS.set(index, point);
        save();
        DebugLogger.info("MapPersonalData", "已保存私人路径点：%s", point.name());
        return true;
    }

    /** 删除私人点。 */
    public static void remove(UUID id) { if (POINTS.removeIf(point -> point.id().equals(id))) save(); }

    /** 批量移动选中的私人点到指定分组。 */
    public static void group(Set<UUID> ids, String group) {
        POINTS.replaceAll(point -> ids.contains(point.id()) ? point.withGroup(group) : point);
        save();
    }

    /** 一次写盘批量改变私人分组的显示状态。 */
    public static void setEnabled(Set<UUID> ids, boolean enabled) {
        POINTS.replaceAll(point -> ids.contains(point.id()) ? point.withEnabled(enabled) : point); save();
    }

    /** 完整预检容量并去除同名同坐标重复点；负数表示未写入任何内容。 */
    public static int importPoints(List<MapWaypoint> points) {
        if (world.isEmpty() || !writableWorld) return -1;
        var additions = new ArrayList<MapWaypoint>();
        var identities = new HashSet<String>();
        POINTS.forEach(point -> identities.add(identity(point)));
        for (var point : points) {
            if (point.shared()) return -1;
            if (identities.add(identity(point))) additions.add(point);
        }
        if (POINTS.size() + additions.size() > MAX_POINTS) return -1;
        POINTS.addAll(additions); save();
        DebugLogger.info("MapPersonalData", "已导入 %d 个私人路径点", additions.size());
        return additions.size();
    }

    private static String identity(MapWaypoint point) {
        return point.dimension() + "\u0000" + point.x() + "/" + point.y() + "/" + point.z() + "\u0000" + point.name();
    }

    /** 自动记录死亡点，每个维度独立应用保留数量。 */
    public static void death(UUID player, String dimension, int x, int y, int z) {
        if (MapSettings.deathLimit() == 0) return;
        if (Math.abs((long) x) > top.csituka.youzaiworldcore.map.MapTileKey.WORLD_LIMIT
                || Math.abs((long) y) > top.csituka.youzaiworldcore.map.MapTileKey.WORLD_LIMIT
                || Math.abs((long) z) > top.csituka.youzaiworldcore.map.MapTileKey.WORLD_LIMIT) { MapClient.message("invalid_position"); return; }
        var deaths = POINTS.stream().filter(point -> point.kind() == MapWaypoint.Kind.DEATH
                && point.dimension().equals(dimension)).sorted(java.util.Comparator.comparingLong(MapWaypoint::createdAt)).toList();
        int remove = Math.max(0, deaths.size() - MapSettings.deathLimit() + 1);
        for (int i = 0; i < remove; i++) POINTS.remove(deaths.get(i));
        if (POINTS.size() >= MAX_POINTS) POINTS.stream().filter(point -> point.kind() == MapWaypoint.Kind.DEATH)
                .min(java.util.Comparator.comparingLong(MapWaypoint::createdAt)).ifPresent(POINTS::remove);
        String time = LocalDateTime.now().format(DateTimeFormatter.ofPattern("MM-dd HH:mm"));
        if (!put(new MapWaypoint(UUID.randomUUID(), player, Component.translatable("map.youzaiworldcore.death_name", time).getString(),
                Component.translatable("map.youzaiworldcore.deaths").getString(), dimension,
                x, y, z, 0xFFE47A78, true, false, MapWaypoint.Kind.DEATH, System.currentTimeMillis()))) MapClient.message("death_limit_reached");
    }

    /** 新增或替换一条绘图，一次操作对应一个撤销步骤。 */
    public static boolean putDrawing(MapDrawing drawing) {
        if (world.isEmpty() || !writableWorld) return false;
        boolean exists = DRAWINGS.stream().anyMatch(value -> value.id().equals(drawing.id()));
        if (!exists && DRAWINGS.size() >= MAX_DRAWINGS) return false;
        remember();
        DRAWINGS.removeIf(value -> value.id().equals(drawing.id()));
        DRAWINGS.add(drawing);
        save();
        return true;
    }

    /** 删除绘图；仍可撤销。 */
    public static void removeDrawing(UUID id) {
        if (DRAWINGS.stream().noneMatch(value -> value.id().equals(id))) return;
        remember(); DRAWINGS.removeIf(value -> value.id().equals(id)); save();
    }

    public static void undo() { restore(UNDO, REDO); }
    public static void redo() { restore(REDO, UNDO); }

    private static void remember() {
        UNDO.push(List.copyOf(DRAWINGS));
        while (UNDO.size() > 32) UNDO.removeLast();
        REDO.clear();
    }

    private static void restore(ArrayDeque<List<MapDrawing>> source, ArrayDeque<List<MapDrawing>> target) {
        if (source.isEmpty()) return;
        target.push(List.copyOf(DRAWINGS)); DRAWINGS.clear(); DRAWINGS.addAll(source.pop()); save();
    }

    private static void save() {
        if (world.isEmpty()) return;
        var section = ClientGlobalSettings.section(ClientGlobalSettings.MAP_MODULE);
        var worlds = section.getObjectList("worlds");
        JsonArray replacement = new JsonArray();
        if (worlds != null) for (var value : worlds) {
            if (!MapDataCodec.uuid(value, "id").toString().equals(world)) replacement.add(value.raw());
        }
        JsonObject entry = new JsonObject();
        entry.addProperty("id", world); entry.addProperty("name", worldName);
        JsonArray points = new JsonArray(), drawings = new JsonArray();
        POINTS.forEach(point -> points.add(MapDataCodec.writeWaypoint(point)));
        DRAWINGS.forEach(drawing -> drawings.add(MapDataCodec.writeDrawing(drawing)));
        entry.add("waypoints", points); entry.add("drawings", drawings);
        replacement.add(entry);
        section.set("worlds", replacement);
        ClientGlobalSettings.save();
    }

    /** 断线时清空当前身份，重连会重新核验配置。 */
    public static void disconnect() { world = ""; writableWorld = false; POINTS.clear(); DRAWINGS.clear(); UNDO.clear(); REDO.clear(); }
}
