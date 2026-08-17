package top.csituka.youzaiworldcore.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import top.csituka.youzaiworldcore.YouzaiworldCore;

import java.util.UUID;

/**
 * S2C：广播某玩家当前是否拥有自定义皮肤或披风。
 */
@SuppressWarnings("null")
public record CosmeticInfoPayload(
        UUID ownerUuid, boolean hasSkin, boolean hasCloak, String snapshotHash)
        implements CustomPacketPayload {

    public CosmeticInfoPayload {
        snapshotHash = snapshotHash == null ? "" : snapshotHash;
    }

    public static final Identifier IDENTIFIER = Identifier.fromNamespaceAndPath(
            YouzaiworldCore.MOD_ID, "cosmetic_info");
    public static final Type<CosmeticInfoPayload> ID = new Type<>(IDENTIFIER);
    public static final StreamCodec<RegistryFriendlyByteBuf, CosmeticInfoPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, payload) -> {
                        buf.writeUUID(payload.ownerUuid());
                        buf.writeBoolean(payload.hasSkin());
                        buf.writeBoolean(payload.hasCloak());
                        buf.writeUtf(payload.snapshotHash(), 64);
                    },
                    buf -> new CosmeticInfoPayload(
                            buf.readUUID(), buf.readBoolean(), buf.readBoolean(), buf.readUtf(64)));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
