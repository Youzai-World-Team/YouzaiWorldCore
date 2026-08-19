package top.csituka.youzaiworldcore.client.renderer.block;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer.CrumblingOverlay;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.network.chat.Style;
import net.minecraft.util.ARGB;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.phys.Vec3;

import top.csituka.youzaiworldcore.block.LargeSignBlock;
import top.csituka.youzaiworldcore.block.entity.LargeSignBlockEntity;
import top.csituka.youzaiworldcore.util.DebugLogger;

/**
 * 大字牌方块实体渲染器：只负责牌面上那一个大字。
 * <p>
 * 牌板本身是普通方块模型（可被区块网格烘焙），不在这里画，
 * 因此本渲染器每个字牌每帧只提交一次文本绘制，开销极低。
 * <p>
 * <b>排版规则</b>：文字根据当前字体烘焙后的实际字形边界等比缩放，放进牌面正中的
 * 14×14 像素框内（方块面为 16×16 像素，四周各留 1 像素边距）；切换原版字体、
 * MCsans 或包含表情符号的字形时，宽高都不会超过该上限。
 * <b>颜色与发光</b>完全对齐原版告示牌：不发光时用染色的 40% 暗色并吃方块光照；
 * 发光时用染色原色 + 满亮度，近距离额外描一圈暗色边。
 *
 * @see LargeSignBlockEntity
 */
@SuppressWarnings("null")
public class LargeSignBlockEntityRenderer
        implements BlockEntityRenderer<LargeSignBlockEntity, LargeSignRenderState> {

    /** 牌面可用的文字边长（方块像素）。 */
    private static final float TEXT_BOX_SIZE = 14.0f;

    /** 方块像素 → 方块局部坐标的换算：1 像素 = 1/16 格。 */
    private static final float PIXEL = 1.0f / 16.0f;

    /**
     * 牌面到方块中心的距离（格）。
     * <p>
     * 牌板厚 2 像素、贴着支撑方块，故可见面距中心 8-2=6 像素 = 0.375 格。
     */
    private static final float FACE_OFFSET = 0.375f;

    /** 文字再往外推的距离，避免与牌面共面导致 Z-fighting。 */
    private static final float TEXT_LIFT = 0.01f;

    /** 发光文字描边的可见距离（平方值），与原版告示牌一致。 */
    private static final double OUTLINE_RENDER_DISTANCE_SQR = 256.0;

    /** 黑色染料 + 发光时原版改用的浅色，避免「黑底黑字」看不清。 */
    private static final int GLOWING_BLACK_TEXT_COLOR = 0xFFF0EBCC;

    private final Font font;

    public LargeSignBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
        this.font = context.font();
        DebugLogger.info("LargeSignRenderer", "大字牌方块实体渲染器已创建");
    }

    @Override
    public LargeSignRenderState createRenderState() {
        return new LargeSignRenderState();
    }

    @Override
    public void extractRenderState(LargeSignBlockEntity entity,
            LargeSignRenderState state,
            float tickProgress,
            Vec3 cameraPos,
            CrumblingOverlay crumblingOverlay) {
        BlockEntityRenderer.super.extractRenderState(entity, state, tickProgress, cameraPos, crumblingOverlay);

        state.facing = entity.getBlockState().getValue(LargeSignBlock.FACING);

        String rawText = entity.getText();
        if (rawText.isEmpty()) {
            // 空字牌：submit 阶段直接跳过
            state.text = null;
            return;
        }
        state.text = FormattedCharSequence.forward(rawText, Style.EMPTY);

        // ── 颜色 / 发光（对齐原版 AbstractSignRenderer）──
        DyeColor color = entity.getColor();
        int darkColor = darkColor(color, entity.isGlowing());
        state.glowing = entity.isGlowing();
        if (state.glowing) {
            int bright = color.getTextColor();
            boolean outline = bright != DyeColor.BLACK.getTextColor() || isOutlineVisible(entity, cameraPos);
            state.textColor = bright;
            state.outlineColor = outline ? darkColor : 0;
            state.textLightCoords = LightCoordsUtil.FULL_BRIGHT;
        } else {
            state.textColor = darkColor;
            state.outlineColor = 0;
            state.textLightCoords = state.lightCoords;
        }

        // ── 排版：等比塞进 14×14 像素框并居中 ──
        // 使用字体烘焙后的实际字形边界计算，覆盖不同字体资源包、表情符号
        // 和字形自身超出标准行高的情况。空白字形没有边界时回退到步进宽度 / 行高。
        var textBounds = font.prepareText(state.text, 0.0f, 0.0f,
                0xFFFFFFFF, false, false, 0).bounds();
        float textWidth;
        float textHeight;
        float textLeft;
        float textTop;
        if (textBounds == null) {
            textWidth = Math.max(1.0f, font.width(state.text));
            textHeight = Math.max(1.0f, font.lineHeight);
            textLeft = 0.0f;
            textTop = 0.0f;
        } else {
            textWidth = Math.max(1.0f, textBounds.width());
            textHeight = Math.max(1.0f, textBounds.height());
            textLeft = textBounds.left();
            textTop = textBounds.top();
        }
        float fit = Math.min(TEXT_BOX_SIZE / textWidth, TEXT_BOX_SIZE / textHeight);

        state.scale = fit * PIXEL;
        state.offsetX = -(textLeft + textWidth / 2.0f);
        state.offsetY = -(textTop + textHeight / 2.0f);
    }

    @Override
    public void submit(LargeSignRenderState state,
            PoseStack matrices,
            SubmitNodeCollector queue,
            CameraRenderState cameraState) {
        if (state.text == null) {
            return;
        }

        matrices.pushPose();

        // 1) 牌板位于朝向的反方向（靠着支撑方块），先移到可见牌面，
        //    再沿牌面朝向往外抬一点点，避免与模型共面导致 Z-fighting。
        matrices.translate(
                0.5f - state.facing.getStepX() * FACE_OFFSET
                        + state.facing.getStepX() * TEXT_LIFT,
                0.5f,
                0.5f - state.facing.getStepZ() * FACE_OFFSET
                        + state.facing.getStepZ() * TEXT_LIFT);

        // 2) 绕 Y 轴转到该朝向。取负是因为文本空间的 +X 要落在「读牌人的右手边」，
        //    而 Direction.toYRot() 描述的是朝向本身的偏航角，两者旋向相反。
        matrices.mulPose(Axis.YP.rotationDegrees(-state.facing.toYRot()));

        // 3) 文本空间 Y 轴朝下（GUI 约定），故 Y 取负；同时缩放到目标字号
        matrices.scale(state.scale, -state.scale, state.scale);

        queue.submitText(
                matrices,
                state.offsetX,
                state.offsetY,
                state.text,
                false,                       // 不投阴影：字牌上的阴影会糊成一团
                Font.DisplayMode.POLYGON_OFFSET,
                state.textLightCoords,
                state.textColor,
                0,                           // 无背景色
                state.outlineColor);

        matrices.popPose();
    }

    /**
     * 计算不发光时使用的暗色（原版 {@code AbstractSignRenderer.getDarkColor} 的等价实现）。
     *
     * @param color   文字染色
     * @param glowing 是否发光
     * @return ARGB 颜色
     */
    private static int darkColor(DyeColor color, boolean glowing) {
        int textColor = color.getTextColor();
        if (textColor == DyeColor.BLACK.getTextColor() && glowing) {
            return GLOWING_BLACK_TEXT_COLOR;
        }
        return ARGB.scaleRGB(textColor, 0.4f);
    }

    /**
     * 判断当前是否应为发光的黑字额外描边（离得太远就不描，省开销）。
     *
     * @param entity    目标字牌
     * @param cameraPos 相机位置
     * @return 需要描边时返回 true
     */
    private static boolean isOutlineVisible(LargeSignBlockEntity entity, Vec3 cameraPos) {
        Minecraft minecraft = Minecraft.getInstance();
        var player = minecraft.player;
        if (player != null && minecraft.options.getCameraType().isFirstPerson() && player.isScoping()) {
            // 使用望远镜时始终描边，与原版一致
            return true;
        }

        Entity camera = minecraft.getCameraEntity();
        Vec3 signCenter = Vec3.atCenterOf(entity.getBlockPos());
        Vec3 reference = camera != null ? camera.position() : cameraPos;
        return reference.distanceToSqr(signCenter) < OUTLINE_RENDER_DISTANCE_SQR;
    }
}
