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

/** 世界切换页按顺序展示七个维度池。 */
public class SwitchWorldMenuElements implements MenuElementGroup {
    @SuppressWarnings("null")
    private static final List<Destination> DESTINATIONS = List.of(
            new Destination("survival_world", MenuIcon.WORLD, "survival_world_pool"),
            new Destination("kingdom", MenuIcon.HOME, "main_city_pool"),
            new Destination("gameplay", MenuIcon.GAME, "gameplay_pool"),
            new Destination("creative", MenuIcon.CROWN, "creation_pool"),
            new Destination("building", MenuIcon.BUILD, "building_pool"),
            new Destination("tutorials_world", MenuIcon.BOOK, "tutorial_world_pool"),
            new Destination("command_zone", MenuIcon.COMMAND, "commands_pool"));

    private record Destination(String navigationId, MenuIcon icon, String poolId) {
    }

    private Map<String, WorldPoolMenuStatePayload.Entry> worlds = Map.of();
    private String currentPoolId = "", currentName = "";
    private UUID metadataRequest, pendingRequest, lastTeleportRequest;
    private long requestedAt, metadataRequestedAt;
    private boolean ready, loadingMetadata;
    private Component status = text("loading");

    @SuppressWarnings("null")
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

    @SuppressWarnings("null")
    public void applyState(WorldPoolMenuStatePayload payload) {
        if (!payload.requestId().equals(metadataRequest) && !payload.requestId().equals(lastTeleportRequest))
            return;
        worlds = payload.entries().stream().collect(java.util.stream.Collectors.toMap(
                WorldPoolMenuStatePayload.Entry::poolId, entry -> entry, (first, second) -> first));
        currentPoolId = payload.currentPoolId();
        currentName = payload.currentName();
        ready = true;
        loadingMetadata = false;
        if (payload.requestId().equals(metadataRequest) && pendingRequest == null)
            status = text("hint");
    }

    public void applyResult(MenuScreen screen, WorldPoolTeleportResultPayload payload) {
        if (payload.requestId().equals(metadataRequest)) {
            ready = false;
            loadingMetadata = false;
            status = payload.message();
            return;
        }
        if (!payload.requestId().equals(pendingRequest))
            return;
        pendingRequest = null;
        status = payload.message();
        if (payload.success())
            screen.closeMenu();
    }

    @SuppressWarnings("null")
    private static Component text(String key, Object... args) {
        return Component.translatable("screen.youzaiworldcore.world_menu." + key, args);
    }

    @Override
    public String getTitleText() {
        return I18n.get("youzaiworldcore.message.gui.title_switch_world");
    }

    @Override
    public String getSubtitleText() {
        return text("current", currentName.isBlank()
                ? I18n.get("youzaiworldcore.message.gui.unknown")
                : currentName).getString();
    }

    @Override
    public boolean isRoot() {
        return false;
    }

    @Override
    public List<AbstractWidget> createButtons(MenuScreen screen, int screenWidth, int screenHeight,
            float scale, float alpha) {
        var grid = new MenuLayout(screenWidth, screenHeight - 24).navigation(DESTINATIONS.size());
        List<AbstractWidget> buttons = new ArrayList<>();
        for (int i = 0; i < DESTINATIONS.size(); i++) {
            var destination = DESTINATIONS.get(i);
            var bounds = grid.tile(i);
            var button = new MenuNavigationButton(bounds.x(), bounds.y(), bounds.width(), bounds.height(),
                    destination.navigationId(), destination.icon(),
                    () -> requestPoolTeleport(screen, destination.poolId()));
            button.setExternalAlpha(alpha);
            buttons.add(button);
        }
        updateButtons(buttons);
        return buttons;
    }

    @SuppressWarnings("null")
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
        for (int i = 0; i < Math.min(buttons.size(), DESTINATIONS.size()); i++) {
            String poolId = DESTINATIONS.get(i).poolId();
            var button = (MenuNavigationButton) buttons.get(i);
            var world = worlds.get(poolId);
            boolean current = poolId.equals(currentPoolId);
            button.active = ready && pendingRequest == null && world != null && world.available() && !current;
            if (world != null)
                button.setMessage(Component.literal(world.name()));
            button.setDetail(!ready ? text(loadingMetadata ? "loading" : "unavailable")
                    : current ? text("here")
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
     * 服务端接收后由
     * {@link top.csituka.youzaiworldcore.dimensionalinventories.DimensionPoolManager}
     * 执行完整的池切换流程。
     */
    private void requestPoolTeleport(MenuScreen screen, String poolId) {
        var world = worlds.get(poolId);
        if (!ready || pendingRequest != null || world == null || !world.available() || poolId.equals(currentPoolId))
            return;
        ConfirmationDialog dialog = new ConfirmationDialog(
                text("confirm_title", world.name()).getString(),
                new String[] {
                        text("confirm_mode", Component.translatable("gameMode." + world.gameMode()).getString())
                                .getString(),
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

}
