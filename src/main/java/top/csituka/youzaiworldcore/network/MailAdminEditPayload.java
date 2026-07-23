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
import java.util.UUID;

/**
 * C2S 数据包：管理员编辑已发送邮件 或 取消编辑。
 * 由 {@link MailComposeScreen}（编辑模式）的 [保存修改] 或 [取消] 按钮触发。
 *
 * @param mailId       目标邮件 ID
 * @param cancel       取消编辑标志（true 时仅恢复 hidden=false，其他字段忽略）
 * @param targets      新接收范围（cancel=true 时忽略）
 * @param type         新邮件类型（cancel=true 时忽略）
 * @param title        新主题（cancel=true 时忽略）
 * @param body         新正文（cancel=true 时忽略）
 * @param expireOption 新过期选项（cancel=true 时忽略）
 * @param attachments  新附件列表（cancel=true 时忽略）
 */
public record MailAdminEditPayload(
        UUID mailId,
        boolean cancel,
        List<TargetSpec> targets,
        MailType mailType,
        String title,
        String body,
        byte expireOption,
        List<AttachmentData> attachments
) implements CustomPacketPayload {

    public static final Identifier IDENTIFIER = Identifier.fromNamespaceAndPath(
            YouzaiworldCore.MOD_ID, "mail_admin_edit");

    @SuppressWarnings("null")
    public static final Type<MailAdminEditPayload> ID = new Type<>(IDENTIFIER);

    @SuppressWarnings("null")
    public static final StreamCodec<RegistryFriendlyByteBuf, MailAdminEditPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, p) -> {
                        buf.writeUUID(p.mailId());
                        buf.writeBoolean(p.cancel());
                        if (!p.cancel()) {
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
                        }
                    },
                    buf -> {
                        UUID mailId = buf.readUUID();
                        boolean cancel = buf.readBoolean();
                        if (cancel) {
                            return new MailAdminEditPayload(mailId, true, List.of(),
                                    MailType.ANNOUNCEMENT, "", "", (byte) 0, List.of());
                        }
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
                        return new MailAdminEditPayload(mailId, false, targets, mailType, title, body, expireOption, attachments);
                    }
            );

    @Override
    @SuppressWarnings("null")
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
