package top.csituka.youzaiworldcore.client.screen.element;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
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
        Minecraft client = Minecraft.getInstance();
        String worldId = "未知";
        if (client.level != null) {
            worldId = client.level.dimension().identifier().toString();
        }
        return "当前在" + worldId + "，请选择要传送的世界";
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

        float scaledLeftW = LARGE_BUTTON_WIDTH * scale;
        float scaledRightW = SMALL_BUTTON_WIDTH * scale;
        float scaledTopH = LARGE_BUTTON_HEIGHT * scale;
        float scaledBottomH = BOTTOM_BUTTON_HEIGHT * scale;
        float scaledSpacing = BUTTON_SPACING * scale;
        float scaledRowSpacing = ROW_SPACING * scale;

        float totalWidth = scaledLeftW + scaledSpacing + scaledRightW;
        float startX = centerX - totalWidth / 2;
        float topY = centerY - scaledTopH / 2 - scaledRowSpacing;

        TransparentButton topLeft = new TransparentButton(
                (int) startX, (int) topY, (int) scaledLeftW, (int) scaledTopH,
                Component.translatable("screen.youzaiworldcore.switch_world.world1"),
                () -> {
                    Minecraft.getInstance().player.connection.sendCommand("say 传送1");
                    Minecraft.getInstance().setScreen(null);
                }
        );
        topLeft.setExternalAlpha(alpha);
        buttons.add(topLeft);

        TransparentButton topRight = new TransparentButton(
                (int) (startX + scaledLeftW + scaledSpacing), (int) topY, (int) scaledRightW, (int) scaledTopH,
                Component.translatable("screen.youzaiworldcore.switch_world.world2"),
                () -> {
                    Minecraft.getInstance().player.connection.sendCommand("say 传送2");
                    Minecraft.getInstance().setScreen(null);
                }
        );
        topRight.setExternalAlpha(alpha);
        buttons.add(topRight);

        float bottomY = topY + scaledTopH + scaledRowSpacing;

        TransparentButton bottomLeft = new TransparentButton(
                (int) startX, (int) bottomY, (int) scaledLeftW, (int) scaledBottomH,
                Component.translatable("screen.youzaiworldcore.switch_world.world3"),
                () -> {
                    Minecraft.getInstance().player.connection.sendCommand("say 传送3");
                    Minecraft.getInstance().setScreen(null);
                }
        );
        bottomLeft.setExternalAlpha(alpha);
        buttons.add(bottomLeft);

        TransparentButton bottomRight = new TransparentButton(
                (int) (startX + scaledLeftW + scaledSpacing), (int) bottomY, (int) scaledRightW, (int) scaledBottomH,
                Component.translatable("screen.youzaiworldcore.switch_world.world4"),
                () -> {
                    Minecraft.getInstance().player.connection.sendCommand("say 传送4");
                    Minecraft.getInstance().setScreen(null);
                }
        );
        bottomRight.setExternalAlpha(alpha);
        buttons.add(bottomRight);

        return buttons;
    }
}
