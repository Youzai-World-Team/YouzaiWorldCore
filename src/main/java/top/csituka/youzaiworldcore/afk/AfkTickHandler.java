package top.csituka.youzaiworldcore.afk;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import top.csituka.youzaiworldcore.config.AfkConfig;
import top.csituka.youzaiworldcore.util.DebugLogger;

import java.util.List;

/**
 * AFK 检测与状态切换的 Tick 处理器（服务端权威）。
 * <p>
 * 每 20 tick（约 1 秒）节流执行一次：
 * <ol>
 *   <li>功能禁用时全局清理全部 AFK 状态；</li>
 *   <li>服务端近似检测：位置 / 视角变化视为活动（仅在
 *       {@code SERVER} / {@code BOTH} 模式生效，兜底原版客户端）；</li>
 *   <li><b>进入判定</b>：非 AFK 时按有效活动时间（检测模式取 max）超阈值进入；</li>
 *   <li><b>退出判定</b>（统一规则，手动/自动一致）：客户端心跳报告 1 秒内有
 *       真实输入，或服务端近似检测到移动 → 立即退出；</li>
 *   <li>超时踢出：AFK 持续超过 {@code auto_kick_seconds} 断开连接（可配置）。</li>
 * </ol>
 * 客户端精确检测通道（心跳）由 {@link AfkManager#onHeartbeat} 维护。
 * </p>
 */
@SuppressWarnings("null")
public final class AfkTickHandler {

    private static final String MODULE = "AfkTickHandler";

    /** 检测节流：每 N tick 扫描一次（20 tick = 1 秒） */
    private static final int CHECK_INTERVAL = 20;

    /** 服务端近似检测：位移平方阈值（0.05 格） */
    private static final double MOVE_DIST_SQ_THRESHOLD = 0.05 * 0.05;
    /** 服务端近似检测：视角变化阈值（度） */
    private static final double ROTATION_DELTA_THRESHOLD = 0.5;

    /** 退出判定：客户端心跳新鲜度上限（tick，约 2 个心跳周期） */
    private static final long CLIENT_ACTIVITY_WINDOW_TICKS = 40;

    private static int tickCounter = 0;
    private static boolean registered = false;

    private AfkTickHandler() {
    }

    /** 注册服务端 tick 事件（幂等，由 {@code YouzaiworldCore.onInitialize} 调用） */
    public static void register() {
        if (registered) {
            return;
        }
        DebugLogger.entering(MODULE, "register");
        ServerTickEvents.END_SERVER_TICK.register(AfkTickHandler::serverTick);
        registered = true;
        DebugLogger.info(MODULE, "AFK 检测已注册（间隔 %d tick）", CHECK_INTERVAL);
        DebugLogger.exiting(MODULE, "register");
    }

    private static void serverTick(MinecraftServer server) {
        tickCounter++;
        if (tickCounter < CHECK_INTERVAL) {
            return;
        }
        tickCounter = 0;
        long now = server.getTickCount();

        if (!AfkConfig.isEnabled()) {
            AfkManager.disableAll(server);
            return;
        }

        List<ServerPlayer> players = server.getPlayerList().getPlayers();
        for (ServerPlayer player : players) {
            checkPlayer(server, player, now);
        }
    }

    private static void checkPlayer(MinecraftServer server, ServerPlayer player, long now) {
        AfkManager.AfkPlayerData data = AfkManager.getOrCreate(player.getUUID());
        String name = player.getName().getString();

        AfkConfig.DetectMode mode = AfkConfig.getDetectMode();
        boolean serverDetect = (mode == AfkConfig.DetectMode.SERVER
                || mode == AfkConfig.DetectMode.BOTH);

        // ===== 服务端近似检测：位置 / 视角变化 =====
        if (serverDetect) {
            double x = player.getX();
            double y = player.getY();
            double z = player.getZ();
            float yRot = player.getYRot();
            float xRot = player.getXRot();
            if (!data.posInitialized) {
                data.posInitialized = true;
                data.lastX = x;
                data.lastY = y;
                data.lastZ = z;
                data.lastYRot = yRot;
                data.lastXRot = xRot;
            } else {
                double dx = x - data.lastX;
                double dy = y - data.lastY;
                double dz = z - data.lastZ;
                double dyRot = Math.abs(yRot - data.lastYRot);
                double dxRot = Math.abs(xRot - data.lastXRot);
                if (dx * dx + dy * dy + dz * dz > MOVE_DIST_SQ_THRESHOLD
                        || dyRot > ROTATION_DELTA_THRESHOLD
                        || dxRot > ROTATION_DELTA_THRESHOLD) {
                    AfkManager.onServerActivity(player, now);
                    DebugLogger.trace(MODULE, "%s 检测到位置/视角变化 → 活动", name);
                }
                data.lastX = x;
                data.lastY = y;
                data.lastZ = z;
                data.lastYRot = yRot;
                data.lastXRot = xRot;
            }
        }

        // ===== 进入判定（仅自动；手动由 /yzwc afk 命令直接调用 enterAfk）=====
        if (!data.isAfk) {
            long effective = AfkManager.getEffectiveActivityTick(data, now);
            if (effective != Long.MAX_VALUE) {
                long thresholdTicks = (long) AfkConfig.getThresholdSeconds() * 20L;
                if (now - effective >= thresholdTicks) {
                    DebugLogger.info(MODULE, "%s 无活动 %d tick（阈值 %d）→ 进入 AFK",
                            name, now - effective, thresholdTicks);
                    AfkManager.enterAfk(player, false);
                }
            }
            return;
        }

        // ===== 退出判定（统一规则：检测到活动即退出，手动/自动一致）=====
        boolean activityDetected = false;
        if (data.lastHeartbeatTick >= 0
                && now - data.lastHeartbeatTick <= CLIENT_ACTIVITY_WINDOW_TICKS
                && data.clientLastActivityTick >= now - CLIENT_ACTIVITY_WINDOW_TICKS) {
            activityDetected = true;
        }
        if (serverDetect && data.serverLastActivityTick >= now) {
            activityDetected = true;
        }
        if (activityDetected) {
            DebugLogger.info(MODULE, "%s 检测到活动 → 退出 AFK", name);
            AfkManager.exitAfk(player);
        }

        // ===== 超时踢出 =====
        if (data.isAfk && AfkConfig.getAutoKickSeconds() > 0) {
            long autoKickTicks = (long) AfkConfig.getAutoKickSeconds() * 20L;
            if (now - data.afkSinceTick >= autoKickTicks) {
                DebugLogger.info(MODULE, "%s AFK 超时 %d tick（阈值 %d）→ 断开连接",
                        name, now - data.afkSinceTick, autoKickTicks);
                player.connection.disconnect(Component.translatable(
                        "youzaiworldcore.message.afk.kicked"));
            }
        }
    }
}
