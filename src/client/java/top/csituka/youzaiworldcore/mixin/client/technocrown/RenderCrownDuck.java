package top.csituka.youzaiworldcore.mixin.client.technocrown;

/**
 * Duck interface for pig render state to control crown rendering.
 * <p>
 * Adapted from technomodel by thecolonel63 (MIT License).
 *
 * @see PigRenderStateMixin
 */
public interface RenderCrownDuck {
    boolean youzaiworldcore$shouldRenderCrown();

    void youzaiworldcore$setRenderCrown(boolean render);
}
