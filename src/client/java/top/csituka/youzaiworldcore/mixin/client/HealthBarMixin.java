package top.csituka.youzaiworldcore.mixin.client;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import top.csituka.youzaiworldcore.client.config.ClientExternalSettings;
import top.csituka.youzaiworldcore.client.hud.HealthBarRenderer;

/**
 * YZUI 血条替换 Mixin。
 *
 * <p>当 YZUI 启用时，取消原版的 {@link Hud#extractPlayerHealth} 调用（爱心渲染），
 * 改为渲染自定义的长条状血量进度条。</p>
 *
 * <p>关闭 YZUI 时回退到原版爱心渲染，不影响资源包替换或其他模组兼容性。</p>
 */
@SuppressWarnings("null")
@Mixin(Hud.class)
public abstract class HealthBarMixin {

    /**
     * 取消原版爱心血条渲染（YZUI 启用时）。
     *
     * <p>在 {@code extractPlayerHealth} 方法执行前拦截，若 YZUI 启用则直接取消，
     * 避免原版爱心占用屏幕空间。</p>
     */
    @Inject(method = "extractPlayerHealth(Lnet/minecraft/client/gui/GuiGraphicsExtractor;)V",
            at = @At("HEAD"),
            cancellable = true)
    private void yzwc$onExtractPlayerHealth(GuiGraphicsExtractor graphics, CallbackInfo ci) {
        if (yzwc$shouldApplyYzui()) {
            ci.cancel();
        }
    }

    /**
     * 在 HUD 渲染结束时绘制自定义长条血条（YZUI 启用时）。
     *
     * <p>此注入点在 {@code extractRenderState} 返回时执行，确保所有原版 HUD 元素
     * 已渲染完毕，我们的自定义血条绘制在最上层。</p>
     */
    @Inject(method = "extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/DeltaTracker;)V",
            at = @At("RETURN"))
    private void yzwc$onRenderHealthBar(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        if (yzwc$shouldApplyYzui()) {
            HealthBarRenderer.render(graphics);
        }
    }

    /**
     * 判断是否应该应用 YZUI 样式。
     *
     * <p>判断逻辑：</p>
     * <ol>
     *   <li>如果 {@link ClientExternalSettings#isYzuiEnabled()} 返回 {@code true}，始终应用；</li>
     *   <li>如果当前打开的屏幕属于本模组包名（{@code top.csituka.youzaiworldcore}），也应用；</li>
     *   <li>否则回退到原版渲染。</li>
     * </ol>
     *
     * @return {@code true} 如果应该应用 YZUI 血条
     */
    @Unique
    private static boolean yzwc$shouldApplyYzui() {
        if (ClientExternalSettings.isYzuiEnabled()) {
            return true;
        }
        // 模组内部屏幕也启用 YZUI
        var screen = Minecraft.getInstance().gui.screen();
        return screen != null
                && screen.getClass().getName().startsWith("top.csituka.youzaiworldcore");
    }
}
