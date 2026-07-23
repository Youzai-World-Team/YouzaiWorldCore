package top.csituka.youzaiworldcore.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import top.csituka.youzaiworldcore.YouzaiworldCore;

import java.util.UUID;

/**
 * S2C 数据包：服务端返回某次操作的结果（成功/失败 + 原因）。
 *
 * @param mailId  操作目标邮件 ID（可为 null）
 * @param success 是否成功
 * @param reason  失败原因或提示信息（成功时可为空字符串）
 */
public record MailOpResultPayload(UUID mailId, boolean success, String reason) implements CustomPacketPayload {

    public static final Identifier IDENTIFIER = Identifier.fromNamespaceAndPath(
            YouzaiworldCore.MOD_ID, "mail_op_result");

    @SuppressWarnings("null")
    public static final Type<MailOpResultPayload> ID = new Type<>(IDENTIFIER);

    public static MailOpResultPayload success(UUID mailId, String reason) {
        return new MailOpResultPayload(mailId, true, reason);
    }

    public static MailOpResultPayload failure(UUID mailId, String reason) {
        return new MailOpResultPayload(mailId, false, reason);
    }

    @SuppressWarnings("null")
    public static final StreamCodec<RegistryFriendlyByteBuf, MailOpResultPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, p) -> {
                        buf.writeBoolean(p.mailId() != null);
                        if (p.mailId() != null) {
                            buf.writeUUID(p.mailId());
                        }
                        buf.writeBoolean(p.success());
                        buf.writeUtf(p.reason() != null ? p.reason() : "");
                    },
                    buf -> {
                        UUID mailId = buf.readBoolean() ? buf.readUUID() : null;
                        boolean success = buf.readBoolean();
                        String reason = buf.readUtf();
                        return new MailOpResultPayload(mailId, success, reason);
                    }
            );

    @Override
    @SuppressWarnings("null")
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
