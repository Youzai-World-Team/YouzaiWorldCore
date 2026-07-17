package top.csituka.youzaiworldcore.client.pickup.display;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;

/**
 * 经验球拾取通知的显示条目。
 * <p>
 * 显示本次累计的经验值总量，使用绿色文本，
 * 并渲染自定义经验瓶贴图。
 * </p>
 */
@SuppressWarnings("null")
public class ExperienceDisplayEntry extends DisplayEntry<Void> {

    private static final int ICON_SIZE = 16;
    private static final int TEXT_ICON_MARGIN = 4;

    /** 经验球图标纹理 */
    private static final Identifier XP_ICON_TEXTURE =
            Identifier.fromNamespaceAndPath("youzaiworldcore", "textures/gui/experience_orb.png");

    /** 累计经验值 */
    private int totalXp;

    /**
     * 构造经验通知条目。
     *
     * @param xpValue     本次拾取的经验值
     * @param displayTime 显示持续 tick 数
     */
    public ExperienceDisplayEntry(int xpValue, int displayTime) {
        super(null, 1, displayTime);
        this.popTime = 5;
        this.totalXp = xpValue;
        this.displayComponent = buildDisplayComponent();
    }

    @Override
    public Object getKey() {
        return ExperienceDisplayEntry.class;
    }

    @Override
    protected Component getEntryName() {
        return Component.literal("经验");
    }

    @Override
    protected ChatFormatting getNameStyle() {
        return ChatFormatting.GREEN;
    }

    @Override
    public void mergeWith(DisplayEntry<?> other) {
        if (other instanceof ExperienceDisplayEntry xpEntry) {
            this.totalXp += xpEntry.totalXp;
            this.displayComponent = buildDisplayComponent();
            if (xpEntry.remainingTicks > this.remainingTicks) {
                this.remainingTicks = xpEntry.remainingTicks;
            }
        }
    }

    @Override
    protected Component buildDisplayComponent() {
        MutableComponent text = Component.literal("")
                .append(Component.literal("经验").copy().withStyle(getNameStyle()));
        text.append(Component.literal(" +" + totalXp)
                .withStyle(ChatFormatting.GREEN));
        return text;
    }

    @Override
    protected void renderSprite(GuiGraphicsExtractor graphics, int x, int y, int alpha) {
        if (popTime > 0) {
            float popScale = 1.0f + popTime / 5.0f * 0.3f;
            graphics.pose().pushMatrix();
            graphics.pose().translate(x + ICON_SIZE / 2.0f, y + ICON_SIZE / 2.0f);
            graphics.pose().scale(popScale, popScale);
            graphics.pose().translate(-ICON_SIZE / 2.0f, -ICON_SIZE / 2.0f);
            graphics.blit(RenderPipelines.GUI_TEXTURED, XP_ICON_TEXTURE, 0, 0, 0, 0, ICON_SIZE, ICON_SIZE, ICON_SIZE, ICON_SIZE);
            graphics.pose().popMatrix();
        } else {
            graphics.blit(RenderPipelines.GUI_TEXTURED, XP_ICON_TEXTURE, x, y, 0, 0, ICON_SIZE, ICON_SIZE, ICON_SIZE, ICON_SIZE);
        }
    }

    @Override
    public int getWidth() {
        Minecraft client = Minecraft.getInstance();
        int textWidth = client.font.width(displayComponent);
        return ICON_SIZE + TEXT_ICON_MARGIN + textWidth + 6;
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof ExperienceDisplayEntry;
    }

    @Override
    public int hashCode() {
        return 31;
    }
}
