package top.csituka.youzaiworldcore.account.util;

import top.csituka.youzaiworldcore.util.DebugLogger;

/**
 * 认证辅助方法
 */
public class AuthHelper {

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
