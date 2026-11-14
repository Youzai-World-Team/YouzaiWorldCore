package top.csituka.youzaiworldcore.client.screen;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.GrindstoneScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.GrindstoneMenu;
import org.jspecify.annotations.NonNull;
import top.csituka.youzaiworldcore.client.render.YzuiWorkstationStyle;
import top.csituka.youzaiworldcore.util.DebugLogger;

/** YZUI 砂轮界面，继承原版以保留维修、去附魔、结果计算及错误提示。 */
@SuppressWarnings("null")
public class YzuGrindstoneScreen extends GrindstoneScreen implements YzuiWorkstationScreen {
    private static final YzuiWorkstationStyle STYLE = YzuiWorkstationStyle.GRINDSTONE;

    public YzuGrindstoneScreen(GrindstoneMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    @Override
    protected void init() {
        super.init();
        addRenderableWidget(YzuiWorkstationStyle.closeButton(leftPos, topPos, imageWidth, this::onClose));
        DebugLogger.debug("YzuGrindstoneScreen", "初始化砂轮界面：%s", title.getString());
    }

    @Override
    public void extractBackground(@NonNull GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        // 全屏背景由统一入口绘制，动态内容在下方的逻辑视口中提取。
    }

    @Override
    public void extractRenderState(@NonNull GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        STYLE.drawBackground(g, font, title, menu, leftPos, topPos, imageWidth, imageHeight, mouseX, mouseY);
        // 原版预览位于 extractBackground 内；限定的 Mixin 跳过其底板与全屏背景。
        super.extractBackground(g, mouseX, mouseY, partialTick);
        super.extractRenderState(g, mouseX, mouseY, partialTick);
    }

    @Override
    protected void extractLabels(@NonNull GuiGraphicsExtractor g, int mouseX, int mouseY) {
        STYLE.drawInventoryLabel(g, font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, imageWidth);
    }

    @Override
    protected boolean hasClickedOutside(double mouseX, double mouseY, int left, int top) {
        return YzuiWorkstationStyle.hasClickedOutside(mouseX, mouseY, left, top, imageWidth, imageHeight);
    }
}
