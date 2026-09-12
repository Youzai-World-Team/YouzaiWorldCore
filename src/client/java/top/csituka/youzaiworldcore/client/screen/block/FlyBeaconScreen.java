package top.csituka.youzaiworldcore.client.screen.block;

import top.csituka.youzaiworldcore.client.render.YzuiTheme;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import org.jspecify.annotations.NonNull;
import top.csituka.youzaiworldcore.client.render.RoundedRect;
import top.csituka.youzaiworldcore.client.screen.widget.ToggleButton;
import top.csituka.youzaiworldcore.network.FlyBeaconActivePayload;
import top.csituka.youzaiworldcore.screen.FlyBeaconMenu;

public class FlyBeaconScreen extends AbstractContainerScreen<FlyBeaconMenu> {

    private static int backgroundColor() { return YzuiTheme.surface(); }
    private static int slotColor() { return YzuiTheme.slot(); }
    private static int slotHoverColor() { return YzuiTheme.slotHover(); }
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

    private static int energyBarBgColor() { return YzuiTheme.surface(); }
    private static int energyBarBorderColor() { return YzuiTheme.outlineVariant(); }

    private static final int TOGGLE_BUTTON_X = 154;
    private static final int TOGGLE_BUTTON_Y = 30;
    private static final int TOGGLE_BUTTON_SIZE = 12;

    private ToggleButton toggleButton;

    public FlyBeaconScreen(FlyBeaconMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
    }

    @Override
    protected void init() {
        super.init();
        this.leftPos = (this.width - this.imageWidth) / 2;
        this.topPos = (this.height - this.imageHeight) / 2;

        this.toggleButton = new ToggleButton(
                this.leftPos + TOGGLE_BUTTON_X,
                this.topPos + TOGGLE_BUTTON_Y,
                TOGGLE_BUTTON_SIZE,
                () -> {
                    boolean newActive = !this.menu.isActive();
                    ClientPlayNetworking.send(new FlyBeaconActivePayload(newActive));
                }
        );
        this.toggleButton.setToggled(this.menu.isActive());

        this.addRenderableWidget(this.toggleButton);
    }

    @Override
    public void extractRenderState(@NonNull GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTick) {
        drawBackground(guiGraphics);
        drawEnergyBar(guiGraphics, mouseX, mouseY);
        drawFuelSlot(guiGraphics, mouseX, mouseY);
        drawPlayerInventory(guiGraphics, mouseX, mouseY);

        this.toggleButton.setToggled(this.menu.isActive());

        super.extractRenderState(guiGraphics, mouseX, mouseY, partialTick);
    }

    private void drawBackground(GuiGraphicsExtractor guiGraphics) {
        YzuiTheme.card(guiGraphics, leftPos, topPos, imageWidth, imageHeight);
    }

    private void drawEnergyBar(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY) {
        int barX = this.leftPos + ENERGY_BAR_X;
        int barY = this.topPos + ENERGY_BAR_Y;

        fillRoundedRect(guiGraphics, barX, barY, ENERGY_BAR_WIDTH, ENERGY_BAR_HEIGHT, ENERGY_BAR_CORNER_RADIUS, energyBarBgColor());

        float ratio = this.menu.getEnergyRatio();
        if (ratio > 0) {
            int fillWidth = Math.max(1, (int) (ENERGY_BAR_WIDTH * ratio));
            int energyColor = getEnergyColor(ratio);
            int fillRadius = Math.min(ENERGY_BAR_CORNER_RADIUS, fillWidth / 2);
            fillRoundedRect(guiGraphics, barX, barY, fillWidth, ENERGY_BAR_HEIGHT, fillRadius, energyColor);
        }

        drawRoundedBorder(guiGraphics, barX, barY, ENERGY_BAR_WIDTH, ENERGY_BAR_HEIGHT, ENERGY_BAR_CORNER_RADIUS, energyBarBorderColor());

        int energy = this.menu.getEnergy();
        int maxEnergy = this.menu.getMaxEnergy();
        String energyText = energy + " / " + maxEnergy;
        int textWidth = this.font.width(energyText);
        int textX = barX + (ENERGY_BAR_WIDTH - textWidth) / 2;
        int textY = barY + (ENERGY_BAR_HEIGHT - 8) / 2;

        guiGraphics.text(this.font, energyText, textX, textY, YzuiTheme.text(), false);
    }

    private int getEnergyColor(float ratio) {
        if (ratio > 0.6f) {
            return YzuiTheme.primaryContainer();
        } else if (ratio > 0.3f) {
            return YzuiTheme.secondaryContainer();
        } else {
            return YzuiTheme.errorContainer();
        }
    }

    private void drawFuelSlot(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY) {
        int slotX = this.leftPos + FUEL_SLOT_X;
        int slotY = this.topPos + FUEL_SLOT_Y;
        int slotEndX = slotX + SLOT_SIZE;
        int slotEndY = slotY + SLOT_SIZE;

        boolean isHovered = mouseX >= slotX && mouseX < slotEndX && mouseY >= slotY && mouseY < slotEndY;
        int color = isHovered ? slotHoverColor() : slotColor();

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

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        YzuiTheme.label(graphics, font, title, titleLabelX, titleLabelY,
                imageWidth - titleLabelX - 8, YzuiTheme.primary(), false);
        YzuiTheme.label(graphics, font, playerInventoryTitle, inventoryLabelX, inventoryLabelY,
                imageWidth - inventoryLabelX - 8, YzuiTheme.textMuted(), false);
    }
}
