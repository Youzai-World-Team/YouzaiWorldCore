package top.csituka.youzaiworldcore.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import top.csituka.youzaiworldcore.YouzaiworldCore;
import top.csituka.youzaiworldcore.config.CosmeticModuleSettings;

/**
 * C2S：离线会话玩家上传当前自定义外观快照；空数组表示删除对应服务端文件。
 */
@SuppressWarnings("null")
public record CosmeticUploadPayload(
        boolean offlineSession,
        String snapshotHash,
        byte[] skinWide,
        byte[] skinSlim,
        byte[] cloak) implements CustomPacketPayload {

    private static final byte[] EMPTY = new byte[0];

    public CosmeticUploadPayload {
        snapshotHash = snapshotHash == null ? "" : snapshotHash;
        skinWide = nonNull(skinWide);
        skinSlim = nonNull(skinSlim);
        cloak = nonNull(cloak);
    }

    public static final Identifier IDENTIFIER = Identifier.fromNamespaceAndPath(
            YouzaiworldCore.MOD_ID, "cosmetic_upload");
    public static final Type<CosmeticUploadPayload> ID = new Type<>(IDENTIFIER);
    public static final StreamCodec<RegistryFriendlyByteBuf, CosmeticUploadPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, payload) -> {
                        buf.writeBoolean(payload.offlineSession());
                        buf.writeUtf(payload.snapshotHash(), 64);
                        buf.writeByteArray(payload.skinWide());
                        buf.writeByteArray(payload.skinSlim());
                        buf.writeByteArray(payload.cloak());
                    },
                    buf -> new CosmeticUploadPayload(
                            buf.readBoolean(),
                            buf.readUtf(64),
                            buf.readByteArray(CosmeticModuleSettings.ABSOLUTE_MAX_FILE_BYTES),
                            buf.readByteArray(CosmeticModuleSettings.ABSOLUTE_MAX_FILE_BYTES),
                            buf.readByteArray(CosmeticModuleSettings.ABSOLUTE_MAX_FILE_BYTES)));

    private static byte[] nonNull(byte[] data) {
        return data == null ? EMPTY : data;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
