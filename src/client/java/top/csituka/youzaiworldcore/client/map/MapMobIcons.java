package top.csituka.youzaiworldcore.client.map;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntitySpawnRequest;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.DefaultAttributes;
import org.joml.Quaternionf;
import org.joml.Vector2f;
import org.joml.Vector3f;
import top.csituka.youzaiworldcore.client.render.YzuiTheme;
import top.csituka.youzaiworldcore.util.DebugLogger;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 从原版/模组模型的头部 UV 提取纹理小图标；共用实际材质，资源包重载后按新渲染器重建。 */
@SuppressWarnings({"rawtypes", "unchecked"})
public final class MapMobIcons {
    private record Face(float x, float y, float w, float h, float z, float u, float v, int uw, int vh, boolean head) { }
    private record Portrait(Object renderer, Identifier texture, List<Face> faces, float x, float y, float size, EntityRenderState fallback) { }
    private static final Map<EntityType<?>, Mob> PREVIEWS = new HashMap<>();
    private static final Map<EntityType<?>, Portrait> PORTRAITS = new HashMap<>();
    private static List<EntityType<?>> types;
    private static ClientLevel level;
    private MapMobIcons() { }

    /** 目录来自注册表，包含当前世界启用的所有生物种类，不依赖附近是否存在。预览实体不加入世界。 */
    public static List<EntityType<?>> types() {
        ensureLevel();
        if (types != null) return types;
        var result = new ArrayList<EntityType<?>>();
        if (level != null) for (var type : BuiltInRegistries.ENTITY_TYPE) {
            if (!DefaultAttributes.hasSupplier(type) || !type.isEnabled(level.enabledFeatures())) continue;
            try {
                var entity = type.create(level, new EntitySpawnRequest(EntitySpawnReason.LOAD, true));
                if (entity instanceof Mob mob) {
                    // 26.2 不再在构造时分配 ID，渲染器提取装备等状态仍要求非零 ID。
                    // 负数 ID 仅供离屏预览使用，不向客户端世界注册这些实体。
                    mob.setId(-1 - BuiltInRegistries.ENTITY_TYPE.getId(type));
                    PREVIEWS.put(type, mob); result.add(type);
                }
            } catch (RuntimeException failure) { DebugLogger.exception("MapMobIcons", "创建生物图标 " + EntityType.getKey(type), failure); }
        }
        result.sort(Comparator.comparing((EntityType<?> type) -> type.getDescription().getString(), String.CASE_INSENSITIVE_ORDER)
                .thenComparing(type -> EntityType.getKey(type).toString()));
        types = List.copyOf(result);
        DebugLogger.debug("MapMobIcons", "已创建 %d 个带独立实体 ID 的图标预览", types.size());
        return types;
    }
    private static void ensureLevel() {
        var current = Minecraft.getInstance().level;
        if (current != level) { clear(); level = current; }
    }
    public static void draw(GuiGraphicsExtractor g, EntityType<?> type, int x, int y, int size, float opacity) {
        if (type == null || size < 1 || opacity <= 0) return;
        types();
        var preview = PREVIEWS.get(type);
        if (preview == null) return;
        var renderer = Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(preview);
        var portrait = PORTRAITS.get(type);
        if (portrait == null || portrait.renderer() != renderer) {
            portrait = create(preview, renderer); PORTRAITS.put(type, portrait);
        }
        if (portrait.texture() != null && !portrait.faces().isEmpty()) {
            float scale = size / portrait.size();
            int tint = YzuiTheme.alpha(0xFFFFFFFF, opacity);
            for (var face : portrait.faces()) {
                int left = x + Math.round((face.x() - portrait.x()) * scale), top = y + Math.round((face.y() - portrait.y()) * scale);
                int w = Math.max(1, Math.round(face.w() * scale)), h = Math.max(1, Math.round(face.h() * scale));
                g.blit(RenderPipelines.GUI_TEXTURED, portrait.texture(), left, top, face.u(), face.v(), w, h, face.uw(), face.vh(), 4096, 4096, tint);
            }
        } else if (portrait.fallback() != null) {
            // 龙及非立方体模组模型沿用原版 GUI 离屏纹理渲染；entity() 不处理 GUI pose，须先换算屏幕坐标。
            var a = g.pose().transformPosition(new Vector2f(x, y));
            var b = g.pose().transformPosition(new Vector2f(x + size, y + size));
            var state = portrait.fallback();
            float scale = Math.min(b.x - a.x, b.y - a.y) * 0.8f / Math.max(0.1f, Math.max(state.boundingBoxWidth, state.boundingBoxHeight));
            g.entity(state, scale, new Vector3f(0, state.boundingBoxHeight / 2, 0), new Quaternionf().rotateZ((float) Math.PI),
                    new Quaternionf(), Math.round(a.x), Math.round(a.y), Math.round(b.x), Math.round(b.y));
        }
    }
    private static Portrait create(Mob preview, net.minecraft.client.renderer.entity.EntityRenderer renderer) {
        EntityRenderState state = null;
        try {
            state = renderer.createRenderState(preview, 0);
            state.nameTag = null; state.scoreText = null; state.shadowPieces.clear(); state.leashStates = List.of();
            state.outlineColor = 0; state.displayFireAnimation = false;
            if (state instanceof LivingEntityRenderState living) { living.bodyRot = 180; living.yRot = 0; living.xRot = 0; }
            if (renderer instanceof LivingEntityRenderer living && state instanceof LivingEntityRenderState livingState) {
                Identifier texture = living.getTextureLocation(livingState);
                net.minecraft.client.model.Model<?> model = living.getModel(); var all = model.allParts();
                var poses = new ArrayList<PartPose>(); all.forEach(part -> poses.add(part.storePose()));
                var faces = new ArrayList<Face>();
                try {
                    model.resetPose();
                    model.root().visit(new PoseStack(), (pose, path, index, cube) -> {
                        for (var polygon : cube.polygons) {
                            if (pose.transformNormal(polygon.normal(), new Vector3f()).z > -0.5f) continue;
                            float minX = Float.POSITIVE_INFINITY, minY = minX, minU = minX, minV = minX;
                            float maxX = Float.NEGATIVE_INFINITY, maxY = maxX, maxU = maxX, maxV = maxX, depth = 0;
                            for (var vertex : polygon.vertices()) {
                                var point = pose.pose().transformPosition(vertex.worldX(), vertex.worldY(), vertex.worldZ(), new Vector3f());
                                minX = Math.min(minX, point.x); maxX = Math.max(maxX, point.x); minY = Math.min(minY, point.y); maxY = Math.max(maxY, point.y);
                                minU = Math.min(minU, vertex.u()); maxU = Math.max(maxU, vertex.u()); minV = Math.min(minV, vertex.v()); maxV = Math.max(maxV, vertex.v()); depth += point.z;
                            }
                            if (maxX - minX > 0.001f && maxY - minY > 0.001f && maxU > minU && maxV > minV)
                                faces.add(new Face(minX, minY, maxX - minX, maxY - minY, depth / 4, minU * 4096, minV * 4096,
                                        Math.max(1, Math.round((maxU - minU) * 4096)), Math.max(1, Math.round((maxV - minV) * 4096)), headPart(path)));
                        }
                    });
                } finally { for (int i = 0; i < all.size(); i++) all.get(i).loadPose(poses.get(i)); }
                if (faces.stream().anyMatch(Face::head)) faces.removeIf(face -> !face.head());
                if (!faces.isEmpty()) {
                    faces.sort(Comparator.comparingDouble(Face::z).reversed());
                    float minX = Float.POSITIVE_INFINITY, minY = minX, maxX = Float.NEGATIVE_INFINITY, maxY = maxX;
                    for (var face : faces) { minX = Math.min(minX, face.x()); minY = Math.min(minY, face.y()); maxX = Math.max(maxX, face.x() + face.w()); maxY = Math.max(maxY, face.y() + face.h()); }
                    float size = Math.max(maxX - minX, maxY - minY);
                    return new Portrait(renderer, texture, List.copyOf(faces), (minX + maxX - size) / 2, (minY + maxY - size) / 2, size, null);
                }
            }
        } catch (RuntimeException failure) { DebugLogger.exception("MapMobIcons", "提取生物纹理 " + EntityType.getKey(preview.getType()), failure); }
        return new Portrait(renderer, null, List.of(), 0, 0, 1, state);
    }
    private static boolean headPart(String path) {
        for (String part : path.toLowerCase(java.util.Locale.ROOT).split("[/_]"))
            for (String name : List.of("head", "skull", "beak", "nose", "snout", "muzzle", "ear", "horn", "antenna", "wattle", "hat", "jaw"))
                if (part.equals(name) || part.matches(name + "[0-9]+")) return true;
        return false;
    }
    public static void clear() { PREVIEWS.clear(); PORTRAITS.clear(); types = null; level = null; }
}
