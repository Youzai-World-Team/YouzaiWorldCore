package top.csituka.youzaiworldcore.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NonNull;
import top.csituka.youzaiworldcore.YouzaiworldCore;

/**
 * C2S 数据包：客户端请求对 Trinket 饰品槽执行交互操作。
 * <p>
 * 由 YZUI 创造/生存屏幕的指示器点击触发，服务端在收到后通过 Trinkets API
 * 修改服务端权威数据，再由 Trinkets 网络层同步回客户端并持久化。
 * </p>
 *
 * @param groupKey  库存键，格式如 {@code "chest/elytra"}
 * @param slotIndex 槽位索引
 * @param action    操作类型：0=放入（光标→槽），1=取出（槽→光标），2=交换
 */
public record TrinketInteractPayload(String groupKey, int slotIndex, byte action) implements CustomPacketPayload {

    /** 放入：光标物品 → 饰品槽，清空光标 */
    public static final byte ACTION_PLACE = 0;
    /** 取出：饰品槽物品 → 光标，清空饰品槽 */
    public static final byte ACTION_TAKE = 1;
    /** 交换：光标与饰品槽互换 */
    public static final byte ACTION_SWAP = 2;

    public static final @NonNull Identifier IDENTIFIER = Identifier.fromNamespaceAndPath(
            YouzaiworldCore.MOD_ID, "trinket_interact");

    public static final @NonNull Type<TrinketInteractPayload> ID = new Type<>(IDENTIFIER);

    public static final StreamCodec<RegistryFriendlyByteBuf, TrinketInteractPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, p) -> {
                        buf.writeUtf(p.groupKey());
                        buf.writeVarInt(p.slotIndex());
                        buf.writeByte(p.action());
                    },
                    buf -> new TrinketInteractPayload(
                            buf.readUtf(),
                            buf.readVarInt(),
                            buf.readByte()
                    )
            );

    @Override
    public @NonNull Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
