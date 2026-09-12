package top.csituka.youzaiworldcore.client.screen.options;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import top.csituka.youzaiworldcore.client.render.RoundedRect;
import top.csituka.youzaiworldcore.client.render.YzuiTheme;
import top.csituka.youzaiworldcore.client.screen.widget.TransparentButton;

/** 设置页面共用外壳。宽屏显示分组导航，窄屏保留完整搜索、内容与操作区。 */
final class SettingsFrame {
    final int bodyX, bodyY, bodyWidth, bodyHeight;
    final SettingsList content;
    final EditBox search;
    private final Screen screen;
    private final int x, y, width, height, navigationWidth;
    private final List<AbstractWidget> chrome = new ArrayList<>();
    private final Consumer<AbstractWidget> addWidget;
    private SettingsList navigation;
    private final List<AbstractWidget> footer;
    private final TransparentButton back;

    SettingsFrame(Screen screen, List<SettingsList.Row> rows, List<AbstractWidget> footer,
                  Consumer<AbstractWidget> addWidget, String query, double scroll, boolean searchable) {
        this.screen = screen;
        this.addWidget = addWidget;
        this.footer = List.copyOf(footer);
        width = Math.min(1000, screen.width - 24);
        height = screen.height - 20;
        x = (screen.width - width) / 2;
        y = 10;
        navigationWidth = searchable && width >= 620 && rows.stream().anyMatch(row -> row.navigation) ? 126 : 0;
        bodyX = x + 12 + (navigationWidth == 0 ? 0 : navigationWidth + 12);
        bodyY = y + 72;
        bodyWidth = width - 24 - (navigationWidth == 0 ? 0 : navigationWidth + 12);
        bodyHeight = Math.max(40, height - 116);

        back = new TransparentButton(x + 12, y + 12, 28, 26, Component.literal("‹"), screen::onClose);
        back.setStyle(YzuiTheme.ButtonStyle.TEXT);
        back.setTooltip(Tooltip.create(Component.translatable("gui.back")));
        back.active = screen.shouldCloseOnEsc();
        addChrome(back);
        Component searchLabel = screen instanceof YzuiOptionsScreen options && options.isUnified()
                ? YzuiOptionsScreen.text("search.all") : YzuiOptionsScreen.text("search");
        search = new EditBox(Minecraft.getInstance().font, bodyX + 2, y + 43,
                Math.max(30, bodyWidth - 4), 20, searchLabel);
        search.setHint(searchLabel);
        search.setMaxLength(128);
        search.setValue(query);
        search.visible = searchable;
        search.active = searchable;
        if (searchable) addChrome(search);

        content = new SettingsList(bodyX, bodyY, bodyWidth, bodyHeight);
        content.setRows(rows, query);
        content.setScrollAmount(scroll);
        addWidget.accept(content);
        search.setResponder(content::filter);
        rebuildNavigation(rows);

        int total = Math.max(1, footer.size());
        int buttonWidth = Math.min(170, Math.max(30, (bodyWidth - (total - 1) * 8) / total));
        int left = bodyX + bodyWidth - (buttonWidth * total + (total - 1) * 8);
        for (int i = 0; i < footer.size(); i++) {
            AbstractWidget button = footer.get(i);
            button.setRectangle(buttonWidth, 24, left + i * (buttonWidth + 8), y + height - 34);
            addChrome(button);
        }
    }

    private void addChrome(AbstractWidget widget) {
        chrome.add(widget);
        addWidget.accept(widget);
    }

    private void rebuildNavigation(List<SettingsList.Row> rows) {
        if (navigationWidth == 0) return;
        navigation = new SettingsList(x + 12, bodyY, navigationWidth, bodyHeight);
        List<SettingsList.Row> navigationRows = new ArrayList<>();
        TransparentButton all = new TransparentButton(0, 0, 100, 24, YzuiOptionsScreen.text("all"), () -> {
            search.setValue("");
            content.setScrollAmount(0);
        });
        all.setStyle(YzuiTheme.ButtonStyle.FILLED);
        navigationRows.add(SettingsList.Row.widget(all));
        for (SettingsList.Row row : rows) {
            if (!row.navigation) continue;
            TransparentButton button = new TransparentButton(0, 0, 100, 24, row.label, () -> {
                search.setValue("");
                content.reveal(row);
            });
            button.setStyle(YzuiTheme.ButtonStyle.TEXT);
            button.setTextLeftAligned(true);
            navigationRows.add(SettingsList.Row.widget(button));
        }
        navigation.setRows(navigationRows, "");
        addWidget.accept(navigation);
    }

    void render(GuiGraphicsExtractor g) {
        YzuiTheme.card(g, x, y, width, height);
        var font = Minecraft.getInstance().font;
        YzuiTheme.label(g, font, screen.getTitle(), x + 48, y + 19, width - 140, YzuiTheme.text(), false);
        RoundedRect.fill(g, x + width - 66, y + 14, 50, 19, 9, YzuiTheme.primaryContainer());
        g.text(font, "YZUI", x + width - 55, y + 19, YzuiTheme.onPrimaryContainer(), false);
        if (navigationWidth > 0) {
            g.text(font, YzuiOptionsScreen.text("sections"), x + 20, y + 49, YzuiTheme.textMuted(), false);
            RoundedRect.fill(g, x + 12, bodyY, navigationWidth, bodyHeight, 10, YzuiTheme.surfaceLow());
        }
        if (content.children().isEmpty() && content.visible) {
            YzuiTheme.label(g, font, YzuiOptionsScreen.text("empty"), bodyX + 12, bodyY + 20,
                    bodyWidth - 24, YzuiTheme.textMuted(), true);
        }
    }

    void setChromeActive(boolean active) {
        for (AbstractWidget widget : chrome) {
            // 原版完成按钮的禁用状态由语言、游戏规则等各自维护。
            if (!footer.contains(widget)) widget.active = active;
        }
        if (navigation != null) navigation.active = active;
    }

    void updateBackState() { back.active = screen.shouldCloseOnEsc(); }

    void setBusy(Component message) {
        boolean active = message == null;
        setChromeActive(active);
        for (AbstractWidget button : footer) {
            button.active = active;
            button.setMessage(active ? Component.translatable("gui.done") : message);
        }
        updateBackState();
    }

    void restoreFocus(AbstractWidget widget) {
        if (widget == null) return;
        if (chrome.contains(widget)) {
            screen.setFocused(widget);
            return;
        }
        for (SettingsList.Row row : content.children()) {
            if (row.widgets.contains(widget)) {
                screen.setFocused(content);
                content.setFocused(row);
                row.setFocused(widget);
                return;
            }
        }
    }
}
