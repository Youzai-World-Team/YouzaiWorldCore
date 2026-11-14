package top.csituka.youzaiworldcore.mixin.client;

import net.minecraft.client.Options;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.csituka.youzaiworldcore.util.DebugLogger;

/**
 * 调整原版选项的初始默认值：空配置使用简体中文，并跳过原版欢迎页。
 * <p>
 * 在构造器读取 {@code options.txt} 前设置，已有配置仍由原版读取并覆盖默认值。
 * 语言管理器随后使用最终语言进行首次资源加载；选项由原版启动流程保存。
 * 模组自身的欢迎导览仍按其完成标记显示。
 * </p>
 */
@Mixin(Options.class)
public abstract class OptionsDefaultsMixin {

    @Shadow
    public String languageCode;

    @Shadow
    public boolean onboardAccessibility;

    @Inject(
            method = "<init>(Lnet/minecraft/client/Minecraft;Ljava/io/File;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Options;load()V")
    )
    private void youzaiworldcore$applyDefaults(CallbackInfo ci) {
        languageCode = "zh_cn";
        onboardAccessibility = false;
        DebugLogger.info("OptionsDefaults", "原版空配置默认使用简体中文并跳过原版欢迎页");
    }
}
