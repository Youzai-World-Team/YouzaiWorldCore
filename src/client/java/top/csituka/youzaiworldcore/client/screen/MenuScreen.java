package top.csituka.youzaiworldcore.client.screen;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import top.csituka.youzaiworldcore.client.screen.element.MenuElementGroup;
import top.csituka.youzaiworldcore.client.screen.widget.TransparentButton;

import java.util.List;

public class MenuScreen extends Screen {

    private static final float ENTRY_ANIMATION_DURATION = 0.5f;
    private static final float TRANSITION_DURATION = 0.4f;

    private MenuElementGroup currentGroup;
    private MenuElementGroup targetGroup;
    private boolean transitionReverse = false;

    private float entryProgress = 0f;
    private long entryStartTime = 0;

    private float transitionProgress = -1f;
    private long transitionStartTime = 0;

    public MenuScreen(MenuElementGroup elementGroup) {
        super(Component.translatable("screen.youzaiworldcore.menu.title"));
        this.currentGroup = elementGroup;
    }

    public void switchTo(MenuElementGroup newGroup) {
        if (targetGroup != null) return;
        this.targetGroup = newGroup;
        this.transitionReverse = false;
        this.transitionProgress = 0f;
        this.transitionStartTime = System.currentTimeMillis();
    }

    public void switchBack(MenuElementGroup newGroup) {
        if (targetGroup != null) return;
        this.targetGroup = newGroup;
        this.transitionReverse = true;
        this.transitionProgress = 0f;
        this.transitionStartTime = System.currentTimeMillis();
    }

    @Override
    protected void init() {
        super.init();
        this.entryProgress = 0f;
        this.entryStartTime = System.currentTimeMillis();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTick) {
        if (entryProgress < 1f) {
            long elapsed = System.currentTimeMillis() - entryStartTime;
            entryProgress = Math.min(1f, elapsed / (ENTRY_ANIMATION_DURATION * 1000f));
        }

        float easedEntry = easeOutCubic(entryProgress);
        int bgAlpha = (int) (easedEntry * 128);
        guiGraphics.fill(0, 0, this.width, this.height, (bgAlpha << 24));

        renderVersionText(guiGraphics, easedEntry);

        if (targetGroup != null) {
            renderTransition(guiGraphics);
        } else {
            renderSingleGroup(guiGraphics, currentGroup, easedEntry);
        }

        super.extractRenderState(guiGraphics, mouseX, mouseY, partialTick);
    }

    private void renderTransition(GuiGraphicsExtractor guiGraphics) {
        if (transitionProgress < 1f) {
            long elapsed = System.currentTimeMillis() - transitionStartTime;
            transitionProgress = Math.min(1f, elapsed / (TRANSITION_DURATION * 1000f));
        }

        float eased = easeInOutCubic(transitionProgress);
        float slideDistance = this.width * 0.12f;

        float direction = transitionReverse ? 1f : -1f;

        float outAlpha = 1f - eased;
        float outOffset = direction * eased * slideDistance;

        float inAlpha = eased;
        float inOffset = -direction * (1f - eased) * slideDistance;

        this.clearWidgets();
        renderGroupButtons(currentGroup, outAlpha, outOffset);
        renderGroupButtons(targetGroup, inAlpha, inOffset);

        renderTitle(guiGraphics, currentGroup, outAlpha);
        renderTitle(guiGraphics, targetGroup, inAlpha);

        if (transitionProgress >= 1f) {
            currentGroup = targetGroup;
            targetGroup = null;
            transitionProgress = -1f;
        }
    }

    private void renderSingleGroup(GuiGraphicsExtractor guiGraphics, MenuElementGroup group, float entryAlpha) {
        this.clearWidgets();
        renderGroupButtons(group, entryAlpha, 0f);
        renderTitle(guiGraphics, group, entryAlpha);
    }

    private void renderGroupButtons(MenuElementGroup group, float alpha, float xOffset) {
        List<TransparentButton> buttons = group.createButtons(this, this.width, this.height, 1f, alpha);
        for (TransparentButton button : buttons) {
            button.setX(button.getX() + (int) xOffset);
            this.addRenderableWidget(button);
        }
    }

    private void renderTitle(GuiGraphicsExtractor guiGraphics, MenuElementGroup group, float alpha) {
        String titleText = group.getTitleText();
        String subtitleText = group.getSubtitleText();

        int textAlpha = (int) (alpha * 255);
        int textColor = (textAlpha << 24) | 0xFFFFFF;

        float scaledLargeH = MenuElementGroup.LARGE_BUTTON_HEIGHT;
        float scaledRowSpacing = MenuElementGroup.ROW_SPACING;
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

    private float easeInOutCubic(float t) {
        return t < 0.5f
                ? 4f * t * t * t
                : 1f - (float) Math.pow(-2f * t + 2f, 3) / 2f;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
