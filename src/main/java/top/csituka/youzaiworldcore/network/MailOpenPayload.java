package top.csituka.youzaiworldcore.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import top.csituka.youzaiworldcore.YouzaiworldCore;

/**
 * C2S 数据包：玩家打开信箱时请求收件箱列表。
 */
public record MailOpenPayload() implements CustomPacketPayload {

    public static final Identifier IDENTIFIER = Identifier.fromNamespaceAndPath(
            YouzaiworldCore.MOD_ID, "mail_open");

    @SuppressWarnings("null")
    public static final Type<MailOpenPayload> ID = new Type<>(IDENTIFIER);

    @SuppressWarnings("null")
    public static final StreamCodec<RegistryFriendlyByteBuf, MailOpenPayload> STREAM_CODEC =
            StreamCodec.unit(new MailOpenPayload());

    @Override
    @SuppressWarnings("null")
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
