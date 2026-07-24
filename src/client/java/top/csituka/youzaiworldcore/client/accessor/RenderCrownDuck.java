package top.csituka.youzaiworldcore.client.accessor;

/**
 * Duck interface for pig render state to control crown rendering.
 * <p>
 * 注意：此接口位于 Mixin 保护包之外，允许非 Mixin 类直接引用。
 * 原位置 {@code mixin.client.technocrown} 因 Mixin 0.8.7 的
 * {@code IllegalClassLoadError} 导致外部引用失败（如 {@code TechnoCrownFeatureRenderer}）。
 * </p>
 * <p>
 * Adapted from technomodel by thecolonel63 (MIT License).
 *
 * @see top.csituka.youzaiworldcore.mixin.client.technocrown.PigRenderStateMixin
 */
public interface RenderCrownDuck {
    boolean youzaiworldcore$shouldRenderCrown();

    void youzaiworldcore$setRenderCrown(boolean render);
}
