package top.csituka.youzaiworldcore.network;

import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** C2S：空池 ID 读取世界菜单，其他值申请切换；请求 ID 隔离迟到结果。 */
public record WorldPoolMenuRequestPayload(UUID requestId, String poolId) implements CustomPacketPayload {
    public static final Type<WorldPoolMenuRequestPayload> ID = new Type<>(
            Identifier.fromNamespaceAndPath("youzaiworldcore", "world_pool_menu_request"));
    public static final StreamCodec<RegistryFriendlyByteBuf, WorldPoolMenuRequestPayload> STREAM_CODEC = StreamCodec.of(
            (buf, value) -> { buf.writeUUID(value.requestId()); buf.writeUtf(value.poolId(), 256); },
            buf -> new WorldPoolMenuRequestPayload(buf.readUUID(), buf.readUtf(256)));
    @Override public Type<? extends CustomPacketPayload> type() { return ID; }
}
