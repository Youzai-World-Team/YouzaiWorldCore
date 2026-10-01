package top.csituka.youzaiworldcore.client.screen;

import top.csituka.youzaiworldcore.client.render.YzuiTheme;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import top.csituka.youzaiworldcore.client.config.YzHudComponent;
import top.csituka.youzaiworldcore.client.config.YzHudSettings;
import top.csituka.youzaiworldcore.client.hud.ScoreboardSidebarRenderer;
import top.csituka.youzaiworldcore.client.hud.YzHudLayout;
import top.csituka.youzaiworldcore.client.render.RoundedRect;
import top.csituka.youzaiworldcore.client.screen.widget.TransparentButton;
import top.csituka.youzaiworldcore.util.DebugLogger;

import java.util.EnumMap;

/** Full-screen YZHUD preview over the current game view. Enter commits; Escape restores the snapshot. */
@SuppressWarnings("null")
public final class YzHudSettingsScreen extends Screen {

    private static final String MODULE = "YzHudSettingsScreen";
    private static final int PAGE_MARGIN = 12;
    private static final int CONTROL_WIDTH = 240;
    private static final int CONTROL_HEIGHT = 20;
    private static final int SELECTOR_TOP = 80;
    private static final int SELECTOR_WIDTH = 300;
    private static final int SELECTOR_GAP = 4;
    private static final int BUTTON_WIDTH = 96;
    private static final int BUTTON_HEIGHT = 22;
    private static final int BUTTON_GAP = 8;
    private static final int CANVAS_TOP = 82;

    private static int footprintBorder() { return YzuiTheme.outlineVariant(); }
    private static int selectedBorder() { return YzuiTheme.primary(); }
    private static int lockedBorder() { return YzuiTheme.outlineVariant(); }
    private static int lockedHintColor() { return YzuiTheme.textMuted(); }
    private static int panelColor() { return YzuiTheme.surface(); }
    private static int slotColor() { return YzuiTheme.slot(); }

    /** 锁定组件的占位预览相对当前透明度的额外衰减系数。 */
    private static final float LOCKED_PREVIEW_ALPHA = 0.35F;
    /** 锁定组件的选择按钮透明度，比选中态更暗以示不可操作。 */
    private static final float LOCKED_BUTTON_ALPHA = 0.22F;

    private final Screen parentScreen;
    private final YzHudSettings.Snapshot originalSettings;
    private final EnumMap<YzHudComponent, TransparentButton> componentButtons =
            new EnumMap<>(YzHudComponent.class);

    private float previewScale;
    private int viewportLeft;
    private int viewportTop;
    private int viewportWidth;
    private int viewportHeight;
    private int hudViewportWidth;
    private int hudViewportHeight;

    private YzHudComponent selectedComponent = YzHudComponent.INVENTORY;
    /** 记分板是否因回退到原版样式而锁定位置。 */
    private boolean scoreboardLocked;
    private boolean dragging;
    private double dragOffsetX;
    private double dragOffsetY;

    /** @param parentScreen 返回时恢复的 Mod 配置页 */
    public YzHudSettingsScreen(Screen parentScreen) {
        super(Component.translatable("screen.youzaiworldcore.yzhud.title"));
        this.parentScreen = parentScreen;
        this.originalSettings = YzHudSettings.capture();
    }

    @Override
    protected void init() {
        super.init();
        rebuildWidgets();
    }

    @Override
    protected void rebuildWidgets() {
        clearWidgets();
        componentButtons.clear();
        scoreboardLocked = !ScoreboardSidebarRenderer.isYzuiStyleEnabled();
        if (isLocked(selectedComponent)) {
            selectedComponent = YzHudComponent.INVENTORY;
        }
        calculateCanvas();

        int controlWidth = Math.max(1, Math.min(CONTROL_WIDTH, width - PAGE_MARGIN * 2));
        int controlX = (width - controlWidth) / 2;
        addRenderableWidget(new OpacitySlider(
                controlX, 4, controlWidth, CONTROL_HEIGHT, YzHudSettings.getOpacity()));
        addRenderableWidget(new ComponentScaleSlider(controlX, 28, controlWidth, CONTROL_HEIGHT,
                selectedComponent, YzHudSettings.getConfiguredScale(selectedComponent)));
        addRenderableWidget(new ComponentOpacitySlider(controlX, 52, controlWidth,
                CONTROL_HEIGHT, selectedComponent, YzHudSettings.getComponentOpacity(selectedComponent)));

        int selectorWidth = Math.max(1, Math.min(SELECTOR_WIDTH, width - PAGE_MARGIN * 2));
        YzHudComponent[] components = YzHudComponent.values();
        int selectorGapWidth = SELECTOR_GAP * Math.max(0, components.length - 1);
        int selectorButtonWidth = Math.max(1,
                (selectorWidth - selectorGapWidth) / components.length);
        int selectorActualWidth = selectorButtonWidth * components.length + selectorGapWidth;
        int selectorX = (width - selectorActualWidth) / 2;
        for (YzHudComponent component : components) {
            int componentX = selectorX
                    + component.ordinal() * (selectorButtonWidth + SELECTOR_GAP);
            TransparentButton button = new TransparentButton(
                    componentX, SELECTOR_TOP, selectorButtonWidth, CONTROL_HEIGHT,
                    Component.translatable("screen.youzaiworldcore.yzhud.component."
                            + component.configPrefix()),
                    () -> selectComponent(component));
            componentButtons.put(component, button);
            addRenderableWidget(button);
        }
        updateComponentButtons();

        int buttonsWidth = BUTTON_WIDTH * 2 + BUTTON_GAP;
        int buttonX = (width - buttonsWidth) / 2;
        int buttonY = Math.max(CANVAS_TOP + 4, height - BUTTON_HEIGHT - 10);
        addRenderableWidget(new TransparentButton(
                buttonX, buttonY, BUTTON_WIDTH, BUTTON_HEIGHT,
                Component.translatable("screen.youzaiworldcore.yzhud.reset"),
                this::resetSettings));
        addRenderableWidget(new TransparentButton(
                buttonX + BUTTON_WIDTH + BUTTON_GAP, buttonY, BUTTON_WIDTH, BUTTON_HEIGHT,
                Component.translatable("screen.youzaiworldcore.yzhud.save"), this::saveAndClose));
        addRenderableWidget(new TransparentButton(buttonX, buttonY - BUTTON_HEIGHT - 4,
                BUTTON_WIDTH, BUTTON_HEIGHT, enabledMessage(),
                () -> { YzHudSettings.setEnabledPreview(selectedComponent,
                        !YzHudSettings.isEnabled(selectedComponent)); rebuildWidgets(); }));
    }

    private void calculateCanvas() {
        hudViewportWidth = Minecraft.getInstance().getWindow().getGuiScaledWidth();
        hudViewportHeight = Minecraft.getInstance().getWindow().getGuiScaledHeight();
        viewportLeft = viewportTop = 0;
        viewportWidth = Math.max(1, width);
        viewportHeight = Math.max(1, height);
        previewScale = Math.min(width / (float) Math.max(1, hudViewportWidth),
                height / (float) Math.max(1, hudViewportHeight));
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics,
            int mouseX, int mouseY, float partialTick) {
        drawHudPreview(graphics);
        drawComponentOutlines(graphics);

        if (scoreboardLocked) {
            Component hint = Component.translatable(
                    "screen.youzaiworldcore.yzhud.scoreboard_locked");
            graphics.text(font, hint, (width - font.width(hint)) / 2,
                    Math.max(0, height - BUTTON_HEIGHT * 3 - 18), lockedHintColor(), false);
        }

        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    private void drawHudPreview(GuiGraphicsExtractor graphics) {
        float opacity = YzHudSettings.getOpacity();

        for (YzHudComponent component : YzHudComponent.values()) {
            float componentOpacity = isLocked(component) ? opacity * LOCKED_PREVIEW_ALPHA : YzHudSettings.getOpacity(component);
            if (!YzHudSettings.isEnabled(component)) componentOpacity *= 0.2F;
            int panelColor = YzHudLayout.applyOpacity(panelColor(), componentOpacity);
            int slotColor = YzHudLayout.applyOpacity(slotColor(), componentOpacity);
            graphics.pose().pushMatrix();
            graphics.pose().translate(
                    componentPreviewX(component), componentPreviewY(component));
            graphics.pose().scale(previewScale * YzHudSettings.getScale(component), previewScale * YzHudSettings.getScale(component));
            switch (component) {
                case INVENTORY -> drawInventoryPreview(graphics, panelColor, slotColor);
                case ARMOR -> drawArmorPreview(graphics, panelColor, slotColor);
                case EFFECTS -> drawEffectsPreview(graphics, panelColor, slotColor);
                case SCOREBOARD -> drawScoreboardPreview(graphics, panelColor, slotColor,
                        componentHudWidth(component), componentHudHeight(component));
                case MINIMAP -> drawMinimapPreview(graphics, panelColor, slotColor);
            }
            graphics.pose().popMatrix();
        }
    }

    private static void drawMinimapPreview(GuiGraphicsExtractor graphics, int panelColor, int slotColor) {
        int size = top.csituka.youzaiworldcore.client.map.MapRenderer.layoutSize();
        int radius = switch (top.csituka.youzaiworldcore.client.config.MapSettings.shape()) {
            case CIRCLE -> size / 2; case SQUARE -> 0; case ROUNDED -> 10;
        };
        RoundedRect.fill(graphics, 0, 0,
                top.csituka.youzaiworldcore.client.map.MapRenderer.cardWidth(),
                top.csituka.youzaiworldcore.client.map.MapRenderer.cardHeight(), 8, panelColor);
        RoundedRect.fill(graphics, 4, 4, size, size, radius, slotColor);
        YzuiTheme.border(graphics, 3, 3, size + 2, size + 2, radius == 0 ? 0 : radius + 1,
                YzHudLayout.applyOpacity(YzuiTheme.primary()));
        graphics.fill(size / 2 + 2, size / 2 - 2, size / 2 + 6, size / 2 + 10,
                YzHudLayout.applyOpacity(YzuiTheme.primary()));
    }

    private static void drawInventoryPreview(
            GuiGraphicsExtractor graphics, int panelColor, int slotColor) {
        RoundedRect.fillOrSquare(graphics, 0, 0, 184, 64, 6, panelColor);
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                int slotX = 3 + col * 20;
                int slotY = 3 + row * 20;
                graphics.fill(slotX, slotY, slotX + 18, slotY + 18, slotColor);
            }
        }
    }

    private static void drawArmorPreview(
            GuiGraphicsExtractor graphics, int panelColor, int slotColor) {
        RoundedRect.fillOrSquare(graphics, 0, 0, 50, 244, 6, panelColor);
        for (int i = 0; i < 12; i++) {
            graphics.fill(3, 3 + i * 20, 21, 21 + i * 20, slotColor);
            graphics.fill(25, 9 + i * 20, 43, 15 + i * 20, slotColor);
        }
    }

    private static void drawEffectsPreview(
            GuiGraphicsExtractor graphics, int panelColor, int slotColor) {
        RoundedRect.fillOrSquare(graphics, 0, 0, 132, 264, 6, panelColor);
        for (int row = 0; row < 13; row++) {
            int rowY = 3 + row * 20;
            graphics.fill(3, rowY, 129, rowY + 18, slotColor);
            graphics.fill(6, rowY + 1, 22, rowY + 17, panelColor);
        }
    }

    private static void drawScoreboardPreview(
            GuiGraphicsExtractor graphics, int panelColor, int slotColor,
            int panelWidth, int panelHeight) {
        RoundedRect.fillOrSquare(graphics, 0, 0, panelWidth, panelHeight, 6, panelColor);
        RoundedRect.fillOrSquare(graphics, 2, 2, Math.max(1, panelWidth - 4), 12, 4, slotColor);
        graphics.fill(6, 18, Math.max(7, panelWidth - 6), 19, slotColor);

        for (int row = 0; row < Math.min(15, Math.max(0, (panelHeight - 22) / 13)); row++) {
            int rowY = 22 + row * 13;
            RoundedRect.fillOrSquare(graphics, 6, rowY, Math.max(1, panelWidth - 12), 12, 3, slotColor);
            graphics.fill(10, rowY + 5, Math.max(11, panelWidth - 54), rowY + 7, panelColor);
        }
    }

    private void drawComponentOutlines(GuiGraphicsExtractor graphics) {
        for (YzHudComponent component : YzHudComponent.values()) {
            int color;
            if (isLocked(component)) {
                color = lockedBorder();
            } else if (component == selectedComponent) {
                color = selectedBorder();
            } else {
                color = footprintBorder();
            }
            graphics.outline(
                    componentPreviewX(component), componentPreviewY(component),
                    componentPreviewWidth(component), componentPreviewHeight(component),
                    color);
        }
    }

    private int componentPreviewX(YzHudComponent component) {
        return viewportLeft + Math.round(
                YzHudLayout.componentLeft(component, hudViewportWidth, scaledHudWidth(component)) * previewScale);
    }

    private int componentPreviewY(YzHudComponent component) {
        return viewportTop + Math.round(
                YzHudLayout.componentTop(component, hudViewportHeight, scaledHudHeight(component)) * previewScale);
    }

    private int componentHudWidth(YzHudComponent component) {
        return YzHudLayout.componentWidth(component);
    }

    private int componentHudHeight(YzHudComponent component) {
        return YzHudLayout.componentHeight(component);
    }

    private int scaledHudWidth(YzHudComponent component) {
        return Math.max(1, Math.round(componentHudWidth(component) * YzHudSettings.getScale(component)));
    }

    private int scaledHudHeight(YzHudComponent component) {
        return Math.max(1, Math.round(componentHudHeight(component) * YzHudSettings.getScale(component)));
    }

    private int componentPreviewWidth(YzHudComponent component) {
        return Math.max(1, Math.round(
                scaledHudWidth(component) * previewScale));
    }

    private int componentPreviewHeight(YzHudComponent component) {
        return Math.max(1, Math.round(
                scaledHudHeight(component) * previewScale));
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean isActuallyClick) {
        if (super.mouseClicked(event, isActuallyClick)) return true;
        YzHudComponent component = componentAt(event.x(), event.y());
        if (component != null) {
            selectComponent(component);
            dragging = true;
            dragOffsetX = event.x() - componentPreviewX(component);
            dragOffsetY = event.y() - componentPreviewY(component);
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (!dragging) {
            return super.mouseDragged(event, dragX, dragY);
        }

        double targetLeft = (event.x() - dragOffsetX - viewportLeft) / previewScale;
        double targetTop = (event.y() - dragOffsetY - viewportTop) / previewScale;
        YzHudSettings.setPositionPreview(
                selectedComponent,
                YzHudLayout.positionXFromLeft(selectedComponent, hudViewportWidth,
                        scaledHudWidth(selectedComponent), targetLeft),
                YzHudLayout.positionYFromTop(selectedComponent, hudViewportHeight,
                        scaledHudHeight(selectedComponent), targetTop));
        if (YzHudSettings.isScaleLocked(selectedComponent)) {
            YzHudSettings.setLockedPositionPreview(selectedComponent, targetLeft, targetTop);
        }
        return true;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (dragging) {
            dragging = false;
            return true;
        }
        return super.mouseReleased(event);
    }

    private YzHudComponent componentAt(double mouseX, double mouseY) {
        if (mouseX < viewportLeft || mouseX >= viewportLeft + viewportWidth || mouseY < viewportTop || mouseY >= viewportTop + viewportHeight) return null;
        if (!isLocked(selectedComponent) && contains(selectedComponent, mouseX, mouseY)) {
            return selectedComponent;
        }
        YzHudComponent[] components = YzHudComponent.values();
        for (int i = components.length - 1; i >= 0; i--) {
            if (components[i] != selectedComponent
                    && !isLocked(components[i])
                    && contains(components[i], mouseX, mouseY)) {
                return components[i];
            }
        }
        return null;
    }

    /** @return 指定组件当前是否锁定位置（不可选中、不可拖拽） */
    private boolean isLocked(YzHudComponent component) {
        return component == YzHudComponent.SCOREBOARD && scoreboardLocked;
    }

    private boolean contains(YzHudComponent component, double x, double y) {
        int left = componentPreviewX(component);
        int top = componentPreviewY(component);
        return x >= left && x < left + componentPreviewWidth(component)
                && y >= top && y < top + componentPreviewHeight(component);
    }

    private void selectComponent(YzHudComponent component) {
        if (isLocked(component)) {
            return;
        }
        selectedComponent = component;
        updateComponentButtons();
        rebuildWidgets();
    }

    private void updateComponentButtons() {
        for (YzHudComponent component : YzHudComponent.values()) {
            TransparentButton button = componentButtons.get(component);
            if (button == null) {
                continue;
            }
            if (isLocked(component)) {
                button.active = false;
                button.setExternalAlpha(LOCKED_BUTTON_ALPHA);
                continue;
            }
            boolean selected = component == selectedComponent;
            button.active = !selected;
            button.setExternalAlpha(1.0F);
            button.setStyle(selected ? YzuiTheme.ButtonStyle.FILLED : YzuiTheme.ButtonStyle.TONAL);
        }
    }

    private void resetSettings() {
        YzHudSettings.resetPreview();
        rebuildWidgets();
        DebugLogger.info(MODULE, "YZHUD 预览设置已恢复默认值");
    }

    private Component enabledMessage() {
        return Component.translatable(YzHudSettings.isEnabled(selectedComponent)
                ? "screen.youzaiworldcore.yzhud.enabled_on"
                : "screen.youzaiworldcore.yzhud.enabled_off");
    }

    private void saveAndClose() {
        YzHudSettings.save();
        Minecraft.getInstance().gui.setScreen(parentScreen);
    }

    @Override
    public void onClose() {
        YzHudSettings.restore(originalSettings);
        Minecraft.getInstance().gui.setScreen(parentScreen);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == 257 || event.key() == 335) { saveAndClose(); return true; }
        if (event.key() == 256) { onClose(); return true; }
        return super.keyPressed(event);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /** 使用原版滑块交互并实时更新 YZHUD 透明度。 */
    private static final class OpacitySlider extends AbstractSliderButton {

        private OpacitySlider(int x, int y, int width, int height, double value) {
            super(x, y, width, height, Component.empty(), value);
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            setMessage(Component.translatable("screen.youzaiworldcore.yzhud.opacity",
                    Math.round(value * 100.0D)));
        }

        @Override
        protected void applyValue() {
            YzHudSettings.setOpacityPreview(value);
        }
    }

    private static final class ComponentScaleSlider extends AbstractSliderButton {
        private final YzHudComponent component;
        ComponentScaleSlider(int x, int y, int w, int h, YzHudComponent c, double value) { super(x, y, w, h, Component.empty(), (value - .25) / 3.75); component = c; updateMessage(); }
        protected void updateMessage() { setMessage(Component.translatable("screen.youzaiworldcore.yzhud.scale", String.format(java.util.Locale.ROOT, "%.2f", .25 + value * 3.75))); }
        protected void applyValue() { YzHudSettings.setScalePreview(component, .25 + value * 3.75); }
    }
    private static final class ComponentOpacitySlider extends AbstractSliderButton {
        private final YzHudComponent component;
        ComponentOpacitySlider(int x, int y, int w, int h, YzHudComponent c, double value) { super(x, y, w, h, Component.empty(), value); component = c; updateMessage(); }
        protected void updateMessage() { setMessage(Component.translatable("screen.youzaiworldcore.yzhud.component_opacity", Math.round(value * 100))); }
        protected void applyValue() { YzHudSettings.setComponentOpacityPreview(component, value); }
    }
}
