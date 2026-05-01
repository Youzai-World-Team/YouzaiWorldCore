package top.csituka.youzaiworldcore.client.screen;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.List;

public class TestMenuScreen extends Screen {

    private static final int LARGE_BUTTON_WIDTH = 120;
    private static final int LARGE_BUTTON_HEIGHT = 80;
    private static final int SMALL_BUTTON_WIDTH = 100;
    private static final int SMALL_BUTTON_HEIGHT = 35;
    private static final int NARROW_BUTTON_WIDTH = 80;
    private static final int WIDE_BUTTON_WIDTH = 140;
    private static final int BOTTOM_BUTTON_HEIGHT = 30;
    private static final int BUTTON_SPACING = 10;
    private static final int ROW_SPACING = 15;
    private static final float ANIMATION_DURATION = 0.5f;

    private float animationProgress = 0f;
    private long startTime = 0;
    private List<TransparentButton> buttons;

    public TestMenuScreen() {
        super(Component.translatable("screen.youzaiworldcore.test_menu.title"));
    }

    @Override
    protected void init() {
        super.init();
        this.animationProgress = 0f;
        this.startTime = System.currentTimeMillis();
        rebuildButtons(1.15f, 0f);
    }

    private void rebuildButtons(float scale, float alpha) {
        this.clearWidgets();
        buttons = new java.util.ArrayList<>();
        
        int centerX = this.width / 2;
        int centerY = this.height / 2;

        float scaledLargeW = LARGE_BUTTON_WIDTH * scale;
        float scaledLargeH = LARGE_BUTTON_HEIGHT * scale;
        float scaledSmallW = SMALL_BUTTON_WIDTH * scale;
        float scaledSmallH = SMALL_BUTTON_HEIGHT * scale;
        float scaledNarrowW = NARROW_BUTTON_WIDTH * scale;
        float scaledWideW = WIDE_BUTTON_WIDTH * scale;
        float scaledBottomH = BOTTOM_BUTTON_HEIGHT * scale;
        float scaledSpacing = BUTTON_SPACING * scale;
        float scaledRowSpacing = ROW_SPACING * scale;

        float topRowWidth = scaledLargeW + scaledSpacing + scaledSmallW;
        float startX = centerX - topRowWidth / 2;

        float largeButtonY = centerY - scaledLargeH / 2 - scaledRowSpacing;

        TransparentButton mainBtn = new TransparentButton(
                (int) startX, (int) largeButtonY, (int) scaledLargeW, (int) scaledLargeH,
                Component.translatable("screen.youzaiworldcore.test_menu.button_main"),
                () -> {}
        );
        mainBtn.setExternalAlpha(alpha);
        buttons.add(mainBtn);
        this.addRenderableWidget(mainBtn);

        float rightButtonsX = startX + scaledLargeW + scaledSpacing;
        float totalSmallHeight = scaledSmallH * 2 + scaledSpacing;
        float smallButtonsStartY = centerY - totalSmallHeight / 2 - scaledRowSpacing;

        TransparentButton smallBtn1 = new TransparentButton(
                (int) rightButtonsX, (int) smallButtonsStartY, (int) scaledSmallW, (int) scaledSmallH,
                Component.translatable("screen.youzaiworldcore.test_menu.button_small1"),
                () -> {}
        );
        smallBtn1.setExternalAlpha(alpha);
        buttons.add(smallBtn1);
        this.addRenderableWidget(smallBtn1);

        TransparentButton smallBtn2 = new TransparentButton(
                (int) rightButtonsX, (int) (smallButtonsStartY + scaledSmallH + scaledSpacing), (int) scaledSmallW, (int) scaledSmallH,
                Component.translatable("screen.youzaiworldcore.test_menu.button_small2"),
                () -> {}
        );
        smallBtn2.setExternalAlpha(alpha);
        buttons.add(smallBtn2);
        this.addRenderableWidget(smallBtn2);

        float bottomRowY = largeButtonY + scaledLargeH + scaledRowSpacing;
        float bottomRowWidth = scaledNarrowW + scaledSpacing + scaledWideW;
        float bottomStartX = centerX - bottomRowWidth / 2;

        TransparentButton narrowBtn = new TransparentButton(
                (int) bottomStartX, (int) bottomRowY, (int) scaledNarrowW, (int) scaledBottomH,
                Component.translatable("screen.youzaiworldcore.test_menu.button_narrow"),
                () -> {}
        );
        narrowBtn.setExternalAlpha(alpha);
        buttons.add(narrowBtn);
        this.addRenderableWidget(narrowBtn);

        TransparentButton wideBtn = new TransparentButton(
                (int) (bottomStartX + scaledNarrowW + scaledSpacing), (int) bottomRowY, (int) scaledWideW, (int) scaledBottomH,
                Component.translatable("screen.youzaiworldcore.test_menu.button_wide"),
                () -> {}
        );
        wideBtn.setExternalAlpha(alpha);
        buttons.add(wideBtn);
        this.addRenderableWidget(wideBtn);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTick) {
        if (animationProgress < 1f) {
            long elapsed = System.currentTimeMillis() - startTime;
            animationProgress = Math.min(1f, elapsed / (ANIMATION_DURATION * 1000f));
        }
        
        float easedProgress = easeOutCubic(animationProgress);
        float scale = 1.15f - 0.15f * easedProgress;
        float alpha = easedProgress;
        
        rebuildButtons(scale, alpha);
        
        int bgAlpha = (int) (easedProgress * 128);
        int backgroundColor = (bgAlpha << 24);
        guiGraphics.fill(0, 0, this.width, this.height, backgroundColor);
        
        super.extractRenderState(guiGraphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTick) {
    }

    private float easeOutCubic(float t) {
        return 1f - (float) Math.pow(1f - t, 3);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
