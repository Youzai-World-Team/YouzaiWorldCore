package top.csituka.youzaiworldcore.client.renderer.entity.state;

import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.phys.Vec3;
import top.csituka.youzaiworldcore.text.StyledTextUtil.GlyphSlot;

import java.util.ArrayList;
import java.util.List;

/**
 * 动画字幕实体的渲染状态。
 * <p>
 * 在 {@code extractRenderState()} 阶段从实体中提取所有渲染所需的数据，
 * 渲染时不再依赖实体对象，从而避免跨线程问题。
 * </p>
 */
public class AnimationSubtitleEntityRenderState extends EntityRenderState {

    /** 字幕模式：0=主字幕, 1=碎片 */
    public int mode;

    /** 显示文本 */
    public String text;

    /** 基础缩放 */
    public float baseScale;

    /** 可见字符数 */
    public int visibleCount;

    /** 已掉落字符数 */
    public int droppedCount;

    /** 保持时间 */
    public int holdTicks;

    /** 碎片状态 */
    public int shardState;

    /** 缩小因子 */
    public float shrinkFactor;

    /** 透明度 (0-255) */
    public int alpha;

    /** RGB 颜色 */
    public int rgbColor;

    /** Y 轴旋转（弧度） */
    public float yRot;

    /** 实体年龄 (tick) */
    public int age;

    /** 字形槽位列表 */
    public List<GlyphSlot> glyphs;

    /** 字形宽度列表 */
    public List<Float> charWidths;

    /** 字形总宽度 */
    public float totalWidth;

    /** 字形总高度（行数） */
    public float totalHeight;

    /** 是否在地面上（碎片用） */
    public boolean onGround;

    /** 沉降进度 (0.0-1.0) */
    public float settleProgress;

    public AnimationSubtitleEntityRenderState() {
        this.glyphs = List.of();
        this.charWidths = List.of();
    }
}
