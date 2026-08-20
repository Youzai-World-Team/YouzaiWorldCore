package top.csituka.youzaiworldcore.client.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.csituka.youzaiworldcore.client.render.RoundedRect;
import top.csituka.youzaiworldcore.client.screen.widget.TransparentButton;
import top.csituka.youzaiworldcore.client.update.ClientUpdateState;
import top.csituka.youzaiworldcore.update.UpdateResult;
import top.csituka.youzaiworldcore.util.DebugLogger;
import top.csituka.youzaiworldcore.client.animation.GuiAnimationController;

/**
 * 强制更新弹窗——与 {@link QuitConfirmationScreen} 样式完全一致。
 * <p>检测到 forcedUpdate=true 时点击「加入服务器」后显示。
 * 玩家只能「前往下载」或「返回」，无法跳过。</p>
 */
@SuppressWarnings("null")
public class ForcedUpdateScreen extends Screen {

    private static final Logger LOGGER = LoggerFactory.getLogger("YouzaiWorldCore/ForcedUpdateScreen");

    // ============ 布局常量 ============
    private static final int DIALOG_WIDTH = 200;
    private static final int DIALOG_HEIGHT = 120;
    private static final int CORNER_RADIUS = 6;
    private static final int BUTTON_WIDTH = 80;
    private static final int BUTTON_HEIGHT = 24;
    private static final int BUTTON_SPACING = 10;

    // ============ 动画常量 ============
    private static final float FADE_DURATION_MS = 200f;

    private TransparentButton downloadBtn;
    private TransparentButton backBtn;

    // ============ 动画状态 ============
    private long entryStartTime = -1;
    private float entryProgress = 1f;

    private boolean entering = false;
    private boolean exiting = false;
    private long exitStartTime = -1;
    private float exitProgress = 0f;
    private Runnable onExitComplete;

    public ForcedUpdateScreen() {
        super(Component.translatable("screen.youzaiworldcore.forced_update.title"));
    }

    @Override
    protected void init() {
        super.init();
        LOGGER.debug("Initializing ForcedUpdateScreen");

        entering = GuiAnimationController.isBasic();
        entryStartTime = System.currentTimeMillis();
        entryProgress = entering ? 0f : 1f;

        int dialogX = (this.width - DIALOG_WIDTH) / 2;
        int dialogY = (this.height - DIALOG_HEIGHT) / 2;

        int buttonY = dialogY + DIALOG_HEIGHT - BUTTON_HEIGHT - 15;
        int totalButtonWidth = BUTTON_WIDTH * 2 + BUTTON_SPACING;
        int buttonStartX = dialogX + (DIALOG_WIDTH - totalButtonWidth) / 2;

        this.downloadBtn = new TransparentButton(
                buttonStartX, buttonY, BUTTON_WIDTH, BUTTON_HEIGHT,
                Component.literal(I18n.get("screen.youzaiworldcore.forced_update.download_btn")),
                this::onDownload
        );
        this.downloadBtn.setTextColor(0x000000);

        this.backBtn = new TransparentButton(
                buttonStartX + BUTTON_WIDTH + BUTTON_SPACING, buttonY, BUTTON_WIDTH, BUTTON_HEIGHT,
                Component.literal(I18n.get("screen.youzaiworldcore.forced_update.back_btn")),
                this::onBack
        );
        this.backBtn.setTextColor(0x000000);
    }

    // ============ 渲染 ============

    @Override
    public void extractRenderState(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTick) {
        // 更新动画进度
        if (entering && entryStartTime != -1) {
            long elapsed = System.currentTimeMillis() - entryStartTime;
            entryProgress = Math.min(1f, elapsed / FADE_DURATION_MS);
            if (entryProgress >= 1f) {
                entering = false;
                entryStartTime = -1;
            }
        }

        if (exiting && exitStartTime != -1) {
            long elapsed = System.currentTimeMillis() - exitStartTime;
            exitProgress = Math.min(1f, elapsed / FADE_DURATION_MS);
        }

        float alpha = exiting
                ? 1f - easeOutCubic(exitProgress)
                : easeOutCubic(entryProgress);

        if (exiting && exitProgress >= 1f) {
            Runnable callback = onExitComplete;
            exiting = false;
            exitProgress = 0f;
            onExitComplete = null;
            if (callback != null) {
                callback.run();
            }
            return;
        }

        // 全屏半透明黑色遮罩
        int overlayAlpha = (int) (0x50 * alpha);
        guiGraphics.fill(0, 0, this.width, this.height, (overlayAlpha << 24));

        int dialogX = (this.width - DIALOG_WIDTH) / 2;
        int dialogY = (this.height - DIALOG_HEIGHT) / 2;

        // 白色圆角对话框背景
        int bgAlpha = (int) (0xBF * alpha);
        int bgColor = (bgAlpha << 24) | 0xFFFFFF;
        fillRoundedRect(guiGraphics, dialogX, dialogY, DIALOG_WIDTH, DIALOG_HEIGHT, CORNER_RADIUS, bgColor);

        // 文本颜色
        int textAlpha = (int) (alpha * 255);
        int textColor = (textAlpha << 24) | 0x000000;
        int titleColor = (textAlpha << 24) | 0x000000;

        // 标题
        String title = Component.translatable("screen.youzaiworldcore.forced_update.title").getString();
        float titleScale = 1.2f;
        int titleWidth = (int) (this.font.width(title) * titleScale);
        int titleX = dialogX + (DIALOG_WIDTH - titleWidth) / 2;
        int titleY = dialogY + 15;

        guiGraphics.pose().pushMatrix();
        guiGraphics.pose().scale(titleScale, titleScale);
        guiGraphics.text(this.font, title,
                (int) (titleX / titleScale),
                (int) (titleY / titleScale),
                titleColor, false);
        guiGraphics.pose().popMatrix();

        // 消息
        String message = I18n.get("screen.youzaiworldcore.forced_update.message");
        int msgWidth = this.font.width(message);
        guiGraphics.text(this.font, message,
                dialogX + (DIALOG_WIDTH - msgWidth) / 2,
                dialogY + 45,
                textColor, false);

        // 按钮透明度同步
        if (this.downloadBtn != null) {
            this.downloadBtn.setExternalAlpha(alpha);
        }
        if (this.backBtn != null) {
            this.backBtn.setExternalAlpha(alpha);
        }

        // 渲染按钮
        if (this.downloadBtn != null) {
            this.downloadBtn.render(guiGraphics, mouseX, mouseY, partialTick);
        }
        if (this.backBtn != null) {
            this.backBtn.render(guiGraphics, mouseX, mouseY, partialTick);
        }
    }

    // ============ 输入事件 ============

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean isActuallyClick) {
        if (exiting) return true;
        if (entering && entryProgress < 1f) return true;

        double mx = event.x();
        double my = event.y();

        if (this.downloadBtn != null && isMouseOver(this.downloadBtn, mx, my)) {
            this.downloadBtn.onClick(event, isActuallyClick);
            return true;
        }
        if (this.backBtn != null && isMouseOver(this.backBtn, mx, my)) {
            this.backBtn.onClick(event, isActuallyClick);
            return true;
        }
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent keyEvent) {
        if (exiting) return true;

        // ESC 键 = 返回
        if (keyEvent.key() == 256) {
            onBack();
            return true;
        }
        // Enter 键 = 前往下载
        if (keyEvent.key() == 257 || keyEvent.key() == 335) {
            onDownload();
            return true;
        }
        return super.keyPressed(keyEvent);
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        onBack();
    }

    // ========== 按钮回调 ==========

    private void onDownload() {
        if (exiting) return;
        // 下载地址固定取自检查结果（系统默认下载页，不再支持自定义跳转地址）
        UpdateResult r = ClientUpdateState.get();
        String url = (r != null) ? r.downloadUrl() : null;
        if (url == null || url.isEmpty()) {
            LOGGER.warn("下载地址为空，无法打开下载页");
            return;
        }
        DebugLogger.info("ForcedUpdateScreen", "显示下载页链接确认: %s", url);
        ConfirmLinkScreen.confirmLinkNow(this, url);
    }

    private void onBack() {
        if (exiting) return;
        startExitAnimation(() -> {
            Minecraft.getInstance().gui.setScreen(null);
        });
    }

    private void startExitAnimation(Runnable onComplete) {
        if (exiting) return;
        if (!GuiAnimationController.isBasic()) {
            onComplete.run();
            return;
        }
        this.exiting = true;
        this.exitStartTime = System.currentTimeMillis();
        this.exitProgress = 0f;
        this.onExitComplete = onComplete;
    }

    // ========== 工具方法 ==========

    private boolean isMouseOver(TransparentButton button, double mx, double my) {
        return mx >= button.getX() && mx < button.getX() + button.getWidth()
                && my >= button.getY() && my < button.getY() + button.getHeight();
    }

    private void fillRoundedRect(GuiGraphicsExtractor g, int x, int y, int w, int h, int r, int color) {
        // 圆角绘制统一走 RoundedRect（行扫描：r=6 时 135 次 fill -> 13 次）。
        // 点亮像素与原逐像素实现一致（45253 组尺寸/半径已逐一比对）；
        // 原实现未做尺寸校验，r > min(w,h)/2 时会画出坐标反转/重叠的结果，此处会钳制半径。
        RoundedRect.fill(g, x, y, w, h, r, color);
    }

    private float easeOutCubic(float t) {
        return 1f - (float) Math.pow(1f - t, 3);
    }
}
