package top.csituka.youzaiworldcore.client.renderer.block;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import top.csituka.youzaiworldcore.block.entity.FlyBeaconBlockEntity;

/**
 * 飞行信标的自定义 BlockEntityRenderer（Minecraft 1.21.5 新渲染系统）。
 * 当信标激活时，渲染一个带有斜条纹的圆柱形生效边界，直接通过 VertexConsumer
 * 渲染，不使用粒子效果。
 */
public class FlyBeaconBlockEntityRenderer implements BlockEntityRenderer<FlyBeaconBlockEntity, FlyBeaconBlockEntityRenderState> {

    /** 边界半径（方块），与 FlyBeaconTickHandler.BEACON_RADIUS 保持一致 */
    private static final float RADIUS = 10.0f;

    /** 圆柱面分段数，越大越圆滑 */
    private static final int SEGMENTS = 128;

    /** 边界底部相对信标 Y 的偏移 */
    private static final float HEIGHT_BOTTOM = -1.0f;

    /** 边界顶部相对信标 Y 的偏移 */
    private static final float HEIGHT_TOP = 10.0f;

    /** 圆周方向的条纹数量 */
    private static final int STRIPE_COUNT = 24;

    /** 斜条纹的倾斜系数：值越大，条纹越倾斜 */
    private static final float STRIPE_SLANT = 0.4f;

    // ---- 条纹配色（RGBA，0-255） ----

    /** 条纹颜色 A：半透明金色 */
    private static final int CA_R = 255;
    private static final int CA_G = 215;
    private static final int CA_B = 0;
    private static final int CA_A = 170;

    /** 条纹颜色 B：半透明天蓝色 */
    private static final int CB_R = 0;
    private static final int CB_G = 191;
    private static final int CB_B = 255;
    private static final int CB_A = 170;

    public FlyBeaconBlockEntityRenderer(BlockEntityRendererProvider.Context ctx) {
    }

    // ---- RenderState 生命周期 ----

    @Override
    public FlyBeaconBlockEntityRenderState createRenderState() {
        return new FlyBeaconBlockEntityRenderState();
    }

    @Override
    public void extractRenderState(FlyBeaconBlockEntity entity, FlyBeaconBlockEntityRenderState state,
                                    float tickProgress, Vec3 cameraPos,
                                    net.minecraft.client.renderer.feature.ModelFeatureRenderer.CrumblingOverlay crumblingOverlay) {
        BlockEntityRenderer.super.extractRenderState(entity, state, tickProgress, cameraPos, crumblingOverlay);
        // 26.2 的 getUpdateTag() 不再自动同步 BE 字段，
        // 必须从已同步的 blockstate 读取 ACTIVE 属性
        state.setActive(entity.getBlockState().getValue(top.csituka.youzaiworldcore.block.FlyBeaconBlock.ACTIVE));
    }

    // ---- 渲染入口 ----

    @Override
    public void submit(FlyBeaconBlockEntityRenderState state, PoseStack matrices,
                       SubmitNodeCollector queue, CameraRenderState cameraState) {
        if (!state.isActive()) {
            return;
        }

        matrices.pushPose();

        // 将坐标原点平移到信标方块中心
        matrices.translate(0.5f, 0.0f, 0.5f);

        // 使用与闪电相同的半透明管线（POSITION_COLOR + QUADS + 混合）
        queue.submitCustomGeometry(matrices, RenderTypes.lightning(), (pose, consumer) -> {
            // 时间偏移用于条纹缓慢旋转动画
            float timeOffset = (System.currentTimeMillis() % 100000L) * 0.00006f;

            for (int i = 0; i < SEGMENTS; i++) {
                float angle1 = (float) i / SEGMENTS * Mth.TWO_PI;
                float angle2 = (float) (i + 1) / SEGMENTS * Mth.TWO_PI;

                float cos1 = Mth.cos(angle1);
                float sin1 = Mth.sin(angle1);
                float cos2 = Mth.cos(angle2);
                float sin2 = Mth.sin(angle2);

                float x1 = cos1 * RADIUS;
                float z1 = sin1 * RADIUS;
                float x2 = cos2 * RADIUS;
                float z2 = sin2 * RADIUS;

                // 斜条纹判定：角度 + 高度 × 倾斜系数 + 时间偏移
                int stripeBot1 = stripeIndex(angle1, HEIGHT_BOTTOM, timeOffset);
                int stripeBot2 = stripeIndex(angle2, HEIGHT_BOTTOM, timeOffset);
                int stripeTop1 = stripeIndex(angle1, HEIGHT_TOP, timeOffset);
                int stripeTop2 = stripeIndex(angle2, HEIGHT_TOP, timeOffset);

                // 外侧四边形（逆时针：v1→v2→v3→v4）
                emitQuad(consumer, pose,
                        x1, HEIGHT_BOTTOM, z1, stripeBot1,
                        x2, HEIGHT_BOTTOM, z2, stripeBot2,
                        x2, HEIGHT_TOP, z2, stripeTop2,
                        x1, HEIGHT_TOP, z1, stripeTop1);

                // 内侧四边形（顺时针，可见于圆柱内部）
                emitQuad(consumer, pose,
                        x2, HEIGHT_BOTTOM, z2, stripeBot2,
                        x1, HEIGHT_BOTTOM, z1, stripeBot1,
                        x1, HEIGHT_TOP, z1, stripeTop1,
                        x2, HEIGHT_TOP, z2, stripeTop2);
            }
        });

        matrices.popPose();
    }

    @Override
    public boolean shouldRenderOffScreen() {
        // 边界半径较大，需要始终渲染以避免在边界进入视野时被剔除
        return true;
    }

    @Override
    public int getViewDistance() {
        return 256;
    }

    // ---- 内部辅助方法 ----

    /** 计算顶点所属的条纹索引（0 或 1） */
    private static int stripeIndex(float angle, float height, float timeOffset) {
        float raw = (angle / Mth.TWO_PI + height * STRIPE_SLANT + timeOffset) * STRIPE_COUNT;
        return Math.floorMod((int) Math.floor(raw), 2);
    }

    /** 输出一个四边形（4 个顶点，QUADS 模式） */
    private static void emitQuad(VertexConsumer consumer, PoseStack.Pose pose,
                                  float x1, float y1, float z1, int si1,
                                  float x2, float y2, float z2, int si2,
                                  float x3, float y3, float z3, int si3,
                                  float x4, float y4, float z4, int si4) {
        putVertex(consumer, pose, x1, y1, z1, si1);
        putVertex(consumer, pose, x2, y2, z2, si2);
        putVertex(consumer, pose, x3, y3, z3, si3);
        putVertex(consumer, pose, x4, y4, z4, si4);
    }

    /** 输出单个顶点（仅位置 + 颜色，适配 POSITION_COLOR 格式） */
    private static void putVertex(VertexConsumer consumer, PoseStack.Pose pose,
                                   float x, float y, float z, int stripeIndex) {
        int r, g, b, a;
        if (stripeIndex == 0) {
            r = CA_R; g = CA_G; b = CA_B; a = CA_A;
        } else {
            r = CB_R; g = CB_G; b = CB_B; a = CB_A;
        }
        consumer.addVertex(pose, x, y, z).setColor(r, g, b, a);
    }
}
