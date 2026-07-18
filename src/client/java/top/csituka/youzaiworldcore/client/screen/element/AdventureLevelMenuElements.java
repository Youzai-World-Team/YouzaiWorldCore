package top.csituka.youzaiworldcore.client.screen.element;

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

    private static final int EXP_BAR_WIDTH = 260;
    private static final int EXP_BAR_HEIGHT = 6;
    private static final int LEVEL_FONT_SCALE = 4;
    private static final int GRID_GAP = 4;
    private static final int CELL_WIDTH = 85;
    private static final int CELL_HEIGHT = 28;
    private static final int PLUS_BTN_SIZE = 18;

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

    /** 缓存上次创建的按钮列表，用于 renderCustomContent 中响应 hover */
    private List<AbstractWidget> attrButtons = new ArrayList<>();

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
        int level = ClientAttributeData.getPlayerLevel();
        int points = ClientAttributeData.getSkillPointsAvailable();

        // ---- 计算属性网格位置（与 renderCustomContent 一致） ----
        int baseY = screenHeight / 2 - 120;
        int levelStrH = (int) (Minecraft.getInstance().font.lineHeight * LEVEL_FONT_SCALE);
        int labelY = baseY + 10 + levelStrH + 4;
        int barY = labelY + Minecraft.getInstance().font.lineHeight + 12;
        int sectionBottom = barY + EXP_BAR_HEIGHT + Minecraft.getInstance().font.lineHeight * 2 + 14;
        int dividerY = sectionBottom + 8;

        int totalGridW = ATTRS.length / 3 * CELL_WIDTH + (ATTRS.length / 3 - 1) * GRID_GAP;
        int gridStartX = (screenWidth - totalGridW) / 2;
        int gridStartY = dividerY + 10 + Minecraft.getInstance().font.lineHeight + 6;

        int cols = 3;
        for (int i = 0; i < ATTRS.length; i++) {
            AttrDef attr = ATTRS[i];
            int col = i % cols;
            int row = i / cols;
            int cellX = gridStartX + col * (CELL_WIDTH + GRID_GAP);
            int cellY = gridStartY + row * (CELL_HEIGHT + GRID_GAP);

            // 判断是否可加点
            boolean isResistance = "damageResistance".equals(attr.key);
            boolean canUpgrade = points > 0 && (level >= 20 || isResistance);

            if (canUpgrade) {
                int btnX = cellX + CELL_WIDTH - PLUS_BTN_SIZE;
                int btnY = cellY + (CELL_HEIGHT - PLUS_BTN_SIZE) / 2;
                String key = attr.key;

                PlusButton btn = new PlusButton(btnX, btnY, PLUS_BTN_SIZE, PLUS_BTN_SIZE,
                        () -> ClientPlayNetworking.send(new AttributeUpgradePayload(key)));
                btn.setExternalAlpha(alpha);
                buttons.add(btn);
            }
        }
        this.attrButtons = buttons;
        return buttons;
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

        // 属性加点数据
        int attrLevel = ClientAttributeData.getPlayerLevel();
        int skillPoints = ClientAttributeData.getSkillPointsAvailable();

        int textAlpha = (int) (alpha * 255);
        int textColor = (textAlpha << 24) | 0xFFFFFF;
        int subTextColor = (textAlpha << 24) | 0xAAAAAA;
        int dimTextColor = (textAlpha << 24) | 0x666666;
        int xOff = (int) xOffset;

        // ---- 定位 ----
        int baseY = screenHeight / 2 - 120;

        // ---- 等级数字（大号金色） ----
        String levelStr = "Lv." + level;
        int levelTextWidth = client.font.width(levelStr);
        int scaledLevelWidth = (int) (levelTextWidth * LEVEL_FONT_SCALE);
        int levelX = (screenWidth - scaledLevelWidth) / 2 + xOff;
        int levelY = baseY + 10;

        guiGraphics.pose().pushMatrix();
        guiGraphics.pose().translate(levelX, levelY);
        guiGraphics.pose().scale(LEVEL_FONT_SCALE, LEVEL_FONT_SCALE);
        guiGraphics.text(client.font, levelStr, 1, 1, (textAlpha << 24) | 0x000000, false);
        guiGraphics.text(client.font, levelStr, 0, 0, (textAlpha << 24) | 0xFFD700, false);
        guiGraphics.pose().popMatrix();

        // ---- "冒险等级" 标签 ----
        int levelStrH = (int) (client.font.lineHeight * LEVEL_FONT_SCALE);
        String label = I18n.get("youzaiworldcore.message.gui.adventure_level_label");
        int labelWidth = client.font.width(label);
        int labelX = (screenWidth - labelWidth) / 2 + xOff;
        int labelY = levelY + levelStrH + 4;
        guiGraphics.text(client.font, label, labelX, labelY, subTextColor, false);

        // ---- 经验进度条 ----
        int barX = (screenWidth - EXP_BAR_WIDTH) / 2 + xOff;
        int barY = labelY + client.font.lineHeight + 12;
        int bgColor = (int) (alpha * 60) << 24;
        guiGraphics.fill(barX, barY, barX + EXP_BAR_WIDTH, barY + EXP_BAR_HEIGHT, bgColor);
        int fillWidth = (int) (expProgress * EXP_BAR_WIDTH);
        if (fillWidth > 0 && alpha > 0.01f) {
            guiGraphics.fill(barX, barY, barX + fillWidth, barY + EXP_BAR_HEIGHT, (textAlpha << 24) | 0xFFAA00);
        }

        // ---- 经验数值 ----
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

        // ==============================================================
        //  分隔线 + 属性加点区域
        // ==============================================================
        int sectionBottom = barY + EXP_BAR_HEIGHT + client.font.lineHeight * 2 + 14;
        int dividerY = sectionBottom + 8;

        // 分隔线
        int dividerAlpha = (int) (alpha * 60);
        int dividerColor = (dividerAlpha << 24) | 0xFFFFFF;
        int dividerX = (screenWidth - EXP_BAR_WIDTH) / 2 + xOff;
        guiGraphics.fill(dividerX, dividerY, dividerX + EXP_BAR_WIDTH, dividerY + 1, dividerColor);

        // ---- 可用技能点 ----
        String pointsText = I18n.get("youzaiworldcore.message.gui.skill_points_available", skillPoints);
        int ptsWidth = client.font.width(pointsText);
        int ptsX = (screenWidth - ptsWidth) / 2 + xOff;
        int ptsY = dividerY + 6;
        int ptsColor = skillPoints > 0
                ? (textAlpha << 24) | 0xFFD700    // 金色有可用点
                : subTextColor;                     // 灰色无可加点
        guiGraphics.text(client.font, pointsText, ptsX, ptsY, ptsColor, false);

        // ---- 上锁提示（20级前仅抗性可加点） ----
        if (attrLevel < 20) {
            String lockHint = I18n.get("youzaiworldcore.message.gui.lock_hint", 20);
            int hintWidth = client.font.width(lockHint);
            int hintX = (screenWidth - hintWidth) / 2 + xOff;
            int hintY = ptsY + client.font.lineHeight + 2;
            guiGraphics.text(client.font, lockHint, hintX, hintY, dimTextColor, false);
        }

        // ---- 3×3 属性网格 ----
        int totalGridW = 3 * CELL_WIDTH + 2 * GRID_GAP;
        int gridStartX = (screenWidth - totalGridW) / 2 + xOff;
        int gridStartY = (attrLevel < 20 ? ptsY + client.font.lineHeight * 2 + 6 : ptsY + client.font.lineHeight + 4) + 4;

        int cols = 3;
        for (int i = 0; i < ATTRS.length; i++) {
            AttrDef attr = ATTRS[i];
            int col = i % cols;
            int row = i / cols;
            int cellX = gridStartX + col * (CELL_WIDTH + GRID_GAP);
            int cellY = gridStartY + row * (CELL_HEIGHT + GRID_GAP);

            int value = ClientAttributeData.get(attr.key);
            boolean isResistance = "damageResistance".equals(attr.key);
            boolean isLocked = attrLevel < 20 && !isResistance;

            // 属性名（短名）
            guiGraphics.text(client.font, attr.shortName, cellX, cellY, subTextColor, false);

            // 属性值
            String valStr = attr.format.formatted(value * attr.stepPercent);
            int valColor = value > 0 ? (textAlpha << 24) | 0x55FF55 : dimTextColor;
            guiGraphics.text(client.font, valStr, cellX, cellY + client.font.lineHeight, valColor, false);

            // 锁定或已满提示
            if (isLocked && skillPoints <= 0) {
                // 无技能点且锁定——显示锁图标
                String lockIcon = "🔒";
                guiGraphics.text(client.font, lockIcon,
                        cellX + CELL_WIDTH - client.font.width(lockIcon), cellY, dimTextColor, false);
            } else if (isLocked) {
                // 有技能点但锁定的属性——显示🔒
                String lockIcon = "🔒";
                guiGraphics.text(client.font, lockIcon,
                        cellX + CELL_WIDTH - client.font.width(lockIcon), cellY, dimTextColor, false);
            }
            // 可加点时，"+"按钮由 createButtons() 中的 PlusButton 绘制
        }

        // ---- 底部提示 ----
        String tip = I18n.get("youzaiworldcore.message.gui.exp_from_activities");
        int tipWidth = client.font.width(tip);
        int tipX = (screenWidth - tipWidth) / 2 + xOff;
        int tipY = screenHeight / 2 + 85;
        int tipTextAlpha = (int) (alpha * 120);
        guiGraphics.text(client.font, tip, tipX, tipY, (tipTextAlpha << 24) | 0x888888, false);
    }

    /**
     * 加点按钮——小号"+"按钮，使用 TransparentButton。
     */
    private static class PlusButton extends TransparentButton {
        public PlusButton(int x, int y, int width, int height, Runnable onPress) {
            super(x, y, width, height,
                    net.minecraft.network.chat.Component.literal("+"),
                    onPress);
            this.setBackgroundVisible(false);
            this.setTextColor(0x55FF55);
        }
    }
}
