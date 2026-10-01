package top.csituka.youzaiworldcore.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import top.csituka.youzaiworldcore.YouzaiworldCore;

import java.util.UUID;

/** S2C：同步单个玩家的 AFK 状态，供客户端名字牌渲染使用。 */
@SuppressWarnings("null")
public record AfkStatePayload(UUID playerUuid, boolean afk, String prefix) implements CustomPacketPayload {

    public static final Identifier IDENTIFIER = Identifier.fromNamespaceAndPath(
            YouzaiworldCore.MOD_ID, "afk_state_v2");
    public static final Type<AfkStatePayload> ID = new Type<>(IDENTIFIER);
    public static final StreamCodec<RegistryFriendlyByteBuf, AfkStatePayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, payload) -> {
                        buf.writeUUID(payload.playerUuid());
                        buf.writeBoolean(payload.afk());
                        buf.writeUtf(payload.prefix(), 64);
                    },
                    buf -> new AfkStatePayload(buf.readUUID(), buf.readBoolean(), buf.readUtf(64)));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
