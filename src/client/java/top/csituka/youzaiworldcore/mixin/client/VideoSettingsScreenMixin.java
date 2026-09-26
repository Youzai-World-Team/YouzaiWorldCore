package top.csituka.youzaiworldcore.mixin.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsSubScreen;
import net.minecraft.client.gui.screens.options.VideoSettingsScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.csituka.youzaiworldcore.client.config.ClientExternalSettings;
import top.csituka.youzaiworldcore.client.config.YzuiThemeMode;
import top.csituka.youzaiworldcore.client.config.YzuiVisualStyle;
import top.csituka.youzaiworldcore.client.screen.YouzaiWorldCoreSettingsScreen;
import top.csituka.youzaiworldcore.client.screen.YzuiAppearanceScreen;

import java.util.Locale;

@Mixin(VideoSettingsScreen.class)
public abstract class VideoSettingsScreenMixin extends OptionsSubScreen {
    protected VideoSettingsScreenMixin(Screen lastScreen, Options options, Component title) {
        super(lastScreen, options, title);
    }

    @Inject(method = "addOptions", at = @At("TAIL"))
    private void youzaiworldcore$addVisualSettings(CallbackInfo ci) {
        if (ClientExternalSettings.isYzuiEnabled()) return;
        Screen screen = (Screen) (Object) this;
        list.addHeader(Component.translatable("screen.youzaiworldcore.settings.sidebar_visual"));
        list.addSmall(
                CycleButton.builder((YzuiThemeMode mode) -> Component.translatable(
                                "screen.youzaiworldcore.appearance.theme." + mode.name().toLowerCase(Locale.ROOT)),
                        ClientExternalSettings.getYzuiTheme())
                        .withValues(YzuiThemeMode.values())
                        .create(Component.translatable("screen.youzaiworldcore.appearance.theme"),
                                (button, mode) -> ClientExternalSettings.setYzuiTheme(mode)),
                CycleButton.builder((YzuiVisualStyle style) -> Component.translatable(
                                "screen.youzaiworldcore.appearance.style." + style.name().toLowerCase(Locale.ROOT)),
                        ClientExternalSettings.getYzuiVisualStyle())
                        .withValues(YzuiVisualStyle.values())
                        .create(Component.translatable("screen.youzaiworldcore.appearance.effects"),
                                (button, style) -> ClientExternalSettings.setYzuiVisualStyle(style)));
        list.addSmall(
                Button.builder(Component.translatable("screen.youzaiworldcore.appearance.title"),
                        button -> Minecraft.getInstance().gui.setScreen(new YzuiAppearanceScreen(screen))).build(),
                Button.builder(Component.translatable("options.youzaiworldcore.settings"),
                        button -> Minecraft.getInstance().gui.setScreen(new YouzaiWorldCoreSettingsScreen(screen))).build());
    }
}
