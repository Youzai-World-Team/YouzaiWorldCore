package top.csituka.youzaiworldcore.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import top.csituka.youzaiworldcore.YouzaiworldCore;

import java.util.UUID;

/** C2S：按 UUID 请求某玩家的自定义外观文件。 */
@SuppressWarnings("null")
public record CosmeticRequestPayload(UUID targetUuid) implements CustomPacketPayload {

    public static final Identifier IDENTIFIER = Identifier.fromNamespaceAndPath(
            YouzaiworldCore.MOD_ID, "cosmetic_request");
    public static final Type<CosmeticRequestPayload> ID = new Type<>(IDENTIFIER);
    public static final StreamCodec<RegistryFriendlyByteBuf, CosmeticRequestPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, payload) -> buf.writeUUID(payload.targetUuid()),
                    buf -> new CosmeticRequestPayload(buf.readUUID()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
