package top.csituka.youzaiworldcore.mixin.jukebox;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.JukeboxBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import top.csituka.youzaiworldcore.config.EventSettings;
import top.csituka.youzaiworldcore.util.DebugLogger;

/**
 * 唱片机循环播放 Mixin。
 * <p>
 * 在唱片机 Tick 完成后，若歌曲已结束但仍有唱片，且附近有玩家，
 * 自动重新开始播放，实现循环播放效果。
 * </p>
 */
@SuppressWarnings("null")
@Mixin(JukeboxBlockEntity.class)
public class JukeboxLoopMixin {

    private static final double PLAYER_RADIUS = 32.0;
    private static final double PLAYER_RADIUS_SQ = PLAYER_RADIUS * PLAYER_RADIUS;

    @Inject(method = "tick", at = @At("TAIL"))
    private static void onTickEnd(Level level, BlockPos pos, BlockState state,
            JukeboxBlockEntity jukebox, CallbackInfo ci) {
        if (level.isClientSide()) {
            return;
        }
        if (!EventSettings.isJukeboxLoopEnabled()) return;

        var songPlayer = jukebox.getSongPlayer();
        if (songPlayer.isPlaying()) {
            return;
        }
        if (jukebox.getTheItem().isEmpty()) {
            return;
        }

        // 检测附近是否有玩家（仅遍历本维度玩家，避免跨维度全服扫描）
        ServerLevel serverLevel = (ServerLevel) level;
        boolean playerNearby = false;
        for (ServerPlayer player : serverLevel.players()) {
            if (player.distanceToSqr(pos.getX(), pos.getY(), pos.getZ()) <= PLAYER_RADIUS_SQ) {
                playerNearby = true;
                break;
            }
        }

        if (!playerNearby) {
            return;
        }

        // 重新播放
        jukebox.tryForcePlaySong();
        DebugLogger.debug("JukeboxLoop", "唱片机循环播放: {}", pos);
    }
}
