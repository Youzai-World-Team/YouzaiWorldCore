package top.csituka.youzaiworldcore.client.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.Panorama;
import net.minecraft.network.chat.Component;
import top.csituka.youzaiworldcore.client.config.ClientExternalSettings;
import top.csituka.youzaiworldcore.client.screen.widget.CheckboxButton;
import top.csituka.youzaiworldcore.update.UpdateAddressState;
import top.csituka.youzaiworldcore.client.screen.widget.DropdownButton;
import top.csituka.youzaiworldcore.client.screen.widget.TitleScreenTextButton;
import top.csituka.youzaiworldcore.client.screen.widget.TransparentButton;
import top.csituka.youzaiworldcore.client.config.ConfigIOManager;
import top.csituka.youzaiworldcore.client.config.PlatformDetector;
import top.csituka.youzaiworldcore.util.DebugLogger;

import java.io.File;
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

    private final Panorama panorama;

    /** 当前选中的分栏索引：0 = 实验性功能, 1 = 开发者, 2 = 导出/导入配置 */
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
    private TitleScreenTextButton sidebarExpFeatures;
    private TitleScreenTextButton sidebarDev;
    private TitleScreenTextButton sidebarConfigIo;
    private CheckboxButton devModeToggle;
    private DropdownButton logLevelDropdown;
    private DropdownButton debugModeDropdown;
    private EditBox debugAddressInput;
    private EditBox debugPortInput;

    // ===== 更新服务器区域（开发者模式下显示） =====
    private EditBox updateCheckInput;
    private EditBox updateJumpInput;

    // ===== 设置状态（通过 ClientExternalSettings 持久化） =====
    private boolean devModeEnabled;
    private int logLevel; // 0=关闭, 1=基本, 2=详细, 3=调试
    private String debugModeType; // "embedded" 或 "dedicated"
    private String debugAddress;
    private String debugPort;
    private String updateCheckAddress;
    private String updateJumpAddress;

    // ===== 配置导入/导出分栏状态 =====
    /** 导出按钮（分栏 2） */
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

    // ===== 更新服务器区域标签 Y 坐标 =====
    /** "更新服务器" 子分栏标题 Y */
    private int updateSectionLabelY;
    private int updateCheckLabelY;
    private int updateJumpLabelY;

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

    public YouzaiWorldCoreSettingsScreen(Screen parent) {
        super(Component.translatable("screen.youzaiworldcore.settings.title"));
        this.panorama = new Panorama();
        // 从持久化配置读取初始状态
        this.devModeEnabled = ClientExternalSettings.isDevModeEnabled();
        this.logLevel = ClientExternalSettings.getLogLevel();
        this.debugModeType = ClientExternalSettings.getDebugModeType();
        this.debugAddress = ClientExternalSettings.getDebugAddress();
        this.debugPort = ClientExternalSettings.getDebugPort();
        this.updateCheckAddress = ClientExternalSettings.getUpdateCheckAddress();
        this.updateJumpAddress = ClientExternalSettings.getUpdateJumpAddress();
        // 打开设置界面时，将当前（持久化）客户端更新地址推送到共享状态，供内嵌服务端使用
        pushUpdateState();
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

        // 用修正后的坐标检查弹窗外部点击（弹窗位置与 widgets 同坐标系均为自然坐标）
        double adjustedY = event.y() + scrollOffset;
        if (debugModeDropdown != null && debugModeDropdown.isOpen()
                && !debugModeDropdown.isPositionInsidePopup(event.x(), adjustedY)) {
            debugModeDropdown.closePopup();
        }
        if (logLevelDropdown != null && logLevelDropdown.isOpen()
                && !logLevelDropdown.isPositionInsidePopup(event.x(), adjustedY)) {
            logLevelDropdown.closePopup();
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
        if (updateCheckInput != null && updateCheckInput.isFocused() && updateCheckInput.keyPressed(keyEvent))
            return true;
        if (updateJumpInput != null && updateJumpInput.isFocused() && updateJumpInput.keyPressed(keyEvent))
            return true;
        return super.keyPressed(keyEvent);
    }

    @Override
    public boolean charTyped(CharacterEvent characterEvent) {
        if (debugAddressInput != null && debugAddressInput.isFocused() && debugAddressInput.charTyped(characterEvent))
            return true;
        if (debugPortInput != null && debugPortInput.isFocused() && debugPortInput.charTyped(characterEvent))
            return true;
        if (updateCheckInput != null && updateCheckInput.isFocused() && updateCheckInput.charTyped(characterEvent))
            return true;
        if (updateJumpInput != null && updateJumpInput.isFocused() && updateJumpInput.charTyped(characterEvent))
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

        sidebarExpFeatures = new TitleScreenTextButton(
                sidebarX, sidebarY, SIDEBAR_WIDTH, 22,
                Component.translatable("screen.youzaiworldcore.settings.sidebar_experimental"),
                () -> { selectedSection = 0; rebuildWidgets(); }
        );
        sidebarExpFeatures.setSelected(selectedSection == 0);
        addRenderableWidget(sidebarExpFeatures);

        sidebarDev = new TitleScreenTextButton(
                sidebarX, sidebarY + 30, SIDEBAR_WIDTH, 22,
                Component.translatable("screen.youzaiworldcore.settings.sidebar_developer"),
                () -> { selectedSection = 1; rebuildWidgets(); }
        );
        sidebarDev.setSelected(selectedSection == 1);
        addRenderableWidget(sidebarDev);

        sidebarConfigIo = new TitleScreenTextButton(
                sidebarX, sidebarY + 60, SIDEBAR_WIDTH, 22,
                Component.translatable("screen.youzaiworldcore.settings.sidebar_config_io"),
                () -> { selectedSection = 2; rebuildWidgets(); }
        );
        sidebarConfigIo.setSelected(selectedSection == 2);
        sidebarConfigIo.active = !configOpActive;
        addRenderableWidget(sidebarConfigIo);

        // ===== 右侧设置内容 =====
        buildContentWidgets();
    }

    /**
     * 将当前开发者模式下自定义更新地址推送到共享状态，供内嵌（集成）服务端使用。
     * 仅开发者模式启用时，自定义基址才对外生效；否则推送空串（使用系统默认）。
     */
    private void pushUpdateState() {
        boolean dev = ClientExternalSettings.isDevModeEnabled();
        UpdateAddressState.pushClientState(
                dev,
                dev ? updateCheckAddress : "",
                dev ? updateJumpAddress : ""
        );
    }

    /**
     * 构建「导出/导入配置」分栏（selectedSection == 2）。
     * <p>
     * 包含导出按钮（PC 弹文件选择器 / Android 自动保存至 config_backups/）与导入按钮。
     * 操作进行中时按钮文案变为进度文本，操作锁定期间禁侧栏切换。
     * </p>
     */
    private void buildConfigIoSection(int baseX, int baseY) {
        this.isAndroidPlatform = PlatformDetector.isAndroid();

        int y = baseY + 30;
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

        maxContentY = y + 40;
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

        if (selectedSection == 0) {
            // 实验性功能 — 无交互组件，纯文本
            maxContentY = baseY + 40;
        } else if (selectedSection == 2) {
            // 导出/导入配置分栏
            buildConfigIoSection(baseX, baseY);
        } else if (selectedSection == 1) {
            int y = baseY + 30;

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
                        // 开发者模式变更影响更新地址是否生效，推送到共享状态
                        pushUpdateState();
                        rebuildWidgets();
                    }
            );
            addRenderableWidget(devModeToggle);
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

                // ===== 更新服务器区域（位于调试服务器区域下方） =====
                // 专用模式：从调试端口输入框底部向下间隔；内嵌模式：紧接调试方式下拉框（跳过调试服务器→无空白）
                y += (isDedicated ? 12 : 4);
                updateSectionLabelY = y;
                y += 12;

                // 检查更新地址 输入框 + 标签
                updateCheckLabelY = y;
                y += 10;
                updateCheckInput = new EditBox(
                        this.font, baseX, y, CONTENT_WIDTH, 20,
                        Component.translatable("screen.youzaiworldcore.settings.label_update_check_address")
                );
                updateCheckInput.setValue(updateCheckAddress);
                updateCheckInput.setResponder(s -> {
                    updateCheckAddress = s;
                    ClientExternalSettings.setUpdateCheckAddress(s);
                    pushUpdateState();
                });
                addRenderableWidget(updateCheckInput);
                y += 26;

                // 跳转地址 输入框 + 标签
                updateJumpLabelY = y;
                y += 10;
                updateJumpInput = new EditBox(
                        this.font, baseX, y, CONTENT_WIDTH, 20,
                        Component.translatable("screen.youzaiworldcore.settings.label_update_jump_address")
                );
                updateJumpInput.setValue(updateJumpAddress);
                updateJumpInput.setResponder(s -> {
                    updateJumpAddress = s;
                    ClientExternalSettings.setUpdateJumpAddress(s);
                    pushUpdateState();
                });
                addRenderableWidget(updateJumpInput);
            } else {
                logLevelDropdown = null;
                debugModeDropdown = null;
                debugAddressInput = null;
                debugPortInput = null;
                updateCheckInput = null;
                updateJumpInput = null;
            }

            // 追踪实际内容底部 Y（最后一个输入框底部 + 余量）
            maxContentY = y + 26;
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
        sidebarExpFeatures.extractRenderState(guiGraphics, mouseX, mouseY, partialTick);
        sidebarDev.extractRenderState(guiGraphics, mouseX, mouseY, partialTick);
        if (sidebarConfigIo != null) {
            sidebarConfigIo.extractRenderState(guiGraphics, mouseX, mouseY, partialTick);
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
        if (selectedSection == 0) {
            guiGraphics.text(this.font, Component.translatable("screen.youzaiworldcore.settings.sidebar_experimental"),
                    baseX, CONTENT_TOP, 0xFFFFFFFF, false);
            guiGraphics.text(this.font, Component.translatable("screen.youzaiworldcore.settings.experimental_empty"),
                    baseX, CONTENT_TOP + 20, 0x80FFFFFF, false);

        } else if (selectedSection == 1) {
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

                // 更新服务器区域标签
                guiGraphics.text(this.font, Component.translatable("screen.youzaiworldcore.settings.label_update_server_section"),
                        baseX, updateSectionLabelY, 0xFFFFCC88, false);
                guiGraphics.text(this.font, Component.translatable("screen.youzaiworldcore.settings.label_update_check_address"),
                        baseX, updateCheckLabelY, 0xB0FFFFFF, false);
                guiGraphics.text(this.font, Component.translatable("screen.youzaiworldcore.settings.label_update_jump_address"),
                        baseX, updateJumpLabelY, 0xB0FFFFFF, false);
            }
        } else if (selectedSection == 2) {
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
