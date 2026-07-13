package top.csituka.youzaiworldcore.client.renderer.block;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer.CrumblingOverlay;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import top.csituka.youzaiworldcore.block.entity.TeleportAnchorBlockEntity;

/**
 * 传送锚点的 BlockEntityRenderer — 根据当前客户端玩家是否激活了此锚点，
 * 渲染不同的纹理（tp_anchor.png / tp_anchor_active.png），
 * 实现每个玩家眼中锚点激活状态不同的效果。
 */
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

        // 无论激活与否，一律使用自发光着色器，锚点始终发光
        // （每个玩家看到的激活/未激活贴图不同，但亮度相同）
        var renderType = RenderTypes.entityTranslucentEmissive(texture);

        // emissive 忽略光照值，随便填
        matrices.pushPose();
        queue.submitCustomGeometry(matrices, renderType, (pose, consumer) -> {
            addCube(consumer, pose, 0, 0);
        });
        matrices.popPose();
    }

    /**
     * 渲染一个单位立方体（从 0,0,0 到 1,1,1）。
     * 顶点按各面外侧视角的逆时针顺序排列，与 GL 正面剔除兼容。
     * 每个面共享整个纹理（UV 0,0 → 1,1）。
     *
     * @param lu lightmap U（块光照分量 * 16，0-240）
     * @param lv lightmap V（天光分量 * 16，0-240）
     */
    private static void addCube(VertexConsumer consumer, PoseStack.Pose pose, int lu, int lv) {

        // 每个面：4 个顶点 (按外侧 CCW)，法线指向外
        // UV 顺序：(0,0) (1,0) (1,1) (0,1)
        // 北面 (z=0) 法线 -Z
        addFace(consumer, pose, 0f, 1f, 0f,  1f, 1f, 0f,  1f, 0f, 0f,  0f, 0f, 0f,  0f, 0f, -1f, lu, lv);
        // 南面 (z=1) 法线 +Z
        addFace(consumer, pose, 1f, 1f, 1f,  0f, 1f, 1f,  0f, 0f, 1f,  1f, 0f, 1f,  0f, 0f, 1f, lu, lv);
        // 西面 (x=0) 法线 -X
        addFace(consumer, pose, 0f, 1f, 0f,  0f, 1f, 1f,  0f, 0f, 1f,  0f, 0f, 0f,  -1f, 0f, 0f, lu, lv);
        // 东面 (x=1) 法线 +X
        addFace(consumer, pose, 1f, 1f, 1f,  1f, 1f, 0f,  1f, 0f, 0f,  1f, 0f, 1f,  1f, 0f, 0f, lu, lv);
        // 底面 (y=0) 法线 -Y
        addFace(consumer, pose, 0f, 0f, 0f,  1f, 0f, 0f,  1f, 0f, 1f,  0f, 0f, 1f,  0f, -1f, 0f, lu, lv);
        // 顶面 (y=1) 法线 +Y
        addFace(consumer, pose, 0f, 1f, 0f,  1f, 1f, 0f,  1f, 1f, 1f,  0f, 1f, 1f,  0f, 1f, 0f, lu, lv);
    }

    /**
     * 发射一个四边形面：4 个顶点按外侧视角逆时针排列。
     * UV 分别为 (0,0), (1,0), (1,1), (0,1)。
     */
    @SuppressWarnings("null")
    private static void addFace(VertexConsumer consumer, PoseStack.Pose pose,
                                float x1, float y1, float z1,
                                float x2, float y2, float z2,
                                float x3, float y3, float z3,
                                float x4, float y4, float z4,
                                float nx, float ny, float nz,
                                int lu, int lv) {
        consumer.addVertex(pose, x1, y1, z1).setColor(255, 255, 255, 255).setUv(0, 0).setUv1(0, 10).setNormal(nx, ny, nz).setUv2(lu, lv);
        consumer.addVertex(pose, x2, y2, z2).setColor(255, 255, 255, 255).setUv(1, 0).setUv1(0, 10).setNormal(nx, ny, nz).setUv2(lu, lv);
        consumer.addVertex(pose, x3, y3, z3).setColor(255, 255, 255, 255).setUv(1, 1).setUv1(0, 10).setNormal(nx, ny, nz).setUv2(lu, lv);
        consumer.addVertex(pose, x4, y4, z4).setColor(255, 255, 255, 255).setUv(0, 1).setUv1(0, 10).setNormal(nx, ny, nz).setUv2(lu, lv);
    }
}
