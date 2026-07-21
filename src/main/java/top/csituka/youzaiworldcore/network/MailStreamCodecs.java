package top.csituka.youzaiworldcore.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import top.csituka.youzaiworldcore.mail.*;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 邮件系统网络编解码工具类。
 * <p>提供 {@link TargetSpec}、{@link MailAttachment}、{@link MailRef}、{@link Mail} 等模型在网络包中的编解码。</p>
 */
@SuppressWarnings("null")
public final class MailStreamCodecs {

    private MailStreamCodecs() {}

    // ===== TargetSpec =====

    public static final StreamCodec<RegistryFriendlyByteBuf, TargetSpec> TARGET_SPEC =
            StreamCodec.of(
                    (buf, spec) -> {
                        buf.writeByte(spec.scope());
                        buf.writeVarInt(spec.args().size());
                        for (String arg : spec.args()) {
                            buf.writeUtf(arg);
                        }
                    },
                    buf -> {
                        byte scope = buf.readByte();
                        int size = buf.readVarInt();
                        List<String> args = new ArrayList<>(size);
                        for (int i = 0; i < size; i++) {
                            args.add(buf.readUtf());
                        }
                        return new TargetSpec(scope, args);
                    }
            );

    // ===== MailAttachment（磁盘 NBT 版本） =====

    public static final StreamCodec<RegistryFriendlyByteBuf, MailAttachment> MAIL_ATTACHMENT =
            StreamCodec.of(
                    (buf, att) -> {
                        buf.writeEnum(att.type());
                        buf.writeUtf(att.data() != null ? att.data() : "");
                        buf.writeVarInt(att.amount());
                        buf.writeBoolean(att.itemNbt() != null);
                        if (att.itemNbt() != null) {
                            buf.writeUtf(att.itemNbt());
                        }
                    },
                    buf -> {
                        AttachmentType type = buf.readEnum(AttachmentType.class);
                        String data = buf.readUtf();
                        int amount = buf.readVarInt();
                        String itemNbt = buf.readBoolean() ? buf.readUtf() : null;
                        return new MailAttachment(type, data, amount, itemNbt);
                    }
            );

    // ===== MailRef =====

    public static final StreamCodec<RegistryFriendlyByteBuf, MailRef> MAIL_REF =
            StreamCodec.of(
                    (buf, ref) -> {
                        buf.writeUUID(ref.getMailId());
                        buf.writeBoolean(ref.isRead());
                        buf.writeBoolean(ref.isStarred());
                        buf.writeBoolean(ref.isClaimed());
                    },
                    buf -> {
                        UUID mailId = buf.readUUID();
                        boolean read = buf.readBoolean();
                        boolean starred = buf.readBoolean();
                        boolean claimed = buf.readBoolean();
                        MailRef ref = new MailRef(mailId);
                        ref.setRead(read);
                        ref.setStarred(starred);
                        ref.setClaimed(claimed);
                        return ref;
                    }
            );

    // ===== Mail（完整，含所有字段） =====

    public static final StreamCodec<RegistryFriendlyByteBuf, Mail> MAIL =
            StreamCodec.of(
                    (buf, mail) -> {
                        buf.writeUUID(mail.getId());
                        buf.writeEnum(mail.getType());
                        buf.writeUtf(mail.getSender() != null ? mail.getSender() : "");
                        // targets
                        buf.writeVarInt(mail.getTargets() != null ? mail.getTargets().size() : 0);
                        if (mail.getTargets() != null) {
                            for (TargetSpec spec : mail.getTargets()) {
                                TARGET_SPEC.encode(buf, spec);
                            }
                        }
                        buf.writeUtf(mail.getScopeSummary() != null ? mail.getScopeSummary() : "");
                        buf.writeUtf(mail.getTitle() != null ? mail.getTitle() : "");
                        buf.writeUtf(mail.getBody() != null ? mail.getBody() : "");
                        buf.writeVarLong(mail.getCreatedTime());
                        buf.writeBoolean(mail.getExpireTime() != null);
                        if (mail.getExpireTime() != null) {
                            buf.writeVarLong(mail.getExpireTime());
                        }
                        buf.writeBoolean(mail.isClaimed());
                        buf.writeBoolean(mail.isHidden());
                        // attachments
                        buf.writeVarInt(mail.getAttachments() != null ? mail.getAttachments().size() : 0);
                        if (mail.getAttachments() != null) {
                            for (MailAttachment att : mail.getAttachments()) {
                                MAIL_ATTACHMENT.encode(buf, att);
                            }
                        }
                    },
                    buf -> {
                        Mail mail = new Mail();
                        mail.setId(buf.readUUID());
                        mail.setType(buf.readEnum(MailType.class));
                        mail.setSender(buf.readUtf());
                        int targetsSize = buf.readVarInt();
                        List<TargetSpec> targets = new ArrayList<>(targetsSize);
                        for (int i = 0; i < targetsSize; i++) {
                            targets.add(TARGET_SPEC.decode(buf));
                        }
                        mail.setTargets(targets);
                        mail.setScopeSummary(buf.readUtf());
                        mail.setTitle(buf.readUtf());
                        mail.setBody(buf.readUtf());
                        mail.setCreatedTime(buf.readVarLong());
                        mail.setExpireTime(buf.readBoolean() ? buf.readVarLong() : null);
                        mail.setClaimed(buf.readBoolean());
                        mail.setHidden(buf.readBoolean());
                        int attSize = buf.readVarInt();
                        List<MailAttachment> attachments = new ArrayList<>(attSize);
                        for (int i = 0; i < attSize; i++) {
                            attachments.add(MAIL_ATTACHMENT.decode(buf));
                        }
                        mail.setAttachments(attachments);
                        return mail;
                    }
            );

    // ===== MailRef + Mail 组合（收件箱列表使用） =====

    public record MailRefAndMail(MailRef ref, Mail mail) {}

    public static final StreamCodec<RegistryFriendlyByteBuf, MailRefAndMail> REF_AND_MAIL =
            StreamCodec.of(
                    (buf, pair) -> {
                        MAIL_REF.encode(buf, pair.ref());
                        MAIL.encode(buf, pair.mail());
                    },
                    buf -> new MailRefAndMail(MAIL_REF.decode(buf), MAIL.decode(buf))
            );

    // ===== MailSummary（已发送邮件摘要） =====

    public record MailSummary(UUID mailId, MailType type, String title, String scopeSummary,
                              long sentTime, Long expireTime, String sender) {}

    public static final StreamCodec<RegistryFriendlyByteBuf, MailSummary> MAIL_SUMMARY =
            StreamCodec.of(
                    (buf, sum) -> {
                        buf.writeUUID(sum.mailId());
                        buf.writeEnum(sum.type());
                        buf.writeUtf(sum.title() != null ? sum.title() : "");
                        buf.writeUtf(sum.scopeSummary() != null ? sum.scopeSummary() : "");
                        buf.writeVarLong(sum.sentTime());
                        buf.writeBoolean(sum.expireTime() != null);
                        if (sum.expireTime() != null) {
                            buf.writeVarLong(sum.expireTime());
                        }
                        buf.writeUtf(sum.sender() != null ? sum.sender() : "");
                    },
                    buf -> {
                        UUID mailId = buf.readUUID();
                        MailType type = buf.readEnum(MailType.class);
                        String title = buf.readUtf();
                        String scopeSummary = buf.readUtf();
                        long sentTime = buf.readVarLong();
                        Long expireTime = buf.readBoolean() ? buf.readVarLong() : null;
                        String sender = buf.readUtf();
                        return new MailSummary(mailId, type, title, scopeSummary, sentTime, expireTime, sender);
                    }
            );
}
