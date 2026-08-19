package top.csituka.youzaiworldcore.client.renderer.block;

import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.core.Direction;
import net.minecraft.util.FormattedCharSequence;

/**
 * 大字牌的客户端渲染状态。
 * <p>
 * 26.2 的方块实体渲染分成 extract / submit 两步：
 * {@code extractRenderState} 在主线程从方块实体抓取数据填进本对象，
 * {@code submit} 在渲染线程只读取本对象。因此这里保存的必须是
 * 已经算好的不可变快照，不能持有方块实体引用。
 */
public class LargeSignRenderState extends BlockEntityRenderState {

    /** 牌面文本的可渲染序列；为 null 表示字牌为空、无需绘制。 */
    public FormattedCharSequence text;

    /** 字牌朝向（牌面法线方向）。 */
    public Direction facing = Direction.NORTH;

    /** 文字最终颜色（已按发光 / 不发光算好）。 */
    public int textColor;

    /** 描边颜色；0 表示不描边。 */
    public int outlineColor;

    /** 是否发光（决定使用满亮度还是方块光照）。 */
    public boolean glowing;

    /** 本帧使用的光照坐标（发光时为满亮度）。 */
    public int textLightCoords;

    /** 把文本缩放到 14×14 像素框内的缩放系数（已含 1/16 的方块像素换算）。 */
    public float scale;

    /** 文本绘制原点的 X 偏移（文本空间，用于水平居中）。 */
    public float offsetX;

    /** 文本绘制原点的 Y 偏移（文本空间，用于垂直居中）。 */
    public float offsetY;
}
