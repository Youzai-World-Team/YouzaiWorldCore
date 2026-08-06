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
import top.csituka.youzaiworldcore.client.hud.HotbarRenderer;
import top.csituka.youzaiworldcore.util.DebugLogger;

/**
 * YZUI 热键栏替换 Mixin。
 *
 * <p>当 YZUI 启用时，取消原版 {@link Hud#extractItemHotbar
 * extractItemHotbar} 渲染，改为调用 {@link HotbarRenderer#render
 * HotbarRenderer.render} 绘制 YZUI 风格热键栏。</p>
 *
 * <p>注入点：{@code extractItemHotbar(GuiGraphicsExtractor, DeltaTracker)} 的 HEAD，
 * 若 YZUI 关闭则回退原版渲染。</p>
 *
 * <p>与原版 HUD 其他元素的交互：</p>
 * <ul>
 *   <li>本 Mixin 仅拦截 {@code extractItemHotbar}，不影响同一调用链下游的
 *       {@code extractPlayerHealth} / {@code extractVehicleHealth} /
 *       {@code extractSelectedItemName} 等方法。</li>
 *   <li>{@link top.csituka.youzaiworldcore.mixin.client.itemborder.HudHotbarMixin
 *       HudHotbarMixin}（物品稀有度边框）在 YZUI 热键栏启用时将不再被触发，
 *       因为原版 {@code extractSlot} 不再被调用。物品边框由容器屏幕的
 *       {@code itemborder.AbstractContainerScreenMixin} 独立处理。</li>
 * </ul>
 */
@SuppressWarnings("null")
@Mixin(Hud.class)
public abstract class HotbarMixin {

    private static final String LOG_TAG = "HotbarMixin";

    /**
     * 取消原版热键栏渲染并替换为 YZUI 风格。
     *
     * @param graphics     GuiGraphicsExtractor 实例
     * @param deltaTracker 帧时间追踪器
     * @param ci           回调信息（用于取消原版逻辑）
     */
    @Inject(method = "extractItemHotbar(Lnet/minecraft/client/gui/GuiGraphicsExtractor;"
            + "Lnet/minecraft/client/DeltaTracker;)V",
            at = @At("HEAD"),
            cancellable = true)
    private void yzwc$onExtractItemHotbar(GuiGraphicsExtractor graphics,
            DeltaTracker deltaTracker, CallbackInfo ci) {
        if (!yzwc$shouldApplyYzui()) {
            return;
        }

        // 取消原版渲染
        ci.cancel();

        // 渲染 YZUI 热键栏
        try {
            HotbarRenderer.render(graphics, deltaTracker);
        } catch (Exception e) {
            DebugLogger.error(LOG_TAG, "YZUI 热键栏渲染异常: %s", e.getMessage());
        }
    }

    // ===== YZUI 开关判断 =====

    /**
     * 判断是否应用 YZUI 样式（与 {@code HealthBarMixin}、{@code AbstractButtonMixin} 等一致）。
     * <ul>
     *   <li>全局 YZUI 开启 → 强制应用</li>
     *   <li>全局关闭但当前屏幕为模组自定义屏幕 → 仍然应用</li>
     *   <li>否则 → 回退原版</li>
     * </ul>
     */
    @Unique
    private static boolean yzwc$shouldApplyYzui() {
        if (ClientExternalSettings.isYzuiEnabled()) {
            return true;
        }
        var screen = Minecraft.getInstance().gui.screen();
        return screen != null
                && screen.getClass().getName().startsWith("top.csituka.youzaiworldcore");
    }
}
