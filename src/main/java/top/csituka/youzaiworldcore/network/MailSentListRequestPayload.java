package top.csituka.youzaiworldcore.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import top.csituka.youzaiworldcore.YouzaiworldCore;

/**
 * C2S 数据包：客户端请求「已发送邮件」列表。
 * 由 {@code /yzwc mail sent} 触发。
 */
public record MailSentListRequestPayload() implements CustomPacketPayload {

    public static final Identifier IDENTIFIER = Identifier.fromNamespaceAndPath(
            YouzaiworldCore.MOD_ID, "mail_sent_list_request");

    public static final Type<MailSentListRequestPayload> ID = new Type<>(IDENTIFIER);

    public static final StreamCodec<RegistryFriendlyByteBuf, MailSentListRequestPayload> STREAM_CODEC =
            StreamCodec.unit(new MailSentListRequestPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
