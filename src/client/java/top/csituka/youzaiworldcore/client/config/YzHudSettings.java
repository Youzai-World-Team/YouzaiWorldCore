package top.csituka.youzaiworldcore.client.config;

import net.minecraft.util.Mth;
import top.csituka.youzaiworldcore.config.ConfigSection;
import top.csituka.youzaiworldcore.util.DebugLogger;

import java.util.EnumMap;

/**
 * YZHUD 客户端显示设置。
 *
 * <p>配置存放于 {@code yzwc/client/global_settings.json} 的
 * {@code yzhud_module} 分节。三个组件分别使用 -1..1 的归一化位移，
 * {@code 0} 保持组件原有默认位置，避免窗口尺寸或 GUI 缩放变化后跑出屏幕。</p>
 */
public final class YzHudSettings {

    private static final String MODULE = "YzHudSettings";

    private static final double DEFAULT_POSITION = 0.0D;
    private static final double DEFAULT_OPACITY = 1.0D;

    private static final EnumMap<YzHudComponent, Position> POSITIONS =
            new EnumMap<>(YzHudComponent.class);
    private static double opacity = DEFAULT_OPACITY;

    static {
        resetPositions();
    }

    private YzHudSettings() {
    }

    /**
     * @param component HUD 组件
     * @return 水平归一化位移，范围 -1..1，0 为默认位置
     */
    public static double getPositionX(YzHudComponent component) {
        return POSITIONS.get(component).x();
    }

    /**
     * @param component HUD 组件
     * @return 垂直归一化位移，范围 -1..1，0 为默认位置
     */
    public static double getPositionY(YzHudComponent component) {
        return POSITIONS.get(component).y();
    }

    /** @return YZHUD 透明度，范围 0..1 */
    public static float getOpacity() {
        return (float) opacity;
    }

    /** 更新指定组件的运行时位置，不立即写盘，供拖拽预览使用。 */
    public static void setPositionPreview(YzHudComponent component, double x, double y) {
        POSITIONS.put(component, new Position(
                Mth.clamp(x, -1.0D, 1.0D),
                Mth.clamp(y, -1.0D, 1.0D)));
    }

    /** 更新运行时透明度，不立即写盘，供滑块预览使用。 */
    public static void setOpacityPreview(double value) {
        opacity = Mth.clamp(value, 0.0D, 1.0D);
    }

    /** 保存当前运行时设置。 */
    public static void save() {
        ConfigSection section = ClientGlobalSettings.section(ClientGlobalSettings.YZHUD_MODULE);
        section.remove("position_x");
        section.remove("position_y");
        for (YzHudComponent component : YzHudComponent.values()) {
            Position position = POSITIONS.get(component);
            section.set(component.configPrefix() + "_position_x", position.x());
            section.set(component.configPrefix() + "_position_y", position.y());
        }
        section.set("opacity", opacity);
        ClientGlobalSettings.save();
        DebugLogger.info(MODULE,
                "已保存 YZHUD 设置: inventory=(%.3f, %.3f), armor=(%.3f, %.3f), "
                        + "effects=(%.3f, %.3f), opacity=%.2f",
                getPositionX(YzHudComponent.INVENTORY), getPositionY(YzHudComponent.INVENTORY),
                getPositionX(YzHudComponent.ARMOR), getPositionY(YzHudComponent.ARMOR),
                getPositionX(YzHudComponent.EFFECTS), getPositionY(YzHudComponent.EFFECTS),
                opacity);
    }

    /** 恢复默认位置和透明度并立即保存。 */
    public static void reset() {
        resetPositions();
        opacity = DEFAULT_OPACITY;
        save();
    }

    /** 从客户端配置的 {@code yzhud_module} 分节加载。 */
    public static void load() {
        ConfigSection section = ClientGlobalSettings.section(ClientGlobalSettings.YZHUD_MODULE);
        if (section.isEmpty()) {
            save();
            return;
        }

        for (YzHudComponent component : YzHudComponent.values()) {
            String prefix = component.configPrefix();
            POSITIONS.put(component, new Position(
                    section.getDouble(prefix + "_position_x", DEFAULT_POSITION, -1.0D, 1.0D),
                    section.getDouble(prefix + "_position_y", DEFAULT_POSITION, -1.0D, 1.0D)));
        }
        opacity = section.getDouble("opacity", DEFAULT_OPACITY, 0.0D, 1.0D);
        DebugLogger.info(MODULE,
                "已加载 YZHUD 设置: inventory=(%.3f, %.3f), armor=(%.3f, %.3f), "
                        + "effects=(%.3f, %.3f), opacity=%.2f",
                getPositionX(YzHudComponent.INVENTORY), getPositionY(YzHudComponent.INVENTORY),
                getPositionX(YzHudComponent.ARMOR), getPositionY(YzHudComponent.ARMOR),
                getPositionX(YzHudComponent.EFFECTS), getPositionY(YzHudComponent.EFFECTS),
                opacity);
    }

    /** 写入完整默认值，供首次安装和坏配置恢复使用。 */
    public static void writeDefaults() {
        resetPositions();
        opacity = DEFAULT_OPACITY;
        save();
    }

    private static void resetPositions() {
        for (YzHudComponent component : YzHudComponent.values()) {
            POSITIONS.put(component, new Position(DEFAULT_POSITION, DEFAULT_POSITION));
        }
    }

    private record Position(double x, double y) {
    }
}
