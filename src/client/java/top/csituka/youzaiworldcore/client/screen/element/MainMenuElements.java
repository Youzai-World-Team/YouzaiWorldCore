package top.csituka.youzaiworldcore.client.screen.element;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import top.csituka.youzaiworldcore.client.screen.MenuScreen;
import top.csituka.youzaiworldcore.client.screen.widget.TransparentButton;

import java.util.ArrayList;
import java.util.List;

public class MainMenuElements implements MenuElementGroup {

    @Override
    public String getTitleText() {
        return "主菜单";
    }

    @Override
    public String getSubtitleText() {
        Minecraft client = Minecraft.getInstance();
        String playerName = client.player != null ? client.player.getName().getString() : "Player";
        return "您好，" + playerName + "！欢迎来到悠哉世界";
    }

    @Override
    public List<TransparentButton> createButtons(MenuScreen screen, int screenWidth, int screenHeight, float scale, float alpha) {
        List<TransparentButton> buttons = new ArrayList<>();

        int centerX = screenWidth / 2;
        int centerY = screenHeight / 2;

        float scaledLargeW = LARGE_BUTTON_WIDTH * scale;
        float scaledLargeH = LARGE_BUTTON_HEIGHT * scale;
        float scaledSmallW = SMALL_BUTTON_WIDTH * scale;
        float scaledSmallH = SMALL_BUTTON_HEIGHT * scale;
        float scaledNarrowW = NARROW_BUTTON_WIDTH * scale;
        float scaledWideW = WIDE_BUTTON_WIDTH * scale;
        float scaledBottomH = BOTTOM_BUTTON_HEIGHT * scale;
        float scaledSpacing = BUTTON_SPACING * scale;
        float scaledRowSpacing = ROW_SPACING * scale;

        float topRowWidth = scaledLargeW + scaledSpacing + scaledSmallW;
        float startX = centerX - topRowWidth / 2;

        float largeButtonY = centerY - scaledLargeH / 2 - scaledRowSpacing;

        TransparentButton mainBtn = new TransparentButton(
                (int) startX, (int) largeButtonY, (int) scaledLargeW, (int) scaledLargeH,
                Component.translatable("screen.youzaiworldcore.test_menu.button_main"),
                () -> screen.switchTo(new SwitchWorldMenuElements())
        );
        mainBtn.setExternalAlpha(alpha);
        buttons.add(mainBtn);

        float rightButtonsX = startX + scaledLargeW + scaledSpacing;
        float totalSmallHeight = scaledSmallH * 2 + scaledSpacing;
        float smallButtonsStartY = centerY - totalSmallHeight / 2 - scaledRowSpacing;

        TransparentButton smallBtn1 = new TransparentButton(
                (int) rightButtonsX, (int) smallButtonsStartY, (int) scaledSmallW, (int) scaledSmallH,
                Component.translatable("screen.youzaiworldcore.test_menu.button_small1"),
                () -> {}
        );
        smallBtn1.setExternalAlpha(alpha);
        buttons.add(smallBtn1);

        TransparentButton smallBtn2 = new TransparentButton(
                (int) rightButtonsX, (int) (smallButtonsStartY + scaledSmallH + scaledSpacing), (int) scaledSmallW, (int) scaledSmallH,
                Component.translatable("screen.youzaiworldcore.test_menu.button_small2"),
                () -> {}
        );
        smallBtn2.setExternalAlpha(alpha);
        buttons.add(smallBtn2);

        float bottomRowY = largeButtonY + scaledLargeH + scaledRowSpacing;
        float bottomRowWidth = scaledNarrowW + scaledSpacing + scaledWideW;
        float bottomStartX = centerX - bottomRowWidth / 2;

        TransparentButton narrowBtn = new TransparentButton(
                (int) bottomStartX, (int) bottomRowY, (int) scaledNarrowW, (int) scaledBottomH,
                Component.translatable("screen.youzaiworldcore.test_menu.button_narrow"),
                () -> {}
        );
        narrowBtn.setExternalAlpha(alpha);
        buttons.add(narrowBtn);

        TransparentButton wideBtn = new TransparentButton(
                (int) (bottomStartX + scaledNarrowW + scaledSpacing), (int) bottomRowY, (int) scaledWideW, (int) scaledBottomH,
                Component.translatable("screen.youzaiworldcore.test_menu.button_wide"),
                () -> {}
        );
        wideBtn.setExternalAlpha(alpha);
        buttons.add(wideBtn);

        return buttons;
    }
}
