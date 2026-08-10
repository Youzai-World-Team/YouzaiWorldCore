package top.csituka.youzaiworldcore.mixin.client;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import top.csituka.youzaiworldcore.client.config.ClientExternalSettings;
import top.csituka.youzaiworldcore.client.hud.ArmorHudRenderer;
import top.csituka.youzaiworldcore.client.hud.InventoryHudRenderer;
import top.csituka.youzaiworldcore.util.DebugLogger;

/**
 * YZUI 物品栏 + 装备耐久 HUD Mixin。
 *
 * <p>在 {@link Hud#extractRenderState} 的 {@code extractHotbarAndDecorations}
 * 调用之后注入，使两个 HUD 与热键栏处于同一渲染层级。仅当 YZUI 启用时生效。</p>
 */
@SuppressWarnings("null")
@Mixin(Hud.class)
public abstract class InventoryHudMixin {

    private static final String LOG_TAG = "InventoryHudMixin";

    @Inject(
            method = "extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;"
                    + "Lnet/minecraft/client/DeltaTracker;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/Hud;extractHotbarAndDecorations"
                            + "(Lnet/minecraft/client/gui/GuiGraphicsExtractor;"
                            + "Lnet/minecraft/client/DeltaTracker;)V",
                    shift = At.Shift.AFTER))
    private void yzwc$afterExtractHotbarAndDecorations(GuiGraphicsExtractor graphics,
            DeltaTracker deltaTracker, CallbackInfo ci) {
        if (!ClientExternalSettings.isYzuiEnabled()) {
            return;
        }

        try {
            InventoryHudRenderer.render(graphics);
            ArmorHudRenderer.render(graphics);
        } catch (Exception e) {
            DebugLogger.error(LOG_TAG, "HUD渲染异常: %s", e.getMessage());
        }
    }
}
