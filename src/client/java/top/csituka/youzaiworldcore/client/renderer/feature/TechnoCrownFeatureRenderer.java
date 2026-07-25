package top.csituka.youzaiworldcore.client.renderer.feature;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.logging.LogUtils;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;

import top.csituka.youzaiworldcore.client.accessor.RenderCrownDuck;

/**
 * Feature renderer that draws a Technoblade crown on a pig when the pig's
 * custom name is "Technoblade".
 * <p>
 * Renders either an adult or baby crown model depending on {@code state.isBaby}.
 * <p>
 * Adapted from technomodel by thecolonel63 (MIT License).
 *
 * @param <S>  the living-entity render state type (typically {@code PigRenderState})
 * @param <RM> the parent renderer's entity model type
 * @param <EM> the crown model type (same as or compatible with the parent model)
 */
public class TechnoCrownFeatureRenderer<S extends LivingEntityRenderState, RM extends EntityModel<? super S>, EM extends EntityModel<? super S>>
        extends RenderLayer<S, RM> {

    private static final Logger LOGGER = LogUtils.getLogger();

    private final @NonNull Identifier TEXTURE_ADULT = Identifier.withDefaultNamespace(
            "textures/entity/pig/technocrown_adult.png");
    private final @NonNull Identifier TEXTURE_BABY = Identifier.withDefaultNamespace(
            "textures/entity/pig/technocrown_baby.png");

    private final EM adultModel;
    private final EM babyModel;

    public TechnoCrownFeatureRenderer(RenderLayerParent<S, RM> context, EM adultModel, EM babyModel) {
        super(context);
        this.adultModel = adultModel;
        this.babyModel = babyModel;
    }

    @Override
    @SuppressWarnings("null")
    public void submit(
            @NonNull PoseStack stack,
            @NonNull SubmitNodeCollector collector,
            int light,
            S state,
            float limbAngle,
            float limbDistance) {

        if (!((RenderCrownDuck) state).youzaiworldcore$shouldRenderCrown()) {
            return;
        }

        EM model = state.isBaby ? this.babyModel : this.adultModel;
        model.setupAnim(state);

        if (state.isBaby) {
            stack.translate(0.0, 1.0625, -0.25);
            stack.translate(0.0, -0.0625, 0.0);
            stack.scale(1.125f, 1.125f, 1.125f);
            stack.translate(0.0, -1.0625, 0.25);

            collector.submitModel(
                    model, state, stack,
                    RenderTypes.entityCutout(this.TEXTURE_BABY),
                    light, OverlayTexture.NO_OVERLAY,
                    state.outlineColor, null
            );
        } else {
            collector.submitModel(
                    model, state, stack,
                    RenderTypes.entityCutout(this.TEXTURE_ADULT),
                    light, OverlayTexture.NO_OVERLAY,
                    state.outlineColor, null
            );
        }

        LOGGER.debug("[YouzaiWorldCore] Rendered technocrown for pig, isBaby={}", state.isBaby);
    }

}
