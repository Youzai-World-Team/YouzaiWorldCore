package top.csituka.youzaiworldcore.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import top.csituka.youzaiworldcore.YouzaiworldCore;

/** S2C：通知客户端当前会话可以同步已保存的皮肤与披风。 */
@SuppressWarnings("null")
public record CosmeticReadyPayload(boolean ready, int requestCooldownSeconds) implements CustomPacketPayload {

    public CosmeticReadyPayload {
        requestCooldownSeconds = Math.max(0, requestCooldownSeconds);
    }

    public static CosmeticReadyPayload disabled() {
        return new CosmeticReadyPayload(false, 0);
    }

    public static final Identifier IDENTIFIER = Identifier.fromNamespaceAndPath(
            YouzaiworldCore.MOD_ID, "cosmetic_editor_ready");
    public static final Type<CosmeticReadyPayload> ID = new Type<>(IDENTIFIER);
    public static final StreamCodec<RegistryFriendlyByteBuf, CosmeticReadyPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, payload) -> {
                        buf.writeBoolean(payload.ready());
                        buf.writeVarInt(payload.requestCooldownSeconds());
                    },
                    buf -> new CosmeticReadyPayload(buf.readBoolean(), buf.readVarInt()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
