package top.csituka.youzaiworldcore.mixin.client.technocrown;

import net.minecraft.client.renderer.entity.state.PigRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/**
 * Mixin that injects a {@link RenderCrownDuck} boolean field into {@link PigRenderState},
 * allowing the crown feature renderer to determine whether the pig's crown should be drawn.
 * <p>
 * Adapted from technomodel by thecolonel63 (MIT License).
 */
@Mixin(PigRenderState.class)
public class PigRenderStateMixin implements RenderCrownDuck {

    @Unique
    private boolean youzaiworldcore$shouldRenderCrown = false;

    @Override
    public boolean youzaiworldcore$shouldRenderCrown() {
        return this.youzaiworldcore$shouldRenderCrown;
    }

    @Override
    public void youzaiworldcore$setRenderCrown(boolean render) {
        this.youzaiworldcore$shouldRenderCrown = render;
    }
}
