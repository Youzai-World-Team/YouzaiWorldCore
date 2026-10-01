package top.csituka.youzaiworldcore.client.screen;

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

    /** 在动画、下拉框和局部弹窗分发之前处理 Esc；受限页面保留原有流程。 */
    static boolean handleEscape(Screen screen, KeyEvent event) {
        if (event.key() != 256 || !(screen instanceof YzuiMenuScreen menu)
                || !menu.usesMenuOverlay() || !menu.canCloseMenu()) return false;
        menu.closeMenu();
        return true;
    }
}
