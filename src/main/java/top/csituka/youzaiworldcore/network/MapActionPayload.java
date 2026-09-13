package top.csituka.youzaiworldcore.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import top.csituka.youzaiworldcore.map.MapWaypoint;

/** C2S：路径点管理或受权限保护的传送请求。owner、shared、kind 不作为授权依据。 */
public record MapActionPayload(Action action, MapWaypoint point) implements CustomPacketPayload {
    public enum Action { ADD, UPDATE, DELETE, LOCK, UNLOCK, TELEPORT, HIDE_POSITION, SHOW_POSITION }
    public static final Type<MapActionPayload> ID = new Type<>(Identifier.fromNamespaceAndPath("youzaiworldcore", "map_action"));
    public static final StreamCodec<RegistryFriendlyByteBuf, MapActionPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override public MapActionPayload decode(RegistryFriendlyByteBuf buf) {
            return new MapActionPayload(buf.readEnum(Action.class), MapStreamCodecs.readWaypoint(buf));
        }
        @Override public void encode(RegistryFriendlyByteBuf buf, MapActionPayload value) {
            buf.writeEnum(value.action());
            MapStreamCodecs.writeWaypoint(buf, value.point());
        }
    };
    @Override public Type<? extends CustomPacketPayload> type() { return ID; }
}
