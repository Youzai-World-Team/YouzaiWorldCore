package top.csituka.youzaiworldcore.mixin.client;

import net.minecraft.client.gui.render.pip.PictureInPictureRenderer;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import net.minecraft.client.renderer.state.gui.pip.PictureInPictureRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.csituka.youzaiworldcore.client.animation.GuiAnimationController;

/** 3D 预览的纹理采用预乘 Alpha，四个通道一起淡化，避免过渡时留下突兀的模型。 */
@Mixin(PictureInPictureRenderer.class)
public class PictureInPictureYzuiOpacityMixin {
    @Unique private float youzaiworldcore$opacity = 1f;

    @Inject(method = "blitTexture", at = @At("HEAD"))
    private void youzaiworldcore$readOpacity(PictureInPictureRenderState state, GuiRenderState gui, CallbackInfo ci) {
        youzaiworldcore$opacity = GuiAnimationController.pictureOpacity(state);
    }

    @ModifyArg(method = "blitTexture", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/state/gui/BlitRenderState;<init>(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/client/gui/render/TextureSetup;Lorg/joml/Matrix3x2fc;IIIIFFFFILnet/minecraft/client/gui/navigation/ScreenRectangle;Lnet/minecraft/client/gui/navigation/ScreenRectangle;)V"), index = 11)
    private int youzaiworldcore$fadePicture(int color) {
        int result = 0;
        for (int shift = 0; shift <= 24; shift += 8) {
            result |= Math.round(((color >>> shift) & 255) * youzaiworldcore$opacity) << shift;
        }
        return result;
    }
}
