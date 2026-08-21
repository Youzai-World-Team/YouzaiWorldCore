package top.csituka.youzaiworldcore.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import top.csituka.youzaiworldcore.YouzaiworldCore;

/**
 * S2C 数据包：服务端通知客户端打开无线红石元件的频道设置界面。
 * <p>
 * 只有服务端确认「方块实体存在、玩家有建造权限」之后才会下发，
 * 同时在方块实体上登记该玩家为唯一有权提交频道者。
 *
 * @param pos            目标元件的世界坐标
 * @param currentChannel 元件当前频道，用于把编辑框预填成现有值（再次右键即为修改）
 * @param transmitter    true 表示发射器、false 表示接收器，仅用于界面标题的措辞
 */
@SuppressWarnings("null")
public record WirelessRedstoneOpenChannelPayload(BlockPos pos, int currentChannel, boolean transmitter)
        implements CustomPacketPayload {

    public static final Identifier ID = Identifier.fromNamespaceAndPath(
            YouzaiworldCore.MOD_ID, "wireless_redstone_open_channel");

    public static final Type<WirelessRedstoneOpenChannelPayload> TYPE = new Type<>(ID);

    public static final StreamCodec<RegistryFriendlyByteBuf, WirelessRedstoneOpenChannelPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, payload) -> {
                        buf.writeBlockPos(payload.pos);
                        buf.writeVarInt(payload.currentChannel);
                        buf.writeBoolean(payload.transmitter);
                    },
                    buf -> {
                        BlockPos pos = buf.readBlockPos();
                        int currentChannel = buf.readVarInt();
                        boolean transmitter = buf.readBoolean();
                        return new WirelessRedstoneOpenChannelPayload(pos, currentChannel, transmitter);
                    }
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
