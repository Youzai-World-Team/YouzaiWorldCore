package top.csituka.youzaiworldcore.client.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import top.csituka.youzaiworldcore.client.animation.GuiAnimationController;
import top.csituka.youzaiworldcore.client.screen.YzuiPopupScreen;

/** 绘制世界/原版全景图以及弹窗后面的页面，不重建父页面，也不转发父页面的输入。 */
public final class YzuiBackdrop {
    private static final YzuiLayerHistory<Screen> HISTORY = new YzuiLayerHistory<>();
    private static Object scene;

    private YzuiBackdrop() { }

    /** 只为明确声明的弹窗保留父页面，不能按自定义屏幕或类名推断。 */
    public static boolean isPopup(Screen screen) {
        return screen instanceof YzuiPopupScreen popup && popup.isPopup();
    }

    public static boolean isReturning(Screen previous, Screen next) {
        return next != null && HISTORY.parents(previous).stream()
                .anyMatch(parent -> parent == next);
    }

    public static boolean willLayer(Screen previous, Screen next) {
        return previous != null && previous != next && isPopup(next) && !isReturning(previous, next);
    }

    public static Screen parent(Screen screen) { return HISTORY.parent(screen); }

    public static void onScreenChanged(Screen previous, Screen next) {
        Object nextScene = Minecraft.getInstance().level;
        if (scene != nextScene) {
            HISTORY.clear();
            previous = null;
            scene = nextScene;
        }
        // 普通页面开始新的背景层；同类弹窗也可能嵌套，返回必须按窗口实例识别。
        HISTORY.change(previous, next, isPopup(next), (first, second) -> first == second);
    }

    /** 调用方已经恢复物理 GUI 坐标；遮罩和全景图不参与卡片的位移与缩放。 */
    public static void render(GuiGraphicsExtractor g, Screen screen, float partialTick) {
        Minecraft client = Minecraft.getInstance();
        if (scene != client.level) {
            HISTORY.clear();
            scene = client.level;
        }
        if (client.level == null) {
            client.gameRenderer.panorama().extractRenderState(g, g.guiWidth(), g.guiHeight());
        }
        if (isPopup(screen)) {
            for (Screen parent : HISTORY.parents(screen)) {
                if (parent.width <= 0 || parent.height <= 0) continue;
                if (client.player == null && parent instanceof AbstractContainerScreen<?>) continue;
                renderParent(g, parent, partialTick);
            }
        }
        g.nextStratum();
        if (YzuiTheme.frosted()) g.blurBeforeThisStratum();
        g.fill(0, 0, g.guiWidth(), g.guiHeight(),
                YzuiTheme.multiplyAlpha(YzuiTheme.scrim(), GuiAnimationController.getScreenOpacity()));
    }

    private static void renderParent(GuiGraphicsExtractor g, Screen parent, float partialTick) {
        int width = g.guiWidth(), height = g.guiHeight();
        float scale = Math.min(width / (float) parent.width, height / (float) parent.height);
        g.nextStratum();
        g.pose().pushMatrix();
        g.pose().translate((width - parent.width * scale) / 2f, (height - parent.height * scale) / 2f);
        g.pose().scale(scale, scale);
        Screen previousViewport = YzuiViewport.beginRendering(parent);
        var previousContent = GuiAnimationController.suspendContent(true);
        try {
            YzuiTheme.screenCard(g, parent);
            parent.extractRenderState(g, -10000, -10000, partialTick);
        } finally {
            GuiAnimationController.restoreContent(previousContent);
            YzuiViewport.beginRendering(previousViewport);
            g.pose().popMatrix();
        }
    }
}
