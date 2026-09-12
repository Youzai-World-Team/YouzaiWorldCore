package top.csituka.youzaiworldcore.mixin.client;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import org.joml.Quaternionfc;
import org.joml.Vector2f;
import org.joml.Vector3fc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import top.csituka.youzaiworldcore.client.animation.GuiAnimationController;
import top.csituka.youzaiworldcore.client.render.YzuiViewport;

/** 只在最终绘制入口叠乘页面透明度，避免不同重载重复淡化文字、纹理或渐变。 */
@Mixin(GuiGraphicsExtractor.class)
public abstract class GuiAnimationGuiGraphicsExtractorMixin {
    @Inject(method = {"setTooltipForNextFrameInternal", "setPreeditOverlay"}, at = @At("HEAD"), cancellable = true)
    private void youzaiworldcore$skipBackgroundOverlays(CallbackInfo ci) {
        if (GuiAnimationController.isBackgroundRendering()) ci.cancel();
    }

    @Inject(method = "guiWidth", at = @At("HEAD"), cancellable = true)
    private void youzaiworldcore$logicalWidth(CallbackInfoReturnable<Integer> cir) {
        var screen = YzuiViewport.renderingScreen();
        if (screen != null) cir.setReturnValue(screen.width);
    }

    @Inject(method = "guiHeight", at = @At("HEAD"), cancellable = true)
    private void youzaiworldcore$logicalHeight(CallbackInfoReturnable<Integer> cir) {
        var screen = YzuiViewport.renderingScreen();
        if (screen != null) cir.setReturnValue(screen.height);
    }

    @ModifyVariable(method = "innerFill", at = @At("HEAD"), argsOnly = true, ordinal = 4)
    private int youzaiworldcore$fadeFill(int color) {
        return GuiAnimationController.fadeColor(color);
    }

    @ModifyVariable(method = "innerFill", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private Integer youzaiworldcore$fadeGradient(Integer color) {
        return color == null ? null : GuiAnimationController.fadeColor(color);
    }

    @ModifyVariable(method = "text(Lnet/minecraft/client/gui/Font;Lnet/minecraft/util/FormattedCharSequence;IIIZ)V",
            at = @At("HEAD"), argsOnly = true, ordinal = 2)
    private int youzaiworldcore$fadeText(int color) {
        return GuiAnimationController.fadeColor(color);
    }

    @ModifyVariable(method = "innerBlit(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lcom/mojang/blaze3d/textures/GpuTextureView;Lcom/mojang/blaze3d/textures/GpuSampler;IIIIFFFFI)V",
            at = @At("HEAD"), argsOnly = true, ordinal = 4)
    private int youzaiworldcore$fadeTexture(int color) {
        return GuiAnimationController.fadeColor(color);
    }

    @ModifyVariable(method = "innerTiledBlit", at = @At("HEAD"), argsOnly = true, ordinal = 6)
    private int youzaiworldcore$fadeTiledTexture(int color) {
        return GuiAnimationController.fadeColor(color);
    }

    /** 26.2 的 entity 预览不读取 pose，必须显式把边界与模型比例换算到物理 GUI 坐标。 */
    @WrapMethod(method = "entity")
    private void youzaiworldcore$transformEntity(EntityRenderState state, float scale, Vector3fc translation,
            Quaternionfc rotation, Quaternionfc cameraRotation, int x0, int y0, int x1, int y1,
            Operation<Void> original) {
        if (YzuiViewport.renderingScreen() == null && GuiAnimationController.contentOpacity() >= 1f) {
            original.call(state, scale, translation, rotation, cameraRotation, x0, y0, x1, y1);
            return;
        }
        var pose = ((GuiGraphicsExtractor) (Object) this).pose();
        Vector2f first = pose.transformPosition(x0, y0, new Vector2f());
        Vector2f last = pose.transformPosition(x1, y1, new Vector2f());
        float viewScale = (float) Math.sqrt(Math.abs(pose.determinant()));
        original.call(state, scale * viewScale, translation, rotation, cameraRotation,
                Math.round(first.x), Math.round(first.y), Math.round(last.x), Math.round(last.y));
    }
}
