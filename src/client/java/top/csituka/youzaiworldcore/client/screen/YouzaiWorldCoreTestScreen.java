package top.csituka.youzaiworldcore.client.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import top.csituka.youzaiworldcore.client.animation.GuiAnimationController;
import top.csituka.youzaiworldcore.client.render.YzuiMenuPanel;
import top.csituka.youzaiworldcore.client.render.YzuiTheme;
import top.csituka.youzaiworldcore.client.screen.widget.MenuCloseButton;
import top.csituka.youzaiworldcore.client.screen.widget.TransparentButton;
import top.csituka.youzaiworldcore.util.DebugLogger;

/** 开发者测试入口：本地账户界面预览与原有测试环境分别选择。 */
public final class YouzaiWorldCoreTestScreen extends Screen implements YzuiMenuScreen {
    private final Screen parent;
    private final Runnable openTestEnvironment;
    private int panelX, panelY, panelWidth;
    private static final int PANEL_HEIGHT = 246;

    public YouzaiWorldCoreTestScreen(Screen parent, Runnable openTestEnvironment) {
        super(Component.translatable("title.youzaiworldcore.test_page"));
        this.parent = parent;
        this.openTestEnvironment = openTestEnvironment;
    }

    @Override
    protected void init() {
        panelWidth = Math.min(440, width - 40);
        panelX = (width - panelWidth) / 2;
        panelY = (height - PANEL_HEIGHT) / 2;
        int buttonX = panelX + 24, buttonWidth = panelWidth - 48;
        addRenderableWidget(new TransparentButton(buttonX, panelY + 82, buttonWidth, 30,
                text("login"), () -> openPreview(false)));
        addRenderableWidget(new TransparentButton(buttonX, panelY + 122, buttonWidth, 30,
                text("register"), () -> openPreview(true)));
        addRenderableWidget(new TransparentButton(buttonX, panelY + 188, buttonWidth, 30,
                text("environment"), openTestEnvironment).setStyle(YzuiTheme.ButtonStyle.TEXT));
        addRenderableWidget(new MenuCloseButton(panelX + panelWidth - 40, panelY + 14, this));
    }

    private void openPreview(boolean registration) {
        Minecraft client = Minecraft.getInstance();
        String name = client.player == null ? client.getUser().getName() : client.player.getName().getString();
        DebugLogger.debug("CoreTestScreen", "打开本地账户界面预览：%s", registration ? "注册" : "登入");
        client.setScreenAndShow(registration ? RegisterScreen.preview(name, this) : LoginScreen.preview(name, this));
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float tick) {
        YzuiMenuPanel.card(g, panelX, panelY, panelWidth, PANEL_HEIGHT);
        YzuiMenuPanel.header(g, font, title, text("description"), panelX, panelY, panelWidth);
        super.extractRenderState(g, mouseX, mouseY, tick);
    }

    @Override
    public void closeMenu() {
        GuiAnimationController.setScreenImmediately(Minecraft.getInstance().gui, parent);
    }

    @Override
    public void onClose() { closeMenu(); }

    @Override
    public boolean keyPressed(KeyEvent event) {
        return YzuiMenuScreen.handleEscape(this, event) || super.keyPressed(event);
    }

    @Override
    public boolean isPauseScreen() { return false; }

    private static Component text(String suffix) {
        return Component.translatable("screen.youzaiworldcore.test." + suffix);
    }
}
