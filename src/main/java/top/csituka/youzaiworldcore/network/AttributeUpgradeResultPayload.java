package top.csituka.youzaiworldcore.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import top.csituka.youzaiworldcore.YouzaiworldCore;

@SuppressWarnings("null")
public record AttributeUpgradeResultPayload(long requestId, boolean success) implements CustomPacketPayload {
    public static final Type<AttributeUpgradeResultPayload> ID = new Type<>(
            Identifier.fromNamespaceAndPath(YouzaiworldCore.MOD_ID, "attribute_upgrade_result"));
    public static final StreamCodec<RegistryFriendlyByteBuf, AttributeUpgradeResultPayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> { buf.writeLong(p.requestId()); buf.writeBoolean(p.success()); },
            buf -> new AttributeUpgradeResultPayload(buf.readLong(), buf.readBoolean()));
    @Override public Type<? extends CustomPacketPayload> type() { return ID; }
}
