package top.csituka.youzaiworldcore.mixin.client;

import com.mojang.blaze3d.platform.Window;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.main.GameConfig;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.csituka.youzaiworldcore.client.screen.QuitConfirmationScreen;

/**
 * 拦截游戏退出行为，在所有退出路径上弹出确认对话框。
 * <p>
 * 覆盖的退出路径：
 * <ol>
 *   <li>鼠标点击窗口右上角关闭按钮（X 按钮）</li>
 *   <li>操作系统级别的窗口关闭事件（Alt+F4 等由 GLFW 转发的关闭事件）</li>
 * </ol>
 * <p>
 * 标题屏幕"退出游戏"按钮的拦截在 {@link TitleScreenMixin} 中处理。
 * <p>
 * 实现原理：
 * <ul>
 *   <li>在 Minecraft 构造后覆盖窗口关闭回调，阻止 {@code ClientShutdownWatchdog} 启动</li>
 *   <li>通过 {@link Redirect} 劫持 {@link Minecraft#runTick(boolean)} 中的
 *       {@link Window#shouldClose()} 调用</li>
 *   <li>当窗口请求关闭时，清除 GLFW 关闭标志并显示确认屏幕</li>
 *   <li>若 {@link QuitConfirmationScreen#quitConfirmed} 为 {@code true}，
 *       则放行正常退出流程</li>
 * </ul>
 */
@Mixin(Minecraft.class)
public class MinecraftQuitMixin {

    private static final Logger LOGGER = LoggerFactory.getLogger("YouzaiWorldCore/MinecraftQuitMixin");

    @Shadow
    @Final
    private Window window;

    /**
     * 在 Minecraft 构造完成后，覆盖原生的窗口关闭回调。
     * <p>
     * 原版 {@code Minecraft$1.run()} 会启动 {@code ClientShutdownWatchdog}，
     * 该看门狗在 15 秒后强制生成崩溃报告并退出进程，与我们的退出确认对话框冲突。
     * 当我们清除了 GLFW 关闭标志但看门狗已启动时，它将在 15 秒后误报崩溃。
     * <p>
     * 替换后的回调仅记录日志，实际关闭检测由 {@link #youzaiworldcore$interceptWindowClose(Window)}
     * 在 {@code runTick()} 中处理。
     */
    @Inject(method = "<init>(Lnet/minecraft/client/main/GameConfig;)V", at = @At("TAIL"))
    private void youzaiworldcore$overrideCloseCallback(GameConfig config, CallbackInfo ci) {
        this.window.setWindowCloseCallback(() -> {
            LOGGER.debug("Window close callback fired (handled by quit confirmation mixin)");
            // 不在此处启动 ClientShutdownWatchdog，
            // 由 youzaiworldcore$interceptWindowClose 统一处理
        });
    }

    /**
     * 在 {@link Minecraft#runTick(boolean)} 中劫持 {@link Window#shouldClose()} 调用，
     * 当窗口关闭事件触发且玩家尚未确认退出时，阻止退出并显示确认对话框。
     */
    @Redirect(
        method = "runTick",
        at = @At(
            value = "INVOKE",
            target = "Lcom/mojang/blaze3d/platform/Window;shouldClose()Z"
        )
    )
    private boolean youzaiworldcore$interceptWindowClose(Window window) {
        // 检查 GLFW 窗口是否请求关闭（用户点击了 X 按钮或 Alt+F4 等）
        boolean shouldClose = window.shouldClose();

        if (shouldClose) {
            // 如果已经确认退出（来自 QuitConfirmationScreen.onConfirmQuit），直接放行
            if (QuitConfirmationScreen.quitConfirmed) {
                LOGGER.debug("Window close confirmed, allowing exit");
                return true;
            }

            LOGGER.info("Window close requested, showing confirmation dialog");

            // 清除 GLFW 关闭标志，防止游戏主循环退出
            GLFW.glfwSetWindowShouldClose(window.handle(), false);

            // 获取当前显示的屏幕
            Minecraft mc = Minecraft.getInstance();
            Screen currentScreen = mc.gui.screen();

            // 如果当前屏幕已经是 QuitConfirmationScreen（例如用户连点 X），
            // 则不叠加第二个对话框，直接放行退出
            if (currentScreen instanceof QuitConfirmationScreen) {
                LOGGER.debug("QuitConfirmationScreen already showing, allowing direct close");
                return true;
            }

            // 所有页面/场景下都弹出确认对话框：
            LOGGER.debug("Showing quit confirmation, current screen: {}",
                    currentScreen != null ? currentScreen.getClass().getSimpleName() : "null");
            mc.gui.setScreen(new QuitConfirmationScreen());

            return false; // 阻止原 shouldClose() 返回 true，游戏继续运行
        }

        return false; // 窗口未请求关闭
    }
}
