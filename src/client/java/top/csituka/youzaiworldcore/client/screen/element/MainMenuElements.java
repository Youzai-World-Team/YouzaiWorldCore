package top.csituka.youzaiworldcore.client.screen.element;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import top.csituka.youzaiworldcore.client.MailClientState;
import top.csituka.youzaiworldcore.client.render.MenuIcon;
import top.csituka.youzaiworldcore.client.render.YzuiTheme;
import top.csituka.youzaiworldcore.client.screen.MailScreen;
import top.csituka.youzaiworldcore.client.screen.MenuScreen;
import top.csituka.youzaiworldcore.client.screen.TitleManagementScreen;
import top.csituka.youzaiworldcore.client.screen.map.YzWorldMapScreen;
import top.csituka.youzaiworldcore.client.screen.widget.MenuNavigationButton;
import top.csituka.youzaiworldcore.client.screen.widget.TransparentButton;
import top.csituka.youzaiworldcore.client.skill.ClientAttributeData;
import top.csituka.youzaiworldcore.client.title.TitleClientState;

/** 首页按常用操作与玩家信息分组，状态就地显示，入口保持一键可达。 */
@SuppressWarnings("null")
public class MainMenuElements implements MenuElementGroup {
    private static final String[] ENTRIES = {
            "switch_worlds", "mail", "map", "about_me", "level", "title"
    };
    private static final MenuIcon[] ICONS = {
            MenuIcon.WORLD, MenuIcon.MAIL, MenuIcon.MAP, MenuIcon.ACCOUNT, MenuIcon.LEVEL, MenuIcon.CROWN
    };

    @Override public String getTitleText() { return I18n.get("youzaiworldcore.message.gui.title_main_menu"); }
    @Override public String getSubtitleText() {
        var player = Minecraft.getInstance().player;
        return I18n.get("youzaiworldcore.message.gui.subtitle_main_menu",
                player == null ? "Player" : player.getName().getString());
    }
    @Override public boolean isRoot() { return true; }

    @Override
    public List<AbstractWidget> createButtons(MenuScreen screen, int width, int height, float scale, float alpha) {
        Runnable[] actions = {
                () -> screen.switchTo(new SwitchWorldMenuElements()),
                () -> screen.startExit(() -> Minecraft.getInstance().setScreenAndShow(new MailScreen(screen))),
                () -> screen.startExit(() -> Minecraft.getInstance().setScreenAndShow(new YzWorldMapScreen(screen))),
                () -> screen.switchTo(new AboutMeMenuElements()),
                () -> screen.switchTo(new AdventureLevelMenuElements()),
                () -> screen.startExit(() -> Minecraft.getInstance().setScreenAndShow(new TitleManagementScreen(screen)))
        };
        var layout = new MenuLayout(width, height).dashboard();
        List<AbstractWidget> buttons = new ArrayList<>();
        for (int i = 0; i < ENTRIES.length; i++) {
            var bounds = layout.tile(i);
            var button = new MenuNavigationButton(bounds.x(), bounds.y(), bounds.width(), bounds.height(),
                    ENTRIES[i], ICONS[i], actions[i]);
            button.setExternalAlpha(alpha);
            buttons.add(button);
        }
        Runnable[] shortcuts = {
                () -> screen.startExit(() -> Minecraft.getInstance().setScreenAndShow(new top.csituka.youzaiworldcore.client.screen.PetScreen(screen))),
                () -> screen.startExit(() -> Minecraft.getInstance().setScreenAndShow(YzWorldMapScreen.withWaypoints(screen))),
                () -> screen.switchTo(new SettingsMenuElements()),
                () -> screen.switchTo(new MoreMenuElements())
        };
        Component[] labels = { text("pets"), text("waypoints"),
                Component.translatable("screen.youzaiworldcore.navigation.settings"), text("more") };
        for (int i = 0; i < shortcuts.length; i++) {
            var bounds = layout.shortcut(i);
            buttons.add(new TransparentButton(bounds.x(), bounds.y(), bounds.width(), bounds.height(), labels[i], shortcuts[i]));
        }
        updateButtons(buttons);
        return buttons;
    }

    @Override
    public void updateButtons(List<AbstractWidget> buttons) {
        if (buttons.size() < ENTRIES.length) return;
        ((MenuNavigationButton) buttons.get(0)).setDetail(text("world_hint"));
        ((MenuNavigationButton) buttons.get(1)).setDetail(MailClientState.unreadCount > 0
                ? text("mail_pending", MailClientState.unreadCount) : text("mail_clear"));
        ((MenuNavigationButton) buttons.get(2)).setDetail(text("map_hint"));
        ((MenuNavigationButton) buttons.get(3)).setDetail(text("account_hint"));
        ((MenuNavigationButton) buttons.get(4)).setDetail(text("level_state",
                ClientAttributeData.getPlayerLevel(), ClientAttributeData.getSkillPointsAvailable()));
        var player = Minecraft.getInstance().player;
        Component title = player == null ? Component.empty() : TitleClientState.equippedComponent(player.getUUID());
        ((MenuNavigationButton) buttons.get(5)).setDetail(title.getString().isBlank() ? text("title_none") : title);
    }

    @Override
    public void renderCustomContent(GuiGraphicsExtractor g, int width, int height, float alpha,
                                    float xOffset, int mouseX, int mouseY) {
        var layout = new MenuLayout(width, height).dashboard();
        var font = Minecraft.getInstance().font;
        for (int group = 0; group < 2; group++) {
            YzuiTheme.label(g, font, text(group == 0 ? "common" : "player"),
                    layout.left() + Math.round(xOffset), layout.headingY(group),
                    layout.tileWidth() * 3 + 20, YzuiTheme.alpha(YzuiTheme.textMuted(), alpha), false);
        }
    }

    private static Component text(String key, Object... args) {
        return Component.translatable("screen.youzaiworldcore.home." + key, args);
    }
}
