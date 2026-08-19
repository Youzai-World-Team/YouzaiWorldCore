package top.csituka.youzaiworldcore.client.renderer.block;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer.CrumblingOverlay;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.phys.Vec3;
import top.csituka.youzaiworldcore.block.DuplicateBlock;
import top.csituka.youzaiworldcore.block.entity.DuplicateBlockEntity;
import top.csituka.youzaiworldcore.util.DebugLogger;

/**
 * 复制方块客户端渲染器。
 * <p>
 * 普通态在方块正上方显示一张始终朝向相机的 2D 指示贴图；
 * 激活态不显示该贴图。
 */
@SuppressWarnings("null")
public class DuplicateBlockEntityRenderer
        implements BlockEntityRenderer<DuplicateBlockEntity, DuplicateBlockEntityRenderState> {

    private static final Identifier INDICATOR_TEXTURE = Identifier.fromNamespaceAndPath(
            "youzaiworldcore", "textures/gui/duplicate_block.png");

    // 原图尺寸 21x48，保持比例；图标底部贴近复制方块顶面。
    private static final float INDICATOR_WIDTH = 0.35f;
    private static final float INDICATOR_HEIGHT = INDICATOR_WIDTH * 48.0f / 21.0f;
    private static final float INDICATOR_BOTTOM = 1.015f;

    public DuplicateBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
        DebugLogger.info("DuplicateBlockRenderer", "复制方块 2D 指示贴图渲染器已创建");
    }

    @Override
    public DuplicateBlockEntityRenderState createRenderState() {
        return new DuplicateBlockEntityRenderState();
    }

    @Override
    public void extractRenderState(DuplicateBlockEntity entity,
                                   DuplicateBlockEntityRenderState state,
                                   float tickProgress,
                                   Vec3 cameraPos,
                                   CrumblingOverlay crumblingOverlay) {
        BlockEntityRenderer.super.extractRenderState(entity, state, tickProgress, cameraPos, crumblingOverlay);
        state.setActive(entity.getBlockState().getValue(DuplicateBlock.ACTIVE));
    }

    @Override
    public void submit(DuplicateBlockEntityRenderState state,
                       PoseStack matrices,
                       SubmitNodeCollector queue,
                       CameraRenderState cameraState) {
        if (state.isActive() || cameraState.orientation == null) {
            return;
        }

        matrices.pushPose();
        matrices.translate(0.5f, INDICATOR_BOTTOM, 0.5f);
        matrices.mulPose(cameraState.orientation);
        matrices.scale(INDICATOR_WIDTH, INDICATOR_HEIGHT, INDICATOR_WIDTH);

        queue.submitCustomGeometry(matrices, RenderTypes.entityCutout(INDICATOR_TEXTURE), (pose, consumer) -> {
            emitQuad(consumer, pose, false);
            emitQuad(consumer, pose, true);
        });

        matrices.popPose();
    }

    private static void emitQuad(VertexConsumer consumer, PoseStack.Pose pose, boolean reverse) {
        if (reverse) {
            vertex(consumer, pose, 0.5f, 0.0f, 0.0f, 1.0f, 1.0f);
            vertex(consumer, pose, -0.5f, 0.0f, 0.0f, 0.0f, 1.0f);
            vertex(consumer, pose, -0.5f, 1.0f, 0.0f, 0.0f, 0.0f);
            vertex(consumer, pose, 0.5f, 1.0f, 0.0f, 1.0f, 0.0f);
            return;
        }

        vertex(consumer, pose, -0.5f, 0.0f, 0.0f, 0.0f, 1.0f);
        vertex(consumer, pose, 0.5f, 0.0f, 0.0f, 1.0f, 1.0f);
        vertex(consumer, pose, 0.5f, 1.0f, 0.0f, 1.0f, 0.0f);
        vertex(consumer, pose, -0.5f, 1.0f, 0.0f, 0.0f, 0.0f);
    }

    private static void vertex(VertexConsumer consumer, PoseStack.Pose pose,
                               float x, float y, float z, float u, float v) {
        consumer.addVertex(pose, x, y, z)
                .setColor(255, 255, 255, 255)
                .setUv(u, v)
                .setUv1(0, 10)
                .setLight(LightCoordsUtil.FULL_BRIGHT)
                .setNormal(0.0f, 0.0f, 1.0f);
    }
}
