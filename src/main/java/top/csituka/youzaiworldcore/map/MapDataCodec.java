package top.csituka.youzaiworldcore.map;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import top.csituka.youzaiworldcore.config.ConfigSection;

import java.util.ArrayList;
import java.util.UUID;

/** 地图 JSON 的强类型读写，坏数据沿用 ConfigSection 的隔离、重建与退出流程。 */
public final class MapDataCodec {
    private MapDataCodec() { }

    /** 将一个路径点写为模块分节内的对象。 */
    public static JsonObject writeWaypoint(MapWaypoint point) {
        JsonObject value = new JsonObject();
        value.addProperty("id", point.id().toString());
        value.addProperty("owner", point.owner().toString());
        value.addProperty("name", point.name());
        value.addProperty("group", point.group());
        value.addProperty("dimension", point.dimension());
        value.addProperty("x", point.x());
        value.addProperty("y", point.y());
        value.addProperty("z", point.z());
        value.addProperty("color", point.color());
        value.addProperty("enabled", point.enabled());
        value.addProperty("shared", point.shared());
        value.addProperty("kind", point.kind().name());
        value.addProperty("created_at", point.createdAt());
        return value;
    }

    /** 读取路径点，值域和枚举错误不能静默丢弃。 */
    public static MapWaypoint readWaypoint(ConfigSection value) {
        try {
            return new MapWaypoint(uuid(value, "id"), uuid(value, "owner"),
                    value.getString("name", "路径点"), value.getString("group", ""),
                    value.getString("dimension", "minecraft:overworld"),
                    value.getInt("x", 0, -MapTileKey.WORLD_LIMIT, MapTileKey.WORLD_LIMIT),
                    value.getInt("y", 64, -MapTileKey.WORLD_LIMIT, MapTileKey.WORLD_LIMIT),
                    value.getInt("z", 0, -MapTileKey.WORLD_LIMIT, MapTileKey.WORLD_LIMIT),
                    value.getInt("color", 0xFF7DC99D), value.getBoolean("enabled", true),
                    value.getBoolean("shared", false),
                    value.getEnum("kind", MapWaypoint.Kind.NORMAL, MapWaypoint.Kind.class),
                    value.getLong("created_at", 0));
        } catch (IllegalArgumentException error) {
            value.fail("<路径点>", error.getMessage());
            throw error;
        }
    }

    /** 序列化一条地图绘图。 */
    public static JsonObject writeDrawing(MapDrawing drawing) {
        JsonObject value = new JsonObject();
        value.addProperty("id", drawing.id().toString());
        value.addProperty("dimension", drawing.dimension());
        value.addProperty("kind", drawing.kind().name());
        value.addProperty("color", drawing.color());
        value.addProperty("label", drawing.label());
        JsonArray points = new JsonArray();
        for (MapVertex vertex : drawing.vertices()) {
            JsonObject point = new JsonObject();
            point.addProperty("x", vertex.x());
            point.addProperty("z", vertex.z());
            points.add(point);
        }
        value.add("vertices", points);
        return value;
    }

    /** 读取绘图，限制点数以避免错误配置造成无界内存分配。 */
    public static MapDrawing readDrawing(ConfigSection value) {
        try {
            var entries = value.getObjectList("vertices");
            if (entries == null || entries.isEmpty() || entries.size() > MapDrawing.MAX_VERTICES) {
                value.fail("vertices", "绘图顶点数量必须在 1..512 之间");
            }
            var vertices = new ArrayList<MapVertex>(entries.size());
            for (var point : entries) {
                vertices.add(new MapVertex(
                        point.getDouble("x", 0, -MapTileKey.WORLD_LIMIT, MapTileKey.WORLD_LIMIT),
                        point.getDouble("z", 0, -MapTileKey.WORLD_LIMIT, MapTileKey.WORLD_LIMIT)));
            }
            return new MapDrawing(uuid(value, "id"), value.getString("dimension", "minecraft:overworld"),
                    value.getEnum("kind", MapDrawing.Kind.PEN, MapDrawing.Kind.class),
                    vertices, value.getInt("color", 0xFF7DC99D), value.getString("label", ""));
        } catch (IllegalArgumentException error) {
            value.fail("<绘图>", error.getMessage());
            throw error;
        }
    }

    /** UUID 格式校验也交由统一的配置失败流程处理。 */
    public static UUID uuid(ConfigSection value, String key) {
        try {
            return UUID.fromString(value.getString(key, ""));
        } catch (IllegalArgumentException error) {
            value.fail(key, "必须是有效的 UUID");
            throw error;
        }
    }
}
