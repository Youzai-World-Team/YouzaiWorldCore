package top.csituka.youzaiworldcore.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import top.csituka.youzaiworldcore.YouzaiworldCore;

/**
 * C2S 数据包：发布页请求「已注册玩家代号」名单，用于「选取玩家」弹窗。
 * <p>服务端校验邮件发布权限后回发 {@link MailPlayerListPayload}。</p>
 */
public record MailPlayerListRequestPayload() implements CustomPacketPayload {

    public static final Identifier IDENTIFIER = Identifier.fromNamespaceAndPath(
            YouzaiworldCore.MOD_ID, "mail_player_list_request");

    @SuppressWarnings("null")
    public static final Type<MailPlayerListRequestPayload> ID = new Type<>(IDENTIFIER);

    @SuppressWarnings("null")
    public static final StreamCodec<RegistryFriendlyByteBuf, MailPlayerListRequestPayload> STREAM_CODEC =
            StreamCodec.unit(new MailPlayerListRequestPayload());

    @Override
    @SuppressWarnings("null")
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
