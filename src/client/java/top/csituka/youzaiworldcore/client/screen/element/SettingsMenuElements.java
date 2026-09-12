package top.csituka.youzaiworldcore.client.screen.element;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import top.csituka.youzaiworldcore.client.screen.MenuScreen;
import top.csituka.youzaiworldcore.client.screen.widget.CheckboxButton;
import top.csituka.youzaiworldcore.client.screen.widget.DropdownButton;
import top.csituka.youzaiworldcore.client.screen.widget.TransparentButton;

import java.util.ArrayList;
import java.util.List;

@SuppressWarnings("null")
public class SettingsMenuElements implements MenuElementGroup {

    private boolean musicEnabled = true;
    private boolean soundEnabled = true;
    private boolean pvpEnabled = false;
    private boolean friendlyFireEnabled = false;
    private int difficultyIndex = 0;
    private boolean dropdownOpen = false;
    private DropdownButton difficultyDropdown;

    @Override
    public String getTitleText() {
        return I18n.get("youzaiworldcore.message.gui.title_settings");
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
        int listWidth = new MenuLayout(screenWidth, screenHeight).contentWidth(380);
        int left = (screenWidth - listWidth) / 2;
        int y = new MenuLayout(screenWidth, screenHeight).centeredTop(189);
        buttons.add(new CheckboxButton(left, y, listWidth, 25,
                Component.translatable("screen.youzaiworldcore.settings.checkbox_music"),
                musicEnabled, () -> musicEnabled = !musicEnabled));
        buttons.add(new CheckboxButton(left, y + 31, listWidth, 25,
                Component.translatable("screen.youzaiworldcore.settings.checkbox_sound"),
                soundEnabled, () -> soundEnabled = !soundEnabled));
        buttons.add(new CheckboxButton(left, y + 62, listWidth, 25,
                Component.translatable("screen.youzaiworldcore.settings.checkbox_pvp"),
                pvpEnabled, () -> pvpEnabled = !pvpEnabled));
        buttons.add(new CheckboxButton(left, y + 93, listWidth, 25,
                Component.translatable("screen.youzaiworldcore.settings.checkbox_friendly_fire"),
                friendlyFireEnabled, () -> friendlyFireEnabled = !friendlyFireEnabled));
        List<String> difficultyOptions = List.of(
                I18n.get("youzaiworldcore.message.gui.difficulty_peaceful"),
                I18n.get("youzaiworldcore.message.gui.difficulty_easy"),
                I18n.get("youzaiworldcore.message.gui.difficulty_normal"),
                I18n.get("youzaiworldcore.message.gui.difficulty_hard"));
        if (difficultyDropdown == null || difficultyDropdown.getWidth() != listWidth) {
            difficultyDropdown = new DropdownButton(left, y + 124, listWidth, listWidth, 25,
                Component.translatable("screen.youzaiworldcore.settings.dropdown_difficulty"),
                difficultyOptions, difficultyIndex, dropdownOpen,
                idx -> difficultyIndex = idx, () -> dropdownOpen = !dropdownOpen);
        }
        // 视口变化时重新定位；日常渲染复用整个页面的控件。
        difficultyDropdown.setX(left);
        difficultyDropdown.setY(y + 124);
        difficultyDropdown.setCanvasSize(screenWidth, screenHeight);
        buttons.add(difficultyDropdown);
        buttons.add(new TransparentButton(left, y + 163, (listWidth - 8) / 2, 26,
                Component.translatable("options.youzaiworldcore.settings"),
                () -> Minecraft.getInstance().gui.setScreen(
                        new top.csituka.youzaiworldcore.client.screen.YouzaiWorldCoreSettingsScreen(screen))));
        buttons.add(new TransparentButton(left + (listWidth + 8) / 2, y + 163, (listWidth - 8) / 2, 26,
                Component.translatable("screen.youzaiworldcore.appearance.title"),
                () -> Minecraft.getInstance().gui.setScreen(
                        new top.csituka.youzaiworldcore.client.screen.YzuiAppearanceScreen(screen))));
        for (AbstractWidget button : buttons) button.setAlpha(alpha);
        return buttons;
    }
}
