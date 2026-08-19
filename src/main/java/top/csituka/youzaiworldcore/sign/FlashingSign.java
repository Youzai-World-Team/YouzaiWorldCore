package top.csituka.youzaiworldcore.sign;

/**
 * 为原版告示牌提供独立于荧光文字的闪烁状态。
 * <p>
 * 原版告示牌正面与背面分别保存状态；大字牌只使用正面参数。
 */
public interface FlashingSign {

    /**
     * 查询某一面是否处于闪烁状态。
     *
     * @param front {@code true} 表示正面，{@code false} 表示背面
     * @return 是否闪烁
     */
    boolean youzaiworldcore$isFlashing(boolean front);

    /**
     * 设置某一面的闪烁状态。
     *
     * @param front    {@code true} 表示正面，{@code false} 表示背面
     * @param flashing 目标状态
     * @return 状态实际发生变化时返回 true
     */
    boolean youzaiworldcore$setFlashing(boolean front, boolean flashing);

    /**
     * @return 任意一面处于闪烁状态时返回 true
     */
    default boolean youzaiworldcore$isAnyFlashing() {
        return youzaiworldcore$isFlashing(true) || youzaiworldcore$isFlashing(false);
    }
}
