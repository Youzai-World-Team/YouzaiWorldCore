package top.csituka.youzaiworldcore.client.screen.element;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import top.csituka.youzaiworldcore.YouzaiworldCore;
import top.csituka.youzaiworldcore.client.screen.MenuScreen;
import top.csituka.youzaiworldcore.client.screen.widget.ConfirmationDialog;
import top.csituka.youzaiworldcore.client.screen.widget.TextureTileButton;
import top.csituka.youzaiworldcore.dimensionalinventories.WorldPoolTeleportPayload;

/** 世界入口共用主菜单的整图网格；保留七个维度池与三个旧入口的操作。 */
public class SwitchWorldMenuElements implements MenuElementGroup {
    private static final Map<String, String> POOL_MAP = Map.of(
            "survival", "survival_world_pool",
            "kingdom", "main_city_pool",
            "gameplay", "gameplay_pool",
            "creative", "creation_pool",
            "building", "building_pool",
            "command", "commands_pool",
            "marketplace", "tutorial_world_pool"
    );

    private static final Identifier[] TEXTURES = {
            texture("survival_world"), texture("kingdom"), texture("gameplay"), texture("creative"),
            texture("building"), texture("tutorials_world"), texture("the_nether"), texture("the_end"),
            texture("command_zone"), texture("overworld")
    };

    private static Identifier texture(String name) {
        return Identifier.fromNamespaceAndPath(YouzaiworldCore.MOD_ID, "textures/gui/" + name + ".png");
    }

    @Override
    public String getTitleText() {
        return I18n.get("youzaiworldcore.message.gui.title_switch_world");
    }

    @Override
    public String getSubtitleText() {
        var level = Minecraft.getInstance().level;
        String worldId = level == null ? I18n.get("youzaiworldcore.message.gui.unknown")
                : level.dimension().identifier().toString();
        return I18n.get("youzaiworldcore.message.gui.subtitle_switch_world", worldId);
    }

    @Override
    public boolean isRoot() { return false; }

    @Override
    public List<AbstractWidget> createButtons(MenuScreen screen, int screenWidth, int screenHeight,
            float scale, float alpha) {
        Runnable[] actions = {
                () -> requestPoolTeleport(screen, "survival"),
                () -> requestPoolTeleport(screen, "kingdom"),
                () -> requestPoolTeleport(screen, "gameplay"),
                () -> requestPoolTeleport(screen, "creative"),
                () -> requestPoolTeleport(screen, "building"),
                () -> requestPoolTeleport(screen, "marketplace"),
                () -> showTeleportDialog(screen, "nether"),
                () -> showTeleportDialog(screen, "end"),
                () -> requestPoolTeleport(screen, "command"),
                () -> showTeleportDialog(screen, "overworld")
        };
        var grid = new MenuLayout(screenWidth, screenHeight).navigation(TEXTURES.length);
        List<AbstractWidget> buttons = new ArrayList<>();
        for (int i = 0; i < TEXTURES.length; i++) {
            var bounds = grid.tile(i);
            var button = new TextureTileButton(bounds.x(), bounds.y(), bounds.width(), bounds.height(),
                    TEXTURES[i], actions[i]);
            button.setExternalAlpha(alpha);
            buttons.add(button);
        }
        return buttons;
    }

    /**
     * 发送维度池传送请求（7 个维度池按钮使用此方法）。
     * <p>
     * 通过 Fabric 网络包 {@link WorldPoolTeleportPayload} 向服务端发送目标池 ID，
     * 服务端接收后由 {@link top.csituka.youzaiworldcore.dimensionalinventories.DimensionPoolManager}
     * 执行完整的池切换流程。
     */
    private void requestPoolTeleport(MenuScreen screen, String buttonId) {
        String poolId = POOL_MAP.get(buttonId);
        if (poolId == null) {
            var player = Minecraft.getInstance().player;
            if (player != null) {
                player.sendSystemMessage(
                        Component.literal("§c未知的维度池按钮: " + buttonId));
            }
            return;
        }

        ConfirmationDialog dialog = new ConfirmationDialog(
                I18n.get("youzaiworldcore.message.gui.confirm_teleport_title"),
                new String[]{I18n.get("youzaiworldcore.message.gui.confirm_teleport_msg1"),
                        I18n.get("youzaiworldcore.message.gui.confirm_teleport_msg2")},
                () -> {
                    ClientPlayNetworking.send(new WorldPoolTeleportPayload(poolId));
                    Minecraft.getInstance().gui.setScreen(null);
                },
                null
        );
        screen.showDialog(dialog);
    }

    /**
     * 显示传送确认对话框（非维度池按钮使用，仅输出聊天提示）。
     * 保留给下界/末地/主世界等旧按钮。
     */
    private void showTeleportDialog(MenuScreen screen, String worldId) {
        ConfirmationDialog dialog = new ConfirmationDialog(
                I18n.get("youzaiworldcore.message.gui.confirm_teleport_title"),
                new String[]{I18n.get("youzaiworldcore.message.gui.confirm_teleport_msg1"), I18n.get("youzaiworldcore.message.gui.confirm_teleport_msg2")},
                () -> {
                    var player = Minecraft.getInstance().player;
                    if (player != null) {
                        player.connection.sendCommand("say 传送" + worldId);
                    }
                    Minecraft.getInstance().gui.setScreen(null);
                },
                null
        );
        screen.showDialog(dialog);
    }

}
