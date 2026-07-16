package top.csituka.youzaiworldcore.client.renderer.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.Font.DisplayMode;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.joml.Matrix4f;
import top.csituka.youzaiworldcore.client.renderer.entity.state.AnimationSubtitleEntityRenderState;
import top.csituka.youzaiworldcore.entity.animation_subtitle.AnimationSubtitleEntity;
import top.csituka.youzaiworldcore.text.StyledTextUtil.GlyphSlot;

import java.util.ArrayList;
import java.util.List;

/**
 * 动画字幕实体渲染器。
 *
 * <h2>渲染流程</h2>
 * <ol>
 *   <li>从实体提取渲染状态（字形布局、动画进度、颜色等）</li>
 *   <li>主字幕：逐字渲染 billboarded 文字，带弹性弹出动画效果</li>
 *   <li>碎片：渲染单个 billboarded 字符，带物理状态动画</li>
 * </ol>
 *
 * <p>使用 MC 1.26.2 的新渲染管线：{@code extractRenderState → submit}。</p>
 */
@SuppressWarnings("null")
public class AnimationSubtitleRenderer extends EntityRenderer<AnimationSubtitleEntity, AnimationSubtitleEntityRenderState> {

    /** 弹出动画每字符间隔 (tick) */
    private static final int POP_INTERVAL = 4;
    /** 弹出动画持续时间 (tick) */
    private static final int POP_DURATION = 8;

    /** 碎片物理状态常量 */
    private static final int SHARD_FALLING = 0;
    private static final int SHARD_SETTLING = 1;
    private static final int SHARD_RESTING = 2;
    private static final int SHARD_SHRINKING = 3;

    public AnimationSubtitleRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    // ======================== 渲染管线 ========================

    @Override
    public AnimationSubtitleEntityRenderState createRenderState() {
        return new AnimationSubtitleEntityRenderState();
    }

    @Override
    public void extractRenderState(
            AnimationSubtitleEntity entity,
            AnimationSubtitleEntityRenderState state,
            float partialTick
    ) {
        super.extractRenderState(entity, state, partialTick);

        state.mode = entity.getMode();
        state.text = entity.getDisplayText();
        state.baseScale = entity.getBaseScale();
        state.visibleCount = entity.getVisibleCount();
        state.droppedCount = entity.getDroppedCount();
        state.holdTicks = entity.getHoldTicks();
        state.shardState = entity.getShardState();
        state.shrinkFactor = entity.getShrinkFactor();
        state.alpha = entity.getAlpha();
        state.rgbColor = entity.getRgb();
        state.yRot = (float) Math.toRadians(entity.getYRot());
        state.age = entity.tickCount;
        state.glyphs = new ArrayList<>(entity.getCachedGlyphs());
        state.charWidths = new ArrayList<>(entity.getCachedCharWidths());
        state.totalWidth = entity.getCachedTotalWidth();
        state.totalHeight = entity.getCachedTotalHeight();
        state.onGround = entity.isOnGround();
    }

    @Override
    public void submit(
            AnimationSubtitleEntityRenderState state,
            PoseStack poseStack,
            SubmitNodeCollector collector,
            CameraRenderState cameraState
    ) {
        if (state.mode == AnimationSubtitleEntity.MODE_MAIN) {
            submitMain(state, poseStack, collector, cameraState);
        } else {
            submitShard(state, poseStack, collector, cameraState);
        }
    }

    // ======================== 主字幕渲染 ========================

    private void submitMain(
            AnimationSubtitleEntityRenderState state,
            PoseStack poseStack,
            SubmitNodeCollector collector,
            CameraRenderState cameraState
    ) {
        List<GlyphSlot> glyphs = state.glyphs;
        List<Float> charWidths = state.charWidths;
        if (glyphs.isEmpty() || state.visibleCount <= 0) {
            return;
        }
        if (state.droppedCount >= countVisibleGlyphsInState(state)) {
            // 所有字符已掉落
            return;
        }

        float scale = state.baseScale;

        // 计算所有可见行的宽度和行信息
        List<Float> lineWidths = new ArrayList<>();
        List<Integer> lineStartIdx = new ArrayList<>();
        float currentLineWidth = 0;
        int visibleIdx = 0;
        lineStartIdx.add(0);

        for (int i = 0; i < glyphs.size(); i++) {
            GlyphSlot slot = glyphs.get(i);
            if (!slot.visible()) {
                continue;
            }
            String s = slot.styledText();
            if ("\n".equals(s)) {
                lineWidths.add(currentLineWidth);
                currentLineWidth = 0;
                lineStartIdx.add(visibleIdx + 1); // next line starts at next visible
                continue;
            }
            if (i < charWidths.size()) {
                currentLineWidth += charWidths.get(i);
            }
            visibleIdx++;
        }
        lineWidths.add(currentLineWidth);

        // 逐字符渲染
        int visibleCount = state.visibleCount;
        int droppedCount = state.droppedCount;
        visibleIdx = 0;
        int currentLine = 0;

        for (int i = 0; i < glyphs.size(); i++) {
            GlyphSlot slot = glyphs.get(i);
            if (!slot.visible()) {
                continue;
            }

            String s = slot.styledText();
            if ("\n".equals(s)) {
                currentLine++;
                continue;
            }

            // 跳过已掉落的字符
            if (!" ".equals(s) && s.length() > 0 && visibleIdx < droppedCount) {
                visibleIdx++;
                continue;
            }

            // 跳过不可见的字符
            if (visibleIdx >= visibleCount) {
                visibleIdx++;
                continue;
            }

            // 获取原始字符（去除样式前缀）
            char rawChar = s.charAt(s.length() - 1);
            String dispChar = String.valueOf(rawChar);

            // 计算字符在行内的X偏移
            float charXOffset = 0;
            int v = 0;
            int targetLine = currentLine;
            int lineStart = 0;
            for (int l = 0; l < targetLine; l++) {
                lineStart += countVisibleInLine(glyphs, l);
            }
            for (int j = 0; j < glyphs.size() && v < visibleIdx; j++) {
                GlyphSlot gs = glyphs.get(j);
                if (!gs.visible()) continue;
                if ("\n".equals(gs.styledText())) continue;
                if (v >= lineStart) {
                    if (j < charWidths.size()) {
                        charXOffset += charWidths.get(j);
                    }
                }
                v++;
            }

            float lineWidth = lineWidths.size() > currentLine ? lineWidths.get(currentLine) : 0;
            float centerX = lineWidth / 2.0F;
            float centerY = state.totalHeight / 2.0F;

            // 字符在2D布局中的位置
            float localX = (charXOffset - centerX) * scale * 0.05F;
            float localY = (centerY - currentLine) * scale * 0.5F;

            // 计算朝向偏移
            float yRot = state.yRot;
            float cos = (float) Math.cos(yRot);
            float sin = (float) Math.sin(yRot);
            float worldOffsetX = localX * cos;
            float worldOffsetZ = localX * sin;

            // 浮动动画
            float floatOffset = (float) Math.sin(state.age * 0.08F + visibleIdx * 0.5F) * 0.008F;

            // 弹出动画（弹性过冲）
            float charScale = getCharScale(state, visibleIdx, visibleCount);

            if (charScale <= 0.001F) {
                visibleIdx++;
                continue;
            }

            float finalScale = scale * charScale * 0.06F;

            // 渲染单个字符
            poseStack.pushPose();
            poseStack.translate(-worldOffsetX, localY + floatOffset, -worldOffsetZ);
            poseStack.scale(finalScale, finalScale, finalScale);

            // 使用billboard效果（始终面向相机）
            applyBillboard(poseStack, cameraState);

            // 屏幕坐标系Y轴向下，3D世界Y轴向上，需要翻转
            poseStack.scale(1, -1, 1);

            Font font = getFont();
            int color = slot.rgbColor() | (state.alpha << 24);
            int packedLight = state.lightCoords;

            renderBillboardText(poseStack, collector, font, dispChar, color, packedLight, cameraState);

            poseStack.popPose();
            visibleIdx++;
        }
    }

    // ======================== 碎片渲染 ========================

    private void submitShard(
            AnimationSubtitleEntityRenderState state,
            PoseStack poseStack,
            SubmitNodeCollector collector,
            CameraRenderState cameraState
    ) {
        float scale = state.baseScale;

        switch (state.shardState) {
            case SHARD_FALLING:
                // 正常渲染，完整的尺寸和透明度
                break;
            case SHARD_SETTLING:
                // 可以添加轻微的旋转归零/沉降动画
                break;
            case SHARD_RESTING:
                // 静止状态，正常渲染
                break;
            case SHARD_SHRINKING:
                scale *= state.shrinkFactor;
                break;
        }

        if (scale <= 0.001F || state.alpha <= 0) {
            return;
        }

        String text = state.text;
        if (text == null || text.isEmpty()) {
            return;
        }

        // 去除样式前缀，获取原始字符
        String dispChar = text;
        if (text.length() > 1 && text.startsWith("\u00a7")) {
            int idx = text.length() - 1;
            // 跳过所有§x格式码
            while (idx > 0 && text.charAt(idx - 1) == '\u00a7') {
                idx -= 2;
            }
            dispChar = text.substring(Math.max(0, idx));
        }

        float finalScale = scale * 0.06F;

        poseStack.pushPose();
        // 碎片微微抬起避免z-fighting
        poseStack.translate(0, 0.03, 0);
        poseStack.scale(finalScale, finalScale, finalScale);

        applyBillboard(poseStack, cameraState);

        // 屏幕坐标系Y轴向下，3D世界Y轴向上，需要翻转
        poseStack.scale(1, -1, 1);

        Font font = getFont();
        int color = state.rgbColor | (state.alpha << 24);
        int packedLight = state.lightCoords;

        renderBillboardText(poseStack, collector, font, dispChar, color, packedLight, cameraState);

        poseStack.popPose();
    }

    // ======================== 文本渲染辅助 ========================

    /**
     * 使用 Minecraft Font 在 billboarded 模式下渲染文本。
     */
    private void renderBillboardText(
            PoseStack poseStack,
            SubmitNodeCollector collector,
            Font font,
            String text,
            int color,
            int packedLight,
            CameraRenderState cameraState
    ) {
        try {
            // 准备文本（在 (0, 0) 位置）
            Font.PreparedText preparedText = font.prepareText(
                    text,
                    0.0F, 0.0F,
                    color,
                    false,
                    packedLight
            );

            // 通过 GlyphVisitor 捕获 TextRenderable
            // 使用一个简单的收集器
            class GlyphCollector implements Font.GlyphVisitor {
                RenderType foundType = null;
                List<net.minecraft.client.gui.font.TextRenderable> renderables = new ArrayList<>();

                @Override
                public void acceptGlyph(net.minecraft.client.gui.font.TextRenderable.Styled styled) {
                    if (foundType == null) {
                        foundType = styled.renderType(DisplayMode.POLYGON_OFFSET);
                    }
                    renderables.add(styled);
                }

                @Override
                public void acceptEffect(net.minecraft.client.gui.font.TextRenderable renderable) {
                    renderables.add(renderable);
                }

                @Override
                public void acceptRenderable(net.minecraft.client.gui.font.TextRenderable renderable) {
                    renderables.add(renderable);
                }

                @Override
                public void acceptEmptyArea(net.minecraft.client.gui.font.EmptyArea area) {
                    // 忽略空白区域
                }
            }

            GlyphCollector glyphCollector = new GlyphCollector();
            preparedText.visit(glyphCollector);

            if (glyphCollector.foundType == null) {
                return;
            }

            final RenderType finalRenderType = glyphCollector.foundType;
            final List<net.minecraft.client.gui.font.TextRenderable> renderables =
                    new ArrayList<>(glyphCollector.renderables);

            // 提交自定义几何体渲染
            collector.order(0).submitCustomGeometry(poseStack, finalRenderType,
                    (pose, vertexConsumer) -> {
                        Matrix4f matrix = pose.pose();
                        for (net.minecraft.client.gui.font.TextRenderable renderable : renderables) {
                            renderable.render(matrix, vertexConsumer, packedLight, false);
                        }
                    }
            );
        } catch (Exception e) {
            // 如果文本渲染失败，回退到使用 name tag
        }
    }

    // ======================== 辅助方法 ========================

    /**
     * 应用圆柱形 billboard 变换，使文字在水平方向始终面向相机，
     * 垂直方向保持直立（仅绕 Y 轴旋转）。
     */
    private void applyBillboard(PoseStack poseStack, CameraRenderState cameraState) {
        poseStack.mulPose(com.mojang.math.Axis.YP.rotationDegrees(-cameraState.yRot));
    }

    /**
     * 计算单个字符的弹性弹出缩放。
     */
    private float getCharScale(AnimationSubtitleEntityRenderState state, int visibleIndex, int visibleCount) {
        if (visibleIndex >= visibleCount) {
            return 0.0F;
        }
        int charAge = state.age - visibleIndex * POP_INTERVAL;
        if (charAge < 0) {
            return 0.0F;
        }
        if (charAge >= POP_DURATION) {
            return 1.0F;
        }
        float t = (float) charAge / POP_DURATION;
        float base = (float) Math.sin(t * Math.PI);
        return base + base * 0.42F; // 弹性过冲
    }

    private int countVisibleInLine(List<GlyphSlot> glyphs, int targetLine) {
        int currentLine = 0;
        int count = 0;
        for (GlyphSlot slot : glyphs) {
            if (!slot.visible()) continue;
            if ("\n".equals(slot.styledText())) {
                currentLine++;
                if (currentLine > targetLine) break;
                continue;
            }
            if (currentLine == targetLine) {
                count++;
            }
        }
        return count;
    }

    private int countVisibleGlyphsInState(AnimationSubtitleEntityRenderState state) {
        int count = 0;
        for (GlyphSlot slot : state.glyphs) {
            if (slot.visible() && !"\n".equals(slot.styledText())) {
                count++;
            }
        }
        return count;
    }

    @Override
    protected boolean shouldShowName(AnimationSubtitleEntity entity, double distance) {
        return false; // 不使用原版名字标签
    }
}
