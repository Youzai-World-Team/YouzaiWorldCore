package top.csituka.youzaiworldcore.client.screen.map;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.events.GuiEventListener;
import top.csituka.youzaiworldcore.client.animation.GuiAnimationController;
import top.csituka.youzaiworldcore.client.animation.YzuiMotion;
import top.csituka.youzaiworldcore.client.render.YzuiTheme;
import top.csituka.youzaiworldcore.util.DebugLogger;

import java.util.ArrayList;
import java.util.List;

/** 地图覆盖页的淡入、淡出与交叉渐变；退出页只绘制和遮挡，不再接收操作。 */
final class MapPanelAnimation {
    private final List<Entry> exiting = new ArrayList<>();
    private Entry current;
    private int width, height;

    /** 同一页面重新布局或刷新时保留进度，窗口尺寸变化时丢弃旧尺寸的退出页。 */
    void layout(int width, int height) {
        if (this.width != width || this.height != height) exiting.clear();
        this.width = width;
        this.height = height;
    }

    <T extends GuiEventListener & Renderable> void show(T panel) {
        if (current != null && current.panel == panel) return;
        if (current != null) hide(current.panel);
        current = new Entry(panel, panel);
        DebugLogger.debug("WorldMap", "覆盖页淡入：%s", panel.getClass().getSimpleName());
    }

    void hide(GuiEventListener panel) {
        if (current == null || current.panel != panel) return;
        panel.setFocused(false);
        if (GuiAnimationController.isEnabled() && current.opacity > 0f) {
            current.closing = true;
            current.from = current.opacity;
            current.startedAt = System.currentTimeMillis();
            exiting.add(current);
        }
        current = null;
        DebugLogger.debug("WorldMap", "覆盖页淡出：%s", panel.getClass().getSimpleName());
    }

    /** 绘制前采样一次，命中判断沿用当前可见帧，避免淡出未画完就提前放行鼠标。 */
    void update() {
        long now = System.currentTimeMillis();
        if (current != null) current.update(now);
        exiting.removeIf(entry -> { entry.update(now); return entry.opacity <= 0f; });
    }

    boolean isClosingOver(double x, double y) {
        return GuiAnimationController.isEnabled() && exiting.stream().anyMatch(entry -> entry.panel.isMouseOver(x, y));
    }

    boolean coversClosing(AbstractWidget widget) {
        return GuiAnimationController.isEnabled() && exiting.stream()
                .anyMatch(entry -> entry.panel.getRectangle().intersects(widget.getRectangle()));
    }

    void draw(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        for (var entry : exiting) entry.draw(graphics, -100, -100, delta);
        if (current != null) current.draw(graphics, mouseX, mouseY, delta);
    }

    /** 离开地图时结束动效；地图作为子页面背景保留时仍能绘制当前面板。 */
    void finish() {
        exiting.clear();
        if (current != null) { current.startedAt = 0L; current.opacity = 1f; }
    }

    private static final class Entry {
        final GuiEventListener panel;
        final Renderable renderer;
        long startedAt = System.currentTimeMillis();
        float opacity = GuiAnimationController.isEnabled() ? 0f : 1f;
        float from;
        boolean closing;

        Entry(GuiEventListener panel, Renderable renderer) { this.panel = panel; this.renderer = renderer; }

        void update(long now) {
            if (!GuiAnimationController.isEnabled()) {
                opacity = closing ? 0f : 1f;
                startedAt = 0L;
                return;
            }
            var style = YzuiTheme.visualStyle();
            float progress = YzuiMotion.progress(now - startedAt,
                    closing ? YzuiMotion.exitDuration(style) : YzuiMotion.enterDuration(style));
            opacity = closing ? from * (1f - YzuiMotion.accelerate(progress)) : YzuiMotion.decelerate(progress);
        }

        void draw(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
            if (opacity <= 0f) return;
            if (closing) panel.setFocused(false);
            // 只改变透明度，控件与文字、头像共用上下文，命中位置和布局保持一致。
            var previous = GuiAnimationController.pushContent(new YzuiMotion.Frame(opacity, 1f, 0f));
            try {
                renderer.extractRenderState(graphics, mouseX, mouseY, delta);
            } finally {
                GuiAnimationController.restoreContent(previous);
            }
        }
    }
}
