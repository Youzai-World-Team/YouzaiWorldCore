package top.csituka.youzaiworldcore.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import top.csituka.youzaiworldcore.YouzaiworldCore;

import java.util.UUID;

/**
 * C2S 数据包：编辑前请求单封完整邮件（含附件 / item NBT）。
 * 由 {@code MailSentScreen} 的 [编辑] 按钮触发。
 *
 * @param mailId 邮件 ID
 */
public record MailFetchPayload(UUID mailId) implements CustomPacketPayload {

    public static final Identifier IDENTIFIER = Identifier.fromNamespaceAndPath(
            YouzaiworldCore.MOD_ID, "mail_fetch");

    @SuppressWarnings("null")
    public static final Type<MailFetchPayload> ID = new Type<>(IDENTIFIER);

    @SuppressWarnings("null")
    public static final StreamCodec<RegistryFriendlyByteBuf, MailFetchPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, p) -> buf.writeUUID(p.mailId()),
                    buf -> new MailFetchPayload(buf.readUUID())
            );

    @Override
    @SuppressWarnings("null")
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
