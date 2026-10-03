package top.csituka.youzaiworldcore.client.map;

import top.csituka.youzaiworldcore.map.MapTileKey;
import top.csituka.youzaiworldcore.map.MapLayer;
import net.minecraft.resources.Identifier;
import net.minecraft.network.chat.Component;
import top.csituka.youzaiworldcore.client.config.MapSettings;
import top.csituka.youzaiworldcore.map.MapBinaryCodec;
import top.csituka.youzaiworldcore.map.MapDatabase;
import java.util.LinkedHashMap;
import java.util.Map;
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
 * 按服务器存档身份隔离的私人路径点和标注，存入客户端地图 SQLite。
 * 历史操作只保留内存中最近 32 步。
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
    private static final Set<MapTileKey> PENDING_EXPLORATION = new HashSet<>();
    private static boolean explorationDirty;
    private static String world = "";
    private static boolean writableWorld;

    private MapPersonalData() {
    }

    /** 切换存档时仅载入对应记录，服务器地址相同但存档 UUID 不同也不混用。 */
    public static void selectWorld(String identity, String label) {
        if (world.equals(identity))
            return;
        flushExploration();
        world = identity;
        lastView = null;
        POINTS.clear();
        DRAWINGS.clear();
        UNDO.clear();
        REDO.clear();
        CATEGORIES.clear();
        EXPLORED.clear();
        PENDING_EXPLORATION.clear();
        NOTES.clear();
        explorationDirty = false;
        var database = MapSettings.database();
        writableWorld = true;
        database.records(world, "points").values().forEach(bytes -> POINTS.add(MapBinaryCodec.waypoint(bytes)));
        database.records(world, "drawings").values().forEach(bytes -> DRAWINGS.add(MapBinaryCodec.drawing(bytes)));
        database.records(world, "categories").values().forEach(bytes -> CATEGORIES.add(MapBinaryCodec.decode(bytes,
                in -> new Category(in.readUTF(), in.readUTF()))));
        database.records(world, "notes").forEach((id, bytes) -> NOTES.put(UUID.fromString(id), MapBinaryCodec.decode(bytes, in -> in.readUTF())));
        var view = database.records(world, "view").get("last");
        if (view != null) lastView = MapBinaryCodec.decode(view, in -> new LastView(in.readUTF(), in.readDouble(), in.readDouble(), in.readDouble()));
        database.read(db -> {
            try (var query = db.prepareStatement("SELECT dimension,x,z,height FROM explored WHERE world=?")) {
                query.setString(1, world);
                try (var rows = query.executeQuery()) { while (rows.next()) EXPLORED.add(new MapTileKey(rows.getString(1), MapLayer.CAVE, rows.getInt(4), rows.getInt(2), rows.getInt(3))); }
            }
            return null;
        });
        MapClient.cache().selectWorld(identity);
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
            PENDING_EXPLORATION.add(new MapTileKey(dimension, MapLayer.CAVE, height, chunkX, chunkZ));
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
        if (!explorationDirty) return;
        MapSettings.database().transaction(db -> { writeExploration(db); return null; });
        PENDING_EXPLORATION.clear(); explorationDirty = false;
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

    /** 私人数据用事务写入，探索记录只追加新增行，不重写全局配置。 */
    private static void save() {
        if (world.isEmpty() || !writableWorld) return;
        MapSettings.database().transaction(db -> {
            MapDatabase.records(db, world, "points", MapDatabase.points(POINTS));
            var drawings = new LinkedHashMap<String, byte[]>();
            for (int i = 0; i < DRAWINGS.size(); i++) drawings.put(String.format(java.util.Locale.ROOT, "%04d", i), MapBinaryCodec.drawing(DRAWINGS.get(i)));
            MapDatabase.records(db, world, "drawings", drawings);
            var categories = new LinkedHashMap<String, byte[]>();
            for (int i = 0; i < CATEGORIES.size(); i++) {
                var category = CATEGORIES.get(i);
                categories.put(Integer.toString(i), MapBinaryCodec.encode(out -> { out.writeUTF(category.dimension()); out.writeUTF(category.name()); }));
            }
            MapDatabase.records(db, world, "categories", categories);
            var notes = new LinkedHashMap<String, byte[]>();
            NOTES.forEach((id, text) -> notes.put(id.toString(), MapBinaryCodec.encode(out -> out.writeUTF(text))));
            MapDatabase.records(db, world, "notes", notes);
            var view = lastView;
            MapDatabase.records(db, world, "view", view == null ? Map.of() : Map.of("last", MapBinaryCodec.encode(out -> {
                out.writeUTF(view.dimension()); out.writeDouble(view.x()); out.writeDouble(view.z()); out.writeDouble(view.zoom());
            })));
            writeExploration(db);
            return null;
        });
        PENDING_EXPLORATION.clear(); explorationDirty = false;
    }

    private static void writeExploration(java.sql.Connection db) throws java.sql.SQLException {
        try (var sql = db.prepareStatement("INSERT OR IGNORE INTO explored VALUES(?,?,?,?,?)")) {
            for (var key : PENDING_EXPLORATION) {
                sql.setString(1, world); sql.setString(2, key.dimension()); sql.setInt(3, key.chunkX());
                sql.setInt(4, key.chunkZ()); sql.setInt(5, key.height()); sql.addBatch();
            }
            sql.executeBatch();
        }
    }

    /** 断线时清空当前身份，重连会重新核验配置。 */
    public static void disconnect() {
        flushExploration();
        lastView = null;
        CATEGORIES.clear();
        EXPLORED.clear();
        PENDING_EXPLORATION.clear();
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
