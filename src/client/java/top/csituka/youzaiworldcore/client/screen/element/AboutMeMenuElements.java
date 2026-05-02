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
    private static final int MODEL_SIZE = 80;
    private static final int MODEL_OFFSET_X = 15;
    private static final int DIVIDER_MARGIN = 10;

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
        int modelCenterY = contentY + CONTENT_HEIGHT / 2;

        int modelX1 = modelCenterX - MODEL_SIZE / 2;
        int modelY1 = modelCenterY - MODEL_SIZE;
        int modelX2 = modelCenterX + MODEL_SIZE / 2;
        int modelY2 = modelCenterY + MODEL_SIZE / 2;

        InventoryScreen.extractEntityInInventoryFollowsMouse(
                guiGraphics,
                modelX1, modelY1,
                modelX2, modelY2,
                30,
                0.0625f,
                modelCenterX, modelCenterY,
                client.player
        );

        int dividerX = screenWidth / 2;
        guiGraphics.fill(dividerX, contentY, dividerX + 1, contentY + CONTENT_HEIGHT, dividerColor);

        String playerName = client.player.getName().getString();
        var font = client.font;
        int nameY = contentY + CONTENT_HEIGHT / 2 - font.lineHeight / 2;
        guiGraphics.text(font, playerName, rightX + 4, nameY, textColor, false);
    }
}
