package top.csituka.youzaiworldcore.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.MenuScreens;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWImage;
import org.lwjgl.system.MemoryUtil;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;
import top.csituka.youzaiworldcore.client.hud.ManaHudRenderer;
import top.csituka.youzaiworldcore.block.entity.ModBlockEntities;
import top.csituka.youzaiworldcore.client.renderer.block.FlyBeaconBlockEntityRenderer;
import top.csituka.youzaiworldcore.client.screen.block.DecompositionTableScreen;
import top.csituka.youzaiworldcore.client.screen.block.FlyBeaconScreen;
import top.csituka.youzaiworldcore.client.screen.MenuScreen;
import top.csituka.youzaiworldcore.client.screen.element.MainMenuElements;
import top.csituka.youzaiworldcore.screen.ModMenuTypes;
import top.csituka.youzaiworldcore.util.DebugLogger;

public class Client implements ClientModInitializer {

    private static boolean wasPressed = false;
    private static boolean windowIconSet = false;

    @SuppressWarnings("null")
    @Override
    public void onInitializeClient() {
        DebugLogger.entering("Client", "onInitializeClient");

        DebugLogger.info("Client", "注册客户端 Tick 事件...");
        ClientTickEvents.END_CLIENT_TICK.register(this::onClientTick);

        DebugLogger.info("Client", "注册魔力条 HUD 渲染...");
        ManaHudRenderer.register();

        // 方块实体渲染器注册
        DebugLogger.info("Client", "注册飞行信标方块实体渲染器...");
        BlockEntityRenderers.register(ModBlockEntities.FLY_BEACON, FlyBeaconBlockEntityRenderer::new);

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
        boolean clientLogToFile = top.csituka.youzaiworldcore.client.config.ClientExternalSettings.getLogLevel() > 0;
        top.csituka.youzaiworldcore.YouzaiworldCore.logToFile = clientLogToFile;
        top.csituka.youzaiworldcore.YouzaiworldCore.devModeEnabled =
                top.csituka.youzaiworldcore.client.config.ClientExternalSettings.isDevModeEnabled();
        DebugLogger.info("Client", "客户端初始化完成 (devMode={}, logToFile={})",
                top.csituka.youzaiworldcore.YouzaiworldCore.devModeEnabled,
                clientLogToFile);

        DebugLogger.exiting("Client", "onInitializeClient");
    }

    private void onClientTick(Minecraft client) {
        // 窗口图标设置（仅执行一次）
        if (!windowIconSet) {
            long handle = client.getWindow().handle();
            if (handle != 0) {
                setWindowIcon(handle);
                windowIconSet = true;
            }
        }

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

    /**
     * 使用模组资源中的 jar_icon.png 设置 Minecraft 窗口图标（任务栏和标题栏）。
     */
    private static void setWindowIcon(long windowHandle) {
        try (var stream = Minecraft.class.getClassLoader()
                .getResourceAsStream("assets/youzaiworldcore/jar_icon.png")) {

            if (stream == null) {
                DebugLogger.warn("Client", "窗口图标资源 jar_icon.png 未找到");
                return;
            }

            // 使用纯 Java ImageIO 解码 PNG（避免 STBImage 的 native 崩溃问题）
            BufferedImage image = ImageIO.read(stream);
            int width = image.getWidth();
            int height = image.getHeight();

            // BufferedImage.getRGB 返回 ARGB（高位 A，次高 R，次次高 G，低位 B）
            // GLFW 需要 RGBA，故需转换
            ByteBuffer buffer = MemoryUtil.memAlloc(width * height * 4);
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    int argb = image.getRGB(x, y);
                    buffer.put((byte) ((argb >> 16) & 0xFF)); // R
                    buffer.put((byte) ((argb >> 8) & 0xFF));  // G
                    buffer.put((byte) (argb & 0xFF));          // B
                    buffer.put((byte) ((argb >> 24) & 0xFF));  // A
                }
            }
            buffer.flip();

            try (GLFWImage icon = GLFWImage.malloc()) {
                icon.set(width, height, buffer);
                try (GLFWImage.Buffer iconBuffer = GLFWImage.malloc(1)) {
                    iconBuffer.put(0, icon);
                    GLFW.glfwSetWindowIcon(windowHandle, iconBuffer);
                    DebugLogger.info("Client", "窗口图标已设置为 jar_icon.png ({}x{})", width, height);
                }
            }

            MemoryUtil.memFree(buffer);
        } catch (Exception e) {
            DebugLogger.error("Client", "设置窗口图标时出错", e);
        }
    }
}
