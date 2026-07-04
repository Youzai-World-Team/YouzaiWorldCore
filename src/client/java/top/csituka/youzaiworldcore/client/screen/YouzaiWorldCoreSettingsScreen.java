package top.csituka.youzaiworldcore.client.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.Panorama;
import net.minecraft.network.chat.Component;

/**
 * YouzaiWorldCore 设置界面。
 * <p>
 * 背景使用原版滚动全景图（与标题界面一致），
 * 可通过 OptionsScreen 中的「YouzaiWorldCore 设置...」按钮或
 * ModMenu 模组列表页面的「设置」按钮打开。
 */
public class YouzaiWorldCoreSettingsScreen extends Screen {

    /** 全景图实例（原版标题界面的滚动全景） */
    private final Panorama panorama;

    public YouzaiWorldCoreSettingsScreen(Screen parent) {
        super(Component.translatable("options.youzaiworldcore.settings"));
        this.panorama = new Panorama();
    }

    @Override
    protected void init() {
        super.init();
        this.panorama.startSpin();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTick) {
        // 渲染原版全景图背景
        this.panorama.extractRenderState(guiGraphics, this.width, this.height);

        // 叠加半透明黑色遮罩，让文字更清晰
        guiGraphics.fill(0, 0, this.width, this.height, 0x40_00_00_00);

        // 调用父类渲染（按钮、标题等）
        super.extractRenderState(guiGraphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTick) {
        // 背景已在 extractRenderState 中绘制，此处留空避免重复
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
