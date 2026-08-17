package top.csituka.youzaiworldcore.client.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import top.csituka.youzaiworldcore.YouzaiworldCore;
import top.csituka.youzaiworldcore.client.config.ClientExternalSettings;
import top.csituka.youzaiworldcore.client.resource.CustomFontResourcePack;
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

    private static final int PAGE_MARGIN = 12;
    private static final int SIDEBAR_WIDTH = 120;
    private static final int SIDEBAR_GAP = 20;
    private static final int CONTENT_MAX_WIDTH = 320;
    private static final int COMPACT_LAYOUT_WIDTH = 500;
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

    /** 打开本页的上级屏幕，用于 Esc/关闭按钮返回。 */
    private final Screen parentScreen;

    /** 当前选中的分栏索引：0 = 视觉, 1 = 导出/导入配置, 2 = 关于, 3 = 开发者 */
    private int selectedSection = 0;

    // ===== 滚动状态 =====
    /** 当前垂直滚动偏移量（像素） */
    private double scrollOffset = 0.0;
    /** 内容区视口高度 */
    private int viewportHeight = 0;
    /** 内容区底部边界 Y（视口底部，不含 padding） */
    private int contentBottom = 0;
    private int contentTop = 0;
    private int contentLeft = 0;
    private int contentWidth = CONTENT_MAX_WIDTH;
    private int sidebarX = PAGE_MARGIN;
    private int sidebarY = 0;
    private int sectionNavY = 0;
    private int headerDesc1Y = 36;
    private int headerDesc2Y = 48;
    private boolean compactLayout;

    // ===== 组件引用 =====
    private TransparentButton closeButton;
    private TitleScreenTextButton sidebarDev;
    private TitleScreenTextButton sidebarConfigIo;
    private TitleScreenTextButton sidebarVisual;
    private TitleScreenTextButton sidebarAbout;
    private DropdownButton sectionDropdown;
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
    /** 是否显示游戏内左侧背包、装备栏与状态效果列表 */
    private boolean leftHudEnabled;
    /** 是否启用模组内置的自定义字体资源包 */
    private boolean customFontEnabled;

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
        this.parentScreen = parent;
        // 从持久化配置读取初始状态
        this.devModeEnabled = ClientExternalSettings.isDevModeEnabled();
        this.logLevel = ClientExternalSettings.getLogLevel();
        this.debugModeType = ClientExternalSettings.getDebugModeType();
        this.debugAddress = ClientExternalSettings.getDebugAddress();
        this.debugPort = ClientExternalSettings.getDebugPort();
        this.yzuiEnabled = ClientExternalSettings.isYzuiEnabled();
        this.leftHudEnabled = ClientExternalSettings.isLeftHudEnabled();
        this.customFontEnabled = ClientExternalSettings.isCustomFontEnabled();
        this.autoSkipExperimentalWarning = ClientExternalSettings.isAutoSkipExperimentalWarning();
        this.experimentalWarningSkipAction = ClientExternalSettings.getExperimentalWarningSkipAction();
    }

    @Override
    protected void init() {
        super.init();
        // 返回已存在的设置屏幕实例时，重新同步 OOBE 中刚保存的选择。
        this.devModeEnabled = ClientExternalSettings.isDevModeEnabled();
        this.logLevel = ClientExternalSettings.getLogLevel();
        this.debugModeType = ClientExternalSettings.getDebugModeType();
        this.debugAddress = ClientExternalSettings.getDebugAddress();
        this.debugPort = ClientExternalSettings.getDebugPort();
        this.yzuiEnabled = ClientExternalSettings.isYzuiEnabled();
        this.leftHudEnabled = ClientExternalSettings.isLeftHudEnabled();
        this.customFontEnabled = ClientExternalSettings.isCustomFontEnabled();
        this.autoSkipExperimentalWarning = ClientExternalSettings.isAutoSkipExperimentalWarning();
        this.experimentalWarningSkipAction = ClientExternalSettings.getExperimentalWarningSkipAction();
        calculateLayout();
        rebuildWidgets();
    }

    /**
     * 根据逻辑窗口尺寸计算标题、导航和内容区几何。
     * 宽屏使用两列布局，窄屏将分栏导航收拢为顶部下拉框。
     */
    private void calculateLayout() {
        int textWidth = Math.max(1, this.width - PAGE_MARGIN * 2);
        Component desc = Component.translatable("screen.youzaiworldcore.settings.desc_line1");
        Component desc2 = Component.translatable("screen.youzaiworldcore.settings.desc_line2");
        headerDesc1Y = 36;
        headerDesc2Y = headerDesc1Y + wrappedTextHeight(desc, textWidth) + 2;
        int headerBottom = headerDesc2Y + wrappedTextHeight(desc2, textWidth);

        compactLayout = this.width < COMPACT_LAYOUT_WIDTH;
        contentWidth = Math.max(1, Math.min(CONTENT_MAX_WIDTH, this.width - PAGE_MARGIN * 2));
        sectionNavY = headerBottom + 10;

        if (compactLayout) {
            contentLeft = Math.max(PAGE_MARGIN, (this.width - contentWidth) / 2);
            contentTop = sectionNavY + 28;
            sidebarX = contentLeft;
            sidebarY = sectionNavY;
        } else {
            int groupWidth = SIDEBAR_WIDTH + SIDEBAR_GAP + contentWidth + SCROLLBAR_PAD + SCROLLBAR_WIDTH;
            int groupLeft = Math.max(PAGE_MARGIN, (this.width - groupWidth) / 2);
            sidebarX = groupLeft;
            contentLeft = groupLeft + SIDEBAR_WIDTH + SIDEBAR_GAP;
            contentTop = sectionNavY;
            sidebarY = contentTop;
        }

        contentBottom = Math.max(contentTop, this.height - CONTENT_BOTTOM_PAD);
        viewportHeight = Math.max(0, contentBottom - contentTop);
    }

    private int wrappedTextHeight(Component text, int width) {
        return Math.max(1, font.split(text, Math.max(1, width)).size()) * (font.lineHeight + 2) - 2;
    }

    private int getMaxScroll() {
        return Math.max(0, maxContentY - contentBottom);
    }

    private List<String> sectionOptions() {
        return List.of(
                Component.translatable("screen.youzaiworldcore.settings.sidebar_visual").getString(),
                Component.translatable("screen.youzaiworldcore.settings.sidebar_config_io").getString(),
                Component.translatable("screen.youzaiworldcore.settings.sidebar_about").getString(),
                Component.translatable("screen.youzaiworldcore.settings.sidebar_developer").getString()
        );
    }

    @Override
    public void tick() {
        super.tick();
        // 26.2 的 EditBox 没有 tick() 方法，无需手动刷新光标
    }

    // ========== 事件处理（Y 坐标滚动修正） ==========

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int maxScroll = getMaxScroll();
        if (maxScroll <= 0) return false;
        // 仅当鼠标在内容区范围内时才响应滚动
        if (mouseX < contentLeft || mouseX > contentLeft + contentWidth
                || mouseY < contentTop || mouseY > contentBottom) return false;
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
        if (sectionDropdown != null) {
            if (sectionDropdown.isMouseOver(mx, my)) {
                sectionDropdown.onClick(event, bl);
                return true;
            }
            if (sectionDropdown.isOpen()) {
                sectionDropdown.closePopup();
            }
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
        sidebarDev = null;
        sidebarConfigIo = null;
        sidebarVisual = null;
        sidebarAbout = null;
        sectionDropdown = null;
        ossNoticeButton = null;
        devModeToggle = null;
        logLevelDropdown = null;
        debugModeDropdown = null;
        skipActionDropdown = null;
        debugAddressInput = null;
        debugPortInput = null;
        configExportButton = null;
        configImportButton = null;

        // ===== 关闭按钮（右上角） =====
        closeButton = new TransparentButton(
                this.width - PAGE_MARGIN - 14, 8, 14, 14,
                Component.translatable("youzaiworldcore.message.gui.close_button"),
                this::onClose
        );
        closeButton.setBackgroundVisible(false);
        closeButton.setTextColor(0xFFFFFFFF);
        closeButton.setTextLeftAligned(true);
        addRenderableWidget(closeButton);

        if (compactLayout) {
            sectionDropdown = new DropdownButton(
                    contentLeft, sectionNavY, contentWidth, contentWidth, 22,
                    Component.empty(), sectionOptions(), selectedSection, false,
                    index -> {
                        selectedSection = index;
                        rebuildWidgets();
                    },
                    null
            );
            sectionDropdown.active = !configOpActive;
            addRenderableWidget(sectionDropdown);
        } else {
            // ===== 左侧索引栏 =====
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
        }

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

        Component sectionTitle = Component.translatable("screen.youzaiworldcore.settings.sidebar_config_io");
        int y = baseY + wrappedTextHeight(sectionTitle, contentWidth) + 8;
        int horizontalInset = Math.min(20, contentWidth / 8);
        int buttonWidth = Math.max(1, contentWidth - horizontalInset * 2);
        int btnX = baseX + horizontalInset;

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
        String exportHintKey = isAndroidPlatform
                ? "screen.youzaiworldcore.settings.config_io_export_hint_android"
                : "screen.youzaiworldcore.settings.config_io_export_hint_pc";
        y += wrappedTextHeight(Component.translatable(exportHintKey), contentWidth);

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
        y += wrappedTextHeight(
                Component.translatable("screen.youzaiworldcore.settings.config_io_import_hint"), contentWidth);

        // 底部通用提示
        y += 20;
        configBottomHintY = y;
        y += wrappedTextHeight(
                Component.translatable("screen.youzaiworldcore.settings.config_io_bottom_hint_line1"), contentWidth);
        y += 2;
        y += wrappedTextHeight(
                Component.translatable("screen.youzaiworldcore.settings.config_io_bottom_hint_line2"), contentWidth);

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
        if (sidebarConfigIo != null) sidebarConfigIo.active = false;
        if (sectionDropdown != null) sectionDropdown.active = false;

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
        if (sectionDropdown != null) sectionDropdown.active = true;
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
        if (selectedSection == 3) {
            buildDeveloperSection();
        } else if (selectedSection == 1) {
            // 导出/导入配置分栏
            buildConfigIoSection(contentLeft, contentTop);
        } else if (selectedSection == 0) {
            buildVisualSection();
        } else if (selectedSection == 2) {
            buildAboutSection();
        }
    }

    private void buildDeveloperSection() {
        Component title = Component.translatable("screen.youzaiworldcore.settings.sidebar_developer");
        Component warning = Component.translatable("screen.youzaiworldcore.settings.dev_warning");
        int y = contentTop + wrappedTextHeight(title, contentWidth) + 4;
        y += wrappedTextHeight(warning, contentWidth) + 6;

        Component devModeMessage = Component.translatable("screen.youzaiworldcore.settings.checkbox_dev_mode");
        int devModeHeight = checkboxHeight(devModeMessage);
        devModeToggle = new CheckboxButton(
                contentLeft, y, contentWidth, devModeHeight, devModeMessage, devModeEnabled,
                () -> {
                    ClientExternalSettings.setDevModeEnabled(!devModeEnabled);
                    devModeEnabled = !devModeEnabled;
                    top.csituka.youzaiworldcore.YouzaiworldCore.devModeEnabled = devModeEnabled;
                    rebuildWidgets();
                }
        ).setWrapMessage(true);
        addRenderableWidget(devModeToggle);
        y += devModeHeight + 6;

        Component autoSkipMessage = Component.translatable(
                "screen.youzaiworldcore.settings.checkbox_auto_skip_experimental_warning");
        int autoSkipHeight = checkboxHeight(autoSkipMessage);
        CheckboxButton autoSkipExperimentalToggle = new CheckboxButton(
                contentLeft, y, contentWidth, autoSkipHeight, autoSkipMessage, autoSkipExperimentalWarning,
                () -> {
                    boolean newVal = !autoSkipExperimentalWarning;
                    autoSkipExperimentalWarning = newVal;
                    ClientExternalSettings.setAutoSkipExperimentalWarning(newVal);
                    DebugLogger.info("SettingsScreen", "自动跳过实验性设置警告已" + (newVal ? "启用" : "禁用"));
                }
        ).setWrapMessage(true);
        addRenderableWidget(autoSkipExperimentalToggle);
        y += autoSkipHeight + 10;

        int skipActionIndex = "backup".equals(experimentalWarningSkipAction) ? 0 : 1;
        skipActionDropdown = new DropdownButton(
                contentLeft, y, contentWidth, SIDEBAR_WIDTH, 20,
                Component.translatable("screen.youzaiworldcore.settings.dropdown_experimental_skip_action"),
                EXPERIMENTAL_SKIP_ACTION_OPTIONS, skipActionIndex, false,
                idx -> {
                    String newAction = (idx == 0) ? "backup" : "skip";
                    ClientExternalSettings.setExperimentalWarningSkipAction(newAction);
                    experimentalWarningSkipAction = newAction;
                    DebugLogger.info("SettingsScreen", "实验性设置跳过操作已设为：" + newAction);
                },
                null
        );
        addRenderableWidget(skipActionDropdown);
        y += 30;

        TransparentButton rerunOobeButton = new TransparentButton(
                contentLeft, y, contentWidth, 22,
                Component.translatable("screen.youzaiworldcore.settings.rerun_oobe"),
                this::rerunWelcomeGuide
        );
        addRenderableWidget(rerunOobeButton);
        y += 28;

        if (devModeEnabled) {
            logLevelDropdown = new DropdownButton(
                    contentLeft, y, contentWidth, SIDEBAR_WIDTH, 20,
                    Component.translatable("screen.youzaiworldcore.settings.dropdown_log_level"),
                    LOG_LEVEL_OPTIONS, logLevel, false,
                    idx -> {
                        ClientExternalSettings.setLogLevel(idx);
                        logLevel = idx;
                    },
                    null
            );
            addRenderableWidget(logLevelDropdown);
            y += 26;

            restartHintY = y;
            Component restartHint = Component.translatable(
                    "screen.youzaiworldcore.settings.log_level_restart_hint");
            y += wrappedTextHeight(restartHint, contentWidth) + 4;

            boolean isDedicated = "dedicated".equals(debugModeType);
            int debugModeIndex = isDedicated ? 1 : 0;
            debugModeDropdown = new DropdownButton(
                    contentLeft, y, contentWidth, SIDEBAR_WIDTH, 20,
                    Component.translatable("screen.youzaiworldcore.settings.dropdown_debug_mode"),
                    DEBUG_MODE_OPTIONS, debugModeIndex, false,
                    idx -> {
                        String newType = (idx == 0) ? "embedded" : "dedicated";
                        ClientExternalSettings.setDebugModeType(newType);
                        debugModeType = newType;
                        rebuildWidgets();
                    },
                    null
            );
            addRenderableWidget(debugModeDropdown);
            y += 30;

            if (isDedicated) {
                Component debugSection = Component.translatable(
                        "screen.youzaiworldcore.settings.label_debug_section");
                debugSectionLabelY = y;
                y += wrappedTextHeight(debugSection, contentWidth) + 4;

                Component addressLabel = Component.translatable("screen.youzaiworldcore.settings.label_address");
                debugAddrLabelY = y;
                y += wrappedTextHeight(addressLabel, contentWidth) + 2;
                debugAddressInput = new EditBox(
                        this.font, contentLeft, y, contentWidth, 20, addressLabel
                );
                debugAddressInput.setValue(debugAddress);
                debugAddressInput.setResponder(s -> {
                    debugAddress = s;
                    ClientExternalSettings.setDebugAddress(s);
                });
                addRenderableWidget(debugAddressInput);
                y += 26;

                Component portLabel = Component.translatable("screen.youzaiworldcore.settings.label_port");
                debugPortLabelY = y;
                y += wrappedTextHeight(portLabel, contentWidth) + 2;
                debugPortInput = new EditBox(
                        this.font, contentLeft, y, contentWidth, 20, portLabel
                );
                debugPortInput.setValue(debugPort);
                debugPortInput.setResponder(s -> {
                    debugPort = s;
                    ClientExternalSettings.setDebugPort(s);
                });
                addRenderableWidget(debugPortInput);
                y += 26;
            }
        }

        maxContentY = y + 6;
    }

    /** 重置完成标记并从当前设置页重新打开欢迎导览。 */
    private void rerunWelcomeGuide() {
        DebugLogger.info("SettingsScreen", "从开发者页面重新进行 OOBE 流程");
        ClientExternalSettings.resetWelcomeGuide();
        Minecraft.getInstance().gui.setScreen(new WelcomeGuideScreen(this));
    }

    private void buildVisualSection() {
        Component title = Component.translatable("screen.youzaiworldcore.settings.sidebar_visual");
        int y = contentTop + wrappedTextHeight(title, contentWidth) + 8;
        Component toggleMessage = Component.translatable("screen.youzaiworldcore.settings.toggle_yzui");
        int toggleHeight = checkboxHeight(toggleMessage);
        CheckboxButton yzuiToggle = new CheckboxButton(
                contentLeft, y, contentWidth, toggleHeight, toggleMessage, yzuiEnabled,
                () -> {
                    boolean newVal = !yzuiEnabled;
                    yzuiEnabled = newVal;
                    ClientExternalSettings.setYzuiEnabled(newVal);
                    DebugLogger.info("SettingsScreen", "YZUI 已" + (newVal ? "启用" : "禁用"));
                }
        ).setWrapMessage(true);
        addRenderableWidget(yzuiToggle);
        y += toggleHeight + 6;

        Component leftHudToggleMessage =
                Component.translatable("screen.youzaiworldcore.settings.toggle_left_hud");
        int leftHudToggleHeight = checkboxHeight(leftHudToggleMessage);
        CheckboxButton leftHudToggle = new CheckboxButton(
                contentLeft, y, contentWidth, leftHudToggleHeight,
                leftHudToggleMessage, leftHudEnabled,
                () -> {
                    boolean newVal = !leftHudEnabled;
                    leftHudEnabled = newVal;
                    ClientExternalSettings.setLeftHudEnabled(newVal);
                    DebugLogger.info("SettingsScreen", "游戏内左侧 HUD 已"
                            + (newVal ? "启用" : "禁用"));
                }
        ).setWrapMessage(true);
        addRenderableWidget(leftHudToggle);
        y += leftHudToggleHeight + 6;

        Component fontToggleMessage =
                Component.translatable("screen.youzaiworldcore.settings.toggle_custom_font");
        int fontToggleHeight = checkboxHeight(fontToggleMessage);
        CheckboxButton customFontToggle = new CheckboxButton(
                contentLeft, y, contentWidth, fontToggleHeight, fontToggleMessage, customFontEnabled,
                () -> {
                    boolean newVal = !customFontEnabled;
                    customFontEnabled = newVal;
                    ClientExternalSettings.setCustomFontEnabled(newVal);
                    CustomFontResourcePack.setEnabled(newVal);
                    DebugLogger.info("SettingsScreen", "自定义字体资源包已" + (newVal ? "启用" : "禁用"));
                }
        ).setWrapMessage(true);
        addRenderableWidget(customFontToggle);
        maxContentY = y + fontToggleHeight + 6;
    }

    private void buildAboutSection() {
        int buttonY = contentTop + ABOUT_ICON_SIZE + 8;
        ossNoticeButton = new TransparentButton(
                contentLeft, buttonY, ABOUT_ICON_SIZE, 22,
                Component.translatable("screen.youzaiworldcore.settings.about_license_btn_notice"),
                this::openOssNotice
        );
        addRenderableWidget(ossNoticeButton);
        maxContentY = Math.max(buttonY + 22, originalAboutTextEndY()) + 6;
    }

    private int checkboxHeight(Component message) {
        int maxTextWidth = Math.max(1, contentWidth - this.font.width("☑") - 12);
        return Math.max(20, wrappedTextHeight(message, maxTextWidth) + 6);
    }

    private int originalAboutTextEndY() {
        int wrapWidth = Math.max(1, contentWidth - ABOUT_ICON_SIZE - 10);
        int y = contentTop + 30;
        y = nextAboutTextY(y,
                Component.translatable("screen.youzaiworldcore.settings.about_desc_line1"), wrapWidth);
        y = nextAboutTextY(y,
                Component.translatable("screen.youzaiworldcore.settings.about_desc_line2"), wrapWidth);
        y += 4;
        y = nextAboutTextY(y,
                Component.translatable("screen.youzaiworldcore.settings.about_website"), wrapWidth);
        y += 4;
        y = nextAboutTextY(y,
                Component.translatable("screen.youzaiworldcore.settings.about_authors"), wrapWidth);
        y += 4;
        y = nextAboutTextY(y,
                Component.translatable("screen.youzaiworldcore.settings.about_license"), wrapWidth);
        y += 20;
        y = nextAboutTextY(y,
                Component.translatable("screen.youzaiworldcore.settings.about_credit_why"), wrapWidth);
        y += 4;
        y = nextAboutTextY(y,
                Component.translatable("screen.youzaiworldcore.settings.about_credit_byzzdemy"), wrapWidth);
        y += 4;
        y = nextAboutTextY(y,
                Component.translatable("screen.youzaiworldcore.settings.about_credit_zhongend"), wrapWidth);
        y += 4;
        y = nextAboutTextY(y,
                Component.translatable("screen.youzaiworldcore.settings.about_credit_testers"), wrapWidth);
        y += 20;
        return nextAboutTextY(y,
                Component.translatable("screen.youzaiworldcore.settings.about_credit_oss"), wrapWidth);
    }

    private int nextAboutTextY(int y, Component text, int width) {
        return y + wrappedTextHeight(text, width) + 2;
    }

    private void renderAboutContent(GuiGraphicsExtractor guiGraphics) {
        String version = UpdateChecker.getCurrentVersionString();
        int iconX = contentLeft;
        int iconY = contentTop;
        int textX = contentLeft + ABOUT_ICON_SIZE + 10;
        int wrapWidth = Math.max(1, contentWidth - ABOUT_ICON_SIZE - 10);

        guiGraphics.blit(RenderPipelines.GUI_TEXTURED, MOD_ICON_TEXTURE,
                iconX, iconY, 0, 0,
                ABOUT_ICON_SIZE, ABOUT_ICON_SIZE,
                ABOUT_ICON_SIZE, ABOUT_ICON_SIZE);
        clipRoundedCorners(guiGraphics, iconX, iconY,
                ABOUT_ICON_SIZE, ABOUT_ICON_SIZE, ICON_CORNER_RADIUS);

        guiGraphics.pose().pushMatrix();
        guiGraphics.pose().scale(ABOUT_TITLE_SCALE, ABOUT_TITLE_SCALE);
        guiGraphics.text(this.font, Component.translatable("screen.youzaiworldcore.settings.about_title"),
                Math.round(textX / ABOUT_TITLE_SCALE),
                Math.round((float) contentTop / ABOUT_TITLE_SCALE),
                ABOUT_TITLE_COLOR, false);
        guiGraphics.pose().popMatrix();

        guiGraphics.text(this.font, Component.translatable(
                        "screen.youzaiworldcore.settings.about_version", version),
                textX, contentTop + 18, 0xFFFFFFFF, false);

        int y = contentTop + 30;
        y = drawAboutWrappedText(guiGraphics,
                Component.translatable("screen.youzaiworldcore.settings.about_desc_line1"),
                textX, y, wrapWidth, 0xA0FFFFFF, false);
        y = drawAboutWrappedText(guiGraphics,
                Component.translatable("screen.youzaiworldcore.settings.about_desc_line2"),
                textX, y, wrapWidth, 0xA0FFFFFF, false);
        y += 4;
        y = drawAboutWrappedText(guiGraphics,
                Component.translatable("screen.youzaiworldcore.settings.about_website"),
                textX, y, wrapWidth, 0xFFFFFFFF, false);
        y += 4;
        y = drawAboutWrappedText(guiGraphics,
                Component.translatable("screen.youzaiworldcore.settings.about_authors"),
                textX, y, wrapWidth, 0x80FFFFFF, false);
        y += 4;
        y = drawAboutWrappedText(guiGraphics,
                Component.translatable("screen.youzaiworldcore.settings.about_license"),
                textX, y, wrapWidth, 0x80FFFFFF, false);
        y += 20;
        y = drawAboutWrappedText(guiGraphics,
                Component.translatable("screen.youzaiworldcore.settings.about_credit_why"),
                textX, y, wrapWidth, 0x80FFFFFF, false);
        y += 4;
        y = drawAboutWrappedText(guiGraphics,
                Component.translatable("screen.youzaiworldcore.settings.about_credit_byzzdemy"),
                textX, y, wrapWidth, 0x80FFFFFF, false);
        y += 4;
        y = drawAboutWrappedText(guiGraphics,
                Component.translatable("screen.youzaiworldcore.settings.about_credit_zhongend"),
                textX, y, wrapWidth, 0x80FFFFFF, false);
        y += 4;
        y = drawAboutWrappedText(guiGraphics,
                Component.translatable("screen.youzaiworldcore.settings.about_credit_testers"),
                textX, y, wrapWidth, 0x80FFFFFF, false);
        y += 20;
        drawAboutWrappedText(guiGraphics,
                Component.translatable("screen.youzaiworldcore.settings.about_credit_oss"),
                textX, y, wrapWidth, 0xA0FFFFFF, false);
    }

    private int drawAboutWrappedText(GuiGraphicsExtractor guiGraphics, Component text,
                                     int x, int y, int maxWidth, int color, boolean shadow) {
        return drawWrappedText(guiGraphics, text, x, y, maxWidth, color, shadow) + 2;
    }

    private void openOssNotice() {
        DebugLogger.info("SettingsScreen", "打开开源许可页");
        try {
            URI uri = new URI(
                    "https://github.com/Youzai-World-Team/YouzaiWorldCore/blob/main/NOTICE.txt");
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(uri);
            } else {
                DebugLogger.warn("SettingsScreen", "当前环境不支持打开浏览器");
            }
        } catch (Exception e) {
            DebugLogger.exception("SettingsScreen", "openOssNotice", e);
        }
    }

    // ========== 换行文本渲染辅助 ==========

    /** 使用 Minecraft 字体的实际格式化结果渲染换行文本。 */
    private int drawWrappedText(GuiGraphicsExtractor guiGraphics, Component text,
                                int x, int y, int maxWidth, int color, boolean shadow) {
        List<FormattedCharSequence> lines = this.font.split(text, Math.max(1, maxWidth));
        int currentY = y;
        for (FormattedCharSequence line : lines) {
            guiGraphics.text(this.font, line, x, currentY, color, shadow);
            currentY += this.font.lineHeight + 2;
        }
        return y + wrappedTextHeight(text, maxWidth);
    }

    private int drawCenteredWrappedText(GuiGraphicsExtractor guiGraphics, Component text,
                                        int y, int maxWidth, int color, boolean shadow) {
        List<FormattedCharSequence> lines = this.font.split(text, Math.max(1, maxWidth));
        int currentY = y;
        for (FormattedCharSequence line : lines) {
            int lineWidth = this.font.width(line);
            guiGraphics.text(this.font, line, (this.width - lineWidth) / 2, currentY, color, shadow);
            currentY += this.font.lineHeight + 2;
        }
        return y + wrappedTextHeight(text, maxWidth);
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
        guiGraphics.fill(0, 0, this.width, this.height, 0x60_00_00_00);

        int cx = this.width / 2;
        var titleText = Component.translatable("screen.youzaiworldcore.settings.title");
        int titleWidth = this.font.width(titleText);
        guiGraphics.text(this.font, titleText, cx - titleWidth / 2, 12, 0xFFFFFFFF, false);

        var desc = Component.translatable("screen.youzaiworldcore.settings.desc_line1");
        var desc2 = Component.translatable("screen.youzaiworldcore.settings.desc_line2");
        int descColor = 0xB0FFFFFF;
        int headerWidth = Math.max(1, this.width - PAGE_MARGIN * 2);
        drawCenteredWrappedText(guiGraphics, desc, headerDesc1Y, headerWidth, descColor, false);
        drawCenteredWrappedText(guiGraphics, desc2, headerDesc2Y, headerWidth, descColor, false);

        // 固定位置组件必须在滚动裁剪外渲染。
        if (closeButton != null) closeButton.extractRenderState(guiGraphics, mouseX, mouseY, partialTick);
        if (compactLayout) {
            if (sectionDropdown != null) {
                sectionDropdown.extractRenderState(guiGraphics, mouseX, mouseY, partialTick);
            }
        } else {
            if (sidebarDev != null) sidebarDev.extractRenderState(guiGraphics, mouseX, mouseY, partialTick);
            if (sidebarConfigIo != null) {
                sidebarConfigIo.extractRenderState(guiGraphics, mouseX, mouseY, partialTick);
            }
            if (sidebarVisual != null) {
                sidebarVisual.extractRenderState(guiGraphics, mouseX, mouseY, partialTick);
            }
            if (sidebarAbout != null) {
                sidebarAbout.extractRenderState(guiGraphics, mouseX, mouseY, partialTick);
            }
        }

        guiGraphics.enableScissor(contentLeft, contentTop, contentLeft + contentWidth, contentBottom);

        guiGraphics.pose().pushMatrix();
        guiGraphics.pose().translate(0, -(float) scrollOffset);

        if (selectedSection == 3) {
            Component developerTitle = Component.translatable(
                    "screen.youzaiworldcore.settings.sidebar_developer");
            drawWrappedText(guiGraphics, developerTitle,
                    contentLeft, contentTop, contentWidth, 0xFFFFFFFF, false);
            int warningY = contentTop + wrappedTextHeight(developerTitle, contentWidth) + 4;
            drawWrappedText(guiGraphics, Component.translatable("screen.youzaiworldcore.settings.dev_warning"),
                    contentLeft, warningY, contentWidth, 0x80FFFFFF, false);

            if (devModeEnabled) {
                var restartHint = Component.translatable("screen.youzaiworldcore.settings.log_level_restart_hint");
                drawWrappedText(guiGraphics, restartHint,
                        contentLeft, restartHintY, contentWidth, 0x80FFFFFF, false);

                if ("dedicated".equals(debugModeType)) {
                    drawWrappedText(guiGraphics,
                            Component.translatable("screen.youzaiworldcore.settings.label_debug_section"),
                            contentLeft, debugSectionLabelY, contentWidth, 0xFFFFCC88, false);
                    drawWrappedText(guiGraphics,
                            Component.translatable("screen.youzaiworldcore.settings.label_address"),
                            contentLeft, debugAddrLabelY, contentWidth, 0xB0FFFFFF, false);
                    drawWrappedText(guiGraphics,
                            Component.translatable("screen.youzaiworldcore.settings.label_port"),
                            contentLeft, debugPortLabelY, contentWidth, 0xB0FFFFFF, false);
                }
            }
        } else if (selectedSection == 1) {
            drawWrappedText(guiGraphics,
                    Component.translatable("screen.youzaiworldcore.settings.sidebar_config_io"),
                    contentLeft, contentTop, contentWidth, 0xFFFFFFFF, false);

            String exportHintKey = isAndroidPlatform
                    ? "screen.youzaiworldcore.settings.config_io_export_hint_android"
                    : "screen.youzaiworldcore.settings.config_io_export_hint_pc";
            drawWrappedText(guiGraphics, Component.translatable(exportHintKey),
                    contentLeft, configExportHintY, contentWidth, 0x80FFFFFF, false);

            drawWrappedText(guiGraphics,
                    Component.translatable("screen.youzaiworldcore.settings.config_io_import_hint"),
                    contentLeft, configImportHintY, contentWidth, 0x80FFFFFF, false);

            Component bottomLine1 = Component.translatable(
                    "screen.youzaiworldcore.settings.config_io_bottom_hint_line1");
            drawWrappedText(guiGraphics, bottomLine1,
                    contentLeft, configBottomHintY, contentWidth, 0x60FFFFFF, false);
            int bottomLine2Y = configBottomHintY + wrappedTextHeight(bottomLine1, contentWidth) + 2;
            drawWrappedText(guiGraphics,
                    Component.translatable("screen.youzaiworldcore.settings.config_io_bottom_hint_line2"),
                    contentLeft, bottomLine2Y, contentWidth, 0x60FFFFFF, false);
        } else if (selectedSection == 0) {
            drawWrappedText(guiGraphics,
                    Component.translatable("screen.youzaiworldcore.settings.sidebar_visual"),
                    contentLeft, contentTop, contentWidth, 0xFFFFFFFF, false);
        } else if (selectedSection == 2) {
            renderAboutContent(guiGraphics);
        }

        super.extractRenderState(guiGraphics, mouseX, (int) (mouseY + scrollOffset), partialTick);
        guiGraphics.disableScissor();

        if (debugModeDropdown != null) {
            debugModeDropdown.renderPopup(guiGraphics, mouseX, (int) (mouseY + scrollOffset), partialTick,
                    contentTop, contentBottom, scrollOffset);
        }
        if (logLevelDropdown != null) {
            logLevelDropdown.renderPopup(guiGraphics, mouseX, (int) (mouseY + scrollOffset), partialTick,
                    contentTop, contentBottom, scrollOffset);
        }
        if (skipActionDropdown != null) {
            skipActionDropdown.renderPopup(guiGraphics, mouseX, (int) (mouseY + scrollOffset), partialTick,
                    contentTop, contentBottom, scrollOffset);
        }

        guiGraphics.pose().popMatrix();

        if (sectionDropdown != null) {
            sectionDropdown.renderPopup(guiGraphics, mouseX, mouseY, partialTick,
                    0, this.height, 0.0);
        }

        int maxScroll = getMaxScroll();
        if (maxScroll > 0) {
            int scrollbarLeft = contentLeft + contentWidth + SCROLLBAR_PAD;
            int scrollbarRight = scrollbarLeft + SCROLLBAR_WIDTH;
            int scrollbarTop = contentTop;
            int scrollbarBottom = contentBottom;

            guiGraphics.fill(scrollbarLeft, scrollbarTop, scrollbarRight, scrollbarBottom, 0x30FFFFFF);

            int contentHeight = Math.max(1, maxContentY - contentTop);
            double ratio = Math.min(1.0, (double) viewportHeight / contentHeight);
            int thumbHeight = Math.min(viewportHeight, Math.max(12, (int) (ratio * viewportHeight)));
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
        // 复用原版 Screen 背景管线：使用 GameRenderer 的全局全景图实例，
        // 同时保留主菜单子页面应有的模糊效果与菜单背景遮罩。
        super.extractBackground(guiGraphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return true;
    }

    @Override
    public void onClose() {
        if (configOpActive) return; // 操作进行中不可关闭
        DebugLogger.info("SettingsScreen", "返回上级屏幕: %s",
                parentScreen == null ? "默认屏幕" : parentScreen.getClass().getSimpleName());
        Minecraft.getInstance().gui.setScreen(parentScreen);
    }
}
