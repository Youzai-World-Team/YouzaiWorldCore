package top.csituka.youzaiworldcore.client.screen;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import top.csituka.youzaiworldcore.client.screen.widget.TransparentButton;

import java.util.List;

public class MainMenu extends Screen {

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

    public MainMenu() {
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
        
        renderVersionText(guiGraphics, alpha);
        renderWelcomeText(guiGraphics, alpha);
        
        super.extractRenderState(guiGraphics, mouseX, mouseY, partialTick);
    }
    
    private void renderVersionText(GuiGraphicsExtractor guiGraphics, float alpha) {
        String version = FabricLoader.getInstance()
                .getModContainer("youzaiworldcore")
                .map(container -> container.getMetadata().getVersion().getFriendlyString())
                .orElse("unknown");
        String versionText = "YouzaiWorldCore v" + version;
        
        int textAlpha = (int) (alpha * 180);
        int textColor = (textAlpha << 24) | 0xAAAAAA;
        
        float scale = 0.5f;
        int marginX = 10;
        int marginY = 10;
        
        guiGraphics.pose().pushMatrix();
        guiGraphics.pose().scale(scale, scale);
        guiGraphics.text(this.font, versionText, (int) (marginX / scale), (int) (marginY / scale), textColor, false);
        guiGraphics.pose().popMatrix();
    }
    
    private void renderWelcomeText(GuiGraphicsExtractor guiGraphics, float alpha) {
        Minecraft client = Minecraft.getInstance();
        String playerName = client.player != null ? client.player.getName().getString() : "Player";
        String welcomeText = "您好，" + playerName + "！欢迎来到悠哉世界";
        String titleText = "主菜单";
        
        int textAlpha = (int) (alpha * 255);
        int textColor = (textAlpha << 24) | 0xFFFFFF;
        
        float scaledLargeH = LARGE_BUTTON_HEIGHT * 1.15f;
        float scaledRowSpacing = ROW_SPACING * 1.15f;
        int baseY = (int) (this.height / 2 - scaledLargeH / 2 - scaledRowSpacing - 15);
        
        float titleScale = 1.3f;
        int letterSpacing = 3;
        int titleWidth = calculateTextWidthWithSpacing(titleText, letterSpacing);
        float titleX = (this.width - titleWidth * titleScale) / 2f / titleScale;
        int titleY = baseY - 25;
        
        guiGraphics.pose().pushMatrix();
        guiGraphics.pose().scale(titleScale, titleScale);
        drawTextWithSpacing(guiGraphics, this.font, titleText, (int) titleX, (int) (titleY / titleScale), textColor, letterSpacing);
        guiGraphics.pose().popMatrix();
        
        int welcomeWidth = this.font.width(welcomeText);
        int welcomeX = (this.width - welcomeWidth) / 2;
        int welcomeY = baseY - 5;
        
        guiGraphics.text(this.font, welcomeText, welcomeX, welcomeY, textColor, false);
    }
    
    private int calculateTextWidthWithSpacing(String text, int letterSpacing) {
        int width = 0;
        for (int i = 0; i < text.length(); i++) {
            width += this.font.width(String.valueOf(text.charAt(i)));
            if (i < text.length() - 1) {
                width += letterSpacing;
            }
        }
        return width;
    }
    
    private void drawTextWithSpacing(GuiGraphicsExtractor guiGraphics, net.minecraft.client.gui.Font font, String text, int x, int y, int color, int letterSpacing) {
        int currentX = x;
        for (int i = 0; i < text.length(); i++) {
            String ch = String.valueOf(text.charAt(i));
            guiGraphics.text(font, ch, currentX, y, color, false);
            currentX += font.width(ch) + letterSpacing;
        }
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
