package top.csituka.youzaiworldcore.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import top.csituka.youzaiworldcore.YouzaiworldCore;

public record FlyBeaconActivePayload(boolean active) implements CustomPacketPayload {
    
    public static final Identifier FLY_BEACON_ACTIVE_ID = Identifier.fromNamespaceAndPath(YouzaiworldCore.MOD_ID, "fly_beacon_active");
    public static final CustomPacketPayload.Type<FlyBeaconActivePayload> ID = new CustomPacketPayload.Type<>(FLY_BEACON_ACTIVE_ID);
    
    public static final StreamCodec<RegistryFriendlyByteBuf, FlyBeaconActivePayload> STREAM_CODEC = StreamCodec.of(
        (buf, payload) -> buf.writeBoolean(payload.active),
        buf -> new FlyBeaconActivePayload(buf.readBoolean())
    );
    
    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
