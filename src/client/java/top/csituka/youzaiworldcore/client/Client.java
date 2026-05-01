package top.csituka.youzaiworldcore.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;
import top.csituka.youzaiworldcore.client.screen.TestMenuScreen;

public class Client implements ClientModInitializer {

    private static final String MOD_ID = "youzaiworldcore";

    private static KeyMapping openMenuKey;
    private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(
            Identifier.fromNamespaceAndPath(MOD_ID, "main")
    );

    @Override
    public void onInitializeClient() {
        openMenuKey = KeyMappingHelper.registerKeyMapping(
                new KeyMapping(
                        "key.youzaiworldcore.open_menu",
                        InputConstants.Type.KEYSYM,
                        GLFW.GLFW_KEY_F,
                        CATEGORY
                )
        );

        ClientTickEvents.END_CLIENT_TICK.register(this::onClientTick);
    }

    private void onClientTick(Minecraft client) {
        if (client.player == null || client.screen != null) {
            return;
        }

        while (openMenuKey.consumeClick()) {
            var window = client.getWindow();
            boolean isShiftPressed = InputConstants.isKeyDown(window, GLFW.GLFW_KEY_LEFT_SHIFT)
                    || InputConstants.isKeyDown(window, GLFW.GLFW_KEY_RIGHT_SHIFT);

            if (isShiftPressed) {
                client.setScreen(new TestMenuScreen());
            }
        }
    }
}
