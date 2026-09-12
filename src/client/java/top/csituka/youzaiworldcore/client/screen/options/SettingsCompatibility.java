package top.csituka.youzaiworldcore.client.screen.options;

import com.terraformersmc.modmenu.ModMenu;
import com.terraformersmc.modmenu.gui.ModsScreen;
import java.lang.reflect.InvocationTargetException;
import java.util.Comparator;
import java.util.List;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import top.csituka.youzaiworldcore.client.screen.widget.TransparentButton;
import top.csituka.youzaiworldcore.util.DebugLogger;

/** 可选模组的设置入口。Sodium 使用工厂，让 Reese、Extra 与 Iris 继续参加其原生扩展流程。 */
final class SettingsCompatibility {
    private static final String SODIUM_SCREEN = "net.caffeinemc.mods.sodium.client.gui.VideoSettingsScreen";
    private static final List<String> PRIORITY = List.of("modmenu", "sodium", "sodium-extra",
            "reeses-sodium-options", "iris", "sodium-fullbright");

    private SettingsCompatibility() { }

    static boolean sodiumAvailable() { return FabricLoader.getInstance().isModLoaded("sodium"); }

    static void openSodium(Screen parent) {
        try {
            // 走公开工厂与原生初始化流程，Reese、Extra、Iris 可继续替换页面或添加选项。
            Screen screen = (Screen) Class.forName(SODIUM_SCREEN).getMethod("createScreen", Screen.class)
                    .invoke(null, parent);
            Minecraft.getInstance().gui.setScreen(screen);
            DebugLogger.info("YzuiSettings", "打开 Sodium 及附属模组设置");
        } catch (ReflectiveOperationException | LinkageError exception) {
            Throwable cause = exception instanceof InvocationTargetException invocation
                    ? invocation.getTargetException() : exception;
            DebugLogger.exception("YzuiSettings", "openSodium", cause);
            Minecraft.getInstance().gui.setScreen(new ModsScreen(parent));
        }
    }

    static void openMods(Screen parent) { Minecraft.getInstance().gui.setScreen(new ModsScreen(parent)); }

    static void addModRows(Screen parent, List<SettingsList.Row> rows) {
        rows.add(SettingsList.Row.widget(button(Component.translatable("options.youzaiworldcore.installed_mods"),
                () -> openMods(parent))));
        if (sodiumAvailable()) {
            rows.add(SettingsList.Row.controls(Component.literal("Sodium / Sodium Extra / Reese’s Sodium Options"),
                    YzuiOptionsScreen.text("sodium.description"), button(YzuiOptionsScreen.text("sodium"), () -> openSodium(parent))));
        }
        FabricLoader.getInstance().getAllMods().stream()
                .filter(mod -> !mod.getMetadata().getId().equals("youzaiworldcore"))
                .filter(mod -> ModMenu.hasConfigScreen(mod.getMetadata().getId()))
                .sorted(Comparator.comparingInt(SettingsCompatibility::priority)
                        .thenComparing(mod -> mod.getMetadata().getName(), String.CASE_INSENSITIVE_ORDER))
                .forEach(mod -> rows.add(SettingsList.Row.controls(Component.literal(mod.getMetadata().getName()),
                        Component.literal(mod.getMetadata().getVersion().getFriendlyString()),
                        button(YzuiOptionsScreen.text("open_mod"), () -> openConfig(mod, parent)))));
    }

    private static int priority(ModContainer mod) {
        int index = PRIORITY.indexOf(mod.getMetadata().getId());
        return index < 0 ? PRIORITY.size() : index;
    }

    private static void openConfig(ModContainer mod, Screen parent) {
        try {
            Screen screen = ModMenu.getConfigScreen(mod.getMetadata().getId(), parent);
            if (screen != null) Minecraft.getInstance().gui.setScreen(screen);
            else openMods(parent);
        } catch (RuntimeException | LinkageError exception) {
            DebugLogger.exception("YzuiSettings", "openConfig:" + mod.getMetadata().getId(), exception);
            openMods(parent);
        }
    }

    private static TransparentButton button(Component text, Runnable action) {
        return new TransparentButton(0, 0, 150, 24, text, action);
    }
}
