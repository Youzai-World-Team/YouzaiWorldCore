package top.csituka.youzaiworldcore.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.NonNull;
import top.csituka.youzaiworldcore.YouzaiworldCore;

/**
 * C2S 数据包：客户端请求对 Trinket 饰品槽执行交互操作。
 * <p>
 * 由 YZUI 创造/生存屏幕的指示器点击触发，服务端在收到后通过 Trinkets API
 * 修改服务端权威数据，再由 Trinkets 网络层同步回客户端并持久化。
 * </p>
 * <p>
 * {@code cursor} 字段携带客户端当前鼠标携带的物品（可能为 EMPTY）。创造模式光标是
 * 客户端生成的虚拟物品，因此服务端使用该字段；生存模式则只信任服务端
 * {@code containerMenu.getCarried()}，避免客户端伪造物品。饰品槽状态与校验始终由服务端权威决定。
 * </p>
 *
 * @param groupKey  库存键，格式如 {@code "chest/elytra"}
 * @param slotIndex 槽位索引
 * @param action    操作类型：0=放入（光标→槽），1=取出（槽→光标），2=交换，3=快捷移动（槽→物品栏）
 * @param cursor    客户端鼠标当前携带的物品（EMPTY 表示空手）
 */
@SuppressWarnings("null")
public record TrinketInteractPayload(String groupKey, int slotIndex, byte action,
                                     ItemStack cursor) implements CustomPacketPayload {

    /** 放入：按饰品槽堆叠上限从光标移入，剩余物品保留在光标 */
    public static final byte ACTION_PLACE = 0;
    /** 取出：饰品槽物品 → 光标，清空饰品槽 */
    public static final byte ACTION_TAKE = 1;
    /** 交换：光标与饰品槽互换 */
    public static final byte ACTION_SWAP = 2;
    /** 快捷移动：饰品槽物品 → 主物品栏/快捷栏（服务端转移到玩家背包 0-35） */
    public static final byte ACTION_QUICK_MOVE = 3;

    /**
     * 便捷构造器：不携带 cursor（服务端回退到 {@code containerMenu.getCarried()}）。
     * 用于不需要 cursor 兜底的调用点（如创造屏右键取半的补充同步）。
     */
    public TrinketInteractPayload(String groupKey, int slotIndex, byte action) {
        this(groupKey, slotIndex, action, ItemStack.EMPTY);
    }

    public static final @NonNull Identifier IDENTIFIER = Identifier.fromNamespaceAndPath(
            YouzaiworldCore.MOD_ID, "trinket_interact");

    public static final @NonNull Type<TrinketInteractPayload> ID = new Type<>(IDENTIFIER);

    public static final StreamCodec<RegistryFriendlyByteBuf, TrinketInteractPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, p) -> {
                        buf.writeUtf(p.groupKey());
                        buf.writeVarInt(p.slotIndex());
                        buf.writeByte(p.action());
                        ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, p.cursor());
                    },
                    buf -> new TrinketInteractPayload(
                            buf.readUtf(),
                            buf.readVarInt(),
                            buf.readByte(),
                            ItemStack.OPTIONAL_STREAM_CODEC.decode(buf)
                    )
            );

    @Override
    public @NonNull Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
