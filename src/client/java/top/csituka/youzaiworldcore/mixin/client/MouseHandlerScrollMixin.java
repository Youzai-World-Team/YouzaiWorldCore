package top.csituka.youzaiworldcore.mixin.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.gui.screens.TitleScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.csituka.youzaiworldcore.client.update.ClientUpdateState;
import top.csituka.youzaiworldcore.client.config.ClientUpdateCheckerConfig;
import top.csituka.youzaiworldcore.update.TitleScreenScrollState;
import top.csituka.youzaiworldcore.update.UpdateResult;
import top.csituka.youzaiworldcore.client.config.ClientExternalSettings;
import top.csituka.youzaiworldcore.client.animation.GuiAnimationController;

/**
 * 在 {@link MouseHandler#onScroll} 阶段截获滚轮事件，
 * 当当前屏幕为 TitleScreen 且显示更新信息块时处理面板滚动。
 * <p>
 * 不依赖 Screen 的 mouseScrolled 继承链（Mixin 0.8.7 无法注入 interface default 方法）。
 * 通过 {@link TitleScreenScrollState} 与 TitleScreenMixin 共享滚动状态。
 */
@Mixin(MouseHandler.class)
public class MouseHandlerScrollMixin {

    /** 右面板布局参数（与 TitleScreenMixin 保持一致） */
    private static final int PANEL_WIDTH = 170;
    private static final int PANEL_HEIGHT = 130;
    private static final int PANEL_GAP = 16;
    private static final int PANEL_Y_OFFSET = 12;

    @Inject(method = "onScroll(JDD)V", at = @At("HEAD"))
    private void youzaiworldcore$onMouseScroll(long window, double horizontal, double vertical, CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.gui.screen() instanceof TitleScreen)) return;
        if (!ClientUpdateCheckerConfig.isShowOnTitleScreen()) return;
        UpdateResult r = ClientUpdateState.get();
        if (r == null || !r.updateAvailable()) return;
        if (!r.forcedUpdate()) {
            String ignored = ClientExternalSettings.getIgnoredUpdateVersion();
            if (ignored != null && !ignored.isEmpty() && ignored.equals(r.latestVersion())) return;
        }

        // 通过 this 获取鼠标位置（注入后运行在 MouseHandler 实例中）
        MouseHandler self = (MouseHandler) (Object) this;
        com.mojang.blaze3d.platform.Window win = mc.getWindow();
        double scaledX = MouseHandler.getScaledXPos(win, self.xpos());
        double scaledY = MouseHandler.getScaledYPos(win, self.ypos())
                - GuiAnimationController.getInputYOffset(mc.gui.screen());

        // 检查鼠标是否在右面板区域内
        var screen = mc.gui.screen();
        if (screen == null) return;
        int width = screen.width;
        int height = screen.height;
        int totalGroupWidth = PANEL_WIDTH * 2 + PANEL_GAP;
        int groupStartX = (width - totalGroupWidth) / 2;
        int rightPanelX = groupStartX + PANEL_WIDTH + PANEL_GAP;
        int panelY = (height - PANEL_HEIGHT) / 2 + PANEL_Y_OFFSET;
        if (scaledX < rightPanelX || scaledX > rightPanelX + PANEL_WIDTH
                || scaledY < panelY || scaledY > panelY + PANEL_HEIGHT) return;

        // 更新滚动偏移
        double cur = TitleScreenScrollState.getScrollOffset();
        int maxScroll = TitleScreenScrollState.getMaxScroll();
        if (maxScroll <= 0) return;
        cur -= vertical * 12;
        cur = Math.max(0, Math.min(maxScroll, cur));
        TitleScreenScrollState.setScrollOffset(cur);
    }
}
