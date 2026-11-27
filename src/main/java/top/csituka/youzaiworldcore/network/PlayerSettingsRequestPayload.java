package top.csituka.youzaiworldcore.network;

import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import top.csituka.youzaiworldcore.YouzaiworldCore;

/** 查询或修改当前玩家自己的服务端设置。 */
@SuppressWarnings("null")
public record PlayerSettingsRequestPayload(UUID requestId, Option option, boolean enabled)
        implements CustomPacketPayload {
    public static final Type<PlayerSettingsRequestPayload> ID = new Type<>(
            Identifier.fromNamespaceAndPath(YouzaiworldCore.MOD_ID, "player_settings_request"));

    @SuppressWarnings("deprecation")
    public static final StreamCodec<RegistryFriendlyByteBuf, PlayerSettingsRequestPayload> STREAM_CODEC = StreamCodec
            .of((buf, payload) -> {
                buf.writeUUID(payload.requestId());
                buf.writeEnum(payload.option());
                buf.writeBoolean(payload.enabled());
            }, buf -> new PlayerSettingsRequestPayload(buf.readUUID(), buf.readEnum(Option.class), buf.readBoolean()));

    public enum Option {
        QUERY,
        DOUBLE_DOORS,
        DAMAGE_NUMBERS
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
