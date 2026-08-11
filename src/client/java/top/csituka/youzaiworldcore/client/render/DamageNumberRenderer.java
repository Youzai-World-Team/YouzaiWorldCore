package top.csituka.youzaiworldcore.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.OrderedSubmitNodeCollector;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import top.csituka.youzaiworldcore.util.DebugLogger;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 客户端世界空间伤害跳字渲染器。
 * <p>
 * 使用 Minecraft 26.2 的提交式世界渲染管线绘制上浮、轻微横移并淡出的伤害数字。
 * </p>
 */
@SuppressWarnings("null")
public final class DamageNumberRenderer {

    private static final String MODULE = "DamageNumberRenderer";
    private static final long LIFETIME_MILLIS = 1_250L;
    private static final int MAX_VISIBLE_NUMBERS = 256;
    private static final double MAX_RENDER_DISTANCE_SQUARED = 64.0D * 64.0D;
    private static final int TEXT_RGB = 0xFF5A47;
    private static final int OUTLINE_RGB = 0x4A0909;
    private static final List<DamageNumber> NUMBERS = new ArrayList<>();

    private DamageNumberRenderer() {
    }

    /** 注册客户端 Tick 清理与世界渲染回调。 */
    public static void initialize() {
        ClientTickEvents.END_CLIENT_TICK.register(DamageNumberRenderer::tick);
        LevelRenderEvents.COLLECT_SUBMITS.register(DamageNumberRenderer::render);
        DebugLogger.info(MODULE, "伤害跳字客户端渲染器已初始化");
    }

    /**
     * 新增一条伤害跳字。
     *
     * @param x 实体受伤时的世界 X 坐标
     * @param y 实体受伤时的世界 Y 坐标
     * @param z 实体受伤时的世界 Z 坐标
     * @param entityHeight 实体碰撞箱高度
     * @param damage 实际伤害值
     */
    public static void add(double x, double y, double z, float entityHeight, float damage) {
        if (damage <= 0.0F || !Float.isFinite(damage)) {
            return;
        }

        if (NUMBERS.size() >= MAX_VISIBLE_NUMBERS) {
            NUMBERS.removeFirst();
        }

        ThreadLocalRandom random = ThreadLocalRandom.current();
        double horizontalOffsetX = random.nextDouble(-0.22D, 0.22D);
        double horizontalOffsetZ = random.nextDouble(-0.22D, 0.22D);
        double horizontalDrift = random.nextDouble(-0.16D, 0.16D);
        FormattedCharSequence text = Component.literal(formatDamage(damage)).getVisualOrderText();
        NUMBERS.add(new DamageNumber(
                x + horizontalOffsetX,
                y + entityHeight + 0.2D,
                z + horizontalOffsetZ,
                horizontalDrift,
                text,
                Minecraft.getInstance().font.width(text),
                Util.getMillis()));
    }

    private static void tick(Minecraft client) {
        if (client.level == null) {
            NUMBERS.clear();
            return;
        }

        long now = Util.getMillis();
        NUMBERS.removeIf(number -> now - number.createdAtMillis() >= LIFETIME_MILLIS);
    }

    private static void render(net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext context) {
        if (NUMBERS.isEmpty()) {
            return;
        }

        Minecraft client = Minecraft.getInstance();
        CameraRenderState camera = context.levelState().cameraRenderState;
        if (client.level == null || camera == null || camera.pos == null) {
            return;
        }

        long now = Util.getMillis();
        PoseStack poseStack = context.poseStack();
        OrderedSubmitNodeCollector collector = context.submitNodeCollector().order(0);
        for (DamageNumber number : NUMBERS) {
            float progress = Mth.clamp(
                    (float) (now - number.createdAtMillis()) / LIFETIME_MILLIS, 0.0F, 1.0F);
            double renderX = number.x() + number.horizontalDrift() * progress;
            double renderY = number.y() + 0.85D * easeOutQuadratic(progress);
            double deltaX = renderX - camera.pos.x;
            double deltaY = renderY - camera.pos.y;
            double deltaZ = number.z() - camera.pos.z;
            if (deltaX * deltaX + deltaY * deltaY + deltaZ * deltaZ > MAX_RENDER_DISTANCE_SQUARED) {
                continue;
            }

            float alpha = fadeAlpha(progress);
            int textColor = colorWithAlpha(TEXT_RGB, alpha);
            int outlineColor = colorWithAlpha(OUTLINE_RGB, alpha * 0.9F);
            float scale = 0.025F * popScale(progress);

            poseStack.pushPose();
            poseStack.translate(deltaX, deltaY, deltaZ);
            poseStack.mulPose(camera.orientation);
            poseStack.scale(scale, -scale, scale);
            collector.submitText(
                    poseStack,
                    -number.width() / 2.0F,
                    0.0F,
                    number.text(),
                    false,
                    Font.DisplayMode.NORMAL,
                    LightCoordsUtil.FULL_BRIGHT,
                    textColor,
                    0,
                    outlineColor);
            poseStack.popPose();
        }
    }

    private static String formatDamage(float damage) {
        if (damage < 0.1F) {
            return String.format(Locale.ROOT, "-%.2f", damage);
        }
        float rounded = Math.round(damage);
        if (Math.abs(damage - rounded) < 0.05F) {
            return String.format(Locale.ROOT, "-%.0f", damage);
        }
        return String.format(Locale.ROOT, "-%.1f", damage);
    }

    private static float fadeAlpha(float progress) {
        if (progress <= 0.62F) {
            return 1.0F;
        }
        return 1.0F - (progress - 0.62F) / 0.38F;
    }

    private static float popScale(float progress) {
        if (progress >= 0.16F) {
            return 1.0F;
        }
        return Mth.lerp(progress / 0.16F, 1.35F, 1.0F);
    }

    private static float easeOutQuadratic(float progress) {
        return 1.0F - (1.0F - progress) * (1.0F - progress);
    }

    private static int colorWithAlpha(int rgb, float alpha) {
        int alphaByte = Mth.clamp(Math.round(alpha * 255.0F), 0, 255);
        return alphaByte << 24 | rgb;
    }

    private record DamageNumber(double x, double y, double z, double horizontalDrift,
                                FormattedCharSequence text, int width, long createdAtMillis) {
    }
}
