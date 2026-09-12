package top.csituka.youzaiworldcore.client.screen;

import top.csituka.youzaiworldcore.client.render.YzuiTheme;
import top.csituka.youzaiworldcore.client.render.YzuiBackdrop;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
    private static final int DIALOG_WIDTH = 400;
    private static final int DIALOG_HEIGHT = 200;
    private static final int BUTTON_WIDTH = 170;
    private static final int BUTTON_HEIGHT = 28;

    // ============ 动画常量 ============

    private TransparentButton downloadBtn;
    private TransparentButton backBtn;

    // ============ 动画状态 ============


    public ForcedUpdateScreen() {
        super(Component.translatable("screen.youzaiworldcore.forced_update.title"));
    }

    @Override
    protected void init() {
        super.init();
        int x = (width - DIALOG_WIDTH) / 2;
        int y = (height - DIALOG_HEIGHT) / 2 + DIALOG_HEIGHT - BUTTON_HEIGHT - 24;
        downloadBtn = addRenderableWidget(new TransparentButton(x + 24, y, BUTTON_WIDTH, BUTTON_HEIGHT,
                Component.translatable("screen.youzaiworldcore.forced_update.download_btn"), this::onDownload));
        downloadBtn.setStyle(YzuiTheme.ButtonStyle.FILLED);
        backBtn = addRenderableWidget(new TransparentButton(x + 206, y, BUTTON_WIDTH, BUTTON_HEIGHT,
                Component.translatable("screen.youzaiworldcore.forced_update.back_btn"), this::onBack));
        setFocused(backBtn);
    }

    // ============ 渲染 ============

    @Override
    public void extractRenderState(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTick) {
        float alpha = 1f;
        int x = (width - DIALOG_WIDTH) / 2, y = (height - DIALOG_HEIGHT) / 2;
        YzuiTheme.card(guiGraphics, x, y, DIALOG_WIDTH, DIALOG_HEIGHT, alpha);
        YzuiTheme.label(guiGraphics, font, getTitle(), x + 24, y + 24,
                DIALOG_WIDTH - 48, YzuiTheme.alpha(YzuiTheme.text(), alpha), false);
        YzuiTheme.wrapped(guiGraphics, font, Component.translatable("screen.youzaiworldcore.forced_update.message"),
                x + 24, y + 52, DIALOG_WIDTH - 48, (DIALOG_HEIGHT - 114) / (font.lineHeight + 2),
                YzuiTheme.alpha(YzuiTheme.textMuted(), alpha));
        downloadBtn.setExternalAlpha(alpha);
        backBtn.setExternalAlpha(alpha);
        super.extractRenderState(guiGraphics, mouseX, mouseY, partialTick);
    }

    // ============ 输入事件 ============

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean isActuallyClick) {
        if (GuiAnimationController.isExiting(this) || event.button() != 0) return true;
        return super.mouseClicked(event, isActuallyClick);
    }

    @Override
    public boolean keyPressed(KeyEvent keyEvent) {
        if (GuiAnimationController.isExiting(this)) return true;
        if (keyEvent.key() == 256) { onBack(); return true; }
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
        if (GuiAnimationController.isExiting(this)) return;
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
        if (GuiAnimationController.isExiting(this)) return;
        startExitAnimation(() -> {
            Minecraft.getInstance().gui.setScreen(YzuiBackdrop.parent(this));
        });
    }

    private void startExitAnimation(Runnable onComplete) {
        if (!GuiAnimationController.isExiting(this)) onComplete.run();
    }

    // ========== 工具方法 ==========


}
