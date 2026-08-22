package top.csituka.youzaiworldcore.account.data;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** 保存与当前已登录玩家连接绑定的换绑邮箱会话。 */
public final class EmailChangeSessionStore {
    private static final int MAX_SESSION_TTL_SECONDS = 86_400;
    private static final ConcurrentHashMap<UUID, PendingSession> SESSIONS = new ConcurrentHashMap<>();

    private EmailChangeSessionStore() {
    }

    public record PendingSession(String sessionId, long expiresAtMillis) {
        public int remainingSeconds() {
            long remainingMillis = expiresAtMillis - System.currentTimeMillis();
            return remainingMillis <= 0 ? 0 : (int) Math.ceil(remainingMillis / 1000.0D);
        }
    }

    public static PendingSession put(UUID playerUuid, String sessionId, long expiresInSeconds) {
        if (playerUuid == null || sessionId == null || sessionId.isBlank()) {
            throw new IllegalArgumentException("换绑邮箱会话参数无效");
        }
        long ttlSeconds = Math.max(1L, Math.min(MAX_SESSION_TTL_SECONDS, expiresInSeconds));
        PendingSession session = new PendingSession(
                sessionId, System.currentTimeMillis() + ttlSeconds * 1000L);
        SESSIONS.put(playerUuid, session);
        return session;
    }

    public static PendingSession get(UUID playerUuid) {
        if (playerUuid == null) return null;
        PendingSession session = SESSIONS.get(playerUuid);
        if (session != null && session.remainingSeconds() <= 0) {
            SESSIONS.remove(playerUuid, session);
            return null;
        }
        return session;
    }

    public static boolean matches(UUID playerUuid, String sessionId) {
        PendingSession pending = get(playerUuid);
        return pending != null && pending.sessionId().equals(sessionId);
    }

    public static void clear(UUID playerUuid) {
        if (playerUuid != null) SESSIONS.remove(playerUuid);
    }
}
