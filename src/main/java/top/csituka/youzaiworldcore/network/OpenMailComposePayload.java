package top.csituka.youzaiworldcore.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import top.csituka.youzaiworldcore.YouzaiworldCore;

/**
 * S2C 数据包：服务端令客户端打开发布 GUI（MailComposeScreen，新建模式）。
 * 由 {@link MailComposeOpenPayload} 的处理器或 {@code /yzwc mail send_mail} 命令触发（需权限校验后）。
 */
public record OpenMailComposePayload() implements CustomPacketPayload {

    public static final Identifier IDENTIFIER = Identifier.fromNamespaceAndPath(
            YouzaiworldCore.MOD_ID, "open_mail_compose");

    public static final Type<OpenMailComposePayload> ID = new Type<>(IDENTIFIER);

    public static final StreamCodec<RegistryFriendlyByteBuf, OpenMailComposePayload> STREAM_CODEC =
            StreamCodec.unit(new OpenMailComposePayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
