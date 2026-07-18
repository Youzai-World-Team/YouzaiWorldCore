package top.csituka.youzaiworldcore.client.renderer.block;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import top.csituka.youzaiworldcore.block.FlyBeaconBlock;
import top.csituka.youzaiworldcore.block.entity.FlyBeaconBlockEntity;
import top.csituka.youzaiworldcore.util.DebugLogger;

import java.util.*;

/**
 * 飞行信标的 BlockEntityRenderer — 激活时在信标周围渲染一个方形蓝色半透明边界。
 * 支持多个信标区域重叠时执行「布尔并集」运算，仅保留外轮廓边界，消除内部多余线条。
 * <p>
 * 实现思路：
 * 1. {@link #extractRenderState} 阶段收集所有已加载飞行信标的激活状态与位置（缓存于实例 Map）。
 * 2. {@link #submit} 阶段对当前 BE 使用「区间减法」算法，只绘制未被其他 BE 正方形体覆盖的墙面段。
 * 3. 每个 BE 只绘制自己的外部贡献段，所有 BE 合起来形成完整的并集外轮廓，无重复绘制。
 */
public class FlyBeaconBlockEntityRenderer implements BlockEntityRenderer<FlyBeaconBlockEntity, FlyBeaconBlockEntityRenderState> {

    // ======================== 几何常量 ========================

    /** 边界水平半宽（方块），与 TickHandler 的 BEACON_HALF (9.5625 = 9 + 9/16) 保持一致 */
    private static final float HALF = 9.0f + 9.0f / 16.0f; // 9.5625

    /** 正方形半宽，用于计算 xmin/xmax/zmin/zmax */
    private static final double HALF_D = 9.0 + 9.0 / 16.0; // 9.5625 双精度版

    /** 中心偏移量 */
    private static final double CENTER_OFFSET = 0.5;

    /** 边界底部相对信标 Y 的偏移 */
    private static final float HEIGHT_BOTTOM = -0.0625f;

    /** 边界顶部相对信标 Y — 极高值模拟无限向上 */
    private static final float HEIGHT_TOP = 1024.0f;

    // ======================== 颜色常量（RGBA 0-255） ========================

    private static final int R = 30;
    private static final int G = 144;
    private static final int B = 255;
    private static final int A = 140;

    // ======================== 区间 / 几何内部类型 ========================

    /** XZ 平面上的轴对齐正方形 */
    private record Square(double xmin, double xmax, double zmin, double zmax) {}

    /** 可见墙面：isXAxis=true → x = coord 上的垂直墙面（z 从 start 到 end）；false → z = coord（x 从 start 到 end） */
    private record VisibleFace(boolean isXAxis, double coord, double start, double end) {}

    /** 一维闭区间 */
    private record Interval(double start, double end) implements Comparable<Interval> {
        @Override
        public int compareTo(Interval o) {
            return Double.compare(this.start, o.start);
        }
    }

    // ======================== 缓存状态（实例级，线程安全：MC 渲染主线程单线程调用） ========================

    /** 当前绘制帧中所有已加载信标的 [位置 → 是否激活]，在 extractRenderState 阶段填充 */
    private final Map<BlockPos, Boolean> collectedBeacons = new HashMap<>();

    /** 收集阶段是否已完成（submit 开始后置 true，下帧 extract 首个 BE 时重置）*/
    private boolean collectingDone = false;

    /** 当前帧是否已由某个 BE 打印了调试日志（防刷屏） */
    private boolean debugLoggedThisFrame = false;

    // ======================== 构造 ========================

    public FlyBeaconBlockEntityRenderer(BlockEntityRendererProvider.Context ctx) {
    }

    // ======================== RenderState ========================

    @Override
    public FlyBeaconBlockEntityRenderState createRenderState() {
        return new FlyBeaconBlockEntityRenderState();
    }

    @SuppressWarnings("null")
    @Override
    public void extractRenderState(FlyBeaconBlockEntity entity, FlyBeaconBlockEntityRenderState state,
                                    float tickProgress, Vec3 cameraPos,
                                    net.minecraft.client.renderer.feature.ModelFeatureRenderer.CrumblingOverlay crumblingOverlay) {
        BlockEntityRenderer.super.extractRenderState(entity, state, tickProgress, cameraPos, crumblingOverlay);

        BlockPos pos = entity.getBlockPos();
        boolean active = entity.getBlockState().getValue(FlyBeaconBlock.ACTIVE);

        state.setActive(active);
        state.setPos(pos);

        // 收集阶段的会计逻辑
        if (collectingDone) {
            // 新帧的第一个 extract — 重置收集状态
            collectedBeacons.clear();
            collectingDone = false;
            debugLoggedThisFrame = false;
        }
        collectedBeacons.put(pos, active);
    }

    // ======================== 渲染核心 ========================

    @SuppressWarnings("null")
    @Override
    public void submit(FlyBeaconBlockEntityRenderState state, PoseStack matrices,
                       SubmitNodeCollector queue, CameraRenderState cameraState) {
        if (!state.isActive()) {
            return;
        }

        collectingDone = true;

        // 获取当前 BE 的世界坐标
        BlockPos myPos = state.getPos();
        if (myPos == null) {
            return;
        }

        // 从收集的缓存中构建所有激活信标的 Square 列表（含当前信标）
        List<Square> allSquares = buildActiveSquares(collectedBeacons);

        if (allSquares.isEmpty()) {
            return;
        }

        // 计算当前信标正方形的可见墙面
        double beaconCenterX = myPos.getX() + CENTER_OFFSET;
        double beaconCenterZ = myPos.getZ() + CENTER_OFFSET;
        Square mySquare = new Square(
                beaconCenterX - HALF_D, beaconCenterX + HALF_D,
                beaconCenterZ - HALF_D, beaconCenterZ + HALF_D
        );

        List<VisibleFace> myVisibleFaces = computeVisibleFacesForSquare(mySquare, allSquares);

        if (myVisibleFaces.isEmpty()) {
            return;
        }

        if (!debugLoggedThisFrame) {
            debugLoggedThisFrame = true;
            DebugLogger.info("FlyBeaconRenderer", "BE [%d,%d,%d]: 共 %d 个激活信标，当前贡献 %d 个可见墙面段",
                    myPos.getX(), myPos.getY(), myPos.getZ(), allSquares.size(), myVisibleFaces.size());
        }

        // 渲染
        matrices.pushPose();
        // 将矩阵原点对齐方块中心（原版渲染约定）
        matrices.translate(CENTER_OFFSET, 0.0f, CENTER_OFFSET);

        queue.submitCustomGeometry(matrices, RenderTypes.lightning(), (pose, consumer) -> {
            // 1) 渲染可见墙面（侧面）
            for (VisibleFace face : myVisibleFaces) {
                // 坐标从世界空间转换到局部空间（减去当前 BE 的方块坐标）
                float localXStart = (float) (face.start() - myPos.getX());
                float localXEnd = (float) (face.end() - myPos.getX());
                float localZStart = (float) (face.start() - myPos.getZ());
                float localZEnd = (float) (face.end() - myPos.getZ());

                if (face.isXAxis()) {
                    // x = face.coord 上的垂直墙面，z 从 start 到 end
                    float localX = (float) (face.coord() - myPos.getX());
                    emitQuad(consumer, pose,
                            localX, HEIGHT_BOTTOM, localZStart,
                            localX, HEIGHT_BOTTOM, localZEnd,
                            localX, HEIGHT_TOP, localZEnd,
                            localX, HEIGHT_TOP, localZStart);
                    // 内侧（法线反向）
                    emitQuad(consumer, pose,
                            localX, HEIGHT_BOTTOM, localZEnd,
                            localX, HEIGHT_BOTTOM, localZStart,
                            localX, HEIGHT_TOP, localZStart,
                            localX, HEIGHT_TOP, localZEnd);
                } else {
                    // z = face.coord 上的垂直墙面，x 从 start 到 end
                    float localZ = (float) (face.coord() - myPos.getZ());
                    emitQuad(consumer, pose,
                            localXStart, HEIGHT_BOTTOM, localZ,
                            localXEnd, HEIGHT_BOTTOM, localZ,
                            localXEnd, HEIGHT_TOP, localZ,
                            localXStart, HEIGHT_TOP, localZ);
                    // 内侧
                    emitQuad(consumer, pose,
                            localXEnd, HEIGHT_BOTTOM, localZ,
                            localXStart, HEIGHT_BOTTOM, localZ,
                            localXStart, HEIGHT_TOP, localZ,
                            localXEnd, HEIGHT_TOP, localZ);
                }
            }

            // 2) 渲染底部封口（每个正方形贡献自己的底部面）
            // 底部互相重叠只会导致 alpha 叠加颜色略深，不会产生线条，所以直接渲染即可
            float localXMin = (float) (mySquare.xmin() - myPos.getX());
            float localXMax = (float) (mySquare.xmax() - myPos.getX());
            float localZMin = (float) (mySquare.zmin() - myPos.getZ());
            float localZMax = (float) (mySquare.zmax() - myPos.getZ());

            // 底部封口（从上方可见，法线朝上 +Y）
            emitQuad(consumer, pose,
                    localXMin, HEIGHT_BOTTOM, localZMin,
                    localXMax, HEIGHT_BOTTOM, localZMin,
                    localXMax, HEIGHT_BOTTOM, localZMax,
                    localXMin, HEIGHT_BOTTOM, localZMax);
            // 底部封口（从下方可见，法线朝下 -Y）
            emitQuad(consumer, pose,
                    localXMin, HEIGHT_BOTTOM, localZMax,
                    localXMax, HEIGHT_BOTTOM, localZMax,
                    localXMax, HEIGHT_BOTTOM, localZMin,
                    localXMin, HEIGHT_BOTTOM, localZMin);
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

    // ======================== 算法：区间可见性计算 ========================

    /**
     * 从收集的 BE 状态 Map 中构建所有激活信标的 Square 列表。
     */
    private static List<Square> buildActiveSquares(Map<BlockPos, Boolean> beacons) {
        List<Square> squares = new ArrayList<>();
        for (Map.Entry<BlockPos, Boolean> entry : beacons.entrySet()) {
            if (!entry.getValue()) continue;
            BlockPos pos = entry.getKey();
            double cx = pos.getX() + CENTER_OFFSET;
            double cz = pos.getZ() + CENTER_OFFSET;
            squares.add(new Square(cx - HALF_D, cx + HALF_D, cz - HALF_D, cz + HALF_D));
        }
        return squares;
    }

    /**
     * 计算给定正方形 {@code sq} 的哪些墙面段在与其他所有正方形的并集中是「可见」（不被覆盖）的。
     * <p>
     * 核心逻辑：对正方形的每条边（+Z, -Z, +X, -X），
     * 找出所有「严格包含该边坐标」的其他正方形，
     * 收集这些正方形在该边方向上的覆盖区间，做区间减法，
     * 得到可见区间列表。
     */
    private static List<VisibleFace> computeVisibleFacesForSquare(Square sq, List<Square> allSquares) {
        List<VisibleFace> faces = new ArrayList<>();

        // ---- +Z 面（z = zmax），沿 x 方向 ----
        List<Interval> coverage = new ArrayList<>();
        for (Square other : allSquares) {
            if (other == sq) continue; // 引用比较，同一对象
            // 条件：other 的 z 区间严格包含 sq.zmax（跨信标判定使用坐标值比较）
            if (other.zmin() < sq.zmax() && sq.zmax() < other.zmax()) {
                double cStart = Math.max(sq.xmin(), other.xmin());
                double cEnd   = Math.min(sq.xmax(), other.xmax());
                if (cStart < cEnd) {
                    coverage.add(new Interval(cStart, cEnd));
                }
            }
        }
        addVisibleFacesForEdge(sq.xmin(), sq.xmax(), coverage, false, sq.zmax(), faces);

        // ---- -Z 面（z = zmin） ----
        coverage.clear();
        for (Square other : allSquares) {
            if (other == sq) continue;
            if (other.zmin() < sq.zmin() && sq.zmin() < other.zmax()) {
                double cStart = Math.max(sq.xmin(), other.xmin());
                double cEnd   = Math.min(sq.xmax(), other.xmax());
                if (cStart < cEnd) {
                    coverage.add(new Interval(cStart, cEnd));
                }
            }
        }
        addVisibleFacesForEdge(sq.xmin(), sq.xmax(), coverage, false, sq.zmin(), faces);

        // ---- +X 面（x = xmax），沿 z 方向 ----
        coverage.clear();
        for (Square other : allSquares) {
            if (other == sq) continue;
            if (other.xmin() < sq.xmax() && sq.xmax() < other.xmax()) {
                double cStart = Math.max(sq.zmin(), other.zmin());
                double cEnd   = Math.min(sq.zmax(), other.zmax());
                if (cStart < cEnd) {
                    coverage.add(new Interval(cStart, cEnd));
                }
            }
        }
        addVisibleFacesForEdge(sq.zmin(), sq.zmax(), coverage, true, sq.xmax(), faces);

        // ---- -X 面（x = xmin） ----
        coverage.clear();
        for (Square other : allSquares) {
            if (other == sq) continue;
            if (other.xmin() < sq.xmin() && sq.xmin() < other.xmax()) {
                double cStart = Math.max(sq.zmin(), other.zmin());
                double cEnd   = Math.min(sq.zmax(), other.zmax());
                if (cStart < cEnd) {
                    coverage.add(new Interval(cStart, cEnd));
                }
            }
        }
        addVisibleFacesForEdge(sq.zmin(), sq.zmax(), coverage, true, sq.xmin(), faces);

        return faces;
    }

    /**
     * 对 {@code [edgeMin, edgeMax]}（原始边的区间）做减去 {@code coverage}（覆盖区间并集）的操作，
     * 将得到的可见区间段转换为 {@link VisibleFace} 并加入结果列表。
     *
     * @param isXAxis  true → 墙面沿 X 方向（x = coord, z 从 start 到 end）；false → 沿 Z 方向
     * @param coord    墙面所在坐标（world space）
     * @param faces    输出列表
     */
    private static void addVisibleFacesForEdge(double edgeMin, double edgeMax,
                                                List<Interval> coverage, boolean isXAxis,
                                                double coord, List<VisibleFace> faces) {
        if (coverage.isEmpty()) {
            // 无任何覆盖 → 整段可见
            faces.add(new VisibleFace(isXAxis, coord, edgeMin, edgeMax));
            return;
        }

        // 合并覆盖区间
        List<Interval> merged = mergeIntervals(coverage);

        // 区间减法
        double currentStart = edgeMin;
        for (Interval cov : merged) {
            if (currentStart < cov.start()) {
                faces.add(new VisibleFace(isXAxis, coord, currentStart, cov.start()));
            }
            currentStart = Math.max(currentStart, cov.end());
        }
        if (currentStart < edgeMax) {
            faces.add(new VisibleFace(isXAxis, coord, currentStart, edgeMax));
        }
    }

    /**
     * 合并一系列可能有重叠的区间，返回不重叠的区间列表（已排序）。
     */
    private static List<Interval> mergeIntervals(List<Interval> intervals) {
        if (intervals.isEmpty()) return List.of();
        List<Interval> sorted = new ArrayList<>(intervals);
        sorted.sort(Comparator.naturalOrder());

        List<Interval> result = new ArrayList<>();
        Interval current = sorted.get(0);
        for (int i = 1; i < sorted.size(); i++) {
            Interval next = sorted.get(i);
            if (next.start() <= current.end()) {
                // 重叠 → 合并
                current = new Interval(current.start(), Math.max(current.end(), next.end()));
            } else {
                result.add(current);
                current = next;
            }
        }
        result.add(current);
        return result;
    }

    // ======================== 辅助：渲染四边形 ========================

    /** 输出一个四边形（4 个顶点），全部使用单色蓝色 */
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

    @SuppressWarnings("null")
    private static void putVertex(VertexConsumer consumer, PoseStack.Pose pose,
                                   float x, float y, float z) {
        consumer.addVertex(pose, x, y, z).setColor(R, G, B, A);
    }
}
