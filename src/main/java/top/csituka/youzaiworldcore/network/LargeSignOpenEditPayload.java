package top.csituka.youzaiworldcore.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import top.csituka.youzaiworldcore.YouzaiworldCore;

/**
 * S2C 数据包：服务端通知客户端打开大字牌编辑界面。
 * <p>
 * 只有服务端确认「方块实体存在、未涂蜡、玩家有建造权限」之后才会下发，
 * 同时在方块实体上登记该玩家为唯一有权提交文本者。
 *
 * @param pos         目标大字牌的世界坐标
 * @param currentText 字牌当前文本，用于把编辑框预填成现有内容（再次右键即为修改）
 */
@SuppressWarnings("null")
public record LargeSignOpenEditPayload(BlockPos pos, String currentText) implements CustomPacketPayload {

    public static final Identifier ID = Identifier.fromNamespaceAndPath(
            YouzaiworldCore.MOD_ID, "large_sign_open_edit");

    public static final Type<LargeSignOpenEditPayload> TYPE = new Type<>(ID);

    public static final StreamCodec<RegistryFriendlyByteBuf, LargeSignOpenEditPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, payload) -> {
                        buf.writeBlockPos(payload.pos);
                        buf.writeUtf(payload.currentText, LargeSignSetTextPayload.MAX_ENCODED_LENGTH);
                    },
                    buf -> {
                        BlockPos pos = buf.readBlockPos();
                        String currentText = buf.readUtf(LargeSignSetTextPayload.MAX_ENCODED_LENGTH);
                        return new LargeSignOpenEditPayload(pos, currentText);
                    }
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
