package top.csituka.youzaiworldcore.client.screen.element;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import top.csituka.youzaiworldcore.client.screen.MenuScreen;

import java.util.ArrayList;
import java.util.List;

public class AboutMeMenuElements implements MenuElementGroup {

    private static final int CONTENT_WIDTH = 300;
    private static final int CONTENT_HEIGHT = 120;
    private static final int MODEL_SIZE = 100;
    private static final int MODEL_OFFSET_X = 15;
    private static final int MODEL_OFFSET_Y = 30;
    private static final int DIVIDER_MARGIN = 10;
    private static final int TEXT_OFFSET_X = -30;
    private static final long DELAY_MS = 300;
    private static final long FADE_DURATION_MS = 600;

    private long firstRenderTime = -1;

    @Override
    public String getTitleText() {
        return "关于我";
    }

    @Override
    public String getSubtitleText() {
        return null;
    }

    @Override
    public boolean isRoot() {
        return false;
    }

    @Override
    public List<AbstractWidget> createButtons(MenuScreen screen, int screenWidth, int screenHeight, float scale, float alpha) {
        return new ArrayList<>();
    }

    @Override
    public void renderCustomContent(GuiGraphicsExtractor guiGraphics, int screenWidth, int screenHeight, float alpha) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) return;

        if (firstRenderTime == -1) {
            firstRenderTime = System.currentTimeMillis();
        }

        long elapsed = System.currentTimeMillis() - firstRenderTime;
        float modelAlpha;
        if (elapsed < DELAY_MS) {
            modelAlpha = 0f;
        } else if (elapsed < DELAY_MS + FADE_DURATION_MS) {
            float t = (float) (elapsed - DELAY_MS) / FADE_DURATION_MS;
            modelAlpha = easeOutCubic(t);
        } else {
            modelAlpha = 1f;
        }
        modelAlpha *= alpha;

        int textAlpha = (int) (alpha * 255);
        int textColor = (textAlpha << 24) | 0xFFFFFF;
        int dividerAlpha = (int) (alpha * 80);
        int dividerColor = (dividerAlpha << 24) | 0xFFFFFF;

        float scaledLargeH = LARGE_BUTTON_HEIGHT;
        float scaledRowSpacing = ROW_SPACING;
        int baseY = (int) (screenHeight / 2 - scaledLargeH / 2 - scaledRowSpacing - 15);

        int contentX = screenWidth / 2 - CONTENT_WIDTH / 2;
        int contentY = baseY;

        int leftWidth = CONTENT_WIDTH / 2 - DIVIDER_MARGIN;
        int rightX = screenWidth / 2 + DIVIDER_MARGIN;

        int modelCenterX = contentX + leftWidth / 2 + MODEL_OFFSET_X;
        int modelCenterY = contentY + CONTENT_HEIGHT / 2 + MODEL_OFFSET_Y;

        if (modelAlpha > 0.01f) {
            int currentSize = Math.max(1, (int) (MODEL_SIZE * modelAlpha));
            int halfSize = Math.max(1, currentSize / 2);

            int modelX1 = modelCenterX - halfSize;
            int modelY1 = modelCenterY - currentSize;
            int modelX2 = modelCenterX + halfSize;
            int modelY2 = modelCenterY + halfSize / 2;

            InventoryScreen.extractEntityInInventoryFollowsMouse(
                    guiGraphics,
                    modelX1, modelY1,
                    modelX2, modelY2,
                    30,
                    0.0625f,
                    modelCenterX, modelCenterY,
                    client.player
            );
        }

        int dividerX = screenWidth / 2;
        guiGraphics.fill(dividerX, contentY, dividerX + 1, contentY + CONTENT_HEIGHT, dividerColor);

        var font = client.font;
        int labelColor = (textAlpha << 24) | 0xAAAAAA;

        String label = "玩家ID：";
        String playerName = client.player.getName().getString();

        int labelWidth = font.width(label);
        int nameWidth = font.width(playerName);
        int maxTextWidth = Math.max(labelWidth, nameWidth);
        int textX = rightX + (CONTENT_WIDTH / 2 - DIVIDER_MARGIN - maxTextWidth) / 2 + TEXT_OFFSET_X;

        int totalTextHeight = font.lineHeight * 2 + 4;
        int textStartY = contentY + CONTENT_HEIGHT / 2 - totalTextHeight / 2;

        guiGraphics.text(font, label, textX, textStartY, labelColor, false);
        guiGraphics.text(font, playerName, textX, textStartY + font.lineHeight + 4, textColor, false);
    }

    private float easeOutCubic(float t) {
        return 1.0f - (float) Math.pow(1.0f - t, 3);
    }
}
