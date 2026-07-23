package top.csituka.youzaiworldcore.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import top.csituka.youzaiworldcore.YouzaiworldCore;
import top.csituka.youzaiworldcore.mail.MailType;
import top.csituka.youzaiworldcore.mail.TargetSpec;

import java.util.ArrayList;
import java.util.List;

/**
 * C2S 数据包：管理员发布新邮件。
 * 由 {@link MailComposeScreen} 的 [发布] 按钮触发。
 *
 * @param targets      接收范围列表（多选并集）
 * @param type         邮件类型
 * @param title        主题
 * @param body         正文
 * @param expireOption 过期选项编码（0=1d, 1=7d, 2=30d, 3=permanent）
 * @param attachments  附件列表
 */
public record MailAdminSendPayload(
        List<TargetSpec> targets,
        MailType mailType,
        String title,
        String body,
        byte expireOption,
        List<AttachmentData> attachments
) implements CustomPacketPayload {

    public static final Identifier IDENTIFIER = Identifier.fromNamespaceAndPath(
            YouzaiworldCore.MOD_ID, "mail_admin_send");

    @SuppressWarnings("null")
    public static final Type<MailAdminSendPayload> ID = new Type<>(IDENTIFIER);

    @SuppressWarnings("null")
    public static final StreamCodec<RegistryFriendlyByteBuf, MailAdminSendPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, p) -> {
                        // targets
                        buf.writeVarInt(p.targets().size());
                        for (TargetSpec spec : p.targets()) {
                            MailStreamCodecs.TARGET_SPEC.encode(buf, spec);
                        }
                        buf.writeEnum(p.mailType());
                        buf.writeUtf(p.title());
                        buf.writeUtf(p.body());
                        buf.writeByte(p.expireOption());
                        // attachments
                        buf.writeVarInt(p.attachments().size());
                        for (AttachmentData att : p.attachments()) {
                            AttachmentData.STREAM_CODEC.encode(buf, att);
                        }
                    },
                    buf -> {
                        int targetsSize = buf.readVarInt();
                        List<TargetSpec> targets = new ArrayList<>(targetsSize);
                        for (int i = 0; i < targetsSize; i++) {
                            targets.add(MailStreamCodecs.TARGET_SPEC.decode(buf));
                        }
                        MailType mailType = buf.readEnum(MailType.class);
                        String title = buf.readUtf();
                        String body = buf.readUtf();
                        byte expireOption = buf.readByte();
                        int attSize = buf.readVarInt();
                        List<AttachmentData> attachments = new ArrayList<>(attSize);
                        for (int i = 0; i < attSize; i++) {
                            attachments.add(AttachmentData.STREAM_CODEC.decode(buf));
                        }
                        return new MailAdminSendPayload(targets, mailType, title, body, expireOption, attachments);
                    }
            );

    @Override
    @SuppressWarnings("null")
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
