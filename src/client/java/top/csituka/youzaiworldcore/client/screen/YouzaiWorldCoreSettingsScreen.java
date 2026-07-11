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
import top.csituka.youzaiworldcore.client.screen.widget.DropdownButton;
import top.csituka.youzaiworldcore.client.screen.widget.TitleScreenTextButton;
import top.csituka.youzaiworldcore.client.screen.widget.TransparentButton;

import java.util.List;

/**
 * YouzaiWorldCore 设置界面。
 * <p>
 * 背景使用原版滚动全景图，左侧索引栏 + 右侧设置内容。
 * 可通过 OptionsScreen 中的「YouzaiWorldCore 设置...」按钮或
 * ModMenu 模组列表页面的「设置」按钮打开。
 */
@SuppressWarnings("null")
public class YouzaiWorldCoreSettingsScreen extends Screen {

    private static final int SIDEBAR_WIDTH = 120;
    private static final int CONTENT_LEFT = 160;
    private static final int CONTENT_WIDTH = 320;

    private final Panorama panorama;

    /** 当前选中的分栏索引：0 = 实验性功能, 1 = 开发者 */
    private int selectedSection = 0;

    // ===== 组件引用 =====
    private TransparentButton closeButton;
    private TitleScreenTextButton sidebarExpFeatures;
    private TitleScreenTextButton sidebarDev;
    private CheckboxButton devModeToggle;
    private DropdownButton logLevelDropdown;
    private DropdownButton debugModeDropdown;
    private EditBox debugAddressInput;
    private EditBox debugPortInput;

    // ===== 设置状态（通过 ClientExternalSettings 持久化） =====
    private boolean devModeEnabled;
    private int logLevel; // 0=关闭, 1=基本, 2=详细, 3=调试
    private String debugModeType; // "embedded" 或 "dedicated"
    private String debugAddress;
    private String debugPort;

    // ===== 文本标签 Y 坐标（由 buildContentWidgets 计算，extractRenderState 使用） =====
    /** "调试服务器" 子分栏标题 Y（仅专用服务端时显示） */
    private int debugSectionLabelY;
    private int debugAddrLabelY;
    private int debugPortLabelY;
    /** "重启客户端后生效" 提示文字 Y */
    private int restartHintY;

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
    }

    @Override
    protected void init() {
        super.init();
        this.panorama.startSpin();
        rebuildWidgets();
    }

    @Override
    public void tick() {
        super.tick();
        // 26.2 的 EditBox 没有 tick() 方法，无需手动刷新光标
    }

    @Override
    public boolean keyPressed(KeyEvent keyEvent) {
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

    @Override
    public boolean mouseClicked(MouseButtonEvent mouseButtonEvent, boolean bl) {
        // ===== 点击弹窗外区域时关闭下拉弹窗 =====
        if (debugModeDropdown != null && debugModeDropdown.isOpen()
                && !debugModeDropdown.isPositionInsidePopup(mouseButtonEvent.x(), mouseButtonEvent.y())) {
            debugModeDropdown.closePopup();
        }
        if (logLevelDropdown != null && logLevelDropdown.isOpen()
                && !logLevelDropdown.isPositionInsidePopup(mouseButtonEvent.x(), mouseButtonEvent.y())) {
            logLevelDropdown.closePopup();
        }
        return super.mouseClicked(mouseButtonEvent, bl);
    }

    protected void rebuildWidgets() {
        this.clearWidgets();

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

        // ===== 右侧设置内容 =====
        buildContentWidgets();
    }

    private void buildContentWidgets() {
        int baseX = CONTENT_LEFT + (this.width - CONTENT_LEFT - CONTENT_WIDTH) / 2;
        int baseY = 90;

        if (selectedSection == 0) {
            // 实验性功能 — 无交互组件，纯文本
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
                            boolean show = "dedicated".equals(newType);
                            if (debugAddressInput != null) {
                                debugAddressInput.setVisible(show);
                                debugAddressInput.setFocused(false);
                            }
                            if (debugPortInput != null) {
                                debugPortInput.setVisible(show);
                                debugPortInput.setFocused(false);
                            }
                        },
                        null
                );
                addRenderableWidget(debugModeDropdown);
                y += 26;

                // ===== 专用服务端子分栏（地址/端口输入框，始终创建，按调试模式控制可见性） =====
                y += 4;
                debugSectionLabelY = y;
                y += 12;

                // 地址输入框 + 标签
                debugAddrLabelY = y;
                y += 10;
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
                y += 26;

                // 端口输入框 + 标签
                debugPortLabelY = y;
                y += 10;
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
            } else {
                logLevelDropdown = null;
                debugModeDropdown = null;
                debugAddressInput = null;
                debugPortInput = null;
            }
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTick) {
        // 全景图背景
        this.panorama.extractRenderState(guiGraphics, this.width, this.height);
        // 半透明遮罩
        guiGraphics.fill(0, 0, this.width, this.height, 0x60_00_00_00);

        int cx = this.width / 2;

        // ===== 标题 =====
        var titleText = Component.translatable("screen.youzaiworldcore.settings.title");
        int titleWidth = this.font.width(titleText);
        guiGraphics.text(this.font, titleText, cx - titleWidth / 2, 12, 0xFFFFFFFF, false);

        // ===== 说明文字 =====
        var desc = Component.translatable("screen.youzaiworldcore.settings.desc_line1");
        var desc2 = Component.translatable("screen.youzaiworldcore.settings.desc_line2");
        int descColor = 0xB0FFFFFF;
        guiGraphics.text(this.font, desc, cx - this.font.width(desc) / 2, 40, descColor, false);
        guiGraphics.text(this.font, desc2, cx - this.font.width(desc2) / 2, 52, descColor, false);

        int baseX = CONTENT_LEFT + (this.width - CONTENT_LEFT - CONTENT_WIDTH) / 2;
        int baseY = 90;

        if (selectedSection == 0) {
            // 实验性功能
            guiGraphics.text(this.font, Component.translatable("screen.youzaiworldcore.settings.sidebar_experimental"),
                    baseX, baseY, 0xFFFFFFFF, false);
            guiGraphics.text(this.font, Component.translatable("screen.youzaiworldcore.settings.experimental_empty"),
                    baseX, baseY + 20, 0x80FFFFFF, false);

        } else if (selectedSection == 1) {
            // 开发者
            guiGraphics.text(this.font, Component.translatable("screen.youzaiworldcore.settings.sidebar_developer"),
                    baseX, baseY, 0xFFFFFFFF, false);
            guiGraphics.text(this.font, Component.translatable("screen.youzaiworldcore.settings.dev_warning"),
                    baseX, baseY + 14, 0x80FFFFFF, false);

            if (devModeEnabled) {
                // ===== "重启客户端后生效" 提示文字（日志输出丰富度下拉框下方） =====
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
        }

        // 父类渲染（按钮等）
        super.extractRenderState(guiGraphics, mouseX, mouseY, partialTick);

        // ===== 下拉弹窗后置渲染（含动画，无论是否打开都需持续调用以驱动淡入淡出） =====
        if (debugModeDropdown != null) {
            debugModeDropdown.renderPopup(guiGraphics, mouseX, mouseY, partialTick);
        }
        if (logLevelDropdown != null) {
            logLevelDropdown.renderPopup(guiGraphics, mouseX, mouseY, partialTick);
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
        Minecraft.getInstance().gui.setScreen(null);
    }
}
