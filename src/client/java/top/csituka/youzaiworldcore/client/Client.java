package top.csituka.youzaiworldcore.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.MenuScreens;
import org.lwjgl.glfw.GLFW;
import top.csituka.youzaiworldcore.client.screen.block.DecompositionTableScreen;
import top.csituka.youzaiworldcore.client.screen.block.FlyBeaconScreen;
import top.csituka.youzaiworldcore.client.screen.MenuScreen;
import top.csituka.youzaiworldcore.client.screen.element.MainMenuElements;
import top.csituka.youzaiworldcore.screen.ModMenuTypes;

public class Client implements ClientModInitializer {

    private static boolean wasPressed = false;

    @Override
    public void onInitializeClient() {
        ClientTickEvents.END_CLIENT_TICK.register(this::onClientTick);
        
        MenuScreens.register(ModMenuTypes.DECOMPOSITION_TABLE, DecompositionTableScreen::new);
        MenuScreens.register(ModMenuTypes.FLY_BEACON, FlyBeaconScreen::new);
    }

    private void onClientTick(Minecraft client) {
        if (client.player == null || client.screen != null) {
            return;
        }

        var window = client.getWindow();
        boolean isShiftPressed = InputConstants.isKeyDown(window, GLFW.GLFW_KEY_LEFT_SHIFT)
                || InputConstants.isKeyDown(window, GLFW.GLFW_KEY_RIGHT_SHIFT);
        boolean isFPressed = InputConstants.isKeyDown(window, GLFW.GLFW_KEY_F);
        boolean isPressed = isShiftPressed && isFPressed;

        if (isPressed && !wasPressed) {
            client.setScreen(new MenuScreen(new MainMenuElements()));
        }
        
        wasPressed = isPressed;
    }
}
