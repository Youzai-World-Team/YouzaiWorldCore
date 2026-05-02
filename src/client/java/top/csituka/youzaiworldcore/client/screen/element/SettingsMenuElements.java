package top.csituka.youzaiworldcore.client.screen.element;

import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Component;
import top.csituka.youzaiworldcore.client.screen.MenuScreen;
import top.csituka.youzaiworldcore.client.screen.widget.CheckboxButton;
import top.csituka.youzaiworldcore.client.screen.widget.TransparentButton;

import java.util.ArrayList;
import java.util.List;

public class SettingsMenuElements implements MenuElementGroup {

    private static final int LIST_WIDTH = 250;
    private static final int ROW_HEIGHT = 20;
    private static final int ROW_SPACING = 4;

    private boolean musicEnabled = true;
    private boolean soundEnabled = true;

    @Override
    public String getTitleText() {
        return "设置";
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
        int startX = (int) (centerX - scaledListW / 2);

        float y = baseY;

        TransparentButton category1 = new TransparentButton(
                startX, (int) y, (int) scaledListW, (int) scaledRowH,
                Component.translatable("screen.youzaiworldcore.settings.category_general"),
                () -> {}
        );
        category1.setBackgroundVisible(false);
        category1.setTextColor(0xFFFFFF);
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

        y += scaledRowH + scaledSpacing;

        TransparentButton category2 = new TransparentButton(
                startX, (int) y, (int) scaledListW, (int) scaledRowH,
                Component.translatable("screen.youzaiworldcore.settings.category_gameplay"),
                () -> {}
        );
        category2.setBackgroundVisible(false);
        category2.setTextColor(0xFFFFFF);
        category2.setExternalAlpha(alpha);
        buttons.add(category2);

        return buttons;
    }
}
