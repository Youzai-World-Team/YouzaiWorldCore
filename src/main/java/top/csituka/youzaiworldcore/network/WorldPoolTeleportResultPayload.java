package top.csituka.youzaiworldcore.network;

import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** S2C：世界切换的权威结果，保留组件翻译与原始失败原因。 */
public record WorldPoolTeleportResultPayload(UUID requestId, boolean success, Component message)
        implements CustomPacketPayload {
    public static final Type<WorldPoolTeleportResultPayload> ID = new Type<>(
            Identifier.fromNamespaceAndPath("youzaiworldcore", "world_pool_teleport_result"));
    public static final StreamCodec<RegistryFriendlyByteBuf, WorldPoolTeleportResultPayload> STREAM_CODEC = StreamCodec.of(
            (buf, value) -> {
                buf.writeUUID(value.requestId()); buf.writeBoolean(value.success());
                ComponentSerialization.STREAM_CODEC.encode(buf, value.message());
            }, buf -> new WorldPoolTeleportResultPayload(
                    buf.readUUID(), buf.readBoolean(), ComponentSerialization.STREAM_CODEC.decode(buf)));
    @Override public Type<? extends CustomPacketPayload> type() { return ID; }
}
