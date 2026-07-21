package top.csituka.youzaiworldcore.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import top.csituka.youzaiworldcore.YouzaiworldCore;

/**
 * C2S 数据包：管理员查看指定玩家的信箱。
 * 由 {@code /yzwc mail list [player]} 触发。
 *
 * @param targetPlayer 目标玩家名（空字符串表示查看自己的）
 */
public record MailListRequestPayload(String targetPlayer) implements CustomPacketPayload {

    public static final Identifier IDENTIFIER = Identifier.fromNamespaceAndPath(
            YouzaiworldCore.MOD_ID, "mail_list_request");

    public static final Type<MailListRequestPayload> ID = new Type<>(IDENTIFIER);

    public static final StreamCodec<RegistryFriendlyByteBuf, MailListRequestPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, p) -> buf.writeUtf(p.targetPlayer()),
                    buf -> new MailListRequestPayload(buf.readUtf())
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
