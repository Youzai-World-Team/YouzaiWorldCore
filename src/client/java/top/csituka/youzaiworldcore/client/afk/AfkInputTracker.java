package top.csituka.youzaiworldcore.client.afk;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
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
