package top.csituka.youzaiworldcore.client.map;

import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.fabric.api.client.rendering.v1.FabricRenderState;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelExtractionContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelExtractionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.renderer.blockentity.BeaconRenderer;
import top.csituka.youzaiworldcore.client.config.YzHudSettings;
import top.csituka.youzaiworldcore.client.render.YzuiTheme;
import top.csituka.youzaiworldcore.util.DebugLogger;

import java.util.List;

/** 世界内路径点光柱；复用原版信标材质与渲染，仅在提取阶段读取地图及玩家状态。 */
@SuppressWarnings("null")
public final class MapWaypointBeams {
    private static final RenderStateDataKey<Frame> FRAME = RenderStateDataKey.create(() -> "youzaiworldcore:map_waypoint_beams");
    private static final Frame EMPTY = new Frame(List.of(), 0);

    /** 交给渲染线程的不可变坐标、颜色和宽度，不携带世界、实体或可变地图集合。 */
    private record Beam(double x, int y, double z, int color, float radiusScale) { }
    private record Frame(List<Beam> beams, float animationTime) { }

    private MapWaypointBeams() { }

    /** 注册提取和提交事件；世界内导航开关同时控制悬浮标点与光柱。 */
    public static void initialize() {
        LevelExtractionEvents.END_EXTRACTION.register(MapWaypointBeams::extract);
        LevelRenderEvents.COLLECT_SUBMITS.register(MapWaypointBeams::submit);
        DebugLogger.info("MapWaypointBeams", "世界内路径点信标光柱已注册，跟随世界内导航开关");
    }

    private static void extract(LevelExtractionContext context) {
        Frame frame = EMPTY;
        if (MapRenderer.worldNavigationVisible()) {
            var origin = context.levelState().cameraRenderState.pos;
            float opacity = YzHudSettings.getGlobalOpacity();
            var beams = MapRenderer.navigationTargets().stream().map(target -> {
                var position = target.position(); var point = target.point();
                // 沿用原版远处光柱逐渐加宽的规则，高差不影响柱宽。
                float scale = (float) Math.max(1.0, Math.hypot(position.x() - origin.x, position.z() - origin.z) / 96.0);
                return new Beam(position.x(), point.y(), position.z(), YzuiTheme.alpha(point.color(), opacity), scale);
            }).toList();
            var state = context.levelState();
            frame = new Frame(beams, Math.floorMod(state.gameTime, 40) + state.worldPartialTicks);
        }
        // 每帧覆盖快照；隐藏、切服和空列表不会遗留上一帧的光柱。
        ((FabricRenderState) context.levelState()).setData(FRAME, frame);
    }

    private static void submit(LevelRenderContext context) {
        var frame = ((FabricRenderState) context.levelState()).getDataOrDefault(FRAME, EMPTY);
        if (frame.beams().isEmpty()) return;
        var origin = context.levelState().cameraRenderState.pos;
        var poses = new PoseStack();
        for (var beam : frame.beams()) {
            poses.pushPose();
            // 原版函数内部会向 X/Z 各偏移半格，在这里抵消，保证光柱中心与保存坐标完全重合。
            poses.translate(beam.x() - origin.x - 0.5, beam.y() - origin.y, beam.z() - origin.z - 0.5);
            BeaconRenderer.submitBeaconBeam(poses, context.submitNodeCollector(), BeaconRenderer.BEAM_LOCATION,
                    1.0F, frame.animationTime(), 0, BeaconRenderer.MAX_RENDER_Y, beam.color(),
                    BeaconRenderer.SOLID_BEAM_RADIUS * beam.radiusScale(), BeaconRenderer.BEAM_GLOW_RADIUS * beam.radiusScale());
            poses.popPose();
        }
    }
}
