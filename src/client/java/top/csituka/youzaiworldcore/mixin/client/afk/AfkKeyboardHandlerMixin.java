package top.csituka.youzaiworldcore.mixin.client.afk;

import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.input.KeyEvent;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.csituka.youzaiworldcore.client.afk.AfkInputTracker;

/**
 * 仅真实按下事件计入 AFK 活动，释放及 GLFW_REPEAT 均忽略。
 * 不再监听 charTyped，避免字符重复和打开物品栏后的字符回调绕过按键过滤。
 * 聊天框打字不算活动，实际发送由 AfkChatSendMixin 记录。
 */
@Mixin(KeyboardHandler.class)
public class AfkKeyboardHandlerMixin {
    @Inject(method = "keyPress(JILnet/minecraft/client/input/KeyEvent;)V", at = @At("HEAD"))
    private void youzaiworldcore$onKeyPress(long window, int action, KeyEvent event, CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        if (action != GLFW.GLFW_PRESS || window != mc.getWindow().handle()
                || !mc.getWindow().isFocused() || mc.player == null) {
            return;
        }
        var screen = mc.gui.screen();
        if (screen instanceof ChatScreen || !AfkInputTracker.isGameActivity(screen)) {
            return;
        }
        var options = mc.options;
        if (event.key() == GLFW.GLFW_KEY_ESCAPE
                || options.keyScreenshot.matches(event) || options.keyTogglePerspective.matches(event)) {
            return;
        }
        // 按当前键位绑定判断；游戏内输入框中的同名字符仍然可以计入操作。
        if (screen == null && (options.keyInventory.matches(event)
                || options.keyChat.matches(event) || options.keyCommand.matches(event))) {
            return;
        }
        if (screen instanceof InventoryScreen && options.keyInventory.matches(event)) {
            return;
        }
        AfkInputTracker.markInput();
    }
}
