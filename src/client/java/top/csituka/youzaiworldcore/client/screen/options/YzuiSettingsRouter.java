package top.csituka.youzaiworldcore.client.screen.options;

import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.CreditsAndAttributionScreen;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.WinScreen;
import net.minecraft.client.gui.screens.multiplayer.RestrictionsScreen;
import net.minecraft.client.gui.screens.options.InWorldGameRulesScreen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.client.gui.screens.options.OptionsSubScreen;
import net.minecraft.client.gui.screens.options.WorldOptionsScreen;
import net.minecraft.client.gui.screens.options.UnsupportedGraphicsWarningScreen;
import net.minecraft.client.gui.screens.packs.PackSelectionScreen;
import net.minecraft.client.gui.screens.telemetry.TelemetryInfoScreen;
import top.csituka.youzaiworldcore.client.config.ClientExternalSettings;
import top.csituka.youzaiworldcore.util.DebugLogger;

/** 在 Gui 屏幕切换入口选择独立的设置界面；不向原版设置屏幕注入新布局。 */
public final class YzuiSettingsRouter {
    private record HostLink(WeakReference<Screen> screen, Screen origin, boolean inWorld,
                            boolean options, boolean unified, String anchor) { }
    private static final Map<Screen, HostLink> HOSTS = new WeakHashMap<>();
    private static final Map<Screen, Screen> COMPATIBILITY = new WeakHashMap<>();

    private YzuiSettingsRouter() { }

    /** 仅由客户端主线程调用。身份映射使模组回调中的原版父屏幕能返回原来的 MD3 页面。 */
    public static Screen resolve(Screen target) {
        if (target == null) return null;
        if (!ClientExternalSettings.isYzuiEnabled()) {
            if (target instanceof YzuiOptionsScreen settings) return settings.legacyScreen();
            if (target instanceof YzuiGameRulesScreen rules) return rules.parent().legacyScreen();
            if (target.getClass() == OptionsScreen.class && (HOSTS.containsKey(target) || COMPATIBILITY.containsKey(target))) {
                OptionsScreen options = (OptionsScreen) target;
                Screen parent = options.getLastScreen();
                if (parent instanceof YzuiOptionsScreen settings) parent = settings.origin();
                return new OptionsScreen(parent, Minecraft.getInstance().options,
                        SettingsWidgets.read(options, "inWorld", Boolean.class));
            }
            return target;
        }
        if (target instanceof YzuiOptionsScreen || target instanceof YzuiGameRulesScreen) return target;
        Screen current = Minecraft.getInstance().gui.screen();
        if (COMPATIBILITY.containsKey(target)) return target;
        if (COMPATIBILITY.containsKey(current) && target != COMPATIBILITY.get(current) && !HOSTS.containsKey(target)
                && !(target instanceof TitleScreen) && !(target instanceof PauseScreen)) {
            COMPATIBILITY.put(target, COMPATIBILITY.get(current));
            return target;
        }
        HostLink link = HOSTS.get(target);
        if (link != null) {
            Screen host = link.screen.get();
            if (host != null) return host;
            // 第三方页面可能只保存了原版父屏幕；即便旧外壳已被 GC，也恢复到 MD3 页面。
            if (link.options) {
                Screen source = link.unified && !(target instanceof OptionsScreen)
                        ? new OptionsScreen(link.origin, Minecraft.getInstance().options, link.inWorld) : target;
                YzuiOptionsScreen restored = new YzuiOptionsScreen(source, link.origin, link.inWorld);
                if (link.unified) restored.reveal(link.anchor);
                return restored;
            }
        }

        if (target.getClass() == OptionsScreen.class) {
            OptionsScreen original = (OptionsScreen) target;
            boolean inWorld = SettingsWidgets.read(original, "inWorld", Boolean.class);
            // 从 ModMenu 开启 YZUI 后，已初始化的原版首页可能仍缺少旧逻辑隐藏的项目。
            OptionsScreen source = original.children().isEmpty() ? original
                    : new OptionsScreen(original.getLastScreen(), Minecraft.getInstance().options, inWorld);
            YzuiOptionsScreen host = new YzuiOptionsScreen(source, original.getLastScreen(), inWorld);
            if (source != original) remember(original, host);
            return host;
        }
        if (current instanceof YzuiOptionsScreen parent && isVanillaPage(target)) {
            if (UnifiedSettingsPage.contains(target)) {
                YzuiOptionsScreen root = parent.rootSettings();
                if (root == null) root = new YzuiOptionsScreen(
                        new OptionsScreen(parent.origin(), Minecraft.getInstance().options, parent.inWorld()), parent.origin(), parent.inWorld());
                root.reveal(UnifiedSettingsPage.anchor(target));
                remember(target, root);
                return root;
            }
            if (target.getClass() == InWorldGameRulesScreen.class) {
                if (Minecraft.getInstance().getConnection() == null) return target;
                YzuiGameRulesScreen screen = new YzuiGameRulesScreen(parent);
                remember(target, screen);
                return screen;
            }
            return new YzuiOptionsScreen(target, parent.origin(), parent.inWorld(), parent.rootSettings());
        }
        if (UnifiedSettingsPage.contains(target)) {
            Screen origin = SettingsWidgets.read(target, "lastScreen", Screen.class);
            boolean inWorld = Minecraft.getInstance().level != null;
            YzuiOptionsScreen root = origin instanceof YzuiOptionsScreen parent ? parent.rootSettings() : null;
            if (root == null) root = new YzuiOptionsScreen(new OptionsScreen(origin, Minecraft.getInstance().options, inWorld), origin, inWorld);
            root.reveal(UnifiedSettingsPage.anchor(target));
            remember(target, root);
            return root;
        }
        if (target instanceof OptionsSubScreen && isVanillaPage(target)) {
            // 语言选择、按键绑定等具有独立交互流程的页面继续使用专用布局。
            Screen origin = SettingsWidgets.read(target, "lastScreen", Screen.class);
            return new YzuiOptionsScreen(target, origin, Minecraft.getInstance().level != null);
        }
        if (current instanceof YzuiGameRulesScreen rules && target.getClass() == ConfirmScreen.class) {
            return new YzuiOptionsScreen(target, rules.parent().origin(), rules.parent().inWorld(), rules.parent().rootSettings());
        }
        return target;
    }

    private static boolean isVanillaPage(Screen screen) {
        // 第三方的子类可能有自己的交互与画面，使用它们自己的页面。
        if (!screen.getClass().getName().startsWith("net.minecraft.client.gui.screens.")) return false;
        return screen instanceof OptionsSubScreen || screen instanceof WorldOptionsScreen
                || screen instanceof PackSelectionScreen || screen instanceof TelemetryInfoScreen
                || screen instanceof CreditsAndAttributionScreen || screen instanceof RestrictionsScreen
                || screen instanceof InWorldGameRulesScreen || screen instanceof ConfirmScreen
                || screen instanceof UnsupportedGraphicsWarningScreen
                || screen instanceof WinScreen && !SettingsWidgets.read(screen, "poem", Boolean.class);
    }

    static void remember(Screen source, Screen host) {
        if (host instanceof YzuiOptionsScreen options) {
            HOSTS.put(source, new HostLink(new WeakReference<>(host), options.origin(), options.inWorld(), true,
                    options.isUnified(), options.isUnified() ? UnifiedSettingsPage.anchor(source) : null));
        } else {
            HOSTS.put(source, new HostLink(new WeakReference<>(host), null, false, false, false, null));
        }
    }

    static void openCompatibility(YzuiOptionsScreen parent) {
        Minecraft minecraft = Minecraft.getInstance();
        OptionsScreen screen = new OptionsScreen(parent, minecraft.options, parent.inWorld());
        COMPATIBILITY.put(screen, parent.origin());
        DebugLogger.info("YzuiSettings", "打开原版选项兼容入口");
        minecraft.gui.setScreen(screen);
    }
}
