package top.csituka.youzaiworldcore.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import top.csituka.youzaiworldcore.YouzaiworldCore;

/** C2S：刷新当前玩家的称号页数据。 */
public record TitleStateRequestPayload() implements CustomPacketPayload {
    public static final Identifier IDENTIFIER = Identifier.fromNamespaceAndPath(YouzaiworldCore.MOD_ID,
            "title_state_request");
    @SuppressWarnings("null")
    public static final Type<TitleStateRequestPayload> ID = new Type<>(IDENTIFIER);
    @SuppressWarnings("null")
    public static final StreamCodec<RegistryFriendlyByteBuf, TitleStateRequestPayload> STREAM_CODEC = StreamCodec
            .unit(new TitleStateRequestPayload());

    @SuppressWarnings("null")
    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
