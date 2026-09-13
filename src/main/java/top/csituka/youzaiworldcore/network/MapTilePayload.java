package top.csituka.youzaiworldcore.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import top.csituka.youzaiworldcore.map.MapTile;

import java.util.UUID;

/** S2C：服务端采样的真实地形；世界身份防止切服后的延迟回调串入新地图。 */
public record MapTilePayload(UUID worldId, MapTile tile) implements CustomPacketPayload {
    public static final Type<MapTilePayload> ID = new Type<>(Identifier.fromNamespaceAndPath("youzaiworldcore", "map_tile"));
    public static final StreamCodec<RegistryFriendlyByteBuf, MapTilePayload> STREAM_CODEC = new StreamCodec<>() {
        @Override public MapTilePayload decode(RegistryFriendlyByteBuf buf) {
            return new MapTilePayload(buf.readUUID(), MapStreamCodecs.readTile(buf));
        }
        @Override public void encode(RegistryFriendlyByteBuf buf, MapTilePayload value) {
            buf.writeUUID(value.worldId());
            MapStreamCodecs.writeTile(buf, value.tile());
        }
    };
    @Override public Type<? extends CustomPacketPayload> type() { return ID; }
}
