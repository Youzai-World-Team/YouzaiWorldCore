package top.csituka.youzaiworldcore.client.screen.element;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.stats.Stats;
import top.csituka.youzaiworldcore.client.screen.MenuScreen;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class AboutMeMenuElements implements MenuElementGroup {

    private static final int CONTENT_WIDTH = 300;
    private static final int CONTENT_HEIGHT = 120;
    private static final int MODEL_SIZE = 100;
    private static final int MODEL_OFFSET_X = 15;
    private static final int MODEL_OFFSET_Y = 30;
    private static final int DIVIDER_MARGIN = 10;
    private static final int TEXT_OFFSET_X = 0;
    private static final long DELAY_MS = 300;
    private static final long FADE_DURATION_MS = 600;

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy/MM/dd");

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
    public void renderCustomContent(GuiGraphicsExtractor guiGraphics, int screenWidth, int screenHeight, float alpha, float xOffset, int mouseX, int mouseY) {
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
                    mouseX, mouseY,
                    client.player
            );
        }

        int dividerX = (int) (screenWidth / 2 + xOffset);
        guiGraphics.fill(dividerX, contentY, dividerX + 1, contentY + CONTENT_HEIGHT, dividerColor);

        var font = client.font;
        int labelColor = (textAlpha << 24) | 0xAAAAAA;

        String playerName = client.player.getName().getString();
        String firstJoinDate = getFirstJoinDate(client);
        String lastJoinDate = getLastJoinDate(client);
        String playTimeStr = getPlayTime(client);

        String[][] infoItems = {
                {"玩家ID：", playerName},
                {"首次加入时间：", firstJoinDate},
                {"最后加入时间：", lastJoinDate},
                {"游玩时长：", playTimeStr}
        };

        int maxLabelWidth = 0;
        int maxValueWidth = 0;
        for (String[] item : infoItems) {
            int lw = font.width(item[0]);
            int vw = font.width(item[1]);
            if (lw > maxLabelWidth) maxLabelWidth = lw;
            if (vw > maxValueWidth) maxValueWidth = vw;
        }

        int textX = (int) (rightX + TEXT_OFFSET_X + xOffset);

        int totalTextHeight = font.lineHeight * infoItems.length + 4 * (infoItems.length - 1);
        int textStartY = contentY + CONTENT_HEIGHT / 2 - totalTextHeight / 2;

        for (int i = 0; i < infoItems.length; i++) {
            int y = textStartY + i * (font.lineHeight + 4);
            guiGraphics.text(font, infoItems[i][0], textX, y, labelColor, false);
            guiGraphics.text(font, infoItems[i][1], textX + maxLabelWidth, y, textColor, false);
        }
    }

    private String getFirstJoinDate(Minecraft client) {
        try {
            long playTicks = client.player.getStats().getValue(Stats.CUSTOM, Stats.PLAY_TIME);
            if (playTicks > 0) {
                long firstPlayedMs = System.currentTimeMillis() - (playTicks * 50L);
                return Instant.ofEpochMilli(firstPlayedMs).atZone(ZoneId.systemDefault()).format(DATE_FORMAT);
            }
        } catch (Exception ignored) {
        }
        return "未知";
    }

    private String getLastJoinDate(Minecraft client) {
        try {
            return Instant.ofEpochMilli(System.currentTimeMillis()).atZone(ZoneId.systemDefault()).format(DATE_FORMAT);
        } catch (Exception ignored) {
        }
        return "未知";
    }

    private String getPlayTime(Minecraft client) {
        try {
            long playTicks = client.player.getStats().getValue(Stats.CUSTOM, Stats.PLAY_TIME);
            long playMinutes = playTicks / 20 / 60;
            if (playMinutes < 60) {
                return playMinutes + "分钟";
            }
            long playHours = playMinutes / 60;
            if (playHours < 24) {
                return playHours + "小时";
            }
            long playDays = playHours / 24;
            return playDays + "天";
        } catch (Exception ignored) {
        }
        return "未知";
    }

    private float easeOutCubic(float t) {
        return 1.0f - (float) Math.pow(1.0f - t, 3);
    }
}
