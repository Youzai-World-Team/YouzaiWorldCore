package top.csituka.youzaiworldcore.network.account;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NonNull;
import top.csituka.youzaiworldcore.YouzaiworldCore;

/**
 * S2C（服务器→客户端）打开登录/注册界面指令包。
 * <p>
 * 服务端在玩家加入游戏且尚未完成登录/注册时，通知客户端打开
 * 对应的 GUI 界面。根据 {@code mode} 字段决定打开登录界面还是注册界面。
 * </p>
 *
 * <p>mode 取值：</p>
 * <ul>
 *   <li>{@code "login"} — 打开登录界面 ({@link top.csituka.youzaiworldcore.client.screen.account.LoginScreen})</li>
 *   <li>{@code "register"} — 打开注册界面 ({@link top.csituka.youzaiworldcore.client.screen.account.RegisterScreen})</li>
 * </ul>
 *
 * @param mode 界面模式：{@code "login"} 或 {@code "register"}
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
