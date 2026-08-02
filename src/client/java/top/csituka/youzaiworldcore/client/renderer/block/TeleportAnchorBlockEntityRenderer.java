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
import top.csituka.youzaiworldcore.util.DebugLogger;

/**
 * 传送锚点的 BlockEntityRenderer — 以石柱 (Pillar) 造型渲染方块。
 * <p>
 * 几何体由 4 个长方体组成（自下而上）：
 * <ol>
 *   <li>底座 (Plinth)：满格 16x16x4</li>
 *   <li>柱身 (Shaft)：等宽 8x20x8，贯穿第1格到第2格</li>
 *   <li>柱头 (Capital)：略宽装饰段 10x3x10</li>
 *   <li>顶饰 (Abacus)：顶部收口 8x5x8</li>
 * </ol>
 * 渲染时根据当前客户端玩家是否激活此锚点切换纹理（tp_anchor.png / tp_anchor_active.png），
 * 实现每个玩家眼中锚点激活状态不同的效果。
 * 石柱总高 2.0 格（32/16），底座满格覆盖完整方块底面。
 *
 * <p>贴图采用<b>展开图布局</b>（画布 <b>128×128</b>，y 自顶向下，UV = 像素 / 128；
 * 展开图按 <b>2 贴图像素 = 1 方块像素</b> 绘制）。纵向自上而下依次为：
 * <pre>
 *   顶饰行 y  0– 10   4 面 × 16px   （= 方块 8 宽 × 5 高）
 *   柱头行 y 10– 16   4 面 × 20px   （= 方块 10 宽 × 3 高）
 *   柱身行 y 16– 56   4 面 × 16px   （= 方块 8 宽 × 20 高）
 *   底座行 y 56– 64   4 面 × 32px   （= 方块 16 宽 × 4 高）
 *   顶/底共用区 x0–32, y64–96       （= 方块 16 × 16）
 * </pre>
 * 每行横向按 北/南/西/东 排列 4 个侧面。每个面的 UV 区域与几何面等比例，因此游戏内画面无拉伸——
 * 这要求几何体的 XZ 尺寸必须与展开图各行的面宽一致（顶饰 8 / 柱头 10 / 柱身 8 / 底座 16）。
 */
@SuppressWarnings("null")
public class TeleportAnchorBlockEntityRenderer
        implements BlockEntityRenderer<TeleportAnchorBlockEntity, TeleportAnchorBlockEntityRenderState> {

    private static final Identifier TEXTURE_INACTIVE = Identifier.fromNamespaceAndPath(
            "youzaiworldcore", "textures/block/tp_anchor.png");
    private static final Identifier TEXTURE_ACTIVE = Identifier.fromNamespaceAndPath(
            "youzaiworldcore", "textures/block/tp_anchor_active.png");

    static {
        DebugLogger.info("TeleportAnchorRenderer",
                "展开图 UV 布局已启用：顶饰行 V0-0.0781 / 柱头行 V0.0781-0.125 / "
                        + "柱身行 V0.125-0.4375 / 底座行 V0.4375-0.5 / 顶底面共用 (U0-0.25, V0.5-0.75)");
    }

    /** 展开图布局中单个面的 UV 区域（归一化 0~1，V=0 对应贴图顶部） */
    private record FaceUv(float uMin, float vMin, float uMax, float vMax) {}

    /** 顶/底面共用区域（贴图 x0–32, y64–96），各部件顶底均采样此处 */
    private static final FaceUv TOP_BOTTOM_UV = new FaceUv(0.00f, 0.50f, 0.25f, 0.75f);

    // ---- 展开图布局常量（贴图 128×128，y 自顶向下，UV = 像素 / 128）----
    // 顶饰 Abacus：y 0–10（V 0→0.078125），面宽 16px（U 0.125/面）
    private static final FaceUv ABACUS_NORTH = new FaceUv(0.0000f, 0.0000f, 0.1250f, 0.078125f);
    private static final FaceUv ABACUS_SOUTH = new FaceUv(0.1250f, 0.0000f, 0.2500f, 0.078125f);
    private static final FaceUv ABACUS_WEST  = new FaceUv(0.2500f, 0.0000f, 0.3750f, 0.078125f);
    private static final FaceUv ABACUS_EAST  = new FaceUv(0.3750f, 0.0000f, 0.5000f, 0.078125f);

    // 柱头 Capital：y 10–16（V 0.078125→0.125），面宽 20px（U 0.15625/面）
    private static final FaceUv CAPITAL_NORTH = new FaceUv(0.00000f, 0.078125f, 0.15625f, 0.1250f);
    private static final FaceUv CAPITAL_SOUTH = new FaceUv(0.15625f, 0.078125f, 0.31250f, 0.1250f);
    private static final FaceUv CAPITAL_WEST  = new FaceUv(0.31250f, 0.078125f, 0.46875f, 0.1250f);
    private static final FaceUv CAPITAL_EAST  = new FaceUv(0.46875f, 0.078125f, 0.62500f, 0.1250f);

    // 柱身 Shaft：y 16–56（V 0.125→0.4375），面宽 16px（U 0.125/面）
    private static final FaceUv SHAFT_NORTH = new FaceUv(0.0000f, 0.1250f, 0.1250f, 0.4375f);
    private static final FaceUv SHAFT_SOUTH = new FaceUv(0.1250f, 0.1250f, 0.2500f, 0.4375f);
    private static final FaceUv SHAFT_WEST  = new FaceUv(0.2500f, 0.1250f, 0.3750f, 0.4375f);
    private static final FaceUv SHAFT_EAST  = new FaceUv(0.3750f, 0.1250f, 0.5000f, 0.4375f);

    // 底座 Plinth：y 56–64（V 0.4375→0.5），面宽 32px（U 0.25/面）
    private static final FaceUv PLINTH_NORTH = new FaceUv(0.00f, 0.4375f, 0.25f, 0.50f);
    private static final FaceUv PLINTH_SOUTH = new FaceUv(0.25f, 0.4375f, 0.50f, 0.50f);
    private static final FaceUv PLINTH_WEST  = new FaceUv(0.50f, 0.4375f, 0.75f, 0.50f);
    private static final FaceUv PLINTH_EAST  = new FaceUv(0.75f, 0.4375f, 1.00f, 0.50f);

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
     * 绘制石柱的完整几何体（4 个长方体）。每个长方体使用展开图布局的独立 UV 区域：
     * <ul>
     *   <li>底座行 (V=0.4375 → 0.50)：满格底座，4 面横向各 32px（方块 16 宽）</li>
     *   <li>柱身行 (V=0.125 → 0.4375)：柱身主体，4 面横向各 16px（方块 8 宽）</li>
     *   <li>柱头行 (V=0.078125 → 0.125)：装饰柱头，4 面横向各 20px（方块 10 宽）</li>
     *   <li>顶饰行 (V=0 → 0.078125)：顶部收口，4 面横向各 16px（方块 8 宽）</li>
     *   <li>顶/底面共用区 (U0-0.25, V0.5-0.75)</li>
     * </ul>
     * 每面 UV 与几何面等比例，游戏内无拉伸。石柱总高 2.0 格，底座满格 16x16。
     */
    private static void addPillar(VertexConsumer consumer, PoseStack.Pose pose, int lu, int lv) {

        // 1. 底座 Plinth — 满格 [0, 0, 0] → [16, 4, 16]
        addBox(consumer, pose,
                0.0000f, 0.0000f, 0.0000f,
                1.0000f, 0.2500f, 1.0000f,
                lu, lv,
                PLINTH_NORTH, PLINTH_SOUTH, PLINTH_WEST, PLINTH_EAST,
                TOP_BOTTOM_UV, TOP_BOTTOM_UV);

        // 2. 柱身 Shaft — 等宽 [4, 4, 4] → [12, 24, 12]（8 宽，对齐展开图柱身行面宽）
        addBox(consumer, pose,
                0.2500f, 0.2500f, 0.2500f,
                0.7500f, 1.5000f, 0.7500f,
                lu, lv,
                SHAFT_NORTH, SHAFT_SOUTH, SHAFT_WEST, SHAFT_EAST,
                TOP_BOTTOM_UV, TOP_BOTTOM_UV);

        // 3. 柱头 Capital — [3, 24, 3] → [13, 27, 13]（10 宽，对齐展开图柱头行面宽）
        addBox(consumer, pose,
                0.1875f, 1.5000f, 0.1875f,
                0.8125f, 1.6875f, 0.8125f,
                lu, lv,
                CAPITAL_NORTH, CAPITAL_SOUTH, CAPITAL_WEST, CAPITAL_EAST,
                TOP_BOTTOM_UV, TOP_BOTTOM_UV);

        // 4. 顶饰 Abacus — [4, 27, 4] → [12, 32, 12]
        addBox(consumer, pose,
                0.2500f, 1.6875f, 0.2500f,
                0.7500f, 2.0000f, 0.7500f,
                lu, lv,
                ABACUS_NORTH, ABACUS_SOUTH, ABACUS_WEST, ABACUS_EAST,
                TOP_BOTTOM_UV, TOP_BOTTOM_UV);
    }

    /**
     * 绘制一个长方体的 6 个面，每个面使用独立的 UV 区域（展开图布局，与几何面等比例，无拉伸）。
     *
     * <p><b>顶点顺序与 UV 配对必须与原版 {@code FaceInfo} + {@code CuboidFace.UVs} 完全一致</b>，
     * 否则 BER 渲染出来的贴图会相对 JSON 模型（手持/物品栏）发生镜像或旋转。原版规则为
     * 顶点 v0=(uMin,vMin)、v1=(uMin,vMax)、v2=(uMax,vMax)、v3=(uMax,vMin)，各面 uMin 所在的世界方位：
     * <pre>
     *   north  uMin→+X(东)    vMin→+Y(上)
     *   south  uMin→-X(西)    vMin→+Y(上)
     *   west   uMin→-Z(北)    vMin→+Y(上)
     *   east   uMin→+Z(南)    vMin→+Y(上)
     *   down   uMin→-X(西)    vMin→+Z(南)
     *   up     uMin→-X(西)    vMin→-Z(北)
     * </pre>
     * 注意 down 与 up 的 vMin 方位相反，且 up/down 的 u 沿 X 轴、v 沿 Z 轴（不可对调）。
     *
     * @param north 北面 (z=z1) UV；@param south 南面 (z=z2) UV；@param west 西面 (x=x1) UV
     * @param east  东面 (x=x2) UV；@param down 底面 (y=y1) UV；@param up 顶面 (y=y2) UV
     */
    private static void addBox(VertexConsumer consumer, PoseStack.Pose pose,
                               float x1, float y1, float z1,
                               float x2, float y2, float z2,
                               int lu, int lv,
                               FaceUv north, FaceUv south, FaceUv west, FaceUv east,
                               FaceUv down, FaceUv up) {
        // 北面 (z=z1) 法线 -Z —— uMin 在 +X(东) 侧
        addFace(consumer, pose,
                x2, y2, z1,   x2, y1, z1,   x1, y1, z1,   x1, y2, z1,
                0, 0, -1, lu, lv,
                north.uMin, north.vMin,   north.uMin, north.vMax,   north.uMax, north.vMax,   north.uMax, north.vMin);
        // 南面 (z=z2) 法线 +Z —— uMin 在 -X(西) 侧
        addFace(consumer, pose,
                x1, y2, z2,   x1, y1, z2,   x2, y1, z2,   x2, y2, z2,
                0, 0, 1, lu, lv,
                south.uMin, south.vMin,   south.uMin, south.vMax,   south.uMax, south.vMax,   south.uMax, south.vMin);
        // 西面 (x=x1) 法线 -X —— uMin 在 -Z(北) 侧
        addFace(consumer, pose,
                x1, y2, z1,   x1, y1, z1,   x1, y1, z2,   x1, y2, z2,
                -1, 0, 0, lu, lv,
                west.uMin, west.vMin,   west.uMin, west.vMax,   west.uMax, west.vMax,   west.uMax, west.vMin);
        // 东面 (x=x2) 法线 +X —— uMin 在 +Z(南) 侧
        addFace(consumer, pose,
                x2, y2, z2,   x2, y1, z2,   x2, y1, z1,   x2, y2, z1,
                1, 0, 0, lu, lv,
                east.uMin, east.vMin,   east.uMin, east.vMax,   east.uMax, east.vMax,   east.uMax, east.vMin);
        // 底面 (y=y1) 法线 -Y —— uMin 在 -X(西) 侧，vMin 在 +Z(南) 侧
        addFace(consumer, pose,
                x1, y1, z2,   x1, y1, z1,   x2, y1, z1,   x2, y1, z2,
                0, -1, 0, lu, lv,
                down.uMin, down.vMin,   down.uMin, down.vMax,   down.uMax, down.vMax,   down.uMax, down.vMin);
        // 顶面 (y=y2) 法线 +Y —— uMin 在 -X(西) 侧，vMin 在 -Z(北) 侧
        addFace(consumer, pose,
                x1, y2, z1,   x1, y2, z2,   x2, y2, z2,   x2, y2, z1,
                0, 1, 0, lu, lv,
                up.uMin, up.vMin,   up.uMin, up.vMax,   up.uMax, up.vMax,   up.uMax, up.vMin);
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
