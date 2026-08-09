package top.csituka.youzaiworldcore.mixin.client;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import top.csituka.youzaiworldcore.client.config.ClientExternalSettings;
import top.csituka.youzaiworldcore.client.hud.InventoryHudRenderer;
import top.csituka.youzaiworldcore.util.DebugLogger;

/**
 * YZUI 物品栏 HUD Mixin。
 *
 * <p>
 * 在 {@link Hud#extractRenderState} 的 {@code extractHotbarAndDecorations}
 * 调用之后注入，使物品栏 HUD 与热键栏、血条处于同一渲染层级。
 * 仅当 YZUI 启用时生效，HUD 始终显示（不随 GUI 开关隐藏）。
 * </p>
 *
 * <p>
 * 渲染顺序（同帧内）：
 * </p>
 * <ol>
 * <li>原版 HUD 元素（热键栏、血量、食物等）→ 同级</li>
 * <li><b>物品栏 HUD</b>（本 Mixin 注入，与热键栏同级）</li>
 * <li>聊天组件（后渲染，自然覆盖 HUD）</li>
 * </ol>
 */
@SuppressWarnings("null")
@Mixin(Hud.class)
public abstract class InventoryHudMixin {

    private static final String LOG_TAG = "InventoryHudMixin";

    /**
     * 在 {@code extractHotbarAndDecorations} 之后渲染物品栏 HUD，
     * 使其与热键栏处于同一 Z 层级，同时聊天仍在其上（因为聊天在更晚阶段渲染）。
     */
    @Inject(method = "extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;"
            + "Lnet/minecraft/client/DeltaTracker;)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/Hud;extractHotbarAndDecorations"
                    + "(Lnet/minecraft/client/gui/GuiGraphicsExtractor;"
                    + "Lnet/minecraft/client/DeltaTracker;)V", shift = At.Shift.AFTER))
    private void yzwc$afterExtractHotbarAndDecorations(GuiGraphicsExtractor graphics,
            DeltaTracker deltaTracker, CallbackInfo ci) {
        if (!ClientExternalSettings.isYzuiEnabled()) {
            return;
        }

        try {
            InventoryHudRenderer.render(graphics);
        } catch (Exception e) {
            DebugLogger.error(LOG_TAG, "物品栏HUD渲染异常: %s", e.getMessage());
        }
    }
}
