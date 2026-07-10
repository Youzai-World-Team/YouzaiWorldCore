package top.csituka.youzaiworldcore.mixin.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.LogoRenderer;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.SplashRenderer;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.csituka.youzaiworldcore.client.config.ClientExternalSettings;
import top.csituka.youzaiworldcore.client.screen.widget.TitleScreenTextButton;

import java.util.ArrayList;
import java.util.List;

/**
 * 修改 Minecraft 标题界面为双半透明方块布局：
 * <ul>
 *   <li>两个等宽半透明方块居中对齐，保留间距</li>
 *   <li>左侧面板：竖向排列「加入服务器」「选项」「退出游戏」</li>
 *   <li>右侧面板：服务器公告文字</li>
 * </ul>
 * 保留开发者测试按钮（仅开发者模式显示）。
 */
@Mixin(TitleScreen.class)
public class TitleScreenMixin {

    @Shadow private SplashRenderer splash;

    // ============ 布局常量 ============
    /** 两个面板统一宽度 */
    private static final int PANEL_WIDTH = 170;

    /** 面板内边距 */
    private static final int PANEL_PADDING = 12;

    /** 两个面板之间的间距 */
    private static final int PANEL_GAP = 16;

    /** 按钮高度 */
    private static final int BUTTON_HEIGHT = 16;

    /** 按钮间距 */
    private static final int BUTTON_GAP = 8;

    /** 两个面板统一高度 */
    private static final int PANEL_HEIGHT = 130;

    /** 公告标题颜色 */
    private static final int ANNOUNCEMENT_TITLE_COLOR = 0xFFFFAA00;

    /** 公告标题阴影颜色 */
    private static final int ANNOUNCEMENT_TITLE_SHADOW = 0x40000000;

    /** 公告正文颜色 */
    private static final int ANNOUNCEMENT_TEXT_COLOR = 0xFFE0E0E0;

    // ============ 淡入动画 ============

    /** 面板间距起始倍率（淡入开始时间距为 PANEL_GAP 的此倍数，随后过渡到正常间距） */
    @Unique
    private static final float PANEL_GAP_START_MULTIPLIER = 2.0f;

    /** 主界面出现后等待该时长（毫秒）再开始淡入 */
    @Unique
    private static final long FADE_IN_DELAY_MS = 1500L;

    /** 淡入动画持续时间（毫秒） */
    @Unique
    private static final long FADE_IN_DURATION_MS = 1200L;

    /** 首次渲染时记录的时间戳，用于计算淡入进度（-1 表示尚未首次渲染） */
    @Unique
    private long youzaiworldcore$fadeInStart = -1L;

    /** 淡入动画是否已完成（完成后不再重复播放，与原版 fading 标志同理） */
    @Unique
    private boolean youzaiworldcore$fadeCompleted = false;

    // ==================== init(): 移除并重排按钮 ====================

    /**
     * 在 {@code TitleScreen.init()} 执行完毕后：
     * <ol>
     *   <li>移除所有原版组件</li>
     *   <li>创建三个自定义无背景文字按钮，替换为悬浮下划线样式</li>
     *   <li>将所有按钮排列在左侧半透明面板区域内</li>
     *   <li>开发者模式额外显示测试按钮</li>
     * </ol>
     */
    @Inject(method = "init", at = @At("TAIL"))
    private void youzaiworldcore$reworkTitleButtons(CallbackInfo ci) {
        TitleScreen screen = (TitleScreen) (Object) this;
        int width = screen.width;
        int height = screen.height;

        ScreenAccessor accessor = (ScreenAccessor) screen;

        // ============ 1. 移除所有原版组件 ============
        List<Renderable> renderables = accessor.youzaiworldcore$getRenderables();
        List<NarratableEntry> narratables = accessor.youzaiworldcore$getNarratables();
        List<GuiEventListener> childrenList = accessor.youzaiworldcore$getChildren();

        List<GuiEventListener> allChildren = new ArrayList<>(childrenList);
        for (GuiEventListener child : allChildren) {
            childrenList.remove(child);
            if (child instanceof Renderable r) {
                renderables.remove(r);
            }
            if (child instanceof NarratableEntry n) {
                narratables.remove(n);
            }
        }

        // ============ 2. 创建自定义按钮 ============
        Minecraft minecraft = accessor.youzaiworldcore$getMinecraft();

        // 隐藏原版标题和闪烁标语
        this.splash = null;

        // 2a. 加入服务器
        TitleScreenTextButton joinBtn = new TitleScreenTextButton(
            0, 0, 0, BUTTON_HEIGHT,
            Component.translatable("title.youzaiworldcore.join_server"),
            () -> {
                ServerData serverData = new ServerData("Youzai World", "play.mcyzw.top", ServerData.Type.OTHER);
                ServerAddress address = ServerAddress.parseString("play.mcyzw.top");
                ConnectScreen.startConnecting(screen, minecraft, address, serverData, false, null);
            }
        );
        childrenList.add(joinBtn);
        renderables.add(joinBtn);
        narratables.add(joinBtn);

        // 2b. 选项
        TitleScreenTextButton optionsBtn = new TitleScreenTextButton(
            0, 0, 0, BUTTON_HEIGHT,
            Component.translatable("menu.options"),
            () -> minecraft.gui.setScreen(new OptionsScreen(screen, minecraft.options, false))
        );
        childrenList.add(optionsBtn);
        renderables.add(optionsBtn);
        narratables.add(optionsBtn);

        // 2c. 退出游戏
        TitleScreenTextButton quitBtn = new TitleScreenTextButton(
            0, 0, 0, BUTTON_HEIGHT,
            Component.translatable("menu.quit"),
            () -> minecraft.stop()
        );
        childrenList.add(quitBtn);
        renderables.add(quitBtn);
        narratables.add(quitBtn);

        // 2d. 开发者测试按钮（仅在开发者模式启用时显示）
        TitleScreenTextButton testButton = null;
        boolean showTest = ClientExternalSettings.isDevModeEnabled();
        if (showTest) {
            String debugMode = ClientExternalSettings.getDebugModeType();
            boolean isDedicated = "dedicated".equals(debugMode);

            Runnable testAction;
            if (isDedicated) {
                String addr = ClientExternalSettings.getDebugAddress();
                String port = ClientExternalSettings.getDebugPort();
                String fullAddr = addr + ":" + port;
                ServerData srvData = new ServerData("Debug Server", fullAddr, ServerData.Type.OTHER);
                ServerAddress srvAddr = ServerAddress.parseString(fullAddr);
                testAction = () -> ConnectScreen.startConnecting(screen, minecraft, srvAddr, srvData, false, null);
            } else {
                testAction = () -> minecraft.gui.setScreen(new SelectWorldScreen(screen));
            }

            testButton = new TitleScreenTextButton(
                0, 0, 0, BUTTON_HEIGHT,
                Component.translatable("title.youzaiworldcore.test_page"),
                testAction
            );
            childrenList.add(testButton);
            renderables.add(testButton);
            narratables.add(testButton);
        }

        // ============ 3. 计算居中布局位置 ============
        int totalGroupWidth = PANEL_WIDTH * 2 + PANEL_GAP;
        int groupStartX = (width - totalGroupWidth) / 2;
        int leftPanelX = groupStartX;
        int panelY = (height - PANEL_HEIGHT) / 2;

        int buttonX = leftPanelX + PANEL_PADDING;
        int buttonWidth = PANEL_WIDTH - PANEL_PADDING * 2;
        int buttonStartY = panelY + PANEL_PADDING;

        // 加入服务器
        joinBtn.setX(buttonX);
        joinBtn.setY(buttonStartY);
        joinBtn.setWidth(buttonWidth);

        // 选项
        optionsBtn.setX(buttonX);
        optionsBtn.setY(buttonStartY + BUTTON_HEIGHT + BUTTON_GAP);
        optionsBtn.setWidth(buttonWidth);

        // 退出游戏
        quitBtn.setX(buttonX);
        quitBtn.setY(buttonStartY + 2 * (BUTTON_HEIGHT + BUTTON_GAP));
        quitBtn.setWidth(buttonWidth);

        // 测试按钮：放在左面板下方
        if (testButton != null) {
            testButton.setX(buttonX);
            testButton.setY(buttonStartY + 3 * (BUTTON_HEIGHT + BUTTON_GAP) + 4);
            testButton.setWidth(buttonWidth);
        }
    }

    // ==================== extractRenderState(): 绘制面板 ====================

    /**
     * 劫持 LogoRenderer 的渲染调用，屏蔽原版标题 LOGO。
     */
    @Redirect(
        method = "extractRenderState",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/components/LogoRenderer;extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IF)V"
        )
    )
    private void youzaiworldcore$hideLogo(LogoRenderer renderer, GuiGraphicsExtractor graphics, int width, float alpha) {
        // no-op：不绘制原版标题 LOGO
    }

    /**
     * 在全景图渲染之后、按钮等组件渲染之前绘制左右两个半透明面板。
     * <p>
     * 两个面板等宽，作为整体居中对齐。
     * 渲染顺序：全景图 → 面板 → 组件(按钮/logo等)
     */
    @Inject(
        method = "extractRenderState",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/screens/TitleScreen;extractPanorama(Lnet/minecraft/client/gui/GuiGraphicsExtractor;F)V",
            shift = At.Shift.AFTER
        )
    )
    private void youzaiworldcore$drawPanels(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        TitleScreen screen = (TitleScreen) (Object) this;
        int width = screen.width;
        int height = screen.height;
        var font = ((ScreenAccessor) screen).youzaiworldcore$getFont();

        // 计算淡入透明度
        float fadeAlpha = youzaiworldcore$computeFadeAlpha();

        // 面板间距随淡入进度插值：从 2 倍间距 → 正常间距
        float currentGap = PANEL_GAP * (PANEL_GAP_START_MULTIPLIER
            - (PANEL_GAP_START_MULTIPLIER - 1.0f) * fadeAlpha);
        int totalGroupWidth = PANEL_WIDTH * 2 + (int) currentGap;
        int groupStartX = (width - totalGroupWidth) / 2;
        int leftPanelX = groupStartX;
        int rightPanelX = groupStartX + PANEL_WIDTH + (int) currentGap;
        int panelY = (height - PANEL_HEIGHT) / 2;

        // 按钮跟随左侧面板移动，并同步应用淡入透明度
        int buttonX = leftPanelX + PANEL_PADDING;
        for (GuiEventListener child : ((ScreenAccessor) screen).youzaiworldcore$getChildren()) {
            if (child instanceof TitleScreenTextButton btn) {
                btn.setRenderAlpha(fadeAlpha);
                btn.setX(buttonX);
            }
        }

        drawPanelBackground(graphics, leftPanelX, panelY, PANEL_WIDTH, PANEL_HEIGHT, fadeAlpha);
        drawPanelBackground(graphics, rightPanelX, panelY, PANEL_WIDTH, PANEL_HEIGHT, fadeAlpha);
        drawPanelContent(graphics, rightPanelX, panelY, PANEL_WIDTH, PANEL_HEIGHT, font, fadeAlpha);
    }

    /**
     * 绘制单个半透明面板背景（无描边），透明度受淡入进度控制。
     */
    private void drawPanelBackground(GuiGraphicsExtractor graphics, int x, int y, int w, int h, float fadeAlpha) {
        int bgAlpha = (int) (0x80 * fadeAlpha);
        graphics.fill(x, y, x + w, y + h, (bgAlpha << 24));
    }

    /**
     * 绘制右侧面板内容（公告标题 + 正文，带自动换行），透明度受淡入进度控制。
     */
    private void drawPanelContent(GuiGraphicsExtractor graphics, int panelX, int panelY, int panelW, int panelH, net.minecraft.client.gui.Font font, float fadeAlpha) {
        int textX = panelX + PANEL_PADDING;
        int textY = panelY + PANEL_PADDING;
        int maxTextWidth = panelW - PANEL_PADDING * 2;

        // 根据淡入透明度计算各文本颜色的 alpha 分量
        int titleAlpha = (int) (0xFF * fadeAlpha);
        int shadowAlpha = (int) (0x40 * fadeAlpha);
        int textAlpha = (int) (0xFF * fadeAlpha);

        int titleColor = (titleAlpha << 24) | (ANNOUNCEMENT_TITLE_COLOR & 0x00FFFFFF);
        int titleShadow = (shadowAlpha << 24);
        int textColor = (textAlpha << 24) | (ANNOUNCEMENT_TEXT_COLOR & 0x00FFFFFF);

        // 公告标题（不加 emoji，带阴影以突出显示）
        String title = Component.translatable("title.youzaiworldcore.announcement_title").getString();
        // 阴影
        graphics.text(font, title, textX + 1, textY + 1, titleShadow);
        // 正文
        graphics.text(font, title, textX, textY, titleColor);

        // 公告内容行（逐行自动换行）
        String[] rawLines = {
            Component.translatable("title.youzaiworldcore.announcement_line1").getString(),
            Component.translatable("title.youzaiworldcore.announcement_line2").getString(),
            Component.translatable("title.youzaiworldcore.announcement_line3").getString(),
            "",
            Component.translatable("title.youzaiworldcore.announcement_line4").getString(),
        };

        int lineY = textY + font.lineHeight + 8;
        for (String raw : rawLines) {
            lineY = drawWrappedLine(graphics, font, raw, textX, lineY, maxTextWidth, textColor);
        }
    }

    // ==================== 淡入动画 ====================

    /**
     * 计算当前淡入进度（0.0 ~ 1.0），使用 easeOutCubic 缓动。
     */
    @Unique
    private float youzaiworldcore$computeFadeAlpha() {
        // 淡入已完成：后续返回主界面时不再重复播放
        if (youzaiworldcore$fadeCompleted) {
            return 1.0f;
        }
        // 首次渲染时记录开始时间（只在 TitleScreen 初次创建后的第一帧触发）
        if (youzaiworldcore$fadeInStart == -1L) {
            youzaiworldcore$fadeInStart = System.currentTimeMillis();
            return 0.0f;
        }
        long elapsed = System.currentTimeMillis() - youzaiworldcore$fadeInStart;

        // 延迟阶段：面板保持完全透明
        if (elapsed < FADE_IN_DELAY_MS) {
            return 0.0f;
        }

        // 淡入阶段
        long fadeElapsed = elapsed - FADE_IN_DELAY_MS;
        if (fadeElapsed >= FADE_IN_DURATION_MS) {
            youzaiworldcore$fadeCompleted = true;
            return 1.0f;
        }
        float t = (float) fadeElapsed / (float) FADE_IN_DURATION_MS;
        return easeOutCubic(t);
    }

    /**
     * easeOutCubic 缓动函数，与项目中其他动画保持一致。
     */
    @Unique
    private static float easeOutCubic(float t) {
        return 1.0f - (float) Math.pow(1.0f - t, 3);
    }

    // ==================== 文本渲染工具 ====================

    /**
     * 绘制一行文本，如果超过最大宽度则自动换行。
     *
     * @return 下一行可用的 Y 坐标
     */
    private static int drawWrappedLine(GuiGraphicsExtractor graphics, net.minecraft.client.gui.Font font,
                                       String text, int x, int y, int maxWidth, int color) {
        if (text.isEmpty()) {
            return y + font.lineHeight + 2;
        }

        int lineHeight = font.lineHeight + 2;
        int currentY = y;
        String remaining = text;

        while (!remaining.isEmpty()) {
            int fitCount = fontWidth(font, remaining, maxWidth);
            if (fitCount <= 0) break; // 完全画不下，放弃

            String line = remaining.substring(0, fitCount);
            graphics.text(font, line, x, currentY, color);

            remaining = remaining.substring(fitCount).trim();
            currentY += lineHeight;
        }

        return currentY;
    }

    /**
     * 计算在给定像素宽度内最多能容纳的字符数，
     * 优先在空格处断开（单词换行），找不到空格则逐字断开。
     */
    private static int fontWidth(net.minecraft.client.gui.Font font, String text, int maxPixels) {
        if (text.isEmpty() || font.width(text) <= maxPixels) {
            return text.length();
        }

        // 二分查找最后一个不超过 maxPixels 的位置
        int low = 0;
        int high = text.length();
        while (low < high) {
            int mid = (low + high + 1) / 2;
            if (font.width(text.substring(0, mid)) <= maxPixels) {
                low = mid;
            } else {
                high = mid - 1;
            }
        }

        int maxChar = low;
        if (maxChar <= 0) return 0;

        // 在 0 ~ maxChar 范围内找最后一个空格（单词换行）
        int lastSpace = text.lastIndexOf(' ', maxChar);
        if (lastSpace > 0) {
            return lastSpace; // 从空格处断开，空格本身留在行末
        }

        // 没有空格，直接按字符截断
        return maxChar;
    }
}