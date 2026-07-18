package top.csituka.youzaiworldcore.network;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import top.csituka.youzaiworldcore.client.screen.LoginScreen;
import top.csituka.youzaiworldcore.client.screen.MenuScreen;
import top.csituka.youzaiworldcore.client.screen.RegisterScreen;
import top.csituka.youzaiworldcore.client.screen.block.TeleportAnchorNameScreen;
import top.csituka.youzaiworldcore.client.screen.block.TeleportAnchorScreen;
import top.csituka.youzaiworldcore.client.screen.element.AboutMeMenuElements;
import top.csituka.youzaiworldcore.client.screen.element.MainMenuElements;
import top.csituka.youzaiworldcore.client.screen.element.MenuElementGroup;
import top.csituka.youzaiworldcore.client.screen.element.SettingsMenuElements;
import top.csituka.youzaiworldcore.client.screen.element.SwitchWorldMenuElements;
import top.csituka.youzaiworldcore.client.hud.AdventureLevelHudRenderer;
import top.csituka.youzaiworldcore.client.skill.ClientAttributeData;
import top.csituka.youzaiworldcore.mana.ManaManager;
import top.csituka.youzaiworldcore.util.DebugLogger;

import java.util.Map;
import java.util.HashMap;
import java.util.UUID;

@SuppressWarnings("null")
public class ClientNetworking {

    private static final Map<String, MenuElementGroup> MENU_MAP = new HashMap<>();

    static {
        MENU_MAP.put("main", new MainMenuElements());
        MENU_MAP.put("switch_world", new SwitchWorldMenuElements());
        MENU_MAP.put("settings", new SettingsMenuElements());
        MENU_MAP.put("about_me", new AboutMeMenuElements());
    }

    public static void initialize() {
        DebugLogger.entering("ClientNetworking", "initialize");

        ClientPlayNetworking.registerGlobalReceiver(OpenMenuPayload.ID, (payload, context) -> {
            DebugLogger.entering("ClientNetworking", "OpenMenuPayload handler");
            Minecraft client = context.client();
            client.execute(() -> {
                MenuElementGroup element = MENU_MAP.get(payload.menuName());
                boolean hasElement = element != null;
                DebugLogger.branch("ClientNetworking", "MENU_MAP contains menuName", hasElement, "menuName=" + payload.menuName());
                if (hasElement) {
                    client.setScreenAndShow(new MenuScreen(element));
                }
            });
            DebugLogger.exiting("ClientNetworking", "OpenMenuPayload handler");
        });
        DebugLogger.info("ClientNetworking", "Registered receiver: OpenMenuPayload");

        // 注册实验性功能同步处理器
        ClientPlayNetworking.registerGlobalReceiver(FeatureSyncPayload.ID, (payload, context) -> {
            DebugLogger.entering("ClientNetworking", "FeatureSyncPayload handler");
            context.client().execute(() -> {
                UUID targetPlayer = payload.targetPlayer();

                // 首次收到同步包时设置客户端玩家 UUID
                boolean hasTarget = targetPlayer != null;
                DebugLogger.branch("ClientNetworking", "targetPlayer != null", hasTarget);
                if (hasTarget) {
                    top.csituka.youzaiworldcore.feature.ExperimentalFeatures.setClientPlayerUuid(targetPlayer);
                    top.csituka.youzaiworldcore.feature.ExperimentalFeatures.applyPersonalSync(
                            targetPlayer, payload.featureId(), payload.enabled()
                    );
                } else {
                    top.csituka.youzaiworldcore.feature.ExperimentalFeatures.applyGlobalSync(
                            payload.featureId(), payload.enabled()
                    );
                }
            });
            DebugLogger.exiting("ClientNetworking", "FeatureSyncPayload handler");
        });
        DebugLogger.info("ClientNetworking", "Registered receiver: FeatureSyncPayload");

        // 注册认证界面打开处理器
        ClientPlayNetworking.registerGlobalReceiver(OpenAuthScreenPayload.ID, (payload, context) -> {
            DebugLogger.entering("ClientNetworking", "OpenAuthScreenPayload handler");
            Minecraft client = context.client();
            client.execute(() -> {
                // 如果当前已经是认证界面，不重复打开
                Screen currentScreen = client.gui.screen();
                boolean isRegisterScreen = currentScreen instanceof RegisterScreen;
                boolean isLoginScreen = currentScreen instanceof LoginScreen;
                DebugLogger.branch("ClientNetworking", "currentScreen instanceof RegisterScreen", isRegisterScreen);
                DebugLogger.branch("ClientNetworking", "currentScreen instanceof LoginScreen", isLoginScreen);
                if (isRegisterScreen || isLoginScreen) {
                    DebugLogger.exiting("ClientNetworking", "OpenAuthScreenPayload handler (skipped, screen already open)");
                    return;
                }
                String type = payload.screenType();
                String username = payload.username();
                boolean isRegister = "register".equals(type);
                DebugLogger.branch("ClientNetworking", "screenType == register", isRegister);
                if (isRegister) {
                    client.setScreenAndShow(new RegisterScreen(username));
                } else if ("login".equals(type)) {
                    client.setScreenAndShow(new LoginScreen(username));
                }
            });
            DebugLogger.exiting("ClientNetworking", "OpenAuthScreenPayload handler");
        });
        DebugLogger.info("ClientNetworking", "Registered receiver: OpenAuthScreenPayload");

        // 注册传送锚点列表处理器
        ClientPlayNetworking.registerGlobalReceiver(TeleportAnchorListPayload.TYPE, (payload, context) -> {
            DebugLogger.entering("ClientNetworking", "TeleportAnchorListPayload handler");
            context.client().execute(() -> {
                context.client().setScreenAndShow(new TeleportAnchorScreen(payload.points(), payload.currentPos(), payload.currentDim()));
            });
            DebugLogger.exiting("ClientNetworking", "TeleportAnchorListPayload handler");
        });
        DebugLogger.info("ClientNetworking", "Registered receiver: TeleportAnchorListPayload");

        // 注册传送锚点命名界面处理器
        ClientPlayNetworking.registerGlobalReceiver(TeleportAnchorOpenNamePayload.TYPE, (payload, context) -> {
            DebugLogger.entering("ClientNetworking", "TeleportAnchorOpenNamePayload handler");
            context.client().execute(() -> {
                context.client().setScreenAndShow(
                        new TeleportAnchorNameScreen(payload.pos(), payload.dimension()));
            });
            DebugLogger.exiting("ClientNetworking", "TeleportAnchorOpenNamePayload handler");
        });
        DebugLogger.info("ClientNetworking", "Registered receiver: TeleportAnchorOpenNamePayload");

        // 注册魔力同步处理器
        ClientPlayNetworking.registerGlobalReceiver(ManaSyncPayload.ID, (payload, context) -> {
            context.client().execute(() -> {
                ManaManager.setClientMana(payload.mana());
            });
        });
        DebugLogger.info("ClientNetworking", "Registered receiver: ManaSyncPayload");

        // 注册冒险经验同步处理器
        ClientPlayNetworking.registerGlobalReceiver(LevelExpSyncPayload.ID, (payload, context) -> {
            context.client().execute(() -> {
                AdventureLevelHudRenderer.onExpGained(
                        payload.level(),
                        payload.currentExp(),
                        payload.neededExp(),
                        payload.gainedExp(),
                        payload.leveledUp()
                );
            });
        });
        DebugLogger.info("ClientNetworking", "Registered receiver: LevelExpSyncPayload");

        // 注册属性数据同步处理器
        ClientPlayNetworking.registerGlobalReceiver(AttributeSyncPayload.TYPE, (payload, context) -> {
            context.client().execute(() -> {
                ClientAttributeData.update(payload);
            });
        });
        DebugLogger.info("ClientNetworking", "Registered receiver: AttributeSyncPayload");

        DebugLogger.exiting("ClientNetworking", "initialize");
    }

    public static void sendDecomposePacket() {
        ClientPlayNetworking.send(new DecomposeItemPayload());
    }
}
