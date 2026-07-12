package top.csituka.youzaiworldcore.client.screen.block;

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

    private static final int PANEL_WIDTH = 280;
    private static final int PANEL_HEIGHT = 90;
    private static final int BUTTON_WIDTH = 80;
    private static final int BUTTON_HEIGHT = 20;

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

        int panelX = (this.width - PANEL_WIDTH) / 2;
        int panelY = (this.height - PANEL_HEIGHT) / 2;

        // 命名输入框
        nameInput = new EditBox(this.font,
                panelX + 12, panelY + 30,
                PANEL_WIDTH - 24, 18,
                Component.translatable("screen.youzaiworldcore.teleport_anchor_name.hint"));
        nameInput.setMaxLength(32);
        nameInput.setFocused(true);
        addRenderableWidget(nameInput);

        // 确认按钮 — 使用 TransparentButton
        confirmButton = new TransparentButton(
                panelX + (PANEL_WIDTH / 2) - (BUTTON_WIDTH / 2), panelY + 58,
                BUTTON_WIDTH, BUTTON_HEIGHT,
                Component.translatable("screen.youzaiworldcore.teleport_anchor_name.confirm"),
                this::confirmName);
        confirmButton.setTextColor(0xFFFFFF);
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
        // 半透明背景
        guiGraphics.fill(0, 0, this.width, this.height, 0x80000000);

        // 标题
        var font = net.minecraft.client.Minecraft.getInstance().font;
        String title = this.getTitle().getString();
        int titleWidth = font.width(title);
        guiGraphics.text(font, title, (this.width - titleWidth) / 2,
                (this.height - PANEL_HEIGHT) / 2 + 10, 0xFFFFFFFF, false);

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
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
