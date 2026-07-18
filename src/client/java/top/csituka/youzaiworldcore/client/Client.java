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
import top.csituka.youzaiworldcore.client.hud.AdventureLevelHudRenderer;
import top.csituka.youzaiworldcore.client.pickup.AddEntriesHandler;
import top.csituka.youzaiworldcore.client.pickup.DrawEntriesHandler;
import top.csituka.youzaiworldcore.block.entity.ModBlockEntities;
import top.csituka.youzaiworldcore.client.renderer.block.FlyBeaconBlockEntityRenderer;
import top.csituka.youzaiworldcore.client.renderer.block.TeleportAnchorBlockEntityRenderer;
import top.csituka.youzaiworldcore.client.screen.block.DecompositionTableScreen;
import top.csituka.youzaiworldcore.client.screen.block.FlyBeaconScreen;
import top.csituka.youzaiworldcore.client.screen.MenuScreen;
import top.csituka.youzaiworldcore.client.screen.element.MainMenuElements;
import top.csituka.youzaiworldcore.screen.ModMenuTypes;
import top.csituka.youzaiworldcore.util.DebugLogger;
import top.csituka.youzaiworldcore.client.effect.TeleportFovEffect;
import top.csituka.youzaiworldcore.enchlevellangpatch.impl.LangPatchImpl;
import top.csituka.youzaiworldcore.highlightitem.HighlightItemClient;
import top.csituka.youzaiworldcore.command.DoubleDoorsClientCommand;
import top.csituka.youzaiworldcore.command.ExperimentalFeatureClientCommand;
import top.csituka.youzaiworldcore.command.InvisibilityClientCommand;

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
        DebugLogger.info("Client", "注册冒险等级 HUD 渲染...");
        AdventureLevelHudRenderer.register();

        DebugLogger.info("Client", "初始化拾取通知系统...");
        DrawEntriesHandler.INSTANCE.setEnabled(true);
        DebugLogger.info("Client", "拾取通知系统已初始化");

        // 方块实体渲染器注册
        DebugLogger.info("Client", "注册飞行信标方块实体渲染器...");
        BlockEntityRenderers.register(ModBlockEntities.FLY_BEACON, FlyBeaconBlockEntityRenderer::new);
        DebugLogger.info("Client", "注册传送锚点方块实体渲染器...");
        BlockEntityRenderers.register(ModBlockEntities.TELEPORT_ANCHOR, TeleportAnchorBlockEntityRenderer::new);

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

        // LangPatch init
        LangPatchImpl.init();

        // 高亮物品功能初始化（配置加载、键位注册、客户端命令、延迟调度器）
        DebugLogger.info("Client", "初始化高亮物品功能...");
        HighlightItemClient.initialize();

        // 双开门功能客户端命令（解析后转发至服务端数据包）
        DebugLogger.info("Client", "注册双开门客户端命令...");
        DoubleDoorsClientCommand.register();

        // 隐身功能客户端命令（解析后转发至服务端数据包）
        DebugLogger.info("Client", "注册隐身客户端命令...");
        InvisibilityClientCommand.register();

        // 实验性功能客户端命令（解析后转发至服务端数据包）
        DebugLogger.info("Client", "注册实验性功能客户端命令...");
        ExperimentalFeatureClientCommand.register();

        DebugLogger.info("Client", "客户端初始化完成 (devMode=%s, logToFile=%s)",
                top.csituka.youzaiworldcore.YouzaiworldCore.devModeEnabled,
                clientLogToFile);

        DebugLogger.exiting("Client", "onInitializeClient");
    }

    private void onClientTick(Minecraft client) {
        // 消费拾取通知队列（从 Netty 线程捕获的数据在主线程上创建条目）
        AddEntriesHandler.drainQueue();

        // 更新拾取通知条目状态
        DrawEntriesHandler.INSTANCE.tick();

        // 更新传送 FOV 动画（在游戏内且不论是否在 GUI 中都持续更新）
        TeleportFovEffect.tick();

        // 高亮物品功能：键位处理（即使界面打开也需响应，故置于界面早退判断之前）
        HighlightItemClient.onClientTick(client);

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
                    DebugLogger.info("Client", "窗口图标已设置为 jar_icon.png (%sx%s)", width, height);
                }
            }

            MemoryUtil.memFree(buffer);
        } catch (Exception e) {
            DebugLogger.error("Client", "设置窗口图标时出错", e);
        }
    }
}
