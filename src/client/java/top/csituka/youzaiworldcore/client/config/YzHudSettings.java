package top.csituka.youzaiworldcore.client.config;

import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import top.csituka.youzaiworldcore.config.ConfigSection;
import top.csituka.youzaiworldcore.client.hud.YzHudLayout;
import top.csituka.youzaiworldcore.util.DebugLogger;

import java.util.EnumMap;

/** Persistent and preview settings for individual YZHUD components. */
public final class YzHudSettings {
    private static final String MODULE = "YzHudSettings";
    private static final double DEFAULT_POSITION = 0.0D;
    private static final double DEFAULT_SCALE = 1.0D;
    private static final double DEFAULT_OPACITY = 1.0D;
    private static final boolean DEFAULT_ENABLED = true;
    private static final EnumMap<YzHudComponent, Position> POSITIONS = new EnumMap<>(YzHudComponent.class);
    private static final EnumMap<YzHudComponent, Double> SCALES = new EnumMap<>(YzHudComponent.class);
    private static final EnumMap<YzHudComponent, Double> OPACITIES = new EnumMap<>(YzHudComponent.class);
    private static final EnumMap<YzHudComponent, Boolean> ENABLED = new EnumMap<>(YzHudComponent.class);
    private static final EnumMap<YzHudComponent, Boolean> SCALE_LOCKED = new EnumMap<>(YzHudComponent.class);
    private static final EnumMap<YzHudComponent, Double> SCALE_REFERENCE = new EnumMap<>(YzHudComponent.class);
    private static final EnumMap<YzHudComponent, Position> LOCKED_PIXEL_POSITIONS = new EnumMap<>(YzHudComponent.class);
    private static final EnumMap<YzHudComponent, Position> REFERENCE_GUI_SIZES = new EnumMap<>(YzHudComponent.class);
    private static final ThreadLocal<YzHudComponent> ACTIVE_COMPONENT = new ThreadLocal<>();
    private static double opacity = DEFAULT_OPACITY;

    static { resetPreview(); }
    private YzHudSettings() { }
    public static double getPositionX(YzHudComponent c) { return POSITIONS.get(c).x(); }
    public static double getPositionY(YzHudComponent c) { return POSITIONS.get(c).y(); }
    public static float getGlobalOpacity() { return (float) opacity; }
    public static float getOpacity() { YzHudComponent c = ACTIVE_COMPONENT.get(); return c == null ? (float) opacity : getOpacity(c); }
    public static float getOpacity(YzHudComponent c) { return (float) Mth.clamp(opacity * OPACITIES.get(c), 0.0D, 1.0D); }
    public static float getComponentOpacity(YzHudComponent c) { return OPACITIES.get(c).floatValue(); }
    public static boolean isEnabled(YzHudComponent c) { return ENABLED.get(c); }
    public static double getConfiguredScale(YzHudComponent c) { return SCALES.get(c); }
    public static float getScale(YzHudComponent c) {
        double value = SCALES.get(c);
        if (SCALE_LOCKED.get(c)) {
            value *= SCALE_REFERENCE.get(c) / currentGuiScale();
            if (c == YzHudComponent.MINIMAP) {
                var window = Minecraft.getInstance().getWindow();
                Position reference = REFERENCE_GUI_SIZES.get(c);
                double referenceScale = Math.min(reference.x() / 960.0D, reference.y() / 540.0D);
                double currentScale = Math.min(window.getGuiScaledWidth() / 960.0D,
                        window.getGuiScaledHeight() / 540.0D);
                if (currentScale > 0.0D) value *= referenceScale / currentScale;
            }
        }
        return (float) Math.max(0.01D, value);
    }
    public static boolean isScaleLocked(YzHudComponent c) { return SCALE_LOCKED.get(c); }
    public static int lockedLeft(YzHudComponent c) { return (int) Math.round(LOCKED_PIXEL_POSITIONS.get(c).x() / currentGuiScale()); }
    public static int lockedTop(YzHudComponent c) { return (int) Math.round(LOCKED_PIXEL_POSITIONS.get(c).y() / currentGuiScale()); }
    public static void beginRender(YzHudComponent c) { ACTIVE_COMPONENT.set(c); }
    public static void endRender() { ACTIVE_COMPONENT.remove(); }
    public static void setPositionPreview(YzHudComponent c, double x, double y) { POSITIONS.put(c, new Position(Mth.clamp(x, -1.0D, 1.0D), Mth.clamp(y, -1.0D, 1.0D))); }
    public static void setOpacityPreview(double value) { opacity = Mth.clamp(value, 0.0D, 1.0D); }
    public static void setComponentOpacityPreview(YzHudComponent c, double value) { OPACITIES.put(c, Mth.clamp(value, 0.0D, 1.0D)); }
    public static void setScalePreview(YzHudComponent c, double value) {
        if (!SCALE_LOCKED.get(c)) {
            var window = Minecraft.getInstance().getWindow();
            setLockedPositionPreview(c, YzHudLayout.componentLeft(c, window.getGuiScaledWidth()),
                    YzHudLayout.componentTop(c, window.getGuiScaledHeight()));
        }
        SCALES.put(c, Mth.clamp(value, 0.25D, 4.0D));
        SCALE_LOCKED.put(c, true);
        SCALE_REFERENCE.put(c, currentGuiScale());
        var window = Minecraft.getInstance().getWindow();
        REFERENCE_GUI_SIZES.put(c, new Position(window.getGuiScaledWidth(), window.getGuiScaledHeight()));
    }
    public static void setLockedPositionPreview(YzHudComponent c, double left, double top) {
        LOCKED_PIXEL_POSITIONS.put(c, new Position(left * currentGuiScale(), top * currentGuiScale()));
    }
    public static void setEnabledPreview(YzHudComponent c, boolean enabled) { ENABLED.put(c, enabled); }

    public static Snapshot capture() { return new Snapshot(new EnumMap<>(POSITIONS), new EnumMap<>(SCALES), new EnumMap<>(OPACITIES), new EnumMap<>(ENABLED), new EnumMap<>(SCALE_LOCKED), new EnumMap<>(SCALE_REFERENCE), new EnumMap<>(LOCKED_PIXEL_POSITIONS), new EnumMap<>(REFERENCE_GUI_SIZES), opacity); }
    public static void restore(Snapshot s) { POSITIONS.clear(); POSITIONS.putAll(s.positions()); SCALES.clear(); SCALES.putAll(s.scales()); OPACITIES.clear(); OPACITIES.putAll(s.opacities()); ENABLED.clear(); ENABLED.putAll(s.enabled()); SCALE_LOCKED.clear(); SCALE_LOCKED.putAll(s.scaleLocked()); SCALE_REFERENCE.clear(); SCALE_REFERENCE.putAll(s.scaleReference()); LOCKED_PIXEL_POSITIONS.clear(); LOCKED_PIXEL_POSITIONS.putAll(s.lockedPixelPositions()); REFERENCE_GUI_SIZES.clear(); REFERENCE_GUI_SIZES.putAll(s.referenceGuiSizes()); opacity = s.opacity(); }

    public static void save() {
        ConfigSection section = ClientGlobalSettings.section(ClientGlobalSettings.YZHUD_MODULE);
        section.remove("position_x"); section.remove("position_y");
        for (YzHudComponent c : YzHudComponent.values()) {
            String p = c.configPrefix(); Position pos = POSITIONS.get(c);
            section.set(p + "_position_x", pos.x()); section.set(p + "_position_y", pos.y());
            section.set(p + "_scale", SCALES.get(c)); section.set(p + "_opacity", OPACITIES.get(c)); section.set(p + "_enabled", ENABLED.get(c));
            section.set(p + "_scale_locked", SCALE_LOCKED.get(c)); section.set(p + "_scale_reference", SCALE_REFERENCE.get(c));
            Position locked = LOCKED_PIXEL_POSITIONS.get(c);
            section.set(p + "_pixel_x", locked.x()); section.set(p + "_pixel_y", locked.y());
            Position reference = REFERENCE_GUI_SIZES.get(c);
            section.set(p + "_reference_gui_width", reference.x());
            section.set(p + "_reference_gui_height", reference.y());
        }
        section.set("opacity", opacity); ClientGlobalSettings.save(); DebugLogger.info(MODULE, "已保存 YZHUD 设置");
    }
    public static void reset() { resetPreview(); save(); }
    public static void resetPreview() {
        for (YzHudComponent c : YzHudComponent.values()) { POSITIONS.put(c, new Position(DEFAULT_POSITION, DEFAULT_POSITION)); SCALES.put(c, DEFAULT_SCALE); OPACITIES.put(c, DEFAULT_OPACITY); ENABLED.put(c, DEFAULT_ENABLED); SCALE_LOCKED.put(c, false); SCALE_REFERENCE.put(c, currentGuiScale()); LOCKED_PIXEL_POSITIONS.put(c, new Position(0, 0)); REFERENCE_GUI_SIZES.put(c, currentGuiSize()); }
        opacity = DEFAULT_OPACITY;
    }
    public static void load() {
        ConfigSection section = ClientGlobalSettings.section(ClientGlobalSettings.YZHUD_MODULE); if (section.isEmpty()) { save(); return; }
        for (YzHudComponent c : YzHudComponent.values()) {
            String p = c.configPrefix(); POSITIONS.put(c, new Position(section.getDouble(p + "_position_x", DEFAULT_POSITION, -1, 1), section.getDouble(p + "_position_y", DEFAULT_POSITION, -1, 1)));
            SCALES.put(c, section.getDouble(p + "_scale", DEFAULT_SCALE, 0.25, 4)); OPACITIES.put(c, section.getDouble(p + "_opacity", DEFAULT_OPACITY, 0, 1)); ENABLED.put(c, section.getBoolean(p + "_enabled", DEFAULT_ENABLED)); SCALE_LOCKED.put(c, section.getBoolean(p + "_scale_locked", false)); SCALE_REFERENCE.put(c, section.getDouble(p + "_scale_reference", currentGuiScale(), 0.1, 32));
            LOCKED_PIXEL_POSITIONS.put(c, new Position(section.getDouble(p + "_pixel_x", 0), section.getDouble(p + "_pixel_y", 0)));
            Position size = currentGuiSize();
            REFERENCE_GUI_SIZES.put(c, new Position(section.getDouble(p + "_reference_gui_width", size.x()),
                    section.getDouble(p + "_reference_gui_height", size.y())));
        }
        opacity = section.getDouble("opacity", DEFAULT_OPACITY, 0, 1);
    }
    public static void writeDefaults() { reset(); }
    private static double currentGuiScale() { try { return Math.max(0.1D, Minecraft.getInstance().getWindow().getGuiScale()); } catch (Exception ignored) { return 1.0D; } }
    private static Position currentGuiSize() { try { var window = Minecraft.getInstance().getWindow(); return new Position(window.getGuiScaledWidth(), window.getGuiScaledHeight()); } catch (Exception ignored) { return new Position(960, 540); } }
    public record Snapshot(EnumMap<YzHudComponent, Position> positions, EnumMap<YzHudComponent, Double> scales, EnumMap<YzHudComponent, Double> opacities, EnumMap<YzHudComponent, Boolean> enabled, EnumMap<YzHudComponent, Boolean> scaleLocked, EnumMap<YzHudComponent, Double> scaleReference, EnumMap<YzHudComponent, Position> lockedPixelPositions, EnumMap<YzHudComponent, Position> referenceGuiSizes, double opacity) { }
    public record Position(double x, double y) { }
}
