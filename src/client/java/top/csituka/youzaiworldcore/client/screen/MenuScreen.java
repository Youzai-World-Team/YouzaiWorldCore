package top.csituka.youzaiworldcore.client.screen;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import top.csituka.youzaiworldcore.client.screen.element.MenuElementGroup;
import top.csituka.youzaiworldcore.client.screen.widget.TransparentButton;

import java.util.List;

public class MenuScreen extends Screen {

    private static final float ANIMATION_DURATION = 0.5f;

    private final MenuElementGroup elementGroup;
    private float animationProgress = 0f;
    private long startTime = 0;
    private List<TransparentButton> buttons;

    public MenuScreen(MenuElementGroup elementGroup) {
        super(Component.translatable("screen.youzaiworldcore.menu.title"));
        this.elementGroup = elementGroup;
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
        buttons = elementGroup.createButtons(this.width, this.height, scale, alpha);
        for (TransparentButton button : buttons) {
            this.addRenderableWidget(button);
        }
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
        renderTitleText(guiGraphics, alpha);

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

    private void renderTitleText(GuiGraphicsExtractor guiGraphics, float alpha) {
        String titleText = elementGroup.getTitleText();
        String subtitleText = elementGroup.getSubtitleText();

        int textAlpha = (int) (alpha * 255);
        int textColor = (textAlpha << 24) | 0xFFFFFF;

        float scaledLargeH = MenuElementGroup.LARGE_BUTTON_HEIGHT * 1.15f;
        float scaledRowSpacing = MenuElementGroup.ROW_SPACING * 1.15f;
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

        if (subtitleText != null) {
            int subtitleWidth = this.font.width(subtitleText);
            int subtitleX = (this.width - subtitleWidth) / 2;
            int subtitleY = baseY - 5;

            guiGraphics.text(this.font, subtitleText, subtitleX, subtitleY, textColor, false);
        }
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
