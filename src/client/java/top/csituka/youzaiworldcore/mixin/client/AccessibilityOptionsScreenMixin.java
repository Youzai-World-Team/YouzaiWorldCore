package top.csituka.youzaiworldcore.mixin.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.OptionsList;
import net.minecraft.client.gui.screens.options.AccessibilityOptionsScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.Arrays;

/**
 * 移除「辅助功能设置」中的以下选项：
 * - 复述功能（narrator）→ @Redirect 跳过整对
 * - 高对比度（highContrast）
 * - 行距（chatLineSpacing）
 * - 聊天延迟（chatDelay）
 * - 隐藏闪烁标语（hideSplashTexts）
 * - 复述功能快捷键（narratorHotkey）
 * - 黑白徽标（darkMojangStudiosBackground）
 * - 文本背景（textBackgroundOpacity）
 * - 按键控制...（controlsButton）→ @Redirect 一并跳过
 */
@Mixin(AccessibilityOptionsScreen.class)
public class AccessibilityOptionsScreenMixin {

    @ModifyArg(
        method = "addOptions",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/components/OptionsList;addSmall([Lnet/minecraft/client/OptionInstance;)V"),
        index = 0
    )
    private OptionInstance<?>[] youzaiworldcore$removeOptionsFromArray(OptionInstance<?>[] options) {
        Minecraft mc = Minecraft.getInstance();
        return Arrays.stream(options)
                .filter(opt -> opt != mc.options.darkMojangStudiosBackground()
                            && opt != mc.options.highContrast()
                            && opt != mc.options.chatLineSpacing()
                            && opt != mc.options.chatDelay()
                            && opt != mc.options.hideSplashTexts()
                            && opt != mc.options.narratorHotkey()
                            && opt != mc.options.textBackgroundOpacity())
                .toArray(OptionInstance<?>[]::new);
    }

    /**
     * 完全跳过 narrator + controls 那对按钮，不再添加。
     */
    @Redirect(
        method = "addOptions",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/components/OptionsList;addSmall(Lnet/minecraft/client/gui/components/AbstractWidget;Lnet/minecraft/client/OptionInstance;Lnet/minecraft/client/gui/components/AbstractWidget;)V")
    )
    private void youzaiworldcore$skipNarratorAndControls(OptionsList list, AbstractWidget narratorWidget, OptionInstance<?> option, AbstractWidget controlsButton) {
        // no-op：跳过整对，不再添加任何按钮
    }
}
