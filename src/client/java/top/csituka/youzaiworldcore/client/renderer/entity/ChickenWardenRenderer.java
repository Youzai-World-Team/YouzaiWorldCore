package top.csituka.youzaiworldcore.client.renderer.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.WardenRenderer;
import net.minecraft.client.renderer.entity.state.WardenRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.entity.monster.warden.Warden;
import top.csituka.youzaiworldcore.feature.ExperimentalFeatures;
import net.minecraft.world.phys.Vec3;

/**
 * 监守者渲染包装器
 * <p>
 * 直接继承 EntityRenderer 持有两个内部渲染器实例，避免 GeckoLib 内部状态类型冲突。
 * 所有渲染相关调用均委托给当前生效的内部渲染器。
 * </p>
 */
public class ChickenWardenRenderer extends net.minecraft.client.renderer.entity.EntityRenderer<Warden, WardenRenderState> {

    private final WardenRenderer vanillaRenderer;
    private final GeckoWardenRenderer geckoRenderer;

    public ChickenWardenRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.vanillaRenderer = new WardenRenderer(context);
        this.geckoRenderer = new GeckoWardenRenderer(context);
        // 继承原版的阴影半径
        this.shadowRadius = 0.9f;
    }

    @Override
    public WardenRenderState createRenderState() {
        return new WardenRenderState();
    }

    private boolean isChickenMode() {
        return ExperimentalFeatures.isEnabled("chicken_warden_model");
    }

    @Override
    public void extractRenderState(Warden entity, WardenRenderState renderState, float partialTick) {
        if (isChickenMode()) {
            geckoRenderer.extractRenderState(entity, renderState, partialTick);
        } else {
            vanillaRenderer.extractRenderState(entity, renderState, partialTick);
        }
    }

    @Override
    public void submit(WardenRenderState renderState, PoseStack poseStack,
                       SubmitNodeCollector renderTasks, CameraRenderState cameraState) {
        if (isChickenMode()) {
            geckoRenderer.submit(renderState, poseStack, renderTasks, cameraState);
        } else {
            vanillaRenderer.submit(renderState, poseStack, renderTasks, cameraState);
        }
    }

    // ===== 以下方法委托给当前渲染器以确保正确的渲染位置和剔除 =====

    @Override
    public boolean shouldRender(Warden entity, Frustum frustum, double x, double y, double z) {
        if (isChickenMode()) {
            return geckoRenderer.shouldRender(entity, frustum, x, y, z);
        }
        return vanillaRenderer.shouldRender(entity, frustum, x, y, z);
    }

    @Override
    public Vec3 getRenderOffset(WardenRenderState renderState) {
        if (isChickenMode()) {
            // GeckoLib 鸡模型需要下移 0.5 格以对齐地面
            return geckoRenderer.getRenderOffset(renderState).add(0, -0.5, 0);
        }
        return vanillaRenderer.getRenderOffset(renderState);
    }

    @Override
    protected float getShadowRadius(WardenRenderState renderState) {
        return shadowRadius;
    }
}
