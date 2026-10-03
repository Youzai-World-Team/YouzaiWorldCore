package top.csituka.youzaiworldcore.client.map;

import com.google.gson.JsonArray;
import top.csituka.youzaiworldcore.map.MapTileKey;
import top.csituka.youzaiworldcore.map.MapLayer;
import net.minecraft.resources.Identifier;
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

    /** 空分类和真实到访层均随当前服务器存档保存，绝不从服务端批量采样反推。 */
    public record Category(String dimension, String name) {
    }

    private static final List<Category> CATEGORIES = new ArrayList<>();
    private static final Set<MapTileKey> EXPLORED = new HashSet<>();
    private static final int MAX_CATEGORIES = 128, MAX_EXPLORED = 65536;

    /** 当前存档上次关闭地图时的视角，不影响其他服务器。 */
    public record LastView(String dimension, double x, double z, double zoom) {
    }

    private static LastView lastView;
    private static final java.util.Map<UUID, String> NOTES = new java.util.LinkedHashMap<>();
    private static boolean explorationDirty;
    private static String world = "", worldName = "";
    private static boolean writableWorld;

    private MapPersonalData() {
    }

    /** 切换存档时仅载入对应记录，服务器地址相同但存档 UUID 不同也不混用。 */
    public static void selectWorld(String identity, String label) {
        if (world.equals(identity))
            return;
        flushExploration();
        world = identity;
        worldName = label;
        lastView = null;
        POINTS.clear();
        DRAWINGS.clear();
        UNDO.clear();
        REDO.clear();
        CATEGORIES.clear();
        EXPLORED.clear();
        NOTES.clear();
        explorationDirty = false;
        var section = ClientGlobalSettings.section(ClientGlobalSettings.MAP_MODULE);
        var worlds = section.getObjectList("worlds");
        writableWorld = worlds == null || worlds.size() < 512;
        if (worlds != null) {
            if (worlds.size() > 512)
                section.fail("worlds", "世界记录数量不可超过 512");
            boolean found = false;
            Set<UUID> worldIds = new HashSet<>();
            for (var entry : worlds) {
                UUID worldId = MapDataCodec.uuid(entry, "id");
                if (!worldIds.add(worldId))
                    entry.fail("id", "世界身份重复");
                if (!identity.equals(worldId.toString()))
                    continue;
                if (found)
                    entry.fail("id", "世界身份重复");
                found = true;
                writableWorld = true;
                var views = entry.getObjectList("last_view");
                if (views != null && !views.isEmpty()) {
                    if (views.size() > 1)
                        entry.fail("last_view", "上次地图视角只能有一条记录");
                    var view = views.getFirst();
                    String dimension = view.getString("dimension", "");
                    if (dimension.length() > 128 || Identifier.tryParse(dimension) == null)
                        view.fail("dimension", "维度无效");
                    double x = view.getDouble("x", 0), z = view.getDouble("z", 0), zoom = view.getDouble("zoom", 1);
                    if (!Double.isFinite(x) || Math.abs(x) > MapTileKey.WORLD_LIMIT)
                        view.fail("x", "地图坐标越界");
                    if (!Double.isFinite(z) || Math.abs(z) > MapTileKey.WORLD_LIMIT)
                        view.fail("z", "地图坐标越界");
                    if (!Double.isFinite(zoom) || zoom < 0.125 || zoom > 16)
                        view.fail("zoom", "缩放应介于 0.125 和 16 之间");
                    lastView = new LastView(dimension, x, z, zoom);
                }
                var notes = entry.getObjectList("point_notes");
                if (notes != null) {
                    if (notes.size() > 2048)
                        entry.fail("point_notes", "描述数量不可超过 2048");
                    for (var note : notes) {
                        UUID id = MapDataCodec.uuid(note, "id");
                        String text = note.getString("text", "");
                        if (text.length() > 1024)
                            note.fail("text", "描述不可超过 1024 字");
                        if (NOTES.putIfAbsent(id, text) != null)
                            note.fail("id", "描述身份重复");
                    }
                }
                var categories = entry.getObjectList("categories");
                if (categories != null) {
                    if (categories.size() > MAX_CATEGORIES)
                        entry.fail("categories", "分类数量不可超过 128");
                    for (var category : categories) {
                        String dimension = category.getString("dimension", ""), name = category.getString("name", "");
                        if (dimension.length() > 128 || Identifier.tryParse(dimension) == null)
                            category.fail("dimension", "维度无效");
                        if (name.isBlank() || name.length() > 32)
                            category.fail("name", "分类名长度应为 1 至 32");
                        var value = new Category(dimension, name.strip());
                        if (CATEGORIES.contains(value))
                            category.fail("name", "同一维度内分类重复");
                        CATEGORIES.add(value);
                    }
                }
                var explored = entry.getObjectList("explored_layers");
                if (explored != null) {
                    if (explored.size() > MAX_EXPLORED)
                        entry.fail("explored_layers", "已探索层记录不可超过 65536");
                    for (var region : explored) {
                        String dimension = region.getString("dimension", "");
                        if (dimension.length() > 128 || Identifier.tryParse(dimension) == null)
                            region.fail("dimension", "维度无效");
                        var key = new MapTileKey(dimension, MapLayer.CAVE, region.getInt("height", 0, -4096, 4095),
                                region.getInt("chunk_x", 0, -MapTileKey.CHUNK_LIMIT, MapTileKey.CHUNK_LIMIT),
                                region.getInt("chunk_z", 0, -MapTileKey.CHUNK_LIMIT, MapTileKey.CHUNK_LIMIT));
                        if (!EXPLORED.add(key))
                            region.fail("height", "同一区块的探索层重复");
                    }
                }
                var points = entry.getObjectList("waypoints");
                var drawings = entry.getObjectList("drawings");
                if (points != null) {
                    if (points.size() > MAX_POINTS)
                        entry.fail("waypoints", "私人路径点数量不可超过 512");
                    Set<UUID> ids = new HashSet<>();
                    for (var point : points) {
                        var value = MapDataCodec.readWaypoint(point);
                        if (value.shared() || !ids.add(value.id()))
                            point.fail("id", "私人路径点身份重复或错误地标记为公共点");
                        POINTS.add(value);
                    }
                }
                if (drawings != null) {
                    if (drawings.size() > MAX_DRAWINGS)
                        entry.fail("drawings", "绘图数量不可超过 128");
                    Set<UUID> ids = new HashSet<>();
                    for (var drawing : drawings) {
                        var value = MapDataCodec.readDrawing(drawing);
                        if (!ids.add(value.id()))
                            drawing.fail("id", "绘图身份重复");
                        DRAWINGS.add(value);
                    }
                }
            }
        }
        DebugLogger.info("MapPersonalData", "地图私人数据已切换：%s，路径点=%d，绘图=%d", world, POINTS.size(), DRAWINGS.size());
    }

    public static List<MapWaypoint> points() {
        return List.copyOf(POINTS);
    }

    public static List<MapDrawing> drawings() {
        return List.copyOf(DRAWINGS);
    }

    public static String worldId() {
        return world;
    }

    /** 当前存档上次浏览的地图位置；从未保存时返回 null。 */
    public static LastView lastView() {
        return lastView;
    }

    /** 在启用记忆功能时保存地图中心与缩放，不创建额外配置文件。 */
    public static void rememberView(String dimension, double x, double z, double zoom) {
        if (world.isEmpty() || !writableWorld)
            return;
        lastView = new LastView(dimension, x, z, zoom);
        save();
        DebugLogger.debug("MapPersonalData", "保存地图视角：%s %.1f %.1f", dimension, x, z);
    }

    /** 当前维度内用户建立的分类，允许分类中暂时没有路径点。 */
    @SuppressWarnings("null")
    public static List<String> categories(String dimension) {
        return CATEGORIES.stream().filter(value -> value.dimension().equals(dimension)).map(Category::name).toList();
    }

    /** 建立可持久保存的空分类。 */
    public static boolean addCategory(String dimension, String name) {
        if (world.isEmpty() || !writableWorld || name.isBlank() || name.length() > 32
                || dimension.length() > 128 || Identifier.tryParse(dimension) == null)
            return false;
        var category = new Category(dimension, name.strip());
        if (CATEGORIES.contains(category))
            return true;
        if (CATEGORIES.size() >= MAX_CATEGORIES)
            return false;
        CATEGORIES.add(category);
        save();
        DebugLogger.info("MapPersonalData", "新建路径点分类：%s / %s", dimension, name);
        return true;
    }

    /** 对同一筛选结果中的两个私人点交换顺序，不改动创建时间及公共数据。 */
    public static void swap(UUID first, UUID second) {
        int a = -1, b = -1;
        for (int i = 0; i < POINTS.size(); i++) {
            if (POINTS.get(i).id().equals(first))
                a = i;
            if (POINTS.get(i).id().equals(second))
                b = i;
        }
        if (a < 0 || b < 0 || a == b)
            return;
        java.util.Collections.swap(POINTS, a, b);
        save();
        DebugLogger.debug("MapPersonalData", "调整私人路径点顺序");
    }

    /** 仅由玩家实际位置记录到访；后台切片和地图拖动不得调用。 */
    public static void explore(String dimension, int chunkX, int chunkZ, int height) {
        if (world.isEmpty() || !writableWorld || EXPLORED.size() >= MAX_EXPLORED)
            return;
        if (EXPLORED.add(new MapTileKey(dimension, MapLayer.CAVE, height, chunkX, chunkZ))) {
            explorationDirty = true;
            DebugLogger.debug("MapPersonalData", "发现地下地图层：%s [%d,%d] Y=%d", dimension, chunkX, chunkZ, height);
        }
    }

    /** 当前所在区块的已探索地下层，按从高到低排列。 */
    @SuppressWarnings("null")
    public static List<Integer> exploredHeights(String dimension, int chunkX, int chunkZ) {
        return EXPLORED.stream()
                .filter(key -> key.dimension().equals(dimension) && key.chunkX() == chunkX && key.chunkZ() == chunkZ)
                .map(MapTileKey::height).sorted(java.util.Comparator.reverseOrder()).toList();
    }

    /** 到访记录合并写盘，切服及断线前也须调用。 */
    public static void flushExploration() {
        if (explorationDirty)
            save();
    }

    /** 新增或修改私人点；达到容量时返回 false，原记录保持不变。 */
    public static boolean put(MapWaypoint point) {
        if (point.shared() || world.isEmpty() || !writableWorld)
            return false;
        int index = -1;
        for (int i = 0; i < POINTS.size(); i++)
            if (POINTS.get(i).id().equals(point.id()))
                index = i;
        if (index < 0 && POINTS.size() >= MAX_POINTS)
            return false;
        if (index < 0)
            POINTS.add(point);
        else
            POINTS.set(index, point);
        save();
        DebugLogger.info("MapPersonalData", "已保存私人路径点：%s", point.name());
        return true;
    }

    /** 私人描述按存档和标记身份保存，公共点与锚点的描述也仅本人可见。 */
    public static String note(UUID id) {
        return NOTES.getOrDefault(id, "");
    }

    /** 保存私人描述；不修改服务端标记。 */
    public static boolean note(UUID id, String text) {
        if (world.isEmpty() || !writableWorld || text.length() > 1024
                || !text.isBlank() && !NOTES.containsKey(id) && NOTES.size() >= 2048)
            return false;
        if (text.isBlank())
            NOTES.remove(id);
        else
            NOTES.put(id, text);
        save();
        DebugLogger.debug("MapPersonalData", "保存标记私人描述：%s", id);
        return true;
    }

    /** 删除私人点。 */
    public static void remove(UUID id) {
        if (POINTS.removeIf(point -> point.id().equals(id))) {
            NOTES.remove(id);
            save();
        }
    }

    /** 批量移动选中的私人点到指定分组。 */
    public static void group(Set<UUID> ids, String group) {
        POINTS.replaceAll(point -> ids.contains(point.id()) ? point.withGroup(group) : point);
        save();
    }

    /** 一次写盘批量改变私人分组的显示状态。 */
    public static void setEnabled(Set<UUID> ids, boolean enabled) {
        POINTS.replaceAll(point -> ids.contains(point.id()) ? point.withEnabled(enabled) : point);
        save();
    }

    /** 完整预检容量并去除同名同坐标重复点；负数表示未写入任何内容。 */
    public static int importPoints(List<MapWaypoint> points) {
        if (world.isEmpty() || !writableWorld)
            return -1;
        var additions = new ArrayList<MapWaypoint>();
        var identities = new HashSet<String>();
        POINTS.forEach(point -> identities.add(identity(point)));
        for (var point : points) {
            if (point.shared())
                return -1;
            if (identities.add(identity(point)))
                additions.add(point);
        }
        if (POINTS.size() + additions.size() > MAX_POINTS)
            return -1;
        POINTS.addAll(additions);
        save();
        DebugLogger.info("MapPersonalData", "已导入 %d 个私人路径点", additions.size());
        return additions.size();
    }

    private static String identity(MapWaypoint point) {
        return point.dimension() + "\u0000" + point.x() + "/" + point.y() + "/" + point.z() + "\u0000" + point.name();
    }

    /** 自动记录死亡点，每个维度独立应用保留数量。 */
    @SuppressWarnings("null")
    public static void death(UUID player, String dimension, int x, int y, int z) {
        if (MapSettings.deathLimit() == 0)
            return;
        if (Math.abs((long) x) > top.csituka.youzaiworldcore.map.MapTileKey.WORLD_LIMIT
                || Math.abs((long) y) > top.csituka.youzaiworldcore.map.MapTileKey.WORLD_LIMIT
                || Math.abs((long) z) > top.csituka.youzaiworldcore.map.MapTileKey.WORLD_LIMIT) {
            MapClient.message("invalid_position");
            return;
        }
        @SuppressWarnings("null")
        var deaths = POINTS.stream().filter(point -> point.kind() == MapWaypoint.Kind.DEATH
                && point.dimension().equals(dimension))
                .sorted(java.util.Comparator.comparingLong(MapWaypoint::createdAt)).toList();
        int remove = Math.max(0, deaths.size() - MapSettings.deathLimit() + 1);
        for (int i = 0; i < remove; i++)
            POINTS.remove(deaths.get(i));
        if (POINTS.size() >= MAX_POINTS)
            POINTS.stream().filter(point -> point.kind() == MapWaypoint.Kind.DEATH)
                    .min(java.util.Comparator.comparingLong(MapWaypoint::createdAt)).ifPresent(POINTS::remove);
        String time = LocalDateTime.now().format(DateTimeFormatter.ofPattern("MM-dd HH:mm"));
        if (!put(new MapWaypoint(UUID.randomUUID(), player,
                Component.translatable("map.youzaiworldcore.death_name", time).getString(),
                Component.translatable("map.youzaiworldcore.deaths").getString(), dimension,
                x, y, z, 0xFFE47A78, true, false, MapWaypoint.Kind.DEATH, System.currentTimeMillis())))
            MapClient.message("death_limit_reached");
    }

    /** 新增或替换一条绘图，一次操作对应一个撤销步骤。 */
    public static boolean putDrawing(MapDrawing drawing) {
        if (world.isEmpty() || !writableWorld)
            return false;
        boolean exists = DRAWINGS.stream().anyMatch(value -> value.id().equals(drawing.id()));
        if (!exists && DRAWINGS.size() >= MAX_DRAWINGS)
            return false;
        remember();
        DRAWINGS.removeIf(value -> value.id().equals(drawing.id()));
        DRAWINGS.add(drawing);
        save();
        return true;
    }

    /** 删除绘图；仍可撤销。 */
    public static void removeDrawing(UUID id) {
        if (DRAWINGS.stream().noneMatch(value -> value.id().equals(id)))
            return;
        remember();
        DRAWINGS.removeIf(value -> value.id().equals(id));
        save();
    }

    public static void undo() {
        restore(UNDO, REDO);
    }

    public static void redo() {
        restore(REDO, UNDO);
    }

    private static void remember() {
        UNDO.push(List.copyOf(DRAWINGS));
        while (UNDO.size() > 32)
            UNDO.removeLast();
        REDO.clear();
    }

    private static void restore(ArrayDeque<List<MapDrawing>> source, ArrayDeque<List<MapDrawing>> target) {
        if (source.isEmpty())
            return;
        target.push(List.copyOf(DRAWINGS));
        DRAWINGS.clear();
        DRAWINGS.addAll(source.pop());
        save();
    }

    @SuppressWarnings("null")
    private static void save() {
        if (world.isEmpty())
            return;
        var section = ClientGlobalSettings.section(ClientGlobalSettings.MAP_MODULE);
        var worlds = section.getObjectList("worlds");
        JsonArray replacement = new JsonArray();
        if (worlds != null)
            for (var value : worlds) {
                if (!MapDataCodec.uuid(value, "id").toString().equals(world))
                    replacement.add(value.raw());
            }
        JsonObject entry = new JsonObject();
        entry.addProperty("id", world);
        entry.addProperty("name", worldName);
        JsonArray views = new JsonArray();
        if (lastView != null) {
            JsonObject view = new JsonObject();
            view.addProperty("dimension", lastView.dimension());
            view.addProperty("x", lastView.x());
            view.addProperty("z", lastView.z());
            view.addProperty("zoom", lastView.zoom());
            views.add(view);
        }
        entry.add("last_view", views);
        JsonArray notes = new JsonArray();
        NOTES.forEach((id, text) -> {
            JsonObject note = new JsonObject();
            note.addProperty("id", id.toString());
            note.addProperty("text", text);
            notes.add(note);
        });
        entry.add("point_notes", notes);
        JsonArray categories = new JsonArray(), explored = new JsonArray();
        for (var category : CATEGORIES) {
            JsonObject value = new JsonObject();
            value.addProperty("dimension", category.dimension());
            value.addProperty("name", category.name());
            categories.add(value);
        }
        EXPLORED.stream()
                .sorted(java.util.Comparator.comparing(MapTileKey::dimension).thenComparingInt(MapTileKey::chunkX)
                        .thenComparingInt(MapTileKey::chunkZ).thenComparingInt(MapTileKey::height))
                .forEach(key -> {
                    JsonObject value = new JsonObject();
                    value.addProperty("dimension", key.dimension());
                    value.addProperty("chunk_x", key.chunkX());
                    value.addProperty("chunk_z", key.chunkZ());
                    value.addProperty("height", key.height());
                    explored.add(value);
                });
        entry.add("categories", categories);
        entry.add("explored_layers", explored);
        JsonArray points = new JsonArray(), drawings = new JsonArray();
        POINTS.forEach(point -> points.add(MapDataCodec.writeWaypoint(point)));
        DRAWINGS.forEach(drawing -> drawings.add(MapDataCodec.writeDrawing(drawing)));
        entry.add("waypoints", points);
        entry.add("drawings", drawings);
        replacement.add(entry);
        section.set("worlds", replacement);
        ClientGlobalSettings.save();
        explorationDirty = false;
    }

    /** 断线时清空当前身份，重连会重新核验配置。 */
    public static void disconnect() {
        flushExploration();
        lastView = null;
        CATEGORIES.clear();
        EXPLORED.clear();
        NOTES.clear();
        explorationDirty = false;
        world = "";
        writableWorld = false;
        POINTS.clear();
        DRAWINGS.clear();
        UNDO.clear();
        REDO.clear();
    }
}
