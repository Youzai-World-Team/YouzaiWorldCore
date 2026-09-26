package top.csituka.youzaiworldcore.client.screen.title;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import top.csituka.youzaiworldcore.client.config.ClientExternalSettings;
import top.csituka.youzaiworldcore.client.config.ClientUpdateCheckerConfig;
import top.csituka.youzaiworldcore.client.config.YzuiThemeMode;
import top.csituka.youzaiworldcore.client.render.RoundedRect;
import top.csituka.youzaiworldcore.client.render.YzuiBrandLogo;
import top.csituka.youzaiworldcore.client.render.YzuiTheme;
import top.csituka.youzaiworldcore.client.screen.ForcedUpdateScreen;
import top.csituka.youzaiworldcore.client.screen.JoinBlockedScreen;
import top.csituka.youzaiworldcore.client.screen.QuitConfirmationScreen;
import top.csituka.youzaiworldcore.client.screen.widget.TransparentButton;
import top.csituka.youzaiworldcore.client.update.ClientUpdateState;
import top.csituka.youzaiworldcore.mixin.client.ScreenAccessor;
import top.csituka.youzaiworldcore.update.TitleScreenScrollState;
import top.csituka.youzaiworldcore.update.UpdateChecker;
import top.csituka.youzaiworldcore.update.UpdateResult;
import top.csituka.youzaiworldcore.util.DebugLogger;

/** 标题页的主题卡片与操作入口；保留服务器限制、更新提示及原版选项路由。 */
@SuppressWarnings("null")
public final class YzuiTitleMenu {
    private final TitleScreen screen;
    private final Minecraft minecraft;
    private final Font font;
    private final TitleMenuLayout layout;
    private final List<TransparentButton> buttons = new ArrayList<>();
    private final TransparentButton downloadButton;
    private final TransparentButton ignoreButton;
    private UpdateResult displayedUpdate;
    private List<FormattedCharSequence> bodyLines;

    public YzuiTitleMenu(TitleScreen screen) {
        this.screen = screen;
        ScreenAccessor accessor = (ScreenAccessor) screen;
        minecraft = accessor.youzaiworldcore$getMinecraft();
        font = accessor.youzaiworldcore$getFont();
        layout = TitleMenuLayout.of(screen.width, screen.height, ClientExternalSettings.isDevModeEnabled());
        accessor.youzaiworldcore$getChildren().clear();
        accessor.youzaiworldcore$getRenderables().clear();
        accessor.youzaiworldcore$getNarratables().clear();
        TitleScreenScrollState.reset();

        add(layout.join(), "title.youzaiworldcore.join_server", this::join, YzuiTheme.ButtonStyle.FILLED);
        add(layout.options(), "menu.options",
                () -> minecraft.gui.setScreen(new OptionsScreen(screen, minecraft.options, false)), YzuiTheme.ButtonStyle.TONAL);
        add(layout.quit(), "menu.quit",
                () -> minecraft.gui.setScreen(new QuitConfirmationScreen()), YzuiTheme.ButtonStyle.TEXT);
        if (ClientExternalSettings.isDevModeEnabled()) {
            add(layout.test(), "title.youzaiworldcore.test_page", this::test, YzuiTheme.ButtonStyle.TEXT);
        }
        downloadButton = add(layout.download(false), "title.youzaiworldcore.update_download_btn",
                this::download, YzuiTheme.ButtonStyle.FILLED);
        ignoreButton = add(layout.ignore(), "title.youzaiworldcore.update_ignore_btn",
                this::ignore, YzuiTheme.ButtonStyle.TEXT);
        downloadButton.visible = ignoreButton.visible = false;
        DebugLogger.info("YzuiTitleMenu", "标题页已初始化，尺寸 %d×%d，主题 %s",
                screen.width, screen.height, ClientExternalSettings.getYzuiTheme());
        if (ClientUpdateCheckerConfig.isCheckOnStartup()) {
            UpdateChecker.checkAsync().thenAccept(result -> {
                if (result != null) ClientUpdateState.set(result);
            });
        }
    }

    private TransparentButton add(TitleMenuLayout.Rect rect, String key, Runnable action, YzuiTheme.ButtonStyle style) {
        TransparentButton button = new TransparentButton(rect.x(), rect.y(), rect.width(), rect.height(),
                Component.translatable(key), action).setStyle(style);
        button.setTooltip(Tooltip.create(button.getMessage()));
        ScreenAccessor accessor = (ScreenAccessor) screen;
        accessor.youzaiworldcore$getChildren().add(button);
        accessor.youzaiworldcore$getRenderables().add(button);
        accessor.youzaiworldcore$getNarratables().add(button);
        buttons.add(button);
        return button;
    }

    /** 在原版全景之后、按钮之前绘制；透明度共用原版标题淡入值。 */
    public void render(GuiGraphicsExtractor g, float alpha) {
        var p = YzuiTheme.palette();
        for (TransparentButton button : buttons) button.setAlpha(alpha);
        if (alpha <= 0.01f) return;
        var logo = layout.logo();
        YzuiBrandLogo.render(g, YzuiBrandLogo.TEXTURE, logo.x(), logo.y(), logo.width(), alpha);
        renderSubtitle(g, alpha);
        card(g, layout.navigation(), alpha);
        card(g, layout.news(), alpha);
        int pad = layout.padding();
        label(g, Component.translatable("title.youzaiworldcore.play"),
                new TitleMenuLayout.Rect(layout.navigation().x() + pad, layout.navigation().y() + pad,
                        layout.navigation().width() - pad * 2, 10), p.primary(), alpha);
        label(g, Component.literal("play.mcyzw.top"),
                new TitleMenuLayout.Rect(layout.navigation().x() + pad, layout.navigation().y() + pad + 15,
                        layout.navigation().width() - pad * 2, 10), p.textMuted(), alpha);
        renderNews(g, alpha);
    }

    private void renderNews(GuiGraphicsExtractor g, float alpha) {
        UpdateResult update = visibleUpdate();
        boolean forced = update != null && update.forcedUpdate();
        var body = layout.body(update != null, forced);
        if (bodyLines == null || displayedUpdate != update) {
            displayedUpdate = update;
            bodyLines = wrapBody(update, body.width());
            TitleScreenScrollState.reset();
        }
        downloadButton.visible = update != null;
        ignoreButton.visible = update != null && !forced;
        downloadButton.active = update != null && update.downloadUrl() != null && !update.downloadUrl().isBlank();
        var download = layout.download(forced);
        downloadButton.setY(download.y());
        String heading = update == null ? "announcement_title" : forced ? "update_forced_title" : "update_title";
        label(g, Component.translatable("title.youzaiworldcore." + heading),
                new TitleMenuLayout.Rect(body.x(), layout.news().y() + layout.padding(), body.width() + 6, 10),
                forced ? YzuiTheme.error() : YzuiTheme.primary(), alpha);
        int lineHeight = font.lineHeight + 4;
        int contentHeight = Math.max(0, bodyLines.size() * lineHeight - 4);
        TitleScreenScrollState.setViewport(body.x(), body.y(), body.width() + 6, body.height(), contentHeight);
        int offset = (int) Math.round(TitleScreenScrollState.getScrollOffset());
        g.enableScissor(body.x(), body.y(), body.right(), body.bottom());
        try {
            for (int i = 0; i < bodyLines.size(); i++) {
                int y = body.y() + i * lineHeight - offset;
                if (y + font.lineHeight > body.y() && y < body.bottom()) {
                    g.text(font, bodyLines.get(i), body.x(), y, YzuiTheme.alpha(YzuiTheme.text(), alpha), false);
                }
            }
        } finally {
            g.disableScissor();
        }
        int max = TitleScreenScrollState.getMaxScroll();
        if (max > 0) {
            int thumb = Math.min(body.height(), Math.max(12, body.height() * body.height() / contentHeight));
            int top = body.y() + (int) Math.round(offset * (body.height() - thumb) / (double) max);
            RoundedRect.fill(g, body.right() + 3, body.y(), 3, body.height(), 1,
                    YzuiTheme.alpha(YzuiTheme.outlineVariant(), alpha * 0.5f));
            RoundedRect.fill(g, body.right() + 3, top, 3, thumb, 1, YzuiTheme.alpha(YzuiTheme.primary(), alpha));
        }
    }

    private List<FormattedCharSequence> wrapBody(UpdateResult update, int width) {
        List<Component> paragraphs = new ArrayList<>();
        if (update == null) {
            for (int i = 1; i <= 4; i++) paragraphs.add(Component.translatable("title.youzaiworldcore.announcement_line" + i));
        } else {
            String type = update.latestType();
            paragraphs.add(Component.translatable("title.youzaiworldcore.update_latest",
                    update.latestVersion() + (type == null || type.isEmpty() ? "" : " (" + type + ")")));
            String date = update.releaseDate() == null ? "" : update.releaseDate();
            String time = update.releaseTime() == null ? "" : update.releaseTime();
            if (!date.isEmpty() || !time.isEmpty()) paragraphs.add(Component.translatable("title.youzaiworldcore.update_released", date, time));
            if (update.changelog() != null && !update.changelog().isEmpty()) {
                paragraphs.add(Component.translatable("title.youzaiworldcore.update_changelog"));
                for (String line : update.changelog()) paragraphs.add(Component.literal(line));
            }
        }
        List<FormattedCharSequence> lines = new ArrayList<>();
        for (Component paragraph : paragraphs) {
            List<FormattedCharSequence> wrapped = font.split(paragraph, width);
            if (wrapped.isEmpty()) lines.add(FormattedCharSequence.EMPTY);
            else lines.addAll(wrapped);
        }
        return List.copyOf(lines);
    }

    /** 公告和更新正文同时支持键盘翻页，按钮继续使用原版 Tab 焦点导航。 */
    public boolean keyPressed(int key) {
        int max = TitleScreenScrollState.getMaxScroll();
        if (max == 0) return false;
        double offset = TitleScreenScrollState.getScrollOffset();
        int page = Math.max(12, TitleScreenScrollState.getViewportHeight() - font.lineHeight);
        switch (key) {
            case 266 -> offset -= page;
            case 267 -> offset += page;
            case 268 -> offset = 0;
            case 269 -> offset = max;
            default -> { return false; }
        }
        TitleScreenScrollState.setScrollOffset(offset);
        return true;
    }

    private static UpdateResult visibleUpdate() {
        if (!ClientUpdateCheckerConfig.isShowOnTitleScreen()) return null;
        UpdateResult result = ClientUpdateState.get();
        if (result == null || !result.updateAvailable()) return null;
        if (!result.forcedUpdate() && result.latestVersion() != null
                && result.latestVersion().equals(ClientExternalSettings.getIgnoredUpdateVersion())) return null;
        return result;
    }

    private void join() {
        UpdateResult update = visibleUpdate();
        if (update != null && update.forcedUpdate()) {
            minecraft.gui.setScreen(new ForcedUpdateScreen());
        } else if (JoinBlockedScreen.isBlocked()) {
            DebugLogger.info("YzuiTitleMenu", "开发版账号命中限制，显示加入限制弹窗");
            minecraft.gui.setScreen(new JoinBlockedScreen());
        } else {
            connect("Youzai World", "play.mcyzw.top");
        }
    }

    private void test() {
        if ("dedicated".equals(ClientExternalSettings.getDebugModeType())) {
            connect("Debug Server", ClientExternalSettings.getDebugAddress() + ":" + ClientExternalSettings.getDebugPort());
        } else {
            minecraft.gui.setScreen(new SelectWorldScreen(screen));
        }
    }

    private void connect(String name, String address) {
        ConnectScreen.startConnecting(screen, minecraft, ServerAddress.parseString(address),
                new ServerData(name, address, ServerData.Type.OTHER), false, null);
    }

    private void download() {
        UpdateResult result = visibleUpdate();
        if (result == null || result.downloadUrl() == null || result.downloadUrl().isBlank()) return;
        DebugLogger.info("YzuiTitleMenu", "显示更新下载链接确认");
        ConfirmLinkScreen.confirmLinkNow(screen, result.downloadUrl());
    }

    private void ignore() {
        UpdateResult result = visibleUpdate();
        if (result == null || result.forcedUpdate() || result.latestVersion() == null) return;
        ClientExternalSettings.setIgnoredUpdateVersion(result.latestVersion());
        DebugLogger.info("YzuiTitleMenu", "已忽略版本更新提示: %s", result.latestVersion());
    }

    private static void card(GuiGraphicsExtractor g, TitleMenuLayout.Rect rect, float alpha) {
        YzuiTheme.card(g, rect.x(), rect.y(), rect.width(), rect.height(), alpha);
    }

    private void renderSubtitle(GuiGraphicsExtractor g, float alpha) {
        var rect = layout.subtitle();
        String text = font.plainSubstrByWidth(Component.translatable("title.youzaiworldcore.subtitle").getString(),
                Math.max(0, rect.width() - 2));
        if (text.isEmpty()) return;
        boolean light = ClientExternalSettings.getYzuiTheme() == YzuiThemeMode.LIGHT;
        int outline = YzuiTheme.alpha(light ? 0xFFFFFFFF : 0xFF101B17, alpha * 0.9f);
        int foreground = YzuiTheme.alpha(light ? 0xFF1C3028 : 0xFFE3F0E7, alpha);
        int x = rect.x() + 1, y = rect.y();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                if (dx != 0 || dy != 0) g.text(font, text, x + dx, y + dy, outline, false);
            }
        }
        g.text(font, text, x, y, foreground, false);
    }

    private void label(GuiGraphicsExtractor g, Component text, TitleMenuLayout.Rect rect, int color, float alpha) {
        YzuiTheme.label(g, font, text, rect.x(), rect.y(), rect.width(), YzuiTheme.alpha(color, alpha), false);
    }
}
