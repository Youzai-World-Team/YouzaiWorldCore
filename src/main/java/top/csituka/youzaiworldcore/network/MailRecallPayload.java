package top.csituka.youzaiworldcore.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import top.csituka.youzaiworldcore.YouzaiworldCore;

import java.util.UUID;

/**
 * C2S 数据包：管理员撤回已发送邮件。
 * 由 {@code /yzwc mail recall <mailId>} 触发。
 *
 * @param mailId 要撤回的邮件 ID
 */
public record MailRecallPayload(UUID mailId) implements CustomPacketPayload {

    public static final Identifier IDENTIFIER = Identifier.fromNamespaceAndPath(
            YouzaiworldCore.MOD_ID, "mail_recall");

    public static final Type<MailRecallPayload> ID = new Type<>(IDENTIFIER);

    public static final StreamCodec<RegistryFriendlyByteBuf, MailRecallPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, p) -> buf.writeUUID(p.mailId()),
                    buf -> new MailRecallPayload(buf.readUUID())
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
