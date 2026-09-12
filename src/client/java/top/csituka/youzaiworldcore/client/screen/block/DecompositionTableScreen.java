package top.csituka.youzaiworldcore.client.screen.block;

import top.csituka.youzaiworldcore.client.render.YzuiTheme;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.NonNull;
import top.csituka.youzaiworldcore.YouzaiworldCore;
import top.csituka.youzaiworldcore.client.render.RoundedRect;
import top.csituka.youzaiworldcore.network.ClientNetworking;
import top.csituka.youzaiworldcore.screen.DecompositionTableMenu;

@SuppressWarnings("null")
public class DecompositionTableScreen extends AbstractContainerScreen<DecompositionTableMenu> {

    private static final Identifier DECOMPOSITION_BUTTON_TEXTURE = Identifier.fromNamespaceAndPath(YouzaiworldCore.MOD_ID, "textures/gui/decomposition_button.png");
    private static int backgroundColor() { return YzuiTheme.surface(); }
    private static int slotColor() { return YzuiTheme.slot(); }
    private static int slotHoverColor() { return YzuiTheme.slotHover(); }
    private static int buttonColor() { return YzuiTheme.primaryContainer(); }
    private static int buttonHoverColor() { return YzuiTheme.surfaceHigh(); }
    private static int buttonDisabledColor() { return YzuiTheme.surfaceHigh(); }
    private static final int CORNER_RADIUS = 6;
    private static final int INPUT_SLOT_X = 49;
    private static final int INPUT_SLOT_Y = 35;
    private static final int OUTPUT_START_X = 107;
    private static final int OUTPUT_START_Y = 17;
    private static final int SLOT_SIZE = 16;
    private static final int SLOT_SPACING = 2;
    private static final int BUTTON_X = 26;
    private static final int BUTTON_Y = 35;
    private static final int BUTTON_SIZE = 16;

    private DecomposeButton decomposeButton;

    public DecompositionTableScreen(DecompositionTableMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
    }

    @Override
    protected void init() {
        super.init();
        this.leftPos = (this.width - this.imageWidth) / 2;
        this.topPos = (this.height - this.imageHeight) / 2;

        decomposeButton = new DecomposeButton(
                this.leftPos + BUTTON_X,
                this.topPos + BUTTON_Y,
                BUTTON_SIZE,
                BUTTON_SIZE,
                this
        );
        this.addRenderableWidget(decomposeButton);
    }

    @Override
    public void extractRenderState(@NonNull GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTick) {
        drawBackground(guiGraphics);

        drawInputSlot(guiGraphics, mouseX, mouseY);

        drawOutputSlots(guiGraphics, mouseX, mouseY);

        drawPlayerInventory(guiGraphics, mouseX, mouseY);

        super.extractRenderState(guiGraphics, mouseX, mouseY, partialTick);
    }

    private void drawBackground(GuiGraphicsExtractor guiGraphics) {
        YzuiTheme.card(guiGraphics, leftPos, topPos, imageWidth, imageHeight);
    }

    boolean canDecompose() {
        ItemStack inputStack = this.menu.getContainer().getItem(0);
        if (inputStack.isEmpty()) {
            return false;
        }

        if (inputStack.isDamageableItem() && inputStack.getDamageValue() > 0) {
            return false;
        }

        for (int i = 1; i <= 9; i++) {
            if (!this.menu.getContainer().getItem(i).isEmpty()) {
                return false;
            }
        }

        return true;
    }

    void onDecomposeClick() {
        if (canDecompose()) {
            ClientNetworking.sendDecomposePacket();
        }
    }

    private void drawInputSlot(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY) {
        int slotX = this.leftPos + INPUT_SLOT_X;
        int slotY = this.topPos + INPUT_SLOT_Y;
        int slotEndX = slotX + SLOT_SIZE;
        int slotEndY = slotY + SLOT_SIZE;

        boolean isHovered = mouseX >= slotX && mouseX < slotEndX && mouseY >= slotY && mouseY < slotEndY;
        int color = isHovered ? slotHoverColor() : slotColor();

        fillRoundedRect(guiGraphics, slotX, slotY, SLOT_SIZE, SLOT_SIZE, 3, color);
    }

    private void drawOutputSlots(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY) {
        for (int i = 0; i < 9; i++) {
            int row = i / 3;
            int col = i % 3;
            int slotX = this.leftPos + OUTPUT_START_X + col * (SLOT_SIZE + SLOT_SPACING);
            int slotY = this.topPos + OUTPUT_START_Y + row * (SLOT_SIZE + SLOT_SPACING);
            int slotEndX = slotX + SLOT_SIZE;
            int slotEndY = slotY + SLOT_SIZE;

            boolean isHovered = mouseX >= slotX && mouseX < slotEndX && mouseY >= slotY && mouseY < slotEndY;
            int color = isHovered ? slotHoverColor() : slotColor();

            fillRoundedRect(guiGraphics, slotX, slotY, SLOT_SIZE, SLOT_SIZE, 3, color);
        }
    }

    private void drawPlayerInventory(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY) {
        int invStartX = this.leftPos + 8;
        int invStartY = this.topPos + 84;
        int hotbarStartY = this.topPos + 142;

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                int slotX = invStartX + col * (SLOT_SIZE + SLOT_SPACING);
                int slotY = invStartY + row * (SLOT_SIZE + SLOT_SPACING);
                int slotEndX = slotX + SLOT_SIZE;
                int slotEndY = slotY + SLOT_SIZE;

                boolean isHovered = mouseX >= slotX && mouseX < slotEndX && mouseY >= slotY && mouseY < slotEndY;
                int color = isHovered ? slotHoverColor() : slotColor();

                fillRoundedRect(guiGraphics, slotX, slotY, SLOT_SIZE, SLOT_SIZE, 3, color);
            }
        }

        for (int col = 0; col < 9; col++) {
            int slotX = invStartX + col * (SLOT_SIZE + SLOT_SPACING);
            int slotY = hotbarStartY;
            int slotEndX = slotX + SLOT_SIZE;
            int slotEndY = slotY + SLOT_SIZE;

            boolean isHovered = mouseX >= slotX && mouseX < slotEndX && mouseY >= slotY && mouseY < slotEndY;
            int color = isHovered ? slotHoverColor() : slotColor();

            fillRoundedRect(guiGraphics, slotX, slotY, SLOT_SIZE, SLOT_SIZE, 3, color);
        }
    }

    // 圆角矩形绘制统一走 RoundedRect（行扫描，r=6 时 135 次 fill → 13 次），
    // 点亮的像素集合与原逐像素实现完全一致。
    private void fillRoundedRect(GuiGraphicsExtractor g, int x, int y, int w, int h, int r, int color) {
        RoundedRect.fillOrSquare(g, x, y, w, h, r, color);
    }

    private static class DecomposeButton extends AbstractWidget {

        private final DecompositionTableScreen screen;

        public DecomposeButton(int x, int y, int width, int height, DecompositionTableScreen screen) {
            super(x, y, width, height, Component.empty());
            this.screen = screen;
        }

        @Override
        protected void extractWidgetRenderState(@NonNull GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTick) {
            boolean canDecompose = screen.canDecompose();
            boolean isHovered = this.isHovered();

            int color;
            if (!canDecompose) {
                color = buttonDisabledColor();
            } else if (isHovered) {
                color = buttonHoverColor();
            } else {
                color = buttonColor();
            }

            int x = this.getX();
            int y = this.getY();

            fillRoundedRect(guiGraphics, x, y, this.width, this.height, 3, color);

            guiGraphics.blit(RenderPipelines.GUI_TEXTURED, DECOMPOSITION_BUTTON_TEXTURE, x, y, 0, 0, this.width, this.height, this.width, this.height);
        }

        private void fillRoundedRect(GuiGraphicsExtractor g, int x, int y, int w, int h, int r, int color) {
            RoundedRect.fillOrSquare(g, x, y, w, h, r, color);
        }

        @Override
        public void onClick(@NonNull MouseButtonEvent event, boolean isActuallyClick) {
            if (screen.canDecompose()) {
                screen.onDecomposeClick();
            }
        }

        @Override
        protected void updateWidgetNarration(@NonNull NarrationElementOutput narrationElementOutput) {
        }
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        YzuiTheme.label(graphics, font, title, titleLabelX, titleLabelY,
                imageWidth - titleLabelX - 8, YzuiTheme.primary(), false);
        YzuiTheme.label(graphics, font, playerInventoryTitle, inventoryLabelX, inventoryLabelY,
                imageWidth - inventoryLabelX - 8, YzuiTheme.textMuted(), false);
    }
}
