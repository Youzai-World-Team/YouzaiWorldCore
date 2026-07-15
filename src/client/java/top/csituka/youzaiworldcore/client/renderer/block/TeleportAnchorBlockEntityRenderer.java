package top.csituka.youzaiworldcore.client.renderer.block;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer.CrumblingOverlay;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import top.csituka.youzaiworldcore.block.entity.TeleportAnchorBlockEntity;
import top.csituka.youzaiworldcore.client.renderer.CustomRenderTypes;

/**
 * 传送锚点的 BlockEntityRenderer — 以石柱 (Pillar) 造型渲染方块。
 * <p>
 * 几何体由 4 个长方体组成（自下而上）：
 * <ol>
 *   <li>底座 (Plinth)：满格 16x16x4</li>
 *   <li>柱身 (Shaft)：等宽 10x20x10，贯穿第1格到第2格</li>
 *   <li>柱头 (Capital)：略宽装饰段 12x3x12</li>
 *   <li>顶饰 (Abacus)：顶部收口 8x5x8</li>
 * </ol>
 * 渲染时根据当前客户端玩家是否激活此锚点切换纹理（tp_anchor.png / tp_anchor_active.png），
 * 实现每个玩家眼中锚点激活状态不同的效果。
 * 石柱总高 2.0 格（32/16），底座满格覆盖完整方块底面。
 */
@SuppressWarnings("null")
public class TeleportAnchorBlockEntityRenderer
        implements BlockEntityRenderer<TeleportAnchorBlockEntity, TeleportAnchorBlockEntityRenderState> {

    private static final Identifier TEXTURE_INACTIVE = Identifier.fromNamespaceAndPath(
            "youzaiworldcore", "textures/block/tp_anchor.png");
    private static final Identifier TEXTURE_ACTIVE = Identifier.fromNamespaceAndPath(
            "youzaiworldcore", "textures/block/tp_anchor_active.png");

    public TeleportAnchorBlockEntityRenderer(BlockEntityRendererProvider.Context ctx) {
    }

    // ---- RenderState ----

    @Override
    public TeleportAnchorBlockEntityRenderState createRenderState() {
        return new TeleportAnchorBlockEntityRenderState();
    }

    @Override
    public void extractRenderState(TeleportAnchorBlockEntity entity,
                                   TeleportAnchorBlockEntityRenderState state,
                                   float tickProgress, Vec3 cameraPos,
                                   CrumblingOverlay crumblingOverlay) {
        BlockEntityRenderer.super.extractRenderState(entity, state, tickProgress, cameraPos, crumblingOverlay);
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            state.setActivatedByMe(entity.isActivatedBy(mc.player.getUUID()));
        } else {
            state.setActivatedByMe(false);
        }
    }

    // ---- 渲染 ----

    @Override
    public void submit(TeleportAnchorBlockEntityRenderState state, PoseStack matrices,
                       SubmitNodeCollector queue, CameraRenderState cameraState) {
        boolean activated = state.isActivatedByMe();
        Identifier texture = activated ? TEXTURE_ACTIVE : TEXTURE_INACTIVE;

        var renderType = CustomRenderTypes.TP_ANCHOR.apply(texture);

        matrices.pushPose();
        queue.submitCustomGeometry(matrices, renderType, (pose, consumer) -> {
            addPillar(consumer, pose, 0, 10);
        });
        matrices.popPose();
    }

    // ---- 石柱几何体 ----

    /**
     * 绘制石柱的完整几何体（4 个长方体）。所有长方体共享同一纹理，
     * 通过不同的 UV 包围盒映射到纹理的不同区域：
     * <ul>
     *   <li>柱身纹理区 (V=0.20 → 0.80)：等宽柱身主体</li>
     *   <li>柱头纹理区 (V=0.08 → 0.20)：装饰柱头</li>
     *   <li>底座纹理区 (V=0.80 → 1.00)：底座底面</li>
     *   <li>顶饰纹理区 (V=0.00 → 0.08)：顶部收口</li>
     * </ul>
     * 石柱总高 2.0 格，底座满格 16x16。
     */
    private static void addPillar(VertexConsumer consumer, PoseStack.Pose pose, int lu, int lv) {

        final float PLINTH_U_MIN = 0.0f,  PLINTH_V_MIN = 0.80f, PLINTH_U_MAX = 1.0f, PLINTH_V_MAX = 1.00f;
        final float SHAFT_U_MIN  = 0.0f,  SHAFT_V_MIN  = 0.20f, SHAFT_U_MAX  = 1.0f, SHAFT_V_MAX  = 0.80f;
        final float CAPITAL_U_MIN = 0.0f, CAPITAL_V_MIN = 0.08f, CAPITAL_U_MAX = 1.0f, CAPITAL_V_MAX = 0.20f;
        final float ABACUS_U_MIN  = 0.0f, ABACUS_V_MIN  = 0.00f, ABACUS_U_MAX  = 1.0f, ABACUS_V_MAX  = 0.08f;

        // 1. 底座 Plinth — 满格 [0, 0, 0] → [16, 4, 16]
        addBox(consumer, pose,
                0.0000f, 0.0000f, 0.0000f,
                1.0000f, 0.2500f, 1.0000f,
                lu, lv,
                PLINTH_U_MIN, PLINTH_V_MIN, PLINTH_U_MAX, PLINTH_V_MAX);

        // 2. 柱身 Shaft — 等宽 [3, 4, 3] → [13, 24, 13]
        addBox(consumer, pose,
                0.1875f, 0.2500f, 0.1875f,
                0.8125f, 1.5000f, 0.8125f,
                lu, lv,
                SHAFT_U_MIN, SHAFT_V_MIN, SHAFT_U_MAX, SHAFT_V_MAX);

        // 3. 柱头 Capital — [2, 24, 2] → [14, 27, 14]
        addBox(consumer, pose,
                0.1250f, 1.5000f, 0.1250f,
                0.8750f, 1.6875f, 0.8750f,
                lu, lv,
                CAPITAL_U_MIN, CAPITAL_V_MIN, CAPITAL_U_MAX, CAPITAL_V_MAX);

        // 4. 顶饰 Abacus — [4, 27, 4] → [12, 32, 12]
        addBox(consumer, pose,
                0.2500f, 1.6875f, 0.2500f,
                0.7500f, 2.0000f, 0.7500f,
                lu, lv,
                ABACUS_U_MIN, ABACUS_V_MIN, ABACUS_U_MAX, ABACUS_V_MAX);
    }

    /**
     * 绘制一个长方体的 6 个面，每个面映射到给定 UV 包围盒 (uMin,vMin)→(uMax,vMax)。
     */
    private static void addBox(VertexConsumer consumer, PoseStack.Pose pose,
                               float x1, float y1, float z1,
                               float x2, float y2, float z2,
                               int lu, int lv,
                               float uMin, float vMin, float uMax, float vMax) {
        // 北面 (z=z1) 法线 -Z
        addFace(consumer, pose,
                x1, y2, z1,   x2, y2, z1,   x2, y1, z1,   x1, y1, z1,
                0, 0, -1, lu, lv,
                uMin, vMin,   uMax, vMin,   uMax, vMax,   uMin, vMax);
        // 南面 (z=z2) 法线 +Z
        addFace(consumer, pose,
                x2, y2, z2,   x1, y2, z2,   x1, y1, z2,   x2, y1, z2,
                0, 0, 1, lu, lv,
                uMin, vMin,   uMax, vMin,   uMax, vMax,   uMin, vMax);
        // 西面 (x=x1) 法线 -X
        addFace(consumer, pose,
                x1, y2, z2,   x1, y2, z1,   x1, y1, z1,   x1, y1, z2,
                -1, 0, 0, lu, lv,
                uMin, vMin,   uMax, vMin,   uMax, vMax,   uMin, vMax);
        // 东面 (x=x2) 法线 +X
        addFace(consumer, pose,
                x2, y2, z1,   x2, y2, z2,   x2, y1, z2,   x2, y1, z1,
                1, 0, 0, lu, lv,
                uMin, vMin,   uMax, vMin,   uMax, vMax,   uMin, vMax);
        // 底面 (y=y1) 法线 -Y
        addFace(consumer, pose,
                x1, y1, z1,   x2, y1, z1,   x2, y1, z2,   x1, y1, z2,
                0, -1, 0, lu, lv,
                uMin, vMin,   uMax, vMin,   uMax, vMax,   uMin, vMax);
        // 顶面 (y=y2) 法线 +Y
        addFace(consumer, pose,
                x1, y2, z1,   x1, y2, z2,   x2, y2, z2,   x2, y2, z1,
                0, 1, 0, lu, lv,
                uMin, vMin,   uMax, vMin,   uMax, vMax,   uMin, vMax);
    }

    @SuppressWarnings("null")
    private static void addFace(VertexConsumer consumer, PoseStack.Pose pose,
                                float x1, float y1, float z1,
                                float x2, float y2, float z2,
                                float x3, float y3, float z3,
                                float x4, float y4, float z4,
                                float nx, float ny, float nz,
                                int lu, int lv,
                                float u1, float v1,
                                float u2, float v2,
                                float u3, float v3,
                                float u4, float v4) {
        consumer.addVertex(pose, x1, y1, z1).setColor(255, 255, 255, 255)
                .setUv(u1, v1).setUv1(0, 10).setNormal(nx, ny, nz).setUv2(lu, lv);
        consumer.addVertex(pose, x2, y2, z2).setColor(255, 255, 255, 255)
                .setUv(u2, v2).setUv1(0, 10).setNormal(nx, ny, nz).setUv2(lu, lv);
        consumer.addVertex(pose, x3, y3, z3).setColor(255, 255, 255, 255)
                .setUv(u3, v3).setUv1(0, 10).setNormal(nx, ny, nz).setUv2(lu, lv);
        consumer.addVertex(pose, x4, y4, z4).setColor(255, 255, 255, 255)
                .setUv(u4, v4).setUv1(0, 10).setNormal(nx, ny, nz).setUv2(lu, lv);
    }
}
