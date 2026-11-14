package top.csituka.youzaiworldcore.client.screen.element;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.client.resources.language.I18n;
import top.csituka.youzaiworldcore.client.render.MenuIcon;
import top.csituka.youzaiworldcore.client.screen.MenuScreen;
import top.csituka.youzaiworldcore.client.screen.widget.ConfirmationDialog;
import top.csituka.youzaiworldcore.client.screen.widget.MenuNavigationButton;

/** 低频入口沿用原操作，避免占据首页常用操作区。 */
public final class MoreMenuElements implements MenuElementGroup {
    private static final String[] ENTRIES = {
            "website", "tutorial_center", "events", "questionnaire_application_and_survey", "report", "management"
    };
    private static final MenuIcon[] ICONS = {
            MenuIcon.WEBSITE, MenuIcon.BOOK, MenuIcon.EVENTS, MenuIcon.SURVEY, MenuIcon.REPORT, MenuIcon.ADMIN
    };

    @Override
    public String getTitleText() {
        return I18n.get("screen.youzaiworldcore.home.more");
    }

    @Override
    public String getSubtitleText() {
        return null;
    }

    @Override
    public boolean isRoot() {
        return false;
    }

    @SuppressWarnings("null")
    @Override
    public List<AbstractWidget> createButtons(MenuScreen screen, int width, int height, float scale, float alpha) {
        var grid = new MenuLayout(width, height).navigation(ENTRIES.length);
        List<AbstractWidget> buttons = new ArrayList<>();
        for (int i = 0; i < ENTRIES.length; i++) {
            int index = i;
            var bounds = grid.tile(i);
            var button = new MenuNavigationButton(bounds.x(), bounds.y(), bounds.width(), bounds.height(),
                    ENTRIES[i], ICONS[i], () -> {
                        if (index == 0)
                            ConfirmLinkScreen.confirmLinkNow(screen, URI.create("https://mcyzw.top"));
                        else
                            screen.showDialog(new ConfirmationDialog(
                                    I18n.get("youzaiworldcore.message.gui.not_implemented_title"),
                                    new String[] { I18n.get("youzaiworldcore.message.gui.not_implemented_desc") },
                                    I18n.get("youzaiworldcore.message.gui.confirm_ok"), null));
                    });
            button.setExternalAlpha(alpha);
            buttons.add(button);
        }
        return buttons;
    }
}
