package top.csituka.youzaiworldcore.client.screen.block;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.MenuAccess;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import top.csituka.youzaiworldcore.network.FlyBeaconActivePayload;
import top.csituka.youzaiworldcore.screen.FlyBeaconMenu;

public class FlyBeaconScreen extends AbstractContainerScreen<FlyBeaconMenu> implements MenuAccess<FlyBeaconMenu> {

    private static final int BACKGROUND_COLOR = 0x80FFFFFF;
    private static final int SLOT_COLOR = 0x40FFFFFF;
    private static final int SLOT_HOVER_COLOR = 0x60FFFFFF;
    private static final int CORNER_RADIUS = 6;
    private static final int SLOT_SIZE = 16;
    private static final int SLOT_SPACING = 2;

    private static final int ENERGY_BAR_X = 26;
    private static final int ENERGY_BAR_Y = 30;
    private static final int ENERGY_BAR_WIDTH = 124;
    private static final int ENERGY_BAR_HEIGHT = 12;
    private static final int ENERGY_BAR_CORNER_RADIUS = 4;

    private static final int FUEL_SLOT_X = 80;
    private static final int FUEL_SLOT_Y = 53;

    private static final int ENERGY_BAR_BG_COLOR = 0x40000000;
    private static final int ENERGY_BAR_BORDER_COLOR = 0xA0FFFFFF;

    private static final int TOGGLE_BUTTON_X = 154;
    private static final int TOGGLE_BUTTON_Y = 30;
    private static final int TOGGLE_BUTTON_SIZE = 12;

    private Button toggleButton;

    public FlyBeaconScreen(FlyBeaconMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
    }

    @Override
    protected void init() {
        super.init();
        this.leftPos = (this.width - this.imageWidth) / 2;
        this.topPos = (this.height - this.imageHeight) / 2;
        
        this.toggleButton = Button.builder(
            Component.literal(""),
            button -> {
                boolean newActive = !this.menu.isActive();
                ClientPlayNetworking.send(new FlyBeaconActivePayload(newActive));
            }
        ).bounds(
            this.leftPos + TOGGLE_BUTTON_X,
            this.topPos + TOGGLE_BUTTON_Y,
            TOGGLE_BUTTON_SIZE,
            TOGGLE_BUTTON_SIZE
        ).build();
        
        this.addRenderableWidget(this.toggleButton);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTick) {
        drawBackground(guiGraphics);
        drawEnergyBar(guiGraphics, mouseX, mouseY);
        drawFuelSlot(guiGraphics, mouseX, mouseY);
        drawPlayerInventory(guiGraphics, mouseX, mouseY);
        drawToggleButton(guiGraphics, mouseX, mouseY);

        super.extractRenderState(guiGraphics, mouseX, mouseY, partialTick);
    }

    private void drawToggleButton(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY) {
        int buttonX = this.leftPos + TOGGLE_BUTTON_X;
        int buttonY = this.topPos + TOGGLE_BUTTON_Y;
        boolean isActive = this.menu.isActive();
        
        int bgColor = isActive ? 0xC04CAF50 : 0x40FFFFFF;
        int borderColor = isActive ? 0xA081C784 : 0xA0FFFFFF;
        
        fillRoundedRect(guiGraphics, buttonX, buttonY, TOGGLE_BUTTON_SIZE, TOGGLE_BUTTON_SIZE, 3, bgColor);
        drawRoundedBorder(guiGraphics, buttonX, buttonY, TOGGLE_BUTTON_SIZE, TOGGLE_BUTTON_SIZE, 3, borderColor);
        
        if (isActive) {
            int checkColor = 0xFFFFFFFF;
            int cx = buttonX + 3;
            int cy = buttonY + 6;
            for (int i = 0; i < 2; i++) {
                guiGraphics.fill(cx + i, cy + i, cx + i + 1, cy + i + 1, checkColor);
            }
            for (int i = 0; i < 4; i++) {
                guiGraphics.fill(cx + 2 + i, cy + 2 - i, cx + 3 + i, cy + 3 - i, checkColor);
            }
        }
    }

    private void drawBackground(GuiGraphicsExtractor guiGraphics) {
        int x = this.leftPos;
        int y = this.topPos;

        fillRoundedRect(guiGraphics, x, y, this.imageWidth, this.imageHeight, CORNER_RADIUS, BACKGROUND_COLOR);
    }

    private void drawEnergyBar(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY) {
        int barX = this.leftPos + ENERGY_BAR_X;
        int barY = this.topPos + ENERGY_BAR_Y;

        fillRoundedRect(guiGraphics, barX, barY, ENERGY_BAR_WIDTH, ENERGY_BAR_HEIGHT, ENERGY_BAR_CORNER_RADIUS, ENERGY_BAR_BG_COLOR);

        float ratio = this.menu.getEnergyRatio();
        if (ratio > 0) {
            int fillWidth = Math.max(1, (int) (ENERGY_BAR_WIDTH * ratio));
            int energyColor = getEnergyColor(ratio);
            fillRoundedRect(guiGraphics, barX, barY, fillWidth, ENERGY_BAR_HEIGHT, ENERGY_BAR_CORNER_RADIUS, energyColor);
        }

        drawRoundedBorder(guiGraphics, barX, barY, ENERGY_BAR_WIDTH, ENERGY_BAR_HEIGHT, ENERGY_BAR_CORNER_RADIUS, ENERGY_BAR_BORDER_COLOR);

        int energy = this.menu.getEnergy();
        int maxEnergy = this.menu.getMaxEnergy();
        String energyText = energy + " / " + maxEnergy;
        int textWidth = this.font.width(energyText);
        int textX = barX + (ENERGY_BAR_WIDTH - textWidth) / 2;
        int textY = barY + (ENERGY_BAR_HEIGHT - 8) / 2;

        guiGraphics.text(this.font, energyText, textX, textY, 0xFFFFFFFF, true);
    }

    private int getEnergyColor(float ratio) {
        if (ratio > 0.6f) {
            return 0xC04CAF50;
        } else if (ratio > 0.3f) {
            return 0xC0FFC107;
        } else {
            return 0xC0F44336;
        }
    }

    private void drawFuelSlot(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY) {
        int slotX = this.leftPos + FUEL_SLOT_X;
        int slotY = this.topPos + FUEL_SLOT_Y;
        int slotEndX = slotX + SLOT_SIZE;
        int slotEndY = slotY + SLOT_SIZE;

        boolean isHovered = mouseX >= slotX && mouseX < slotEndX && mouseY >= slotY && mouseY < slotEndY;
        int color = isHovered ? SLOT_HOVER_COLOR : SLOT_COLOR;

        fillRoundedRect(guiGraphics, slotX, slotY, SLOT_SIZE, SLOT_SIZE, 3, color);
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

    private void drawRoundedBorder(GuiGraphicsExtractor g, int x, int y, int w, int h, int r, int color) {
        for (int i = 0; i < w; i++) {
            boolean inLeftCorner = i < r;
            boolean inRightCorner = i >= w - r;
            boolean skipCorner = false;

            if (inLeftCorner) {
                int dx = r - i - 1;
                if (dx * dx + (r - 1) * (r - 1) >= r * r) skipCorner = true;
            }
            if (inRightCorner) {
                int dx = i - (w - r);
                if (dx * dx + (r - 1) * (r - 1) >= r * r) skipCorner = true;
            }

            if (!skipCorner) {
                g.fill(x + i, y, x + i + 1, y + 1, color);
                g.fill(x + i, y + h - 1, x + i + 1, y + h, color);
            }
        }

        for (int j = 0; j < h; j++) {
            boolean inTopCorner = j < r;
            boolean inBottomCorner = j >= h - r;
            boolean skipCorner = false;

            if (inTopCorner) {
                int dy = r - j - 1;
                if ((r - 1) * (r - 1) + dy * dy >= r * r) skipCorner = true;
            }
            if (inBottomCorner) {
                int dy = j - (h - r);
                if ((r - 1) * (r - 1) + dy * dy >= r * r) skipCorner = true;
            }

            if (!skipCorner) {
                g.fill(x, y + j, x + 1, y + j + 1, color);
                g.fill(x + w - 1, y + j, x + w, y + j + 1, color);
            }
        }
    }
}
