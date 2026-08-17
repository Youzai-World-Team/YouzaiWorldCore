package top.csituka.youzaiworldcore.client.hud;

import net.minecraft.util.Mth;
import top.csituka.youzaiworldcore.client.config.YzHudComponent;
import top.csituka.youzaiworldcore.client.config.YzHudSettings;

/**
 * YZHUD 各组件共享的独立布局与颜色工具。
 */
public final class YzHudLayout {

    /** HUD 与屏幕边缘的最小间距。 */
    public static final int SCREEN_MARGIN = 2;

    private static final Geometry INVENTORY = new Geometry(
            184, 64, 2, 2, HorizontalAnchor.LEFT, VerticalAnchor.BOTTOM);
    private static final Geometry ARMOR = new Geometry(
            50, 244, 2, 70, HorizontalAnchor.LEFT, VerticalAnchor.BOTTOM);
    private static final Geometry EFFECTS = new Geometry(
            132, 264, 54, 70, HorizontalAnchor.LEFT, VerticalAnchor.BOTTOM);
    private static final Geometry SCOREBOARD = new Geometry(
            180, 221, 4, 0, HorizontalAnchor.RIGHT, VerticalAnchor.CENTER);

    private YzHudLayout() {
    }

    /** @return 指定组件的最大布局宽度 */
    public static int componentWidth(YzHudComponent component) {
        return geometry(component).width();
    }

    /** @return 指定组件的最大布局高度 */
    public static int componentHeight(YzHudComponent component) {
        return geometry(component).height();
    }

    /** @return 指定组件在当前 GUI 中的实际左边界 */
    public static int componentLeft(YzHudComponent component, int guiWidth) {
        Geometry geometry = geometry(component);
        return componentLeft(component, guiWidth, geometry.width());
    }

    /**
     * 按组件当前实际宽度取得左边界，供动态尺寸 HUD 使用。
     *
     * @param component HUD 组件
     * @param guiWidth 当前 GUI 宽度
     * @param componentWidth 组件当帧实际宽度
     * @return 应用归一化位置后的左边界
     */
    public static int componentLeft(
            YzHudComponent component, int guiWidth, int componentWidth) {
        Geometry geometry = geometry(component);
        return defaultLeft(geometry, guiWidth, componentWidth)
                + translationX(component, guiWidth, componentWidth);
    }

    /** @return 指定组件在当前 GUI 中的实际上边界 */
    public static int componentTop(YzHudComponent component, int guiHeight) {
        Geometry geometry = geometry(component);
        return componentTop(component, guiHeight, geometry.height());
    }

    /**
     * 按组件当前实际高度取得上边界，供动态尺寸 HUD 使用。
     *
     * @param component HUD 组件
     * @param guiHeight 当前 GUI 高度
     * @param componentHeight 组件当帧实际高度
     * @return 应用归一化位置后的上边界
     */
    public static int componentTop(
            YzHudComponent component, int guiHeight, int componentHeight) {
        Geometry geometry = geometry(component);
        return defaultTop(geometry, guiHeight, componentHeight)
                + translationY(component, guiHeight, componentHeight);
    }

    /** @return 相对指定组件默认位置的水平平移量 */
    public static int translationX(YzHudComponent component, int guiWidth) {
        Geometry geometry = geometry(component);
        return translationX(component, guiWidth, geometry.width());
    }

    /** @return 指定实际宽度下相对组件默认位置的水平平移量 */
    public static int translationX(
            YzHudComponent component, int guiWidth, int componentWidth) {
        Geometry geometry = geometry(component);
        int defaultLeft = defaultLeft(geometry, guiWidth, componentWidth);
        int leftDistance = Math.max(0, defaultLeft - SCREEN_MARGIN);
        int rightDistance = Math.max(0, guiWidth - SCREEN_MARGIN
                - componentWidth - defaultLeft);
        return translation(YzHudSettings.getPositionX(component),
                leftDistance, rightDistance);
    }

    /** @return 相对指定组件默认位置的垂直平移量 */
    public static int translationY(YzHudComponent component, int guiHeight) {
        Geometry geometry = geometry(component);
        return translationY(component, guiHeight, geometry.height());
    }

    /** @return 指定实际高度下相对组件默认位置的垂直平移量 */
    public static int translationY(
            YzHudComponent component, int guiHeight, int componentHeight) {
        Geometry geometry = geometry(component);
        int defaultTop = defaultTop(geometry, guiHeight, componentHeight);
        int topDistance = Math.max(0, defaultTop - SCREEN_MARGIN);
        int bottomDistance = Math.max(0, guiHeight - SCREEN_MARGIN
                - componentHeight - defaultTop);
        return translation(YzHudSettings.getPositionY(component),
                topDistance, bottomDistance);
    }

    /** 把目标左边界换算为指定组件的归一化水平位移。 */
    public static double positionXFromLeft(
            YzHudComponent component, int guiWidth, double targetLeft) {
        Geometry geometry = geometry(component);
        int defaultLeft = defaultLeft(geometry, guiWidth, geometry.width());
        int leftDistance = Math.max(0, defaultLeft - SCREEN_MARGIN);
        int rightDistance = Math.max(0, guiWidth - SCREEN_MARGIN
                - geometry.width() - defaultLeft);
        return normalizedPosition(targetLeft - defaultLeft,
                leftDistance, rightDistance);
    }

    /** 把目标上边界换算为指定组件的归一化垂直位移。 */
    public static double positionYFromTop(
            YzHudComponent component, int guiHeight, double targetTop) {
        Geometry geometry = geometry(component);
        int defaultTop = defaultTop(geometry, guiHeight, geometry.height());
        int topDistance = Math.max(0, defaultTop - SCREEN_MARGIN);
        int bottomDistance = Math.max(0, guiHeight - SCREEN_MARGIN
                - geometry.height() - defaultTop);
        return normalizedPosition(targetTop - defaultTop,
                topDistance, bottomDistance);
    }

    /** 把现有 ARGB 颜色的 Alpha 乘以 YZHUD 透明度。 */
    public static int applyOpacity(int color) {
        return applyOpacity(color, YzHudSettings.getOpacity());
    }

    /** 把现有 ARGB 颜色的 Alpha 乘以指定透明度。 */
    public static int applyOpacity(int color, float opacity) {
        int alpha = Math.round((color >>> 24) * Mth.clamp(opacity, 0.0F, 1.0F));
        return (alpha << 24) | (color & 0x00FFFFFF);
    }

    private static int translation(double position, int negativeDistance, int positiveDistance) {
        int distance = position < 0.0D ? negativeDistance : positiveDistance;
        return (int) Math.round(position * distance);
    }

    private static double normalizedPosition(
            double translation, int negativeDistance, int positiveDistance) {
        if (translation < 0.0D) {
            return negativeDistance == 0
                    ? 0.0D
                    : Mth.clamp(translation / negativeDistance, -1.0D, 0.0D);
        }
        return positiveDistance == 0
                ? 0.0D
                : Mth.clamp(translation / positiveDistance, 0.0D, 1.0D);
    }

    private static int defaultLeft(
            Geometry geometry, int guiWidth, int componentWidth) {
        return switch (geometry.horizontalAnchor()) {
            case LEFT -> geometry.horizontalOffset();
            case RIGHT -> guiWidth - geometry.horizontalOffset() - componentWidth;
        };
    }

    private static int defaultTop(
            Geometry geometry, int guiHeight, int componentHeight) {
        return switch (geometry.verticalAnchor()) {
            case BOTTOM -> guiHeight - geometry.verticalOffset() - componentHeight;
            case CENTER -> (guiHeight - componentHeight) / 2 + geometry.verticalOffset();
        };
    }

    private static Geometry geometry(YzHudComponent component) {
        return switch (component) {
            case INVENTORY -> INVENTORY;
            case ARMOR -> ARMOR;
            case EFFECTS -> EFFECTS;
            case SCOREBOARD -> SCOREBOARD;
        };
    }

    private enum HorizontalAnchor {
        LEFT,
        RIGHT
    }

    private enum VerticalAnchor {
        BOTTOM,
        CENTER
    }

    private record Geometry(
            int width, int height,
            int horizontalOffset, int verticalOffset,
            HorizontalAnchor horizontalAnchor, VerticalAnchor verticalAnchor) {
    }
}
