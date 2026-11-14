package top.csituka.youzaiworldcore.client.screen;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.BeaconScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.BeaconMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.NonNull;
import top.csituka.youzaiworldcore.client.render.YzuiTheme;
import top.csituka.youzaiworldcore.client.render.YzuiWorkstationStyle;
import top.csituka.youzaiworldcore.util.DebugLogger;

/** YZUI 信标界面，沿用原版效果选择、层级限制、付款确认和取消流程。 */
@SuppressWarnings("null")
public class YzuBeaconScreen extends BeaconScreen implements YzuiWorkstationScreen {
    private static final YzuiWorkstationStyle STYLE = YzuiWorkstationStyle.BEACON;
    private static final Component PRIMARY_LABEL = Component.translatable("block.minecraft.beacon.primary");
    private static final Component SECONDARY_LABEL = Component.translatable("block.minecraft.beacon.secondary");
    private static final ItemStack[] PAYMENT_ICONS = {
            new ItemStack(Items.NETHERITE_INGOT), new ItemStack(Items.EMERALD), new ItemStack(Items.DIAMOND),
            new ItemStack(Items.GOLD_INGOT), new ItemStack(Items.IRON_INGOT)
    };
    private static final int[] PAYMENT_ICON_X = { 20, 41, 63, 86, 108 };

    /** 使用正在交互的原版菜单，付款物品和效果同步仍由原版处理。 */
    public YzuBeaconScreen(BeaconMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    @Override
    protected void init() {
        super.init();
        inventoryLabelX = 36;
        inventoryLabelY = 127;
        addRenderableWidget(YzuiWorkstationStyle.closeButton(leftPos, topPos, imageWidth, this::onClose));
        DebugLogger.debug("YzuBeaconScreen", "初始化信标界面：%s", title.getString());
    }

    @Override
    public void extractBackground(@NonNull GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        // 全屏背景由统一入口绘制；信标的底板和付款示意图在逻辑视口中提取。
    }

    @Override
    public void extractRenderState(@NonNull GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        STYLE.drawBackground(g, font, title, menu, leftPos, topPos, imageWidth, imageHeight, mouseX, mouseY);
        // 原版背景只有静态底板和五种付款物品示意，无需再执行其全屏背景绘制。
        for (int index = 0; index < PAYMENT_ICONS.length; index++) {
            g.item(PAYMENT_ICONS[index], leftPos + PAYMENT_ICON_X[index], topPos + 109);
        }
        super.extractRenderState(g, mouseX, mouseY, partialTick);
    }

    @Override
    protected void extractLabels(@NonNull GuiGraphicsExtractor g, int mouseX, int mouseY) {
        YzuiTheme.label(g, font, PRIMARY_LABEL, 10, 10, 102, YzuiTheme.text(), true);
        YzuiTheme.label(g, font, SECONDARY_LABEL, 124, 10, 96, YzuiTheme.text(), true);
        STYLE.drawInventoryLabel(g, font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, imageWidth);
    }

    @Override
    protected boolean hasClickedOutside(double mouseX, double mouseY, int left, int top) {
        return YzuiWorkstationStyle.hasClickedOutside(mouseX, mouseY, left, top, imageWidth, imageHeight);
    }
}
