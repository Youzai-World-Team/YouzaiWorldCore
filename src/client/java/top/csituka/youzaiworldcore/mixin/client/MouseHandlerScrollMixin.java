package top.csituka.youzaiworldcore.mixin.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.gui.screens.TitleScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.csituka.youzaiworldcore.client.animation.GuiAnimationController;
import top.csituka.youzaiworldcore.update.TitleScreenScrollState;

/** 标题页公告与更新日志滚动；直接使用绘制发布的正文视口，避免布局漂移。 */
@Mixin(MouseHandler.class)
public class MouseHandlerScrollMixin {
    @Inject(method = "onScroll(JDD)V", at = @At("HEAD"), cancellable = true)
    private void youzaiworldcore$onMouseScroll(long window, double horizontal, double vertical, CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui.overlay() != null || !(mc.gui.screen() instanceof TitleScreen screen) || GuiAnimationController.isExiting(screen)) return;
        MouseHandler self = (MouseHandler) (Object) this;
        var win = mc.getWindow();
        var frame = GuiAnimationController.inputFrame(screen);
        double x = frame.toLocalX(MouseHandler.getScaledXPos(win, self.xpos()), screen.width);
        double y = frame.toLocalY(MouseHandler.getScaledYPos(win, self.ypos()), screen.height);
        if (vertical == 0 || !TitleScreenScrollState.contains(x, y) || TitleScreenScrollState.getMaxScroll() <= 0) return;
        TitleScreenScrollState.setScrollOffset(TitleScreenScrollState.getScrollOffset() - vertical * 16);
        ci.cancel();
    }
}
