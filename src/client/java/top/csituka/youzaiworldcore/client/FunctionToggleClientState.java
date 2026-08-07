package top.csituka.youzaiworldcore.client;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 客户端功能开关状态存储。
 * 接收服务端 FunctionToggleSyncPayload 后更新。
 * 3 个客户端渲染功能（ToolInfoOverlay、BlockAnimationRenderer、ItemSparkleRenderer）
 * 通过此类查询开关状态。
 */
public final class FunctionToggleClientState {

    /** 功能键 → 开关状态，默认 true */
    private static final Map<String, Boolean> STATE = new ConcurrentHashMap<>();

    private FunctionToggleClientState() {}

    public static void update(Map<String, Boolean> newState) {
        STATE.clear();
        STATE.putAll(newState);
    }

    public static boolean isEnabled(String key) {
        return STATE.getOrDefault(key, true);
    }
}
