package top.csituka.youzaiworldcore.map;

import net.minecraft.resources.Identifier;

import java.util.UUID;

/** 私人、死亡和服务器公共路径点的值对象；公共点的所有者及锁定状态由服务端决定。 */
public record MapWaypoint(UUID id, UUID owner, String name, String group, String dimension,
                          int x, int y, int z, int color, boolean enabled, boolean shared,
                          Kind kind, long createdAt) {
    public enum Kind { NORMAL, DEATH, SERVER, STRUCTURE }

    public MapWaypoint {
        if (id == null || owner == null || name == null || name.isBlank() || name.length() > 64
                || group == null || group.length() > 32 || dimension == null || dimension.length() > 128
                || Identifier.tryParse(dimension) == null || kind == null
                || Math.abs((long) x) > MapTileKey.WORLD_LIMIT || Math.abs((long) z) > MapTileKey.WORLD_LIMIT
                || Math.abs((long) y) > MapTileKey.WORLD_LIMIT) {
            throw new IllegalArgumentException("路径点名称、维度或坐标无效");
        }
        name = name.strip();
        group = group.strip();
        color = 0xFF000000 | color;
    }

    /** @return 同维度坐标，或主世界/下界之间经过传送门比例换算的坐标；不相关维度返回 null */
    public MapVertex projected(String targetDimension, boolean portalProjection) {
        if (dimension.equals(targetDimension)) return new MapVertex(x, z);
        if (!portalProjection) return null;
        if (dimension.equals("minecraft:overworld") && targetDimension.equals("minecraft:the_nether")) {
            return new MapVertex(x / 8.0, z / 8.0);
        }
        if (dimension.equals("minecraft:the_nether") && targetDimension.equals("minecraft:overworld")) {
            double px = x * 8.0, pz = z * 8.0;
            if (Math.abs(px) <= MapTileKey.WORLD_LIMIT && Math.abs(pz) <= MapTileKey.WORLD_LIMIT) {
                return new MapVertex(px, pz);
            }
        }
        return null;
    }

    /** 创建仅改变启用状态的私人路径点。 */
    public MapWaypoint withEnabled(boolean value) {
        return new MapWaypoint(id, owner, name, group, dimension, x, y, z, color, value, shared, kind, createdAt);
    }

    /** 创建仅改变分组的路径点。 */
    public MapWaypoint withGroup(String value) {
        return new MapWaypoint(id, owner, name, value, dimension, x, y, z, color, enabled, shared, kind, createdAt);
    }
}
