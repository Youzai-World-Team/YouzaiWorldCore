package top.csituka.youzaiworldcore.client.screen.block;

import top.csituka.youzaiworldcore.client.render.YzuiTheme;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import top.csituka.youzaiworldcore.client.screen.widget.TransparentButton;
import top.csituka.youzaiworldcore.network.TeleportAnchorActivatePayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

/**
 * 传送锚点命名界面。
 * <p>
 * 玩家激活锚点时弹出，输入自定义名称后确认激活。
 * 若名称为空则使用默认名称。
 */
@SuppressWarnings("null")
public class TeleportAnchorNameScreen extends Screen {

    private static final int PANEL_WIDTH = 400;
    private static final int PANEL_HEIGHT = 220;
    private static final int BUTTON_WIDTH = 128;
    private static final int BUTTON_HEIGHT = 28;

    private final BlockPos anchorPos;
    private final ResourceKey<Level> anchorDim;
    private EditBox nameInput;
    private TransparentButton confirmButton;

    public TeleportAnchorNameScreen(BlockPos anchorPos, ResourceKey<Level> anchorDim) {
        super(Component.translatable("screen.youzaiworldcore.teleport_anchor_name.title"));
        this.anchorPos = anchorPos;
        this.anchorDim = anchorDim;
    }

    @Override
    protected void init() {
        super.init();
        String savedValue = nameInput == null ? "" : nameInput.getValue();

        int panelX = (this.width - PANEL_WIDTH) / 2;
        int panelY = (this.height - PANEL_HEIGHT) / 2;

        // 命名输入框
        nameInput = new EditBox(this.font,
                panelX + 24, panelY + 100,
                PANEL_WIDTH - 48, 26,
                Component.translatable("screen.youzaiworldcore.teleport_anchor_name.hint"));
        nameInput.setMaxLength(32);
        nameInput.setValue(savedValue);
        nameInput.setFocused(true);
        addRenderableWidget(nameInput);

        // 确认按钮 — 使用 TransparentButton
        confirmButton = new TransparentButton(
                panelX + (PANEL_WIDTH / 2) - (BUTTON_WIDTH / 2), panelY + 162,
                BUTTON_WIDTH, BUTTON_HEIGHT,
                Component.translatable("screen.youzaiworldcore.teleport_anchor_name.confirm"),
                this::confirmName);
        confirmButton.setStyle(YzuiTheme.ButtonStyle.FILLED);
        addRenderableWidget(confirmButton);
    }

    private void confirmName() {
        String name = nameInput.getValue().trim();
        if (name.isEmpty()) {
            name = Component.translatable("screen.youzaiworldcore.teleport_anchor_name.default").getString();
        }
        ClientPlayNetworking.send(new TeleportAnchorActivatePayload(anchorPos, anchorDim, name));
        Minecraft.getInstance().setScreenAndShow(null);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTick) {
        int x = (width - PANEL_WIDTH) / 2, y = (height - PANEL_HEIGHT) / 2;
        YzuiTheme.card(guiGraphics, x, y, PANEL_WIDTH, PANEL_HEIGHT);
        YzuiTheme.label(guiGraphics, font, title, x + 24, y + 24, PANEL_WIDTH - 48, YzuiTheme.text(), false);
        YzuiTheme.wrapped(guiGraphics, font, Component.translatable("screen.youzaiworldcore.teleport_anchor_name.hint"),
                x + 24, y + 54, PANEL_WIDTH - 48, 3, YzuiTheme.textMuted());
        super.extractRenderState(guiGraphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent keyEvent) {
        if (nameInput != null && nameInput.isFocused()) {
            if (keyEvent.key() == 257) { // Enter
                confirmName();
                return true;
            }
            if (nameInput.keyPressed(keyEvent)) {
                return true;
            }
        }
        return super.keyPressed(keyEvent);
    }

    @Override
    public boolean charTyped(net.minecraft.client.input.CharacterEvent characterEvent) {
        if (nameInput != null && nameInput.isFocused()) {
            if (nameInput.charTyped(characterEvent)) {
                return true;
            }
        }
        return super.charTyped(characterEvent);
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent mouseButtonEvent, boolean bl) {
        if (nameInput != null) {
            nameInput.setFocused(
                    mouseButtonEvent.x() >= nameInput.getX()
                    && mouseButtonEvent.x() < nameInput.getX() + nameInput.getWidth()
                    && mouseButtonEvent.y() >= nameInput.getY()
                    && mouseButtonEvent.y() < nameInput.getY() + nameInput.getHeight()
            );
        }
        return super.mouseClicked(mouseButtonEvent, bl);
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
