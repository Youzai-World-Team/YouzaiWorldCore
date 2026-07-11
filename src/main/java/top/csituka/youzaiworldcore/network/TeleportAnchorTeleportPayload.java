package top.csituka.youzaiworldcore.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import top.csituka.youzaiworldcore.YouzaiworldCore;

/**
 * C2S 数据包：客户端请求传送到指定索引的传送锚点。
 *
 * @param pointIndex 在传送点列表中的索引
 */
public record TeleportAnchorTeleportPayload(int pointIndex) implements CustomPacketPayload {

    public static final Identifier ID = Identifier.fromNamespaceAndPath(
            YouzaiworldCore.MOD_ID, "teleport_anchor_teleport");

    public static final Type<TeleportAnchorTeleportPayload> TYPE = new Type<>(ID);

    public static final StreamCodec<RegistryFriendlyByteBuf, TeleportAnchorTeleportPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, payload) -> buf.writeInt(payload.pointIndex),
                    buf -> new TeleportAnchorTeleportPayload(buf.readInt())
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
