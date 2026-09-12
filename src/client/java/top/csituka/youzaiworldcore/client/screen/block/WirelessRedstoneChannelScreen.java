package top.csituka.youzaiworldcore.client.screen.block;

import top.csituka.youzaiworldcore.client.render.YzuiTheme;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

import top.csituka.youzaiworldcore.client.screen.widget.TransparentButton;
import top.csituka.youzaiworldcore.network.WirelessRedstoneSetChannelPayload;
import top.csituka.youzaiworldcore.redstone.WirelessRedstoneChannel;
import top.csituka.youzaiworldcore.redstone.WirelessRedstoneNetwork;

/**
 * 无线红石元件的频道设置界面。
 * <p>
 * 由服务端在玩家右键发射器 / 接收器时下发
 * {@link top.csituka.youzaiworldcore.network.WirelessRedstoneOpenChannelPayload}
 * 打开，
 * 输入框会预填元件当前频道 —— 因此「再次右键即为修改」。
 * <p>
 * 输入框按 {@link WirelessRedstoneChannel} 实时裁剪：只保留数字、最多四位。
 * 客户端裁剪只是即时反馈，服务端收到 C2S 包后仍会独立复核频道号是否合法。
 */
@SuppressWarnings("null")
public class WirelessRedstoneChannelScreen extends Screen {

    private static final int PANEL_WIDTH = 400;
    private static final int PANEL_HEIGHT = 220;
    private static final int BUTTON_WIDTH = 128;
    private static final int BUTTON_HEIGHT = 28;

    private final BlockPos componentPos;
    private final int initialChannel;

    private EditBox channelInput;

    /** 防止在 {@code setResponder} 回调里调用 {@code setValue} 造成无限递归。 */
    private boolean clamping;

    public WirelessRedstoneChannelScreen(BlockPos componentPos, int initialChannel, boolean transmitter) {
        super(Component.translatable(transmitter
                ? "screen.youzaiworldcore.wireless_redstone_channel.title.transmitter"
                : "screen.youzaiworldcore.wireless_redstone_channel.title.receiver"));
        this.componentPos = componentPos;
        this.initialChannel = WirelessRedstoneChannel.clamp(initialChannel);
    }

    @Override
    protected void init() {
        super.init();
        String savedValue = channelInput == null ? Integer.toString(initialChannel) : channelInput.getValue();

        int panelX = (this.width - PANEL_WIDTH) / 2;
        int panelY = (this.height - PANEL_HEIGHT) / 2;

        channelInput = new EditBox(this.font,
                panelX + 24, panelY + 100,
                PANEL_WIDTH - 48, 26,
                Component.translatable("screen.youzaiworldcore.wireless_redstone_channel.hint"));
        channelInput.setMaxLength(WirelessRedstoneChannel.MAX_DIGITS);
        channelInput.setResponder(this::onChannelTextChanged);
        channelInput.setValue(savedValue);
        channelInput.moveCursorToEnd(false);
        channelInput.setFocused(true);
        addRenderableWidget(channelInput);

        TransparentButton confirmButton = new TransparentButton(
                panelX + (PANEL_WIDTH / 2) - (BUTTON_WIDTH / 2), panelY + 162,
                BUTTON_WIDTH, BUTTON_HEIGHT,
                Component.translatable("screen.youzaiworldcore.wireless_redstone_channel.confirm"),
                this::confirm);
        confirmButton.setStyle(YzuiTheme.ButtonStyle.FILLED);
        addRenderableWidget(confirmButton);
    }

    /**
     * 输入变化时只保留数字并截到四位，其余字符被直接丢弃。
     *
     * @param value 输入框当前内容
     */
    private void onChannelTextChanged(String value) {
        if (clamping) {
            return;
        }
        String clamped = WirelessRedstoneChannel.clampInput(value);
        if (clamped.equals(value)) {
            return;
        }

        clamping = true;
        try {
            channelInput.setValue(clamped);
            channelInput.moveCursorToEnd(false);
        } finally {
            clamping = false;
        }
    }

    /**
     * 提交频道并关闭界面。
     * <p>
     * 内容为空（玩家把数字全删了）时视为不改动，回落到打开界面时的频道，
     * 避免「误清空 → 悄悄跳到 0 频道」。
     */
    private void confirm() {
        int channel = WirelessRedstoneChannel.parse(channelInput.getValue(), initialChannel);
        ClientPlayNetworking.send(new WirelessRedstoneSetChannelPayload(componentPos, channel));
        Minecraft.getInstance().setScreenAndShow(null);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTick) {
        int x = (width - PANEL_WIDTH) / 2, y = (height - PANEL_HEIGHT) / 2;
        YzuiTheme.card(guiGraphics, x, y, PANEL_WIDTH, PANEL_HEIGHT);
        YzuiTheme.label(guiGraphics, font, title, x + 24, y + 24, PANEL_WIDTH - 48, YzuiTheme.text(), false);
        YzuiTheme.wrapped(guiGraphics, font, Component.translatable("screen.youzaiworldcore.wireless_redstone_channel.rule", WirelessRedstoneChannel.MIN, WirelessRedstoneChannel.MAX, WirelessRedstoneNetwork.RANGE),
                x + 24, y + 54, PANEL_WIDTH - 48, 3, YzuiTheme.textMuted());
        super.extractRenderState(guiGraphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean keyPressed(KeyEvent keyEvent) {
        if (channelInput != null && channelInput.isFocused()) {
            if (keyEvent.key() == 257 || keyEvent.key() == 335) { // Enter / 小键盘 Enter
                confirm();
                return true;
            }
            if (channelInput.keyPressed(keyEvent)) {
                return true;
            }
        }
        return super.keyPressed(keyEvent);
    }

    @Override
    public boolean charTyped(CharacterEvent characterEvent) {
        if (channelInput != null && channelInput.isFocused() && channelInput.charTyped(characterEvent)) {
            return true;
        }
        return super.charTyped(characterEvent);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent mouseButtonEvent, boolean doubleClick) {
        if (channelInput != null) {
            channelInput.setFocused(
                    mouseButtonEvent.x() >= channelInput.getX()
                            && mouseButtonEvent.x() < channelInput.getX() + channelInput.getWidth()
                            && mouseButtonEvent.y() >= channelInput.getY()
                            && mouseButtonEvent.y() < channelInput.getY() + channelInput.getHeight());
        }
        return super.mouseClicked(mouseButtonEvent, doubleClick);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTick) {
        // 背景由共用屏幕入口在内容变换之前绘制，避免重复模糊与叠加遮罩。
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
