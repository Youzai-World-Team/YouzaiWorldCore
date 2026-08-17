package top.csituka.youzaiworldcore.mixin.client;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import top.csituka.youzaiworldcore.client.config.ClientExternalSettings;
import top.csituka.youzaiworldcore.client.config.YzHudComponent;
import top.csituka.youzaiworldcore.client.config.YzHudSettings;
import top.csituka.youzaiworldcore.client.hud.ArmorHudRenderer;
import top.csituka.youzaiworldcore.client.hud.InventoryHudRenderer;
import top.csituka.youzaiworldcore.client.hud.StatusEffectHudRenderer;
import top.csituka.youzaiworldcore.client.hud.YzHudLayout;
import top.csituka.youzaiworldcore.util.DebugLogger;

/**
 * 游戏内左侧物品栏、装备耐久与状态效果 HUD Mixin。
 *
 * <p>在 {@link Hud#extractRenderState} 的 {@code extractHotbarAndDecorations}
 * 调用之后注入，使三个 HUD 与热键栏处于同一渲染层级。其显示状态由独立的
 * {@code left_hud_enabled} 客户端配置控制，不跟随 YZUI 总开关。</p>
 *
 * <p>三个 HUD 直接以 GUI 单位坐标绘制（不做额外的响应式缩放），因此会像原版
 * 热键栏一样随 MC「界面缩放」设置等比例缩放，在不同 GUI 比例下保持与
 * 原版界面一致的大小。</p>
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
        if (!ClientExternalSettings.isLeftHudEnabled()) {
            return;
        }
        if (YzHudSettings.getOpacity() <= 0.0F) {
            return;
        }

        // 直接使用当前 GUI 尺寸（GUI 单位坐标），三个 HUD 随界面缩放自然缩放，
        // 并分别应用自己的位置变换。
        int guiWidth = graphics.guiWidth();
        int guiHeight = graphics.guiHeight();
        youzaiworldcore$renderComponent(
                graphics, guiWidth, guiHeight, YzHudComponent.INVENTORY);
        youzaiworldcore$renderComponent(
                graphics, guiWidth, guiHeight, YzHudComponent.ARMOR);
        youzaiworldcore$renderComponent(
                graphics, guiWidth, guiHeight, YzHudComponent.EFFECTS);
    }

    private static void youzaiworldcore$renderComponent(
            GuiGraphicsExtractor graphics, int guiWidth, int guiHeight,
            YzHudComponent component) {
        try {
            graphics.pose().pushMatrix();
            graphics.pose().translate(
                    YzHudLayout.translationX(component, guiWidth),
                    YzHudLayout.translationY(component, guiHeight));
            switch (component) {
                case INVENTORY -> InventoryHudRenderer.render(graphics, guiHeight);
                case ARMOR -> ArmorHudRenderer.render(graphics, guiHeight);
                case EFFECTS -> StatusEffectHudRenderer.render(graphics, guiHeight);
            }
        } catch (Exception e) {
            DebugLogger.exception(LOG_TAG, "render" + component.name(), e);
        } finally {
            graphics.pose().popMatrix();
        }
    }

    /**
     * 左侧状态效果列表启用时，停用右上角原版效果图标，避免重复显示。
     */
    @Inject(
            method = "extractEffects(Lnet/minecraft/client/gui/GuiGraphicsExtractor;"
                    + "Lnet/minecraft/client/DeltaTracker;)V",
            at = @At("HEAD"),
            cancellable = true)
    private void yzwc$hideVanillaEffects(GuiGraphicsExtractor graphics,
            DeltaTracker deltaTracker, CallbackInfo ci) {
        if (ClientExternalSettings.isLeftHudEnabled()) {
            ci.cancel();
        }
    }
}
