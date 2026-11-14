package top.csituka.youzaiworldcore.client.screen.pause;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import top.csituka.youzaiworldcore.account.util.AuthPlayerHelper;
import top.csituka.youzaiworldcore.client.config.ClientExternalSettings;
import top.csituka.youzaiworldcore.client.render.MenuIcon;
import top.csituka.youzaiworldcore.client.render.YzuiMenuPanel;
import top.csituka.youzaiworldcore.client.render.YzuiTheme;
import top.csituka.youzaiworldcore.client.screen.AccountManagementScreen;
import top.csituka.youzaiworldcore.client.screen.widget.PauseButtonStyle;
import top.csituka.youzaiworldcore.client.screen.widget.WidgetFocus;
import top.csituka.youzaiworldcore.mixin.client.ScreenAccessor;
import top.csituka.youzaiworldcore.util.DebugLogger;

/** 原版暂停屏幕的 YZUI 外壳；只负责布局与外观，暂停、退出和设置继续由原版执行。 */
@SuppressWarnings("null")
public final class YzuiPauseMenu {
    private final PauseScreen screen;
    private final PauseMenuLayout layout;
    private final List<Button> buttons = new ArrayList<>();
    private String lastFocus;

    public YzuiPauseMenu(PauseScreen screen, String previousFocus) {
        this.screen = screen;
        lastFocus = previousFocus;
        Minecraft client = Minecraft.getInstance();
        boolean accountAllowed = !(client.hasSingleplayerServer() && ClientExternalSettings.isDevModeEnabled()
                && client.player != null && AuthPlayerHelper.isDeveloperPlayerName(client.player.getScoreboardName()));
        layout = PauseMenuLayout.of(screen.width, screen.height, accountAllowed);
        ScreenAccessor accessor = (ScreenAccessor) screen;
        // 保留原版按钮实例，disconnectButton 字段及异步回调不会指向已被替换的控件。
        for (var child : List.copyOf(screen.children())) {
            if (!(child instanceof AbstractWidget widget)) continue;
            String key = translationKey(widget.getMessage());
            PauseMenuLayout.Rect rect = rect(key);
            if (child instanceof Button button && rect != null) {
                button.setRectangle(rect.width(), rect.height(), rect.x(), rect.y());
                button.setTooltip(Tooltip.create(button.getMessage()));
                PauseButtonStyle.set(button, icon(key), style(key));
                buttons.add(button);
            } else {
                accessor.youzaiworldcore$getChildren().remove(child);
                accessor.youzaiworldcore$getRenderables().remove(child);
                accessor.youzaiworldcore$getNarratables().remove(child);
            }
        }
        if (accountAllowed) {
            var rect = layout.account();
            Button account = Button.builder(Component.translatable("screen.youzaiworldcore.account_management.button_open"),
                    button -> client.setScreenAndShow(new AccountManagementScreen(screen)))
                    .bounds(rect.x(), rect.y(), rect.width(), rect.height()).build();
            account.setTooltip(Tooltip.create(account.getMessage()));
            PauseButtonStyle.set(account, MenuIcon.ACCOUNT, YzuiTheme.ButtonStyle.TONAL);
            accessor.youzaiworldcore$getChildren().add(account);
            accessor.youzaiworldcore$getRenderables().add(account);
            accessor.youzaiworldcore$getNarratables().add(account);
            buttons.add(account);
        }
        // Tab 的顺序与从上到下的视觉顺序一致；方向键按真实位置移动。
        buttons.sort(java.util.Comparator.comparingInt(Button::getY).thenComparingInt(Button::getX));
        var children = accessor.youzaiworldcore$getChildren();
        children.removeAll(buttons);
        children.addAll(buttons);
        restoreFocus();
        DebugLogger.info("YzuiPauseMenu", "暂停屏幕已初始化：%d×%d，玩家卡片=%s，账户入口=%s",
                screen.width, screen.height, layout.profile().width() > 0, accountAllowed);
    }

    /** 在原版控件之前绘制卡片与玩家预览；原版音乐和举报草稿提示仍由暂停屏幕绘制。 */
    public void render(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        lastFocus = focusedKey();
        var font = ((ScreenAccessor) screen).youzaiworldcore$getFont();
        var actions = layout.actions();
        var p = YzuiTheme.palette();
        int text = YzuiMenuPanel.textOn(p.text(), p.surface());
        int muted = YzuiMenuPanel.textOn(p.textMuted(), p.surface());
        YzuiMenuPanel.card(g, actions.x(), actions.y(), actions.width(), actions.height());
        int x = actions.x() + layout.padding(), y = actions.y() + layout.padding();
        MenuIcon.GAME.render(g, x + 2, y + 2, 12, YzuiMenuPanel.textOn(p.primary(), p.surface()));
        YzuiTheme.label(g, font, screen.getTitle(), x + 26, y + 3,
                actions.width() - layout.padding() * 2 - 26, text, false);
        if (layout.footerY() >= 0) {
            // 底部提示直接位于世界画面上，沿用 HUD 的字形阴影。
            Component hint = Component.translatable("screen.youzaiworldcore.pause.hint");
            YzuiTheme.hudText(g, font, hint.getVisualOrderText(),
                    actions.x() + (actions.width() - font.width(hint)) / 2,
                    layout.footerY(), p.text(), 1f);
        }
        renderProfile(g, mouseX, mouseY, text, muted);
    }

    private void renderProfile(GuiGraphicsExtractor g, int mouseX, int mouseY, int text, int muted) {
        var profile = layout.profile();
        if (profile.width() == 0) return;
        var client = Minecraft.getInstance();
        var font = ((ScreenAccessor) screen).youzaiworldcore$getFont();
        YzuiMenuPanel.card(g, profile.x(), profile.y(), profile.width(), profile.height());
        MenuIcon.ACCOUNT.render(g, profile.x() + 14, profile.y() + 16, 12, muted);
        YzuiTheme.label(g, font, Component.translatable("screen.youzaiworldcore.pause.profile"),
                profile.x() + 34, profile.y() + 17, profile.width() - 46, muted, false);
        Component name = client.player == null ? Component.empty() : client.player.getName();
        YzuiTheme.label(g, font, name, profile.x() + 12, profile.y() + 40, profile.width() - 24, text, true);
        var server = client.getCurrentServer();
        Component session = server == null ? Component.translatable("screen.youzaiworldcore.pause.singleplayer")
                : Component.literal(server.name);
        YzuiTheme.label(g, font, session, profile.x() + 12, profile.y() + 56, profile.width() - 24, muted, true);
        if (client.player == null) return;
        var model = layout.model();
        g.enableScissor(model.x(), model.y(), model.right(), model.bottom());
        try {
            InventoryScreen.extractEntityInInventoryFollowsMouse(g, model.x(), model.y(), model.right(), model.bottom(),
                    Math.min(54, Math.max(20, (model.height() - 12) / 2)), 0.0625f, mouseX, mouseY, client.player);
        } finally {
            g.disableScissor();
        }
    }

    /** 与原版 Screen 的焦点对象同步，让回车和朗读跟随方向键选中的按钮。 */
    public boolean moveFocus(KeyEvent event) {
        if (!WidgetFocus.moveInGrid(event, buttons, true)) return false;
        buttons.stream().filter(Button::isFocused).findFirst().ifPresent(screen::setFocused);
        lastFocus = focusedKey();
        return true;
    }

    /** 重建窗口前保留当前入口，避免 GUI 比例或返回设置后丢失焦点。 */
    public String focusedKey() {
        return buttons.stream().filter(Button::isFocused).findFirst()
                .map(button -> translationKey(button.getMessage())).orElse(lastFocus);
    }

    /** 原版窗口重建会先清除旧控件焦点，使用上一帧记录恢复实际入口。 */
    public boolean restoreFocus() {
        if (lastFocus == null) return false;
        var button = buttons.stream().filter(value -> value.active && value.visible
                && lastFocus.equals(translationKey(value.getMessage()))).findFirst().orElse(null);
        if (button == null) return false;
        screen.setFocused(button);
        return true;
    }

    private PauseMenuLayout.Rect rect(String key) {
        if (key == null) return null;
        return switch (key) {
            case "menu.returnToGame" -> layout.resume();
            case "gui.advancements" -> layout.advancements();
            case "gui.stats" -> layout.statistics();
            case "menu.options" -> layout.options();
            case "menu.returnToMenu", "menu.disconnect" -> layout.disconnect();
            default -> null;
        };
    }

    private static MenuIcon icon(String key) {
        return switch (key) {
            case "menu.returnToGame" -> MenuIcon.GAME;
            case "gui.advancements" -> MenuIcon.CROWN;
            case "gui.stats" -> MenuIcon.LEVEL;
            case "menu.options" -> MenuIcon.SETTINGS;
            default -> MenuIcon.EXIT;
        };
    }

    private static YzuiTheme.ButtonStyle style(String key) {
        return switch (key) {
            case "menu.returnToGame" -> YzuiTheme.ButtonStyle.FILLED;
            case "menu.returnToMenu", "menu.disconnect" -> YzuiTheme.ButtonStyle.DANGER;
            default -> YzuiTheme.ButtonStyle.TONAL;
        };
    }

    private static String translationKey(Component message) {
        if (message.getContents() instanceof TranslatableContents contents) return contents.getKey();
        for (Component sibling : message.getSiblings()) {
            String key = translationKey(sibling);
            if (key != null) return key;
        }
        return null;
    }
}
