package top.csituka.youzaiworldcore.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import top.csituka.youzaiworldcore.YouzaiworldCore;

/**
 * S2C 数据包：通知某两只猫退出「老吴贴贴」状态（右键释放 / 猫消失 / 事件被禁用）。
 * 客户端收到后清除渲染状态并停止循环音频。
 *
 * @param catAId 老吴猫的 entity id
 * @param catBId 配对邻猫的 entity id
 */
@SuppressWarnings("null")
public record LaowuMemeStopPayload(int catAId, int catBId) implements CustomPacketPayload {

    public static final Identifier IDENTIFIER = Identifier.fromNamespaceAndPath(
            YouzaiworldCore.MOD_ID, "laowu_meme_stop");

    public static final Type<LaowuMemeStopPayload> ID = new Type<>(IDENTIFIER);

    public static final StreamCodec<RegistryFriendlyByteBuf, LaowuMemeStopPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.INT, LaowuMemeStopPayload::catAId,
                    ByteBufCodecs.INT, LaowuMemeStopPayload::catBId,
                    LaowuMemeStopPayload::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
