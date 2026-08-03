package top.csituka.youzaiworldcore.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import top.csituka.youzaiworldcore.YouzaiworldCore;

/**
 * S2C 数据包：通知客户端玩家的传送石蓄力被服务端打断（当前只有受到伤害这一种情况）。
 * <p>
 * 服务端 {@code stopUsingItem} 只会清掉服务端的使用状态，客户端仍会保持举手蓄力的表现，
 * 因此需要这个数据包让客户端同步停止使用物品。数据包本身不带任何字段。
 */
@SuppressWarnings("null")
public record TeleportStoneInterruptPayload() implements CustomPacketPayload {

    public static final Identifier IDENTIFIER = Identifier.fromNamespaceAndPath(
            YouzaiworldCore.MOD_ID, "teleport_stone_interrupt");

    public static final Type<TeleportStoneInterruptPayload> TYPE = new Type<>(IDENTIFIER);

    public static final StreamCodec<RegistryFriendlyByteBuf, TeleportStoneInterruptPayload> STREAM_CODEC =
            StreamCodec.unit(new TeleportStoneInterruptPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
