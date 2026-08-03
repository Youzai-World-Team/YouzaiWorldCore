package top.csituka.youzaiworldcore.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import top.csituka.youzaiworldcore.YouzaiworldCore;

import java.util.ArrayList;
import java.util.List;

/**
 * S2C 数据包：返回本项目账户系统中已注册的全部玩家代号（username）。
 * <p>供发布页「选取玩家」弹窗列表使用，含离线玩家。</p>
 *
 * @param playerNames 已注册玩家代号列表（按字典序）
 */
public record MailPlayerListPayload(List<String> playerNames) implements CustomPacketPayload {

    public static final Identifier IDENTIFIER = Identifier.fromNamespaceAndPath(
            YouzaiworldCore.MOD_ID, "mail_player_list");

    @SuppressWarnings("null")
    public static final Type<MailPlayerListPayload> ID = new Type<>(IDENTIFIER);

    @SuppressWarnings("null")
    public static final StreamCodec<RegistryFriendlyByteBuf, MailPlayerListPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, p) -> {
                        buf.writeVarInt(p.playerNames().size());
                        for (String name : p.playerNames()) {
                            buf.writeUtf(name);
                        }
                    },
                    buf -> {
                        int size = buf.readVarInt();
                        List<String> names = new ArrayList<>(size);
                        for (int i = 0; i < size; i++) {
                            names.add(buf.readUtf());
                        }
                        return new MailPlayerListPayload(names);
                    }
            );

    @Override
    @SuppressWarnings("null")
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
