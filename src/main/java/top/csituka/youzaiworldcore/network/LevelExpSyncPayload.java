package top.csituka.youzaiworldcore.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NonNull;
import top.csituka.youzaiworldcore.YouzaiworldCore;

/**
 * 服务端 → 客户端：冒险经验同步数据包。
 * 当玩家获得冒险经验时，服务端发送此包以驱动客户端 HUD 显示。
 *
 * @param level       当前冒险等级
 * @param currentExp  当前等级内的经验进度
 * @param neededExp   升至下一级所需总经验
 * @param gainedExp   本次获得的经验值（用于动画）
 * @param leveledUp   是否在本经验包中升级
 */
@SuppressWarnings("null")
public record LevelExpSyncPayload(
        int level,
        int currentExp,
        int neededExp,
        int gainedExp,
        boolean leveledUp
) implements CustomPacketPayload {

    public static final Identifier LEVEL_EXP_SYNC_ID =
            Identifier.fromNamespaceAndPath(YouzaiworldCore.MOD_ID, "level_exp_sync");
    public static final CustomPacketPayload.Type<LevelExpSyncPayload> ID =
            new CustomPacketPayload.Type<>(LEVEL_EXP_SYNC_ID);

    public static final StreamCodec<RegistryFriendlyByteBuf, LevelExpSyncPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeVarInt(payload.level);
                buf.writeVarInt(payload.currentExp);
                buf.writeVarInt(payload.neededExp);
                buf.writeVarInt(payload.gainedExp);
                buf.writeBoolean(payload.leveledUp);
            },
            buf -> new LevelExpSyncPayload(
                    buf.readVarInt(),
                    buf.readVarInt(),
                    buf.readVarInt(),
                    buf.readVarInt(),
                    buf.readBoolean()
            )
    );

    @Override
    @NonNull
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
