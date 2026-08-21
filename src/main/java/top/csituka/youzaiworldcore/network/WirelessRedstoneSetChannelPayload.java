package top.csituka.youzaiworldcore.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import top.csituka.youzaiworldcore.YouzaiworldCore;

/**
 * C2S 数据包：客户端提交无线红石元件的新频道。
 * <p>
 * 服务端在 {@link ModNetworking} 的接收器里复核全部条件后才写入：
 * 目标仍是无线红石元件、该玩家是当前被授权的设置者、距离不超过 8 格、
 * 且频道号通过
 * {@link top.csituka.youzaiworldcore.redstone.WirelessRedstoneChannel#isValid(int)}。
 *
 * @param pos     目标元件的世界坐标
 * @param channel 新频道号（{@code 0 ~ 9999}）
 */
@SuppressWarnings("null")
public record WirelessRedstoneSetChannelPayload(BlockPos pos, int channel) implements CustomPacketPayload {

    public static final Identifier ID = Identifier.fromNamespaceAndPath(
            YouzaiworldCore.MOD_ID, "wireless_redstone_set_channel");

    public static final Type<WirelessRedstoneSetChannelPayload> TYPE = new Type<>(ID);

    public static final StreamCodec<RegistryFriendlyByteBuf, WirelessRedstoneSetChannelPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, payload) -> {
                        buf.writeBlockPos(payload.pos);
                        buf.writeVarInt(payload.channel);
                    },
                    buf -> {
                        BlockPos pos = buf.readBlockPos();
                        int channel = buf.readVarInt();
                        return new WirelessRedstoneSetChannelPayload(pos, channel);
                    }
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
