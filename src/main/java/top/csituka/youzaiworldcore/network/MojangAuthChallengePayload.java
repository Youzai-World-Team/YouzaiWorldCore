package top.csituka.youzaiworldcore.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import top.csituka.youzaiworldcore.YouzaiworldCore;

/** S2C：要求在线会话客户端通过 Mojang 会话服务完成一次随机挑战。 */
@SuppressWarnings("null")
public record MojangAuthChallengePayload(String challenge) implements CustomPacketPayload {

    public static final Identifier IDENTIFIER = Identifier.fromNamespaceAndPath(
            YouzaiworldCore.MOD_ID, "mojang_auth_challenge");
    public static final Type<MojangAuthChallengePayload> ID = new Type<>(IDENTIFIER);
    public static final StreamCodec<RegistryFriendlyByteBuf, MojangAuthChallengePayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, payload) -> buf.writeUtf(payload.challenge(), 128),
                    buf -> new MojangAuthChallengePayload(buf.readUtf(128)));

    public MojangAuthChallengePayload {
        challenge = challenge == null ? "" : challenge;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
