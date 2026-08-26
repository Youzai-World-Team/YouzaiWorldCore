package top.csituka.youzaiworldcore.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import top.csituka.youzaiworldcore.YouzaiworldCore;

/** S2C：玩家死亡时同步原地重生是否启用及本次费用。 */
public record InPlaceRespawnInfoPayload(boolean enabled, int requiredLevel)
        implements CustomPacketPayload {

    public static final Identifier IDENTIFIER = Identifier.fromNamespaceAndPath(
            YouzaiworldCore.MOD_ID, "in_place_respawn_info");
    @SuppressWarnings("null")
    public static final Type<InPlaceRespawnInfoPayload> ID = new Type<>(IDENTIFIER);
    public static final StreamCodec<RegistryFriendlyByteBuf, InPlaceRespawnInfoPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeBoolean(payload.enabled());
                buf.writeVarInt(payload.requiredLevel());
            },
            buf -> new InPlaceRespawnInfoPayload(buf.readBoolean(), buf.readVarInt()));

    @Override
    @SuppressWarnings("null")
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
