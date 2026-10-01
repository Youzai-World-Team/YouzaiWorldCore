package top.csituka.youzaiworldcore.client.screen.element;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.client.resources.language.I18n;
import top.csituka.youzaiworldcore.client.MailClientState;
import top.csituka.youzaiworldcore.client.render.RoundedRect;
import top.csituka.youzaiworldcore.client.render.YzuiTheme;
import top.csituka.youzaiworldcore.client.screen.MailScreen;
import top.csituka.youzaiworldcore.client.screen.MenuScreen;
import top.csituka.youzaiworldcore.client.screen.TitleManagementScreen;
import top.csituka.youzaiworldcore.client.screen.widget.ConfirmationDialog;
import top.csituka.youzaiworldcore.client.screen.widget.MenuNavigationButton;
import top.csituka.youzaiworldcore.client.render.MenuIcon;

/** 主菜单通透卡片网格；各按钮共用 2:1 比例，邮件角标限制在对应按钮内。 */
public class MainMenuElements implements MenuElementGroup {
    private static final String[] ENTRIES = {
            "switch_worlds", "mail", "level", "about_me",
            "title", "settings", "website", "tutorial_center",
            "events", "questionnaire_application_and_survey", "report", "management"
    };

    private static final MenuIcon[] ICONS = {
            MenuIcon.WORLD, MenuIcon.MAIL, MenuIcon.LEVEL, MenuIcon.ACCOUNT,
            MenuIcon.CROWN, MenuIcon.SETTINGS, MenuIcon.WEBSITE, MenuIcon.BOOK,
            MenuIcon.EVENTS, MenuIcon.SURVEY, MenuIcon.REPORT, MenuIcon.ADMIN
    };

    @Override
    public String getTitleText() {
        return I18n.get("youzaiworldcore.message.gui.title_main_menu");
    }

    @Override
    public String getSubtitleText() {
        var player = Minecraft.getInstance().player;
        return I18n.get("youzaiworldcore.message.gui.subtitle_main_menu",
                player == null ? "Player" : player.getName().getString());
    }

    @Override
    public boolean isRoot() { return true; }

    @Override
    public List<AbstractWidget> createButtons(MenuScreen screen, int screenWidth, int screenHeight,
            float scale, float alpha) {
        Runnable[] actions = {
                () -> screen.switchTo(new SwitchWorldMenuElements()),
                () -> screen.startExit(() -> Minecraft.getInstance().setScreenAndShow(new MailScreen())),
                () -> screen.switchTo(new AdventureLevelMenuElements()),
                () -> screen.switchTo(new AboutMeMenuElements()),
                () -> screen.startExit(() -> Minecraft.getInstance().setScreenAndShow(new TitleManagementScreen(screen))),
                () -> screen.switchTo(new SettingsMenuElements()),
                () -> ConfirmLinkScreen.confirmLinkNow(screen, "https://mcyzw.top"),
                () -> showNotImplementedDialog(screen), () -> showNotImplementedDialog(screen),
                () -> showNotImplementedDialog(screen), () -> showNotImplementedDialog(screen),
                () -> showNotImplementedDialog(screen)
        };
        var grid = new MenuLayout(screenWidth, screenHeight).navigation(ENTRIES.length);
        List<AbstractWidget> buttons = new ArrayList<>();
        for (int i = 0; i < ENTRIES.length; i++) {
            var bounds = grid.tile(i);
            var button = new MenuNavigationButton(bounds.x(), bounds.y(), bounds.width(), bounds.height(),
                    ENTRIES[i], ICONS[i], actions[i]);
            button.setExternalAlpha(alpha);
            buttons.add(button);
        }
        return buttons;
    }

    private void showNotImplementedDialog(MenuScreen screen) {
        screen.showDialog(new ConfirmationDialog(
                I18n.get("youzaiworldcore.message.gui.not_implemented_title"),
                new String[]{I18n.get("youzaiworldcore.message.gui.not_implemented_desc")},
                I18n.get("youzaiworldcore.message.gui.confirm_ok"), null));
    }

    @Override
    public void renderCustomContent(GuiGraphicsExtractor g, int width, int height,
            float alpha, float xOffset, int mouseX, int mouseY) {
        int unread = MailClientState.unreadCount;
        if (unread <= 0) return;
        var tile = new MenuLayout(width, height).navigation(ENTRIES.length).tile(1);
        String count = unread > 99 ? "99+" : Integer.toString(unread);
        var font = Minecraft.getInstance().font;
        int badgeWidth = Math.max(16, font.width(count) + 8);
        int x = tile.right() - badgeWidth - 6 + Math.round(xOffset), y = tile.y() + 6;
        RoundedRect.fill(g, x, y, badgeWidth, 16, 8, YzuiTheme.alpha(YzuiTheme.errorContainer(), alpha));
        g.text(font, count, x + (badgeWidth - font.width(count)) / 2, y + (16 - font.lineHeight) / 2,
                YzuiTheme.alpha(YzuiTheme.error(), alpha), false);
    }
}
