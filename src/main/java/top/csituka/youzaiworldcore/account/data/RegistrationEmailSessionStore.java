package top.csituka.youzaiworldcore.account.data;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 保存当前连接的待验证邮箱注册会话。
 * 会话只存在于内存中，并与发起注册的玩家 UUID 绑定。
 */
public final class RegistrationEmailSessionStore {
    private static final int MAX_SESSION_TTL_SECONDS = 86_400;
    private static final ConcurrentHashMap<UUID, PendingSession> SESSIONS = new ConcurrentHashMap<>();

    private RegistrationEmailSessionStore() {
    }

    public record PendingSession(String sessionId, long expiresAtMillis) {
        public int remainingSeconds() {
            long remainingMillis = expiresAtMillis - System.currentTimeMillis();
            return remainingMillis <= 0 ? 0 : (int) Math.ceil(remainingMillis / 1000.0D);
        }
    }

    /** 绑定 Api 返回的会话；同一玩家的新会话会覆盖旧会话。 */
    public static PendingSession put(UUID playerUuid, String sessionId, long expiresInSeconds) {
        if (playerUuid == null || sessionId == null || sessionId.isBlank()) {
            throw new IllegalArgumentException("邮箱注册会话参数无效");
        }
        long ttlSeconds = Math.max(1L, Math.min(MAX_SESSION_TTL_SECONDS, expiresInSeconds));
        PendingSession session = new PendingSession(
                sessionId, System.currentTimeMillis() + ttlSeconds * 1000L);
        SESSIONS.put(playerUuid, session);
        return session;
    }

    /** 返回仍有效的会话；过期会话会立即移除。 */
    public static PendingSession get(UUID playerUuid) {
        if (playerUuid == null) return null;
        PendingSession session = SESSIONS.get(playerUuid);
        if (session != null && session.remainingSeconds() <= 0) {
            SESSIONS.remove(playerUuid, session);
            return null;
        }
        return session;
    }

    /** 校验请求中的会话是否属于当前玩家。 */
    public static boolean matches(UUID playerUuid, String sessionId) {
        PendingSession pending = get(playerUuid);
        return pending != null && pending.sessionId().equals(sessionId);
    }

    public static void clear(UUID playerUuid) {
        if (playerUuid != null) SESSIONS.remove(playerUuid);
    }
}
