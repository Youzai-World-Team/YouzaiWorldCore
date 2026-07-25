package top.csituka.youzaiworldcore.mixin.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.player.LocalPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.csituka.youzaiworldcore.client.config.ClientExternalSettings;
import top.csituka.youzaiworldcore.client.screen.YzuCreativeInventoryScreen;
import top.csituka.youzaiworldcore.client.screen.YzuInventoryScreen;

/**
 * 拦截 {@link Gui#setScreen(Screen)}。
 * <p>
 * 在 {@code yzuiEnabled == true} 时将原版物品栏替换为 YZUI 版本。
 * 两种路径：InventoryScreen → 根据游戏模式分流；CreativeModeInventoryScreen → 创造屏。
 * YZUI 创造屏内部使用 BuiltInRegistries.ITEM 自填充物品，不需外部注入。
 */
@Mixin(Gui.class)
public abstract class InventoryScreenSwitchMixin {

    @Unique
    private static final Logger LOGGER = LoggerFactory.getLogger("ScreenSwitchMixin");

    @Unique
    private static boolean yzwc$switchingScreen = false;

    @Inject(method = "setScreen", at = @At("HEAD"), cancellable = true)
    private void yzwc$onSetScreen(Screen newScreen, CallbackInfo ci) {
        if (yzwc$switchingScreen) return;
        if (!ClientExternalSettings.isYzuiEnabled()) return;
        if (newScreen == null) return;

        // ───── 物品栏 ─────
        if (newScreen instanceof InventoryScreen) {
            yzwc$switchingScreen = true;
            try {
                LocalPlayer player = Minecraft.getInstance().player;
                if (player == null) return;

                if (player.isCreative() || player.isSpectator()) {
                    LOGGER.debug("→ YzuCreativeInventoryScreen (creative)");
                    Minecraft.getInstance().gui.setScreen(new YzuCreativeInventoryScreen(player));
                } else {
                    LOGGER.debug("→ YzuInventoryScreen (survival)");
                    Minecraft.getInstance().gui.setScreen(new YzuInventoryScreen(player));
                }
                ci.cancel();
            } finally {
                yzwc$switchingScreen = false;
            }
            return;
        }

        // ───── 创造模式物品栏（从原版直接打开时） ─────
        if (newScreen instanceof CreativeModeInventoryScreen) {
            yzwc$switchingScreen = true;
            try {
                LocalPlayer player = Minecraft.getInstance().player;
                if (player == null) return;
                LOGGER.debug("→ YzuCreativeInventoryScreen (from CreativeModeInventoryScreen)");
                Minecraft.getInstance().gui.setScreen(new YzuCreativeInventoryScreen(player));
                ci.cancel();
            } finally {
                yzwc$switchingScreen = false;
            }
            return;
        }
    }
}
