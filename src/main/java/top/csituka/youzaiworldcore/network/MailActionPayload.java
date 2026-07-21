package top.csituka.youzaiworldcore.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import top.csituka.youzaiworldcore.YouzaiworldCore;

import java.util.UUID;

/**
 * C2S 数据包：玩家对某封邮件的操作（打开详情 / 标记已读 / 星标 / 取消星标 / 领取 / 删除）。
 *
 * @param mailId 操作目标邮件 ID
 * @param action 操作类型：OPEN / READ / STAR / UNSTAR / CLAIM / DELETE
 */
public record MailActionPayload(UUID mailId, String action) implements CustomPacketPayload {

    public static final String ACTION_OPEN = "OPEN";
    public static final String ACTION_READ = "READ";
    public static final String ACTION_STAR = "STAR";
    public static final String ACTION_UNSTAR = "UNSTAR";
    public static final String ACTION_CLAIM = "CLAIM";
    public static final String ACTION_DELETE = "DELETE";

    public static final Identifier IDENTIFIER = Identifier.fromNamespaceAndPath(
            YouzaiworldCore.MOD_ID, "mail_action");

    public static final Type<MailActionPayload> ID = new Type<>(IDENTIFIER);

    public static final StreamCodec<RegistryFriendlyByteBuf, MailActionPayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeUUID(p.mailId());
                buf.writeUtf(p.action());
            },
            buf -> new MailActionPayload(buf.readUUID(), buf.readUtf()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
