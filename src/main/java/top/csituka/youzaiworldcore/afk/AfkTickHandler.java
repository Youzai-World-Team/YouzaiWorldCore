package top.csituka.youzaiworldcore.afk;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import top.csituka.youzaiworldcore.config.AfkConfig;
import top.csituka.youzaiworldcore.util.DebugLogger;

/** 每 20 tick 采样和判定一次；进入与恢复采用同一检测通道。 */
@SuppressWarnings("null")
public final class AfkTickHandler {
    private static final String MODULE = "AfkTickHandler";
    private static final int CHECK_INTERVAL = 20;
    private static final double MOVE_DIST_SQ_THRESHOLD = 0.05 * 0.05;
    private static final float ROTATION_DELTA_THRESHOLD = 0.5F;
    private static int tickCounter;
    private static boolean registered;

    private AfkTickHandler() {
    }

    /** 幂等注册服务端检测。 */
    public static void register() {
        if (!registered) {
            ServerTickEvents.END_SERVER_TICK.register(AfkTickHandler::serverTick);
            registered = true;
            DebugLogger.info(MODULE, "AFK 检测已注册，间隔 %d tick", CHECK_INTERVAL);
        }
    }

    private static void serverTick(MinecraftServer server) {
        if (++tickCounter < CHECK_INTERVAL) {
            return;
        }
        tickCounter = 0;
        if (!AfkConfig.isEnabled()) {
            AfkManager.disableAll(server);
            return;
        }
        // 踢人会改变在线列表，使用快照迭代。
        for (ServerPlayer player : java.util.List.copyOf(server.getPlayerList().getPlayers())) {
            if (!player.hasDisconnected()) {
                checkPlayer(player, server.getTickCount());
            }
        }
    }

    private static void checkPlayer(ServerPlayer player, long now) {
        AfkManager.AfkPlayerData data = AfkManager.getOrCreate(player);
        double dx = player.getX() - data.lastX;
        double dy = player.getY() - data.lastY;
        double dz = player.getZ() - data.lastZ;
        float yaw = Math.abs(Mth.wrapDegrees(player.getYRot() - data.lastYRot));
        float pitch = Math.abs(player.getXRot() - data.lastXRot);
        if (dx * dx + dy * dy + dz * dz > MOVE_DIST_SQ_THRESHOLD
                || yaw > ROTATION_DELTA_THRESHOLD || pitch > ROTATION_DELTA_THRESHOLD) {
            data.activity.serverActivity(now);
            // 小幅移动累计到阈值再更新基准，避免慢速移动被完全漏掉。
            data.samplePosition(player);
        }

        var mode = AfkConfig.getDetectMode();
        if (data.activity.shouldExit(mode, now)) {
            DebugLogger.info(MODULE, "%s 出现新的有效活动，退出 AFK", player.getName().getString());
            AfkManager.exitAfk(player);
        } else if (data.activity.shouldEnter(mode, now, AfkConfig.getThresholdSeconds() * 20L)) {
            AfkManager.enterAfk(player, false);
        } else if (data.activity.shouldKick(now, AfkConfig.getAutoKickSeconds() * 20L)) {
            DebugLogger.info(MODULE, "%s AFK 超时，断开连接", player.getName().getString());
            player.connection.disconnect(Component.translatable("youzaiworldcore.message.afk.kicked"));
        }
    }
}
