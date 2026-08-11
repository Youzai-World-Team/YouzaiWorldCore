package top.csituka.youzaiworldcore.client;

import net.minecraft.network.chat.Component;

/** 原地重生死亡界面的客户端临时状态。 */
public final class InPlaceRespawnClientState {

    private static boolean enabled;
    private static int requiredLevel;
    private static int currentLevel;
    private static boolean pending;
    private static boolean reactivateButtons;
    private static String failureReason = "";

    private InPlaceRespawnClientState() {
    }

    /** 接收一次新的死亡信息，并清空上次申请状态。 */
    public static void updateInfo(boolean newEnabled, int newRequiredLevel) {
        enabled = newEnabled;
        requiredLevel = Math.max(0, newRequiredLevel);
        currentLevel = 0;
        pending = false;
        reactivateButtons = false;
        failureReason = "";
    }

    /** 标记申请正在等待服务端审批。 */
    public static boolean beginRequest() {
        if (!enabled || pending) {
            return false;
        }
        pending = true;
        reactivateButtons = false;
        failureReason = "";
        return true;
    }

    /** 应用服务端拒绝结果。批准结果由网络处理器直接触发原版重生指令。 */
    public static void applyRejection(String reason, int newRequiredLevel, int newCurrentLevel) {
        pending = false;
        reactivateButtons = true;
        failureReason = reason;
        requiredLevel = Math.max(0, newRequiredLevel);
        currentLevel = Math.max(0, newCurrentLevel);
    }

    public static boolean isEnabled() {
        return enabled;
    }

    public static boolean isPending() {
        return pending;
    }

    /** 返回并消费一次“重新启用死亡界面按钮”的请求。 */
    public static boolean consumeButtonReactivation() {
        boolean result = reactivateButtons;
        reactivateButtons = false;
        return result;
    }

    /** 获取按钮上方的费用或失败提示。 */
    public static Component getStatusText() {
        if (pending) {
            return Component.translatable("youzaiworldcore.respawn.in_place.pending");
        }
        if ("not_enough_levels".equals(failureReason)) {
            return Component.translatable("youzaiworldcore.respawn.in_place.not_enough_levels",
                    requiredLevel, currentLevel);
        }
        if (!failureReason.isEmpty()) {
            return Component.translatable("youzaiworldcore.respawn.in_place.unavailable");
        }
        return Component.translatable("youzaiworldcore.respawn.in_place.cost", requiredLevel);
    }
}
