package top.csituka.youzaiworldcore.client.screen.element;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Component;
import top.csituka.youzaiworldcore.client.screen.MenuScreen;
import top.csituka.youzaiworldcore.client.screen.widget.ConfirmationDialog;
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
                () -> showTeleportDialog(screen, "1")
        );
        topLeft.setExternalAlpha(alpha);
        buttons.add(topLeft);

        TransparentButton topRight = new TransparentButton(
                (int) (startX + scaledLeftW + scaledSpacing), (int) topY, (int) scaledRightW, (int) scaledTopH,
                Component.translatable("screen.youzaiworldcore.switch_world.world2"),
                () -> showTeleportDialog(screen, "2")
        );
        topRight.setExternalAlpha(alpha);
        buttons.add(topRight);

        float bottomY = topY + scaledTopH + scaledRowSpacing;

        TransparentButton bottomLeft = new TransparentButton(
                (int) startX, (int) bottomY, (int) scaledLeftW, (int) scaledBottomH,
                Component.translatable("screen.youzaiworldcore.switch_world.world3"),
                () -> showTeleportDialog(screen, "3")
        );
        bottomLeft.setExternalAlpha(alpha);
        buttons.add(bottomLeft);

        TransparentButton bottomRight = new TransparentButton(
                (int) (startX + scaledLeftW + scaledSpacing), (int) bottomY, (int) scaledRightW, (int) scaledBottomH,
                Component.translatable("screen.youzaiworldcore.switch_world.world4"),
                () -> showTeleportDialog(screen, "4")
        );
        bottomRight.setExternalAlpha(alpha);
        buttons.add(bottomRight);

        return buttons;
    }

    private void showTeleportDialog(MenuScreen screen, String worldId) {
        ConfirmationDialog dialog = new ConfirmationDialog(
                "是否继续",
                new String[]{"确定要传送吗？", "传送后您在当前世界的重生点将会被修改！"},
                () -> {
                    Minecraft.getInstance().player.connection.sendCommand("say 传送" + worldId);
                    Minecraft.getInstance().setScreen(null);
                },
                null
        );
        screen.showDialog(dialog);
    }
}
