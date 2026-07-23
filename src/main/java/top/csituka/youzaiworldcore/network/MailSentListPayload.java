package top.csituka.youzaiworldcore.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import top.csituka.youzaiworldcore.YouzaiworldCore;

import java.util.ArrayList;
import java.util.List;

/**
 * S2C 数据包：服务端向管理员发送「已发送邮件」摘要列表。
 *
 * @param summaries 已发送邮件摘要列表
 */
public record MailSentListPayload(List<MailStreamCodecs.MailSummary> summaries) implements CustomPacketPayload {

    public static final Identifier IDENTIFIER = Identifier.fromNamespaceAndPath(
            YouzaiworldCore.MOD_ID, "mail_sent_list");

    @SuppressWarnings("null")
    public static final Type<MailSentListPayload> ID = new Type<>(IDENTIFIER);

    public static final StreamCodec<RegistryFriendlyByteBuf, MailSentListPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, p) -> {
                        buf.writeVarInt(p.summaries().size());
                        for (MailStreamCodecs.MailSummary sum : p.summaries()) {
                            MailStreamCodecs.MAIL_SUMMARY.encode(buf, sum);
                        }
                    },
                    buf -> {
                        int size = buf.readVarInt();
                        List<MailStreamCodecs.MailSummary> summaries = new ArrayList<>(size);
                        for (int i = 0; i < size; i++) {
                            summaries.add(MailStreamCodecs.MAIL_SUMMARY.decode(buf));
                        }
                        return new MailSentListPayload(summaries);
                    }
            );

    @Override
    @SuppressWarnings("null")
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
