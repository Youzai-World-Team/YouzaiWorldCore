package top.csituka.youzaiworldcore.account.data;

import top.csituka.youzaiworldcore.account.util.AuthLocationData;

/**
 * 供外部类访问 AccountServerPlayerMixin 混合数据的接口。
 * Mixin 0.8.7 禁止从外部直接引用 mixin 类，必须通过接口桥接。
 */
public interface PlayerAuthAccess {
    boolean yzwc$isAuthenticated();
    void yzwc$setAuthenticated(boolean authenticated);
    boolean yzwc$canSkipAuth();
    void yzwc$setCanSkipAuth(boolean canSkip);
    PlayerAccount yzwc$getAccount();
    void yzwc$setAccount(PlayerAccount account);
    String yzwc$getIpAddress();
    void yzwc$setIpAddress(String ip);
    AuthLocationData yzwc$getLastLocation();
    void yzwc$setLastLocation(AuthLocationData loc);
    void yzwc$saveLocation();
    int yzwc$getKickTimer();
    void yzwc$setKickTimer(int timer);
}
