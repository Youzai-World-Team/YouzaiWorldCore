package top.csituka.youzaiworldcore.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.BlockEntityRendererRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.MenuScreens;
import org.lwjgl.glfw.GLFW;
import top.csituka.youzaiworldcore.block.entity.ModBlockEntities;
import top.csituka.youzaiworldcore.client.config.ClientExternalSettings;
import top.csituka.youzaiworldcore.client.renderer.block.FlyBeaconBlockEntityRenderer;
import top.csituka.youzaiworldcore.client.screen.block.DecompositionTableScreen;
import top.csituka.youzaiworldcore.client.screen.block.FlyBeaconScreen;
import top.csituka.youzaiworldcore.client.screen.MenuScreen;
import top.csituka.youzaiworldcore.client.screen.element.MainMenuElements;
import top.csituka.youzaiworldcore.screen.ModMenuTypes;
import top.csituka.youzaiworldcore.util.DebugLogger;

public class Client implements ClientModInitializer {

    private static boolean wasPressed = false;

    @Override
    public void onInitializeClient() {
        DebugLogger.entering("Client", "onInitializeClient");

        DebugLogger.info("Client", "注册客户端 Tick 事件...");
        ClientTickEvents.END_CLIENT_TICK.register(this::onClientTick);

        // 方块实体渲染器注册
        DebugLogger.info("Client", "注册飞行信标方块实体渲染器...");
        BlockEntityRendererRegistry.register(ModBlockEntities.FLY_BEACON, FlyBeaconBlockEntityRenderer::new);

        DebugLogger.info("Client", "注册菜单屏幕...");
        MenuScreens.register(ModMenuTypes.DECOMPOSITION_TABLE, DecompositionTableScreen::new);
        MenuScreens.register(ModMenuTypes.FLY_BEACON, FlyBeaconScreen::new);

        DebugLogger.info("Client", "初始化客户端网络...");
        top.csituka.youzaiworldcore.network.ClientNetworking.initialize();

        // 加载客户端持久化配置
        DebugLogger.info("Client", "加载实验性功能客户端配置...");
        top.csituka.youzaiworldcore.feature.ExperimentalFeatures.loadClientSettings();
        // 加载客户端外部设置
        DebugLogger.info("Client", "加载客户端外部设置...");
        top.csituka.youzaiworldcore.client.config.ClientExternalSettings.load();
        // 单人模式集成服务器：遵照客户端设置覆盖 logToFile 标志
        boolean clientLogToFile = top.csituka.youzaiworldcore.client.config.ClientExternalSettings.isLogToFile();
        top.csituka.youzaiworldcore.YouzaiworldCore.logToFile = clientLogToFile;
        top.csituka.youzaiworldcore.YouzaiworldCore.devModeEnabled =
                top.csituka.youzaiworldcore.client.config.ClientExternalSettings.isDevModeEnabled();
        DebugLogger.info("Client", "客户端初始化完成 (devMode={}, logToFile={})",
                top.csituka.youzaiworldcore.YouzaiworldCore.devModeEnabled,
                clientLogToFile);

        DebugLogger.exiting("Client", "onInitializeClient");
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

        DebugLogger.branch("Client", "检测 Shift+F 快捷键", isPressed);
        if (isPressed && !wasPressed) {
            DebugLogger.info("Client", "Shift+F 触发，打开主菜单");
            client.setScreenAndShow(new MenuScreen(new MainMenuElements()));
        }

        wasPressed = isPressed;
    }
}
