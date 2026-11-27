package top.csituka.youzaiworldcore.client.screen;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.DispenserMenu;
import net.minecraft.world.inventory.HopperMenu;
import org.jspecify.annotations.NonNull;
import top.csituka.youzaiworldcore.client.render.YzuiWorkstationStyle;
import top.csituka.youzaiworldcore.util.DebugLogger;

/** 漏斗、发射器和投掷器共用的 YZUI 容器界面，直接使用原版菜单和槽位。 */
@SuppressWarnings("null")
public final class YzuStorageScreen extends AbstractContainerScreen<AbstractContainerMenu> {
    private final YzuiWorkstationStyle style;

    /** 漏斗保留原版五格布局和面板高度。 */
    public YzuStorageScreen(HopperMenu menu, Inventory inventory, Component title) {
        this(menu, inventory, title, 133, YzuiWorkstationStyle.HOPPER);
    }

    /** 发射器和投掷器共用原版九格菜单，以原版翻译键识别投掷器。 */
    public YzuStorageScreen(DispenserMenu menu, Inventory inventory, Component title) {
        this(menu, inventory, title, 166,
                title.getContents() instanceof TranslatableContents contents && contents.getKey().equals("container.dropper")
                        ? YzuiWorkstationStyle.DROPPER : YzuiWorkstationStyle.DISPENSER);
    }

    private YzuStorageScreen(AbstractContainerMenu menu, Inventory inventory, Component title, int height,
            YzuiWorkstationStyle style) {
        super(menu, inventory, title, 176, height);
        this.style = style;
    }

    @Override
    protected void init() {
        super.init();
        addRenderableWidget(YzuiWorkstationStyle.panelCloseButton(leftPos, topPos, imageWidth, this::onClose));
        DebugLogger.CLIENT.debug("YzuStorageScreen", "初始化容器界面：{}", style);
    }

    @Override
    public void extractBackground(@NonNull GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        // 全屏背景由 YZUI 统一绘制。
    }

    @Override
    public void extractRenderState(@NonNull GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        style.drawBackground(g, font, title, menu, leftPos, topPos, imageWidth, imageHeight, mouseX, mouseY);
        super.extractRenderState(g, mouseX, mouseY, partialTick);
    }

    @Override
    protected void extractLabels(@NonNull GuiGraphicsExtractor g, int mouseX, int mouseY) {
        style.drawInventoryLabel(g, font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, imageWidth);
    }
}
