package top.csituka.youzaiworldcore.client.screen;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class TestMenuScreen extends Screen {

    private static final int BACKGROUND_COLOR = 0x80000000;
    private static final int LARGE_BUTTON_WIDTH = 120;
    private static final int LARGE_BUTTON_HEIGHT = 80;
    private static final int SMALL_BUTTON_WIDTH = 100;
    private static final int SMALL_BUTTON_HEIGHT = 35;
    private static final int SIDE_LARGE_BUTTON_WIDTH = 100;
    private static final int SIDE_LARGE_BUTTON_HEIGHT = 80;
    private static final int SIDE_SMALL_BUTTON_WIDTH = 80;
    private static final int SIDE_SMALL_BUTTON_HEIGHT = 50;
    private static final int BUTTON_SPACING = 10;

    public TestMenuScreen() {
        super(Component.translatable("screen.youzaiworldcore.test_menu.title"));
    }

    @Override
    protected void init() {
        super.init();
        int centerX = this.width / 2;
        int centerY = this.height / 2;

        int centerGroupWidth = LARGE_BUTTON_WIDTH + BUTTON_SPACING + SMALL_BUTTON_WIDTH;
        int totalWidth = SIDE_SMALL_BUTTON_WIDTH + BUTTON_SPACING + centerGroupWidth + BUTTON_SPACING + SIDE_LARGE_BUTTON_WIDTH;
        int startX = centerX - totalWidth / 2;

        int leftSmallX = startX;
        int leftSmallY = centerY - SIDE_SMALL_BUTTON_HEIGHT / 2;
        this.addRenderableWidget(Button.builder(
                Component.translatable("screen.youzaiworldcore.test_menu.button_left"),
                button -> {}
        ).bounds(leftSmallX, leftSmallY, SIDE_SMALL_BUTTON_WIDTH, SIDE_SMALL_BUTTON_HEIGHT).build());

        int centerGroupX = startX + SIDE_SMALL_BUTTON_WIDTH + BUTTON_SPACING;
        int largeButtonY = centerY - LARGE_BUTTON_HEIGHT / 2;

        this.addRenderableWidget(Button.builder(
                Component.translatable("screen.youzaiworldcore.test_menu.button_main"),
                button -> {}
        ).bounds(centerGroupX, largeButtonY, LARGE_BUTTON_WIDTH, LARGE_BUTTON_HEIGHT).build());

        int rightButtonsX = centerGroupX + LARGE_BUTTON_WIDTH + BUTTON_SPACING;
        int totalSmallHeight = SMALL_BUTTON_HEIGHT * 2 + BUTTON_SPACING;
        int smallButtonsStartY = centerY - totalSmallHeight / 2;

        this.addRenderableWidget(Button.builder(
                Component.translatable("screen.youzaiworldcore.test_menu.button_small1"),
                button -> {}
        ).bounds(rightButtonsX, smallButtonsStartY, SMALL_BUTTON_WIDTH, SMALL_BUTTON_HEIGHT).build());

        this.addRenderableWidget(Button.builder(
                Component.translatable("screen.youzaiworldcore.test_menu.button_small2"),
                button -> {}
        ).bounds(rightButtonsX, smallButtonsStartY + SMALL_BUTTON_HEIGHT + BUTTON_SPACING, SMALL_BUTTON_WIDTH, SMALL_BUTTON_HEIGHT).build());

        int rightLargeX = rightButtonsX + SMALL_BUTTON_WIDTH + BUTTON_SPACING;
        int rightLargeY = centerY - SIDE_LARGE_BUTTON_HEIGHT / 2;
        this.addRenderableWidget(Button.builder(
                Component.translatable("screen.youzaiworldcore.test_menu.button_right"),
                button -> {}
        ).bounds(rightLargeX, rightLargeY, SIDE_LARGE_BUTTON_WIDTH, SIDE_LARGE_BUTTON_HEIGHT).build());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.extractBackground(guiGraphics, mouseX, mouseY, partialTick);
        super.extractRenderState(guiGraphics, mouseX, mouseY, partialTick);

        String titleText = this.title.getString();
        int titleWidth = this.font.width(titleText);
        guiGraphics.text(
                this.font,
                this.title,
                this.width / 2 - titleWidth / 2,
                20,
                0xFFFFFFFF,
                true
        );
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTick) {
        guiGraphics.fill(0, 0, this.width, this.height, BACKGROUND_COLOR);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
