package top.csituka.youzaiworldcore.client.screen.element;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import top.csituka.youzaiworldcore.client.screen.MenuScreen;
import top.csituka.youzaiworldcore.client.screen.widget.ConfirmationDialog;
import top.csituka.youzaiworldcore.client.screen.widget.MenuNavigationButton;
import top.csituka.youzaiworldcore.client.render.MenuIcon;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import top.csituka.youzaiworldcore.client.render.YzuiTheme;
import top.csituka.youzaiworldcore.network.WorldPoolMenuRequestPayload;
import top.csituka.youzaiworldcore.network.WorldPoolMenuStatePayload;
import top.csituka.youzaiworldcore.network.WorldPoolTeleportResultPayload;

/** 世界入口共用主菜单的通透卡片网格；保留七个维度池与三个旧入口的操作。 */
public class SwitchWorldMenuElements implements MenuElementGroup {
    private Map<String, WorldPoolMenuStatePayload.Entry> worlds = Map.of();
    private String currentPoolId = "", currentName = "";
    private UUID metadataRequest, pendingRequest, lastTeleportRequest;
    private long requestedAt, metadataRequestedAt;
    private boolean ready, loadingMetadata;
    private Component status = text("loading");

    @Override
    public void onShown() {
        ready = false;
        pendingRequest = lastTeleportRequest = null;
        worlds = Map.of();
        currentPoolId = currentName = "";
        metadataRequest = UUID.randomUUID();
        metadataRequestedAt = System.currentTimeMillis();
        loadingMetadata = true;
        status = text("loading");
        if (!ClientPlayNetworking.canSend(WorldPoolMenuRequestPayload.ID)) {
            loadingMetadata = false;
            metadataRequest = null;
            status = text("unsupported");
            return;
        }
        ClientPlayNetworking.send(new WorldPoolMenuRequestPayload(metadataRequest, ""));
    }

    public void applyState(WorldPoolMenuStatePayload payload) {
        if (!payload.requestId().equals(metadataRequest) && !payload.requestId().equals(lastTeleportRequest)) return;
        worlds = payload.entries().stream().collect(java.util.stream.Collectors.toMap(
                WorldPoolMenuStatePayload.Entry::poolId, entry -> entry, (first, second) -> first));
        currentPoolId = payload.currentPoolId();
        currentName = payload.currentName();
        ready = true;
        loadingMetadata = false;
        if (payload.requestId().equals(metadataRequest) && pendingRequest == null) status = text("hint");
    }

    public void applyResult(MenuScreen screen, WorldPoolTeleportResultPayload payload) {
        if (payload.requestId().equals(metadataRequest)) {
            ready = false;
            loadingMetadata = false;
            status = payload.message();
            return;
        }
        if (!payload.requestId().equals(pendingRequest)) return;
        pendingRequest = null;
        status = payload.message();
        if (payload.success()) screen.closeMenu();
    }

    private static Component text(String key, Object... args) {
        return Component.translatable("screen.youzaiworldcore.world_menu." + key, args);
    }

    private static final Map<String, String> POOL_MAP = Map.of(
            "survival", "survival_world_pool",
            "kingdom", "main_city_pool",
            "gameplay", "gameplay_pool",
            "creative", "creation_pool",
            "building", "building_pool",
            "command", "commands_pool",
            "marketplace", "tutorial_world_pool"
    );

    private static final String[] ENTRIES = {
            "survival_world", "kingdom", "gameplay", "creative",
            "building", "tutorials_world", "the_nether", "the_end",
            "command_zone", "overworld"
    };

    private static final MenuIcon[] ICONS = {
            MenuIcon.WORLD, MenuIcon.HOME, MenuIcon.GAME, MenuIcon.CROWN,
            MenuIcon.BUILD, MenuIcon.BOOK, MenuIcon.PORTAL, MenuIcon.PORTAL,
            MenuIcon.COMMAND, MenuIcon.WORLD
    };

    @Override
    public String getTitleText() {
        return I18n.get("youzaiworldcore.message.gui.title_switch_world");
    }

    @Override
    public String getSubtitleText() {
        return text("current", currentName.isBlank()
                ? I18n.get("youzaiworldcore.message.gui.unknown") : currentName).getString();
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
        var grid = new MenuLayout(screenWidth, screenHeight - 24).navigation(ENTRIES.length);
        List<AbstractWidget> buttons = new ArrayList<>();
        for (int i = 0; i < ENTRIES.length; i++) {
            var bounds = grid.tile(i);
            var button = new MenuNavigationButton(bounds.x(), bounds.y(), bounds.width(), bounds.height(),
                    ENTRIES[i], ICONS[i], actions[i]);
            button.setExternalAlpha(alpha);
            buttons.add(button);
        }
        updateButtons(buttons);
        return buttons;
    }

    @Override
    public void updateButtons(List<AbstractWidget> buttons) {
        if (loadingMetadata && System.currentTimeMillis() - metadataRequestedAt > 15000) {
            loadingMetadata = false;
            status = text("load_timeout");
        }
        if (pendingRequest != null && System.currentTimeMillis() - requestedAt > 15000) {
            pendingRequest = null;
            status = text("timeout");
        }
        for (int i = 0; i < Math.min(buttons.size(), ENTRIES.length); i++) {
            String poolId = switch (i) {
                case 0 -> POOL_MAP.get("survival"); case 1 -> POOL_MAP.get("kingdom");
                case 2 -> POOL_MAP.get("gameplay"); case 3 -> POOL_MAP.get("creative");
                case 4 -> POOL_MAP.get("building"); case 5 -> POOL_MAP.get("marketplace");
                case 8 -> POOL_MAP.get("command"); default -> null;
            };
            if (poolId == null) continue;
            var button = (MenuNavigationButton) buttons.get(i);
            var world = worlds.get(poolId);
            boolean current = poolId.equals(currentPoolId);
            button.active = ready && pendingRequest == null && world != null && world.available() && !current;
            if (world != null) button.setMessage(Component.literal(world.name()));
            button.setDetail(!ready ? text(loadingMetadata ? "loading" : "unavailable") : current ? text("here")
                    : world == null || !world.available() ? text("unavailable")
                    : Component.translatable("gameMode." + world.gameMode()));
        }
    }

    @Override
    public void renderCustomContent(GuiGraphicsExtractor graphics, int width, int height, float alpha,
                                    float xOffset, int mouseX, int mouseY) {
        var font = Minecraft.getInstance().font;
        YzuiTheme.label(graphics, font, status, (width - new MenuLayout(width, height).contentWidth(640)) / 2,
                height - MenuLayout.FOOTER_MARGIN - 16, new MenuLayout(width, height).contentWidth(640),
                YzuiTheme.alpha(YzuiTheme.textMuted(), alpha), false);
    }

    /**
     * 发送维度池传送请求（7 个维度池按钮使用此方法）。
     * <p>
     * 通过 Fabric 网络包 {@link WorldPoolMenuRequestPayload} 向服务端发送目标池 ID，
     * 服务端接收后由 {@link top.csituka.youzaiworldcore.dimensionalinventories.DimensionPoolManager}
     * 执行完整的池切换流程。
     */
    private void requestPoolTeleport(MenuScreen screen, String buttonId) {
        String poolId = POOL_MAP.get(buttonId);
        var world = worlds.get(poolId);
        if (!ready || pendingRequest != null || world == null || !world.available() || poolId.equals(currentPoolId)) return;
        ConfirmationDialog dialog = new ConfirmationDialog(
                text("confirm_title", world.name()).getString(),
                new String[] {
                        text("confirm_mode", Component.translatable("gameMode." + world.gameMode()).getString()).getString(),
                        text("confirm_inventory").getString(), text("confirm_position").getString()
                }, () -> {
                    pendingRequest = UUID.randomUUID();
                    lastTeleportRequest = pendingRequest;
                    requestedAt = System.currentTimeMillis();
                    status = text("waiting", world.name());
                    ClientPlayNetworking.send(new WorldPoolMenuRequestPayload(pendingRequest, poolId));
                }, null);
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
