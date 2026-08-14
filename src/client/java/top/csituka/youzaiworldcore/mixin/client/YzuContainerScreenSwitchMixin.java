package top.csituka.youzaiworldcore.mixin.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.client.gui.screens.inventory.ShulkerBoxScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ShulkerBoxMenu;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.csituka.youzaiworldcore.client.config.ClientExternalSettings;
import top.csituka.youzaiworldcore.client.screen.YzuContainerScreen;
import top.csituka.youzaiworldcore.client.screen.YzuShulkerBoxScreen;
import top.csituka.youzaiworldcore.util.DebugLogger;

/**
 * 拦截 {@link Gui#setScreen(Screen)}，将原版容器/潜影盒屏幕替换为 YZUI 版本。
 * <p>
 * 26.2 中普通箱子、陷阱箱、末影箱、木桶、双格大箱子统一由
 * {@link ContainerScreen}（原 ChestScreen 重命名）承载；潜影盒（含 16 种染色
 * 变体）使用独立的 {@link ShulkerBoxScreen}。两个分支：
 * <ul>
 * <li>{@code yzuiEnabled == false} → 直接返回，原版屏幕原样通过（回退原版）；</li>
 * <li>{@code yzuiEnabled == true} →
 *     {@link ContainerScreen} → {@link YzuContainerScreen}（容器类型由菜单解析）；
 *     {@link ShulkerBoxScreen} → {@link YzuShulkerBoxScreen}。
 *     原版屏幕的菜单与标题分别经
 *     {@link net.minecraft.client.gui.screens.inventory.MenuAccess#getMenu()} 与
 *     {@link Screen#getTitle()} 取出。</li>
 * </ul>
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
        if (!(newScreen instanceof ContainerScreen || newScreen instanceof ShulkerBoxScreen))
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
            }
        } catch (Exception e) {
            // 替换失败时保持原版屏幕，不阻断玩家操作
            DebugLogger.exception("YzuContainerScreen", "替换容器/潜影盒屏幕失败，回退原版", e);
        } finally {
            yzwc$containerSwitchingScreen = false;
        }
    }
}
