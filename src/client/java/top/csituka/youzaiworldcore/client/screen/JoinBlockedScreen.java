package top.csituka.youzaiworldcore.client.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.User;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.csituka.youzaiworldcore.client.render.RoundedRect;
import top.csituka.youzaiworldcore.client.screen.widget.TransparentButton;
import top.csituka.youzaiworldcore.update.UpdateChecker;
import top.csituka.youzaiworldcore.util.DebugLogger;
import top.csituka.youzaiworldcore.client.animation.GuiAnimationController;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 「当前状态无法加入服务器」弹窗。
 * <p>
 * 当满足以下两个条件时，点击标题屏幕的「加入服务器」按钮会显示本弹窗，
 * 提示玩家更换账户，仅提供「返回」按钮回到标题屏幕：
 * <ol>
 *   <li>本模组为开发版本（版本号内带有 {@code indev}，如 {@code 2.10.6-indev}）</li>
 *   <li>当前登录玩家代号形如 {@code Player<数字>}（如 {@code Player123}）</li>
 * </ol>
 * 判断逻辑见 {@link #isBlocked()}。
 * <p>
 * 样式与 {@link ForcedUpdateScreen} / {@link QuitConfirmationScreen} 保持一致：
 * 全屏半透明遮罩 + 白色圆角对话框 + 黑色标题/正文 + {@link TransparentButton}，
 * 带 200ms easeOutCubic 淡入淡出动画。
 */
@SuppressWarnings("null")
public class JoinBlockedScreen extends Screen {

    private static final Logger LOGGER = LoggerFactory.getLogger("YouzaiWorldCore/JoinBlockedScreen");

    /** DebugLogger 模块名 */
    public static final String MODULE = "JoinBlockedScreen";

    // ============ 布局常量 ============
    private static final int DIALOG_WIDTH = 200;
    private static final int DIALOG_HEIGHT = 150;
    private static final int CORNER_RADIUS = 6;
    private static final int BUTTON_WIDTH = 80;
    private static final int BUTTON_HEIGHT = 24;

    // ============ 动画常量 ============
    private static final float FADE_DURATION_MS = 200f;

    /** 匹配默认离线/开发账户代号：Player 后跟任意位数字（如 Player123） */
    private static final Pattern DEFAULT_PLAYER_NAME = Pattern.compile("^Player\\d+$");

    private TransparentButton backBtn;

    // ============ 动画状态 ============
    private long entryStartTime = -1;
    private float entryProgress = 1f;
    private boolean entering = false;
    private boolean exiting = false;
    private long exitStartTime = -1;
    private float exitProgress = 0f;
    private Runnable onExitComplete;

    public JoinBlockedScreen() {
        super(Component.translatable("screen.youzaiworldcore.join_blocked.title"));
    }

    /**
     * 判断当前是否应阻止加入服务器：
     * <ol>
     *   <li>模组版本号包含 {@code indev}（开发版本，如 2.10.6-indev）</li>
     *   <li>当前登录玩家代号匹配 {@code Player<数字>}（如 Player123）</li>
     * </ol>
     * 任一条件不满足则放行。任何异常均视为放行并记录日志，绝不阻塞正常玩家。
     */
    public static boolean isBlocked() {
        try {
            String version = UpdateChecker.getCurrentVersionString();
            boolean isDevBuild = version != null && version.contains("indev");
            if (!isDevBuild) {
                DebugLogger.debug(MODULE, "版本非开发版(version=%s)，放行加入服务器", version);
                return false;
            }
            User user = Minecraft.getInstance().getUser();
            String name = (user != null) ? user.getName() : null;
            boolean nameMatch = name != null && DEFAULT_PLAYER_NAME.matcher(name).matches();
            DebugLogger.debug(MODULE, "拦截检查: version=%s, playerName=%s, nameMatch=%s",
                    version, name, nameMatch);
            if (nameMatch) {
                DebugLogger.info(MODULE, "开发版(%s) + 玩家代号 %s 命中限制，阻止加入服务器",
                        version, name);
            }
            return nameMatch;
        } catch (Exception e) {
            DebugLogger.exception(MODULE, "isBlocked", e);
            return false;
        }
    }

    @Override
    protected void init() {
        super.init();
        LOGGER.debug("Initializing JoinBlockedScreen");

        entering = GuiAnimationController.isBasic();
        entryStartTime = System.currentTimeMillis();
        entryProgress = entering ? 0f : 1f;

        int dialogX = (this.width - DIALOG_WIDTH) / 2;
        int dialogY = (this.height - DIALOG_HEIGHT) / 2;

        // 单按钮：居中放置
        int buttonY = dialogY + DIALOG_HEIGHT - BUTTON_HEIGHT - 15;
        int buttonStartX = dialogX + (DIALOG_WIDTH - BUTTON_WIDTH) / 2;

        this.backBtn = new TransparentButton(
                buttonStartX, buttonY, BUTTON_WIDTH, BUTTON_HEIGHT,
                Component.literal(I18n.get("screen.youzaiworldcore.join_blocked.back_btn")),
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

        // 淡出完成：执行返回回调（回到标题屏幕）
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

        // 标题（抱歉）
        String title = Component.translatable("screen.youzaiworldcore.join_blocked.title").getString();
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

        // 消息（自动换行，逐行居中）
        String message = I18n.get("screen.youzaiworldcore.join_blocked.message");
        List<String> lines = wrapText(message, DIALOG_WIDTH - 24);
        int messageY = dialogY + 42;
        for (String line : lines) {
            int lineWidth = this.font.width(line);
            guiGraphics.text(this.font, line,
                    dialogX + (DIALOG_WIDTH - lineWidth) / 2,
                    messageY,
                    textColor, false);
            messageY += this.font.lineHeight + 4;
        }

        // 按钮透明度同步
        if (this.backBtn != null) {
            this.backBtn.setExternalAlpha(alpha);
        }

        // 渲染按钮
        if (this.backBtn != null) {
            this.backBtn.render(guiGraphics, mouseX, mouseY, partialTick);
        }
    }

    // ============ 输入事件 ============

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean isActuallyClick) {
        if (exiting) return true; // 淡出期间屏蔽点击
        if (entering && entryProgress < 1f) return true; // 淡入完成前屏蔽点击

        double mx = event.x();
        double my = event.y();

        if (this.backBtn != null && isMouseOver(this.backBtn, mx, my)) {
            this.backBtn.onClick(event, isActuallyClick);
            return true;
        }
        return true; // 点击对话框外部不穿透
    }

    @Override
    public boolean keyPressed(KeyEvent keyEvent) {
        if (exiting) return true; // 淡出期间屏蔽键盘

        // ESC / Enter / 小键盘 Enter = 返回标题屏幕
        if (keyEvent.key() == 256 || keyEvent.key() == 257 || keyEvent.key() == 335) {
            onBack();
            return true;
        }
        return super.keyPressed(keyEvent);
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false; // 由 keyPressed 处理 ESC
    }

    @Override
    public boolean isPauseScreen() {
        return false; // 不暂停游戏
    }

    @Override
    public void onClose() {
        // 阻止直接关闭（如屏幕被替换），视为返回
        onBack();
    }

    // ========== 按钮回调 ==========

    private void onBack() {
        if (exiting) return;
        DebugLogger.info(MODULE, "玩家点击返回，淡出后回到标题屏幕");
        startExitAnimation(() -> {
            Minecraft.getInstance().setScreenAndShow(null);
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
        // 圆角绘制统一走 RoundedRect（行扫描：r=6 时 135 次 fill -> 13 次），与其它弹窗一致。
        RoundedRect.fill(g, x, y, w, h, r, color);
    }

    /**
     * 按最大像素宽度自动换行，返回各行文本。
     * <p>对 CJK / 拉丁文本均按 {@code font.width} 实际测量，避免不同语言下溢出。</p>
     */
    private List<String> wrapText(String text, int maxWidth) {
        List<String> lines = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            return lines;
        }
        String remaining = text.trim();
        while (!remaining.isEmpty()) {
            if (this.font.width(remaining) <= maxWidth) {
                lines.add(remaining);
                break;
            }
            // 二分查找最后一个不超过 maxWidth 的断点
            int low = 0;
            int high = remaining.length();
            while (low < high) {
                int mid = (low + high + 1) / 2;
                if (this.font.width(remaining.substring(0, mid)) <= maxWidth) {
                    low = mid;
                } else {
                    high = mid - 1;
                }
            }
            if (low <= 0) {
                // 单个字符都放不下：直接放入避免死循环（极不可能出现）
                lines.add(remaining);
                break;
            }
            lines.add(remaining.substring(0, low));
            remaining = remaining.substring(low).trim();
        }
        return lines;
    }

    private float easeOutCubic(float t) {
        return 1f - (float) Math.pow(1f - t, 3);
    }
}
