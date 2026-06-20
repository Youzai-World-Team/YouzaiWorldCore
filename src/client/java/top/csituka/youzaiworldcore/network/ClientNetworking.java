package top.csituka.youzaiworldcore.network;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import top.csituka.youzaiworldcore.client.screen.MenuScreen;
import top.csituka.youzaiworldcore.client.screen.account.LoginScreen;
import top.csituka.youzaiworldcore.client.screen.account.RegisterScreen;
import top.csituka.youzaiworldcore.client.screen.element.AboutMeMenuElements;
import top.csituka.youzaiworldcore.client.screen.element.MainMenuElements;
import top.csituka.youzaiworldcore.client.screen.element.MenuElementGroup;
import top.csituka.youzaiworldcore.client.screen.element.SettingsMenuElements;
import top.csituka.youzaiworldcore.client.screen.element.SwitchWorldMenuElements;
import top.csituka.youzaiworldcore.network.account.LoginResultPayload;
import top.csituka.youzaiworldcore.network.account.OpenLoginScreenPayload;
import top.csituka.youzaiworldcore.network.account.RegisterResultPayload;

import java.util.Map;
import java.util.HashMap;

public class ClientNetworking {

    private static final Map<String, MenuElementGroup> MENU_MAP = new HashMap<>();

    // Minecraft 26.2 中 Minecraft.screen 不可访问，使用本地跟踪
    private static volatile Screen currentAccountScreen = null;

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

        // 接收登录/注册界面打开请求
        ClientPlayNetworking.registerGlobalReceiver(OpenLoginScreenPayload.TYPE, (payload, context) -> {
            Minecraft client = context.client();
            client.execute(() -> {
                String playerName = client.player != null ? client.player.getGameProfile().name() : "";
                if ("login".equals(payload.mode())) {
                    currentAccountScreen = new LoginScreen(playerName);
                    client.setScreenAndShow(currentAccountScreen);
                } else if ("register".equals(payload.mode())) {
                    currentAccountScreen = new RegisterScreen(playerName);
                    client.setScreenAndShow(currentAccountScreen);
                }
            });
        });

        // 接收登录结果
        ClientPlayNetworking.registerGlobalReceiver(LoginResultPayload.TYPE, (payload, context) -> {
            Minecraft client = context.client();
            client.execute(() -> {
                // 使用 tracking 变量检查当前界面
                if (currentAccountScreen instanceof LoginScreen loginScreen) {
                    int code = payload.resultCode();
                    String msg = payload.message();
                    if (code == 0) {
                        loginScreen.setStatus(msg, 0xFF55FF55);
                        // 登录成功，关闭界面
                        currentAccountScreen = null;
                        client.setScreenAndShow(null);
                    } else if (code == 1 || code == 4) {
                        loginScreen.setStatus(msg, 0xFFFF5555);
                    }
                    // code=2 (KICK) 和 code=3 (BLOCKED) 由服务器踢出处理
                }
            });
        });

        // 接收注册结果
        ClientPlayNetworking.registerGlobalReceiver(RegisterResultPayload.TYPE, (payload, context) -> {
            Minecraft client = context.client();
            client.execute(() -> {
                if (currentAccountScreen instanceof RegisterScreen registerScreen) {
                    int code = payload.resultCode();
                    String msg = payload.message();
                    if (code == 0) {
                        registerScreen.setStatus(msg, 0xFF55FF55);
                        currentAccountScreen = null;
                        client.setScreenAndShow(null);
                    } else {
                        registerScreen.setStatus(msg, 0xFFFF5555);
                    }
                }
            });
        });
    }

    public static void sendDecomposePacket() {
        ClientPlayNetworking.send(new DecomposeItemPayload());
    }
}
