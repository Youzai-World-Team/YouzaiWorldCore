package top.csituka.youzaiworldcore.afk;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import top.csituka.youzaiworldcore.config.AfkConfig;
import top.csituka.youzaiworldcore.network.AfkStatePayload;
import top.csituka.youzaiworldcore.util.DebugLogger;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 服务端 AFK 会话管理器。全部状态在主线程读写，掉线即清理。
 * 活动序号和计时交给 AfkActivityState，显示同步及广播在此处理。
 * 无敌直接由伤害入口读取状态，不添加或修改玩家的药水效果。
 */
@SuppressWarnings("null")
public final class AfkManager {
    private static final String MODULE = "AfkManager";
    private static final Map<UUID, AfkPlayerData> DATA = new HashMap<>();

    private AfkManager() {
    }

    /** 单个玩家的会话状态和位置采样基线。 */
    static final class AfkPlayerData {
        final AfkActivityState activity;
        double lastX, lastY, lastZ;
        float lastYRot, lastXRot;

        AfkPlayerData(ServerPlayer player, long now) {
            activity = new AfkActivityState(now);
            samplePosition(player);
        }

        void samplePosition(ServerPlayer player) {
            lastX = player.getX();
            lastY = player.getY();
            lastZ = player.getZ();
            lastYRot = player.getYRot();
            lastXRot = player.getXRot();
        }
    }

    /** 查询在线玩家的 AFK 状态。 */
    public static boolean isAfk(ServerPlayer player) {
        return isAfk(player.getUUID());
    }

    /** 查询 UUID 的 AFK 状态。 */
    public static boolean isAfk(UUID uuid) {
        AfkPlayerData data = DATA.get(uuid);
        return data != null && data.activity.afk;
    }

    /** 伤害入口直接读取配置，开关变更立即生效。 */
    public static boolean isProtected(ServerPlayer player) {
        return AfkConfig.isEnabled() && AfkConfig.isInvulnerableEnabled() && isAfk(player);
    }

    static AfkPlayerData getOrCreate(ServerPlayer player) {
        return DATA.computeIfAbsent(player.getUUID(),
                uuid -> new AfkPlayerData(player, player.level().getServer().getTickCount()));
    }

    /** 当前所有 AFK 玩家 UUID 的不可变快照。 */
    public static Set<UUID> getAfkPlayers() {
        return DATA.entrySet().stream().filter(entry -> entry.getValue().activity.afk)
                .map(Map.Entry::getKey).collect(Collectors.toUnmodifiableSet());
    }

    /** 主线程接收客户端心跳，输入序号不变时不会被识别成新活动。 */
    public static void onHeartbeat(ServerPlayer player, long now, int idleTicks, long sequence) {
        if (player.hasDisconnected()) {
            return;
        }
        getOrCreate(player).activity.heartbeat(now, idleTicks, sequence);
        DebugLogger.trace(MODULE, "心跳: player=%s, idle=%d, sequence=%d",
                player.getName().getString(), idleTicks, sequence);
    }

    /** 聊天和命令都作为明确活动，并参与自动进入计时。 */
    public static void onChatActivity(ServerPlayer player, long now) {
        if (player.hasDisconnected()) {
            return;
        }
        getOrCreate(player).activity.explicitActivity(now);
        DebugLogger.trace(MODULE, "聊天/命令活动: player=%s, tick=%d", player.getName().getString(), now);
    }

    /** 进入 AFK；重新采样位置，避免把执行命令前的移动当作新操作。 */
    public static void enterAfk(ServerPlayer player, boolean manual) {
        AfkPlayerData data = getOrCreate(player);
        if (data.activity.afk) {
            return;
        }
        data.samplePosition(player);
        data.activity.enter(player.level().getServer().getTickCount(), manual);
        DebugLogger.stateChange(MODULE, player.getName().getString(), "isAfk", false, true);
        announce(player, true);
        refreshTabDisplay(player);
        broadcastAfkState(player);
    }

    /** 退出 AFK，并从此次恢复活动重新开始计时。 */
    public static void exitAfk(ServerPlayer player) {
        AfkPlayerData data = getOrCreate(player);
        if (!data.activity.afk) {
            return;
        }
        data.activity.exit(player.level().getServer().getTickCount());
        data.samplePosition(player);
        DebugLogger.stateChange(MODULE, player.getName().getString(), "isAfk", true, false);
        announce(player, false);
        refreshTabDisplay(player);
        broadcastAfkState(player);
    }

    /** 手动切换，命令本身的活动已经在进入前纳入基线。 */
    public static boolean toggleManual(ServerPlayer player) {
        if (isAfk(player)) {
            exitAfk(player);
            return false;
        }
        enterAfk(player, true);
        return true;
    }

    private static void announce(ServerPlayer player, boolean afk) {
        if (AfkConfig.isBroadcastEnabled()) {
            player.level().getServer().getPlayerList().broadcastSystemMessage(
                    Component.translatable(afk ? "youzaiworldcore.message.afk.entered"
                            : "youzaiworldcore.message.afk.left", player.getDisplayName()), false);
        }
    }

    /** 禁用时清除状态，不发送返回广播；保留连接输入序号供再次启用使用。 */
    public static void disableAll(MinecraftServer server) {
        long now = server.getTickCount();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            AfkPlayerData data = getOrCreate(player);
            boolean wasAfk = data.activity.afk;
            data.activity.exit(now);
            data.samplePosition(player);
            if (wasAfk) {
                refreshTabDisplay(player);
                broadcastAfkState(player);
                DebugLogger.info(MODULE, "功能禁用，清除 %s 的 AFK 状态", player.getName().getString());
            }
        }
    }

    /** 配置变更后同步显示；切换模式或重新启用时重新建立活动基线。 */
    public static void applyConfiguration(MinecraftServer server, boolean rebase) {
        if (!AfkConfig.isEnabled()) {
            disableAll(server);
        } else if (rebase) {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                AfkPlayerData data = getOrCreate(player);
                data.activity.rebase(server.getTickCount());
                data.samplePosition(player);
            }
        }
        refreshAllTabDisplays(server);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (isAfk(player)) {
                broadcastAfkState(player);
            }
        }
        DebugLogger.info(MODULE, "AFK 配置已同步到在线玩家，重建活动基线=%s", rebase);
    }

    /** 加入时创建独立会话，并补发其他玩家的 AFK 状态。 */
    public static void onJoin(ServerPlayer player, long now) {
        DATA.put(player.getUUID(), new AfkPlayerData(player, now));
        broadcastAfkState(player);
        if (ServerPlayNetworking.canSend(player, AfkStatePayload.ID)) {
            for (UUID uuid : getAfkPlayers()) {
                ServerPlayNetworking.send(player, statePayload(uuid, true));
            }
        }
        DebugLogger.info(MODULE, "初始化 AFK 会话: %s", player.getName().getString());
    }

    /** 掉线清理会话，不持久化 AFK 状态。 */
    public static void onDisconnect(ServerPlayer player) {
        DATA.remove(player.getUUID());
        broadcastAfkState(player);
        DebugLogger.info(MODULE, "清理 AFK 会话: %s", player.getName().getString());
    }

    private static AfkStatePayload statePayload(UUID uuid, boolean afk) {
        String prefix = AfkConfig.isNametagPrefixEnabled() ? AfkConfig.getPrefix() : "";
        return new AfkStatePayload(uuid, afk, prefix);
    }

    private static void broadcastAfkState(ServerPlayer player) {
        MinecraftServer server = player.level().getServer();
        AfkStatePayload payload = statePayload(player.getUUID(), isAfk(player));
        for (ServerPlayer recipient : server.getPlayerList().getPlayers()) {
            if (!recipient.hasDisconnected() && ServerPlayNetworking.canSend(recipient, AfkStatePayload.ID)) {
                ServerPlayNetworking.send(recipient, payload);
            }
        }
    }

    static void refreshTabDisplay(ServerPlayer player) {
        player.level().getServer().getPlayerList().broadcastAll(new ClientboundPlayerInfoUpdatePacket(
                ClientboundPlayerInfoUpdatePacket.Action.UPDATE_DISPLAY_NAME, player));
    }

    /** 刷新全部 Tab 显示名，供 AFK 配置和称号系统使用。 */
    public static void refreshAllTabDisplays(MinecraftServer server) {
        if (server != null) {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                refreshTabDisplay(player);
            }
        }
    }
}
