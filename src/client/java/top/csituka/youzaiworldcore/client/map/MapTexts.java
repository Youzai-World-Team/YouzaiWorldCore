package top.csituka.youzaiworldcore.client.map;

import net.minecraft.network.chat.Component;
import top.csituka.youzaiworldcore.map.MapWaypoint;

/** 地图界面共用的本地化与名称显示。 */
public final class MapTexts {
    private MapTexts() { }
    public static Component text(String key, Object... arguments) {
        return Component.translatable("map.youzaiworldcore." + key, arguments);
    }
    public static Component biome(String id) {
        return Component.translatableWithFallback("biome." + id.replace(':', '.').replace('/', '.'), id);
    }
    public static Component dimension(String id) {
        return Component.translatableWithFallback("map.youzaiworldcore.dimension." + id.replace(':', '.').replace('/', '.'), id);
    }
    public static Component waypoint(MapWaypoint point) {
        return point.kind() == MapWaypoint.Kind.STRUCTURE
                ? Component.translatableWithFallback("structure." + point.name().replace(':', '.').replace('/', '.'), point.name())
                : Component.literal(point.name());
    }
}
