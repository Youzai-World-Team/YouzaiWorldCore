package top.csituka.youzaiworldcore.client.screen.element;

import top.csituka.youzaiworldcore.client.screen.MenuScreen;
import top.csituka.youzaiworldcore.client.screen.widget.TransparentButton;

import java.util.List;

public interface MenuElementGroup {

    int LARGE_BUTTON_WIDTH = 120;
    int LARGE_BUTTON_HEIGHT = 80;
    int SMALL_BUTTON_WIDTH = 100;
    int SMALL_BUTTON_HEIGHT = 35;
    int NARROW_BUTTON_WIDTH = 80;
    int WIDE_BUTTON_WIDTH = 140;
    int BOTTOM_BUTTON_HEIGHT = 30;
    int BUTTON_SPACING = 10;
    int ROW_SPACING = 15;

    String getTitleText();

    String getSubtitleText();

    boolean isRoot();

    List<TransparentButton> createButtons(MenuScreen screen, int screenWidth, int screenHeight, float scale, float alpha);
}
