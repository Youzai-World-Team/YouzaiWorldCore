package top.csituka.youzaiworldcore.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import java.util.UUID;

/** S2C：公共操作确认，客户端仅在成功后关闭编辑器或转换私人点。 */
public record MapActionResultPayload(UUID worldId, UUID id, MapActionPayload.Action action, boolean success, String message) implements CustomPacketPayload {
    public static final Type<MapActionResultPayload> ID = new Type<>(Identifier.fromNamespaceAndPath("youzaiworldcore", "map_action_result"));
    public static final StreamCodec<RegistryFriendlyByteBuf, MapActionResultPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override public MapActionResultPayload decode(RegistryFriendlyByteBuf buf) {
            return new MapActionResultPayload(buf.readUUID(), buf.readUUID(), buf.readEnum(MapActionPayload.Action.class), buf.readBoolean(), buf.readUtf(64));
        }
        @Override public void encode(RegistryFriendlyByteBuf buf, MapActionResultPayload value) {
            buf.writeUUID(value.worldId()); buf.writeUUID(value.id()); buf.writeEnum(value.action()); buf.writeBoolean(value.success()); buf.writeUtf(value.message(), 64);
        }
    };
    @Override public Type<? extends CustomPacketPayload> type() { return ID; }
}
