package top.csituka.youzaiworldcore.client.screen;

import top.csituka.youzaiworldcore.client.render.YzuiTheme;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import top.csituka.youzaiworldcore.client.screen.element.MainMenuElements;
import top.csituka.youzaiworldcore.client.animation.GuiAnimationController;

/**
 * 三个邮件界面的公共基类。
 * <p>
 * 统一负责两件事：
 * </p>
 * <ol>
 *   <li><b>缩放适配</b>——界面坐标一律写在 {@link MailViewport} 的设计空间里，
 *       由本类在渲染前压入缩放矩阵、在输入事件里反向换算，从而适配任意分辨率与界面尺寸。</li>
 *   <li><b>进出场动画</b>——所有动画范围使用统一页面过渡，
 *       返回/关闭时完成过渡再切屏，绘制与点击使用同一坐标变换。</li>
 * </ol>
 */
@SuppressWarnings("null")
public abstract class MailBaseScreen extends Screen {

    protected final MailViewport viewport = new MailViewport();

    protected MailBaseScreen(Component title) {
        super(title);
    }

    // ===== 渲染 =====

    @Override
    protected void init() {
        super.init();
        viewport.update(width, height);
    }

    /**
     * 绘制界面内容，坐标为设计空间坐标。
     *
     * @param mouseX 已换算到设计空间的鼠标 X
     * @param mouseY 已换算到设计空间的鼠标 Y
     */
    protected abstract void renderMailContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
                                              float partialTick);

    /**
     * 渲染原版组件树。
     * <p>子类在 {@link #renderMailContent} 内调用本方法，而不是直接调用
     * {@code super.extractRenderState}——后者会打到本类的实现上造成无限递归。</p>
     */
    protected void renderWidgets(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        viewport.update(width, height);
        viewport.setTransitionOffsetY(0f);
        int designMouseX = (int) viewport.toDesignX(mouseX);
        int designMouseY = (int) viewport.toDesignY(mouseY);
        viewport.push(graphics);
        try {
            renderMailContent(graphics, designMouseX, designMouseY, partialTick);
            if (!GuiAnimationController.isBackgroundRendering()) {
                MailToast.render(graphics, font, MailViewport.DESIGN_WIDTH);
            }
        } finally {
            viewport.pop(graphics);
        }
    }


    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        // 背景由共用屏幕入口在内容变换之前绘制，避免重复模糊与叠加遮罩。
    }

    // ===== 页面切换 =====

    /** 播放淡出动画，结束后执行 {@code action}。 */
    protected void startExit(Runnable action) {
        if (!isExiting()) action.run();
    }

    /** 返回主菜单（带过渡动画）。 */
    protected void backToMenu() {
        MailToast.clear();
        startExit(() -> Minecraft.getInstance().setScreenAndShow(new MenuScreen(new MainMenuElements())));
    }

    /** 关闭界面回到游戏（带过渡动画）。 */
    protected void closeToGame() {
        MailToast.clear();
        startExit(() -> Minecraft.getInstance().setScreenAndShow(null));
    }

    /** 切换到另一个邮件界面；由统一页面动画负责切换。 */
    protected void switchTo(Screen screen) {
        Minecraft.getInstance().setScreenAndShow(screen);
    }

    @Override
    public void onClose() {
        closeToGame();
    }

    /** 供子类的手动列表、附件与收件人选择器阻止退出期间重复操作。 */
    protected final boolean isExiting() { return GuiAnimationController.isExiting(this); }

    // ===== 输入（统一换算到设计空间） =====

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean isActuallyClick) {
        if (isExiting()) {
            return true;
        }
        return super.mouseClicked(viewport.toDesignEvent(event), isActuallyClick);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (isExiting()) return true;
        return super.mouseReleased(viewport.toDesignEvent(event));
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (isExiting()) return true;
        return super.mouseDragged(viewport.toDesignEvent(event), dragX / viewport.scale(),
                dragY / viewport.scale());
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (isExiting()) return true;
        return super.mouseScrolled(viewport.toDesignX(mouseX), viewport.toDesignY(mouseY), scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (isExiting()) {
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
