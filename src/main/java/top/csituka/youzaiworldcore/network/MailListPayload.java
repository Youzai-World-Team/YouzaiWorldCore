package top.csituka.youzaiworldcore.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import top.csituka.youzaiworldcore.YouzaiworldCore;

import java.util.ArrayList;
import java.util.List;

/**
 * S2C 数据包：服务端向玩家发送收件箱列表。
 *
 * @param entries 邮件条目列表（每个包含 MailRef + Mail）
 */
public record MailListPayload(List<MailStreamCodecs.MailRefAndMail> entries) implements CustomPacketPayload {

    public static final Identifier IDENTIFIER = Identifier.fromNamespaceAndPath(
            YouzaiworldCore.MOD_ID, "mail_list");

    public static final Type<MailListPayload> ID = new Type<>(IDENTIFIER);

    public static final StreamCodec<RegistryFriendlyByteBuf, MailListPayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeVarInt(p.entries().size());
                for (MailStreamCodecs.MailRefAndMail pair : p.entries()) {
                    MailStreamCodecs.REF_AND_MAIL.encode(buf, pair);
                }
            },
            buf -> {
                int size = buf.readVarInt();
                List<MailStreamCodecs.MailRefAndMail> entries = new ArrayList<>(size);
                for (int i = 0; i < size; i++) {
                    entries.add(MailStreamCodecs.REF_AND_MAIL.decode(buf));
                }
                return new MailListPayload(entries);
            });

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
