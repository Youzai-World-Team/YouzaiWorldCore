package top.csituka.youzaiworldcore.account.util;

import top.csituka.youzaiworldcore.util.DebugLogger;

public class AuthHelper {

    public static String getIp(java.net.SocketAddress socketAddress) {
        DebugLogger.SERVER.entering("AuthHelper", "getIp",
                "socketAddress=" + socketAddress);
        if (socketAddress instanceof java.net.InetSocketAddress inetAddr) {
            DebugLogger.SERVER.branch("AuthHelper", "socketAddress is InetSocketAddress", true);
            java.net.InetAddress addr = inetAddr.getAddress();
            if (addr != null) {
                String ip = com.google.common.net.InetAddresses.toAddrString(addr);
                DebugLogger.SERVER.exiting("AuthHelper", "getIp", ip);
                return ip;
            }
            DebugLogger.SERVER.branch("AuthHelper", "InetAddress is null", true);
        } else {
            DebugLogger.SERVER.branch("AuthHelper", "socketAddress is InetSocketAddress", false);
        }
        DebugLogger.SERVER.exiting("AuthHelper", "getIp", "<unknown>");
        return "<unknown>";
    }
}
