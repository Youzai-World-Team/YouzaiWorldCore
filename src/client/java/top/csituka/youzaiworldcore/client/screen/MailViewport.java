package top.csituka.youzaiworldcore.client.screen;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;

/**
 * 邮件界面的等比缩放视口。
 * <p>
 * 三个邮件界面的坐标全部按「设计空间」硬编码：{@value #DESIGN_WIDTH}×{@value #DESIGN_HEIGHT}
 * GUI 单位（即 1920×1080 分辨率 + 界面尺寸 2 时的可用区域）。本类负责把设计空间等比映射到
 * 任意真实屏幕尺寸上：渲染前压入缩放矩阵，输入事件则反向换算回设计坐标，
 * 从而在任何分辨率 / 界面尺寸下都保持相同排版，不会重叠或溢出。
 * </p>
 */
final class MailViewport {

    /** 设计基准宽度（GUI 单位） */
    static final int DESIGN_WIDTH = 960;
    /** 设计基准高度（GUI 单位） */
    static final int DESIGN_HEIGHT = 540;

    private float scale = 1f;
    private float offsetX;
    private float offsetY;

    /**
     * 按当前屏幕尺寸重新计算缩放系数与居中偏移。
     *
     * @param screenWidth  屏幕宽（GUI 单位）
     * @param screenHeight 屏幕高（GUI 单位）
     */
    void update(int screenWidth, int screenHeight) {
        scale = Math.min(screenWidth / (float) DESIGN_WIDTH, screenHeight / (float) DESIGN_HEIGHT);
        if (scale <= 0f) {
            scale = 1f;
        }
        offsetX = (screenWidth - DESIGN_WIDTH * scale) / 2f;
        offsetY = (screenHeight - DESIGN_HEIGHT * scale) / 2f;
    }

    float scale() {
        return scale;
    }

    /** 压入「平移 + 缩放」矩阵，之后即可直接用设计坐标绘制。 */
    void push(GuiGraphicsExtractor graphics) {
        graphics.pose().pushMatrix();
        graphics.pose().translate(offsetX, offsetY);
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
        return (screenY - offsetY) / scale;
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
        return (int) Math.round(designY * scale + offsetY);
    }

    /** 复制一份坐标已换算到设计空间的鼠标事件，用于转发给原版组件。 */
    MouseButtonEvent toDesignEvent(MouseButtonEvent event) {
        return new MouseButtonEvent(toDesignX(event.x()), toDesignY(event.y()), event.buttonInfo());
    }
}
