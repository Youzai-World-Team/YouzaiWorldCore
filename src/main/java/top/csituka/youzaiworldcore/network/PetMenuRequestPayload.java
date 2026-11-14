package top.csituka.youzaiworldcore.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import top.csituka.youzaiworldcore.YouzaiworldCore;

@SuppressWarnings("null")
public record PetMenuRequestPayload(long requestId, String action, String petId, String value) implements CustomPacketPayload {
    public static final Type<PetMenuRequestPayload> ID = new Type<>(Identifier.fromNamespaceAndPath(YouzaiworldCore.MOD_ID, "pet_menu_request"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PetMenuRequestPayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> { buf.writeLong(p.requestId()); buf.writeUtf(p.action(), 16); buf.writeUtf(p.petId(), 32); buf.writeUtf(p.value(), 64); },
            buf -> new PetMenuRequestPayload(buf.readLong(), buf.readUtf(16), buf.readUtf(32), buf.readUtf(64)));
    @Override public Type<? extends CustomPacketPayload> type() { return ID; }
}
