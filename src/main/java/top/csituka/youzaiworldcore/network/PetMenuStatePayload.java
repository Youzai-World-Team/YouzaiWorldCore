package top.csituka.youzaiworldcore.network;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import top.csituka.youzaiworldcore.YouzaiworldCore;

@SuppressWarnings("null")
public record PetMenuStatePayload(long requestId, boolean success, Component message, List<Entry> pets) implements CustomPacketPayload {
    public record Entry(String id, String name, String mode) { }
    public static final Type<PetMenuStatePayload> ID = new Type<>(Identifier.fromNamespaceAndPath(YouzaiworldCore.MOD_ID, "pet_menu_state"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PetMenuStatePayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeLong(p.requestId()); buf.writeBoolean(p.success()); ComponentSerialization.STREAM_CODEC.encode(buf, p.message()); buf.writeVarInt(p.pets().size());
                for (Entry e : p.pets()) { buf.writeUtf(e.id()); buf.writeUtf(e.name()); buf.writeUtf(e.mode()); }
            }, buf -> {
                long id = buf.readLong(); boolean success = buf.readBoolean(); Component message = ComponentSerialization.STREAM_CODEC.decode(buf); int count = buf.readVarInt();
                if (count < 0 || count > 10000) throw new IllegalArgumentException("Invalid pet list size");
                List<Entry> pets = new ArrayList<>(count);
                for (int i = 0; i < count; i++) pets.add(new Entry(buf.readUtf(), buf.readUtf(), buf.readUtf()));
                return new PetMenuStatePayload(id, success, message, List.copyOf(pets));
            });
    @Override public Type<? extends CustomPacketPayload> type() { return ID; }
}
