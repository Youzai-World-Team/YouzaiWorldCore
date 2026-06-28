package top.csituka.youzaiworldcore.network;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import top.csituka.youzaiworldcore.client.screen.LoginScreen;
import top.csituka.youzaiworldcore.client.screen.MenuScreen;
import top.csituka.youzaiworldcore.client.screen.RegisterScreen;
import top.csituka.youzaiworldcore.client.screen.element.AboutMeMenuElements;
import top.csituka.youzaiworldcore.client.screen.element.MainMenuElements;
import top.csituka.youzaiworldcore.client.screen.element.MenuElementGroup;
import top.csituka.youzaiworldcore.client.screen.element.SettingsMenuElements;
import top.csituka.youzaiworldcore.client.screen.element.SwitchWorldMenuElements;

import java.util.Map;
import java.util.HashMap;
import java.util.UUID;

public class ClientNetworking {

    private static final Map<String, MenuElementGroup> MENU_MAP = new HashMap<>();

    static {
        MENU_MAP.put("main", new MainMenuElements());
        MENU_MAP.put("switch_world", new SwitchWorldMenuElements());
        MENU_MAP.put("settings", new SettingsMenuElements());
        MENU_MAP.put("about_me", new AboutMeMenuElements());
    }

    public static void initialize() {
        ClientPlayNetworking.registerGlobalReceiver(OpenMenuPayload.ID, (payload, context) -> {
            Minecraft client = context.client();
            client.execute(() -> {
                MenuElementGroup element = MENU_MAP.get(payload.menuName());
                if (element != null) {
                    client.setScreenAndShow(new MenuScreen(element));
                }
            });
        });

        // 注册实验性功能同步处理器
        ClientPlayNetworking.registerGlobalReceiver(FeatureSyncPayload.ID, (payload, context) -> {
            context.client().execute(() -> {
                var features = top.csituka.youzaiworldcore.feature.ExperimentalFeatures.class;
                UUID targetPlayer = payload.targetPlayer();

                // 首次收到同步包时设置客户端玩家 UUID
                if (targetPlayer != null) {
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
        });

        // 注册认证界面打开处理器
        ClientPlayNetworking.registerGlobalReceiver(OpenAuthScreenPayload.ID, (payload, context) -> {
            Minecraft client = context.client();
            client.execute(() -> {
                // 如果当前已经是认证界面，不重复打开
                Screen currentScreen = client.gui.screen();
                if (currentScreen instanceof RegisterScreen
                        || currentScreen instanceof LoginScreen) {
                    return;
                }
                String type = payload.screenType();
                String username = payload.username();
                if ("register".equals(type)) {
                    client.setScreenAndShow(new RegisterScreen(username));
                } else if ("login".equals(type)) {
                    client.setScreenAndShow(new LoginScreen(username));
                }
            });
        });
    }

    public static void sendDecomposePacket() {
        ClientPlayNetworking.send(new DecomposeItemPayload());
    }
}
