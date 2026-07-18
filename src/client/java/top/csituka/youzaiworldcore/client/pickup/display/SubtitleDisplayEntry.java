package top.csituka.youzaiworldcore.client.pickup.display;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import top.csituka.youzaiworldcore.client.pickup.SubtitleCaptureHandler;

/**
 * 声音字幕的显示条目。
 * <p>
 * 显示刚播放的声音对应的字幕文本（如"脚步声"、"门打开"），
 * 并带有方向指示符（< / >），颜色为白色随剩余时间淡出。
 * 与拾取提示共享 {@link top.csituka.youzaiworldcore.client.pickup.DrawEntriesHandler} 的渲染区域。
 * </p>
 */
@SuppressWarnings("null")
public class SubtitleDisplayEntry extends DisplayEntry<Void> {

    private static final int ICON_SIZE = 0; // 无图标
    private static final int TEXT_ICON_MARGIN = 0;
    private static final int DIRECTION_MARGIN = 2;

    /** 方向指示文本 */
    private static final String INDICATOR_LEFT = " <";
    private static final String INDICATOR_RIGHT = "> ";
    private static final String INDICATOR_FORWARD = "";
    private static final String INDICATOR_BEHIND = " ";

    /** 字幕文本 */
    private final Component subtitleText;

    /** 方向指示 */
    private final SubtitleCaptureHandler.Direction direction;

    /**
     * 构造字幕显示条目。
     *
     * @param subtitleText 字幕文本组件
     * @param direction    声音方向
     * @param displayTime  显示持续 tick 数
     */
    public SubtitleDisplayEntry(Component subtitleText, SubtitleCaptureHandler.Direction direction, int displayTime) {
        super(null, 0, displayTime);
        this.subtitleText = subtitleText;
        this.direction = direction;
        this.displayComponent = buildDisplayComponent();
    }

    @Override
    public Object getKey() {
        return this;
    }

    @Override
    protected Component getEntryName() {
        return subtitleText;
    }

    @Override
    protected ChatFormatting getNameStyle() {
        return ChatFormatting.WHITE;
    }

    @Override
    public void mergeWith(DisplayEntry<?> other) {
        // 字幕不合并，每次播放创建新条目
    }

    @Override
    protected Component buildDisplayComponent() {
        MutableComponent text = switch (direction) {
            case LEFT -> Component.literal(INDICATOR_LEFT).withStyle(ChatFormatting.GRAY)
                    .append(subtitleText.copy().withStyle(getNameStyle()));
            case RIGHT -> subtitleText.copy().withStyle(getNameStyle())
                    .append(Component.literal(INDICATOR_RIGHT).withStyle(ChatFormatting.GRAY));
            case BEHIND -> Component.literal(INDICATOR_BEHIND).withStyle(ChatFormatting.GRAY)
                    .append(subtitleText.copy().withStyle(getNameStyle()));
            default -> subtitleText.copy().withStyle(getNameStyle());
        };
        return text;
    }

    @Override
    protected void renderSprite(GuiGraphicsExtractor graphics, int x, int y, int alpha) {
        // 字幕无图标
    }

    @Override
    public void render(GuiGraphicsExtractor graphics, int x, int y, int alpha) {
        // 字幕无图标，直接渲染文字（无偏移）
        int textX = x + 4;
        int textY = y + (ELEMENT_HEIGHT - Minecraft.getInstance().font.lineHeight) / 2;
        graphics.text(Minecraft.getInstance().font, displayComponent, textX, textY,
                0xFFFFFF | (alpha << 24), false);
    }

    @Override
    public int getWidth() {
        Minecraft client = Minecraft.getInstance();
        int textWidth = client.font.width(displayComponent);
        return TEXT_ICON_MARGIN + textWidth + 6;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof SubtitleDisplayEntry other)) return false;
        // 相同文本且方向一致视为同一条字幕
        return subtitleText.getString().equals(other.subtitleText.getString())
                && direction == other.direction;
    }

    @Override
    public int hashCode() {
        return subtitleText.getString().hashCode() * 31 + direction.ordinal();
    }
}
