package top.csituka.youzaiworldcore.client.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import top.csituka.youzaiworldcore.client.screen.element.MainMenuElements;

/**
 * 三个邮件界面的公共基类。
 * <p>
 * 统一负责两件事：
 * </p>
 * <ol>
 *   <li><b>缩放适配</b>——界面坐标一律写在 {@link MailViewport} 的设计空间里，
 *       由本类在渲染前压入缩放矩阵、在输入事件里反向换算，从而适配任意分辨率与界面尺寸。</li>
 *   <li><b>进出场动画</b>——与 {@code MenuScreen} 一致的缓动曲线，进入时淡入、
 *       返回/关闭时淡出后再切屏。</li>
 * </ol>
 */
@SuppressWarnings("null")
public abstract class MailBaseScreen extends Screen {

    /** 进出场动画时长（秒） */
    private static final float ANIMATION_DURATION = 0.22f;

    protected final MailViewport viewport = new MailViewport();

    private long animationStart = System.currentTimeMillis();
    private boolean exiting;
    private Runnable onExitComplete;
    /** 0=完全透明（黑屏），1=完全显示 */
    private float animationProgress;

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
        updateAnimation();

        extractBackground(graphics, mouseX, mouseY, partialTick);

        int designMouseX = (int) viewport.toDesignX(mouseX);
        int designMouseY = (int) viewport.toDesignY(mouseY);

        viewport.push(graphics);
        // 进出场的轻微缩放：幅度仅 2%，动画结束后恒为 1，
        // 故点击判定沿用未缩放的设计坐标即可（过渡期内的 1~2px 偏差不影响使用）。
        float animationScale = 0.98f + 0.02f * animationProgress;
        graphics.pose().pushMatrix();
        graphics.pose().translate(MailViewport.DESIGN_WIDTH / 2f, MailViewport.DESIGN_HEIGHT / 2f);
        graphics.pose().scale(animationScale, animationScale);
        graphics.pose().translate(-MailViewport.DESIGN_WIDTH / 2f, -MailViewport.DESIGN_HEIGHT / 2f);

        renderMailContent(graphics, designMouseX, designMouseY, partialTick);
        MailToast.render(graphics, font, MailViewport.DESIGN_WIDTH);

        graphics.pose().popMatrix();
        viewport.pop(graphics);

        // 淡入淡出遮罩（屏幕空间全屏）
        int fadeAlpha = (int) ((1f - animationProgress) * 255f);
        if (fadeAlpha > 0) {
            graphics.fill(0, 0, width, height, fadeAlpha << 24);
        }

        if (exiting && animationProgress <= 0f) {
            Runnable callback = onExitComplete;
            exiting = false;
            onExitComplete = null;
            if (callback != null) {
                callback.run();
            }
        }
    }

    private void updateAnimation() {
        float elapsed = (System.currentTimeMillis() - animationStart) / (ANIMATION_DURATION * 1000f);
        float raw = Math.max(0f, Math.min(1f, elapsed));
        animationProgress = exiting ? 1f - easeInOutCubic(raw) : easeOutCubic(raw);
    }

    private static float easeOutCubic(float t) {
        return 1f - (float) Math.pow(1f - t, 3);
    }

    private static float easeInOutCubic(float t) {
        return t < 0.5f ? 4f * t * t * t : 1f - (float) Math.pow(-2f * t + 2f, 3) / 2f;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        MailUi.drawBackdrop(graphics, width, height);
    }

    // ===== 页面切换 =====

    /** 播放淡出动画，结束后执行 {@code action}。 */
    protected void startExit(Runnable action) {
        if (exiting) {
            return;
        }
        exiting = true;
        animationStart = System.currentTimeMillis();
        onExitComplete = action;
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

    /** 切换到另一个邮件界面：不播放淡出，由目标界面自己淡入，避免两段动画叠加显得拖沓。 */
    protected void switchTo(Screen screen) {
        Minecraft.getInstance().setScreenAndShow(screen);
    }

    @Override
    public void onClose() {
        closeToGame();
    }

    // ===== 输入（统一换算到设计空间） =====

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean isActuallyClick) {
        if (exiting) {
            return true;
        }
        return super.mouseClicked(viewport.toDesignEvent(event), isActuallyClick);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        return super.mouseReleased(viewport.toDesignEvent(event));
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        return super.mouseDragged(viewport.toDesignEvent(event), dragX / viewport.scale(),
                dragY / viewport.scale());
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        return super.mouseScrolled(viewport.toDesignX(mouseX), viewport.toDesignY(mouseY), scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (exiting) {
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
