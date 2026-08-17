package top.csituka.youzaiworldcore.client.hud;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.numbers.NumberFormat;
import net.minecraft.network.chat.numbers.StyledFormat;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerScoreEntry;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;

import top.csituka.youzaiworldcore.client.render.RoundedRect;
import top.csituka.youzaiworldcore.util.DebugLogger;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * YZUI 原版侧边栏记分板渲染器。
 *
 * <p>只替换记分板的视觉表现，目标选择、隐藏条目过滤、队伍名称格式和数字格式
 * 均沿用原版逻辑。面板采用 YZUI 半透明白色圆角容器，标题和每条记分板项目使用
 * 独立的圆角行背景，以便在不同世界背景上保持可读性。</p>
 */
@SuppressWarnings("null")
public final class ScoreboardSidebarRenderer {

    private static final String MODULE = "ScoreboardSidebarRenderer";

    private static final int MAX_ENTRIES = 15;
    private static final int SCREEN_MARGIN = 4;
    private static final int PANEL_PADDING_X = 6;
    private static final int PANEL_PADDING_Y = 5;
    private static final int PANEL_RADIUS = 6;
    private static final int HEADER_RADIUS = 4;
    private static final int ROW_RADIUS = 3;
    private static final int HEADER_GAP = 3;
    private static final int ROW_VERTICAL_PADDING = 2;
    private static final int ROW_TEXT_INSET = 3;
    private static final int COLUMN_GAP = 5;

    private static final int PANEL_BACKGROUND = 0x80FFFFFF;
    private static final int PANEL_SHADOW = 0x30000000;
    private static final int HEADER_BACKGROUND = 0x58FFFFFF;
    private static final int ROW_BACKGROUND = 0x32FFFFFF;
    private static final int ROW_BACKGROUND_ALT = 0x22FFFFFF;
    private static final int DIVIDER_COLOR = 0x68FFFFFF;
    private static final int TEXT_COLOR = 0xFFFFFFFF;

    private static String lastObjectiveName;
    private static int lastEntryCount = -1;

    private ScoreboardSidebarRenderer() {
    }

    /** 注册客户端记分板渲染器，并记录初始化里程碑。 */
    public static void initialize() {
        DebugLogger.info(MODULE, "YZUI 记分板侧边栏渲染器已初始化");
    }

    /**
     * 绘制一个记分板目标。
     *
     * @param graphics 当前 HUD 图形提取器
     * @param objective 当前侧边栏显示目标
     */
    public static void render(GuiGraphicsExtractor graphics, Objective objective) {
        if (objective == null) {
            return;
        }

        Minecraft client = Minecraft.getInstance();
        Font font = client.font;
        Scoreboard scoreboard = objective.getScoreboard();
        NumberFormat numberFormat = objective.numberFormatOrDefault(StyledFormat.SIDEBAR_DEFAULT);

        List<DisplayEntry> entries = collectEntries(scoreboard, objective, numberFormat);
        int rowHeight = Math.max(font.lineHeight + ROW_VERTICAL_PADDING * 2, 12);
        int headerHeight = rowHeight;

        // 极小 GUI 比例下缩短列表，避免面板超出屏幕边界。
        int minimumPanelHeight = PANEL_PADDING_Y * 2 + headerHeight + HEADER_GAP;
        int availableHeight = Math.max(minimumPanelHeight,
                graphics.guiHeight() - SCREEN_MARGIN * 2);
        int maxVisibleRows = Math.max(0,
                (availableHeight - minimumPanelHeight) / rowHeight);
        if (entries.size() > maxVisibleRows) {
            entries = new ArrayList<>(entries.subList(0, maxVisibleRows));
        }

        int titleWidth = font.width(objective.getDisplayName());
        int maxNameWidth = 0;
        int maxScoreWidth = 0;
        for (DisplayEntry entry : entries) {
            maxNameWidth = Math.max(maxNameWidth, font.width(entry.name()));
            maxScoreWidth = Math.max(maxScoreWidth, font.width(entry.score()));
        }

        int scoreSeparatorWidth = maxScoreWidth > 0 ? font.width(":") + COLUMN_GAP : 0;
        int desiredContentWidth = Math.max(titleWidth,
                maxNameWidth + scoreSeparatorWidth + maxScoreWidth + ROW_TEXT_INSET * 2);
        int maxContentWidth = Math.max(1,
                graphics.guiWidth() - SCREEN_MARGIN * 2 - PANEL_PADDING_X * 2);
        int contentWidth = Math.max(1, Math.min(desiredContentWidth, maxContentWidth));
        int panelWidth = contentWidth + PANEL_PADDING_X * 2;
        int panelHeight = minimumPanelHeight + entries.size() * rowHeight;
        int panelTop = Math.max(SCREEN_MARGIN,
                (graphics.guiHeight() - panelHeight) / 2);
        if (panelTop + panelHeight > graphics.guiHeight() - SCREEN_MARGIN) {
            panelTop = Math.max(SCREEN_MARGIN,
                    graphics.guiHeight() - SCREEN_MARGIN - panelHeight);
        }
        int panelX = graphics.guiWidth() - SCREEN_MARGIN - panelWidth;

        int rowTextWidth = Math.max(1, contentWidth - ROW_TEXT_INSET * 2);
        int scoreColumnWidth = Math.min(maxScoreWidth,
                Math.max(0, (rowTextWidth - (maxScoreWidth > 0 ? COLUMN_GAP : 0)) / 3));
        int nameColumnWidth = maxScoreWidth > 0
                ? Math.max(1, rowTextWidth - COLUMN_GAP - scoreColumnWidth)
                : rowTextWidth;

        RoundedRect.fillOrSquare(graphics, panelX + 1, panelTop + 2,
                panelWidth, panelHeight, PANEL_RADIUS,
                YzHudLayout.applyOpacity(PANEL_SHADOW));
        RoundedRect.fillOrSquare(graphics, panelX, panelTop,
                panelWidth, panelHeight, PANEL_RADIUS,
                YzHudLayout.applyOpacity(PANEL_BACKGROUND));

        int headerX = panelX + 2;
        int headerY = panelTop + 2;
        int headerWidth = Math.max(1, panelWidth - 4);
        int headerInnerHeight = Math.max(1, headerHeight - 1);
        RoundedRect.fillOrSquare(graphics, headerX, headerY,
                headerWidth, headerInnerHeight, HEADER_RADIUS,
                YzHudLayout.applyOpacity(HEADER_BACKGROUND));

        FormattedCharSequence clippedTitle = clip(objective.getDisplayName(), font, contentWidth);
        int titleX = panelX + (panelWidth - font.width(clippedTitle)) / 2;
        int titleY = headerY + Math.max(0, (headerInnerHeight - font.lineHeight) / 2);
        graphics.text(font, clippedTitle, titleX, titleY,
                YzHudLayout.applyOpacity(TEXT_COLOR), true);

        int dividerY = panelTop + PANEL_PADDING_Y + headerHeight + HEADER_GAP / 2;
        graphics.fill(panelX + PANEL_PADDING_X, dividerY,
                panelX + panelWidth - PANEL_PADDING_X, dividerY + 1,
                YzHudLayout.applyOpacity(DIVIDER_COLOR));

        int rowX = panelX + PANEL_PADDING_X;
        int rowWidth = Math.max(1, panelWidth - PANEL_PADDING_X * 2);
        int rowStartY = panelTop + PANEL_PADDING_Y + headerHeight + HEADER_GAP;
        int textColor = YzHudLayout.applyOpacity(TEXT_COLOR);

        for (int index = 0; index < entries.size(); index++) {
            DisplayEntry entry = entries.get(index);
            int rowY = rowStartY + index * rowHeight;
            int rowColor = (index & 1) == 0 ? ROW_BACKGROUND : ROW_BACKGROUND_ALT;
            RoundedRect.fillOrSquare(graphics, rowX, rowY, rowWidth, rowHeight,
                    ROW_RADIUS, YzHudLayout.applyOpacity(rowColor));

            int textY = rowY + Math.max(0, (rowHeight - font.lineHeight) / 2);
            FormattedCharSequence name = clip(entry.name(), font, nameColumnWidth);
            graphics.text(font, name, rowX + ROW_TEXT_INSET, textY, textColor, true);

            if (scoreColumnWidth > 0 && !entry.score().getString().isEmpty()) {
                FormattedCharSequence score = clip(entry.score(), font, scoreColumnWidth);
                int scoreX = rowX + rowWidth - ROW_TEXT_INSET - font.width(score);
                graphics.text(font, score, scoreX, textY, textColor, true);
            }
        }

        if (!objective.getName().equals(lastObjectiveName) || entries.size() != lastEntryCount) {
            DebugLogger.debug(MODULE, "记分板目标=%s，显示条目=%d，面板=%dx%d",
                    objective.getName(), entries.size(), panelWidth, panelHeight);
            lastObjectiveName = objective.getName();
            lastEntryCount = entries.size();
        }
    }

    private static List<DisplayEntry> collectEntries(
            Scoreboard scoreboard, Objective objective, NumberFormat numberFormat) {
        List<DisplayEntry> entries = new ArrayList<>();
        scoreboard.listPlayerScores(objective).stream()
                .filter(entry -> !entry.isHidden())
                .sorted(Comparator.comparingInt(PlayerScoreEntry::value).reversed()
                        .thenComparing(PlayerScoreEntry::owner,
                                String.CASE_INSENSITIVE_ORDER))
                .limit(MAX_ENTRIES)
                .forEach(entry -> {
                    PlayerTeam team = scoreboard.getPlayersTeam(entry.owner());
                    Component name = PlayerTeam.formatNameForTeam(team, entry.ownerName());
                    Component score = entry.formatValue(numberFormat);
                    entries.add(new DisplayEntry(name, score));
                });
        return entries;
    }

    /** 按最大宽度截断文字并保留原有队伍颜色、前缀与数字格式。 */
    private static FormattedCharSequence clip(FormattedText text, Font font, int maxWidth) {
        return Language.getInstance().getVisualOrder(
                font.substrByWidth(text, Math.max(0, maxWidth)));
    }

    private record DisplayEntry(Component name, Component score) {
    }
}
