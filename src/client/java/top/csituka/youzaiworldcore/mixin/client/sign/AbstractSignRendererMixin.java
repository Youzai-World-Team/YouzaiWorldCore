package top.csituka.youzaiworldcore.mixin.client.sign;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.AbstractSignRenderer;
import net.minecraft.client.renderer.blockentity.state.SignRenderState;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

import top.csituka.youzaiworldcore.client.renderer.sign.FlashingSignAnimation;
import top.csituka.youzaiworldcore.client.renderer.sign.FlashingSignRenderState;
import top.csituka.youzaiworldcore.sign.FlashingSign;

/** 为原版告示牌文字和描边增加平滑的淡入淡出动画。 */
@Mixin(AbstractSignRenderer.class)
public abstract class AbstractSignRendererMixin {

    @Unique
    private float youzaiworldcore$currentAlpha = 1.0f;

    @Inject(method = "extractRenderState(Lnet/minecraft/world/level/block/entity/SignBlockEntity;Lnet/minecraft/client/renderer/blockentity/state/SignRenderState;FLnet/minecraft/world/phys/Vec3;Lnet/minecraft/client/renderer/feature/ModelFeatureRenderer$CrumblingOverlay;)V", at = @At("TAIL"))
    private void youzaiworldcore$extractFlashingState(SignBlockEntity sign, SignRenderState state,
            float tickProgress, net.minecraft.world.phys.Vec3 cameraPos,
            net.minecraft.client.renderer.feature.ModelFeatureRenderer.CrumblingOverlay crumblingOverlay,
            CallbackInfo callbackInfo) {
        FlashingSignRenderState flashingState = (FlashingSignRenderState) state;
        if (!(sign instanceof FlashingSign flashingSign)) {
            flashingState.youzaiworldcore$setFlashingAlpha(true, 1.0f);
            flashingState.youzaiworldcore$setFlashingAlpha(false, 1.0f);
            return;
        }

        float alpha = youzaiworldcore$flashingAlpha(sign, flashingSign, tickProgress);
        flashingState.youzaiworldcore$setFlashingAlpha(true,
                flashingSign.youzaiworldcore$isFlashing(true) ? alpha : 1.0f);
        flashingState.youzaiworldcore$setFlashingAlpha(false,
                flashingSign.youzaiworldcore$isFlashing(false) ? alpha : 1.0f);
    }

    @Inject(method = "submitSignText", at = @At("HEAD"))
    private void youzaiworldcore$beginText(SignRenderState state, PoseStack matrices,
            SubmitNodeCollector queue, SignText text, CallbackInfo callbackInfo) {
        FlashingSignRenderState flashingState = (FlashingSignRenderState) state;
        if (text == state.frontText) {
            youzaiworldcore$currentAlpha = flashingState.youzaiworldcore$getFlashingAlpha(true);
        } else if (text == state.backText) {
            youzaiworldcore$currentAlpha = flashingState.youzaiworldcore$getFlashingAlpha(false);
        } else {
            youzaiworldcore$currentAlpha = 1.0f;
        }
    }

    @Inject(method = "submitSignText", at = @At("RETURN"))
    private void youzaiworldcore$endText(SignRenderState state, PoseStack matrices,
            SubmitNodeCollector queue, SignText text, CallbackInfo callbackInfo) {
        youzaiworldcore$currentAlpha = 1.0f;
    }

    @ModifyArgs(method = "submitSignText", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/SubmitNodeCollector;submitText(Lcom/mojang/blaze3d/vertex/PoseStack;FFLnet/minecraft/util/FormattedCharSequence;ZLnet/minecraft/client/gui/Font$DisplayMode;IIII)V"))
    private void youzaiworldcore$applyTextAlpha(Args args) {
        args.set(7, FlashingSignAnimation.applyAlpha(args.get(7), youzaiworldcore$currentAlpha));
        args.set(9, FlashingSignAnimation.applyAlpha(args.get(9), youzaiworldcore$currentAlpha));
    }

    @SuppressWarnings("null")
	@Unique
    private static float youzaiworldcore$flashingAlpha(SignBlockEntity sign,
            FlashingSign flashingSign, float tickProgress) {
        if ((!flashingSign.youzaiworldcore$isFlashing(true)
                && !flashingSign.youzaiworldcore$isFlashing(false))
                || sign.getLevel() == null) {
            return 1.0f;
        }
        return FlashingSignAnimation.alpha(sign.getLevel().getGameTime(), tickProgress);
    }
}
