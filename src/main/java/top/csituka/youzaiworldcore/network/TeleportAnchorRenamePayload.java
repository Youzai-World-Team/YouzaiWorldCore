package top.csituka.youzaiworldcore.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import top.csituka.youzaiworldcore.YouzaiworldCore;

/**
 * C2S 数据包：客户端请求重命名指定索引的传送锚点。
 *
 * @param pointIndex 在传送点列表中的索引
 * @param newName    新的显示名称
 */
public record TeleportAnchorRenamePayload(int pointIndex, String newName) implements CustomPacketPayload {

    public static final Identifier ID = Identifier.fromNamespaceAndPath(
            YouzaiworldCore.MOD_ID, "teleport_anchor_rename");

    public static final Type<TeleportAnchorRenamePayload> TYPE = new Type<>(ID);

    public static final StreamCodec<RegistryFriendlyByteBuf, TeleportAnchorRenamePayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, payload) -> {
                        buf.writeInt(payload.pointIndex);
                        buf.writeUtf(payload.newName);
                    },
                    buf -> new TeleportAnchorRenamePayload(buf.readInt(), buf.readUtf())
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
