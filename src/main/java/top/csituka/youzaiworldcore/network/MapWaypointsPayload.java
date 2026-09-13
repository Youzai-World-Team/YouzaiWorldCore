package top.csituka.youzaiworldcore.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import top.csituka.youzaiworldcore.map.MapWaypoint;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** S2C：公共路径点的权威快照，普通玩家只可修改本人发布且未锁定的条目。 */
public record MapWaypointsPayload(UUID worldId, List<MapWaypoint> points) implements CustomPacketPayload {
    public static final Type<MapWaypointsPayload> ID = new Type<>(Identifier.fromNamespaceAndPath("youzaiworldcore", "map_waypoints"));
    public static final StreamCodec<RegistryFriendlyByteBuf, MapWaypointsPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override public MapWaypointsPayload decode(RegistryFriendlyByteBuf buf) {
            UUID world = buf.readUUID();
            int count = MapStreamCodecs.count(buf, 512);
            var points = new ArrayList<MapWaypoint>(count);
            for (int i = 0; i < count; i++) points.add(MapStreamCodecs.readWaypoint(buf));
            return new MapWaypointsPayload(world, List.copyOf(points));
        }
        @Override public void encode(RegistryFriendlyByteBuf buf, MapWaypointsPayload value) {
            buf.writeUUID(value.worldId());
            buf.writeVarInt(value.points().size());
            for (MapWaypoint point : value.points()) MapStreamCodecs.writeWaypoint(buf, point);
        }
    };
    @Override public Type<? extends CustomPacketPayload> type() { return ID; }
}
