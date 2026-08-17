package top.csituka.youzaiworldcore.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import top.csituka.youzaiworldcore.YouzaiworldCore;
import top.csituka.youzaiworldcore.config.CosmeticModuleSettings;

import java.util.UUID;

/** S2C：回传某玩家的完整自定义外观快照。 */
@SuppressWarnings("null")
public record CosmeticDataPayload(
        UUID ownerUuid,
        String snapshotHash,
        byte[] skinWide,
        byte[] skinSlim,
        byte[] cloakWide,
        byte[] cloakSlim) implements CustomPacketPayload {

    private static final byte[] EMPTY = new byte[0];

    public CosmeticDataPayload {
        snapshotHash = snapshotHash == null ? "" : snapshotHash;
        skinWide = nonNull(skinWide);
        skinSlim = nonNull(skinSlim);
        cloakWide = nonNull(cloakWide);
        cloakSlim = nonNull(cloakSlim);
    }

    public static final Identifier IDENTIFIER = Identifier.fromNamespaceAndPath(
            YouzaiworldCore.MOD_ID, "cosmetic_data");
    public static final Type<CosmeticDataPayload> ID = new Type<>(IDENTIFIER);
    public static final StreamCodec<RegistryFriendlyByteBuf, CosmeticDataPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, payload) -> {
                        buf.writeUUID(payload.ownerUuid());
                        buf.writeUtf(payload.snapshotHash(), 64);
                        buf.writeByteArray(payload.skinWide());
                        buf.writeByteArray(payload.skinSlim());
                        buf.writeByteArray(payload.cloakWide());
                        buf.writeByteArray(payload.cloakSlim());
                    },
                    buf -> new CosmeticDataPayload(
                            buf.readUUID(),
                            buf.readUtf(64),
                            buf.readByteArray(CosmeticModuleSettings.ABSOLUTE_MAX_FILE_BYTES),
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
