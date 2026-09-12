package top.csituka.youzaiworldcore.client.screen;

import top.csituka.youzaiworldcore.client.render.YzuiTheme;
import top.csituka.youzaiworldcore.client.render.YzuiBackdrop;

import net.minecraft.client.Minecraft;
import net.minecraft.client.User;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.csituka.youzaiworldcore.client.screen.widget.TransparentButton;
import top.csituka.youzaiworldcore.update.UpdateChecker;
import top.csituka.youzaiworldcore.util.DebugLogger;
import top.csituka.youzaiworldcore.client.animation.GuiAnimationController;

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
 * 游戏背景暗化、主题圆角卡片及 {@link TransparentButton}，
 * 带 共用的缩放、位移与淡入淡出动画。
 */
@SuppressWarnings("null")
public class JoinBlockedScreen extends Screen {

    private static final Logger LOGGER = LoggerFactory.getLogger("YouzaiWorldCore/JoinBlockedScreen");

    /** DebugLogger 模块名 */
    public static final String MODULE = "JoinBlockedScreen";

    // ============ 布局常量 ============
    private static final int DIALOG_WIDTH = 400;
    private static final int DIALOG_HEIGHT = 232;
    private static final int BUTTON_WIDTH = 170;
    private static final int BUTTON_HEIGHT = 28;

    // ============ 动画常量 ============

    /** 匹配默认离线/开发账户代号：Player 后跟任意位数字（如 Player123） */
    private static final Pattern DEFAULT_PLAYER_NAME = Pattern.compile("^Player\\d+$");

    private TransparentButton backBtn;

    // ============ 动画状态 ============

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
        int x = (width - DIALOG_WIDTH) / 2;
        int y = (height - DIALOG_HEIGHT) / 2 + DIALOG_HEIGHT - BUTTON_HEIGHT - 24;
        backBtn = addRenderableWidget(new TransparentButton(x + 115, y, BUTTON_WIDTH, BUTTON_HEIGHT,
                Component.translatable("screen.youzaiworldcore.join_blocked.back_btn"), this::onBack));
        backBtn.setStyle(YzuiTheme.ButtonStyle.FILLED);
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
        YzuiTheme.wrapped(guiGraphics, font, Component.translatable("screen.youzaiworldcore.join_blocked.message"),
                x + 24, y + 52, DIALOG_WIDTH - 48, (DIALOG_HEIGHT - 114) / (font.lineHeight + 2),
                YzuiTheme.alpha(YzuiTheme.textMuted(), alpha));
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
        if (GuiAnimationController.isExiting(this)) return;
        DebugLogger.info(MODULE, "玩家点击返回，淡出后回到标题屏幕");
        startExitAnimation(() -> {
            Minecraft.getInstance().setScreenAndShow(YzuiBackdrop.parent(this));
        });
    }

    private void startExitAnimation(Runnable onComplete) {
        if (!GuiAnimationController.isExiting(this)) onComplete.run();
    }

    // ========== 工具方法 ==========


}
