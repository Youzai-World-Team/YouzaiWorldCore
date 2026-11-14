package top.csituka.youzaiworldcore.client.screen;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;

/** 邮件自适应视口：普通窗口保留 GUI 字号，仅小于 320×240 的极端视口缩放。 */
final class MailViewport {

    static final int DESIGN_WIDTH = 960;
    static final int DESIGN_HEIGHT = 540;

    private int canvasWidth = DESIGN_WIDTH, canvasHeight = DESIGN_HEIGHT;
    int width() { return canvasWidth; }
    int height() { return canvasHeight; }

    private float scale = 1f;
    private float offsetX;
    private float offsetY;
    private float transitionOffsetY;

    /** 基础过渡的设计空间位移，同时用于绘制、点击与延迟提示定位。 */
    void setTransitionOffsetY(float offset) { transitionOffsetY = offset; }

    /**
     * 按当前屏幕尺寸重新计算缩放系数与居中偏移。
     *
     * @param screenWidth  屏幕宽（GUI 单位）
     * @param screenHeight 屏幕高（GUI 单位）
     */
    void update(int screenWidth, int screenHeight) {
        canvasWidth = Math.max(320, screenWidth);
        canvasHeight = Math.max(240, screenHeight);
        scale = Math.min(1f, Math.min(screenWidth / (float) canvasWidth, screenHeight / (float) canvasHeight));
        if (scale <= 0f) scale = 1f;
        offsetX = (screenWidth - canvasWidth * scale) / 2f;
        offsetY = (screenHeight - canvasHeight * scale) / 2f;
    }

    float scale() {
        return scale;
    }

    /** 压入「平移 + 缩放」矩阵，之后即可直接用设计坐标绘制。 */
    void push(GuiGraphicsExtractor graphics) {
        graphics.pose().pushMatrix();
        graphics.pose().translate(offsetX, offsetY + transitionOffsetY * scale);
        graphics.pose().scale(scale, scale);
    }

    /** 弹出矩阵，与 {@link #push} 成对使用。 */
    void pop(GuiGraphicsExtractor graphics) {
        graphics.pose().popMatrix();
    }

    /** 屏幕 X → 设计空间 X。 */
    double toDesignX(double screenX) {
        return (screenX - offsetX) / scale;
    }

    /** 屏幕 Y → 设计空间 Y。 */
    double toDesignY(double screenY) {
        return (screenY - offsetY) / scale - transitionOffsetY;
    }

    /**
     * 设计空间 X → 屏幕 X。
     * <p>用于把坐标交给缩放矩阵之外渲染的东西，典型如
     * {@code setTooltipForNextFrame}——物品提示在整帧末尾以屏幕坐标绘制。</p>
     */
    int toScreenX(double designX) {
        return (int) Math.round(designX * scale + offsetX);
    }

    /** 设计空间 Y → 屏幕 Y。 */
    int toScreenY(double designY) {
        return (int) Math.round((designY + transitionOffsetY) * scale + offsetY);
    }

    /** 复制一份坐标已换算到设计空间的鼠标事件，用于转发给原版组件。 */
    MouseButtonEvent toDesignEvent(MouseButtonEvent event) {
        return new MouseButtonEvent(toDesignX(event.x()), toDesignY(event.y()), event.buttonInfo());
    }
}
