package top.csituka.youzaiworldcore.mixin.client.sign;

import net.minecraft.client.renderer.blockentity.state.SignRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

import top.csituka.youzaiworldcore.client.renderer.sign.FlashingSignRenderState;

/** 在每个原版告示牌渲染快照中独立保存正反面动画透明度。 */
@Mixin(SignRenderState.class)
public abstract class SignRenderStateMixin implements FlashingSignRenderState {

    @Unique
    private float youzaiworldcore$frontFlashingAlpha = 1.0f;

    @Unique
    private float youzaiworldcore$backFlashingAlpha = 1.0f;

    @Override
    public float youzaiworldcore$getFlashingAlpha(boolean front) {
        return front ? youzaiworldcore$frontFlashingAlpha : youzaiworldcore$backFlashingAlpha;
    }

    @Override
    public void youzaiworldcore$setFlashingAlpha(boolean front, float alpha) {
        if (front) {
            youzaiworldcore$frontFlashingAlpha = alpha;
        } else {
            youzaiworldcore$backFlashingAlpha = alpha;
        }
    }
}
