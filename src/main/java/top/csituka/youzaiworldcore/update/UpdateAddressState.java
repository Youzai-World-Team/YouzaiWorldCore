package top.csituka.youzaiworldcore.update;

import top.csituka.youzaiworldcore.util.DebugLogger;

/**
 * 客户端更新地址共享状态（main 源集）。
 * <p>
 * 客户端的自定义更新地址（检查 / 跳转）保存在 {@code ClientExternalSettings}（client 源集），
 * 而服务端（main 源集）无法反向引用 client 类。因此由客户端在启动时 / 设置变更时，
 * 将当前开发者模式下的基址推送到本类，供「内嵌（集成）服务端」读取使用。
 * </p>
 * <p>专用服务端不使用本状态，而是直接读取配置文件 {@link UpdateCheckerConfig}。</p>
 */
public final class UpdateAddressState {

    private static final String MODULE = "UpdateAddressState";

    private static volatile boolean clientDevMode = false;
    private static volatile String clientCheckBase = "";
    private static volatile String clientJumpBase = "";

    private UpdateAddressState() {
    }

    /** 客户端推送当前状态（开发者模式 + 两个基址，空串表示使用系统默认） */
    public static void pushClientState(boolean devMode, String checkBase, String jumpBase) {
        DebugLogger.entering(MODULE, "pushClientState",
                "devMode=" + devMode + ", checkBase=" + checkBase + ", jumpBase=" + jumpBase);
        clientDevMode = devMode;
        clientCheckBase = (checkBase == null) ? "" : checkBase;
        clientJumpBase = (jumpBase == null) ? "" : jumpBase;
        DebugLogger.exiting(MODULE, "pushClientState", "1");
    }

    public static boolean isClientDevMode() {
        return clientDevMode;
    }

    public static String getClientCheckBase() {
        return clientCheckBase;
    }

    public static String getClientJumpBase() {
        return clientJumpBase;
    }
}
