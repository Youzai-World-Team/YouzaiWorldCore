package top.csituka.youzaiworldcore.mixin.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.main.GameConfig;
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
     * 原版窗口以隐藏状态创建；仅在 GLFW 将其设为可见之后关闭加载窗口。
     */
    @Inject(
            method = "<init>(Lnet/minecraft/client/main/GameConfig;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lorg/lwjgl/glfw/GLFW;glfwShowWindow(J)V",
                    shift = At.Shift.AFTER,
                    remap = false
            )
    )
    private void youzaiworldcore$closeStartupSplash(GameConfig config, CallbackInfo ci) {
        StartupSplashWindow.close();
    }
}
