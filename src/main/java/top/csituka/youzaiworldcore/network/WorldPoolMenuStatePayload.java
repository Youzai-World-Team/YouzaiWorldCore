package top.csituka.youzaiworldcore.network;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** S2C：服务端公开世界目录及当前世界，不包含隐藏池或玩家背包数据。 */
public record WorldPoolMenuStatePayload(UUID requestId, String currentPoolId, String currentName, List<Entry> entries)
        implements CustomPacketPayload {
    public record Entry(String poolId, String name, String gameMode, boolean available) { }
    public WorldPoolMenuStatePayload { entries = List.copyOf(entries); }
    public static final Type<WorldPoolMenuStatePayload> ID = new Type<>(
            Identifier.fromNamespaceAndPath("youzaiworldcore", "world_pool_menu_state"));
    public static final StreamCodec<RegistryFriendlyByteBuf, WorldPoolMenuStatePayload> STREAM_CODEC = StreamCodec.of(
            (buf, value) -> {
                buf.writeUUID(value.requestId()); buf.writeUtf(value.currentPoolId(), 256);
                buf.writeUtf(value.currentName(), 512); buf.writeVarInt(value.entries().size());
                for (Entry entry : value.entries()) {
                    buf.writeUtf(entry.poolId(), 256); buf.writeUtf(entry.name(), 512);
                    buf.writeUtf(entry.gameMode(), 32); buf.writeBoolean(entry.available());
                }
            }, buf -> {
                UUID id = buf.readUUID(); String current = buf.readUtf(256), name = buf.readUtf(512);
                int count = buf.readVarInt();
                if (count < 0 || count > 128) throw new IllegalArgumentException("世界菜单条目数量越界");
                List<Entry> entries = new ArrayList<>(count);
                for (int i = 0; i < count; i++) entries.add(new Entry(
                        buf.readUtf(256), buf.readUtf(512), buf.readUtf(32), buf.readBoolean()));
                return new WorldPoolMenuStatePayload(id, current, name, entries);
            });
    @Override public Type<? extends CustomPacketPayload> type() { return ID; }
}
