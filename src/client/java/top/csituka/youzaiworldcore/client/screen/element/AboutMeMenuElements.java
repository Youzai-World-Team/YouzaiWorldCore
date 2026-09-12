package top.csituka.youzaiworldcore.client.screen.element;

import top.csituka.youzaiworldcore.client.render.YzuiTheme;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.stats.Stats;
import top.csituka.youzaiworldcore.client.screen.MenuScreen;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class AboutMeMenuElements implements MenuElementGroup {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy/MM/dd");

    @Override
    public String getTitleText() {
        return I18n.get("youzaiworldcore.message.gui.title_about_me");
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
        int w = new MenuLayout(screenWidth, screenHeight).contentWidth(560);
        int top = new MenuLayout(screenWidth, screenHeight).centeredTop(238);
        var button = new top.csituka.youzaiworldcore.client.screen.widget.TransparentButton(
                (screenWidth - w) / 2, top + 210, w, 28,
                net.minecraft.network.chat.Component.translatable("screen.youzaiworldcore.account_management.title"),
                () -> Minecraft.getInstance().setScreenAndShow(new top.csituka.youzaiworldcore.client.screen.AccountManagementScreen(screen)));
        button.setExternalAlpha(alpha);
        return List.of(button);
    }

    @SuppressWarnings("null")
    @Override
    public void renderCustomContent(GuiGraphicsExtractor guiGraphics, int screenWidth, int screenHeight, float alpha, float xOffset, int mouseX, int mouseY) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) return;
        var player = client.player;
        var font = client.font;
        int w = new MenuLayout(screenWidth, screenHeight).contentWidth(560);
        int top = new MenuLayout(screenWidth, screenHeight).centeredTop(238);
        int x = (screenWidth - w) / 2 + (int) xOffset;
        int modelWidth = Math.min(144, w / 3);
        guiGraphics.enableScissor(x + 8, top + 8, x + modelWidth - 8, top + 186);
        try {
            InventoryScreen.extractEntityInInventoryFollowsMouse(guiGraphics,
                    x + 10, top + 10, x + modelWidth - 10, top + 182,
                    52, 0.0625f, mouseX, mouseY, player);
        } finally {
            guiGraphics.disableScissor();
        }
        String[][] info = {
                {I18n.get("youzaiworldcore.message.gui.label_player_id"), player.getName().getString()},
                {I18n.get("youzaiworldcore.message.gui.label_first_join"), getFirstJoinDate(player)},
                {I18n.get("youzaiworldcore.message.gui.label_last_join"), getLastJoinDate(client)},
                {I18n.get("youzaiworldcore.message.gui.label_play_time"), getPlayTime(player)}
        };
        int textX = x + modelWidth + 24;
        for (int row = 0; row < info.length; row++) {
            int y = top + 14 + row * 44;
            YzuiTheme.label(guiGraphics, font, net.minecraft.network.chat.Component.literal(info[row][0]),
                    textX, y, w - modelWidth - 40, YzuiTheme.alpha(YzuiTheme.textMuted(), alpha), false);
            YzuiTheme.label(guiGraphics, font, net.minecraft.network.chat.Component.literal(info[row][1]),
                    textX, y + 15, w - modelWidth - 40, YzuiTheme.alpha(YzuiTheme.text(), alpha), false);
        }
    }

    private String getFirstJoinDate(LocalPlayer player) {
        try {
            long playTicks = player.getStats().getValue(Stats.CUSTOM.get(Stats.PLAY_TIME));
            if (playTicks > 0) {
                long firstPlayedMs = System.currentTimeMillis() - (playTicks * 50L);
                return Instant.ofEpochMilli(firstPlayedMs).atZone(ZoneId.systemDefault()).format(DATE_FORMAT);
            }
        } catch (Exception ignored) {
        }
        return I18n.get("youzaiworldcore.message.gui.unknown");
    }

    private String getLastJoinDate(Minecraft client) {
        try {
            return Instant.ofEpochMilli(System.currentTimeMillis()).atZone(ZoneId.systemDefault()).format(DATE_FORMAT);
        } catch (Exception ignored) {
        }
        return I18n.get("youzaiworldcore.message.gui.unknown");
    }

    private String getPlayTime(LocalPlayer player) {
        try {
            long playTicks = player.getStats().getValue(Stats.CUSTOM.get(Stats.PLAY_TIME));
            long playMinutes = playTicks / 20 / 60;
            if (playMinutes < 60) {
                return I18n.get("youzaiworldcore.message.gui.time_minutes", playMinutes);
            }
            long playHours = playMinutes / 60;
            if (playHours < 24) {
                return I18n.get("youzaiworldcore.message.gui.time_hours", playHours);
            }
            long playDays = playHours / 24;
            return I18n.get("youzaiworldcore.message.gui.time_days", playDays);
        } catch (Exception ignored) {
        }
        return I18n.get("youzaiworldcore.message.gui.unknown");
    }

    @Override
    public void renderCustomBackground(GuiGraphicsExtractor g, int width, int height, float alpha, float xOffset) {
        int w = new MenuLayout(width, height).contentWidth(560), x = (width - w) / 2 + (int) xOffset;
        int y = new MenuLayout(width, height).centeredTop(238), modelWidth = Math.min(144, w / 3);
        YzuiTheme.card(g, x, y, modelWidth, 194, alpha);
        YzuiTheme.card(g, x + modelWidth + 10, y, w - modelWidth - 10, 194, alpha);
    }
}
