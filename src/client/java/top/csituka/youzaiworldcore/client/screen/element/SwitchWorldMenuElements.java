package top.csituka.youzaiworldcore.client.screen.element;

import net.minecraft.network.chat.Component;
import top.csituka.youzaiworldcore.client.screen.MenuScreen;
import top.csituka.youzaiworldcore.client.screen.widget.TransparentButton;

import java.util.ArrayList;
import java.util.List;

public class SwitchWorldMenuElements implements MenuElementGroup {

    @Override
    public String getTitleText() {
        return "切换世界";
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

        TransparentButton topLeft = new TransparentButton(
                (int) startX, (int) largeButtonY, (int) scaledLargeW, (int) scaledLargeH,
                Component.translatable("screen.youzaiworldcore.switch_world.world1"),
                () -> {}
        );
        topLeft.setExternalAlpha(alpha);
        buttons.add(topLeft);

        float rightButtonsX = startX + scaledLargeW + scaledSpacing;
        float totalSmallHeight = scaledSmallH * 2 + scaledSpacing;
        float smallButtonsStartY = centerY - totalSmallHeight / 2 - scaledRowSpacing;

        TransparentButton topRightUpper = new TransparentButton(
                (int) rightButtonsX, (int) smallButtonsStartY, (int) scaledSmallW, (int) scaledSmallH,
                Component.translatable("screen.youzaiworldcore.switch_world.world2"),
                () -> {}
        );
        topRightUpper.setExternalAlpha(alpha);
        buttons.add(topRightUpper);

        TransparentButton topRightLower = new TransparentButton(
                (int) rightButtonsX, (int) (smallButtonsStartY + scaledSmallH + scaledSpacing), (int) scaledSmallW, (int) scaledSmallH,
                Component.translatable("screen.youzaiworldcore.switch_world.world3"),
                () -> {}
        );
        topRightLower.setExternalAlpha(alpha);
        buttons.add(topRightLower);

        float bottomRowY = largeButtonY + scaledLargeH + scaledRowSpacing;
        float bottomRowWidth = scaledNarrowW + scaledSpacing + scaledWideW;
        float bottomStartX = centerX - bottomRowWidth / 2;

        TransparentButton bottomLeft = new TransparentButton(
                (int) bottomStartX, (int) bottomRowY, (int) scaledNarrowW, (int) scaledBottomH,
                Component.translatable("screen.youzaiworldcore.switch_world.world4"),
                () -> {}
        );
        bottomLeft.setExternalAlpha(alpha);
        buttons.add(bottomLeft);

        TransparentButton bottomRight = new TransparentButton(
                (int) (bottomStartX + scaledNarrowW + scaledSpacing), (int) bottomRowY, (int) scaledWideW, (int) scaledBottomH,
                Component.translatable("screen.youzaiworldcore.switch_world.back"),
                () -> screen.goBack()
        );
        bottomRight.setExternalAlpha(alpha);
        buttons.add(bottomRight);

        return buttons;
    }
}
