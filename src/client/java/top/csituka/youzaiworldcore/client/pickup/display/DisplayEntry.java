package top.csituka.youzaiworldcore.client.pickup.display;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.jspecify.annotations.NonNull;

/**
 * 拾取通知显示条目的抽象基类。
 * <p>
 * 每个条目代表一条拾取通知，包含图标、文字描述和数量。
 * 支持 tick 倒计时、移出动画、淡出效果。
 * </p>
 *
 * @param <T> 条目携带的数据类型（ItemStack 或 Component）
 */
@SuppressWarnings("null")
public abstract class DisplayEntry<T> {

    /** 每行高度（像素） */
    public static final int ELEMENT_HEIGHT = 18;

    /** 文字与图标之间的间距 */
    protected static final int TEXT_ICON_MARGIN = 4;

    /** 条目携带的数据 */
    protected final T data;

    /** 显示数量 */
    protected int displayAmount;

    /** 已拼装好的显示文本组件 */
    protected Component displayComponent;

    /** 剩余 tick 数 */
    protected int remainingTicks;

    /** 总显示时间（tick） */
    protected final int maxDisplayTime;

    /** 移出动画持续时间（tick） */
    protected int moveOutDuration;

    /** 是否正在移出 */
    protected boolean isMovingOut;

    /** 当前移动进度 [0.0, 1.0] */
    protected float moveOutProgress;

    /** 淡入动画剩余 tick */
    protected int fadeInTime;

    /** 淡入动画总时长（tick） */
    protected static final int FADE_IN_DURATION = 5;

    /** 弹出动画剩余 tick（用于拾取瞬间的弹入效果） */
    protected int popTime;

    /**
     * 构造一个新的显示条目。
     *
     * @param data        条目数据
     * @param amount      数量
     * @param displayTime 显示时间（tick）
     */
    protected DisplayEntry(T data, int amount, int displayTime) {
        this.data = data;
        this.displayAmount = amount;
        this.remainingTicks = displayTime;
        this.maxDisplayTime = displayTime;
        this.moveOutDuration = Math.min(displayTime, 10); // 移出动画约 0.5s（20tick/s × 10tick）
        this.isMovingOut = false;
        this.moveOutProgress = 0.0f;
        this.fadeInTime = FADE_IN_DURATION;
        this.popTime = 0;
        // displayComponent 由子类构造器在设置完字段后自行调用 buildDisplayComponent()
    }

    /**
     * 获取条目数据的键，用于合并相同条目。
     */
    public abstract Object getKey();

    /**
     * 获取条目的显示名称。
     */
    protected abstract Component getEntryName();

    /**
     * 将另一个条目合并到当前条目中。
     *
     * @param other 要被合并的条目
     */
    public abstract void mergeWith(DisplayEntry<?> other);

    /**
     * 渲染条目图标（由子类实现具体渲染方式）。
     *
     * @param graphics   GUI 渲染上下文
     * @param x          图标 X 坐标
     * @param y          图标 Y 坐标
     * @param alpha      透明度 [0, 255]
     */
    protected abstract void renderSprite(GuiGraphicsExtractor graphics, int x, int y, int alpha);

    /**
     * 构建显示文本组件。
     */
    protected Component buildDisplayComponent() {
        MutableComponent text = Component.literal("")
                .append(getEntryName().copy().withStyle(getNameStyle()))
                .append(Component.literal(" ×" + displayAmount).withStyle(ChatFormatting.WHITE));
        return text;
    }

    /**
     * 获取名称样式（基于稀有度）。
     */
    @NonNull
    protected ChatFormatting getNameStyle() {
        return ChatFormatting.WHITE;
    }

    /**
     * 每 tick 更新。
     */
    public void tick() {
        if (remainingTicks > 0) {
            remainingTicks--;
        }
        if (fadeInTime > 0) {
            fadeInTime--;
        }
        if (popTime > 0) {
            popTime--;
        }
        if (isMovingOut) {
            moveOutProgress += 1.0f / moveOutDuration;
            if (moveOutProgress >= 1.0f) {
                moveOutProgress = 1.0f;
            }
        }
    }

    /**
     * 判断此条目是否应该被移除。
     */
    public boolean shouldDiscard() {
        return remainingTicks <= 0 && moveOutProgress >= 1.0f;
    }

    /**
     * 标记该条目开始移出。
     */
    public void startMovingOut() {
        if (!isMovingOut) {
            isMovingOut = true;
            moveOutProgress = 0.0f;
        }
    }

    /**
     * 刷新显示时间（合并相同条目时调用），延长显示时间但不会重播淡入动画。
     */
    protected void refreshDisplay(int newRemainingTicks) {
        if (newRemainingTicks > this.remainingTicks) {
            this.remainingTicks = newRemainingTicks;
        }
        this.isMovingOut = false;
        this.moveOutProgress = 0.0f;
    }

    /**
     * 判断此条目是否正在移出。
     */
    public boolean isMovingOut() {
        return isMovingOut;
    }

    /**
     * 获取相对剩余时间比例 [0.0, 1.0]。
     */
    public float getRelativeRemainingTime() {
        if (maxDisplayTime <= 0) return 1.0f;
        return Math.max(0.0f, (float) remainingTicks / maxDisplayTime);
    }

    /**
     * 获取淡出透明度 [0.0, 1.0]。
     * <ul>
     *   <li>淡入阶段：从 0 渐变为 1（{@value #FADE_IN_DURATION} tick）</li>
     *   <li>正常显示：保持 1.0</li>
     *   <li>移出阶段：从 1 渐变为 0（仅淡出一次，无重复）</li>
     * </ul>
     */
    public float getFadeAlpha() {
        // 淡入阶段
        if (fadeInTime > 0) {
            return (float) (FADE_IN_DURATION - fadeInTime) / FADE_IN_DURATION;
        }
        // 移出阶段（包含滑动 + 淡出，仅执行一次）
        if (isMovingOut) {
            return Math.max(0f, 1.0f - moveOutProgress);
        }
        // 正常显示
        return 1.0f;
    }

    /**
     * 获取移出偏移量（像素）。
     * 使用 ease-in 曲线（由慢到快），让滑出动画更自然。
     */
    public int getMoveOffset() {
        if (!isMovingOut) return 0;
        // ease-in: moveOutProgress²，开始时缓慢，后面加速
        float eased = moveOutProgress * moveOutProgress;
        return (int) (eased * 30); // 向右移出 30 像素
    }

    /**
     * 渲染此条目。
     *
     * @param graphics GUI 渲染上下文
     * @param x        渲染 X 坐标
     * @param y        渲染 Y 坐标
     * @param alpha    透明度 [0, 255]
     */
    public void render(GuiGraphicsExtractor graphics, int x, int y, int alpha) {
        // 弹出动画缩放
        float popScale = 1.0f;
        if (popTime > 0) {
            popScale = 1.0f + popTime / 10.0f;
        }

        // 图标
        if (popScale > 1.0f) {
            // 弹出动画期间，图标放大
            int iconSize = (int) (16 * popScale);
            int iconX = x + (16 - iconSize) / 2;
            int iconY = y + (ELEMENT_HEIGHT - iconSize) / 2;
            renderSprite(graphics, iconX, iconY, alpha);
        } else {
            renderSprite(graphics, x + 1, y + 1, alpha);
        }

        // 文字（无背景，常规字体）
        int textX = x + 18 + TEXT_ICON_MARGIN;
        int textY = y + (ELEMENT_HEIGHT - Minecraft.getInstance().font.lineHeight) / 2;
        graphics.text(Minecraft.getInstance().font, displayComponent, textX, textY,
                0xFFFFFF | (alpha << 24), false);
    }

    /**
     * 获取此条目的渲染宽度。
     */
    public int getWidth() {
        Minecraft client = Minecraft.getInstance();
        int textWidth = client.font.width(displayComponent);
        return 18 + TEXT_ICON_MARGIN + textWidth + 4; // 图标(18) + 间距 + 文字 + 边距
    }

    @Override
    public abstract boolean equals(Object obj);

    @Override
    public abstract int hashCode();

    /**
     * 获取数量描述文本。
     */
    public String getAmountText() {
        if (displayAmount >= 1_000_000_000) {
            return String.format("%.1fB", displayAmount / 1_000_000_000.0);
        } else if (displayAmount >= 1_000_000) {
            return String.format("%.1fM", displayAmount / 1_000_000.0);
        } else if (displayAmount >= 1000) {
            return String.format("%.1fK", displayAmount / 1000.0);
        }
        return String.valueOf(displayAmount);
    }
}
