package top.csituka.youzaiworldcore.afk;

import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import top.csituka.youzaiworldcore.config.AfkConfig;
import top.csituka.youzaiworldcore.util.DebugLogger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * AFK（挂机）状态管理器 — 服务端权威。
 * <p>
 * 每个在线玩家对应一条 {@link AfkPlayerData}，记录三类活动时间：
 * <ul>
 *   <li>{@code clientLastActivityTick} — 客户端心跳上报的真实输入时间
 *       （客户端 mixin 键盘/鼠标后按「距最后输入的 tick 差值」上报，无时钟同步问题）；</li>
 *   <li>{@code serverLastActivityTick} — 服务端近似检测（位置/视角变化），
 *       兜底原版客户端；</li>
 *   <li>{@code lastHeartbeatTick} — 最近一次心跳到达时间，用于判断客户端通道是否失效。</li>
 * </ul>
 * 有效活动时间 = 按 {@link AfkConfig.DetectMode} 取 max(...)，由
 * {@link AfkTickHandler} 每 20 tick 判定进入/退出 AFK。
 * <p>
 * AFK 表现：Tab 前缀（mixin {@code ServerPlayer.getTabListDisplayName()}）、
 * 进入/退出广播、可选无敌（无限抗性提升 V）、可选超时踢出，全部由配置控制。
 * </p>
 */
@SuppressWarnings("null")
public final class AfkManager {

    private static final String MODULE = "AfkManager";

    /** 客户端心跳超时（tick）：超过 5 秒未收到心跳视为客户端通道失效（原版客户端） */
    private static final int HEARTBEAT_TIMEOUT_TICKS = 100;

    /**
     * 手动 AFK 宽限期（tick）：进入后的 4 秒内忽略「客户端输入」与「聊天/指令」活动。
     * 覆盖两个场景：
     * <ol>
     *   <li>{@code /yzwc afk} 命令输入本身是输入事件，进入后 ~3 秒内客户端心跳仍报告
     *       「刚有输入」，若不忽略会立即触发自动退出（「切了就切回来」bug）；</li>
     *   <li>命令执行触发的 {@code CHAT_MESSAGE} 服务端活动事件。</li>
     * </ol>
     * 位置/视角变化<b>不受</b>宽限期影响（手动 AFK 后立刻走动仍会恢复）。
     */
    private static final int MANUAL_GRACE_TICKS = 80;

    /** 玩家 AFK 数据表（会话级，掉线即清理） */
    private static final Map<UUID, AfkPlayerData> DATA = new ConcurrentHashMap<>();

    private AfkManager() {
    }

    /** @return 客户端心跳超时（tick）：超过该值视为客户端通道失效（原版客户端） */
    public static int getHeartbeatTimeoutTicks() {
        return HEARTBEAT_TIMEOUT_TICKS;
    }

    /**
     * 单个玩家的 AFK 运行数据（可变，非线程安全——全部在服务端主线程访问）。
     */
    public static final class AfkPlayerData {
        /** 客户端心跳上报的最后输入对应的服务端 tick，-1 = 从未收到心跳 */
        long clientLastActivityTick = -1;
        /** 最近一次心跳到达的服务端 tick，-1 = 从未收到心跳 */
        long lastHeartbeatTick = -1;
        /** 服务端近似检测（位置/视角变化）的最后活动 tick */
        long serverLastActivityTick;
        /** 服务端聊天/指令事件（ServerMessageEvents.CHAT_MESSAGE）的最后活动 tick，-1 = 从未 */
        long chatLastActivityTick = -1;
        /** 手动 AFK 宽限期截止 tick：此 tick 之前忽略客户端输入与聊天活动 */
        long activityGraceUntilTick = 0;
        /** 当前是否处于 AFK 状态 */
        boolean isAfk = false;
        /** 进入 AFK 的服务端 tick */
        long afkSinceTick = 0;
        /** 是否由 /yzwc afk 手动标记（不影响自动恢复逻辑，仅用于状态查询展示） */
        boolean manualAfk = false;
        /** 进入 AFK 前是否已拥有抗性提升（用于退出时按原状恢复，避免误删） */
        boolean hadResistanceBefore = false;
        /** 服务端近似检测：上一采样位置/视角 */
        double lastX, lastY, lastZ, lastYRot, lastXRot;
        /** 服务端近似检测：首采样尚未初始化 */
        boolean posInitialized = false;
    }

    // ==================== 查询 ====================

    /** @return 玩家是否处于 AFK 状态 */
    public static boolean isAfk(ServerPlayer player) {
        return isAfk(player.getUUID());
    }

    /** @return 玩家是否处于 AFK 状态 */
    public static boolean isAfk(UUID uuid) {
        AfkPlayerData data = DATA.get(uuid);
        return data != null && data.isAfk;
    }

    /** @return 玩家 AFK 运行数据（不存在则懒创建） */
    public static AfkPlayerData getOrCreate(UUID uuid) {
        return DATA.computeIfAbsent(uuid, k -> new AfkPlayerData());
    }

    /** @return 当前所有 AFK 中玩家的 UUID 集合（只读视图） */
    public static Set<UUID> getAfkPlayers() {
        Set<UUID> result = ConcurrentHashMap.newKeySet();
        DATA.forEach((uuid, data) -> {
            if (data.isAfk) {
                result.add(uuid);
            }
        });
        return Collections.unmodifiableSet(result);
    }

    // ==================== 活动上报（服务端主线程） ====================

    /**
     * 客户端心跳上报：更新客户端通道的最后输入时间。
     *
     * @param player    目标玩家
     * @param serverTick 当前服务端 tick
     * @param idleTicks 客户端自最后一次输入以来经过的 tick 数（差值，无时钟问题）
     */
    public static void onHeartbeat(ServerPlayer player, long serverTick, int idleTicks) {
        AfkPlayerData data = getOrCreate(player.getUUID());
        long clientLast = Math.max(0, serverTick - Math.max(0, idleTicks));
        data.clientLastActivityTick = clientLast;
        data.lastHeartbeatTick = serverTick;
        DebugLogger.trace(MODULE, "onHeartbeat %s: idleTicks=%d, clientLastActivityTick=%d",
                player.getName().getString(), idleTicks, clientLast);
    }

    /**
     * 服务端近似检测到活动（位置/视角变化），更新服务端通道的最后活动时间。
     * <p>
     * 仅当客户端通道失效（原版客户端）时作为活动依据，见
     * {@link #getEffectiveActivityTick}。</p>
     */
    public static void onServerActivity(ServerPlayer player, long serverTick) {
        AfkPlayerData data = getOrCreate(player.getUUID());
        data.serverLastActivityTick = serverTick;
        DebugLogger.trace(MODULE, "onServerActivity %s: serverLastActivityTick=%d",
                player.getName().getString(), serverTick);
    }

    /**
     * 服务端聊天/指令事件（{@code ServerMessageEvents.CHAT_MESSAGE}）上报活动。
     * <p>
     * 「发送聊天消息」「执行指令」属明确语义活动，任何检测模式下都触发退出
     * （手动 AFK 宽限期除外，用于挡住 {@code /yzwc afk} 命令自身）。</p>
     */
    public static void onChatActivity(ServerPlayer player, long serverTick) {
        AfkPlayerData data = getOrCreate(player.getUUID());
        data.chatLastActivityTick = serverTick;
        DebugLogger.trace(MODULE, "onChatActivity %s: chatLastActivityTick=%d",
                player.getName().getString(), serverTick);
    }

    /**
     * 计算玩家当前的有效活动 tick（进入 AFK 判定用）。
     * <p>
     * 各模式语义：
     * <ul>
     *   <li>{@code CLIENT}：仅客户端通道；通道失效 → {@link Long#MAX_VALUE}（永不判定，
     *       原版客户端无精确检测，文档约定）；</li>
     *   <li>{@code SERVER}：仅服务端位置/视角近似检测；</li>
     *   <li>{@code BOTH}：客户端通道存活时<b>以客户端为准</b>（位置检测不覆盖客户端
     *       精确判定——「按住 W 挂机」客户端无重复输入 → 判定 AFK）；
     *       客户端通道失效（原版客户端）时回退服务端近似检测。</li>
     * </ul>
     * </p>
     *
     * @return 有效活动 tick；返回 {@link Long#MAX_VALUE} 表示不可判定
     */
    public static long getEffectiveActivityTick(AfkPlayerData data, long serverTick) {
        AfkConfig.DetectMode mode = AfkConfig.getDetectMode();
        boolean clientAlive = data.lastHeartbeatTick >= 0
                && serverTick - data.lastHeartbeatTick <= HEARTBEAT_TIMEOUT_TICKS;

        switch (mode) {
            case CLIENT -> {
                return clientAlive && data.clientLastActivityTick >= 0
                        ? data.clientLastActivityTick : Long.MAX_VALUE;
            }
            case SERVER -> {
                return data.serverLastActivityTick;
            }
            case BOTH -> {
                if (clientAlive && data.clientLastActivityTick >= 0) {
                    return data.clientLastActivityTick;
                }
                return data.serverLastActivityTick;
            }
            default -> {
                return data.serverLastActivityTick;
            }
        }
    }

    // ==================== 状态切换（由 AfkTickHandler / 命令调用） ====================

    /**
     * 进入 AFK：置状态、广播、可选无敌、刷新 Tab 前缀。
     *
     * @param player 目标玩家
     * @param manual 是否由 /yzwc afk 手动标记
     */
    public static void enterAfk(ServerPlayer player, boolean manual) {
        DebugLogger.entering(MODULE, "enterAfk",
                "player=" + player.getName().getString() + ", manual=" + manual);
        AfkPlayerData data = getOrCreate(player.getUUID());
        if (data.isAfk) {
            DebugLogger.info(MODULE, "玩家 %s 已处于 AFK 状态，跳过重复进入", player.getName().getString());
            DebugLogger.exiting(MODULE, "enterAfk", "already-afk");
            return;
        }
        data.isAfk = true;
        data.manualAfk = manual;
        data.afkSinceTick = player.level().getServer().getTickCount();
        // 手动进入：设宽限期，忽略命令输入自身的残余活动（客户端心跳 + CHAT_MESSAGE）
        data.activityGraceUntilTick = manual
                ? data.afkSinceTick + MANUAL_GRACE_TICKS : 0;
        DebugLogger.stateChange(MODULE, player.getName().getString(), "isAfk", false, true);
        DebugLogger.info(MODULE, "%s 手动模式宽限期截止 tick=%d（%d tick = %ds）",
                player.getName().getString(), data.activityGraceUntilTick,
                MANUAL_GRACE_TICKS, MANUAL_GRACE_TICKS / 20);

        // 可选无敌：记录进入前状态，退出时按原状恢复
        if (AfkConfig.isInvulnerableEnabled()) {
            data.hadResistanceBefore = player.hasEffect(MobEffects.RESISTANCE);
            if (!data.hadResistanceBefore) {
                player.addEffect(new MobEffectInstance(
                        MobEffects.RESISTANCE,
                        MobEffectInstance.INFINITE_DURATION,
                        5, false, false, false));
                DebugLogger.info(MODULE, "玩家 %s AFK 无敌已启用（抗性提升 V 无限）",
                        player.getName().getString());
            }
        }

        if (AfkConfig.isBroadcastEnabled()) {
            MinecraftServer server = player.level().getServer();
            if (server != null) {
                server.getPlayerList().broadcastSystemMessage(
                        Component.translatable("youzaiworldcore.message.afk.entered",
                                player.getDisplayName()),
                        false);
            }
        }
        refreshTabDisplay(player);
        DebugLogger.info(MODULE, "玩家 %s 进入 AFK 状态", player.getName().getString());
        DebugLogger.exiting(MODULE, "enterAfk");
    }

    /**
     * 退出 AFK：清状态、广播、恢复无敌、刷新 Tab 前缀。
     */
    public static void exitAfk(ServerPlayer player) {
        DebugLogger.entering(MODULE, "exitAfk", "player=" + player.getName().getString());
        AfkPlayerData data = getOrCreate(player.getUUID());
        if (!data.isAfk) {
            DebugLogger.info(MODULE, "玩家 %s 未处于 AFK 状态，跳过退出", player.getName().getString());
            DebugLogger.exiting(MODULE, "exitAfk", "not-afk");
            return;
        }
        data.isAfk = false;
        data.manualAfk = false;
        data.afkSinceTick = 0;
        data.activityGraceUntilTick = 0;
        DebugLogger.stateChange(MODULE, player.getName().getString(), "isAfk", true, false);

        // 无敌恢复：仅当是我们加上去的才移除
        if (AfkConfig.isInvulnerableEnabled() && !data.hadResistanceBefore) {
            player.removeEffect(MobEffects.RESISTANCE);
            DebugLogger.info(MODULE, "玩家 %s 退出 AFK，无敌已解除", player.getName().getString());
        }
        data.hadResistanceBefore = false;

        if (AfkConfig.isBroadcastEnabled()) {
            MinecraftServer server = player.level().getServer();
            if (server != null) {
                server.getPlayerList().broadcastSystemMessage(
                        Component.translatable("youzaiworldcore.message.afk.left",
                                player.getDisplayName()),
                        false);
            }
        }
        refreshTabDisplay(player);
        DebugLogger.info(MODULE, "玩家 %s 退出 AFK 状态", player.getName().getString());
        DebugLogger.exiting(MODULE, "exitAfk");
    }

    /**
     * 手动切换 AFK（/yzwc afk）：处于 AFK 则退出，否则立即进入。
     *
     * @return 切换后的 AFK 状态
     */
    public static boolean toggleManual(ServerPlayer player) {
        DebugLogger.entering(MODULE, "toggleManual", "player=" + player.getName().getString());
        boolean now = isAfk(player);
        if (now) {
            exitAfk(player);
        } else {
            enterAfk(player, true);
        }
        boolean result = !now;
        DebugLogger.exiting(MODULE, "toggleManual", "nowAfk=" + result);
        return result;
    }

    /**
     * 功能被禁用时的全局清理：全部退出 AFK 并清空数据表。
     */
    public static void disableAll(MinecraftServer server) {
        DebugLogger.entering(MODULE, "disableAll");
        List<ServerPlayer> afkPlayers = new ArrayList<>();
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (isAfk(p)) {
                afkPlayers.add(p);
            }
        }
        for (ServerPlayer p : afkPlayers) {
            // 直接清状态（不广播，避免功能禁用时刷屏）
            AfkPlayerData data = DATA.get(p.getUUID());
            if (data != null) {
                data.isAfk = false;
                data.manualAfk = false;
                if (AfkConfig.isInvulnerableEnabled() && !data.hadResistanceBefore) {
                    p.removeEffect(MobEffects.RESISTANCE);
                }
                data.hadResistanceBefore = false;
                refreshTabDisplay(p);
                DebugLogger.info(MODULE, "功能禁用，玩家 %s 退出 AFK", p.getName().getString());
            }
        }
        DebugLogger.exiting(MODULE, "disableAll", "cleared=" + afkPlayers.size());
    }

    // ==================== 生命周期 ====================

    /** 玩家加入：初始化 AFK 数据（服务端近似检测的基准位置） */
    public static void onJoin(ServerPlayer player, long serverTick) {
        DebugLogger.entering(MODULE, "onJoin", "player=" + player.getName().getString());
        AfkPlayerData data = getOrCreate(player.getUUID());
        data.serverLastActivityTick = serverTick;
        data.clientLastActivityTick = -1;
        data.lastHeartbeatTick = -1;
        data.chatLastActivityTick = -1;
        data.activityGraceUntilTick = 0;
        data.isAfk = false;
        data.manualAfk = false;
        data.posInitialized = false;
        DebugLogger.exiting(MODULE, "onJoin");
    }

    /** 玩家登出：清理 AFK 数据（会话级，不持久化） */
    public static void onDisconnect(ServerPlayer player) {
        DebugLogger.entering(MODULE, "onDisconnect", "player=" + player.getName().getString());
        UUID uuid = player.getUUID();
        AfkPlayerData data = DATA.remove(uuid);
        if (data != null && data.isAfk) {
            DebugLogger.info(MODULE, "玩家 %s 登出时处于 AFK，已清理", player.getName().getString());
        }
        DebugLogger.exiting(MODULE, "onDisconnect");
    }

    // ==================== Tab 前缀刷新 ====================

    /**
     * 向全体在线玩家广播 {@code UPDATE_DISPLAY_NAME} 包，使 Tab 列表立即反映
     * 被 mixin 后的 {@code getTabListDisplayName()}（带/不带 [AFK] 前缀）。
     * <p>
     * 该包构造时从 {@code ServerPlayer.getTabListDisplayName()} 读取显示名，
     * 因此广播前确保 mixin 已生效。</p>
     */
    static void refreshTabDisplay(ServerPlayer player) {
        MinecraftServer server = player.level().getServer();
        if (!AfkConfig.isTabPrefixEnabled()) {
            return;
        }
        server.getPlayerList().broadcastAll(
                new ClientboundPlayerInfoUpdatePacket(
                        ClientboundPlayerInfoUpdatePacket.Action.UPDATE_DISPLAY_NAME,
                        player));
        DebugLogger.trace(MODULE, "已广播 UPDATE_DISPLAY_NAME: %s", player.getName().getString());
    }
}
