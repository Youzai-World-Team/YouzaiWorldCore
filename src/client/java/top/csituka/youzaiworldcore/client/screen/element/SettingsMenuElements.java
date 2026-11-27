package top.csituka.youzaiworldcore.client.screen.element;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import top.csituka.youzaiworldcore.client.chat.ChatStyleMode;
import top.csituka.youzaiworldcore.client.config.ChatStyleSettings;
import top.csituka.youzaiworldcore.client.screen.MenuScreen;
import top.csituka.youzaiworldcore.client.screen.YouzaiWorldCoreSettingsScreen;
import top.csituka.youzaiworldcore.client.screen.YzuiAppearanceScreen;
import top.csituka.youzaiworldcore.client.screen.widget.CheckboxButton;
import top.csituka.youzaiworldcore.client.screen.widget.TransparentButton;
import top.csituka.youzaiworldcore.highlightitem.Configurator;
import top.csituka.youzaiworldcore.highlightitem.HighlightItem;
import top.csituka.youzaiworldcore.network.PlayerSettingsRequestPayload;
import top.csituka.youzaiworldcore.network.PlayerSettingsRequestPayload.Option;
import top.csituka.youzaiworldcore.network.PlayerSettingsStatePayload;

/** Shift+F 设置页，控制本机显示与当前玩家自己的玩法偏好。 */
@SuppressWarnings("null")
public class SettingsMenuElements implements MenuElementGroup {
    private static final String TRANSLATION_PREFIX = "screen.youzaiworldcore.settings.player.";
    private static final long REQUEST_TIMEOUT = TimeUnit.SECONDS.toNanos(15);

    private UUID pendingRequest;
    private long sentAt;
    private PlayerSettingsStatePayload state;
    private ServerStatus status;

    private enum ServerStatus {
        READY(null),
        UNAVAILABLE("unavailable"),
        WAITING("waiting"),
        TIMED_OUT("timeout");

        private final Tooltip tooltip;

        ServerStatus(String key) {
            tooltip = key == null ? null : Tooltip.create(text(key));
        }
    }

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
    public void onShown() {
        state = null;
        pendingRequest = null;
        request(Option.QUERY, false);
    }

    /** 接受当前页最近一次请求的服务端状态。 */
    public void applyState(PlayerSettingsStatePayload payload) {
        if (payload.requestId().equals(pendingRequest)) {
            state = payload;
            pendingRequest = null;
        }
    }

    @Override
    public List<AbstractWidget> createButtons(MenuScreen screen, int screenWidth, int screenHeight, float scale, float alpha) {
        var client = Minecraft.getInstance();
        var layout = new MenuLayout(screenWidth, screenHeight);
        int gap = 6;
        int rowHeight = Math.max(1, Math.min(30, (layout.contentHeight() - gap * 2) / 3));
        int left = layout.left(440);
        int columnWidth = (layout.contentWidth(440) - gap) / 2;
        int right = left + columnWidth + gap;
        int top = layout.centeredTop(rowHeight * 3 + gap * 2);
        int middle = top + rowHeight + gap;
        int bottom = middle + rowHeight + gap;

        var chatStyle = new CheckboxButton(left, top, columnWidth, rowHeight, text("gradient_chat"),
                ChatStyleSettings.getMode() == ChatStyleMode.GRADIENT, () -> {
                    ChatStyleSettings.setMode(ChatStyleSettings.getMode() == ChatStyleMode.GRADIENT
                            ? ChatStyleMode.PLAIN : ChatStyleMode.GRADIENT);
                    client.gui.hud.getChat().rescaleChat();
                }).setWrapMessage(true);
        var highlight = new CheckboxButton(right, top, columnWidth, rowHeight, text("highlight_item"),
                Configurator.TOGGLE, () -> HighlightItem.configurator.updateToggle(
                        client.player, Configurator.NotificationContext.NONE)).setWrapMessage(true);
        var doubleDoors = new CheckboxButton(left, middle, columnWidth, rowHeight, text("double_doors"),
                false, () -> request(Option.DOUBLE_DOORS, !state.doubleDoors())).setWrapMessage(true);
        var damageNumbers = new CheckboxButton(right, middle, columnWidth, rowHeight, text("damage_numbers"),
                false, () -> request(Option.DAMAGE_NUMBERS, !state.damageNumbers())).setWrapMessage(true);

        List<AbstractWidget> buttons = List.of(chatStyle, highlight, doubleDoors, damageNumbers,
                new TransparentButton(left, bottom, columnWidth, rowHeight,
                        Component.translatable("options.youzaiworldcore.settings"),
                        () -> client.gui.setScreen(new YouzaiWorldCoreSettingsScreen(screen))),
                new TransparentButton(right, bottom, columnWidth, rowHeight,
                        Component.translatable("screen.youzaiworldcore.appearance.title"),
                        () -> client.gui.setScreen(new YzuiAppearanceScreen(screen))));
        for (AbstractWidget button : buttons) {
            button.setAlpha(alpha);
        }
        // 重排会创建新控件，当前请求状态的提示也需要重新绑定。
        status = null;
        return buttons;
    }

    @Override
    public void updateButtons(List<AbstractWidget> buttons) {
        ((CheckboxButton) buttons.get(0)).setChecked(ChatStyleSettings.getMode() == ChatStyleMode.GRADIENT);
        ((CheckboxButton) buttons.get(1)).setChecked(Configurator.TOGGLE);

        var doubleDoors = (CheckboxButton) buttons.get(2);
        var damageNumbers = (CheckboxButton) buttons.get(3);
        doubleDoors.setChecked(state != null && state.doubleDoors());
        damageNumbers.setChecked(state != null && state.damageNumbers());

        ServerStatus next = serverStatus();
        doubleDoors.active = damageNumbers.active = state != null
                && (next == ServerStatus.READY || next == ServerStatus.TIMED_OUT);
        if (next != status) {
            status = next;
            doubleDoors.setTooltip(next.tooltip);
            damageNumbers.setTooltip(next.tooltip);
        }
    }

    private ServerStatus serverStatus() {
        if (!canRequest()) {
            return ServerStatus.UNAVAILABLE;
        }
        if (pendingRequest != null) {
            return isWaiting() ? ServerStatus.WAITING : ServerStatus.TIMED_OUT;
        }
        return ServerStatus.READY;
    }

    private boolean isWaiting() {
        return pendingRequest != null && System.nanoTime() - sentAt < REQUEST_TIMEOUT;
    }

    private void request(Option option, boolean enabled) {
        if (!canRequest() || isWaiting()) {
            return;
        }
        pendingRequest = UUID.randomUUID();
        sentAt = System.nanoTime();
        ClientPlayNetworking.send(new PlayerSettingsRequestPayload(pendingRequest, option, enabled));
    }

    private static boolean canRequest() {
        return Minecraft.getInstance().getConnection() != null
                && ClientPlayNetworking.canSend(PlayerSettingsRequestPayload.ID);
    }

    private static Component text(String key) {
        return Component.translatable(TRANSLATION_PREFIX + key);
    }
}
