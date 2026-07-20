package top.csituka.youzaiworldcore.client.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.csituka.youzaiworldcore.client.screen.widget.TransparentButton;
import top.csituka.youzaiworldcore.util.DebugLogger;

/**
 * 配置导入成功弹窗 — 不可 ESC 关闭，仅提供「关闭客户端」按钮强制重启。
 * <p>
 * 设计稿 §5.5：唯一关闭方式是点击按钮调用 {@link Minecraft#stop()}，确保配置立即生效。
 * </p>
 */
@SuppressWarnings("null")
public class ConfigImportSuccessScreen extends Screen {

    private static final Logger LOGGER = LoggerFactory.getLogger("YouzaiWorldCore/ConfigImportSuccessScreen");
    private static final String LOG_MODULE = "ConfigImportSuccessScreen";

    // ============ 布局常量 ============
    private static final int DIALOG_WIDTH = 260;
    private static final int DIALOG_HEIGHT = 120;
    private static final int CORNER_RADIUS = 6;
    private static final int BUTTON_WIDTH = 120;
    private static final int BUTTON_HEIGHT = 24;

    private TransparentButton quitButton;

    public ConfigImportSuccessScreen() {
        super(Component.translatable("screen.youzaiworldcore.config_io.import_success_title"));
        LOGGER.info("配置导入成功弹窗已打开");
    }

    @Override
    protected void init() {
        super.init();
        DebugLogger.debug(LOG_MODULE, "初始化配置导入成功弹窗");

        int dialogX = (this.width - DIALOG_WIDTH) / 2;
        int dialogY = (this.height - DIALOG_HEIGHT) / 2;

        int buttonX = dialogX + (DIALOG_WIDTH - BUTTON_WIDTH) / 2;
        int buttonY = dialogY + DIALOG_HEIGHT - BUTTON_HEIGHT - 20;

        this.quitButton = new TransparentButton(
                buttonX, buttonY, BUTTON_WIDTH, BUTTON_HEIGHT,
                Component.translatable("screen.youzaiworldcore.config_io.import_success_quit"),
                this::onQuit
        );
        this.quitButton.setTextColor(0x000000);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTick) {
        // 全屏半透明黑色遮罩
        guiGraphics.fill(0, 0, this.width, this.height, 0x80000000);

        int dialogX = (this.width - DIALOG_WIDTH) / 2;
        int dialogY = (this.height - DIALOG_HEIGHT) / 2;

        // 白色圆角背景
        fillRoundedRect(guiGraphics, dialogX, dialogY, DIALOG_WIDTH, DIALOG_HEIGHT, CORNER_RADIUS, 0xBFFFFFFF);

        // 标题
        String title = Component.translatable("screen.youzaiworldcore.config_io.import_success_title").getString();
        int titleWidth = this.font.width(title);
        guiGraphics.text(this.font, title,
                dialogX + (DIALOG_WIDTH - titleWidth) / 2,
                dialogY + 18, 0xFF000000, false);

        // 提示文本
        String hint1 = Component.translatable("screen.youzaiworldcore.config_io.import_success_hint1").getString();
        String hint2 = Component.translatable("screen.youzaiworldcore.config_io.import_success_hint2").getString();
        int hint1Width = this.font.width(hint1);
        int hint2Width = this.font.width(hint2);
        guiGraphics.text(this.font, hint1,
                dialogX + (DIALOG_WIDTH - hint1Width) / 2,
                dialogY + 45, 0xFF000000, false);
        guiGraphics.text(this.font, hint2,
                dialogX + (DIALOG_WIDTH - hint2Width) / 2,
                dialogY + 58, 0xFF000000, false);

        // 按钮
        if (this.quitButton != null) {
            this.quitButton.extractRenderState(guiGraphics, mouseX, mouseY, partialTick);
        }
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTick) {
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean isActuallyClick) {
        double mx = event.x();
        double my = event.y();

        if (this.quitButton != null && isClicked(this.quitButton, mx, my)) {
            this.quitButton.onClick(event, isActuallyClick);
            return true;
        }
        return true; // 点击外部不穿透
    }

    @Override
    public boolean keyPressed(KeyEvent keyEvent) {
        // ESC 无响应 — 必须点击按钮关闭
        return true;
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false; // 不可 ESC 关闭
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ========== 回调 ==========

    private void onQuit() {
        DebugLogger.info(LOG_MODULE, "用户确认关闭客户端，配置文件将在下次启动时生效");
        LOGGER.info("用户确认关闭客户端");
        Minecraft.getInstance().stop();
    }

    // ========== 工具 ==========

    private boolean isClicked(TransparentButton button, double mx, double my) {
        return mx >= button.getX() && mx < button.getX() + button.getWidth()
                && my >= button.getY() && my < button.getY() + button.getHeight();
    }

    private void fillRoundedRect(GuiGraphicsExtractor g, int x, int y, int w, int h, int r, int color) {
        g.fill(x + r, y, x + w - r, y + h, color);
        g.fill(x, y + r, x + r, y + h - r, color);
        g.fill(x + w - r, y + r, x + w, y + h - r, color);

        for (int i = 0; i < r; i++) {
            for (int j = 0; j < r; j++) {
                int dx = r - 1 - i;
                int dy = r - 1 - j;
                if (dx * dx + dy * dy < r * r) {
                    g.fill(x + i, y + j, x + i + 1, y + j + 1, color);
                    g.fill(x + w - 1 - i, y + j, x + w - i, y + j + 1, color);
                    g.fill(x + i, y + h - 1 - j, x + i + 1, y + h - j, color);
                    g.fill(x + w - 1 - i, y + h - 1 - j, x + w - i, y + h - j, color);
                }
            }
        }
    }
}
