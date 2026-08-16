package top.csituka.youzaiworldcore.mixin.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AnvilScreen;
import net.minecraft.client.gui.screens.inventory.BlastFurnaceScreen;
import net.minecraft.client.gui.screens.inventory.BrewingStandScreen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.client.gui.screens.inventory.CraftingScreen;
import net.minecraft.client.gui.screens.inventory.FurnaceScreen;
import net.minecraft.client.gui.screens.inventory.ShulkerBoxScreen;
import net.minecraft.client.gui.screens.inventory.SmithingScreen;
import net.minecraft.client.gui.screens.inventory.SmokerScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.AbstractFurnaceMenu;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.BrewingStandMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.ShulkerBoxMenu;
import net.minecraft.world.inventory.SmithingMenu;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.csituka.youzaiworldcore.client.config.ClientExternalSettings;
import top.csituka.youzaiworldcore.client.screen.YzuAnvilScreen;
import top.csituka.youzaiworldcore.client.screen.YzuBrewingStandScreen;
import top.csituka.youzaiworldcore.client.screen.YzuContainerScreen;
import top.csituka.youzaiworldcore.client.screen.YzuCraftingScreen;
import top.csituka.youzaiworldcore.client.screen.YzuFurnaceScreen;
import top.csituka.youzaiworldcore.client.screen.YzuShulkerBoxScreen;
import top.csituka.youzaiworldcore.client.screen.YzuSmithingScreen;
import top.csituka.youzaiworldcore.util.DebugLogger;

/**
 * 拦截 {@link Gui#setScreen(Screen)}，将原版容器/潜影盒/铁砧/锻造台/工作台/酿造台
 * /熔炉/高炉/烟熏炉屏幕替换为 YZUI 版本。
 * <p>
 * 26.2 中普通箱子、陷阱箱、末影箱、木桶、双格大箱子统一由
 * {@link ContainerScreen}（原 ChestScreen 重命名）承载；潜影盒（含 16 种染色
 * 变体）使用独立的 {@link ShulkerBoxScreen}；铁砧/锻造台继承
 * {@code ItemCombinerScreen}；工作台为配方书屏；酿造台为普通容器屏；
 * 熔炉、高炉、烟熏炉统一继承 {@code AbstractFurnaceScreen}（{@code extends AbstractRecipeBookScreen}），
 * 三者共用 {@code FurnaceRecipeBookComponent}。各分支：
 * <ul>
 * <li>{@code yzuiEnabled == false} → 直接返回，原版屏幕原样通过（回退原版）；</li>
 * <li>{@code yzuiEnabled == true} → 按下表替换，原版屏幕的菜单与标题分别经
 *     {@link net.minecraft.client.gui.screens.inventory.MenuAccess#getMenu()} 与
 *     {@link Screen#getTitle()} 取出：</li>
 * </ul>
 * <table>
 *   <tr><th>原版屏幕</th><th>YZUI 替换</th></tr>
 *   <tr><td>{@link ContainerScreen}</td><td>{@link YzuContainerScreen}</td></tr>
 *   <tr><td>{@link ShulkerBoxScreen}</td><td>{@link YzuShulkerBoxScreen}</td></tr>
 *   <tr><td>{@link AnvilScreen}</td><td>{@link YzuAnvilScreen}</td></tr>
 *   <tr><td>{@link SmithingScreen}</td><td>{@link YzuSmithingScreen}</td></tr>
 *   <tr><td>{@link CraftingScreen}</td><td>{@link YzuCraftingScreen}</td></tr>
 *   <tr><td>{@link BrewingStandScreen}</td><td>{@link YzuBrewingStandScreen}</td></tr>
 *   <tr><td>{@link FurnaceScreen} / {@link BlastFurnaceScreen} / {@link SmokerScreen}</td>
 *       <td>{@link YzuFurnaceScreen}（单一屏幕类，按 {@code menu.getType()} 自动识别）</td></tr>
 * </table>
 * 替换过程通过静态标志 {@code yzwc$containerSwitchingScreen} 防止递归（嵌套 setScreen 调用
 * 仍会触发本方法与 InventoryScreenSwitchMixin，但新屏幕类型不再匹配任一拦截分支）。
 */
@Mixin(Gui.class)
public abstract class YzuContainerScreenSwitchMixin {

    @Unique
    private static final Logger YZWC_CONTAINER_SWITCH_LOGGER = LoggerFactory.getLogger("YzuContainerScreenSwitch");

    @Unique
    private static boolean yzwc$containerSwitchingScreen = false;

    @Inject(method = "setScreen", at = @At("HEAD"), cancellable = true)
    private void yzwc$onSetScreen(Screen newScreen, CallbackInfo ci) {
        if (yzwc$containerSwitchingScreen)
            return;
        if (!ClientExternalSettings.isYzuiEnabled())
            return;
        if (newScreen == null)
            return;
        if (!(newScreen instanceof ContainerScreen || newScreen instanceof ShulkerBoxScreen
                || newScreen instanceof AnvilScreen || newScreen instanceof SmithingScreen
                || newScreen instanceof CraftingScreen || newScreen instanceof BrewingStandScreen
                || newScreen instanceof FurnaceScreen || newScreen instanceof BlastFurnaceScreen
                || newScreen instanceof SmokerScreen))
            return;

        yzwc$containerSwitchingScreen = true;
        try {
            LocalPlayer player = Minecraft.getInstance().player;
            if (player == null)
                return;

            if (newScreen instanceof ContainerScreen vanilla) {
                ChestMenu menu = vanilla.getMenu();
                Component title = vanilla.getTitle();
                YZWC_CONTAINER_SWITCH_LOGGER.debug("→ YzuContainerScreen (rows={}, menuType={}, title={})",
                        menu.getRowCount(), menu.getType(), title.getString());
                Minecraft.getInstance().gui.setScreen(
                        new YzuContainerScreen(menu, player.getInventory(), title));
                ci.cancel();
            } else if (newScreen instanceof ShulkerBoxScreen shulker) {
                ShulkerBoxMenu menu = shulker.getMenu();
                Component title = shulker.getTitle();
                YZWC_CONTAINER_SWITCH_LOGGER.debug("→ YzuShulkerBoxScreen (title={})", title.getString());
                Minecraft.getInstance().gui.setScreen(
                        new YzuShulkerBoxScreen(menu, player.getInventory(), title));
                ci.cancel();
            } else if (newScreen instanceof AnvilScreen anvil) {
                AnvilMenu menu = anvil.getMenu();
                Component title = anvil.getTitle();
                YZWC_CONTAINER_SWITCH_LOGGER.debug("→ YzuAnvilScreen (menuType={}, title={})",
                        menu.getType(), title.getString());
                Minecraft.getInstance().gui.setScreen(
                        new YzuAnvilScreen(menu, player.getInventory(), title));
                ci.cancel();
            } else if (newScreen instanceof SmithingScreen smithing) {
                SmithingMenu menu = smithing.getMenu();
                Component title = smithing.getTitle();
                YZWC_CONTAINER_SWITCH_LOGGER.debug("→ YzuSmithingScreen (menuType={}, title={})",
                        menu.getType(), title.getString());
                Minecraft.getInstance().gui.setScreen(
                        new YzuSmithingScreen(menu, player.getInventory(), title));
                ci.cancel();
            } else if (newScreen instanceof CraftingScreen crafting) {
                CraftingMenu menu = crafting.getMenu();
                Component title = crafting.getTitle();
                YZWC_CONTAINER_SWITCH_LOGGER.debug("→ YzuCraftingScreen (menuType={}, title={})",
                        menu.getType(), title.getString());
                Minecraft.getInstance().gui.setScreen(
                        new YzuCraftingScreen(menu, player.getInventory(), title));
                ci.cancel();
            } else if (newScreen instanceof BrewingStandScreen brewing) {
                BrewingStandMenu menu = brewing.getMenu();
                Component title = brewing.getTitle();
                YZWC_CONTAINER_SWITCH_LOGGER.debug("→ YzuBrewingStandScreen (menuType={}, title={})",
                        menu.getType(), title.getString());
                Minecraft.getInstance().gui.setScreen(
                        new YzuBrewingStandScreen(menu, player.getInventory(), title));
                ci.cancel();
            } else if (newScreen instanceof FurnaceScreen || newScreen instanceof BlastFurnaceScreen
                    || newScreen instanceof SmokerScreen) {
                // 三种 furnace 屏幕统一替换为 YzuFurnaceScreen（通过 menu.getType() 自动识别类型）
                AbstractFurnaceMenu menu;
                Component title;
                String sourceKind;
                if (newScreen instanceof FurnaceScreen furnace) {
                    menu = furnace.getMenu();
                    title = furnace.getTitle();
                    sourceKind = "FurnaceScreen";
                } else if (newScreen instanceof BlastFurnaceScreen blast) {
                    menu = blast.getMenu();
                    title = blast.getTitle();
                    sourceKind = "BlastFurnaceScreen";
                } else {
                    SmokerScreen smoker = (SmokerScreen) newScreen;
                    menu = smoker.getMenu();
                    title = smoker.getTitle();
                    sourceKind = "SmokerScreen";
                }
                YZWC_CONTAINER_SWITCH_LOGGER.debug("→ YzuFurnaceScreen (source={}, menuType={}, title={})",
                        sourceKind, menu.getType(), title.getString());
                Minecraft.getInstance().gui.setScreen(
                        new YzuFurnaceScreen(menu, player.getInventory(), title));
                ci.cancel();
            }
        } catch (Exception e) {
            // 替换失败时保持原版屏幕，不阻断玩家操作
            DebugLogger.exception("YzuContainerScreen", "替换容器/潜影盒/铁砧/锻造台/工作台/酿造台/熔炉/高炉/烟熏炉屏幕失败，回退原版", e);
        } finally {
            yzwc$containerSwitchingScreen = false;
        }
    }
}
