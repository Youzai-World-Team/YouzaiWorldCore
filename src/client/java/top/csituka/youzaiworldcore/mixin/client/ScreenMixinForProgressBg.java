package top.csituka.youzaiworldcore.mixin.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ProgressScreen;
import net.minecraft.client.gui.screens.Screen;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.csituka.youzaiworldcore.client.GradientBackgroundUtil;

/**
 * 在 Screen.extractBackground 中拦截 ProgressScreen 实例的背景绘制，
 * 阻止原版绘制全景图和菜单背景，防止屏幕切换时闪现原版加载界面的品牌背景色。
 * <p>
 * 之所以在 Screen 级别而非 ProgressScreen 级别拦截，是因为 ProgressScreen
 * 未覆写 extractBackground 方法（继承自 Screen），Mixins 无法直接定位于继承方法。
 */
@Mixin(Screen.class)
public class ScreenMixinForProgressBg {

    /**
     * 仅在当前实例为 ProgressScreen 时生效：
     * 1. 绘制渐变背景替代原版背景
     * 2. 将 clearColorOverride 设为透明，防止清除颜色缓冲区时闪现原版品牌色
     * 3. 取消后续所有原版背景绘制（全景图、菜单背景纹理、模糊效果等）
     * <p>
     * 注意：{@code extractDeferredSubtitles} 会在 {@code Gui.extractRenderState} 尾部被再次调用，
     * 因此在此处跳过是安全的。
     */
    @Inject(method = "extractBackground", at = @At("HEAD"), cancellable = true)
    private void youzaiworldcore$overrideProgressScreenBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        // 只对 ProgressScreen 生效
        if (!((Object) this instanceof ProgressScreen)) {
            return;
        }

        int width = graphics.guiWidth();
        int height = graphics.guiHeight();

        // 绘制渐变色背景（放在背景层，确保其他 UI 元素覆盖其上）
        GradientBackgroundUtil.drawDiagonalGradient(graphics, width, height, 255);

        // 将 clearColorOverride 设为透明，防止渲染管线在清除颜色缓冲区时闪现原版颜色
        Minecraft minecraft = Minecraft.getInstance();
        Vector4f clearColor = minecraft.gameRenderer.gameRenderState().guiRenderState.clearColorOverride;
        if (clearColor != null) {
            clearColor.set(0, 0, 0, 0);
        }

        // 跳过原版 extractBackground 的所有绘制（全景图、菜单背景纹理、模糊等）
        ci.cancel();
    }
}
