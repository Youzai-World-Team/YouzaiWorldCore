package top.csituka.youzaiworldcore.client.afk;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import top.csituka.youzaiworldcore.network.AfkStatePayload;
import top.csituka.youzaiworldcore.util.DebugLogger;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** 客户端只读 AFK 状态缓存，仅用于玩家头顶名字牌渲染。 */
public final class AfkClientState {

    private static final String MODULE = "AfkClientState";
    private static final Map<UUID, String> AFK_PLAYERS = new HashMap<>();

    private AfkClientState() {
    }

    /** 注册断线清理，避免跨服务器保留上一会话状态。 */
    public static void initialize() {
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> clear());
        DebugLogger.info(MODULE, "客户端 AFK 状态缓存已初始化");
    }

    /** 应用服务端下发的单玩家 AFK 状态。 */
    public static void apply(AfkStatePayload payload) {
        boolean changed;
        if (payload.afk()) {
            changed = !Objects.equals(AFK_PLAYERS.put(payload.playerUuid(), payload.prefix()), payload.prefix());
        } else {
            changed = AFK_PLAYERS.remove(payload.playerUuid()) != null;
        }
        if (changed) {
            DebugLogger.trace(MODULE, "AFK 显示更新: player=%s, afk=%s, prefix=%s",
                    payload.playerUuid(), payload.afk(), payload.prefix());
        }
    }

    public static boolean isAfk(UUID playerUuid) {
        return AFK_PLAYERS.containsKey(playerUuid);
    }

    /** 服务端已应用名字牌开关后的前缀；关闭显示或非 AFK 时返回空串。 */
    public static String getPrefix(UUID playerUuid) {
        return AFK_PLAYERS.getOrDefault(playerUuid, "");
    }

    private static void clear() {
        if (!AFK_PLAYERS.isEmpty()) {
            DebugLogger.info(MODULE, "断开连接，清理 %d 条 AFK 状态", AFK_PLAYERS.size());
            AFK_PLAYERS.clear();
        }
    }
}
