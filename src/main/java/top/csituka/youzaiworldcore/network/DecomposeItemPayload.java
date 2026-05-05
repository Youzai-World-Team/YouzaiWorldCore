package top.csituka.youzaiworldcore.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import top.csituka.youzaiworldcore.YouzaiworldCore;

public record DecomposeItemPayload() implements CustomPacketPayload {
    
    public static final Identifier DECOMPOSE_ITEM_ID = Identifier.fromNamespaceAndPath(YouzaiworldCore.MOD_ID, "decompose_item");
    public static final CustomPacketPayload.Type<DecomposeItemPayload> ID = new CustomPacketPayload.Type<>(DECOMPOSE_ITEM_ID);
    
    public static final StreamCodec<RegistryFriendlyByteBuf, DecomposeItemPayload> STREAM_CODEC = StreamCodec.of(
        (buf, payload) -> {},
        buf -> new DecomposeItemPayload()
    );
    
    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
