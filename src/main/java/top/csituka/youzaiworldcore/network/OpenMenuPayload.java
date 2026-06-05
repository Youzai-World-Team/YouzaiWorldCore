package top.csituka.youzaiworldcore.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NonNull;
import top.csituka.youzaiworldcore.YouzaiworldCore;

public record OpenMenuPayload(String menuName) implements CustomPacketPayload {

    public static final Identifier OPEN_MENU_ID = Identifier.fromNamespaceAndPath(YouzaiworldCore.MOD_ID, "open_menu");
    public static final CustomPacketPayload.Type<OpenMenuPayload> ID = new CustomPacketPayload.Type<>(OPEN_MENU_ID);

    public static final StreamCodec<RegistryFriendlyByteBuf, OpenMenuPayload> STREAM_CODEC = StreamCodec.of(
        (buf, payload) -> buf.writeUtf(payload.menuName),
        buf -> new OpenMenuPayload(buf.readUtf())
    );

    @Override
    @NonNull
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}