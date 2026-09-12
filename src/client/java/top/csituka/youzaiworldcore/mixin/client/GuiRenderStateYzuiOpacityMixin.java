package top.csituka.youzaiworldcore.mixin.client;

import net.minecraft.client.renderer.state.gui.GuiRenderState;
import net.minecraft.client.renderer.state.gui.pip.PictureInPictureRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.csituka.youzaiworldcore.client.animation.GuiAnimationController;

/** 为玩家模型、皮肤、书本等延迟合成的图片保存当前页面透明度。 */
@Mixin(GuiRenderState.class)
public class GuiRenderStateYzuiOpacityMixin {
    @Inject(method = "addPicturesInPictureState", at = @At("HEAD"))
    private void youzaiworldcore$captureOpacity(PictureInPictureRenderState state, CallbackInfo ci) {
        GuiAnimationController.capturePictureOpacity(state);
    }
}
