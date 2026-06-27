package top.csituka.youzaiworldcore.client.screen.element;

import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import top.csituka.youzaiworldcore.client.screen.MenuScreen;
import top.csituka.youzaiworldcore.client.screen.widget.CheckboxButton;
import top.csituka.youzaiworldcore.client.screen.widget.DropdownButton;
import top.csituka.youzaiworldcore.client.screen.widget.TransparentButton;

import java.util.ArrayList;
import java.util.List;

public class SettingsMenuElements implements MenuElementGroup {

    private static final int LIST_WIDTH = 250;
    private static final int ROW_HEIGHT = 14;
    private static final int ROW_SPACING = 2;
    private static final int CATEGORY_SPACING = 8;
    private static final int GRAY_COLOR = 0xAAAAAA;

    private boolean musicEnabled = true;
    private boolean soundEnabled = true;
    private boolean pvpEnabled = false;
    private boolean friendlyFireEnabled = false;
    private int difficultyIndex = 0;
    private boolean dropdownOpen = false;

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

        int centerX = screenWidth / 2;
        int centerY = screenHeight / 2;

        float scaledLargeH = LARGE_BUTTON_HEIGHT * scale;
        float scaledRowSpacing = ROW_SPACING * scale;
        float baseY = centerY - scaledLargeH / 2 - scaledRowSpacing;

        float scaledListW = LIST_WIDTH * scale;
        float scaledRowH = ROW_HEIGHT * scale;
        float scaledSpacing = ROW_SPACING * scale;
        float scaledCategorySpacing = CATEGORY_SPACING * scale;
        int startX = (int) (centerX - scaledListW / 2);

        float y = baseY;

        TransparentButton category1 = new TransparentButton(
                startX, (int) y, (int) scaledListW, (int) scaledRowH,
                Component.translatable("screen.youzaiworldcore.settings.category_general"),
                () -> {}
        );
        category1.setBackgroundVisible(false);
        category1.setTextColor(GRAY_COLOR);
        category1.setTextLeftAligned(true);
        category1.setExternalAlpha(alpha);
        buttons.add(category1);

        y += scaledRowH + scaledSpacing;

        CheckboxButton checkbox1 = new CheckboxButton(
                startX, (int) y, (int) scaledListW, (int) scaledRowH,
                Component.translatable("screen.youzaiworldcore.settings.checkbox_music"),
                musicEnabled,
                () -> musicEnabled = !musicEnabled
        );
        checkbox1.setExternalAlpha(alpha);
        buttons.add(checkbox1);

        y += scaledRowH + scaledSpacing;

        CheckboxButton checkbox2 = new CheckboxButton(
                startX, (int) y, (int) scaledListW, (int) scaledRowH,
                Component.translatable("screen.youzaiworldcore.settings.checkbox_sound"),
                soundEnabled,
                () -> soundEnabled = !soundEnabled
        );
        checkbox2.setExternalAlpha(alpha);
        buttons.add(checkbox2);

        y += scaledRowH + scaledCategorySpacing;

        TransparentButton category2 = new TransparentButton(
                startX, (int) y, (int) scaledListW, (int) scaledRowH,
                Component.translatable("screen.youzaiworldcore.settings.category_gameplay"),
                () -> {}
        );
        category2.setBackgroundVisible(false);
        category2.setTextColor(GRAY_COLOR);
        category2.setTextLeftAligned(true);
        category2.setExternalAlpha(alpha);
        buttons.add(category2);

        y += scaledRowH + scaledSpacing;

        CheckboxButton checkbox3 = new CheckboxButton(
                startX, (int) y, (int) scaledListW, (int) scaledRowH,
                Component.translatable("screen.youzaiworldcore.settings.checkbox_pvp"),
                pvpEnabled,
                () -> pvpEnabled = !pvpEnabled
        );
        checkbox3.setExternalAlpha(alpha);
        buttons.add(checkbox3);

        y += scaledRowH + scaledSpacing;

        CheckboxButton checkbox4 = new CheckboxButton(
                startX, (int) y, (int) scaledListW, (int) scaledRowH,
                Component.translatable("screen.youzaiworldcore.settings.checkbox_friendly_fire"),
                friendlyFireEnabled,
                () -> friendlyFireEnabled = !friendlyFireEnabled
        );
        checkbox4.setExternalAlpha(alpha);
        buttons.add(checkbox4);

        y += scaledRowH + scaledSpacing;

        List<String> difficultyOptions = List.of(
                I18n.get("youzaiworldcore.message.gui.difficulty_peaceful"),
                I18n.get("youzaiworldcore.message.gui.difficulty_easy"),
                I18n.get("youzaiworldcore.message.gui.difficulty_normal"),
                I18n.get("youzaiworldcore.message.gui.difficulty_hard")
        );
        DropdownButton dropdown = new DropdownButton(
                startX, (int) y, (int) scaledListW, (int) scaledRowH,
                Component.translatable("screen.youzaiworldcore.settings.dropdown_difficulty"),
                difficultyOptions, difficultyIndex, dropdownOpen,
                idx -> difficultyIndex = idx,
                () -> dropdownOpen = !dropdownOpen
        );
        dropdown.setExternalAlpha(alpha);
        buttons.add(dropdown);

        return buttons;
    }
}
