package top.csituka.youzaiworldcore.client.renderer.sign;

/** 为原版告示牌渲染状态补充正反面的闪烁透明度。 */
public interface FlashingSignRenderState {

    float youzaiworldcore$getFlashingAlpha(boolean front);

    void youzaiworldcore$setFlashingAlpha(boolean front, float alpha);
}
