package top.csituka.youzaiworldcore.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NonNull;
import top.csituka.youzaiworldcore.YouzaiworldCore;

/**
 * S2C：同步单玩家功能开关状态到客户端。
 */
@SuppressWarnings("null")
public record FunctionToggleSyncPayload(
        boolean ladderExtendDownward,
        boolean cropXpDrop,
        boolean toolInfoOverlay,
        boolean blockAnimation,
        boolean craftingSound,
        boolean itemSparkle) implements CustomPacketPayload {

    public static final Identifier FUNC_TOGGLE_ID = Identifier.fromNamespaceAndPath(
            YouzaiworldCore.MOD_ID, "function_toggle_sync");
    public static final CustomPacketPayload.Type<FunctionToggleSyncPayload> TYPE =
            new CustomPacketPayload.Type<>(FUNC_TOGGLE_ID);

    public static final StreamCodec<RegistryFriendlyByteBuf, FunctionToggleSyncPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, p) -> {
                        buf.writeBoolean(p.ladderExtendDownward);
                        buf.writeBoolean(p.cropXpDrop);
                        buf.writeBoolean(p.toolInfoOverlay);
                        buf.writeBoolean(p.blockAnimation);
                        buf.writeBoolean(p.craftingSound);
                        buf.writeBoolean(p.itemSparkle);
                    },
                    buf -> new FunctionToggleSyncPayload(
                            buf.readBoolean(), buf.readBoolean(), buf.readBoolean(),
                            buf.readBoolean(), buf.readBoolean(), buf.readBoolean()));

    @Override
    @NonNull
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
