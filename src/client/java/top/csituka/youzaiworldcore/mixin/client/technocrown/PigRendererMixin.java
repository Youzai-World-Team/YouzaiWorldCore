package top.csituka.youzaiworldcore.mixin.client.technocrown;

import com.mojang.logging.LogUtils;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.animal.pig.BabyPigModel;
import net.minecraft.client.model.animal.pig.PigModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.PigRenderer;
import net.minecraft.client.renderer.entity.state.PigRenderState;
import net.minecraft.world.entity.animal.pig.Pig;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import top.csituka.youzaiworldcore.client.renderer.feature.TechnoCrownFeatureRenderer;
import top.csituka.youzaiworldcore.mixin.client.technocrown.RenderCrownDuck;

/**
 * Mixin into {@link PigRenderer} to:
 * <ol>
 *   <li>Register the {@link TechnoCrownFeatureRenderer} layer on construction.</li>
 *   <li>Compute the crown visibility each frame by checking whether the pig's
 *       custom name equals "Technoblade".</li>
 * </ol>
 * <p>
 * Adapted from technomodel by thecolonel63 (MIT License).
 */
@SuppressWarnings({"unchecked", "rawtypes"})
@Mixin(PigRenderer.class)
public abstract class PigRendererMixin extends LivingEntityRenderer {

    private static final Logger LOGGER = LogUtils.getLogger();

    public PigRendererMixin(EntityRendererProvider.Context ctx, EntityModel model, float shadowRadius) {
        super(ctx, model, shadowRadius);
    }

    /**
     * Injects at the tail of the {@code PigRenderer(Context)} constructor to
     * attach the Technoblade crown feature renderer.
     */
    @Inject(method = "<init>(Lnet/minecraft/client/renderer/entity/EntityRendererProvider$Context;)V",
            at = @At("TAIL"))
    private void addTechnoCrownFeature(EntityRendererProvider.Context context, CallbackInfo ci) {
        this.addLayer(new TechnoCrownFeatureRenderer<PigRenderState, PigModel, PigModel>(
                this,
                new PigModel(context.bakeLayer(ModelLayers.PIG_SADDLE)),
                new BabyPigModel(context.bakeLayer(ModelLayers.PIG_BABY))
        ));
        LOGGER.debug("[YouzaiWorldCore] TechnoCrownFeatureRenderer added to PigRenderer");
    }

    /**
     * Injects at the tail of {@code extractRenderState(Pig, PigRenderState, float)}
     * to set the crown render flag based on the pig's custom name.
     */
    @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/animal/pig/Pig;"
            + "Lnet/minecraft/client/renderer/entity/state/PigRenderState;F)V",
            at = @At("TAIL"))
    private void onUpdateRenderState(Pig pigEntity, PigRenderState pigEntityRenderState, float f, CallbackInfo ci) {
        boolean shouldRender = pigEntity.getName().getString().equals("Technoblade");
        ((RenderCrownDuck) pigEntityRenderState).youzaiworldcore$setRenderCrown(shouldRender);
        if (shouldRender) {
            LOGGER.debug("[YouzaiWorldCore] Detected Technoblade pig, crown render flag = true");
        }
    }
}
