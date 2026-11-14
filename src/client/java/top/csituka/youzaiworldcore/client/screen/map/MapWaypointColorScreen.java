package top.csituka.youzaiworldcore.client.screen.map;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import top.csituka.youzaiworldcore.client.map.MapTexts;
import top.csituka.youzaiworldcore.client.map.MapWaypointColors;
import top.csituka.youzaiworldcore.client.render.YzuiTheme;
import top.csituka.youzaiworldcore.client.screen.widget.TransparentButton;
import top.csituka.youzaiworldcore.util.DebugLogger;

import java.util.function.IntConsumer;

/** 点击颜色编辑入口后打开的色图；只修改调用方草稿，路径点保存仍由原表单负责。 */
@SuppressWarnings("null")
final class MapWaypointColorScreen extends MapScreen {
    private final IntConsumer changed;
    private int color;
    private float hue, saturation, value;
    private String text;
    private boolean updatingText;
    private EditBox input;
    private TransparentButton done;

    MapWaypointColorScreen(Screen parent, int color, IntConsumer changed) {
        super(parent, "color");
        this.changed = changed;
        setColor(color);
        text = MapWaypointColors.hex(color);
        DebugLogger.debug("WaypointColor", "打开路径点色图：#%s", text);
    }

    @Override protected void init() {
        super.init();
        panelWidth = Math.min(400, width - 16);
        panelHeight = Math.min(320, height - 16);
        panelX = (width - panelWidth) / 2;
        panelY = (height - panelHeight) / 2;
        int x = panelX + 16, w = panelWidth - 32;
        input = field(x + 40, panelY + 40, w - 40, "color", text, 7, this::readText);
        int chartY = panelY + 72, chartHeight = Math.max(16, panelHeight - 140);
        var shade = addRenderableWidget(new ColorChart(x, chartY, Math.max(16, w - 28), chartHeight, false));
        addRenderableWidget(new ColorChart(x + w - 16, chartY, 16, chartHeight, true));
        int buttonWidth = (w - 6) / 2, footer = panelY + panelHeight - 34;
        done = button(x, footer, buttonWidth, MapTexts.text("done"), () -> {
            changed.accept(color);
            DebugLogger.debug("WaypointColor", "确认路径点颜色草稿：#%s", MapWaypointColors.hex(color));
            onClose();
        }).setStyle(YzuiTheme.ButtonStyle.FILLED);
        button(x + buttonWidth + 6, footer, buttonWidth, MapTexts.text("cancel"), this::onClose);
        done.active = error.getString().isEmpty();
        setFocused(shade);
    }

    /** 从 RGB 恢复选择位置；无彩色保留色相，打开或确认不改变原有 RGB 精度。 */
    private void setColor(int selected) {
        color = selected | 0xFF000000;
        float r = ((color >> 16) & 255) / 255.0F;
        float g = ((color >> 8) & 255) / 255.0F;
        float b = (color & 255) / 255.0F;
        float max = Math.max(r, Math.max(g, b)), min = Math.min(r, Math.min(g, b)), delta = max - min;
        value = max;
        saturation = max == 0.0F ? 0.0F : delta / max;
        if (delta > 0.0F) {
            float sector = max == r ? (g - b) / delta : max == g ? (b - r) / delta + 2 : (r - g) / delta + 4;
            hue = (sector / 6.0F + 1.0F) % 1.0F;
        }
    }

    private void readText(String draft) {
        text = draft;
        if (updatingText) return;
        try {
            setColor(MapWaypointColors.parse(draft));
            error = Component.empty();
            if (done != null) done.active = true;
        } catch (IllegalArgumentException invalid) {
            error = MapTexts.text("invalid_color");
            if (done != null) done.active = false;
        }
    }

    /** 拖动时同步预览和 RGB 输入，不让文本回调重置黑白处正在选择的色相。 */
    private void chartChanged() {
        color = rgb(hue, saturation, value);
        text = MapWaypointColors.hex(color);
        updatingText = true;
        try { input.setValue(text); } finally { updatingText = false; }
        error = Component.empty();
        done.active = true;
    }

    /** 色相条的首尾都表示红色；原版转换函数要求色相小于 1。 */
    private static int rgb(float hue, float saturation, float value) {
        return Mth.hsvToArgb(hue % 1.0F, saturation, value, 255);
    }

    @Override protected void content(GuiGraphicsExtractor g, int mx, int my, float delta) {
        MapWaypointColors.render(g, color, panelX + 30, panelY + 50, 1.0F);
        label(g, Component.literal("#"), panelX + 44, panelY + 46, 12);
        if (error.getString().isEmpty())
            label(g, MapTexts.text("color_picker_hint"), panelX + 16, panelY + panelHeight - 58, panelWidth - 32);
    }

    /** 色相条与饱和度／明度色图使用原版控件命中、拖动及焦点分发。 */
    private final class ColorChart extends AbstractWidget {
        private final boolean hueStrip;

        ColorChart(int x, int y, int width, int height, boolean hueStrip) {
            super(x, y, width, height, MapTexts.text(hueStrip ? "color_hue" : "color_shade"));
            this.hueStrip = hueStrip;
        }

        @Override protected void extractWidgetRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
            int x = getX(), y = getY(), w = getWidth(), h = getHeight();
            if (hueStrip) {
                for (int row = 0; row < h; row++)
                    g.fill(x, y + row, x + w, y + row + 1,
                            rgb((float) row / Math.max(1, h - 1), 1.0F, 1.0F));
                int selectedY = y + Math.round(hue * (h - 1));
                g.fill(x - 2, selectedY - 2, x + w + 2, selectedY + 3, 0xFF000000);
                g.outline(x - 1, selectedY - 1, w + 2, 3, 0xFFFFFFFF);
            } else {
                for (int column = 0; column < w; column++)
                    g.fillGradient(x + column, y, x + column + 1, y + h,
                            rgb(hue, (float) column / Math.max(1, w - 1), 1.0F), 0xFF000000);
                int selectedX = x + Math.round(saturation * (w - 1));
                int selectedY = y + Math.round((1.0F - value) * (h - 1));
                g.outline(selectedX - 4, selectedY - 4, 9, 9, 0xFF000000);
                g.outline(selectedX - 3, selectedY - 3, 7, 7, 0xFFFFFFFF);
            }
            g.outline(x - 1, y - 1, w + 2, h + 2, isFocused() ? YzuiTheme.primary() : YzuiTheme.outlineVariant());
        }

        private void select(double mx, double my) {
            if (!active || !visible) return;
            float vertical = (float) Math.clamp((my - getY()) / Math.max(1, getHeight() - 1), 0.0, 1.0);
            if (hueStrip) hue = vertical;
            else {
                saturation = (float) Math.clamp((mx - getX()) / Math.max(1, getWidth() - 1), 0.0, 1.0);
                value = 1.0F - vertical;
            }
            chartChanged();
        }

        @Override public void onClick(MouseButtonEvent event, boolean actual) { select(event.x(), event.y()); }
        @Override protected void onDrag(MouseButtonEvent event, double dx, double dy) { select(event.x(), event.y()); }

        @Override public boolean keyPressed(KeyEvent event) {
            if (!active || !visible || !isFocused()) return false;
            int key = event.key();
            float step = 0.01F;
            if (hueStrip) {
                if (key != InputConstants.KEY_UP && key != InputConstants.KEY_DOWN) return false;
                hue = Math.clamp(hue + (key == InputConstants.KEY_DOWN ? step : -step), 0.0F, 1.0F);
            } else {
                if (key == InputConstants.KEY_LEFT || key == InputConstants.KEY_RIGHT)
                    saturation = Math.clamp(saturation + (key == InputConstants.KEY_RIGHT ? step : -step), 0.0F, 1.0F);
                else if (key == InputConstants.KEY_UP || key == InputConstants.KEY_DOWN)
                    value = Math.clamp(value + (key == InputConstants.KEY_UP ? step : -step), 0.0F, 1.0F);
                else return false;
            }
            chartChanged();
            return true;
        }

        @Override protected void updateWidgetNarration(NarrationElementOutput output) {
            output.add(NarratedElementType.TITLE, getMessage());
            output.add(NarratedElementType.USAGE, MapTexts.text("color_picker_keys"));
            output.add(NarratedElementType.HINT, Component.literal("#" + MapWaypointColors.hex(color)));
        }
    }
}
