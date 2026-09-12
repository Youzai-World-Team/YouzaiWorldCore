package top.csituka.youzaiworldcore.client.animation;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import top.csituka.youzaiworldcore.client.render.YzuiTheme;

/** 自绘弹窗共用的过渡与命中坐标；淡出完成前始终保留模态状态。 */
public final class YzuiPopupAnimation {
    private boolean visible;
    private boolean exiting;
    private long startedAt;
    private int width = 1;
    private int height = 1;
    private YzuiMotion.Frame frame = YzuiMotion.Frame.IDENTITY;
    private YzuiMotion.Frame exitFrame = YzuiMotion.Frame.IDENTITY;

    public void show(int width, int height) {
        this.width = width;
        this.height = height;
        visible = true;
        exiting = false;
        startedAt = System.currentTimeMillis();
        frame = GuiAnimationController.isEnabled()
                ? YzuiMotion.enter(YzuiTheme.visualStyle(), 0f, height) : YzuiMotion.Frame.IDENTITY;
    }

    public void hide() {
        if (!visible || exiting) return;
        if (!GuiAnimationController.isEnabled()) {
            visible = false;
            return;
        }
        exiting = true;
        exitFrame = frame;
        startedAt = System.currentTimeMillis();
    }

    public boolean isVisible() { return visible; }
    public boolean acceptsInput() { return visible && !exiting && !GuiAnimationController.isBackgroundRendering(); }

    /** 每次绘制只采样一次，输入事件沿用已经显示的帧。 */
    public boolean update(int width, int height) {
        this.width = width;
        this.height = height;
        if (!visible) return false;
        if (!GuiAnimationController.isEnabled()) {
            if (exiting) visible = false;
            exiting = false;
            frame = YzuiMotion.Frame.IDENTITY;
            return visible;
        }
        var style = YzuiTheme.visualStyle();
        float progress = YzuiMotion.progress(System.currentTimeMillis() - startedAt,
                exiting ? YzuiMotion.exitDuration(style) : YzuiMotion.enterDuration(style));
        frame = exiting ? YzuiMotion.exit(style, exitFrame, progress, height)
                : GuiAnimationController.isBackgroundRendering() ? YzuiMotion.Frame.IDENTITY
                : YzuiMotion.enter(style, progress, height);
        if (exiting && progress >= 1f) {
            visible = false;
            exiting = false;
        }
        return visible;
    }

    public double toLocalX(double x) { return frame.toLocalX(x, width); }
    public double toLocalY(double y) { return frame.toLocalY(y, height); }

    public void scrim(GuiGraphicsExtractor graphics) {
        graphics.nextStratum();
        graphics.fill(0, 0, width, height, YzuiTheme.multiplyAlpha(YzuiTheme.scrim(), frame.opacity()));
    }

    public GuiAnimationController.ContentState begin(GuiGraphicsExtractor graphics) {
        graphics.pose().pushMatrix();
        graphics.pose().translate(frame.translateX(width), frame.translateY(height));
        graphics.pose().scale(frame.scale(), frame.scale());
        return GuiAnimationController.pushContent(frame);
    }

    public void end(GuiGraphicsExtractor graphics, GuiAnimationController.ContentState previous) {
        GuiAnimationController.restoreContent(previous);
        graphics.pose().popMatrix();
    }
}
