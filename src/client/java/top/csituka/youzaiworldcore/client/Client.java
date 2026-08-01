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
import top.csituka.youzaiworldcore.client.resource.ItemModelDefinitionValidator;
import top.csituka.youzaiworldcore.client.screen.block.DecompositionTableScreen;
import top.csituka.youzaiworldcore.client.screen.block.FlyBeaconScreen;
import top.csituka.youzaiworldcore.client.screen.MenuScreen;
import top.csituka.youzaiworldcore.client.screen.element.MainMenuElements;
import top.csituka.youzaiworldcore.client.config.ClientExternalSettings;
import top.csituka.youzaiworldcore.update.UpdateChecker;
import top.csituka.youzaiworldcore.update.UpdateAddressState;
import top.csituka.youzaiworldcore.config.UpdateCheckerConfig;
import top.csituka.youzaiworldcore.client.update.ClientUpdateState;
import top.csituka.youzaiworldcore.screen.ModMenuTypes;
import top.csituka.youzaiworldcore.util.DebugLogger;
import top.csituka.youzaiworldcore.client.effect.TeleportFovEffect;
import top.csituka.youzaiworldcore.enchlevellangpatch.impl.LangPatchImpl;
import top.csituka.youzaiworldcore.highlightitem.HighlightItemClient;
import top.csituka.youzaiworldcore.itemborder.ItemBorderClient;
import top.csituka.youzaiworldcore.anviluses.AnvilUsesClient;
import top.csituka.youzaiworldcore.command.DoubleDoorsClientCommand;
import top.csituka.youzaiworldcore.command.InvisibilityClientCommand;
import top.csituka.youzaiworldcore.command.PetClientCommand;
import top.csituka.youzaiworldcore.client.config.ConfigIOManager;


public class Client implements ClientModInitializer {

    private static boolean wasPressed = false;
    private static boolean windowIconSet = false;

    @SuppressWarnings("null")
    @Override
    public void onInitializeClient() {
        DebugLogger.entering("Client", "onInitializeClient");

        // 配置文件导入崩溃自愈：检测并恢复孤立的 config_bak_* 备份
        DebugLogger.info("Client", "检查上次导入中断后的配置恢复...");
        ConfigIOManager.recoverIfNeeded(Minecraft.getInstance().gameDirectory);

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

        // 物品模型定义自检：资源重载后校验模组物品是否都有 assets/youzaiworldcore/items/*.json
        // （缺失会导致该物品在物品栏/手持时渲染为黑紫丢失材质）
        DebugLogger.info("Client", "注册物品模型定义自检...");
        ItemModelDefinitionValidator.register();

        DebugLogger.info("Client", "注册菜单屏幕...");
        MenuScreens.register(ModMenuTypes.DECOMPOSITION_TABLE, DecompositionTableScreen::new);
        MenuScreens.register(ModMenuTypes.FLY_BEACON, FlyBeaconScreen::new);

        DebugLogger.info("Client", "初始化客户端网络...");
        top.csituka.youzaiworldcore.network.ClientNetworking.initialize();

        // 加载客户端外部设置
        DebugLogger.info("Client", "加载客户端外部设置...");
        top.csituka.youzaiworldcore.client.config.ClientExternalSettings.load();
        // 单人模式集成服务器：遵照客户端设置覆盖 logToFile 标志
        boolean clientLogToFile = top.csituka.youzaiworldcore.client.config.ClientExternalSettings.getLogLevel() > 0;
        top.csituka.youzaiworldcore.YouzaiworldCore.logToFile = clientLogToFile;
        top.csituka.youzaiworldcore.YouzaiworldCore.devModeEnabled =
                top.csituka.youzaiworldcore.client.config.ClientExternalSettings.isDevModeEnabled();

        // 加载更新检查器配置（客户端独立直连 version.json，用于标题界面公告）
        DebugLogger.info("Client", "加载更新检查器配置...");
        UpdateCheckerConfig.load();

        // 客户端（含内嵌服务端）更新地址：开发者模式启用时采用自定义基址，否则使用系统默认
        boolean clientDevMode = ClientExternalSettings.isDevModeEnabled();
        String checkBase = clientDevMode ? ClientExternalSettings.getUpdateCheckAddress() : "";
        String jumpBase = clientDevMode ? ClientExternalSettings.getUpdateJumpAddress() : "";
        // 推送到共享状态，供内嵌（集成）服务端使用
        UpdateAddressState.pushClientState(clientDevMode, checkBase, jumpBase);

        // 客户端启动时异步检查更新（与服务端解耦，标题界面尚未连服，无法依赖 S2C 推送）
        if (UpdateCheckerConfig.isEnabled() && UpdateCheckerConfig.isCheckOnStartupClient()) {
            DebugLogger.info("Client", "启动时异步检查更新...");
            UpdateChecker.checkAsync(checkBase, jumpBase).thenAccept(result -> {
                if (result != null) {
                    ClientUpdateState.set(result);
                    DebugLogger.info("Client", "更新检查完成: updateAvailable=%s, latest=%s, error=%s",
                            result.updateAvailable(), result.latestVersion(), result.errorMessage());
                }
            });
        }

        // LangPatch init
        LangPatchImpl.init();

        // 高亮物品功能初始化（配置加载、键位注册、客户端命令、延迟调度器）
        DebugLogger.info("Client", "初始化高亮物品功能...");
        HighlightItemClient.initialize();

        // 物品边框功能初始化（配置加载）
        DebugLogger.info("Client", "初始化物品边框功能...");
        ItemBorderClient.initialize();

        // 铁砧使用次数显示功能初始化（注册物品悬浮提示回调）
        DebugLogger.info("Client", "初始化铁砧使用次数显示功能...");
        AnvilUsesClient.initialize();

        // 双开门功能客户端命令（解析后转发至服务端数据包）
        DebugLogger.info("Client", "注册双开门客户端命令...");
        DoubleDoorsClientCommand.register();

        // 隐身功能客户端命令（解析后转发至服务端数据包）
        DebugLogger.info("Client", "注册隐身客户端命令...");
        InvisibilityClientCommand.register();

        // 宠物管理命令转发客户端命令（解析后转发至服务端数据包）
        DebugLogger.info("Client", "注册宠物管理命令客户端命令...");
        PetClientCommand.register();

        // 邮件系统客户端命令（解析后转发至服务端数据包）
        DebugLogger.info("Client", "注册邮件系统客户端命令...");
        top.csituka.youzaiworldcore.command.MailClientCommand.register();

        // 服务端 /yzwc 子命令客户端占位镜像：防止服务端专属子命令
        // （event / update / teleport_world / status 等）在客户端解析阶段被
        // Fabric 客户端命令机制拦截（报"错误的命令参数 at position 5"）
        DebugLogger.info("Client", "注册服务端 /yzwc 子命令占位镜像...");
        top.csituka.youzaiworldcore.command.YzwcServerMirrorCommand.register();

        // 老吴贴贴事件：注册内置曲目 SoundEvent + 初始化本地音频池
        DebugLogger.info("Client", "初始化老吴贴贴音频系统...");
        top.csituka.youzaiworldcore.client.laowumeme.LaowuModSounds.init();
        top.csituka.youzaiworldcore.client.laowumeme.LaowuAudioPool.init();

        DebugLogger.info("Client", "客户端初始化完成 (devMode=%s, logToFile=%s)",
                top.csituka.youzaiworldcore.YouzaiworldCore.devModeEnabled,
                clientLogToFile);

        DebugLogger.exiting("Client", "onInitializeClient");
    }

    private void onClientTick(Minecraft client) {
        // AFK 心跳：需在 GUI 打开早退之前发送（任何界面状态下都要维持心跳）
        top.csituka.youzaiworldcore.client.afk.AfkInputTracker.onClientTick(client);

        // 消费拾取通知队列（从 Netty 线程捕获的数据在主线程上创建条目）
        AddEntriesHandler.drainQueue();

        // 更新拾取通知条目状态
        DrawEntriesHandler.INSTANCE.tick();

        // 更新传送 FOV 动画（在游戏内且不论是否在 GUI 中都持续更新）
        TeleportFovEffect.tick();

        // 老吴贴贴：生成愤怒粒子（active 配对中点持续冒出，直到状态结束；内部判空）
        top.csituka.youzaiworldcore.client.laowumeme.LaowuMemeClientState.get().tick();

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
