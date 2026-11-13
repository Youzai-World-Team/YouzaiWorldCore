package top.csituka.youzaiworldcore.client.map;

import top.csituka.youzaiworldcore.map.MapTileKey;
import top.csituka.youzaiworldcore.map.MapLayer;
import net.minecraft.network.chat.Component;
import top.csituka.youzaiworldcore.client.config.MapSettings;
import top.csituka.youzaiworldcore.map.MapBinaryCodec;
import top.csituka.youzaiworldcore.map.MapDatabase;
import java.util.LinkedHashMap;
import java.util.Map;
import top.csituka.youzaiworldcore.map.MapWaypoint;
import top.csituka.youzaiworldcore.map.MapWaypointGroup;
import top.csituka.youzaiworldcore.map.MapWaypointIcon;
import top.csituka.youzaiworldcore.util.DebugLogger;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * 按服务器存档身份隔离的私人路径点、分组、公共点私人归属、描述和视角，存入客户端地图 SQLite。
 */
public final class MapPersonalData {
    public static final int MAX_POINTS = 512;
    private static final List<MapWaypoint> POINTS = new ArrayList<>();

    /** 真实到访层随当前服务器存档保存，绝不从服务端批量采样反推。 */
    private static final Set<MapTileKey> EXPLORED = new HashSet<>();
    private static final int MAX_EXPLORED = 65536;

    /** 当前存档上次关闭地图时的视角，不影响其他服务器。 */
    public record LastView(String dimension, double x, double z, double zoom) {
    }

    private static LastView lastView;
    private static final java.util.Map<UUID, String> NOTES = new java.util.LinkedHashMap<>();
    public static final int MAX_GROUPS = 64;
    private static final Map<String, MapWaypointGroup> GROUPS = new LinkedHashMap<>();
    private static final Map<UUID, String> SHARED_GROUPS = new LinkedHashMap<>();
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
        EXPLORED.clear();
        PENDING_EXPLORATION.clear();
        NOTES.clear();
        GROUPS.clear();
        SHARED_GROUPS.clear();
        explorationDirty = false;
        var database = MapSettings.database();
        writableWorld = true;
        database.records(world, "points").values().forEach(bytes -> POINTS.add(MapBinaryCodec.waypoint(bytes)));
        database.records(world, "notes").forEach((id, bytes) -> NOTES.put(UUID.fromString(id), MapBinaryCodec.decode(bytes, in -> in.readUTF())));
        database.records(world, "groups").forEach((id, bytes) -> {
            var group = MapBinaryCodec.group(id, bytes);
            if (!group.editable() || GROUPS.size() >= MAX_GROUPS)
                throw new IllegalStateException("地图自建分组数量或身份无效");
            GROUPS.put(id, group);
        });
        database.records(world, "point_groups").forEach((id, bytes) -> {
            String group = MapBinaryCodec.decode(bytes, in -> in.readUTF());
            if (!group.isEmpty() && !GROUPS.containsKey(group))
                throw new IllegalStateException("地图公共点的私人分组不存在：" + group);
            SHARED_GROUPS.put(UUID.fromString(id), group);
        });
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
        DebugLogger.info("MapPersonalData", "地图私人数据已切换：%s，路径点=%d", world, POINTS.size());
    }

    public static List<MapWaypoint> points() {
        return List.copyOf(POINTS);
    }

    public static String worldId() {
        return world;
    }

    /** 内置死亡点和未分组始终存在，自建分组按名称排列，各维度共用。 */
    public static List<MapWaypointGroup> groups() {
        var result = new ArrayList<MapWaypointGroup>();
        result.add(MapWaypointGroup.DEATH); result.add(MapWaypointGroup.UNGROUPED);
        result.addAll(GROUPS.values().stream().sorted(java.util.Comparator.comparing(
                MapWaypointGroup::name, String.CASE_INSENSITIVE_ORDER).thenComparing(MapWaypointGroup::id)).toList());
        return List.copyOf(result);
    }

    /** 普通路径点只可选择未分组和自建分组；死亡点由类型自动归组。 */
    public static List<MapWaypointGroup> assignableGroups() {
        return groups().stream().filter(group -> !group.equals(MapWaypointGroup.DEATH)).toList();
    }

    /** 旧固定分组仅保留原记录，显示到未分组；公共点归属仅本人可见。 */
    public static MapWaypointGroup groupOf(MapWaypoint point) {
        if (point.kind() == MapWaypoint.Kind.DEATH) return MapWaypointGroup.DEATH;
        String id = point.shared() ? SHARED_GROUPS.getOrDefault(point.id(), "") : point.group();
        return GROUPS.getOrDefault(id, MapWaypointGroup.UNGROUPED);
    }

    /** 按当前存档解析分组，删除或切服后的旧界面引用回落到未分组。 */
    public static MapWaypointGroup resolveGroup(String id) {
        if (MapWaypointGroup.DEATH.id().equals(id)) return MapWaypointGroup.DEATH;
        return GROUPS.getOrDefault(id, MapWaypointGroup.UNGROUPED);
    }

    private static boolean assignable(MapWaypointGroup group) {
        return group != null && (group.equals(MapWaypointGroup.UNGROUPED) || group.equals(GROUPS.get(group.id())));
    }
    private static boolean uniqueName(String id, String name) {
        return GROUPS.values().stream().noneMatch(group -> !group.id().equals(id) && group.name().equalsIgnoreCase(name));
    }

    /** 创建自建分组，空白、重名、超长或容量已满时返回 null，不修改数据。 */
    public static MapWaypointGroup createGroup(String name, MapWaypointIcon icon) {
        if (world.isEmpty() || !writableWorld || name == null || icon == null || GROUPS.size() >= MAX_GROUPS) return null;
        String value = name.strip();
        if (!MapWaypointGroup.validName(value) || !uniqueName("", value)) return null;
        var group = new MapWaypointGroup(UUID.randomUUID().toString().replace("-", ""), value, icon);
        var groups = new LinkedHashMap<>(GROUPS); groups.put(group.id(), group);
        persistGroups(groups, POINTS, SHARED_GROUPS);
        GROUPS.clear(); GROUPS.putAll(groups);
        DebugLogger.info("MapPersonalData", "创建地图分组：%s，图标=%s", group.id(), icon.id());
        return group;
    }

    /** 编辑已有自建分组名称和图标，分组身份及路径点归属保持不变。 */
    public static boolean editGroup(String id, String name, MapWaypointIcon icon) {
        if (world.isEmpty() || !writableWorld || !GROUPS.containsKey(id) || name == null || icon == null) return false;
        String value = name.strip();
        if (!MapWaypointGroup.validName(value) || !uniqueName(id, value)) return false;
        var groups = new LinkedHashMap<>(GROUPS); groups.put(id, new MapWaypointGroup(id, value, icon));
        persistGroups(groups, POINTS, SHARED_GROUPS);
        GROUPS.clear(); GROUPS.putAll(groups);
        DebugLogger.info("MapPersonalData", "编辑地图分组：%s，图标=%s", id, icon.id());
        return true;
    }

    /** 删除自建分组并将其全部路径点转入未分组；三个集合在同一事务保存。 */
    public static boolean deleteGroup(String id) {
        if (world.isEmpty() || !writableWorld || !GROUPS.containsKey(id)) return false;
        var groups = new LinkedHashMap<>(GROUPS); groups.remove(id);
        var points = POINTS.stream().map(point -> point.group().equals(id)
                ? point.withGroup(MapWaypointGroup.UNGROUPED) : point).toList();
        var shared = new LinkedHashMap<>(SHARED_GROUPS); shared.values().removeIf(id::equals);
        persistGroups(groups, points, shared);
        GROUPS.clear(); GROUPS.putAll(groups); POINTS.clear(); POINTS.addAll(points);
        SHARED_GROUPS.clear(); SHARED_GROUPS.putAll(shared);
        DebugLogger.info("MapPersonalData", "删除地图分组并保留路径点：%s", id);
        return true;
    }

    /** 整理公共点不修改服务器内容和其他玩家的分组，不需要公共点编辑权限。 */
    public static boolean setSharedGroup(MapWaypoint point, MapWaypointGroup group) {
        if (world.isEmpty() || !writableWorld || !point.shared() || point.kind() == MapWaypoint.Kind.DEATH
                || MapClient.isAnchor(point) || !assignable(group)) return false;
        var shared = new LinkedHashMap<>(SHARED_GROUPS);
        if (group.equals(MapWaypointGroup.UNGROUPED)) shared.remove(point.id()); else shared.put(point.id(), group.id());
        persistGroups(GROUPS, POINTS, shared);
        SHARED_GROUPS.clear(); SHARED_GROUPS.putAll(shared);
        DebugLogger.debug("MapPersonalData", "修改公共点私人分组：%s / %s", point.id(), group.id());
        return true;
    }

    private static void writeGroups(java.sql.Connection db, Map<String, MapWaypointGroup> groups,
                                    Map<UUID, String> shared) throws java.sql.SQLException {
        var records = new LinkedHashMap<String, byte[]>();
        groups.forEach((id, group) -> records.put(id, MapBinaryCodec.group(group)));
        MapDatabase.records(db, world, "groups", records);
        var assignments = new LinkedHashMap<String, byte[]>();
        shared.forEach((id, group) -> assignments.put(id.toString(), MapBinaryCodec.encode(out -> out.writeUTF(group))));
        MapDatabase.records(db, world, "point_groups", assignments);
    }
    private static void persistGroups(Map<String, MapWaypointGroup> groups, List<MapWaypoint> points,
                                      Map<UUID, String> shared) {
        MapSettings.database().transaction(db -> {
            writeGroups(db, groups, shared);
            MapDatabase.records(db, world, "points", MapDatabase.points(points));
            return null;
        });
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
        if (point.kind() == MapWaypoint.Kind.DEATH) point = point.withGroup(MapWaypointGroup.DEATH);
        else if (!point.group().isEmpty() && !GROUPS.containsKey(point.group())
                && (index < 0 || !POINTS.get(index).group().equals(point.group()))) return false;
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

    public static void remove(UUID id) {
        if (POINTS.removeIf(point -> point.id().equals(id))) {
            NOTES.remove(id);
            SHARED_GROUPS.remove(id);
            save();
        }
    }

    /** 批量整理当前筛选的私人与公共点；死亡点和锚点不改变归属。 */
    public static boolean group(List<MapWaypoint> selected, MapWaypointGroup group) {
        if (world.isEmpty() || !writableWorld || !assignable(group)) return false;
        var ids = selected.stream().filter(point -> point.kind() != MapWaypoint.Kind.DEATH && !MapClient.isAnchor(point))
                .map(MapWaypoint::id).collect(java.util.stream.Collectors.toSet());
        var points = POINTS.stream().map(point -> ids.contains(point.id()) && point.kind() != MapWaypoint.Kind.DEATH
                ? point.withGroup(group) : point).toList();
        var shared = new LinkedHashMap<>(SHARED_GROUPS);
        for (var point : selected) if (point.shared() && ids.contains(point.id())) {
            if (group.equals(MapWaypointGroup.UNGROUPED)) shared.remove(point.id()); else shared.put(point.id(), group.id());
        }
        persistGroups(GROUPS, points, shared);
        POINTS.clear(); POINTS.addAll(points); SHARED_GROUPS.clear(); SHARED_GROUPS.putAll(shared);
        DebugLogger.debug("MapPersonalData", "移动路径点至私人分组：%s，数量=%d", group.id(), ids.size());
        return true;
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
                MapWaypointGroup.DEATH.id(), dimension,
                x, y, z, 0xFFE47A78, true, false, MapWaypoint.Kind.DEATH, System.currentTimeMillis())))
            MapClient.message("death_limit_reached");
    }

    /** 私人数据用事务写入，探索记录只追加新增行，不重写全局配置。 */
    private static void save() {
        if (world.isEmpty() || !writableWorld) return;
        MapSettings.database().transaction(db -> {
            MapDatabase.records(db, world, "points", MapDatabase.points(POINTS));
            writeGroups(db, GROUPS, SHARED_GROUPS);
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
        EXPLORED.clear();
        PENDING_EXPLORATION.clear();
        NOTES.clear();
        GROUPS.clear();
        SHARED_GROUPS.clear();
        explorationDirty = false;
        world = "";
        writableWorld = false;
        POINTS.clear();
    }
}
