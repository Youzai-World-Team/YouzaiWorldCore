package top.csituka.youzaiworldcore.account.util;

import top.csituka.youzaiworldcore.account.data.PlayerAccount;
import top.csituka.youzaiworldcore.util.DebugLogger;

/**
 * 认证辅助方法
 */
public class AuthHelper {

    public enum PasswordResult {
        /** 密码正确 */
        CORRECT,
        /** 密码错误 */
        WRONG,
        /** 未注册 */
        NOT_REGISTERED
    }

    /**
     * 检查密码
     */
    public static PasswordResult checkPassword(PlayerAccount account, String password) {
        DebugLogger.entering("AuthHelper", "checkPassword", "account=" + (account != null ? account.username : "null"));
        if (account == null || !account.isRegistered()) {
            DebugLogger.branch("AuthHelper", "account null or not registered", true);
            DebugLogger.exiting("AuthHelper", "checkPassword", "NOT_REGISTERED");
            return PasswordResult.NOT_REGISTERED;
        }
        DebugLogger.branch("AuthHelper", "account null or not registered", false);
        if (PasswordHasher.verify(password, account.password)) {
            DebugLogger.branch("AuthHelper", "password verification", true);
            DebugLogger.exiting("AuthHelper", "checkPassword", "CORRECT");
            return PasswordResult.CORRECT;
        }
        DebugLogger.branch("AuthHelper", "password verification", false);
        DebugLogger.exiting("AuthHelper", "checkPassword", "WRONG");
        return PasswordResult.WRONG;
    }

    /**
     * 检查 IP 是否相同（用于会话恢复）
     */
    public static boolean sameIp(String storedIp, String currentIp) {
        DebugLogger.entering("AuthHelper", "sameIp",
                "storedIp=" + storedIp + ", currentIp=" + currentIp);
        if (storedIp == null || storedIp.isEmpty()) {
            DebugLogger.branch("AuthHelper", "storedIp valid", false);
            DebugLogger.exiting("AuthHelper", "sameIp", "false (storedIp null/empty)");
            return false;
        }
        DebugLogger.branch("AuthHelper", "storedIp valid", true);
        if (currentIp == null || currentIp.isEmpty()) {
            DebugLogger.branch("AuthHelper", "currentIp valid", false);
            DebugLogger.exiting("AuthHelper", "sameIp", "false (currentIp null/empty)");
            return false;
        }
        DebugLogger.branch("AuthHelper", "currentIp valid", true);
        boolean result = storedIp.equals(currentIp);
        DebugLogger.exiting("AuthHelper", "sameIp", String.valueOf(result));
        return result;
    }

    /**
     * 获取客户端的 IP 地址
     */
    public static String getIp(java.net.SocketAddress socketAddress) {
        DebugLogger.entering("AuthHelper", "getIp",
                "socketAddress=" + socketAddress);
        if (socketAddress instanceof java.net.InetSocketAddress inetAddr) {
            DebugLogger.branch("AuthHelper", "socketAddress is InetSocketAddress", true);
            java.net.InetAddress addr = inetAddr.getAddress();
            if (addr != null) {
                String ip = com.google.common.net.InetAddresses.toAddrString(addr);
                DebugLogger.exiting("AuthHelper", "getIp", ip);
                return ip;
            }
            DebugLogger.branch("AuthHelper", "InetAddress is null", true);
        } else {
            DebugLogger.branch("AuthHelper", "socketAddress is InetSocketAddress", false);
        }
        DebugLogger.exiting("AuthHelper", "getIp", "<unknown>");
        return "<unknown>";
    }
}
