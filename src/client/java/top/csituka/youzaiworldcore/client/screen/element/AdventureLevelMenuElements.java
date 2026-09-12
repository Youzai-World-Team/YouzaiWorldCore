package top.csituka.youzaiworldcore.client.screen.element;

import top.csituka.youzaiworldcore.client.render.YzuiTheme;
import top.csituka.youzaiworldcore.client.render.RoundedRect;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.resources.language.I18n;
import top.csituka.youzaiworldcore.client.screen.MenuScreen;
import top.csituka.youzaiworldcore.client.screen.widget.TransparentButton;
import top.csituka.youzaiworldcore.client.hud.AdventureLevelHudRenderer;
import top.csituka.youzaiworldcore.client.skill.ClientAttributeData;
import top.csituka.youzaiworldcore.network.AttributeUpgradePayload;

import java.util.ArrayList;
import java.util.List;

/**
 * 冒险等级菜单页面。
 * 上半部分：显示当前玩家的冒险等级、经验进度条。
 * 下半部分：属性加点 UI — 可用技能点 + 9 项属性 3×3 网格。
 */
public class AdventureLevelMenuElements implements MenuElementGroup {

    private static final int CONTENT_HEIGHT = 254;
    private static final int HEADER_HEIGHT = 68;
    private static final int GRID_TOP = 112;
    private static final int ROW_PITCH = 42;
    private static final int CELL_HEIGHT = 36;

    /** 9 项属性的展示配置 */
    private static final AttrDef[] ATTRS = {
            new AttrDef("maxHealth",               "血量",       "+%d 血量",        1),
            new AttrDef("healingAmplification",     "恢复",       "+%d%% 倍率",     1),
            new AttrDef("miningSpeed",             "挖掘",       "+%d%% 倍率",     1),
            new AttrDef("movementSpeed",           "移速",       "+%d%% 倍率",     1),
            new AttrDef("jumpAmplitude",           "跳跃",       "+%d%% 倍率",     1),
            new AttrDef("luck",                     "幸运",       "+%d 幸运",       1),
            new AttrDef("meleeDamage",              "近战伤害",   "+%d%% 倍率",     2),
            new AttrDef("rangedDamage",             "远程伤害",   "+%d%% 倍率",     2),
            new AttrDef("damageResistance",         "抗性",       "-%d%% 伤害",     2),
    };

    private record AttrDef(String key, String shortName, String format, int stepPercent) {}

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
        List<AbstractWidget> buttons = new ArrayList<>();
        int w = new MenuLayout(screenWidth, screenHeight).contentWidth(560);
        int cellWidth = (w - 16) / 3;
        int startX = (screenWidth - w) / 2;
        int gridY = contentTop(screenWidth, screenHeight) + GRID_TOP;
        for (int i = 0; i < ATTRS.length; i++) {
            AttrDef attr = ATTRS[i];
            PlusButton button = new PlusButton(startX + (i % 3) * (cellWidth + 8) + cellWidth - 28,
                    gridY + (i / 3) * ROW_PITCH + 7, 22, 22,
                    () -> ClientPlayNetworking.send(new AttributeUpgradePayload(attr.key)));
            button.setExternalAlpha(alpha);
            buttons.add(button);
        }
        updateButtons(buttons);
        return buttons;
    }

    @Override
    public void updateButtons(List<AbstractWidget> buttons) {
        int level = ClientAttributeData.getPlayerLevel();
        int points = ClientAttributeData.getSkillPointsAvailable();
        for (int i = 0; i < ATTRS.length; i++) {
            AbstractWidget button = buttons.get(i);
            button.active = points > 0 && (level >= 20 || "damageResistance".equals(ATTRS[i].key));
            if (!button.active) button.setFocused(false);
        }
    }

    @Override
    public void renderCustomContent(GuiGraphicsExtractor guiGraphics, int screenWidth, int screenHeight,
                                     float alpha, float xOffset, int mouseX, int mouseY) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) return;
        var font = client.font;
        int level = AdventureLevelHudRenderer.getLevel();
        int currentExp = AdventureLevelHudRenderer.getCurrentExp();
        int neededExp = AdventureLevelHudRenderer.getNeededExp();
        float progress = neededExp <= 0 ? 0 : Math.clamp(currentExp / (float) neededExp, 0, 1);
        int w = new MenuLayout(screenWidth, screenHeight).contentWidth(560), x = (screenWidth - w) / 2 + (int) xOffset;
        int top = contentTop(screenWidth, screenHeight);
        int normal = YzuiTheme.alpha(YzuiTheme.text(), alpha);
        int muted = YzuiTheme.alpha(YzuiTheme.textMuted(), alpha);
        guiGraphics.pose().pushMatrix();
        String levelText = "Lv." + level;
        float levelScale = Math.min(2f, 104f / Math.max(1, font.width(levelText)));
        guiGraphics.pose().translate(x + 16, top + (HEADER_HEIGHT - font.lineHeight * levelScale) / 2);
        guiGraphics.pose().scale(levelScale, levelScale);
        guiGraphics.text(font, levelText, 0, 0, YzuiTheme.alpha(YzuiTheme.primary(), alpha), false);
        guiGraphics.pose().popMatrix();
        int barX = x + 132, barWidth = w - 150;
        YzuiTheme.label(guiGraphics, font, net.minecraft.network.chat.Component.translatable(
                "youzaiworldcore.message.gui.adventure_level_label"), barX, top + 13, barWidth, muted, false);
        RoundedRect.fill(guiGraphics,
                barX, top + 31, barWidth, 6, 3, YzuiTheme.multiplyAlpha(YzuiTheme.surfaceHigh(), alpha));
        RoundedRect.fill(guiGraphics,
                barX, top + 31, Math.round(barWidth * progress), 6, 3, YzuiTheme.alpha(YzuiTheme.primary(), alpha));
        YzuiTheme.label(guiGraphics, font, net.minecraft.network.chat.Component.literal(
                currentExp + " / " + neededExp + "  ·  " + (int) (progress * 100) + "%"),
                barX, top + 47, barWidth, normal, false);
        int points = ClientAttributeData.getSkillPointsAvailable();
        YzuiTheme.label(guiGraphics, font, net.minecraft.network.chat.Component.translatable(
                "youzaiworldcore.message.gui.skill_points_available", points),
                x, top + 78, w, YzuiTheme.alpha(YzuiTheme.primary(), alpha), true);
        if (ClientAttributeData.getPlayerLevel() < 20) {
            YzuiTheme.label(guiGraphics, font, net.minecraft.network.chat.Component.translatable(
                    "youzaiworldcore.message.gui.lock_hint", 20), x, top + 94, w, muted, true);
        }
        int cellWidth = (w - 16) / 3;
        for (int i = 0; i < ATTRS.length; i++) {
            AttrDef attr = ATTRS[i];
            int cellX = x + (i % 3) * (cellWidth + 8);
            int cellY = top + GRID_TOP + (i / 3) * ROW_PITCH;
            int value = ClientAttributeData.get(attr.key);
            YzuiTheme.label(guiGraphics, font, net.minecraft.network.chat.Component.literal(attr.shortName),
                    cellX + 8, cellY + 5, cellWidth - 42, normal, false);
            YzuiTheme.label(guiGraphics, font, net.minecraft.network.chat.Component.literal(attr.format.formatted(value * attr.stepPercent)),
                    cellX + 8, cellY + 21, cellWidth - 42, value > 0 ? YzuiTheme.alpha(YzuiTheme.success(), alpha) : muted, false);
        }
        YzuiTheme.label(guiGraphics, font, net.minecraft.network.chat.Component.translatable(
                "youzaiworldcore.message.gui.exp_from_activities"), x, top + 244, w, muted, true);
    }

    /**
     * 加点按钮——小号"+"按钮，使用 TransparentButton。
     */
    private static class PlusButton extends TransparentButton {
        public PlusButton(int x, int y, int width, int height, Runnable onPress) {
            super(x, y, width, height,
                    net.minecraft.network.chat.Component.literal("+"),
                    onPress);
            this.setStyle(YzuiTheme.ButtonStyle.TONAL);

        }
    }

    private static int contentTop(int width, int height) {
        return new MenuLayout(width, height).centeredTop(CONTENT_HEIGHT);
    }

    @Override
    public void renderCustomBackground(GuiGraphicsExtractor g, int width, int height, float alpha, float xOffset) {
        int w = new MenuLayout(width, height).contentWidth(560), x = (width - w) / 2 + (int) xOffset, y = contentTop(width, height);
        YzuiTheme.card(g, x, y, w, HEADER_HEIGHT, alpha);
        int cellWidth = (w - 16) / 3;
        for (int i = 0; i < ATTRS.length; i++) {
            YzuiTheme.card(g, x + (i % 3) * (cellWidth + 8), y + GRID_TOP + (i / 3) * ROW_PITCH,
                    cellWidth, CELL_HEIGHT, alpha);
        }
    }
}
