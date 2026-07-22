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
import net.minecraft.resources.Identifier;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.util.ARGB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.csituka.youzaiworldcore.YouzaiworldCore;
import top.csituka.youzaiworldcore.client.config.ClientExternalSettings;
import top.csituka.youzaiworldcore.client.screen.widget.TitleScreenTextButton;
import top.csituka.youzaiworldcore.client.update.ClientUpdateState;
import top.csituka.youzaiworldcore.config.UpdateCheckerConfig;
import top.csituka.youzaiworldcore.update.TitleScreenScrollState;
import top.csituka.youzaiworldcore.update.UpdateChecker;
import top.csituka.youzaiworldcore.update.UpdateResult;
import top.csituka.youzaiworldcore.util.DebugLogger;

import java.awt.Desktop;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;

/**
 * 修改 Minecraft 标题界面为双半透明方块布局：
 * <ul>
 *   <li>两个等宽半透明方块居中对齐，保留间距</li>
 *   <li>左侧面板：竖向排列「加入服务器」「选项」「退出游戏」</li>
 *   <li>右侧面板：服务器公告文字；若有可用更新，则在公告上方叠加更新信息块（标题 + 最新版本 + 发布时间 + 更新内容 + 可点击下载按钮 + 条件忽略按钮）</li>
 * </ul>
 * 保留开发者测试按钮（仅开发者模式显示）。
 */
@SuppressWarnings("null")
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

    /** 面板组垂直下移偏移量（相对垂直居中位置） */
    @Unique
    private static final int PANEL_Y_OFFSET = 12;

    /** 公告标题颜色 */
    private static final int ANNOUNCEMENT_TITLE_COLOR = 0xFFFFAA00;

    /** 公告正文颜色 */
    private static final int ANNOUNCEMENT_TEXT_COLOR = 0xFFE0E0E0;

    // ============ Logo ============

    /** Logo 资源路径 */
    @Unique
    private static final Identifier LOGO_TEXTURE = Identifier.fromNamespaceAndPath(
            YouzaiworldCore.MOD_ID, "textures/gui/yzw-logo.png");

    /** Logo 距离屏幕上边缘的像素 */
    @Unique
    private static final int LOGO_TOP_MARGIN = 20;

    /** Logo 绘制宽度（像素） */
    @Unique
    private static final int LOGO_DRAW_WIDTH = 200;

    /** Logo 绘制高度（像素） */
    @Unique
    private static final int LOGO_DRAW_HEIGHT = 34;

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

    /** 更新下载按钮（独立于左侧布局循环，按右面板坐标定位） */
    @Unique
    private TitleScreenTextButton youzaiworldcore$downloadUpdateBtn;

    /** 不再提示此版本按钮（仅当非强制更新时显示） */
    @Unique
    private TitleScreenTextButton youzaiworldcore$ignoreUpdateBtn;

    /** 更新信息块滚动偏移量（每帧从 TitleScreenScrollState 同步） */
    @Unique
    private double youzaiworldcore$updateScrollOffset = 0.0;

    // ==================== init(): 移除并重排按钮 ====================

    // ==================== init(): 移除并重排按钮 ====================

    /**
     * 在 {@code TitleScreen.init()} 执行完毕后：
     * <ol>
     *   <li>移除所有原版组件</li>
     *   <li>创建三个自定义无背景文字按钮，替换为悬浮下划线样式</li>
     *   <li>将所有按钮排列在左侧半透明面板区域内</li>
     *   <li>开发者模式额外显示测试按钮</li>
     *   <li>创建更新提示按钮（默认隐藏，由 drawPanelContent 按更新状态定位与显隐）</li>
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
                // 强制更新时阻止进入服务器，显示弹窗
                if (youzaiworldcore$isForcedUpdateBlocking()) {
                    Minecraft.getInstance().gui.setScreen(
                            new top.csituka.youzaiworldcore.client.screen.ForcedUpdateScreen());
                    return;
                }
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

        // 2c. 退出游戏（弹出确认对话框）
        TitleScreenTextButton quitBtn = new TitleScreenTextButton(
            0, 0, 0, BUTTON_HEIGHT,
            Component.translatable("menu.quit"),
            () -> minecraft.gui.setScreen(new top.csituka.youzaiworldcore.client.screen.QuitConfirmationScreen())
        );
        childrenList.add(quitBtn);
        renderables.add(quitBtn);
        narratables.add(quitBtn);

        // 2e. 开发者测试按钮（仅在开发者模式启用时显示）
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

        // 2f. 更新提示按钮（下载 / 忽略），默认隐藏，由 drawPanelContent 按更新状态定位与显隐
        TitleScreenTextButton downloadBtn = new TitleScreenTextButton(
            0, 0, 0, BUTTON_HEIGHT,
            Component.translatable("title.youzaiworldcore.update_download_btn"),
            this::youzaiworldcore$openDownloadPage
        );
        downloadBtn.visible = (false);
        childrenList.add(downloadBtn);
        renderables.add(downloadBtn);
        narratables.add(downloadBtn);

        TitleScreenTextButton ignoreBtn = new TitleScreenTextButton(
            0, 0, 0, BUTTON_HEIGHT,
            Component.translatable("title.youzaiworldcore.update_ignore_btn"),
            this::youzaiworldcore$ignoreUpdate
        );
        ignoreBtn.visible = (false);
        childrenList.add(ignoreBtn);
        renderables.add(ignoreBtn);
        narratables.add(ignoreBtn);

        youzaiworldcore$downloadUpdateBtn = downloadBtn;
        youzaiworldcore$ignoreUpdateBtn = ignoreBtn;

        // ============ 3. 计算居中布局位置 ============
        int totalGroupWidth = PANEL_WIDTH * 2 + PANEL_GAP;
        int groupStartX = (width - totalGroupWidth) / 2;
        int leftPanelX = groupStartX;
        int panelY = (height - PANEL_HEIGHT) / 2 + PANEL_Y_OFFSET;

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

        // 测试按钮：放在官网下方
        if (testButton != null) {
            testButton.setX(buttonX);
            testButton.setY(buttonStartY + 3 * (BUTTON_HEIGHT + BUTTON_GAP));
            testButton.setWidth(buttonWidth);
        }

        // 每次进入标题界面（含从服务器断开后）重新检查更新
        if (UpdateCheckerConfig.isCheckOnStartupClient()) {
            boolean clientDevMode = ClientExternalSettings.isDevModeEnabled();
            String checkBase = clientDevMode ? ClientExternalSettings.getUpdateCheckAddress() : "";
            String jumpBase = clientDevMode ? ClientExternalSettings.getUpdateJumpAddress() : "";
            UpdateChecker.checkAsync(checkBase, jumpBase).thenAccept(result -> {
                if (result != null) {
                    ClientUpdateState.set(result);
                }
            });
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
     * </p>
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
        int panelY = (height - PANEL_HEIGHT) / 2 + PANEL_Y_OFFSET;

        // 按钮跟随左侧面板移动，并同步应用淡入透明度
        int buttonX = leftPanelX + PANEL_PADDING;
        for (GuiEventListener child : ((ScreenAccessor) screen).youzaiworldcore$getChildren()) {
            if (child instanceof TitleScreenTextButton btn
                    && btn != youzaiworldcore$downloadUpdateBtn
                    && btn != youzaiworldcore$ignoreUpdateBtn) {
                btn.setRenderAlpha(fadeAlpha);
                btn.setX(buttonX);
            }
        }

        // 绘制前同步滚动偏移量（MouseHandlerScrollMixin 通过 TitleScreenScrollState 更新）
        youzaiworldcore$updateScrollOffset = TitleScreenScrollState.getScrollOffset();

        // 左右面板统一使用相同的位置与高度
        boolean showUpdate = youzaiworldcore$shouldShowUpdate();

        drawPanelBackground(graphics, leftPanelX, panelY, PANEL_WIDTH, PANEL_HEIGHT, fadeAlpha);
        drawPanelBackground(graphics, rightPanelX, panelY, PANEL_WIDTH, PANEL_HEIGHT, fadeAlpha);
        drawPanelContent(graphics, rightPanelX, panelY, PANEL_WIDTH, PANEL_HEIGHT, font, fadeAlpha, showUpdate);
        drawLogo(graphics, width, fadeAlpha);

        // 更新信息块滚动条（仅在内容超出面板时显示）
        if (showUpdate) {
            int maxScroll = TitleScreenScrollState.getMaxScroll();
            if (maxScroll > 0) {
                int sbLeft = rightPanelX + PANEL_WIDTH - 8;
                int sbRight = sbLeft + 4;
                int sbTop = panelY + 2;
                int sbBottom = panelY + PANEL_HEIGHT - 2;
                // 轨道（白色半透明）
                int trackAlpha = (int) (0x40 * fadeAlpha);
                graphics.fill(sbLeft, sbTop, sbRight, sbBottom, (trackAlpha << 24) | 0x00FFFFFF);
                // 滑块（白色较高透明度）
                double ratio = (double) PANEL_HEIGHT / TitleScreenScrollState.getContentHeight();
                int thumbH = Math.max(10, (int)(ratio * PANEL_HEIGHT));
                int scroll = (int) Math.round(youzaiworldcore$updateScrollOffset);
                int thumbY = sbTop + (int)((double)scroll / maxScroll * (sbBottom - sbTop - thumbH));
                int thumbA = (int) (0x90 * fadeAlpha);
                int thumbColor = (thumbA << 24) | 0x00FFFFFF;
                graphics.fill(sbLeft, thumbY, sbRight, thumbY + thumbH, thumbColor);
            }
        }
    }

    /**
     * 绘制单个半透明面板背景（无描边），透明度受淡入进度控制。
     */
    private void drawPanelBackground(GuiGraphicsExtractor graphics, int x, int y, int w, int h, float fadeAlpha) {
        int bgAlpha = (int) (0x80 * fadeAlpha);
        graphics.fill(x, y, x + w, y + h, (bgAlpha << 24));
    }

    /**
     * 绘制顶部居中 Logo，水平居中、距离上边缘 LOGO_TOP_MARGIN 像素，透明度受淡入进度控制。
     */
    private void drawLogo(GuiGraphicsExtractor graphics, int screenWidth, float fadeAlpha) {
        int logoX = (screenWidth - LOGO_DRAW_WIDTH) / 2;
        int logoY = LOGO_TOP_MARGIN;
        int color = ARGB.white(fadeAlpha);
        graphics.blit(RenderPipelines.GUI_TEXTURED, LOGO_TEXTURE,
                logoX, logoY,
                0, 0,
                LOGO_DRAW_WIDTH, LOGO_DRAW_HEIGHT,
                LOGO_DRAW_WIDTH, LOGO_DRAW_HEIGHT,
                color);
    }

    /**
     * 绘制右侧面板内容：
     * <ul>
     *   <li>有可用更新时，整个面板显示更新信息（标题 + 最新版本 + 发布时间 + 更新内容 + 下载/忽略按钮），不显示公告</li>
     *   <li>无可用更新时，显示原服务器公告</li>
     * </ul>
     * 透明度受淡入进度控制。
     */
    private void drawPanelContent(GuiGraphicsExtractor graphics, int panelX, int panelY, int panelW, int panelH, net.minecraft.client.gui.Font font, float fadeAlpha, boolean showUpdate) {
        int textX = panelX + PANEL_PADDING;
        int maxTextWidth = panelW - PANEL_PADDING * 2;
        int panelBottom = panelY + panelH - PANEL_PADDING;

        // 根据淡入透明度计算各文本颜色的 alpha 分量
        int titleAlpha = (int) (0xFF * fadeAlpha);
        int shadowAlpha = (int) (0x40 * fadeAlpha);
        int textAlpha = (int) (0xFF * fadeAlpha);

        int titleColor = (titleAlpha << 24) | (ANNOUNCEMENT_TITLE_COLOR & 0x00FFFFFF);
        int titleShadow = (shadowAlpha << 24);
        int textColor = (textAlpha << 24) | (ANNOUNCEMENT_TEXT_COLOR & 0x00FFFFFF);

        if (showUpdate) {
            UpdateResult r = ClientUpdateState.get();
            if (r != null) {
                int scroll = (int) Math.round(youzaiworldcore$updateScrollOffset);

                // 按钮固定在面板底部（不被平移影响）
                int downloadY = panelBottom - BUTTON_HEIGHT * 2 - 2;
                int ignoreY = panelBottom - BUTTON_HEIGHT;

                // ===== 可滚动文本区域（标题 + 日志）：裁切到按钮上方 =====
                int textAreaBottom = downloadY; // 文本裁切到下载按钮顶边，避免透过按钮漏出
                graphics.enableScissor(panelX, panelY, panelX + panelW, textAreaBottom);
                graphics.pose().pushMatrix();
                graphics.pose().translate(0, -scroll);

                // 标题（强制更新用红色，普通更新用绿色）
                String updTitle = Component.translatable(r.forcedUpdate()
                        ? "title.youzaiworldcore.update_forced_title"
                        : "title.youzaiworldcore.update_title").getString();
                int updTitleColor = (titleAlpha << 24) | (r.forcedUpdate() ? 0x00FF5555 : 0x0088FF88);
                int updTitleY = panelY + PANEL_PADDING;
                graphics.text(font, updTitle, textX + 1, updTitleY + 1, titleShadow);
                graphics.text(font, updTitle, textX, updTitleY, updTitleColor);

                int y = updTitleY + font.lineHeight + 4;

                // 最新版本
                String latestLine = Component.translatable("title.youzaiworldcore.update_latest",
                        r.latestVersion()
                                + (r.latestType() != null && !r.latestType().isEmpty() ? " (" + r.latestType() + ")" : "")
                ).getString();
                y = drawWrappedLine(graphics, font, latestLine, textX, y, maxTextWidth, textColor);

                // 发布时间
                if ((r.releaseDate() != null && !r.releaseDate().isEmpty())
                        || (r.releaseTime() != null && !r.releaseTime().isEmpty())) {
                    String rel = Component.translatable("title.youzaiworldcore.update_released",
                            r.releaseDate() == null ? "" : r.releaseDate(),
                            r.releaseTime() == null ? "" : r.releaseTime()).getString();
                    y = drawWrappedLine(graphics, font, rel, textX, y, maxTextWidth, textColor);
                }

                // 更新内容 — 不再添加 • 前缀
                if (r.changelog() != null && !r.changelog().isEmpty()) {
                    String head = Component.translatable("title.youzaiworldcore.update_changelog").getString();
                    y = drawWrappedLine(graphics, font, head, textX, y, maxTextWidth, textColor);
                    for (String line : r.changelog()) {
                        y = drawWrappedLine(graphics, font, line, textX, y, maxTextWidth, textColor);
                    }
                }

                // 内容总高度 = 面板高度 + 文本超出 textAreaBottom 的部分
                int contentH = panelH + Math.max(0, y - textAreaBottom);
                TitleScreenScrollState.setContentHeight(contentH);

                graphics.pose().popMatrix();
                graphics.disableScissor();

                // 定位按钮（文本裁切之外，始终完整可见）
                youzaiworldcore$positionUpdateButtons(textX, downloadY, ignoreY, maxTextWidth, fadeAlpha, r);
            }
        } else {
            // ===== 无更新时，显示原服务器公告 =====
            int annY = panelY + PANEL_PADDING;
            youzaiworldcore$drawAnnouncement(graphics, font, textX, annY, maxTextWidth, textColor, titleColor, titleShadow, panelBottom);
        }
    }

    /**
     * 渲染原服务器公告（标题 + 数行正文），超出面板底部时截断。
     */
    private void youzaiworldcore$drawAnnouncement(GuiGraphicsExtractor graphics, net.minecraft.client.gui.Font font,
                                                 int textX, int startY, int maxTextWidth,
                                                 int textColor, int titleColor, int titleShadow, int bottomLimit) {
        String title = Component.translatable("title.youzaiworldcore.announcement_title").getString();
        graphics.text(font, title, textX + 1, startY + 1, titleShadow);
        graphics.text(font, title, textX, startY, titleColor);

        String[] rawLines = {
            Component.translatable("title.youzaiworldcore.announcement_line1").getString(),
            Component.translatable("title.youzaiworldcore.announcement_line2").getString(),
            Component.translatable("title.youzaiworldcore.announcement_line3").getString(),
            "",
            Component.translatable("title.youzaiworldcore.announcement_line4").getString(),
        };

        int lineY = startY + font.lineHeight + 8;
        for (String raw : rawLines) {
            if (lineY > bottomLimit) break;
            lineY = drawWrappedLine(graphics, font, raw, textX, lineY, maxTextWidth, textColor);
        }
    }

    /**
     * 按当前更新状态定位下载 / 忽略按钮并设置可见性。
     * <p>忽略按钮仅在非强制更新时显示（强制更新不可忽略）。</p>
     */
    private void youzaiworldcore$positionUpdateButtons(int x, int downloadY, int ignoreY, int w, float fadeAlpha, UpdateResult r) {
        TitleScreenTextButton d = youzaiworldcore$downloadUpdateBtn;
        if (d != null) {
            d.visible = (true);
            d.setRenderAlpha(fadeAlpha);
            d.setX(x);
            d.setY(downloadY);
            d.setWidth(w);
        }
        TitleScreenTextButton i = youzaiworldcore$ignoreUpdateBtn;
        if (i != null) {
            boolean visible = !r.forcedUpdate();
            i.visible = (visible);
            if (visible) {
                i.setRenderAlpha(fadeAlpha);
                i.setX(x);
                i.setY(ignoreY);
                i.setWidth(w);
            }
        }
    }

    /**
     * 判断标题界面是否应显示更新信息块。
     * <p>规则：开启显示 + 存在可用更新 + （非强制更新时未被忽略）。</p>
     */
    private static boolean youzaiworldcore$shouldShowUpdate() {
        if (!UpdateCheckerConfig.isShowOnTitleScreen()) return false;
        UpdateResult r = ClientUpdateState.get();
        if (r == null || !r.updateAvailable()) return false;
        if (!r.forcedUpdate()) {
            String ignored = ClientExternalSettings.getIgnoredUpdateVersion();
            if (ignored != null && !ignored.isEmpty() && ignored.equals(r.latestVersion())) {
                return false;
            }
        }
        return true;
    }

    /** 强制更新状态下阻止进入服务器 */
    private static boolean youzaiworldcore$isForcedUpdateBlocking() {
        if (!UpdateCheckerConfig.isShowOnTitleScreen()) return false;
        UpdateResult r = ClientUpdateState.get();
        return r != null && r.updateAvailable() && r.forcedUpdate();
    }

    /** 点击「前往下载更新」：在默认浏览器打开构造好的下载页地址 */
    private void youzaiworldcore$openDownloadPage() {
        String url;
        if (ClientExternalSettings.isDevModeEnabled()
                && ClientExternalSettings.getUpdateJumpAddress() != null
                && !ClientExternalSettings.getUpdateJumpAddress().isEmpty()) {
            // 开发者模式 + 自定义跳转地址：实时按客户端设置构造（自动附加 ?version=&type=）
            url = UpdateChecker.buildJumpUrl(
                    ClientExternalSettings.getUpdateJumpAddress(),
                    UpdateChecker.getCurrentVersionString());
        } else {
            // 否则使用检查结果中的下载地址（含系统默认，或内嵌服务端依客户端设置解析）
            UpdateResult r = ClientUpdateState.get();
            url = (r != null) ? r.downloadUrl() : null;
        }
        if (url == null || url.isEmpty()) return;
        DebugLogger.entering("TitleScreenMixin", "openDownloadPage", url);
        try {
            URI uri = new URI(url);
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(uri);
                DebugLogger.info("TitleScreenMixin", "已在浏览器打开更新下载页: %s", url);
            } else {
                DebugLogger.warn("TitleScreenMixin", "当前环境不支持打开浏览器，下载地址: %s", url);
            }
        } catch (Exception e) {
            DebugLogger.exception("TitleScreenMixin", "openDownloadPage", e);
        }
    }

    /** 点击「不再提示此版本」：记住当前最新版本号，重启后仍不再提示（强制更新除外） */
    private void youzaiworldcore$ignoreUpdate() {
        UpdateResult r = ClientUpdateState.get();
        if (r == null || r.latestVersion() == null) return;
        DebugLogger.entering("TitleScreenMixin", "ignoreUpdate", r.latestVersion());
        ClientExternalSettings.setIgnoredUpdateVersion(r.latestVersion());
        DebugLogger.info("TitleScreenMixin", "已忽略版本更新提示: %s", r.latestVersion());
        if (youzaiworldcore$downloadUpdateBtn != null) youzaiworldcore$downloadUpdateBtn.visible = (false);
        if (youzaiworldcore$ignoreUpdateBtn != null) youzaiworldcore$ignoreUpdateBtn.visible = (false);
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
