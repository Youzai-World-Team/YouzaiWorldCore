package top.csituka.youzaiworldcore.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import top.csituka.youzaiworldcore.YouzaiworldCore;

/**
 * S2C 数据包：服务端推送未读邮件数量与发布权限标志。
 * 用于客户端显示未读徽标与控制 [发布邮件]/[已发送邮件] 按钮显隐。
 *
 * @param unreadCount 未读邮件数量
 * @param canSend     当前玩家是否可发布邮件（持有邮件权限）
 */
public record MailUnreadCountPayload(int unreadCount, boolean canSend) implements CustomPacketPayload {

    public static final Identifier IDENTIFIER = Identifier.fromNamespaceAndPath(
            YouzaiworldCore.MOD_ID, "mail_unread_count");

    public static final Type<MailUnreadCountPayload> ID = new Type<>(IDENTIFIER);

    public static final StreamCodec<RegistryFriendlyByteBuf, MailUnreadCountPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, p) -> {
                        buf.writeVarInt(p.unreadCount());
                        buf.writeBoolean(p.canSend());
                    },
                    buf -> new MailUnreadCountPayload(buf.readVarInt(), buf.readBoolean())
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
