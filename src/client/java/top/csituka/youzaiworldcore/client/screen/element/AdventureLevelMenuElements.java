package top.csituka.youzaiworldcore.client.screen.element;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.resources.language.I18n;
import top.csituka.youzaiworldcore.client.hud.AdventureLevelHudRenderer;
import top.csituka.youzaiworldcore.client.screen.MenuScreen;

import java.util.ArrayList;
import java.util.List;

/**
 * 冒险等级菜单页面。
 * 显示当前玩家的冒险等级、经验进度条和详细数值。
 * 支持页面切换时的划入划出动效（通过 xOffset 参数）。
 */
public class AdventureLevelMenuElements implements MenuElementGroup {

    private static final int EXP_BAR_WIDTH = 260;
    private static final int EXP_BAR_HEIGHT = 6;
    private static final int LEVEL_FONT_SCALE = 4;

    @Override
    public String getTitleText() {
        return I18n.get("youzaiworldcore.message.gui.title_adventure_level");
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
    public void renderCustomContent(GuiGraphicsExtractor guiGraphics, int screenWidth, int screenHeight,
                                     float alpha, float xOffset, int mouseX, int mouseY) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) return;

        int level = AdventureLevelHudRenderer.getLevel();
        int currentExp = AdventureLevelHudRenderer.getCurrentExp();
        int neededExp = AdventureLevelHudRenderer.getNeededExp();
        float expProgress = neededExp > 0 ? Math.min(1f, (float) currentExp / neededExp) : 0f;

        int textAlpha = (int) (alpha * 255);
        int textColor = (textAlpha << 24) | 0xFFFFFF;
        int subTextColor = (textAlpha << 24) | 0xAAAAAA;
        int xOff = (int) xOffset;

        // ---- 定位 ----
        int baseY = screenHeight / 2 - 120;

        // ---- 等级数字（大号，居中，带 xOffset 划入） ----
        String levelStr = "Lv." + level;
        float levelScale = LEVEL_FONT_SCALE;
        int levelTextWidth = client.font.width(levelStr);
        int scaledLevelWidth = (int) (levelTextWidth * levelScale);
        int levelX = (screenWidth - scaledLevelWidth) / 2 + xOff;
        int levelY = baseY + 10;

        guiGraphics.pose().pushMatrix();
        guiGraphics.pose().translate(levelX, levelY);
        guiGraphics.pose().scale(levelScale, levelScale);
        // 阴影
        guiGraphics.text(client.font, levelStr, 1, 1, (textAlpha << 24) | 0x000000, false);
        // 主文字——金色
        int goldColor = (textAlpha << 24) | 0xFFD700;
        guiGraphics.text(client.font, levelStr, 0, 0, goldColor, false);
        guiGraphics.pose().popMatrix();

        // ---- "冒险等级" 标签 ----
        String label = I18n.get("youzaiworldcore.message.gui.adventure_level_label");
        int labelWidth = client.font.width(label);
        int labelX = (screenWidth - labelWidth) / 2 + xOff;
        int labelY = levelY + (int) (client.font.lineHeight * levelScale) + 4;
        guiGraphics.text(client.font, label, labelX, labelY, subTextColor, false);

        // ---- 经验进度条 ----
        int barX = (screenWidth - EXP_BAR_WIDTH) / 2 + xOff;
        int barY = labelY + client.font.lineHeight + 12;

        // 背景
        int bgColor = (int) (alpha * 60) << 24;
        guiGraphics.fill(barX, barY, barX + EXP_BAR_WIDTH, barY + EXP_BAR_HEIGHT, bgColor);

        // 填充条
        int fillWidth = (int) (expProgress * EXP_BAR_WIDTH);
        if (fillWidth > 0 && alpha > 0.01f) {
            int fillColor = (textAlpha << 24) | 0xFFAA00;
            guiGraphics.fill(barX, barY, barX + fillWidth, barY + EXP_BAR_HEIGHT, fillColor);
        }

        // ---- 经验数值文字 ----
        String expText = currentExp + " / " + neededExp;
        int expTextWidth = client.font.width(expText);
        int expTextX = (screenWidth - expTextWidth) / 2 + xOff;
        int expTextY = barY + EXP_BAR_HEIGHT + 4;
        guiGraphics.text(client.font, expText, expTextX, expTextY, textColor, false);

        // ---- 进度百分比 ----
        String pctText = (int) (expProgress * 100) + "%";
        int pctWidth = client.font.width(pctText);
        int pctX = (screenWidth - pctWidth) / 2 + xOff;
        int pctY = expTextY + client.font.lineHeight + 2;
        guiGraphics.text(client.font, pctText, pctX, pctY, subTextColor, false);

        // ---- 底部温馨提示 ----
        String tip = I18n.get("youzaiworldcore.message.gui.exp_from_activities");
        int tipWidth = client.font.width(tip);
        int tipX = (screenWidth - tipWidth) / 2 + xOff;
        int tipY = screenHeight / 2 + 70;
        int tipTextAlpha = (int) (alpha * 120);
        int tipColor = (tipTextAlpha << 24) | 0x888888;
        guiGraphics.text(client.font, tip, tipX, tipY, tipColor, false);
    }
}
