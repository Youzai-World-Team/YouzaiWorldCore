package top.csituka.youzaiworldcore.mixin.client.afk;

import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.csituka.youzaiworldcore.client.afk.AfkInputTracker;

/**
 * AFK 输入检测：键盘通道（语义化活动判定）。
 * <p>
 * 在 {@link KeyboardHandler#keyPress} 与 {@link KeyboardHandler#charTyped}
 * 的 HEAD 注入，窗口聚焦时按上下文判定是否算「活动」：
 * <ul>
 *   <li><b>聊天框打开时</b>：仅回车（发送消息 / 执行指令）算活动；打字、ESC
 *       关闭均不算（符合「打开聊天框但未发送不关闭 AFK」）；</li>
 *   <li><b>其他界面</b>：排除 ESC / F5 / F2 / E（打开物品栏）/ T（打开聊天框），
 *       其余按键（WASD 移动、空格跳跃、Q 丢弃、数字键换槽等）均算活动。</li>
 * </ul>
 * keyPress 仅在按键按下 / 释放时回调（持续按住不重复触发），因此「按住 W 挂机」
 * 不会被误判为活动——配合服务端「客户端通道存活时以客户端为准」的判定，
 * 挂机脚本将正确进入 AFK。
 * </p>
 */
@Mixin(KeyboardHandler.class)
public class AfkKeyboardHandlerMixin {

    @Inject(
            method = "keyPress(JILnet/minecraft/client/input/KeyEvent;)V",
            at = @At("HEAD")
    )
    private void youzaiworldcore$onKeyPress(long window, int keyOrAction,
            KeyEvent event, CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        if (!mc.getWindow().isFocused()) {
            return;
        }
        int key = event.key();
        if (mc.gui.screen() instanceof ChatScreen) {
            // 聊天框：仅回车（发送）算活动；打字与 ESC 不算
            if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
                AfkInputTracker.markInput();
            }
            return;
        }
        // 游戏外屏幕（标题 / 暂停 / 设置 / ModMenu 等）的操作不算活动
        if (!AfkInputTracker.isGameActivity(mc.gui.screen())) {
            return;
        }
        // 非聊天界面：明确排除不算活动的键
        switch (key) {
            case GLFW.GLFW_KEY_ESCAPE: // ESC（关闭界面 / 暂停菜单）
            case GLFW.GLFW_KEY_F5:     // 视角切换
            case GLFW.GLFW_KEY_F2:     // 截图
            case GLFW.GLFW_KEY_E:      // 打开/关闭物品栏（纯打开不算）
            case GLFW.GLFW_KEY_T:      // 打开聊天框（未发送）
                return;
            default:
                AfkInputTracker.markInput();
        }
    }

    @Inject(
            method = "charTyped(JLnet/minecraft/client/input/CharacterEvent;)V",
            at = @At("HEAD")
    )
    private void youzaiworldcore$onCharTyped(long window,
            CharacterEvent event, CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        if (!mc.getWindow().isFocused()) {
            return;
        }
        if (mc.gui.screen() instanceof ChatScreen) {
            // 聊天框打字（未发送）不算活动
            return;
        }
        // 游戏外屏幕打字不算活动
        if (!AfkInputTracker.isGameActivity(mc.gui.screen())) {
            return;
        }
        // 其他输入框打字（本项目屏幕等）视为操作
        AfkInputTracker.markInput();
    }
}
