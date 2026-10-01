package top.csituka.youzaiworldcore.afk;

/**
 * 单个会话的 AFK 活动状态机，仅由服务端主线程访问。
 * 输入序号用于辨认新操作；时间仅用于超时，不用来推断手动切换之后是否操作过。
 * 不依赖 Minecraft，状态切换日志由 {@link AfkManager} 记录。
 */
public final class AfkActivityState {

    /** 自动检测和恢复使用相同的通道选择规则。 */
    public enum DetectMode {
        CLIENT, SERVER, BOTH
    }

    static final long HEARTBEAT_TIMEOUT_TICKS = 100;

    private long clientSequence = -1;
    private long clientActivityTick;
    private long heartbeatTick = -1;
    private long serverActivityTick;
    private long explicitActivityTick;
    private long serverSequence;
    private long explicitSequence;
    private long clientBaseline;
    private long serverBaseline;
    private long explicitBaseline;

    boolean afk;
    boolean manual;
    long afkSinceTick;

    /** 初始化会话，加入前的空闲时间不会带入本次计时。 */
    public AfkActivityState(long now) {
        explicitActivityTick = now;
        serverActivityTick = now;
    }

    /** 接收心跳；同一输入序号不刷新活动时间，避免延迟抖动伪造新操作。 */
    public void heartbeat(long now, int idleTicks, long sequence) {
        if (idleTicks < 0 || sequence < 0 || sequence < clientSequence) {
            return;
        }
        if (sequence != clientSequence) {
            // 首次接入的客户端只能建立基线，不能证明它在进入 AFK 后操作过。
            if (clientSequence < 0 && afk) {
                clientBaseline = sequence;
            }
            clientActivityTick = Math.max(0, now - idleTicks);
            clientSequence = sequence;
        }
        heartbeatTick = now;
    }

    /** 记录采样到的位置或视角变化。 */
    public void serverActivity(long now) {
        serverActivityTick = now;
        serverSequence++;
    }

    /** 记录已发送的聊天或命令；在同一 tick 内也能区分不同事件。 */
    public void explicitActivity(long now) {
        explicitActivityTick = now;
        explicitSequence++;
    }

    /** 是否有仍然有效的客户端心跳。 */
    public boolean clientAlive(long now) {
        return heartbeatTick >= 0 && now - heartbeatTick <= HEARTBEAT_TIMEOUT_TICKS;
    }

    private boolean useClient(DetectMode mode, long now) {
        return mode != DetectMode.SERVER && clientAlive(now);
    }

    /** 自动进入所采用的最后活动时间；CLIENT 无心跳时不可自动判定。 */
    public long effectiveActivityTick(DetectMode mode, long now) {
        if (mode == DetectMode.CLIENT && !clientAlive(now)) {
            return Long.MAX_VALUE;
        }
        long channelTick = useClient(mode, now) ? clientActivityTick : serverActivityTick;
        return Math.max(channelTick, explicitActivityTick);
    }

    /** 是否达到自动进入阈值，单位为服务端 tick。 */
    public boolean shouldEnter(DetectMode mode, long now, long thresholdTicks) {
        long last = effectiveActivityTick(mode, now);
        return !afk && last != Long.MAX_VALUE && now - last >= thresholdTicks;
    }

    /** 进入 AFK，快照全部活动序号，忽略切换命令及之前的输入。 */
    public void enter(long now, boolean manualEntry) {
        afk = true;
        manual = manualEntry;
        afkSinceTick = now;
        snapshot();
    }

    /** 只接受进入后的新活动；有客户端通道时被动位移不会恢复 AFK。 */
    public boolean shouldExit(DetectMode mode, long now) {
        if (!afk) {
            return false;
        }
        if (explicitSequence > explicitBaseline) {
            return true;
        }
        if (useClient(mode, now)) {
            // 丢弃客户端通道有效期间的位移，避免随后心跳超时重放旧移动。
            serverBaseline = serverSequence;
            return clientSequence > clientBaseline;
        }
        boolean useServer = mode != DetectMode.CLIENT || manual;
        return useServer && serverSequence > serverBaseline;
    }

    /** 退出并重新计时，防止下一次检测立刻重新进入。 */
    public void exit(long now) {
        afk = false;
        manual = false;
        afkSinceTick = 0;
        rebase(now);
    }

    /** 切换检测模式或重新启用功能时，更新活动基线但保留现有 AFK 状态。 */
    public void rebase(long now) {
        explicitActivityTick = now;
        serverActivityTick = now;
        snapshot();
    }

    private void snapshot() {
        clientBaseline = clientSequence;
        serverBaseline = serverSequence;
        explicitBaseline = explicitSequence;
    }

    /** 踢出从进入 AFK 开始计时，0 表示禁用。 */
    public boolean shouldKick(long now, long autoKickTicks) {
        return afk && autoKickTicks > 0 && now - afkSinceTick >= autoKickTicks;
    }
}
