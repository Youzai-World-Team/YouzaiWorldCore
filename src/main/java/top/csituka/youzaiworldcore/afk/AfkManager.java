package top.csituka.youzaiworldcore.afk;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import top.csituka.youzaiworldcore.config.AfkConfig;
import top.csituka.youzaiworldcore.network.AfkStatePayload;
import top.csituka.youzaiworldcore.util.DebugLogger;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * AFK（挂机）状态管理器 — 服务端权威。
 * <p>
 * 每个在线玩家对应一条 {@link AfkPlayerData}，记录客户端、服务端和聊天/命令活动时间，
 * 以及客户端心跳的新鲜度：
 * <ul>
 * <li>{@code clientLastActivityTick} — 客户端心跳上报的真实输入时间
 * （客户端 mixin 键盘/鼠标后按「距最后输入的 tick 差值」上报，无时钟同步问题）；</li>
 * <li>{@code serverLastActivityTick} — 服务端近似检测（位置/视角变化），
 * 兜底原版客户端；</li>
 * <li>{@code lastHeartbeatTick} — 最近一次心跳到达时间，用于判断客户端通道是否失效。</li>
 * </ul>
 * 有效活动时间 = 按 {@link AfkConfig.DetectMode} 选择客户端或服务端通道，由
 * {@link AfkTickHandler} 每 20 tick 判定进入/退出 AFK。
 * <p>
 * AFK 表现：Tab 前缀（mixin {@code ServerPlayer.getTabListDisplayName()}）、
 * 客户端头顶名字牌前缀、进入/退出广播、可选无敌（无限抗性提升 V）、
 * 可选超时踢出，全部由配置控制。
 * </p>
 */
@SuppressWarnings("null")
public final class AfkManager {

    private static final String MODULE = "AfkManager";

    /** 客户端心跳超时（tick）：超过 5 秒未收到心跳视为客户端通道失效（原版客户端） */
    private static final int HEARTBEAT_TIMEOUT_TICKS = 100;

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
        /** 客户端聊天/指令数据包的最后活动 tick，-1 = 从未 */
        long chatLastActivityTick = -1;
        /** 手动 AFK 进入时的客户端输入基线，用于忽略切换命令自身的残余心跳 */
        long manualClientActivityBaselineTick = -1;
        /** 手动 AFK 进入时的聊天/指令基线，用于忽略切换命令自身的活动事件 */
        long manualChatActivityBaselineTick = -1;
        /** 当前是否处于 AFK 状态 */
        boolean isAfk = false;
        /** 进入 AFK 的服务端 tick */
        long afkSinceTick = 0;
        /** 是否由 /yzwc afk 手动标记（不影响自动恢复逻辑，仅用于状态查询展示） */
        boolean manualAfk = false;
        /** 进入 AFK 前的抗性效果快照；为空表示进入前没有抗性 */
        MobEffectInstance resistanceBefore;
        /** 保存抗性快照时的服务端 tick，用于恢复有限时长效果时扣除已过时间 */
        long resistanceSnapshotTick;
        /** 是否由 AFK 模块当前持有并添加了抗性效果 */
        boolean afkResistanceApplied = false;
        /** 实际添加到玩家身上的效果实例，用于避免误删 AFK 期间的外部抗性 */
        MobEffectInstance afkResistanceEffect;
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
     * @param player     目标玩家
     * @param serverTick 当前服务端 tick
     * @param idleTicks  客户端自最后一次输入以来经过的 tick 数（差值，无时钟问题）
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
     * 进入 AFK 的退出判定会使用该信号；自动进入判定是否采用它由
     * {@link #getEffectiveActivityTick} 决定。
     * </p>
     */
    public static void onServerActivity(ServerPlayer player, long serverTick) {
        AfkPlayerData data = getOrCreate(player.getUUID());
        data.serverLastActivityTick = serverTick;
        DebugLogger.trace(MODULE, "onServerActivity %s: serverLastActivityTick=%d",
                player.getName().getString(), serverTick);
    }

    /**
     * 客户端聊天/指令数据包上报活动。
     * <p>
     * 该事件在命令执行前触发，因此可以覆盖不产生广播消息的普通命令。
     * </p>
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
     * <li>{@code CLIENT}：仅客户端通道；通道失效 → {@link Long#MAX_VALUE}（永不判定，
     * 原版客户端无精确检测，文档约定）；</li>
     * <li>{@code SERVER}：仅服务端位置/视角近似检测；</li>
     * <li>{@code BOTH}：客户端通道存活时<b>以客户端为准</b>（位置检测不覆盖客户端
     * 精确判定——「按住 W 挂机」客户端无重复输入 → 判定 AFK）；
     * 客户端通道失效（原版客户端）时回退服务端近似检测。</li>
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
                        ? data.clientLastActivityTick
                        : Long.MAX_VALUE;
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
        // 记录手动切换命令执行前已经存在的活动，后续只接受新的输入。
        data.manualClientActivityBaselineTick = manual
                ? Math.max(data.clientLastActivityTick, data.afkSinceTick)
                : -1;
        data.manualChatActivityBaselineTick = manual
                ? Math.max(data.chatLastActivityTick, data.afkSinceTick)
                : -1;
        DebugLogger.stateChange(MODULE, player.getName().getString(), "isAfk", false, true);
        if (manual) {
            DebugLogger.info(MODULE, "%s 手动 AFK 活动基线: client=%d, chat=%d",
                    player.getName().getString(), data.manualClientActivityBaselineTick,
                    data.manualChatActivityBaselineTick);
        }

        // 可选无敌：保存进入前的抗性效果，退出时恢复
        if (AfkConfig.isInvulnerableEnabled()) {
            applyAfkResistance(player, data);
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
        broadcastAfkState(player, true);
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
        data.manualClientActivityBaselineTick = -1;
        data.manualChatActivityBaselineTick = -1;
        DebugLogger.stateChange(MODULE, player.getName().getString(), "isAfk", true, false);

        removeAfkResistance(player, data);

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
        broadcastAfkState(player, false);
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
        int cleared = 0;
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            // 直接清状态（不广播，避免功能禁用时刷屏）
            AfkPlayerData data = DATA.get(p.getUUID());
            if (data != null && (data.isAfk || data.afkResistanceApplied)) {
                boolean wasAfk = data.isAfk;
                data.isAfk = false;
                data.manualAfk = false;
                data.afkSinceTick = 0;
                removeAfkResistance(p, data);
                data.manualClientActivityBaselineTick = -1;
                data.manualChatActivityBaselineTick = -1;
                refreshTabDisplay(p);
                if (wasAfk) {
                    broadcastAfkState(p, false);
                    cleared++;
                    DebugLogger.info(MODULE, "功能禁用，玩家 %s 退出 AFK", p.getName().getString());
                }
            }
        }
        DebugLogger.exiting(MODULE, "disableAll", "cleared=" + cleared);
    }

    // ==================== 生命周期 ====================

    /** 玩家加入：初始化 AFK 数据（服务端近似检测的基准位置） */
    public static void onJoin(ServerPlayer player, long serverTick) {
        DebugLogger.entering(MODULE, "onJoin", "player=" + player.getName().getString());
        AfkPlayerData data = getOrCreate(player.getUUID());
        // 防止异常重连顺序留下上一会话由 AFK 添加的抗性效果。
        removeAfkResistance(player, data);
        data.serverLastActivityTick = serverTick;
        data.clientLastActivityTick = -1;
        data.lastHeartbeatTick = -1;
        data.chatLastActivityTick = -1;
        data.manualClientActivityBaselineTick = -1;
        data.manualChatActivityBaselineTick = -1;
        data.isAfk = false;
        data.manualAfk = false;
        data.posInitialized = false;
        broadcastAfkState(player, false);
        sendAfkSnapshot(player);
        DebugLogger.exiting(MODULE, "onJoin");
    }

    /** 玩家登出：清理 AFK 数据（会话级，不持久化） */
    public static void onDisconnect(ServerPlayer player) {
        DebugLogger.entering(MODULE, "onDisconnect", "player=" + player.getName().getString());
        UUID uuid = player.getUUID();
        AfkPlayerData data = DATA.remove(uuid);
        if (data != null) {
            boolean wasAfk = data.isAfk;
            removeAfkResistance(player, data);
            if (wasAfk) {
                DebugLogger.info(MODULE, "玩家 %s 登出时处于 AFK，已清理", player.getName().getString());
            }
        }
        broadcastAfkState(player, false);
        DebugLogger.exiting(MODULE, "onDisconnect");
    }

    // ==================== 名字牌状态同步与 Tab 前缀刷新 ====================

    /** 向所有在线客户端广播单个玩家的 AFK 状态。 */
    private static void broadcastAfkState(ServerPlayer player, boolean afk) {
        MinecraftServer server = player.level().getServer();
        AfkStatePayload payload = new AfkStatePayload(player.getUUID(), afk);
        int recipients = 0;
        for (ServerPlayer recipient : server.getPlayerList().getPlayers()) {
            if (recipient.hasDisconnected()) {
                continue;
            }
            ServerPlayNetworking.send(recipient, payload);
            recipients++;
        }
        DebugLogger.trace(MODULE, "已广播 AFK 状态: player=%s, afk=%s, recipients=%d",
                player.getName().getString(), afk, recipients);
    }

    /** 玩家加入时补发当前全部 AFK 玩家，避免只收到后续状态变化。 */
    private static void sendAfkSnapshot(ServerPlayer recipient) {
        if (recipient.hasDisconnected()) {
            return;
        }
        int count = 0;
        for (UUID uuid : getAfkPlayers()) {
            ServerPlayNetworking.send(recipient, new AfkStatePayload(uuid, true));
            count++;
        }
        DebugLogger.debug(MODULE, "已向 %s 同步 %d 条 AFK 名字牌状态",
                recipient.getName().getString(), count);
    }

    /**
     * 向全体在线玩家广播 {@code UPDATE_DISPLAY_NAME} 包，使 Tab 列表立即反映
     * 被 mixin 后的 {@code getTabListDisplayName()}（带/不带 [AFK] 前缀）。
     * <p>
     * 该包构造时从 {@code ServerPlayer.getTabListDisplayName()} 读取显示名，
     * 因此广播前确保 mixin 已生效。
     * </p>
     */
    static void refreshTabDisplay(ServerPlayer player) {
        MinecraftServer server = player.level().getServer();
        server.getPlayerList().broadcastAll(
                new ClientboundPlayerInfoUpdatePacket(
                        ClientboundPlayerInfoUpdatePacket.Action.UPDATE_DISPLAY_NAME,
                        player));
        DebugLogger.trace(MODULE, "已广播 UPDATE_DISPLAY_NAME: %s", player.getName().getString());
    }

    /** 刷新所有在线玩家的 Tab 显示名，用于运行时配置或称号变更。 */
    public static void refreshAllTabDisplays(MinecraftServer server) {
        if (server == null) {
            return;
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            refreshTabDisplay(player);
        }
    }

    /** 将 AFK 无敌配置同步到当前在线玩家。 */
    public static void syncInvulnerability(MinecraftServer server) {
        if (server == null) {
            return;
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            AfkPlayerData data = DATA.get(player.getUUID());
            if (data == null || !data.isAfk) {
                continue;
            }
            if (AfkConfig.isInvulnerableEnabled()) {
                applyAfkResistance(player, data);
            } else {
                removeAfkResistance(player, data);
            }
        }
    }

    private static void applyAfkResistance(ServerPlayer player, AfkPlayerData data) {
        if (data.afkResistanceApplied) {
            return;
        }
        MobEffectInstance existing = player.getEffect(MobEffects.RESISTANCE);
        data.resistanceBefore = existing == null ? null : new MobEffectInstance(existing);
        MinecraftServer server = player.level().getServer();
        data.resistanceSnapshotTick = server == null ? 0 : server.getTickCount();
        MobEffectInstance afkEffect = new MobEffectInstance(
                MobEffects.RESISTANCE,
                MobEffectInstance.INFINITE_DURATION,
                5, false, false, false);
        player.addEffect(afkEffect);
        // addEffect 可能合并到已有实例，读取实体当前实例才能准确追踪所有权。
        data.afkResistanceEffect = player.getEffect(MobEffects.RESISTANCE);
        if (data.afkResistanceEffect == null) {
            data.afkResistanceEffect = afkEffect;
        }
        data.afkResistanceApplied = true;
        DebugLogger.info(MODULE, "玩家 %s AFK 无敌已启用（抗性提升 V 无限）",
                player.getName().getString());
    }

    private static void removeAfkResistance(ServerPlayer player, AfkPlayerData data) {
        if (!data.afkResistanceApplied) {
            data.resistanceBefore = null;
            data.resistanceSnapshotTick = 0;
            data.afkResistanceEffect = null;
            return;
        }
        MobEffectInstance current = player.getEffect(MobEffects.RESISTANCE);
        boolean stillOwned = current == data.afkResistanceEffect
                && current != null
                && current.isInfiniteDuration()
                && current.getAmplifier() == 5
                && !current.isAmbient()
                && !current.isVisible()
                && !current.showIcon();
        if (stillOwned) {
            player.removeEffect(MobEffects.RESISTANCE);
        }
        if (stillOwned && data.resistanceBefore != null) {
            MobEffectInstance restored = data.resistanceBefore;
            if (!restored.isInfiniteDuration()) {
                MinecraftServer server = player.level().getServer();
                long now = server == null ? data.resistanceSnapshotTick : server.getTickCount();
                long elapsed = Math.max(0, now - data.resistanceSnapshotTick);
                int remaining = (int) Math.max(0L, restored.getDuration() - elapsed);
                if (remaining > 0) {
                    restored = new MobEffectInstance(restored.getEffect(), remaining,
                            restored.getAmplifier(), restored.isAmbient(),
                            restored.isVisible(), restored.showIcon());
                    player.addEffect(restored);
                }
            } else {
                player.addEffect(restored);
            }
        }
        data.afkResistanceApplied = false;
        data.resistanceBefore = null;
        data.resistanceSnapshotTick = 0;
        data.afkResistanceEffect = null;
        DebugLogger.info(MODULE, "玩家 %s 退出 AFK，无敌效果已处理（外部抗性保持不变）",
                player.getName().getString());
    }
}
