package top.csituka.youzaiworldcore.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** 地图位置共享管理；客户端只提交动作，名单与请求状态由服务端回传。 */
public record MapSocialPayload(Action action, UUID target, boolean blacklist, List<UUID> allowed,
                               List<UUID> blocked, List<UUID> incoming, String result) implements CustomPacketPayload {
    public enum Action { FETCH, STATE, MODE, ALLOW, REMOVE, REQUEST, ACCEPT, REJECT, BLOCK, UNBLOCK }
    public static final Type<MapSocialPayload> ID = new Type<>(Identifier.fromNamespaceAndPath("youzaiworldcore", "map_social"));
    public static MapSocialPayload request(Action action, UUID target, boolean blacklist) {
        return new MapSocialPayload(action, target, blacklist, List.of(), List.of(), List.of(), "");
    }
    public static final StreamCodec<RegistryFriendlyByteBuf, MapSocialPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override public MapSocialPayload decode(RegistryFriendlyByteBuf buf) {
            return new MapSocialPayload(buf.readEnum(Action.class), buf.readUUID(), buf.readBoolean(), read(buf), read(buf), read(buf), buf.readUtf(64));
        }
        @Override public void encode(RegistryFriendlyByteBuf buf, MapSocialPayload value) {
            buf.writeEnum(value.action()); buf.writeUUID(value.target()); buf.writeBoolean(value.blacklist());
            write(buf, value.allowed()); write(buf, value.blocked()); write(buf, value.incoming()); buf.writeUtf(value.result(), 64);
        }
        private List<UUID> read(RegistryFriendlyByteBuf buf) {
            int count = MapStreamCodecs.count(buf, 256); var list = new ArrayList<UUID>(count);
            for (int i = 0; i < count; i++) list.add(buf.readUUID()); return List.copyOf(list);
        }
        private void write(RegistryFriendlyByteBuf buf, List<UUID> list) {
            buf.writeVarInt(list.size()); for (UUID id : list) buf.writeUUID(id);
        }
    };
    @Override public Type<? extends CustomPacketPayload> type() { return ID; }
}
