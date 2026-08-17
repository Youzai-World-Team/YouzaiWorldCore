package top.csituka.youzaiworldcore.mixin.client;

import net.minecraft.client.gui.render.GuiItemAtlas;
import net.minecraft.client.gui.render.GuiRenderer;
import net.minecraft.client.renderer.state.gui.GuiItemRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.csituka.youzaiworldcore.client.hud.YzHudItemOpacityAccess;

/**
 * 把 YZHUD 物品图标透明度传给 26.2 的 GUI 物品图集合成颜色。
 */
@Mixin(GuiRenderer.class)
public abstract class GuiRendererYzHudOpacityMixin {

    @Unique
    private float youzaiworldcore$currentItemOpacity = 1.0F;

    @Inject(method = "submitBlitFromItemAtlas", at = @At("HEAD"))
    private void youzaiworldcore$readYzHudOpacity(GuiItemRenderState state,
            GuiItemAtlas.SlotView slotView, CallbackInfo ci) {
        youzaiworldcore$currentItemOpacity =
                ((YzHudItemOpacityAccess) (Object) state).youzaiworldcore$getOpacity();
    }

    @ModifyArg(
            method = "submitBlitFromItemAtlas",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/state/gui/BlitRenderState;<init>"
                            + "(Lcom/mojang/blaze3d/pipeline/RenderPipeline;"
                            + "Lnet/minecraft/client/gui/render/TextureSetup;"
                            + "Lorg/joml/Matrix3x2fc;IIIIFFFFI"
                            + "Lnet/minecraft/client/gui/navigation/ScreenRectangle;"
                            + "Lnet/minecraft/client/gui/navigation/ScreenRectangle;)V"),
            index = 11)
    private int youzaiworldcore$applyYzHudOpacity(int color) {
        float opacity = youzaiworldcore$currentItemOpacity;
        int a = Math.round((color >>> 24) * opacity);
        int r = Math.round(((color >>> 16) & 0xFF) * opacity);
        int g = Math.round(((color >>> 8) & 0xFF) * opacity);
        int b = Math.round((color & 0xFF) * opacity);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }
}
