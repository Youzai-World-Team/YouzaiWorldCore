package top.csituka.youzaiworldcore.client.screen;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import top.csituka.youzaiworldcore.client.animation.GuiAnimationController;
import top.csituka.youzaiworldcore.util.DebugLogger;

/** 游戏菜单与账户页面的通透外壳约定；关闭权限始终由当前页面决定。 */
public interface YzuiMenuScreen {
    default boolean usesMenuOverlay() { return true; }

    default boolean canCloseMenu() { return ((Screen) this).shouldCloseOnEsc(); }

    /** 用户主动关闭时直接回到游戏，取消尚未提交的页面过渡。 */
    default void closeMenu() {
        if (!canCloseMenu()) return;
        DebugLogger.debug("YzuiMenu", "关闭菜单：%s", getClass().getSimpleName());
        GuiAnimationController.setScreenImmediately(Minecraft.getInstance().gui, null);
    }

    /** Esc 委托页面逐层返回；右上角关闭按钮仍明确退出到游戏。 */
    static boolean handleEscape(Screen screen, KeyEvent event) {
        if (event.key() != InputConstants.KEY_ESCAPE || !(screen instanceof YzuiMenuScreen menu)
                || !menu.usesMenuOverlay() || !menu.canCloseMenu()) return false;
        screen.onClose();
        return true;
    }
}
