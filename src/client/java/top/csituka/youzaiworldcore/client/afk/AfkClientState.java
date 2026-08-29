package top.csituka.youzaiworldcore.client.afk;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import top.csituka.youzaiworldcore.network.AfkStatePayload;
import top.csituka.youzaiworldcore.util.DebugLogger;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** 客户端只读 AFK 状态缓存，仅用于玩家头顶名字牌渲染。 */
public final class AfkClientState {

    private static final String MODULE = "AfkClientState";
    private static final Set<UUID> AFK_PLAYERS = new HashSet<>();

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
            changed = AFK_PLAYERS.add(payload.playerUuid());
        } else {
            changed = AFK_PLAYERS.remove(payload.playerUuid());
        }
        if (changed) {
            DebugLogger.stateChange(MODULE, payload.playerUuid().toString(),
                    "afk", !payload.afk(), payload.afk());
        }
    }

    /** @return 指定玩家是否处于 AFK 状态。 */
    public static boolean isAfk(UUID playerUuid) {
        return AFK_PLAYERS.contains(playerUuid);
    }

    private static void clear() {
        if (!AFK_PLAYERS.isEmpty()) {
            DebugLogger.info(MODULE, "断开连接，清理 %d 条 AFK 状态", AFK_PLAYERS.size());
            AFK_PLAYERS.clear();
        }
    }
}
