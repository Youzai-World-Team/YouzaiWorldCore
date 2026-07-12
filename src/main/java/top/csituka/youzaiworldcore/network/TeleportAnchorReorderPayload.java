package top.csituka.youzaiworldcore.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import top.csituka.youzaiworldcore.YouzaiworldCore;

/**
 * C2S 数据包：客户端请求调整传送锚点列表顺序（向上/向下移动）。
 *
 * @param fromIndex 移动前的索引
 * @param toIndex   移动后的目标索引
 */
@SuppressWarnings("null")
public record TeleportAnchorReorderPayload(int fromIndex, int toIndex) implements CustomPacketPayload {

    public static final Identifier ID = Identifier.fromNamespaceAndPath(
            YouzaiworldCore.MOD_ID, "teleport_anchor_reorder");

    public static final Type<TeleportAnchorReorderPayload> TYPE = new Type<>(ID);

    public static final StreamCodec<RegistryFriendlyByteBuf, TeleportAnchorReorderPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, payload) -> {
                        buf.writeInt(payload.fromIndex);
                        buf.writeInt(payload.toIndex);
                    },
                    buf -> new TeleportAnchorReorderPayload(buf.readInt(), buf.readInt())
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
