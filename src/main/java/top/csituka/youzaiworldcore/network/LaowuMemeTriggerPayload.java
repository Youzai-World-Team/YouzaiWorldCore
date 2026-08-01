package top.csituka.youzaiworldcore.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import top.csituka.youzaiworldcore.YouzaiworldCore;

/**
 * S2C 数据包：通知某两只猫进入「老吴贴贴」锁定（对头）状态。
 * <p>
 * 携带：两只猫的 entity id、服务端选中的内置音频 id（0/1/2）、歪头方向（±1）。
 * {@code soundId} 由服务端随机选出并经此包下发，客户端<b>必须按它播放对应曲目</b>，
 * 实现「服务端选曲、全体玩家同听」——与旧版「客户端各自随机」不同。
 * </p>
 *
 * @param catAId  老吴猫的 entity id
 * @param catBId  配对邻猫的 entity id
 * @param soundId 服务端选中的内置曲目索引（0=laowu2, 1=qiliang, 2=zhanhou）
 * @param rollSign 歪头方向（±1，镜像）
 */
@SuppressWarnings("null")
public record LaowuMemeTriggerPayload(int catAId, int catBId, int soundId, int rollSign)
        implements CustomPacketPayload {

    public static final Identifier IDENTIFIER = Identifier.fromNamespaceAndPath(
            YouzaiworldCore.MOD_ID, "laowu_meme_trigger");

    public static final Type<LaowuMemeTriggerPayload> ID = new Type<>(IDENTIFIER);

    public static final StreamCodec<RegistryFriendlyByteBuf, LaowuMemeTriggerPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.INT, LaowuMemeTriggerPayload::catAId,
                    ByteBufCodecs.INT, LaowuMemeTriggerPayload::catBId,
                    ByteBufCodecs.INT, LaowuMemeTriggerPayload::soundId,
                    ByteBufCodecs.INT, LaowuMemeTriggerPayload::rollSign,
                    LaowuMemeTriggerPayload::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
