package top.csituka.youzaiworldcore.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import java.util.UUID;

/** C2S：按生物种类查询玩家周围的模拟区块；中心与维度始终由服务端确定。 */
public record MapMobQueryPayload(UUID request, Identifier entityType, int radius) implements CustomPacketPayload {
    public static final Type<MapMobQueryPayload> ID = new Type<>(Identifier.fromNamespaceAndPath("youzaiworldcore", "map_mob_query"));
    public static final StreamCodec<RegistryFriendlyByteBuf, MapMobQueryPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override public MapMobQueryPayload decode(RegistryFriendlyByteBuf buf) {
            UUID request = buf.readUUID(); Identifier type = Identifier.parse(buf.readUtf(128));
            int radius = buf.readVarInt();
            if (radius < 1 || radius > 32) throw new IllegalArgumentException("生物雷达距离无效");
            return new MapMobQueryPayload(request, type, radius);
        }
        @Override public void encode(RegistryFriendlyByteBuf buf, MapMobQueryPayload value) {
            buf.writeUUID(value.request()); buf.writeUtf(value.entityType().toString(), 128); buf.writeVarInt(value.radius());
        }
    };
    @Override public Type<? extends CustomPacketPayload> type() { return ID; }
}
