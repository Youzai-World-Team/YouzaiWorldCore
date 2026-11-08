package top.csituka.youzaiworldcore.mixin.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.main.GameConfig;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.csituka.youzaiworldcore.client.startup.StartupSplashWindow;

/**
 * 在 Minecraft 原生主窗口真正显示后关闭启动加载窗口。
 */
@Mixin(Minecraft.class)
public class MinecraftStartupSplashMixin {

    /**
     * 26.3 的原版窗口在创建时即可见，没有单独的显示调用；窗口对象写入字段后关闭加载窗口。
     */
    @Inject(
            method = "<init>(Lnet/minecraft/client/main/GameConfig;)V",
            at = @At(
                    value = "FIELD",
                    target = "Lnet/minecraft/client/Minecraft;window:Lcom/mojang/blaze3d/platform/Window;",
                    opcode = Opcodes.PUTFIELD,
                    shift = At.Shift.AFTER
            )
    )
    private void youzaiworldcore$closeStartupSplash(GameConfig config, CallbackInfo ci) {
        StartupSplashWindow.close();
    }
}
