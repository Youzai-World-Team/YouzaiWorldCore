package top.csituka.youzaiworldcore.client.renderer.entity.layer;

import com.geckolib.cache.model.BakedGeoModel;
import com.geckolib.renderer.base.GeoRenderer;
import com.geckolib.renderer.base.RenderPassInfo;
import com.geckolib.renderer.layer.GeoRenderLayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import top.csituka.youzaiworldcore.client.renderer.entity.ChickenWardenAnimatable;
import net.minecraft.client.renderer.entity.state.WardenRenderState;

/**
 * 生物发光层 — 使用 warden_bioluminescent.png 以半透明方式叠加发光效果
 */
public class BioluminescentLayer extends GeoRenderLayer<ChickenWardenAnimatable, net.minecraft.world.entity.monster.warden.Warden, WardenRenderState> {

    private static final Identifier TEXTURE = Identifier.parse("youzaiworldcore:textures/entity/warden/warden_bioluminescent_layer.png");

    public BioluminescentLayer(GeoRenderer<ChickenWardenAnimatable, net.minecraft.world.entity.monster.warden.Warden, WardenRenderState> renderer) {
        super(renderer);
    }

    @Override
    public void submitRenderTask(RenderPassInfo<WardenRenderState> renderPassInfo, SubmitNodeCollector renderTasks) {
        var renderType = RenderTypes.entityTranslucentEmissive(TEXTURE);
        BakedGeoModel model = getDefaultBakedModel(renderPassInfo.renderState());
        int packedLight = renderPassInfo.packedLight();
        int packedOverlay = renderPassInfo.packedOverlay();
        int renderColor = renderPassInfo.renderColor();

        renderTasks.submitCustomGeometry(renderPassInfo.poseStack(), renderType, (pose, vertexConsumer) -> {
            renderPassInfo.poseStack().pushPose();
            renderPassInfo.poseStack().last().set(pose);
            renderPassInfo.renderPosed(() -> model.render(renderPassInfo, vertexConsumer, packedLight, packedOverlay, renderColor));
            renderPassInfo.poseStack().popPose();
        });
    }
}
