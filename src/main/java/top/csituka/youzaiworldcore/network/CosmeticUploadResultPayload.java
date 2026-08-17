package top.csituka.youzaiworldcore.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import top.csituka.youzaiworldcore.YouzaiworldCore;

/**
 * S2C：确认一次外观快照是否已经由服务端完整接受。
 *
 * @param snapshotHash 客户端上传时声明的快照哈希
 * @param accepted 服务端是否完整接受并对齐该快照
 * @param retryAfterSeconds 大于零时客户端可在指定秒数后重试
 */
@SuppressWarnings("null")
public record CosmeticUploadResultPayload(
        String snapshotHash, boolean accepted, int retryAfterSeconds) implements CustomPacketPayload {

    public CosmeticUploadResultPayload {
        snapshotHash = snapshotHash == null ? "" : snapshotHash;
        retryAfterSeconds = accepted ? 0 : Math.max(0, retryAfterSeconds);
    }

    public static final Identifier IDENTIFIER = Identifier.fromNamespaceAndPath(
            YouzaiworldCore.MOD_ID, "cosmetic_upload_result");
    public static final Type<CosmeticUploadResultPayload> ID = new Type<>(IDENTIFIER);
    public static final StreamCodec<RegistryFriendlyByteBuf, CosmeticUploadResultPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, payload) -> {
                        buf.writeUtf(payload.snapshotHash(), 64);
                        buf.writeBoolean(payload.accepted());
                        buf.writeVarInt(payload.retryAfterSeconds());
                    },
                    buf -> new CosmeticUploadResultPayload(
                            buf.readUtf(64), buf.readBoolean(), buf.readVarInt()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
