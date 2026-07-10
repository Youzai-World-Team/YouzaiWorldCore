package top.csituka.youzaiworldcore.client.renderer.block;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.phys.Vec3;
import top.csituka.youzaiworldcore.block.entity.FlyBeaconBlockEntity;

/**
 * 飞行信标的 BlockEntityRenderer — 激活时在信标周围渲染一个方形蓝色半透明边界。
 * 边界从信标略下方开始，无限向上延伸（渲染到极高处以模拟无限）。
 * 使用 submitCustomGeometry + lightning 管线，不使用粒子效果。
 */
public class FlyBeaconBlockEntityRenderer implements BlockEntityRenderer<FlyBeaconBlockEntity, FlyBeaconBlockEntityRenderState> {

    /** 边界水平半宽（方块）。比 BEACON_RADIUS(10) 少半格外扩一像素 */
    private static final float HALF = 9.5f + 0.0625f;

    /** 边界底部相对信标 Y 的偏移 */
    private static final float HEIGHT_BOTTOM = -0.0625f;

    /** 边界顶部相对信标 Y — 极高值模拟无限向上 */
    private static final float HEIGHT_TOP = 1024.0f;

    // ---- 单色蓝色（RGBA，0-255） ----
    private static final int R = 30;
    private static final int G = 144;
    private static final int B = 255;
    private static final int A = 140;

    public FlyBeaconBlockEntityRenderer(BlockEntityRendererProvider.Context ctx) {
    }

    // ---- RenderState ----

    @Override
    public FlyBeaconBlockEntityRenderState createRenderState() {
        return new FlyBeaconBlockEntityRenderState();
    }

    @Override
    public void extractRenderState(FlyBeaconBlockEntity entity, FlyBeaconBlockEntityRenderState state,
                                    float tickProgress, Vec3 cameraPos,
                                    net.minecraft.client.renderer.feature.ModelFeatureRenderer.CrumblingOverlay crumblingOverlay) {
        BlockEntityRenderer.super.extractRenderState(entity, state, tickProgress, cameraPos, crumblingOverlay);
        state.setActive(entity.getBlockState().getValue(top.csituka.youzaiworldcore.block.FlyBeaconBlock.ACTIVE));
    }

    // ---- 渲染 ----

    @Override
    public void submit(FlyBeaconBlockEntityRenderState state, PoseStack matrices,
                       SubmitNodeCollector queue, CameraRenderState cameraState) {
        if (!state.isActive()) {
            return;
        }

        matrices.pushPose();
        matrices.translate(0.5f, 0.0f, 0.5f);

        queue.submitCustomGeometry(matrices, RenderTypes.lightning(), (pose, consumer) -> {
            // 方形边界底部四角：(-H,-H), (+H,-H), (+H,+H), (-H,+H)

            // --- +Z 面 (z = +HALF) ---
            emitQuad(consumer, pose,
                    -HALF, HEIGHT_BOTTOM, HALF,
                     HALF, HEIGHT_BOTTOM, HALF,
                     HALF, HEIGHT_TOP,    HALF,
                    -HALF, HEIGHT_TOP,    HALF);
            emitQuad(consumer, pose, // 内侧
                     HALF, HEIGHT_BOTTOM, HALF,
                    -HALF, HEIGHT_BOTTOM, HALF,
                    -HALF, HEIGHT_TOP,    HALF,
                     HALF, HEIGHT_TOP,    HALF);

            // --- -Z 面 (z = -HALF) ---
            emitQuad(consumer, pose,
                     HALF, HEIGHT_BOTTOM, -HALF,
                    -HALF, HEIGHT_BOTTOM, -HALF,
                    -HALF, HEIGHT_TOP,    -HALF,
                     HALF, HEIGHT_TOP,    -HALF);
            emitQuad(consumer, pose, // 内侧
                    -HALF, HEIGHT_BOTTOM, -HALF,
                     HALF, HEIGHT_BOTTOM, -HALF,
                     HALF, HEIGHT_TOP,    -HALF,
                    -HALF, HEIGHT_TOP,    -HALF);

            // --- +X 面 (x = +HALF) ---
            emitQuad(consumer, pose,
                     HALF, HEIGHT_BOTTOM, -HALF,
                     HALF, HEIGHT_BOTTOM,  HALF,
                     HALF, HEIGHT_TOP,     HALF,
                     HALF, HEIGHT_TOP,    -HALF);
            emitQuad(consumer, pose, // 内侧
                     HALF, HEIGHT_BOTTOM,  HALF,
                     HALF, HEIGHT_BOTTOM, -HALF,
                     HALF, HEIGHT_TOP,    -HALF,
                     HALF, HEIGHT_TOP,     HALF);

            // --- -X 面 (x = -HALF) ---
            emitQuad(consumer, pose,
                    -HALF, HEIGHT_BOTTOM,  HALF,
                    -HALF, HEIGHT_BOTTOM, -HALF,
                    -HALF, HEIGHT_TOP,    -HALF,
                    -HALF, HEIGHT_TOP,     HALF);
            emitQuad(consumer, pose, // 内侧
                    -HALF, HEIGHT_BOTTOM, -HALF,
                    -HALF, HEIGHT_BOTTOM,  HALF,
                    -HALF, HEIGHT_TOP,     HALF,
                    -HALF, HEIGHT_TOP,    -HALF);

            // --- 底部封口 (y = HEIGHT_BOTTOM)，标记生效区域下限 ---
            // 从上方可见（法线朝上 +Y）
            emitQuad(consumer, pose,
                    -HALF, HEIGHT_BOTTOM, -HALF,
                     HALF, HEIGHT_BOTTOM, -HALF,
                     HALF, HEIGHT_BOTTOM,  HALF,
                    -HALF, HEIGHT_BOTTOM,  HALF);
            // 从下方可见（法线朝下 -Y）
            emitQuad(consumer, pose,
                    -HALF, HEIGHT_BOTTOM,  HALF,
                     HALF, HEIGHT_BOTTOM,  HALF,
                     HALF, HEIGHT_BOTTOM, -HALF,
                    -HALF, HEIGHT_BOTTOM, -HALF);
        });

        matrices.popPose();
    }

    @Override
    public boolean shouldRenderOffScreen() {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 256;
    }

    // ---- 辅助 ----

    /** 输出一个四边形（4 个顶点，QUADS 模式），全部使用单色蓝色 */
    private static void emitQuad(VertexConsumer consumer, PoseStack.Pose pose,
                                  float x1, float y1, float z1,
                                  float x2, float y2, float z2,
                                  float x3, float y3, float z3,
                                  float x4, float y4, float z4) {
        putVertex(consumer, pose, x1, y1, z1);
        putVertex(consumer, pose, x2, y2, z2);
        putVertex(consumer, pose, x3, y3, z3);
        putVertex(consumer, pose, x4, y4, z4);
    }

    private static void putVertex(VertexConsumer consumer, PoseStack.Pose pose,
                                   float x, float y, float z) {
        consumer.addVertex(pose, x, y, z).setColor(R, G, B, A);
    }
}
