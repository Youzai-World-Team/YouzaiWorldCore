package top.csituka.youzaiworldcore.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;
import top.csituka.youzaiworldcore.YouzaiworldCore;
import top.csituka.youzaiworldcore.mail.Mail;
import top.csituka.youzaiworldcore.mail.MailRef;

import java.util.UUID;

/**
 * S2C 数据包：
 * <ul>
 *   <li>mode=0: 新增/更新一封邮件（含 ref + mail）</li>
 *   <li>mode=1: 移除一封邮件（removedMailId）</li>
 *   <li>mode=2: 编辑预填完整邮件（含 canEdit 判定）</li>
 * </ul>
 */
@SuppressWarnings("null")
public record MailUpdatePayload(
        byte mode,
        @Nullable MailRef ref,
        @Nullable Mail mail,
        @Nullable UUID removedMailId,
        boolean canEdit,
        boolean hidden
) implements CustomPacketPayload {

    public static final byte MODE_UPDATE = 0;
    public static final byte MODE_REMOVE = 1;
    public static final byte MODE_EDIT_PREFILL = 2;

    public static final Identifier IDENTIFIER = Identifier.fromNamespaceAndPath(
            YouzaiworldCore.MOD_ID, "mail_update");

    public static final Type<MailUpdatePayload> ID = new Type<>(IDENTIFIER);

    public static MailUpdatePayload createUpdate(MailRef ref, Mail mail) {
        return new MailUpdatePayload(MODE_UPDATE, ref, mail, null, false, false);
    }

    public static MailUpdatePayload createRemove(UUID removedMailId) {
        return new MailUpdatePayload(MODE_REMOVE, null, null, removedMailId, false, false);
    }

    public static MailUpdatePayload createEditPrefill(MailRef ref, Mail mail, boolean canEdit) {
        return new MailUpdatePayload(MODE_EDIT_PREFILL, ref, mail, null, canEdit, mail != null && mail.isHidden());
    }

    public static final StreamCodec<RegistryFriendlyByteBuf, MailUpdatePayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, p) -> {
                        buf.writeByte(p.mode());
                        switch (p.mode()) {
                            case MODE_UPDATE -> {
                                MailStreamCodecs.MAIL_REF.encode(buf, p.ref());
                                MailStreamCodecs.MAIL.encode(buf, p.mail());
                            }
                            case MODE_REMOVE -> buf.writeUUID(p.removedMailId());
                            case MODE_EDIT_PREFILL -> {
                                MailStreamCodecs.MAIL_REF.encode(buf, p.ref());
                                MailStreamCodecs.MAIL.encode(buf, p.mail());
                                buf.writeBoolean(p.canEdit());
                            }
                        }
                    },
                    buf -> {
                        byte mode = buf.readByte();
                        return switch (mode) {
                            case MODE_UPDATE -> {
                                MailRef ref = MailStreamCodecs.MAIL_REF.decode(buf);
                                Mail mail = MailStreamCodecs.MAIL.decode(buf);
                                yield new MailUpdatePayload(mode, ref, mail, null, false, false);
                            }
                            case MODE_REMOVE -> {
                                UUID removedId = buf.readUUID();
                                yield new MailUpdatePayload(mode, null, null, removedId, false, false);
                            }
                            case MODE_EDIT_PREFILL -> {
                                MailRef ref = MailStreamCodecs.MAIL_REF.decode(buf);
                                Mail mail = MailStreamCodecs.MAIL.decode(buf);
                                boolean canEdit = buf.readBoolean();
                                yield new MailUpdatePayload(mode, ref, mail, null, canEdit, mail.isHidden());
                            }
                            default -> throw new IllegalArgumentException("Unknown MailUpdatePayload mode: " + mode);
                        };
                    }
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
