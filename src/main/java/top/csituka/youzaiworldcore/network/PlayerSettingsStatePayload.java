package top.csituka.youzaiworldcore.network;

import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import top.csituka.youzaiworldcore.YouzaiworldCore;

/** 返回该请求执行后的双开门与伤害跳字状态。 */
@SuppressWarnings("null")
public record PlayerSettingsStatePayload(UUID requestId, boolean doubleDoors, boolean damageNumbers)
        implements CustomPacketPayload {
    public static final Type<PlayerSettingsStatePayload> ID = new Type<>(
            Identifier.fromNamespaceAndPath(YouzaiworldCore.MOD_ID, "player_settings_state"));

    public static final StreamCodec<RegistryFriendlyByteBuf, PlayerSettingsStatePayload> STREAM_CODEC =
            StreamCodec.of((buf, payload) -> {
                buf.writeUUID(payload.requestId());
                buf.writeBoolean(payload.doubleDoors());
                buf.writeBoolean(payload.damageNumbers());
            }, buf -> new PlayerSettingsStatePayload(buf.readUUID(), buf.readBoolean(), buf.readBoolean()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
