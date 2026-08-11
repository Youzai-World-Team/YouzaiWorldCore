package top.csituka.youzaiworldcore.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import top.csituka.youzaiworldcore.YouzaiworldCore;

/** S2C：原地重生申请的服务端权威结果。 */
public record InPlaceRespawnResultPayload(
        boolean approved, String reason, int requiredLevel, int currentLevel)
        implements CustomPacketPayload {

    public static final Identifier IDENTIFIER = Identifier.fromNamespaceAndPath(
            YouzaiworldCore.MOD_ID, "in_place_respawn_result");
    public static final Type<InPlaceRespawnResultPayload> ID = new Type<>(IDENTIFIER);
    public static final StreamCodec<RegistryFriendlyByteBuf, InPlaceRespawnResultPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, payload) -> {
                        buf.writeBoolean(payload.approved());
                        buf.writeUtf(payload.reason());
                        buf.writeVarInt(payload.requiredLevel());
                        buf.writeVarInt(payload.currentLevel());
                    },
                    buf -> new InPlaceRespawnResultPayload(
                            buf.readBoolean(), buf.readUtf(), buf.readVarInt(), buf.readVarInt()));

    @Override
    @SuppressWarnings("null")
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
