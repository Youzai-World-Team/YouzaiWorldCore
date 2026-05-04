package top.csituka.youzaiworldcore.client.screen;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.MenuAccess;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import top.csituka.youzaiworldcore.network.ClientNetworking;
import top.csituka.youzaiworldcore.screen.DecompositionTableMenu;

public class DecompositionTableScreen extends AbstractContainerScreen<DecompositionTableMenu> implements MenuAccess<DecompositionTableMenu> {

    private static final int BACKGROUND_COLOR = 0x80FFFFFF;
    private static final int SLOT_COLOR = 0x40FFFFFF;
    private static final int SLOT_HOVER_COLOR = 0x60FFFFFF;
    private static final int BUTTON_COLOR = 0x40FFFFFF;
    private static final int BUTTON_HOVER_COLOR = 0x80FFFFFF;
    private static final int BUTTON_DISABLED_COLOR = 0x20FFFFFF;
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
    public void extractRenderState(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTick) {
        drawBackground(guiGraphics);

        drawInputSlot(guiGraphics, mouseX, mouseY);

        drawOutputSlots(guiGraphics, mouseX, mouseY);

        drawPlayerInventory(guiGraphics, mouseX, mouseY);

        super.extractRenderState(guiGraphics, mouseX, mouseY, partialTick);
    }

    private void drawBackground(GuiGraphicsExtractor guiGraphics) {
        int x = this.leftPos;
        int y = this.topPos;

        fillRoundedRect(guiGraphics, x, y, this.imageWidth, this.imageHeight, CORNER_RADIUS, BACKGROUND_COLOR);
    }

    boolean canDecompose() {
        ItemStack inputStack = this.menu.getContainer().getItem(0);
        if (inputStack.isEmpty()) {
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
        int color = isHovered ? SLOT_HOVER_COLOR : SLOT_COLOR;

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
            int color = isHovered ? SLOT_HOVER_COLOR : SLOT_COLOR;

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
                int color = isHovered ? SLOT_HOVER_COLOR : SLOT_COLOR;

                fillRoundedRect(guiGraphics, slotX, slotY, SLOT_SIZE, SLOT_SIZE, 3, color);
            }
        }

        for (int col = 0; col < 9; col++) {
            int slotX = invStartX + col * (SLOT_SIZE + SLOT_SPACING);
            int slotY = hotbarStartY;
            int slotEndX = slotX + SLOT_SIZE;
            int slotEndY = slotY + SLOT_SIZE;

            boolean isHovered = mouseX >= slotX && mouseX < slotEndX && mouseY >= slotY && mouseY < slotEndY;
            int color = isHovered ? SLOT_HOVER_COLOR : SLOT_COLOR;

            fillRoundedRect(guiGraphics, slotX, slotY, SLOT_SIZE, SLOT_SIZE, 3, color);
        }
    }

    private void fillRoundedRect(GuiGraphicsExtractor g, int x, int y, int w, int h, int r, int color) {
        g.fill(x + r, y, x + w - r, y + h, color);
        g.fill(x, y + r, x + r, y + h - r, color);
        g.fill(x + w - r, y + r, x + w, y + h - r, color);

        for (int i = 0; i < r; i++) {
            for (int j = 0; j < r; j++) {
                if (i * i + j * j < r * r) {
                    g.fill(x + r - i - 1, y + r - j - 1, x + r - i, y + r - j, color);
                    g.fill(x + w - r + i, y + r - j - 1, x + w - r + i + 1, y + r - j, color);
                    g.fill(x + r - i - 1, y + h - r + j, x + r - i, y + h - r + j + 1, color);
                    g.fill(x + w - r + i, y + h - r + j, x + w - r + i + 1, y + h - r + j + 1, color);
                }
            }
        }
    }

    private static class DecomposeButton extends AbstractWidget {
        
        private final DecompositionTableScreen screen;
        
        public DecomposeButton(int x, int y, int width, int height, DecompositionTableScreen screen) {
            super(x, y, width, height, Component.empty());
            this.screen = screen;
        }

        @Override
        protected void extractWidgetRenderState(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTick) {
            boolean canDecompose = screen.canDecompose();
            boolean isHovered = this.isHovered();
            
            int color;
            if (!canDecompose) {
                color = BUTTON_DISABLED_COLOR;
            } else if (isHovered) {
                color = BUTTON_HOVER_COLOR;
            } else {
                color = BUTTON_COLOR;
            }

            int x = this.getX();
            int y = this.getY();
            
            fillRoundedRect(guiGraphics, x, y, this.width, this.height, 3, color);
            
            drawDecomposeIcon(guiGraphics, x, y, canDecompose);
        }

        private void drawDecomposeIcon(GuiGraphicsExtractor guiGraphics, int x, int y, boolean enabled) {
            int iconColor = enabled ? 0xFFFFFFFF : 0x60FFFFFF;
            int centerX = x + this.width / 2;
            int centerY = y + this.height / 2;
            
            guiGraphics.fill(centerX - 4, centerY - 1, centerX + 5, centerY + 1, iconColor);
            guiGraphics.fill(centerX - 1, centerY - 4, centerX + 1, centerY + 5, iconColor);
            
            guiGraphics.fill(centerX + 2, centerY - 3, centerX + 5, centerY - 2, iconColor);
            guiGraphics.fill(centerX + 2, centerY - 3, centerX + 3, centerY, iconColor);
        }

        private void fillRoundedRect(GuiGraphicsExtractor g, int x, int y, int w, int h, int r, int color) {
            g.fill(x + r, y, x + w - r, y + h, color);
            g.fill(x, y + r, x + r, y + h - r, color);
            g.fill(x + w - r, y + r, x + w, y + h - r, color);

            for (int i = 0; i < r; i++) {
                for (int j = 0; j < r; j++) {
                    if (i * i + j * j < r * r) {
                        g.fill(x + r - i - 1, y + r - j - 1, x + r - i, y + r - j, color);
                        g.fill(x + w - r + i, y + r - j - 1, x + w - r + i + 1, y + r - j, color);
                        g.fill(x + r - i - 1, y + h - r + j, x + r - i, y + h - r + j + 1, color);
                        g.fill(x + w - r + i, y + h - r + j, x + w - r + i + 1, y + h - r + j + 1, color);
                    }
                }
            }
        }

        @Override
        public void onClick(MouseButtonEvent event, boolean isActuallyClick) {
            if (screen.canDecompose()) {
                screen.onDecomposeClick();
            }
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput narrationElementOutput) {
        }
    }
}
