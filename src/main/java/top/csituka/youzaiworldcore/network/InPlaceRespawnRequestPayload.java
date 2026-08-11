package top.csituka.youzaiworldcore.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import top.csituka.youzaiworldcore.YouzaiworldCore;

/** C2S：死亡界面请求原地重生。 */
public record InPlaceRespawnRequestPayload() implements CustomPacketPayload {

    public static final Identifier IDENTIFIER = Identifier.fromNamespaceAndPath(
            YouzaiworldCore.MOD_ID, "in_place_respawn_request");
    public static final Type<InPlaceRespawnRequestPayload> ID = new Type<>(IDENTIFIER);
    public static final StreamCodec<RegistryFriendlyByteBuf, InPlaceRespawnRequestPayload> STREAM_CODEC =
            StreamCodec.unit(new InPlaceRespawnRequestPayload());

    @Override
    @SuppressWarnings("null")
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
