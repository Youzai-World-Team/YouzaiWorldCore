package top.csituka.youzaiworldcore.client.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import top.csituka.youzaiworldcore.client.config.YzHudComponent;
import top.csituka.youzaiworldcore.client.config.YzHudSettings;
import top.csituka.youzaiworldcore.client.hud.YzHudLayout;
import top.csituka.youzaiworldcore.client.render.RoundedRect;
import top.csituka.youzaiworldcore.client.screen.widget.TransparentButton;
import top.csituka.youzaiworldcore.util.DebugLogger;

import java.util.EnumMap;

/**
 * YZHUD 位置与透明度编辑页面。
 *
 * <p>画布按比例展示完整 GUI 视口，物品栏、装备栏和状态效果列表可分别选中并拖拽。
 * 三组位置以归一化位移保存到 {@code yzwc/client/global_settings.json} 的
 * {@code yzhud_module} 分节。</p>
 */
@SuppressWarnings("null")
public final class YzHudSettingsScreen extends Screen {

    private static final String MODULE = "YzHudSettingsScreen";
    private static final int PAGE_MARGIN = 12;
    private static final int CONTROL_WIDTH = 240;
    private static final int CONTROL_HEIGHT = 20;
    private static final int SELECTOR_TOP = 56;
    private static final int SELECTOR_WIDTH = 300;
    private static final int SELECTOR_GAP = 4;
    private static final int BUTTON_WIDTH = 96;
    private static final int BUTTON_HEIGHT = 22;
    private static final int BUTTON_GAP = 8;
    private static final int CANVAS_TOP = 82;
    private static final int CANVAS_BOTTOM_SPACE = 44;

    private static final int CANVAS_COLOR = 0x28000000;
    private static final int CANVAS_BORDER = 0x50FFFFFF;
    private static final int VIEWPORT_BORDER = 0x70FFFFFF;
    private static final int FOOTPRINT_BORDER = 0x50FFFFFF;
    private static final int SELECTED_BORDER = 0xE0FFFFFF;
    private static final int PANEL_COLOR = 0x80FFFFFF;
    private static final int SLOT_COLOR = 0x40FFFFFF;

    private final Screen parentScreen;
    private final EnumMap<YzHudComponent, TransparentButton> componentButtons =
            new EnumMap<>(YzHudComponent.class);

    private int canvasLeft;
    private int canvasTop;
    private int canvasWidth;
    private int canvasHeight;
    private float previewScale;
    private int viewportLeft;
    private int viewportTop;
    private int viewportWidth;
    private int viewportHeight;

    private YzHudComponent selectedComponent = YzHudComponent.INVENTORY;
    private boolean dragging;
    private double dragOffsetX;
    private double dragOffsetY;

    /** @param parentScreen 返回时恢复的 Mod 配置页 */
    public YzHudSettingsScreen(Screen parentScreen) {
        super(Component.translatable("screen.youzaiworldcore.yzhud.title"));
        this.parentScreen = parentScreen;
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
        calculateCanvas();

        int controlWidth = Math.max(1, Math.min(CONTROL_WIDTH, width - PAGE_MARGIN * 2));
        int controlX = (width - controlWidth) / 2;
        addRenderableWidget(new OpacitySlider(
                controlX, 30, controlWidth, CONTROL_HEIGHT, YzHudSettings.getOpacity()));

        int selectorWidth = Math.max(1, Math.min(SELECTOR_WIDTH, width - PAGE_MARGIN * 2));
        int selectorButtonWidth = Math.max(1,
                (selectorWidth - SELECTOR_GAP * 2) / YzHudComponent.values().length);
        int selectorX = (width - (selectorButtonWidth * 3 + SELECTOR_GAP * 2)) / 2;
        for (YzHudComponent component : YzHudComponent.values()) {
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
                Component.translatable("screen.youzaiworldcore.yzhud.done"),
                this::onClose));
    }

    private void calculateCanvas() {
        canvasLeft = PAGE_MARGIN;
        canvasTop = Math.min(CANVAS_TOP, Math.max(0, height - CANVAS_BOTTOM_SPACE - 1));
        canvasWidth = Math.max(1, width - PAGE_MARGIN * 2);
        canvasHeight = Math.max(1, height - canvasTop - CANVAS_BOTTOM_SPACE);

        float scaleX = canvasWidth / (float) Math.max(1, width);
        float scaleY = canvasHeight / (float) Math.max(1, height);
        previewScale = Math.min(scaleX, scaleY);
        viewportWidth = Math.max(1, Math.round(width * previewScale));
        viewportHeight = Math.max(1, Math.round(height * previewScale));
        viewportLeft = canvasLeft + (canvasWidth - viewportWidth) / 2;
        viewportTop = canvasTop + (canvasHeight - viewportHeight) / 2;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics,
            int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0x60000000);

        Component title = Component.translatable("screen.youzaiworldcore.yzhud.title");
        graphics.text(font, title, (width - font.width(title)) / 2, 10,
                0xFFFFFFFF, false);

        RoundedRect.fillOrSquare(graphics, canvasLeft, canvasTop,
                canvasWidth, canvasHeight, 6, CANVAS_COLOR);
        graphics.outline(canvasLeft, canvasTop, canvasWidth, canvasHeight, CANVAS_BORDER);
        graphics.outline(viewportLeft, viewportTop,
                viewportWidth, viewportHeight, VIEWPORT_BORDER);

        graphics.enableScissor(viewportLeft, viewportTop,
                viewportLeft + viewportWidth, viewportTop + viewportHeight);
        drawHudPreview(graphics);
        drawComponentOutlines(graphics);
        graphics.disableScissor();

        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    private void drawHudPreview(GuiGraphicsExtractor graphics) {
        float opacity = YzHudSettings.getOpacity();
        int panelColor = YzHudLayout.applyOpacity(PANEL_COLOR, opacity);
        int slotColor = YzHudLayout.applyOpacity(SLOT_COLOR, opacity);

        for (YzHudComponent component : YzHudComponent.values()) {
            graphics.pose().pushMatrix();
            graphics.pose().translate(
                    componentPreviewX(component), componentPreviewY(component));
            graphics.pose().scale(previewScale, previewScale);
            switch (component) {
                case INVENTORY -> drawInventoryPreview(graphics, panelColor, slotColor);
                case ARMOR -> drawArmorPreview(graphics, panelColor, slotColor);
                case EFFECTS -> drawEffectsPreview(graphics, panelColor, slotColor);
            }
            graphics.pose().popMatrix();
        }
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

    private void drawComponentOutlines(GuiGraphicsExtractor graphics) {
        for (YzHudComponent component : YzHudComponent.values()) {
            int color = component == selectedComponent
                    ? SELECTED_BORDER
                    : FOOTPRINT_BORDER;
            graphics.outline(
                    componentPreviewX(component), componentPreviewY(component),
                    componentPreviewWidth(component), componentPreviewHeight(component),
                    color);
        }
    }

    private int componentPreviewX(YzHudComponent component) {
        return viewportLeft + Math.round(
                YzHudLayout.componentLeft(component, width) * previewScale);
    }

    private int componentPreviewY(YzHudComponent component) {
        return viewportTop + Math.round(
                YzHudLayout.componentTop(component, height) * previewScale);
    }

    private int componentPreviewWidth(YzHudComponent component) {
        return Math.max(1, Math.round(
                YzHudLayout.componentWidth(component) * previewScale));
    }

    private int componentPreviewHeight(YzHudComponent component) {
        return Math.max(1, Math.round(
                YzHudLayout.componentHeight(component) * previewScale));
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean isActuallyClick) {
        YzHudComponent component = componentAt(event.x(), event.y());
        if (component != null) {
            selectComponent(component);
            dragging = true;
            dragOffsetX = event.x() - componentPreviewX(component);
            dragOffsetY = event.y() - componentPreviewY(component);
            return true;
        }
        return super.mouseClicked(event, isActuallyClick);
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
                YzHudLayout.positionXFromLeft(selectedComponent, width, targetLeft),
                YzHudLayout.positionYFromTop(selectedComponent, height, targetTop));
        return true;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (dragging) {
            dragging = false;
            YzHudSettings.save();
            return true;
        }
        return super.mouseReleased(event);
    }

    private YzHudComponent componentAt(double mouseX, double mouseY) {
        if (contains(selectedComponent, mouseX, mouseY)) {
            return selectedComponent;
        }
        YzHudComponent[] components = YzHudComponent.values();
        for (int i = components.length - 1; i >= 0; i--) {
            if (components[i] != selectedComponent
                    && contains(components[i], mouseX, mouseY)) {
                return components[i];
            }
        }
        return null;
    }

    private boolean contains(YzHudComponent component, double x, double y) {
        int left = componentPreviewX(component);
        int top = componentPreviewY(component);
        return x >= left && x < left + componentPreviewWidth(component)
                && y >= top && y < top + componentPreviewHeight(component);
    }

    private void selectComponent(YzHudComponent component) {
        selectedComponent = component;
        updateComponentButtons();
    }

    private void updateComponentButtons() {
        for (YzHudComponent component : YzHudComponent.values()) {
            TransparentButton button = componentButtons.get(component);
            if (button == null) {
                continue;
            }
            boolean selected = component == selectedComponent;
            button.active = !selected;
            button.setExternalAlpha(selected ? 0.4F : 1.0F);
        }
    }

    private void resetSettings() {
        YzHudSettings.reset();
        rebuildWidgets();
        DebugLogger.info(MODULE, "YZHUD 设置已恢复默认值");
    }

    @Override
    public void onClose() {
        YzHudSettings.save();
        DebugLogger.info(MODULE, "返回视觉设置页面");
        Minecraft.getInstance().gui.setScreen(parentScreen);
    }

    @Override
    public boolean isPauseScreen() {
        return true;
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
}
