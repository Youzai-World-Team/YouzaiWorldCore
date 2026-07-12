package top.csituka.youzaiworldcore.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NonNull;
import top.csituka.youzaiworldcore.YouzaiworldCore;

@SuppressWarnings("null")
public record ManaSyncPayload(int mana) implements CustomPacketPayload {

    public static final Identifier MANA_SYNC_ID = Identifier.fromNamespaceAndPath(YouzaiworldCore.MOD_ID, "mana_sync");
    public static final CustomPacketPayload.Type<ManaSyncPayload> ID = new CustomPacketPayload.Type<>(MANA_SYNC_ID);

    public static final StreamCodec<RegistryFriendlyByteBuf, ManaSyncPayload> STREAM_CODEC = StreamCodec.of(
        (buf, payload) -> buf.writeInt(payload.mana),
        buf -> new ManaSyncPayload(buf.readInt())
    );

    @Override
    @NonNull
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
