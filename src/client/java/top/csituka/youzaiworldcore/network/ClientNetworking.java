package top.csituka.youzaiworldcore.network;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import top.csituka.youzaiworldcore.client.screen.MenuScreen;
import top.csituka.youzaiworldcore.client.screen.element.AboutMeMenuElements;
import top.csituka.youzaiworldcore.client.screen.element.MainMenuElements;
import top.csituka.youzaiworldcore.client.screen.element.MenuElementGroup;
import top.csituka.youzaiworldcore.client.screen.element.SettingsMenuElements;
import top.csituka.youzaiworldcore.client.screen.element.SwitchWorldMenuElements;

import java.util.Map;
import java.util.HashMap;

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
                top.csituka.youzaiworldcore.feature.ExperimentalFeatures.setEnabled(
                        payload.featureId(), payload.enabled()
                );
            });
        });
    }

    public static void sendDecomposePacket() {
        ClientPlayNetworking.send(new DecomposeItemPayload());
    }
}
