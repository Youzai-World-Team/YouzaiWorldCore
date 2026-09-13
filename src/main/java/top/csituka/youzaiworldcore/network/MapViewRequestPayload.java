package top.csituka.youzaiworldcore.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import top.csituka.youzaiworldcore.map.MapLayer;

/** C2S：订阅一个地图视口。服务端校验维度、范围、认证和请求频率。 */
public record MapViewRequestPayload(String dimension, MapLayer layer, int height,
                                    int minX, int minZ, int maxX, int maxZ, int generation) implements CustomPacketPayload {
    public static final Type<MapViewRequestPayload> ID = new Type<>(Identifier.fromNamespaceAndPath("youzaiworldcore", "map_view_request"));
    public static final StreamCodec<RegistryFriendlyByteBuf, MapViewRequestPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override public MapViewRequestPayload decode(RegistryFriendlyByteBuf buf) {
            return new MapViewRequestPayload(buf.readUtf(128), buf.readEnum(MapLayer.class), buf.readInt(),
                    buf.readInt(), buf.readInt(), buf.readInt(), buf.readInt(), buf.readInt());
        }
        @Override public void encode(RegistryFriendlyByteBuf buf, MapViewRequestPayload value) {
            buf.writeUtf(value.dimension(), 128);
            buf.writeEnum(value.layer());
            buf.writeInt(value.height());
            buf.writeInt(value.minX());
            buf.writeInt(value.minZ());
            buf.writeInt(value.maxX());
            buf.writeInt(value.maxZ());
            buf.writeInt(value.generation());
        }
    };
    @Override public Type<? extends CustomPacketPayload> type() { return ID; }
}
