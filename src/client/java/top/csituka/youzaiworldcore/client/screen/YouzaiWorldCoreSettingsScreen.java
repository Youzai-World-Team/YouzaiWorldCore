package top.csituka.youzaiworldcore.client.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.Panorama;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import top.csituka.youzaiworldcore.YouzaiworldCore;
import top.csituka.youzaiworldcore.client.config.ClientExternalSettings;
import top.csituka.youzaiworldcore.client.screen.widget.CheckboxButton;
import top.csituka.youzaiworldcore.update.UpdateChecker;
import top.csituka.youzaiworldcore.client.screen.widget.DropdownButton;
import top.csituka.youzaiworldcore.client.screen.widget.TitleScreenTextButton;
import top.csituka.youzaiworldcore.client.screen.widget.TransparentButton;
import top.csituka.youzaiworldcore.client.config.ConfigIOManager;
import top.csituka.youzaiworldcore.client.config.PlatformDetector;
import top.csituka.youzaiworldcore.util.DebugLogger;

import java.awt.Desktop;
import java.io.File;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;

/**
 * YouzaiWorldCore 设置界面。
 * <p>
 * 背景使用原版滚动全景图，左侧索引栏 + 右侧设置内容。
 * 可通过 OptionsScreen 中的「YouzaiWorldCore 设置...」按钮或
 * ModMenu 模组列表页面的「设置」按钮打开。
 * <p>
 * 当窗口高度不足以容纳所有内容时，右侧设置区可滚动，并显示滚动条。
 */
@SuppressWarnings("null")
public class YouzaiWorldCoreSettingsScreen extends Screen {

    private static final int SIDEBAR_WIDTH = 120;
    private static final int CONTENT_LEFT = 160;
    private static final int CONTENT_WIDTH = 320;

    /** 内容区顶部 Y（section 标题绘制的起始行） */
    private static final int CONTENT_TOP = 90;
    /** 内容区底部留白 */
    private static final int CONTENT_BOTTOM_PAD = 10;
    /** 滚动条宽度 */
    private static final int SCROLLBAR_WIDTH = 4;
    /** 滚动条距右侧间距 */
    private static final int SCROLLBAR_PAD = 2;

    // ===== 关于分栏专用常量 =====
    /** 关于分栏左上角的模组图标（64x64 PNG） */
    private static final Identifier MOD_ICON_TEXTURE =
            Identifier.fromNamespaceAndPath(YouzaiworldCore.MOD_ID, "textures/mod_icon.png");
    /** 图标边长（像素） */
    private static final int ABOUT_ICON_SIZE = 64;
    /** 标题字体缩放比例（相对默认字号） */
    private static final float ABOUT_TITLE_SCALE = 1.5f;
    /** 标题颜色（金橙） */
    private static final int ABOUT_TITLE_COLOR = 0xFFFFCC88;
    /** 图标圆角半径（像素） */
    private static final int ICON_CORNER_RADIUS = 6;

    private final Panorama panorama;

    /** 当前选中的分栏索引：0 = 视觉, 1 = 导出/导入配置, 2 = 关于, 3 = 开发者 */
    private int selectedSection = 0;

    // ===== 滚动状态 =====
    /** 当前垂直滚动偏移量（像素） */
    private double scrollOffset = 0.0;
    /** 内容区视口高度（buildContentWidgets 中根据 window height 计算） */
    private int viewportHeight = 0;
    /** 内容区底部边界 Y（视口底部 exl. padding） */
    private int contentBottom = 0;

    // ===== 组件引用 =====
    private TransparentButton closeButton;
    private TitleScreenTextButton sidebarDev;
    private TitleScreenTextButton sidebarConfigIo;
    private TitleScreenTextButton sidebarVisual;
    private TitleScreenTextButton sidebarAbout;
    /** 关于分栏底部的「查看开源许可」按钮 */
    private TransparentButton ossNoticeButton;
    private CheckboxButton devModeToggle;
    private DropdownButton logLevelDropdown;
    private DropdownButton debugModeDropdown;
    private DropdownButton skipActionDropdown;
    private EditBox debugAddressInput;
    private EditBox debugPortInput;

    // ===== 设置状态（通过 ClientExternalSettings 持久化） =====
    private boolean devModeEnabled;
    private int logLevel; // 0=关闭, 1=基本, 2=详细, 3=调试
    private String debugModeType; // "embedded" 或 "dedicated"
    private String debugAddress;
    private String debugPort;
    /** 是否启用 YZUI 自定义 UI 样式 */
    private boolean yzuiEnabled;

    /** 是否自动跳过实验性设置警告屏幕 */
    private boolean autoSkipExperimentalWarning;
    /** 自动跳过时的操作："skip" = 我知道我在做什么，"backup" = 创建备份并进入 */
    private String experimentalWarningSkipAction;

    // ===== 配置导入/导出分栏状态 =====
    /** 导出按钮（分栏 1） */
    private TransparentButton configExportButton;
    /** 导入按钮（分栏 2） */
    private TransparentButton configImportButton;
    /** 配置操作进行中（导出或导入） */
    private boolean configOpActive = false;
    /** 操作开始时间戳（毫秒） */
    private long configOpStartTime = 0;
    /** 操作类型： "export" 或 "import" */
    private String configOpType = "";
    /** 当前操作进度文本 */
    private String configOpProgressText = "";
    /** 是否 Android 平台（缓存） */
    private boolean isAndroidPlatform = false;
    /** 导出提示文字 Y */
    private int configExportHintY;
    /** 导入提示文字 Y */
    private int configImportHintY;
    /** 底部通用提示 Y */
    private int configBottomHintY;

    // ===== 文本标签 Y 坐标（由 buildContentWidgets 计算，extractRenderState 使用） =====
    /** "调试服务器" 子分栏标题 Y（仅专用服务端时显示） */
    private int debugSectionLabelY;
    private int debugAddrLabelY;
    private int debugPortLabelY;
    /** "重启客户端后生效" 提示文字 Y */
    private int restartHintY;

    /** 内容区底部的最大 Y 值（由 buildContentWidgets 追踪） */
    private int maxContentY = 0;

    private static final List<String> DEBUG_MODE_OPTIONS = List.of(
            Component.translatable("screen.youzaiworldcore.settings.debug_mode_embedded").getString(),
            Component.translatable("screen.youzaiworldcore.settings.debug_mode_dedicated").getString()
    );

    private static final List<String> LOG_LEVEL_OPTIONS = List.of(
            Component.translatable("screen.youzaiworldcore.settings.log_level_off").getString(),
            Component.translatable("screen.youzaiworldcore.settings.log_level_basic").getString(),
            Component.translatable("screen.youzaiworldcore.settings.log_level_detailed").getString(),
            Component.translatable("screen.youzaiworldcore.settings.log_level_debug").getString()
    );

    private static final List<String> EXPERIMENTAL_SKIP_ACTION_OPTIONS = List.of(
            Component.translatable("screen.youzaiworldcore.settings.experimental_skip_action_backup").getString(),
            Component.translatable("screen.youzaiworldcore.settings.experimental_skip_action_skip").getString()
    );

    public YouzaiWorldCoreSettingsScreen(Screen parent) {
        super(Component.translatable("screen.youzaiworldcore.settings.title"));
        this.panorama = new Panorama();
        // 从持久化配置读取初始状态
        this.devModeEnabled = ClientExternalSettings.isDevModeEnabled();
        this.logLevel = ClientExternalSettings.getLogLevel();
        this.debugModeType = ClientExternalSettings.getDebugModeType();
        this.debugAddress = ClientExternalSettings.getDebugAddress();
        this.debugPort = ClientExternalSettings.getDebugPort();
        this.yzuiEnabled = ClientExternalSettings.isYzuiEnabled();
        this.autoSkipExperimentalWarning = ClientExternalSettings.isAutoSkipExperimentalWarning();
        this.experimentalWarningSkipAction = ClientExternalSettings.getExperimentalWarningSkipAction();
    }

    @Override
    protected void init() {
        super.init();
        this.panorama.startSpin();
        // 初始化视口尺寸
        this.viewportHeight = this.height - CONTENT_TOP - CONTENT_BOTTOM_PAD;
        this.contentBottom = this.height - CONTENT_BOTTOM_PAD;
        rebuildWidgets();
    }

    @Override
    public void tick() {
        super.tick();
        // 26.2 的 EditBox 没有 tick() 方法，无需手动刷新光标
    }

    // ========== 事件处理（Y 坐标滚动修正） ==========

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int maxScroll = Math.max(0, maxContentY - viewportHeight);
        if (maxScroll <= 0) return false;
        // 仅当鼠标在内容区范围内时才响应滚动
        int baseX = CONTENT_LEFT + (this.width - CONTENT_LEFT - CONTENT_WIDTH) / 2;
        if (mouseX < baseX || mouseX > baseX + CONTENT_WIDTH) return false;
        // 每次滚动约 20px（与多数 UI 组件一致）
        scrollOffset -= scrollY * 20;
        scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset));
        return true;
    }

    /**
     * 注意：不覆盖 getChildAt — mouseClicked/mouseReleased/mouseDragged
     * 已在事件对象中做好 scrollOffset 调整，若 getChildAt 再追加一次将导致双重偏移，
     * 使滚动后的点击/拖拽全部失效。
     */

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean bl) {
        // 操作进行中时屏蔽所有点击
        if (configOpActive) return true;

        // ========== 固定位置组件（侧栏/关闭按钮）：使用屏幕坐标，不受滚动偏移影响 ==========
        double mx = event.x();
        double my = event.y();

        if (closeButton != null && mx >= closeButton.getX() && mx < closeButton.getX() + closeButton.getWidth()
                && my >= closeButton.getY() && my < closeButton.getY() + closeButton.getHeight()) {
            closeButton.onClick(event, bl);
            return true;
        }
        if (sidebarDev != null && mx >= sidebarDev.getX() && mx < sidebarDev.getX() + sidebarDev.getWidth()
                && my >= sidebarDev.getY() && my < sidebarDev.getY() + sidebarDev.getHeight()) {
            sidebarDev.onClick(event, bl);
            return true;
        }
        if (sidebarConfigIo != null && mx >= sidebarConfigIo.getX() && mx < sidebarConfigIo.getX() + sidebarConfigIo.getWidth()
                && my >= sidebarConfigIo.getY() && my < sidebarConfigIo.getY() + sidebarConfigIo.getHeight()) {
            sidebarConfigIo.onClick(event, bl);
            return true;
        }
        if (sidebarVisual != null && mx >= sidebarVisual.getX() && mx < sidebarVisual.getX() + sidebarVisual.getWidth()
                && my >= sidebarVisual.getY() && my < sidebarVisual.getY() + sidebarVisual.getHeight()) {
            sidebarVisual.onClick(event, bl);
            return true;
        }
        if (sidebarAbout != null && mx >= sidebarAbout.getX() && mx < sidebarAbout.getX() + sidebarAbout.getWidth()
                && my >= sidebarAbout.getY() && my < sidebarAbout.getY() + sidebarAbout.getHeight()) {
            if (!configOpActive) sidebarAbout.onClick(event, bl);
            return true;
        }

        // ========== 滚动内容区：用修正后的坐标检查弹窗外部点击 ==========
        double adjustedY = event.y() + scrollOffset;
        if (debugModeDropdown != null && debugModeDropdown.isOpen()
                && !debugModeDropdown.isPositionInsidePopup(event.x(), adjustedY)) {
            debugModeDropdown.closePopup();
        }
        if (logLevelDropdown != null && logLevelDropdown.isOpen()
                && !logLevelDropdown.isPositionInsidePopup(event.x(), adjustedY)) {
            logLevelDropdown.closePopup();
        }
        if (skipActionDropdown != null && skipActionDropdown.isOpen()
                && !skipActionDropdown.isPositionInsidePopup(event.x(), adjustedY)) {
            skipActionDropdown.closePopup();
        }
        // ===== 关于分栏：打开开源许可链接（与标题屏幕打开下载页实现一致） =====
        if (selectedSection == 2) {
            int btnY0 = CONTENT_TOP + 64 + 8;  // 图标正下方
            int btnX0 = CONTENT_LEFT + (this.width - CONTENT_LEFT - CONTENT_WIDTH) / 2;
            if (mx >= btnX0 && mx < btnX0 + ABOUT_ICON_SIZE
                    && adjustedY >= btnY0 && adjustedY < btnY0 + 22) {
                DebugLogger.info("SettingsScreen", "OSS button HIT");
                try {
                    URI uri = new URI(
                            "https://github.com/Youzai-World-Team/YouzaiWorldCore/blob/main/NOTICE.txt");
                    if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                        Desktop.getDesktop().browse(uri);
                        DebugLogger.info("SettingsScreen", "已在浏览器打开开源许可页");
                    } else {
                        DebugLogger.warn("SettingsScreen", "当前环境不支持打开浏览器");
                    }
                } catch (Exception e) {
                    DebugLogger.exception("SettingsScreen", "ossButtonClick", e);
                }
                return true;
            }
        }
        // 向子组件传递修正后的坐标（super 靠 adjustedEvent.y 匹配自然 Y 的 widget）
        MouseButtonEvent adjustedEvent = new MouseButtonEvent(
                event.x(), adjustedY, event.buttonInfo()
        );
        return super.mouseClicked(adjustedEvent, bl);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        MouseButtonEvent adjusted = new MouseButtonEvent(
                event.x(), event.y() + scrollOffset, event.buttonInfo()
        );
        return super.mouseReleased(adjusted);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        MouseButtonEvent adjusted = new MouseButtonEvent(
                event.x(), event.y() + scrollOffset, event.buttonInfo()
        );
        return super.mouseDragged(adjusted, dragX, dragY);
    }

    @Override
    public boolean keyPressed(KeyEvent keyEvent) {
        // 操作进行中时屏蔽键盘事件
        if (configOpActive) return true;

        if (keyEvent.key() == 256) { // ESC
            onClose();
            return true;
        }
        if (debugAddressInput != null && debugAddressInput.isFocused() && debugAddressInput.keyPressed(keyEvent))
            return true;
        if (debugPortInput != null && debugPortInput.isFocused() && debugPortInput.keyPressed(keyEvent))
            return true;
        return super.keyPressed(keyEvent);
    }

    @Override
    public boolean charTyped(CharacterEvent characterEvent) {
        if (debugAddressInput != null && debugAddressInput.isFocused() && debugAddressInput.charTyped(characterEvent))
            return true;
        if (debugPortInput != null && debugPortInput.isFocused() && debugPortInput.charTyped(characterEvent))
            return true;
        return super.charTyped(characterEvent);
    }

    // ========== 布局构建 ==========

    protected void rebuildWidgets() {
        this.clearWidgets();
        // 切换分栏或重置布局时复位滚动偏移
        scrollOffset = 0.0;
        maxContentY = 0;

        int cx = this.width / 2;

        // ===== 关闭按钮（右上角） =====
        closeButton = new TransparentButton(
                cx + 180, 8, 14, 14,
                Component.translatable("youzaiworldcore.message.gui.close_button"),
                this::onClose
        );
        closeButton.setBackgroundVisible(false);
        closeButton.setTextColor(0xFFFFFFFF);
        closeButton.setTextLeftAligned(true);
        addRenderableWidget(closeButton);

        // ===== 左侧索引栏 =====
        int sidebarX = 20;
        int sidebarY = 90;

        sidebarVisual = new TitleScreenTextButton(
                sidebarX, sidebarY, SIDEBAR_WIDTH, 22,
                Component.translatable("screen.youzaiworldcore.settings.sidebar_visual"),
                () -> { selectedSection = 0; rebuildWidgets(); }
        );
        sidebarVisual.setSelected(selectedSection == 0);
        addRenderableWidget(sidebarVisual);

        sidebarConfigIo = new TitleScreenTextButton(
                sidebarX, sidebarY + 30, SIDEBAR_WIDTH, 22,
                Component.translatable("screen.youzaiworldcore.settings.sidebar_config_io"),
                () -> { selectedSection = 1; rebuildWidgets(); }
        );
        sidebarConfigIo.setSelected(selectedSection == 1);
        sidebarConfigIo.active = !configOpActive;
        addRenderableWidget(sidebarConfigIo);

        sidebarAbout = new TitleScreenTextButton(
                sidebarX, sidebarY + 60, SIDEBAR_WIDTH, 22,
                Component.translatable("screen.youzaiworldcore.settings.sidebar_about"),
                () -> { selectedSection = 2; rebuildWidgets(); }
        );
        sidebarAbout.setSelected(selectedSection == 2);
        addRenderableWidget(sidebarAbout);

        sidebarDev = new TitleScreenTextButton(
                sidebarX, sidebarY + 90, SIDEBAR_WIDTH, 22,
                Component.translatable("screen.youzaiworldcore.settings.sidebar_developer"),
                () -> { selectedSection = 3; rebuildWidgets(); }
        );
        sidebarDev.setSelected(selectedSection == 3);
        addRenderableWidget(sidebarDev);

        // ===== 右侧设置内容 =====
        buildContentWidgets();
    }

    /**
     * 构建「导出/导入配置」分栏（selectedSection == 1）。
     * <p>
     * 包含导出按钮（PC 弹文件选择器 / Android 自动保存至 config_backups/）与导入按钮。
     * 操作进行中时按钮文案变为进度文本，操作锁定期间禁侧栏切换。
     * </p>
     */
    private void buildConfigIoSection(int baseX, int baseY) {
        this.isAndroidPlatform = PlatformDetector.isAndroid();

        int y = baseY + 16;
        int buttonWidth = CONTENT_WIDTH - 40;
        int btnX = baseX + (CONTENT_WIDTH - buttonWidth) / 2;

        // ----- 导出按钮 -----
        String exportLabel = (configOpActive && "export".equals(configOpType))
                ? configOpProgressText
                : Component.translatable("screen.youzaiworldcore.settings.config_io_export_btn").getString();
        configExportButton = new TransparentButton(
                btnX, y, buttonWidth, 22,
                Component.literal(exportLabel),
                this::onConfigExport
        );
        configExportButton.active = !configOpActive;
        configExportButton.setTextColor(0xFFFFFFFF);
        addRenderableWidget(configExportButton);
        y += 24;

        // 导出提示
        configExportHintY = y;
        y += 16;

        // ----- 导入按钮 -----
        y += 14;
        String importLabel = (configOpActive && "import".equals(configOpType))
                ? configOpProgressText
                : Component.translatable("screen.youzaiworldcore.settings.config_io_import_btn").getString();
        configImportButton = new TransparentButton(
                btnX, y, buttonWidth, 22,
                Component.literal(importLabel),
                this::onConfigImport
        );
        configImportButton.active = !configOpActive;
        configImportButton.setTextColor(0xFFFFFFFF);
        addRenderableWidget(configImportButton);
        y += 24;

        // 导入提示
        configImportHintY = y;
        y += 16;

        // 底部通用提示
        y += 20;
        configBottomHintY = y;

        maxContentY = y + 6;
    }

    /**
     * 导出按钮回调。自动保存至 {@code config_backups/} 目录。
     */
    private void onConfigExport() {
        if (configOpActive) return;
        DebugLogger.entering("SettingsScreen", "onConfigExport");
        File gameDir = Minecraft.getInstance().gameDirectory;

        configOpActive = true;
        configOpType = "export";
        configOpStartTime = System.currentTimeMillis();
        configOpProgressText = Component.translatable("screen.youzaiworldcore.settings.config_io_op_in_progress").getString();
        configExportButton.setMessage(Component.literal(configOpProgressText));
        configExportButton.active = false;
        configImportButton.active = false;
        sidebarConfigIo.active = false;

        ConfigIOManager.exportConfig(gameDir, (processed, total, phase) -> {
            int pct = total > 0 ? (int) ((float) processed / total * 100) : 0;
            String progress = Component.translatable(
                    "screen.youzaiworldcore.settings.config_io_op_progress",
                    pct
            ).getString();
            // 更新按钮文本（回主线程）
            Minecraft.getInstance().execute(() -> {
                configOpProgressText = progress;
                if (configExportButton != null) {
                    configExportButton.setMessage(Component.literal(progress));
                }
            });
        }).thenAccept(path -> {
            Minecraft.getInstance().execute(() -> {
                finishConfigOp();
                // 自动保存至 config_backups/，用 Toast 提示路径
                showToast(Component.translatable("message.youzaiworldcore.config_io.export_saved",
                        Component.literal(path.toString())));
            });
        }).exceptionally(ex -> {
            Minecraft.getInstance().execute(() -> {
                finishConfigOp();
                String msg = extractErrorMessage(ex);
                showToast(Component.literal("§e" + msg));
            });
            return null;
        });
    }

    /**
     * 导入按钮回调。打开备份文件列表（全平台统一）。
     */
    private void onConfigImport() {
        if (configOpActive) return;
        DebugLogger.entering("SettingsScreen", "onConfigImport");
        File gameDir = Minecraft.getInstance().gameDirectory;

        Minecraft.getInstance().execute(() -> {
            Minecraft.getInstance().setScreenAndShow(new ConfigBackupListScreen(this, gameDir));
        });
    }

    /** 完成操作（恢复按钮状态） */
    private void finishConfigOp() {
        configOpActive = false;
        configOpType = "";
        configOpProgressText = "";
        if (configExportButton != null) {
            configExportButton.setMessage(Component.translatable("screen.youzaiworldcore.settings.config_io_export_btn"));
            configExportButton.active = true;
        }
        if (configImportButton != null) {
            configImportButton.setMessage(Component.translatable("screen.youzaiworldcore.settings.config_io_import_btn"));
            configImportButton.active = true;
        }
        if (sidebarConfigIo != null) sidebarConfigIo.active = true;
    }

    /** 从异常中提取用户可阅读的错误消息 */
    private static String extractErrorMessage(Throwable ex) {
        Throwable cause = ex;
        while (cause.getCause() != null) cause = cause.getCause();
        String msg = cause.getMessage();
        if (msg == null) msg = ex.getMessage();
        if (msg == null) return Component.translatable("message.youzaiworldcore.config_io.import_failed_generic").getString();
        if (msg.contains("被占用")) return Component.translatable("message.youzaiworldcore.config_io.import_failed_occupied").getString();
        if (msg.contains("ZIP 炸弹") || msg.contains("损坏") || msg.contains("无效")) {
            return Component.translatable("message.youzaiworldcore.config_io.import_failed_corrupt").getString();
        }
        if (msg.contains("磁盘空间")) return Component.translatable("message.youzaiworldcore.config_io.import_failed_disk").getString();
        if (msg.contains("无效")) return Component.translatable("message.youzaiworldcore.config_io.import_invalid_pack").getString();
        // 通用
        return Component.translatable("message.youzaiworldcore.config_io.import_failed_generic").getString();
    }

    /** 显示 Toast（MC 原生 ToastManager，Android 端也可渲染） */
    private void showToast(Component message) {
        var toast = new net.minecraft.client.gui.components.toasts.SystemToast(
                new net.minecraft.client.gui.components.toasts.SystemToast.SystemToastId(),
                Component.translatable("screen.youzaiworldcore.config_io.export_success_title"),
                message
        );
        Minecraft.getInstance().gui.toastManager().addToast(toast);
    }

    private void buildContentWidgets() {
        int baseX = CONTENT_LEFT + (this.width - CONTENT_LEFT - CONTENT_WIDTH) / 2;
        int baseY = CONTENT_TOP;

        if (selectedSection == 3) {
            // 开发者分栏 — 直接内联，不再委托给 buildContentWidgetsDeveloper（原计划未实现）
            int y = baseY + 23;  // warning 文字底 (CONTENT_TOP+14 + lineHeight≈9 = 113) + 1px 间隙

            // 启用开发者模式（始终显示）
            devModeToggle = new CheckboxButton(
                    baseX, y, CONTENT_WIDTH, 20,
                    Component.translatable("screen.youzaiworldcore.settings.checkbox_dev_mode"),
                    devModeEnabled,
                    () -> {
                        ClientExternalSettings.setDevModeEnabled(!devModeEnabled);
                        devModeEnabled = !devModeEnabled;
                        // 同步到全局标志
                        top.csituka.youzaiworldcore.YouzaiworldCore.devModeEnabled = devModeEnabled;
                        rebuildWidgets();
                    }
            );
            addRenderableWidget(devModeToggle);
            y += 26;

            // 自动跳过实验性设置警告（始终显示，不依赖开发者模式）
            CheckboxButton autoSkipExperimentalToggle = new CheckboxButton(
                    baseX, y, CONTENT_WIDTH, 20,
                    Component.translatable("screen.youzaiworldcore.settings.checkbox_auto_skip_experimental_warning"),
                    autoSkipExperimentalWarning,
                    () -> {
                        boolean newVal = !autoSkipExperimentalWarning;
                        autoSkipExperimentalWarning = newVal;
                        ClientExternalSettings.setAutoSkipExperimentalWarning(newVal);
                        DebugLogger.info("SettingsScreen", "自动跳过实验性设置警告已" + (newVal ? "启用" : "禁用"));
                    }
            );
            addRenderableWidget(autoSkipExperimentalToggle);
            y += 26;

            // 自动跳过时的操作选择（下拉框）
            y += 4;
            int skipActionIndex = "backup".equals(experimentalWarningSkipAction) ? 0 : 1;
            skipActionDropdown = new DropdownButton(
                    baseX, y, CONTENT_WIDTH, SIDEBAR_WIDTH, 20,
                    Component.translatable("screen.youzaiworldcore.settings.dropdown_experimental_skip_action"),
                    EXPERIMENTAL_SKIP_ACTION_OPTIONS,
                    skipActionIndex,
                    false,
                    idx -> {
                        String newAction = (idx == 0) ? "backup" : "skip";
                        ClientExternalSettings.setExperimentalWarningSkipAction(newAction);
                        experimentalWarningSkipAction = newAction;
                        DebugLogger.info("SettingsScreen", "实验性设置跳过操作已设为：" + newAction);
                    },
                    null
            );
            addRenderableWidget(skipActionDropdown);
            y += 26;

            if (devModeEnabled) {
                // ===== 开发者模式下才显示的选项 =====

                // ===== 日志输出丰富度（下拉框） =====
                y += 4;
                logLevelDropdown = new DropdownButton(
                        baseX, y, CONTENT_WIDTH, SIDEBAR_WIDTH, 20,
                        Component.translatable("screen.youzaiworldcore.settings.dropdown_log_level"),
                        LOG_LEVEL_OPTIONS,
                        logLevel,
                        false,
                        idx -> {
                            ClientExternalSettings.setLogLevel(idx);
                            logLevel = idx;
                        },
                        null
                );
                addRenderableWidget(logLevelDropdown);
                y += 26;

                // "重启客户端后生效" 提示文字
                restartHintY = y;
                y += 12;

                // ===== 调试方式选择 =====
                y += 4;
                boolean isDedicated = "dedicated".equals(debugModeType);
                int debugModeIndex = isDedicated ? 1 : 0;

                // "调试方式" 下拉选择框
                debugModeDropdown = new DropdownButton(
                        baseX, y, CONTENT_WIDTH, SIDEBAR_WIDTH, 20,
                        Component.translatable("screen.youzaiworldcore.settings.dropdown_debug_mode"),
                        DEBUG_MODE_OPTIONS,
                        debugModeIndex,
                        false,
                        idx -> {
                            String newType = (idx == 0) ? "embedded" : "dedicated";
                            ClientExternalSettings.setDebugModeType(newType);
                            debugModeType = newType;
                            // 切换调试方式后完全重建布局，自动处理 Y 坐标重新计算与可见性
                            rebuildWidgets();
                        },
                        null
                );
                addRenderableWidget(debugModeDropdown);
                y += 26;

                // ===== 调试服务器区域（专用服务端时占用垂直空间，内嵌时跳过 Y 偏移） =====
                // 始终创建 widget（保持下拉回调可切换可见性），但仅专用模式时递增 y
                y += 4;
                debugSectionLabelY = isDedicated ? y : -1;
                if (isDedicated) y += 12;

                // 地址输入框 + 标签
                debugAddrLabelY = isDedicated ? y : -1;
                if (isDedicated) y += 10;
                debugAddressInput = new EditBox(
                        this.font, baseX, y, CONTENT_WIDTH, 20,
                        Component.translatable("screen.youzaiworldcore.settings.label_address")
                );
                debugAddressInput.setValue(debugAddress);
                debugAddressInput.setResponder(s -> {
                    debugAddress = s;
                    ClientExternalSettings.setDebugAddress(s);
                });
                debugAddressInput.setVisible(isDedicated);
                addRenderableWidget(debugAddressInput);
                y += (isDedicated ? 26 : 0);

                // 端口输入框 + 标签
                debugPortLabelY = isDedicated ? y : -1;
                if (isDedicated) y += 10;
                debugPortInput = new EditBox(
                        this.font, baseX, y, CONTENT_WIDTH, 20,
                        Component.translatable("screen.youzaiworldcore.settings.label_port")
                );
                debugPortInput.setValue(debugPort);
                debugPortInput.setResponder(s -> {
                    debugPort = s;
                    ClientExternalSettings.setDebugPort(s);
                });
                debugPortInput.setVisible(isDedicated);
                addRenderableWidget(debugPortInput);
                y += (isDedicated ? 26 : 0);
            } else {
                logLevelDropdown = null;
                debugModeDropdown = null;
                skipActionDropdown = null;
                debugAddressInput = null;
                debugPortInput = null;
            }

            // 追踪实际内容底部 Y（最后一个输入框底部 + 余量）
            maxContentY = y + 6;
        } else if (selectedSection == 1) {
            // 导出/导入配置分栏
            buildConfigIoSection(baseX, baseY);
        } else if (selectedSection == 0) {
            // 视觉分栏 — YZUI 开关
            int y = baseY + 16;

            CheckboxButton yzuiToggle = new CheckboxButton(
                    baseX, y, CONTENT_WIDTH, 20,
                    Component.translatable("screen.youzaiworldcore.settings.toggle_yzui"),
                    yzuiEnabled,
                    () -> {
                        boolean newVal = !yzuiEnabled;
                        yzuiEnabled = newVal;
                        ClientExternalSettings.setYzuiEnabled(newVal);
                        DebugLogger.info("SettingsScreen", "YZUI 已" + (newVal ? "启用" : "禁用"));
                    }
            );
            addRenderableWidget(yzuiToggle);

            maxContentY = y + 40;
        } else if (selectedSection == 2) {
            // 关于分栏 — 添加查看开源许可按钮
            int btnY = CONTENT_TOP + 64 + 8;  // 图标正下方（≈ 90+64+8 = 162）
            ossNoticeButton = new TransparentButton(
                    baseX, btnY, ABOUT_ICON_SIZE, 22,
                    Component.translatable("screen.youzaiworldcore.settings.about_license_btn_notice"),
                    () -> {}  // 点击逻辑在 mouseClicked 中，用 Runtime.exec 绕过 headless 限制
            );
            addRenderableWidget(ossNoticeButton);
            // 内容区高度：仅覆盖实际文本与按钮末尾，不留多余空白 → 无滚动条
            maxContentY = CONTENT_TOP + 255;  // ≈345，覆盖 5 条鸣谢 + OSS 致谢末尾
            DebugLogger.info("SettingsScreen", "关于分栏: maxContentY=%d (height=%d, vpH=%d)",
                    maxContentY, this.height, this.viewportHeight);
        }
    }

    // ========== 换行文本渲染辅助 ==========

    /**
     * 渲染带自动换行的文本。逐字符追加并测量宽度，超出 maxWidth 时自动断行。
     * 返回下一行的起始 Y（最后一行底部 + LINE_GAP）。
     */
    private int drawWrappedText(GuiGraphicsExtractor guiGraphics, Component text,
                                int x, int y, int maxWidth, int color, boolean shadow) {
        String raw = text.getString();
        // 逐字符分组，确保每行 ≤ maxWidth
        List<String> chunks = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (int i = 0; i < raw.length(); i++) {
            String test = current.toString() + raw.charAt(i);
            if (this.font.width(Component.literal(test)) > maxWidth && !current.isEmpty()) {
                chunks.add(current.toString());
                current = new StringBuilder().append(raw.charAt(i));
            } else {
                current.append(raw.charAt(i));
            }
        }
        if (!current.isEmpty()) chunks.add(current.toString());

        // 逐行渲染
        int currentY = y;
        for (String chunk : chunks) {
            guiGraphics.text(this.font, Component.literal(chunk).withStyle(text.getStyle()),
                    x, currentY, color, shadow);
            currentY += this.font.lineHeight + 2;
        }
        return currentY;
    }

    /**
     * 为矩形添加圆角效果：覆盖角上的像素以产生裁切感。
     * 使用和内容区背景色相同的半透明黑色。
     */
    private void clipRoundedCorners(GuiGraphicsExtractor guiGraphics, int x, int y, int w, int h, int r) {
        // 背景覆盖色 = 0x60000000（与内容区的半透明黑色遮罩一致）
        int bgColor = 0x60000000;
        for (int i = 0; i < r; i++) {
            for (int j = 0; j < r; j++) {
                int dx = r - 1 - i;
                int dy = r - 1 - j;
                if (dx * dx + dy * dy >= r * r) {
                    // 左上角
                    guiGraphics.fill(x + i, y + j, x + i + 1, y + j + 1, bgColor);
                    // 右上角
                    guiGraphics.fill(x + w - 1 - i, y + j, x + w - i, y + j + 1, bgColor);
                    // 左下角
                    guiGraphics.fill(x + i, y + h - 1 - j, x + i + 1, y + h - j, bgColor);
                    // 右下角
                    guiGraphics.fill(x + w - 1 - i, y + h - 1 - j, x + w - i, y + h - j, bgColor);
                }
            }
        }
    }

    // ========== 渲染 ==========

    @Override
    public void extractRenderState(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTick) {
        // 1. 背景层（屏幕坐标）
        this.panorama.extractRenderState(guiGraphics, this.width, this.height);
        guiGraphics.fill(0, 0, this.width, this.height, 0x60_00_00_00);

        int cx = this.width / 2;

        // 标题
        var titleText = Component.translatable("screen.youzaiworldcore.settings.title");
        int titleWidth = this.font.width(titleText);
        guiGraphics.text(this.font, titleText, cx - titleWidth / 2, 12, 0xFFFFFFFF, false);

        // 说明文字
        var desc = Component.translatable("screen.youzaiworldcore.settings.desc_line1");
        var desc2 = Component.translatable("screen.youzaiworldcore.settings.desc_line2");
        int descColor = 0xB0FFFFFF;
        guiGraphics.text(this.font, desc, cx - this.font.width(desc) / 2, 40, descColor, false);
        guiGraphics.text(this.font, desc2, cx - this.font.width(desc2) / 2, 52, descColor, false);

        int baseX = CONTENT_LEFT + (this.width - CONTENT_LEFT - CONTENT_WIDTH) / 2;

        // ===================================================================
        // 2. 预渲染侧栏 + 关闭按钮（屏幕坐标，不受裁切影响 → 始终可见）
        // ===================================================================
        closeButton.extractRenderState(guiGraphics, mouseX, mouseY, partialTick);
        sidebarDev.extractRenderState(guiGraphics, mouseX, mouseY, partialTick);
        if (sidebarConfigIo != null) {
            sidebarConfigIo.extractRenderState(guiGraphics, mouseX, mouseY, partialTick);
        }
        if (sidebarVisual != null) {
            sidebarVisual.extractRenderState(guiGraphics, mouseX, mouseY, partialTick);
        }
        if (sidebarAbout != null) {
            sidebarAbout.extractRenderState(guiGraphics, mouseX, mouseY, partialTick);
        }

        // ===================================================================
        // 3. 内容区（可滚动）— 先设裁切（屏幕坐标），再平移坐标系
        // ===================================================================
        // enableScissor 在 pushMatrix 之前调用：裁切矩形保留在屏幕坐标中，
        // 不受后续 translate 影响，正确限制内容区的可见范围。
        guiGraphics.enableScissor(baseX, CONTENT_TOP, baseX + CONTENT_WIDTH, contentBottom);

        guiGraphics.pose().pushMatrix();
        guiGraphics.pose().translate(0, -(float) scrollOffset);

        // 3a. 纯文本标签（在平移坐标系下使用自然 Y 坐标）
        if (selectedSection == 3) {
            guiGraphics.text(this.font, Component.translatable("screen.youzaiworldcore.settings.sidebar_developer"),
                    baseX, CONTENT_TOP, 0xFFFFFFFF, false);
            guiGraphics.text(this.font, Component.translatable("screen.youzaiworldcore.settings.dev_warning"),
                    baseX, CONTENT_TOP + 14, 0x80FFFFFF, false);

            if (devModeEnabled) {
                var restartHint = Component.translatable("screen.youzaiworldcore.settings.log_level_restart_hint");
                guiGraphics.text(this.font, restartHint,
                        baseX, restartHintY, 0x80FFFFFF, false);

                if ("dedicated".equals(debugModeType)) {
                    guiGraphics.text(this.font, Component.translatable("screen.youzaiworldcore.settings.label_debug_section"),
                            baseX, debugSectionLabelY, 0xFFFFCC88, false);
                    guiGraphics.text(this.font, Component.translatable("screen.youzaiworldcore.settings.label_address"),
                            baseX, debugAddrLabelY, 0xB0FFFFFF, false);
                    guiGraphics.text(this.font, Component.translatable("screen.youzaiworldcore.settings.label_port"),
                            baseX, debugPortLabelY, 0xB0FFFFFF, false);
                }
            }
        } else if (selectedSection == 1) {
            // 导出/导入配置分栏文本
            guiGraphics.text(this.font, Component.translatable("screen.youzaiworldcore.settings.sidebar_config_io"),
                    baseX, CONTENT_TOP, 0xFFFFFFFF, false);

            // 导出提示文字
            String exportHintKey = isAndroidPlatform
                    ? "screen.youzaiworldcore.settings.config_io_export_hint_android"
                    : "screen.youzaiworldcore.settings.config_io_export_hint_pc";
            guiGraphics.text(this.font, Component.translatable(exportHintKey),
                    baseX, configExportHintY, 0x80FFFFFF, false);

            // 导入提示文字
            guiGraphics.text(this.font, Component.translatable("screen.youzaiworldcore.settings.config_io_import_hint"),
                    baseX, configImportHintY, 0x80FFFFFF, false);

            // 底部通用提示
            guiGraphics.text(this.font, Component.translatable("screen.youzaiworldcore.settings.config_io_bottom_hint_line1"),
                    baseX, configBottomHintY, 0x60FFFFFF, false);
            guiGraphics.text(this.font, Component.translatable("screen.youzaiworldcore.settings.config_io_bottom_hint_line2"),
                    baseX, configBottomHintY + 10, 0x60FFFFFF, false);
        } else if (selectedSection == 0) {
            // ===== 视觉分栏文本 =====
            guiGraphics.text(this.font, Component.translatable("screen.youzaiworldcore.settings.sidebar_visual"),
                    baseX, CONTENT_TOP, 0xFFFFFFFF, false);
        } else if (selectedSection == 2) {
            // ===== 关于分栏 =====
            // 所有文字在图标右侧，使用 drawWrappedText 自动换行。
            // 除"开源许可"和"感谢所有群成员"后有一空行外，其余行间无多余空行。
            String version = UpdateChecker.getCurrentVersionString();
            int topY = CONTENT_TOP;
            int iconX = baseX;
            int iconY = topY;
            int textX = baseX + ABOUT_ICON_SIZE + 10;
            int wrapWidth = CONTENT_WIDTH - ABOUT_ICON_SIZE - 10;  // 320 - 64 - 10 = 246

            // 1. 绘制模组图标 + 圆角（左上）
            guiGraphics.blit(RenderPipelines.GUI_TEXTURED, MOD_ICON_TEXTURE,
                    iconX, iconY, 0, 0,
                    ABOUT_ICON_SIZE, ABOUT_ICON_SIZE,
                    ABOUT_ICON_SIZE, ABOUT_ICON_SIZE);
            clipRoundedCorners(guiGraphics, iconX, iconY, ABOUT_ICON_SIZE, ABOUT_ICON_SIZE, ICON_CORNER_RADIUS);

            int y = topY;
            // 2. 标题（1.5x，图标右侧）
            guiGraphics.pose().pushMatrix();
            guiGraphics.pose().scale(ABOUT_TITLE_SCALE, ABOUT_TITLE_SCALE);
            guiGraphics.text(this.font, Component.translatable("screen.youzaiworldcore.settings.about_title"),
                    Math.round(textX / ABOUT_TITLE_SCALE),
                    Math.round((float) y / ABOUT_TITLE_SCALE),
                    ABOUT_TITLE_COLOR, false);
            guiGraphics.pose().popMatrix();

            // 3. 版本号
            y = topY + 18;
            guiGraphics.text(this.font, Component.translatable(
                            "screen.youzaiworldcore.settings.about_version", version),
                    textX, y, 0xFFFFFFFF, false);

            // 4. 描述文本（带换行）
            y = topY + 30;
            y = drawWrappedText(guiGraphics,
                    Component.translatable("screen.youzaiworldcore.settings.about_desc_line1"),
                    textX, y, wrapWidth, 0xA0FFFFFF, false);
            y = drawWrappedText(guiGraphics,
                    Component.translatable("screen.youzaiworldcore.settings.about_desc_line2"),
                    textX, y, wrapWidth, 0xA0FFFFFF, false);

            // 5. 链接与版权（行间仅 4px 呼吸空间）
            y += 4;
            y = drawWrappedText(guiGraphics,
                    Component.translatable("screen.youzaiworldcore.settings.about_website"),
                    textX, y, wrapWidth, 0xFFFFFFFF, false);
            y += 4;
            y = drawWrappedText(guiGraphics,
                    Component.translatable("screen.youzaiworldcore.settings.about_authors"),
                    textX, y, wrapWidth, 0x80FFFFFF, false);
            y += 4;
            y = drawWrappedText(guiGraphics,
                    Component.translatable("screen.youzaiworldcore.settings.about_license"),
                    textX, y, wrapWidth, 0x80FFFFFF, false);

            // 6. 鸣谢（开源许可后有一空行）
            y += 20;
            y = drawWrappedText(guiGraphics,
                    Component.translatable("screen.youzaiworldcore.settings.about_credit_why"),
                    textX, y, wrapWidth, 0x80FFFFFF, false);
            y += 4;
            y = drawWrappedText(guiGraphics,
                    Component.translatable("screen.youzaiworldcore.settings.about_credit_byzzdemy"),
                    textX, y, wrapWidth, 0x80FFFFFF, false);
            y += 4;
            y = drawWrappedText(guiGraphics,
                    Component.translatable("screen.youzaiworldcore.settings.about_credit_zhongend"),
                    textX, y, wrapWidth, 0x80FFFFFF, false);
            y += 4;
            y = drawWrappedText(guiGraphics,
                    Component.translatable("screen.youzaiworldcore.settings.about_credit_testers"),
                    textX, y, wrapWidth, 0x80FFFFFF, false);

            // 7. OSS 致谢（感谢测试后有一空行），纯文本，自动换行
            y += 20;
            y = drawWrappedText(guiGraphics,
                    Component.translatable("screen.youzaiworldcore.settings.about_credit_oss"),
                    textX, y, wrapWidth, 0xA0FFFFFF, false);
            // 按钮由 buildContentWidgets 渲染，不在此处
        }

        // 3b. 父类渲染 widgets（侧栏/关闭按钮在此二次渲染，但被裁切矩形剪裁 → 不可见）
        //     传入修正后的鼠标 Y 以保证 hover 高亮正确
        super.extractRenderState(guiGraphics, mouseX, (int) (mouseY + scrollOffset), partialTick);

        // 弹窗同属平移坐标系，关闭裁切以免弹窗被截断
        guiGraphics.disableScissor();

        // 3c. 下拉弹窗后置渲染（在平移坐标系中保持与按钮的相对位置）
        if (debugModeDropdown != null) {
            debugModeDropdown.renderPopup(guiGraphics, mouseX, (int) (mouseY + scrollOffset), partialTick);
        }
        if (logLevelDropdown != null) {
            logLevelDropdown.renderPopup(guiGraphics, mouseX, (int) (mouseY + scrollOffset), partialTick);
        }
        if (skipActionDropdown != null) {
            skipActionDropdown.renderPopup(guiGraphics, mouseX, (int) (mouseY + scrollOffset), partialTick);
        }

        guiGraphics.pose().popMatrix();

        // ===================================================================
        // 4. 滚动条（屏幕坐标，位于内容区右侧边缘）
        // ===================================================================
        int maxScroll = Math.max(0, maxContentY - viewportHeight);
        if (maxScroll > 0) {
            int scrollbarLeft = baseX + CONTENT_WIDTH + SCROLLBAR_PAD;
            int scrollbarRight = scrollbarLeft + SCROLLBAR_WIDTH;
            int scrollbarTop = CONTENT_TOP;
            int scrollbarBottom = contentBottom;

            // 滚动条轨道
            guiGraphics.fill(scrollbarLeft, scrollbarTop, scrollbarRight, scrollbarBottom, 0x30FFFFFF);

            // 滚动条滑块（按比例计算高度与位置）
            double ratio = (double) viewportHeight / maxContentY;
            int thumbHeight = Math.max(12, (int) (ratio * viewportHeight));
            int thumbY = scrollbarTop + (int) ((scrollOffset / maxScroll) * (viewportHeight - thumbHeight));
            guiGraphics.fill(scrollbarLeft, thumbY, scrollbarRight, thumbY + thumbHeight, 0x80FFFFFF);
        }

        // ===================================================================
        // 5. 防 ANR 遮罩（操作耗时 > 5 秒时显示）
        // ===================================================================
        if (configOpActive) {
            long elapsed = System.currentTimeMillis() - configOpStartTime;
            if (elapsed > 5000) {
                // 半透明遮罩
                guiGraphics.fill(0, 0, this.width, this.height, 0x80000000);

                // 进度条背景
                int barWidth = 200;
                int barHeight = 8;
                int barX = (this.width - barWidth) / 2;
                int barY = this.height / 2;

                // 背景矩形
                guiGraphics.fill(barX, barY, barX + barWidth, barY + barHeight, 0xFFFFFFFF);

                // 前景进度（模拟，仅使用简单动画）
                float progress = Math.min(1f, (elapsed - 5000) / 30000f); // 0→1 over 30 seconds
                int fillWidth = (int) (barWidth * progress);
                guiGraphics.fill(barX, barY, barX + fillWidth, barY + barHeight, 0xFF00AAFF);

                // 操作提示文字
                String opLabel = "import".equals(configOpType)
                        ? Component.translatable("screen.youzaiworldcore.settings.config_io_importing_mask").getString()
                        : Component.translatable("screen.youzaiworldcore.settings.config_io_exporting_mask").getString();
                int opLabelWidth = this.font.width(opLabel);
                guiGraphics.text(this.font, opLabel,
                        (this.width - opLabelWidth) / 2, barY - 16, 0xFFFFFFFF, false);

                // 当前进度文本
                if (configOpProgressText != null && !configOpProgressText.isEmpty()) {
                    int progWidth = this.font.width(configOpProgressText);
                    guiGraphics.text(this.font, configOpProgressText,
                            (this.width - progWidth) / 2, barY + barHeight + 6, 0xB0FFFFFF, false);
                }
            }
        }
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTick) {
    }

    @Override
    public boolean isPauseScreen() {
        return true;
    }

    @Override
    public void onClose() {
        if (configOpActive) return; // 操作进行中不可关闭
        Minecraft.getInstance().gui.setScreen(null);
    }
}
