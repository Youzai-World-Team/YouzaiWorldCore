package top.csituka.youzaiworldcore.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import top.csituka.youzaiworldcore.YouzaiworldCore;

import java.util.UUID;

/**
 * C2S：客户端提交当前启动器登录的 Mojang 档案标识，请求服务端核验正版外观。
 * <p>服务端不会信任客户端提交的纹理内容，而是使用自身的 Mojang 会话服务重新取得签名纹理属性。</p>
 */
@SuppressWarnings("null")
public record MojangProfileRequestPayload(
        String challenge, UUID profileId, String profileName) implements CustomPacketPayload {

    public static final Identifier IDENTIFIER = Identifier.fromNamespaceAndPath(
            YouzaiworldCore.MOD_ID, "mojang_profile_request");
    public static final Type<MojangProfileRequestPayload> ID = new Type<>(IDENTIFIER);
    public static final StreamCodec<RegistryFriendlyByteBuf, MojangProfileRequestPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, payload) -> {
                        buf.writeUtf(payload.challenge(), 128);
                        buf.writeUUID(payload.profileId());
                        buf.writeUtf(payload.profileName(), 64);
                    },
                    buf -> new MojangProfileRequestPayload(
                            buf.readUtf(128), buf.readUUID(), buf.readUtf(64)));

    public MojangProfileRequestPayload {
        challenge = challenge == null ? "" : challenge;
        profileId = profileId == null ? new UUID(0L, 0L) : profileId;
        profileName = profileName == null ? "" : profileName;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
