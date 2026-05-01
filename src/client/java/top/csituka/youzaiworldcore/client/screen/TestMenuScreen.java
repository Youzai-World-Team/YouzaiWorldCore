package top.csituka.youzaiworldcore.client.screen;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class TestMenuScreen extends Screen {

    private static final int BACKGROUND_COLOR = 0x80000000;
    private static final int LARGE_BUTTON_WIDTH = 120;
    private static final int LARGE_BUTTON_HEIGHT = 80;
    private static final int SMALL_BUTTON_WIDTH = 100;
    private static final int SMALL_BUTTON_HEIGHT = 35;
    private static final int NARROW_BUTTON_WIDTH = 80;
    private static final int WIDE_BUTTON_WIDTH = 140;
    private static final int BOTTOM_BUTTON_HEIGHT = 30;
    private static final int BUTTON_SPACING = 10;
    private static final int ROW_SPACING = 15;

    public TestMenuScreen() {
        super(Component.translatable("screen.youzaiworldcore.test_menu.title"));
    }

    @Override
    protected void init() {
        super.init();
        int centerX = this.width / 2;
        int centerY = this.height / 2;

        int topRowWidth = LARGE_BUTTON_WIDTH + BUTTON_SPACING + SMALL_BUTTON_WIDTH;
        int startX = centerX - topRowWidth / 2;

        int largeButtonY = centerY - LARGE_BUTTON_HEIGHT / 2 - ROW_SPACING;

        this.addRenderableWidget(new TransparentButton(
                startX, largeButtonY, LARGE_BUTTON_WIDTH, LARGE_BUTTON_HEIGHT,
                Component.translatable("screen.youzaiworldcore.test_menu.button_main"),
                () -> {}
        ));

        int rightButtonsX = startX + LARGE_BUTTON_WIDTH + BUTTON_SPACING;
        int totalSmallHeight = SMALL_BUTTON_HEIGHT * 2 + BUTTON_SPACING;
        int smallButtonsStartY = centerY - totalSmallHeight / 2 - ROW_SPACING;

        this.addRenderableWidget(new TransparentButton(
                rightButtonsX, smallButtonsStartY, SMALL_BUTTON_WIDTH, SMALL_BUTTON_HEIGHT,
                Component.translatable("screen.youzaiworldcore.test_menu.button_small1"),
                () -> {}
        ));

        this.addRenderableWidget(new TransparentButton(
                rightButtonsX, smallButtonsStartY + SMALL_BUTTON_HEIGHT + BUTTON_SPACING, SMALL_BUTTON_WIDTH, SMALL_BUTTON_HEIGHT,
                Component.translatable("screen.youzaiworldcore.test_menu.button_small2"),
                () -> {}
        ));

        int bottomRowY = largeButtonY + LARGE_BUTTON_HEIGHT + ROW_SPACING;
        int bottomRowWidth = NARROW_BUTTON_WIDTH + BUTTON_SPACING + WIDE_BUTTON_WIDTH;
        int bottomStartX = centerX - bottomRowWidth / 2;

        this.addRenderableWidget(new TransparentButton(
                bottomStartX, bottomRowY, NARROW_BUTTON_WIDTH, BOTTOM_BUTTON_HEIGHT,
                Component.translatable("screen.youzaiworldcore.test_menu.button_narrow"),
                () -> {}
        ));

        this.addRenderableWidget(new TransparentButton(
                bottomStartX + NARROW_BUTTON_WIDTH + BUTTON_SPACING, bottomRowY, WIDE_BUTTON_WIDTH, BOTTOM_BUTTON_HEIGHT,
                Component.translatable("screen.youzaiworldcore.test_menu.button_wide"),
                () -> {}
        ));
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
