package top.csituka.youzaiworldcore.client.renderer.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.WardenRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.WardenRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.entity.monster.warden.Warden;
import top.csituka.youzaiworldcore.feature.ExperimentalFeatures;

/**
 * 监守者渲染包装器
 * <p>
 * 不直接继承 GeoReplacedEntityRenderer（避免 GeckoLib 内部状态类型冲突），
 * 而是直接继承 EntityRenderer 并持有两个渲染器实例。
 * </p>
 */
public class ChickenWardenRenderer extends net.minecraft.client.renderer.entity.EntityRenderer<Warden, WardenRenderState> {

    private final WardenRenderer vanillaRenderer;
    private final GeckoWardenRenderer geckoRenderer;

    public ChickenWardenRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.vanillaRenderer = new WardenRenderer(context);
        this.geckoRenderer = new GeckoWardenRenderer(context);
    }

    @Override
    public WardenRenderState createRenderState() {
        return new WardenRenderState();
    }

    @Override
    public void extractRenderState(Warden entity, WardenRenderState renderState, float partialTick) {
        if (ExperimentalFeatures.isEnabled("chicken_warden_model")) {
            geckoRenderer.extractRenderState(entity, renderState, partialTick);
        } else {
            vanillaRenderer.extractRenderState(entity, renderState, partialTick);
        }
    }

    @Override
    public void submit(WardenRenderState renderState, PoseStack poseStack,
                       SubmitNodeCollector renderTasks, CameraRenderState cameraState) {
        if (ExperimentalFeatures.isEnabled("chicken_warden_model")) {
            geckoRenderer.submit(renderState, poseStack, renderTasks, cameraState);
        } else {
            vanillaRenderer.submit(renderState, poseStack, renderTasks, cameraState);
        }
    }
}
