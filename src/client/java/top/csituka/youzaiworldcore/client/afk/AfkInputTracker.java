package top.csituka.youzaiworldcore.client.afk;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import top.csituka.youzaiworldcore.network.AfkHeartbeatPayload;
import top.csituka.youzaiworldcore.util.DebugLogger;

/**
 * 客户端输入追踪：每 20 tick 发送空闲时间和输入序号，所有读写均在客户端主线程。
 * 只有真实操作递增序号；重复心跳、按键释放、键盘自动重复和鼠标重新捕获不算新输入。
 */
@SuppressWarnings("null")
public final class AfkInputTracker {

    private static final String MODULE = "AfkInputTracker";

    /** 心跳发送间隔（客户端 tick）：20 tick ≈ 1 秒 */
    private static final int HEARTBEAT_INTERVAL = 20;

    /** 单个游戏 tick 的纳秒数（50ms） */
    private static final long NANOS_PER_TICK = 50_000_000L;

    /** 本项目游戏内屏幕包名前缀（Shift+F 主菜单 / 传送锚点 / 拆解台 / 邮箱 / 登录注册等） */
    private static final String MOD_SCREEN_PACKAGE = "top.csituka.youzaiworldcore.client.screen";

    /**
     * 本项目「游戏外」屏幕黑名单（全限定类名）：虽然位于 {@link #MOD_SCREEN_PACKAGE}
     * 包下，但从游戏外入口打开，操作不应算活动。
     * <ul>
     *   <li>{@code YouzaiWorldCoreSettingsScreen} — ModMenu 配置屏（暂停菜单进入）；</li>
     *   <li>{@code YzHudSettingsScreen} — YZHUD 位置与透明度编辑页；</li>
     *   <li>{@code ConfigBackupListScreen} / {@code ConfigImportSuccessScreen} —
     *       配置备份/导入链（设置屏内打开）；</li>
     *   <li>{@code QuitConfirmationScreen} — 退出确认（系统级）；</li>
     *   <li>{@code ForcedUpdateScreen} — 标题界面强制更新屏。</li>
     * </ul>
     * 新增本项目屏幕时，若其入口在游戏外，须同步加入此列表。
     */
    private static final java.util.Set<String> OUT_OF_GAME_MOD_SCREENS = java.util.Set.of(
            "top.csituka.youzaiworldcore.client.screen.YouzaiWorldCoreSettingsScreen",
            "top.csituka.youzaiworldcore.client.screen.YzHudSettingsScreen",
            "top.csituka.youzaiworldcore.client.screen.ConfigBackupListScreen",
            "top.csituka.youzaiworldcore.client.screen.ConfigImportSuccessScreen",
            "top.csituka.youzaiworldcore.client.screen.QuitConfirmationScreen",
            "top.csituka.youzaiworldcore.client.screen.ForcedUpdateScreen");

    /** 最后一次真实操作的单调时钟时间。 */
    private static long lastInputNanos = System.nanoTime();
    private static long inputSequence;

    private static int tickCounter = 0;

    private AfkInputTracker() {
    }

    /** 记录一次输入活动（由 mixin 在键盘/鼠标事件中调用） */
    public static void markInput() {
        inputSequence++;
        lastInputNanos = System.nanoTime();
    }

    /** 重置连接会话的输入基线，避免标题界面的空闲时间带入新服务器。 */
    public static void reset() {
        lastInputNanos = System.nanoTime();
        tickCounter = 0;
        inputSequence = 0;
        DebugLogger.trace(MODULE, "已重置客户端 AFK 输入基线");
    }

    /**
     * 判断当前所在界面是否属于「游戏内」活动场景。
     * <p>
     * 语义约定（需求）：只有游戏内屏幕的操作才算活动——如 Shift+F 主菜单、
     * 传送锚点、拆解台、物品栏、容器、聊天框等；游戏外屏幕（标题界面、暂停
     * 菜单、设置、ModMenu 等）的操作<b>不算</b>活动。
     * </p>
     *
     * @param screen 当前打开的屏幕（{@code null} = 游戏世界内无界面）
     * @return {@code true} = 游戏内活动场景，输入可计为活动
     */
    public static boolean isGameActivity(Screen screen) {
        // 无界面 = 游戏世界内，直接操作
        if (screen == null) {
            return true;
        }
        // 本项目「游戏外」屏幕（ModMenu 配置链 / 退出确认 / 强制更新等）→ 不算
        if (OUT_OF_GAME_MOD_SCREENS.contains(screen.getClass().getName())) {
            return false;
        }
        // 本项目游戏内屏幕（按包前缀匹配，含 .block / .element 等子包）
        if (screen.getClass().getName().startsWith(MOD_SCREEN_PACKAGE)) {
            return true;
        }
        // 原版游戏内交互界面：容器（箱子 / 拆解台 / 飞行信标…）与物品栏
        if (screen instanceof AbstractContainerScreen || screen instanceof InventoryScreen) {
            return true;
        }
        // 其余（TitleScreen / PauseScreen / OptionsScreen / ModMenu 等）→ 游戏外
        return false;
    }

    /** 消息/命令实际发送前，先同步本次输入，使服务端能建立精确的手动 AFK 基线。 */
    public static void onChatSent() {
        markInput();
        sendHeartbeat(Minecraft.getInstance());
    }

    /** GUI 打开时仍继续发送保活心跳。 */
    public static void onClientTick(Minecraft client) {
        if (++tickCounter >= HEARTBEAT_INTERVAL) {
            sendHeartbeat(client);
        }
    }

    private static void sendHeartbeat(Minecraft client) {
        tickCounter = 0;
        if (client.player == null || client.getConnection() == null
                || !ClientPlayNetworking.canSend(AfkHeartbeatPayload.ID)) {
            return;
        }
        long idleNanos = Math.max(0L, System.nanoTime() - lastInputNanos);
        int idleTicks = (int) Math.min(Integer.MAX_VALUE, idleNanos / NANOS_PER_TICK);
        ClientPlayNetworking.send(new AfkHeartbeatPayload(idleTicks, inputSequence));
        DebugLogger.trace(MODULE, "发送 AFK 心跳: idleTicks=%d, sequence=%d", idleTicks, inputSequence);
    }
}
