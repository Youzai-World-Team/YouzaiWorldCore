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
 *   <li>服务端近似检测：位置 / 视角变化（{@code SERVER} 模式用于进入判定，
 *       {@code BOTH} 模式在客户端通道失效时兜底，AFK 后的真实移动始终可恢复状态）；</li>
 *   <li><b>进入判定</b>：非 AFK 时按有效活动时间（检测模式语义见
 *       {@link AfkManager#getEffectiveActivityTick}）超阈值进入；</li>
 *   <li><b>退出判定</b>（统一规则，手动/自动一致）：
 *       <ul>
 *         <li>客户端心跳报告真实输入 → 退出（手动切换命令自身的旧心跳会被基线忽略）；</li>
 *         <li>服务端位置 / 视角变化 → 退出，走动立即恢复；</li>
 *         <li>客户端发送聊天 / 执行指令 → 退出（切换命令自身的事件会被基线忽略）；</li>
 *       </ul>
 *   </li>
 *   <li>超时踢出：AFK 持续超过 {@code auto_kick_seconds} 断开连接（可配置）。</li>
 * </ol>
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
                || mode == AfkConfig.DetectMode.BOTH
                // 手动 AFK 必须支持原版客户端通过走动恢复，即使自动检测模式为 CLIENT。
                || (data.isAfk && data.manualAfk));

        // ===== 服务端近似检测：位置 / 视角变化（仅作客户端失效时的活动依据）=====
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
                    DebugLogger.trace(MODULE, "%s 检测到位置/视角变化 → 服务端活动", name);
                }
                data.lastX = x;
                data.lastY = y;
                data.lastZ = z;
                data.lastYRot = yRot;
                data.lastXRot = xRot;
            }
        }

        boolean clientAlive = data.lastHeartbeatTick >= 0
                && now - data.lastHeartbeatTick <= AfkManager.getHeartbeatTimeoutTicks();
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

        // ===== 退出判定（统一规则，手动/自动一致）=====
        boolean activityDetected = false;
        String source = null;
        // 1) 客户端真实输入（覆盖移动/跳跃/丢弃/容器/合成/攻击/点击等）
        boolean clientActivityAfterManualEntry = !data.manualAfk
                || data.clientLastActivityTick > data.manualClientActivityBaselineTick;
        if (clientAlive
                && data.clientLastActivityTick >= now - CLIENT_ACTIVITY_WINDOW_TICKS
                && clientActivityAfterManualEntry) {
            activityDetected = true;
            source = "客户端输入";
        }
        // 2) 服务端位置/视角变化：AFK 后发生的真实移动立即恢复
        if (serverDetect && data.serverLastActivityTick > data.afkSinceTick) {
            activityDetected = true;
            source = "服务端移动";
        }
        // 3) 发送聊天 / 执行指令（忽略 /yzwc afk 命令自身）
        boolean chatActivityAfterManualEntry = !data.manualAfk
                || data.chatLastActivityTick > data.manualChatActivityBaselineTick;
        if (data.chatLastActivityTick >= now - CLIENT_ACTIVITY_WINDOW_TICKS
                && chatActivityAfterManualEntry) {
            activityDetected = true;
            source = "聊天/指令";
        }
        if (activityDetected) {
            DebugLogger.info(MODULE, "%s 检测到活动（%s）→ 退出 AFK", name, source);
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
