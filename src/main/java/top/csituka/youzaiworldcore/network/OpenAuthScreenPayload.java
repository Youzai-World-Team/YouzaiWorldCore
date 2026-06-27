package top.csituka.youzaiworldcore.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NonNull;
import top.csituka.youzaiworldcore.YouzaiworldCore;

/**
 * 服务端 → 客户端：打开账户认证界面（注册/登录）
 * type 值为 "register" 或 "login"
 */
public record OpenAuthScreenPayload(String screenType, String username) implements CustomPacketPayload {

    public static final Identifier OPEN_AUTH_SCREEN_ID = Identifier.fromNamespaceAndPath(
            YouzaiworldCore.MOD_ID, "open_auth_screen");
    public static final CustomPacketPayload.Type<OpenAuthScreenPayload> ID =
            new CustomPacketPayload.Type<>(OPEN_AUTH_SCREEN_ID);

    public static final StreamCodec<RegistryFriendlyByteBuf, OpenAuthScreenPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeUtf(payload.screenType);
                buf.writeUtf(payload.username);
            },
            buf -> new OpenAuthScreenPayload(buf.readUtf(), buf.readUtf())
    );

    @Override
    @NonNull
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
