package top.csituka.youzaiworldcore.client.afk;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import top.csituka.youzaiworldcore.network.AfkHeartbeatPayload;
import top.csituka.youzaiworldcore.util.DebugLogger;

/**
 * 客户端 AFK 输入追踪器。
 * <p>
 * 由 mixin（{@code AfkKeyboardHandlerMixin} / {@code AfkMouseHandlerMixin}）
 * 在每次键盘 / 鼠标输入时调用 {@link #markInput()} 刷新最后输入时间；
 * 本类在客户端 tick 中每 20 tick（约 1 秒）向服务端发送
 * {@link AfkHeartbeatPayload}，携带「距最后输入的 tick 差值」（单调时钟差值，
 * 与服务端时钟无同步依赖）。服务端据此维护客户端精确检测通道的活动时间。
 * </p>
 * <p>
 * 窗口失焦时 GLFW 输入回调不触发 → 不刷新最后输入时间 → 心跳差值持续增长 →
 * 服务端判定 AFK，符合「人离开电脑即 AFK」的语义。</p>
 */
@SuppressWarnings("null")
public final class AfkInputTracker {

    private static final String MODULE = "AfkInputTracker";

    /** 心跳发送间隔（客户端 tick）：20 tick ≈ 1 秒 */
    private static final int HEARTBEAT_INTERVAL = 20;

    /** 单个游戏 tick 的纳秒数（50ms） */
    private static final long NANOS_PER_TICK = 50_000_000L;

    /** 最后输入时间（nanoTime 单调时钟，跨线程可见） */
    private static volatile long lastInputNanos = System.nanoTime();

    private static int tickCounter = 0;

    private AfkInputTracker() {
    }

    /** 记录一次输入活动（由 mixin 在键盘/鼠标事件中调用） */
    public static void markInput() {
        lastInputNanos = System.nanoTime();
    }

    /**
     * 客户端 tick 驱动：节流发送心跳包。
     * <p>
     * 需在 {@code client.player != null} 时发送；连接断开（返回标题界面）时
     * 静默跳过。应放在客户端主 tick 处理器最前部调用（不受 GUI 打开早退影响）。</p>
     *
     * @param client 客户端实例
     */
    public static void onClientTick(Minecraft client) {
        tickCounter++;
        if (tickCounter < HEARTBEAT_INTERVAL) {
            return;
        }
        tickCounter = 0;

        if (client.player == null || client.getConnection() == null) {
            return;
        }

        long idleNanos = System.nanoTime() - lastInputNanos;
        int idleTicks = (int) (idleNanos / NANOS_PER_TICK);
        if (idleTicks < 0) {
            idleTicks = 0;
        }
        ClientPlayNetworking.send(new AfkHeartbeatPayload(idleTicks));
        DebugLogger.trace(MODULE, "发送 AFK 心跳: idleTicks=%d", idleTicks);
    }
}
