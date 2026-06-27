package top.csituka.youzaiworldcore.account.util;

import top.csituka.youzaiworldcore.account.data.PlayerAccount;

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
        if (account == null || !account.isRegistered()) {
            return PasswordResult.NOT_REGISTERED;
        }
        if (PasswordHasher.verify(password, account.password)) {
            return PasswordResult.CORRECT;
        }
        return PasswordResult.WRONG;
    }

    /**
     * 检查 IP 是否相同（用于会话恢复）
     */
    public static boolean sameIp(String storedIp, String currentIp) {
        if (storedIp == null || storedIp.isEmpty()) return false;
        if (currentIp == null || currentIp.isEmpty()) return false;
        return storedIp.equals(currentIp);
    }

    /**
     * 获取客户端的 IP 地址
     */
    public static String getIp(java.net.SocketAddress socketAddress) {
        if (socketAddress instanceof java.net.InetSocketAddress inetAddr) {
            java.net.InetAddress addr = inetAddr.getAddress();
            if (addr != null) {
                return com.google.common.net.InetAddresses.toAddrString(addr);
            }
        }
        return "<unknown>";
    }
}
