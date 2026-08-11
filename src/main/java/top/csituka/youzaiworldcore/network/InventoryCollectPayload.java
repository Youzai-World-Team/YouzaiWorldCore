package top.csituka.youzaiworldcore.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NonNull;
import top.csituka.youzaiworldcore.YouzaiworldCore;

/**
 * C2S 数据包：把指定槽位中的同类物品收集到生存物品栏的鼠标携带物中。
 * <p>
 * 仅用于 YZUI 生存物品栏的 Mouse Tweaks 左键拖拽。服务端会校验当前菜单、
 * 槽位可取性、物品类型与光标剩余容量，客户端不能通过该请求生成或替换物品。
 *
 * @param containerId 当前玩家物品栏菜单 ID
 * @param slotIndex   要收集物品的菜单槽位索引
 */
@SuppressWarnings("null")
public record InventoryCollectPayload(int containerId, int slotIndex) implements CustomPacketPayload {

    public static final @NonNull Identifier IDENTIFIER = Identifier.fromNamespaceAndPath(
            YouzaiworldCore.MOD_ID, "inventory_collect");

    public static final @NonNull Type<InventoryCollectPayload> ID = new Type<>(IDENTIFIER);

    public static final StreamCodec<RegistryFriendlyByteBuf, InventoryCollectPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, payload) -> {
                        buf.writeVarInt(payload.containerId());
                        buf.writeVarInt(payload.slotIndex());
                    },
                    buf -> new InventoryCollectPayload(buf.readVarInt(), buf.readVarInt()));

    @Override
    public @NonNull Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
