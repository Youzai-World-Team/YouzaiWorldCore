package top.csituka.youzaiworldcore.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityType;
import org.lwjgl.glfw.GLFW;
import top.csituka.youzaiworldcore.client.renderer.entity.ChickenWardenRenderer;
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

        top.csituka.youzaiworldcore.network.ClientNetworking.initialize();

        // 注册监守者鸡自定义渲染器（替换原版 Warden 渲染）
        var wardenHolder = BuiltInRegistries.ENTITY_TYPE.get(Identifier.parse("minecraft:warden")).orElseThrow();
        EntityType<? extends net.minecraft.world.entity.monster.warden.Warden> wardenType = (EntityType<? extends net.minecraft.world.entity.monster.warden.Warden>) wardenHolder.value();
        EntityRendererRegistry.register(wardenType, ChickenWardenRenderer::new);
    }

    private void onClientTick(Minecraft client) {
        if (client.player == null || client.gui.screen() != null) {
            return;
        }

        var window = client.getWindow();
        boolean isShiftPressed = InputConstants.isKeyDown(window, GLFW.GLFW_KEY_LEFT_SHIFT)
                || InputConstants.isKeyDown(window, GLFW.GLFW_KEY_RIGHT_SHIFT);
        boolean isFPressed = InputConstants.isKeyDown(window, GLFW.GLFW_KEY_F);
        boolean isPressed = isShiftPressed && isFPressed;

        if (isPressed && !wasPressed) {
            client.setScreenAndShow(new MenuScreen(new MainMenuElements()));
        }
        
        wasPressed = isPressed;
    }
}
