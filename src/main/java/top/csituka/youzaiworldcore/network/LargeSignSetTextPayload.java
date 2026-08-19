package top.csituka.youzaiworldcore.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import top.csituka.youzaiworldcore.YouzaiworldCore;

/**
 * C2S 数据包：客户端提交大字牌的新文本。
 * <p>
 * 服务端在 {@link ModNetworking} 的接收器里复核全部条件后才写入：
 * 方块仍是大字牌、未涂蜡、该玩家是当前被授权的编辑者、距离不超过 8 格、
 * 且文本通过 {@link top.csituka.youzaiworldcore.util.LargeSignTextRules#isValid(String)}。
 *
 * @param pos  目标大字牌的世界坐标
 * @param text 新文本（1 个全角或 2 个半角字符；空串表示清空字牌）
 */
@SuppressWarnings("null")
public record LargeSignSetTextPayload(BlockPos pos, String text) implements CustomPacketPayload {

    /**
     * 网络层允许的最大编码长度。
     * <p>
     * 业务上限只有 2 个宽度单位（最多 4 个码点），这里给出宽松的字节上限，
     * 用于在解码阶段就挡掉超长报文；真正的规则校验在接收器里做。
     */
    public static final int MAX_ENCODED_LENGTH = 64;

    public static final Identifier ID = Identifier.fromNamespaceAndPath(
            YouzaiworldCore.MOD_ID, "large_sign_set_text");

    public static final Type<LargeSignSetTextPayload> TYPE = new Type<>(ID);

    public static final StreamCodec<RegistryFriendlyByteBuf, LargeSignSetTextPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, payload) -> {
                        buf.writeBlockPos(payload.pos);
                        buf.writeUtf(payload.text, MAX_ENCODED_LENGTH);
                    },
                    buf -> {
                        BlockPos pos = buf.readBlockPos();
                        String text = buf.readUtf(MAX_ENCODED_LENGTH);
                        return new LargeSignSetTextPayload(pos, text);
                    }
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
