package top.csituka.youzaiworldcore.client.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.renderer.Panorama;
import net.minecraft.network.chat.Component;
import top.csituka.youzaiworldcore.client.config.ClientExternalSettings;
import top.csituka.youzaiworldcore.client.screen.widget.CheckboxButton;
import top.csituka.youzaiworldcore.client.screen.widget.TransparentButton;

/**
 * YouzaiWorldCore 设置界面。
 * <p>
 * 背景使用原版滚动全景图，左侧索引栏 + 右侧设置内容。
 * 可通过 OptionsScreen 中的「YouzaiWorldCore 设置...」按钮或
 * ModMenu 模组列表页面的「设置」按钮打开。
 */
public class YouzaiWorldCoreSettingsScreen extends Screen {

    private static final int SIDEBAR_WIDTH = 120;
    private static final int CONTENT_LEFT = 160;
    private static final int CONTENT_WIDTH = 320;

    private final Panorama panorama;

    /** 当前选中的分栏索引：0 = 实验性功能, 1 = 开发者 */
    private int selectedSection = 0;

    // ===== 组件引用 =====
    private TransparentButton closeButton;
    private TransparentButton sidebarExpFeatures;
    private TransparentButton sidebarDev;
    private CheckboxButton devModeToggle;
    private CheckboxButton logToggle;
    private EditBox debugAddressInput;
    private EditBox debugPortInput;

    // ===== 设置状态（通过 ClientExternalSettings 持久化） =====
    private boolean devModeEnabled;
    private boolean logToFile;
    private String debugAddress;
    private String debugPort;

    // ===== 文本标签 Y 坐标（由 buildContentWidgets 计算，extractRenderState 使用） =====
    private int debugSectionLabelY;
    private int debugAddrLabelY;
    private int debugPortLabelY;

    public YouzaiWorldCoreSettingsScreen(Screen parent) {
        super(Component.translatable("options.youzaiworldcore.settings"));
        this.panorama = new Panorama();
        // 从持久化配置读取初始状态
        this.devModeEnabled = ClientExternalSettings.isDevModeEnabled();
        this.logToFile = ClientExternalSettings.isLogToFile();
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
        closeButton.setTextColor(0xFFFFFF);
        closeButton.setTextLeftAligned(true);
        addRenderableWidget(closeButton);

        // ===== 左侧索引栏 =====
        int sidebarX = 20;
        int sidebarY = 90;

        sidebarExpFeatures = new TransparentButton(
                sidebarX, sidebarY, SIDEBAR_WIDTH, 22,
                Component.literal("实验性功能"),
                () -> { selectedSection = 0; rebuildWidgets(); }
        );
        sidebarExpFeatures.setTextLeftAligned(true);
        sidebarExpFeatures.setTextColor(0xFFFFFF);
        sidebarExpFeatures.setBackgroundVisible(selectedSection == 0);
        addRenderableWidget(sidebarExpFeatures);

        sidebarDev = new TransparentButton(
                sidebarX, sidebarY + 30, SIDEBAR_WIDTH, 22,
                Component.literal("开发者"),
                () -> { selectedSection = 1; rebuildWidgets(); }
        );
        sidebarDev.setTextLeftAligned(true);
        sidebarDev.setTextColor(0xFFFFFF);
        sidebarDev.setBackgroundVisible(selectedSection == 1);
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
                    Component.literal("启用开发者模式"),
                    devModeEnabled,
                    () -> {
                        ClientExternalSettings.setDevModeEnabled(!devModeEnabled);
                        devModeEnabled = !devModeEnabled;
                        rebuildWidgets();
                    }
            );
            addRenderableWidget(devModeToggle);
            y += 26;

            if (devModeEnabled) {
                // ===== 开发者模式下才显示的选项 =====

                // 输出日志
                logToggle = new CheckboxButton(
                        baseX, y, CONTENT_WIDTH, 20,
                        Component.literal("输出日志到 latest.log 文件里"),
                        logToFile,
                        () -> {
                            ClientExternalSettings.setLogToFile(!logToFile);
                            logToFile = !logToFile;
                            rebuildWidgets();
                        }
                );
                addRenderableWidget(logToggle);
                y += 26;

                // 调试服务器（子分栏）- 标签文字 + 4px 间距
                y += 4;
                debugSectionLabelY = y;
                y += 12;

                // 地址输入框 + 标签
                debugAddrLabelY = y;
                y += 10;
                debugAddressInput = new EditBox(
                        this.font, baseX, y, CONTENT_WIDTH, 20,
                        Component.literal("地址")
                );
                debugAddressInput.setValue(debugAddress);
                debugAddressInput.setResponder(s -> {
                    debugAddress = s;
                    ClientExternalSettings.setDebugAddress(s);
                });
                addRenderableWidget(debugAddressInput);
                y += 26;

                // 端口输入框 + 标签
                debugPortLabelY = y;
                y += 10;
                debugPortInput = new EditBox(
                        this.font, baseX, y, CONTENT_WIDTH, 20,
                        Component.literal("端口")
                );
                debugPortInput.setValue(debugPort);
                debugPortInput.setResponder(s -> {
                    debugPort = s;
                    ClientExternalSettings.setDebugPort(s);
                });
                addRenderableWidget(debugPortInput);
            } else {
                logToggle = null;
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
        var titleText = Component.translatable("options.youzaiworldcore.settings");
        int titleWidth = this.font.width(titleText);
        guiGraphics.text(this.font, titleText, cx - titleWidth / 2, 12, 0xFFFFFFFF, false);

        // ===== 说明文字 =====
        String desc = "在此处您只能调整客户端设置，其他设置请加入服务器并登入账户后";
        String desc2 = "按下 Shift + F 按键组合打开主菜单，转到 设置 来更改。";
        int descColor = 0xB0FFFFFF;
        guiGraphics.text(this.font, desc, cx - this.font.width(desc) / 2, 40, descColor, false);
        guiGraphics.text(this.font, desc2, cx - this.font.width(desc2) / 2, 52, descColor, false);

        int baseX = CONTENT_LEFT + (this.width - CONTENT_LEFT - CONTENT_WIDTH) / 2;
        int baseY = 90;

        if (selectedSection == 0) {
            // 实验性功能
            guiGraphics.text(this.font, "实验性功能", baseX, baseY, 0xFFFFFFFF, false);
            guiGraphics.text(this.font, "当前版本无可用的实验性功能~", baseX, baseY + 20, 0x80FFFFFF, false);

        } else if (selectedSection == 1) {
            // 开发者
            guiGraphics.text(this.font, "开发者", baseX, baseY, 0xFFFFFFFF, false);
            guiGraphics.text(this.font, "这些设置仅用于开发，默认情况下请不要修改！", baseX, baseY + 14, 0x80FFFFFF, false);

            if (devModeEnabled) {
                // 调试服务器子分栏标题
                guiGraphics.text(this.font, "调试服务器", baseX, debugSectionLabelY, 0xFFFFCC88, false);
                // 地址/端口标签
                guiGraphics.text(this.font, "地址", baseX, debugAddrLabelY, 0xB0FFFFFF, false);
                guiGraphics.text(this.font, "端口", baseX, debugPortLabelY, 0xB0FFFFFF, false);
            }
        }

        // 父类渲染（按钮等）
        super.extractRenderState(guiGraphics, mouseX, mouseY, partialTick);
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
