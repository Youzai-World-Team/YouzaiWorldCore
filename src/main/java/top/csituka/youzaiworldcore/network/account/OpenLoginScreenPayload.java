package top.csituka.youzaiworldcore.network.account;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NonNull;
import top.csituka.youzaiworldcore.YouzaiworldCore;

/**
 * S2C: 服务器通知客户端打开登录/注册界面
 * mode: "login" 或 "register"
 */
public record OpenLoginScreenPayload(String mode) implements CustomPacketPayload {

    public static final Identifier IDENTIFIER = Identifier.fromNamespaceAndPath(YouzaiworldCore.MOD_ID, "open_login_screen");
    public static final CustomPacketPayload.Type<OpenLoginScreenPayload> TYPE = new CustomPacketPayload.Type<>(IDENTIFIER);

    public static final StreamCodec<RegistryFriendlyByteBuf, OpenLoginScreenPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> buf.writeUtf(payload.mode),
            buf -> new OpenLoginScreenPayload(buf.readUtf())
    );

    @Override
    @NonNull
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
