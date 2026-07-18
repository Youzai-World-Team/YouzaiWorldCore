package top.csituka.youzaiworldcore.client.pickup.display;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.NonNull;

/**
 * 物品拾取通知的显示条目。
 * <p>
 * 显示物品图标、名称（按稀有度着色）和数量。
 * 支持同类物品合并以及拾取瞬间的弹出动画。
 * </p>
 */
@SuppressWarnings("null")
public class ItemDisplayEntry extends DisplayEntry<ItemStack> {

    /** 弹出动画初始 tick 数 */
    private static final int POP_TIME = 5;

    /** 图标宽高 */
    private static final int ICON_SIZE = 16;

    /** 文字与图标间距 */
    private static final int TEXT_ICON_MARGIN = 2;

    private final ChatFormatting rarityFormat;

    /**
     * 构造物品通知条目。
     *
     * @param stack       物品堆
     * @param amount      拾取数量
     * @param displayTime 显示持续 tick 数
     */
    public ItemDisplayEntry(ItemStack stack, int amount, int displayTime) {
        super(stack, amount, displayTime);
        this.popTime = POP_TIME;
        this.rarityFormat = getRarityFormat(stack);
        this.displayComponent = buildDisplayComponent();
    }

    @Override
    public Object getKey() {
        return this;
    }

    @Override
    @NonNull
    protected Component getEntryName() {
        return data.getHoverName();
    }

    @Override
    @NonNull
    protected ChatFormatting getNameStyle() {
        return rarityFormat;
    }

    @Override
    public void mergeWith(DisplayEntry<?> other) {
        if (other instanceof ItemDisplayEntry itemEntry) {
            this.displayAmount += itemEntry.displayAmount;
            this.displayComponent = buildDisplayComponent();
            if (itemEntry.remainingTicks > this.remainingTicks) {
                this.remainingTicks = itemEntry.remainingTicks;
            }
        }
    }

    @Override
    protected Component buildDisplayComponent() {
        MutableComponent text = Component.literal("")
                .append(getEntryName().copy().withStyle(getNameStyle()));

        if (displayAmount > 1) {
            text.append(Component.literal(" ×" + getAmountText()).withStyle(ChatFormatting.WHITE));
        }
        return text;
    }

    @Override
    protected void renderSprite(GuiGraphicsExtractor graphics, int x, int y, int alpha) {
        if (data.isEmpty()) return;

        // 弹出动画：围绕中心缩放
        if (popTime > 0) {
            float popScale = 1.0f + popTime / (float) POP_TIME * 0.3f;
            // 使用 PoseStack 进行缩放
            graphics.pose().pushMatrix();
            graphics.pose().translate(x + ICON_SIZE / 2.0f, y + ICON_SIZE / 2.0f);
            graphics.pose().scale(popScale, popScale);
            graphics.pose().translate(-ICON_SIZE / 2.0f, -ICON_SIZE / 2.0f);
            graphics.item(data, 0, 0);
            graphics.pose().popMatrix();
        } else {
            graphics.item(data, x, y);
        }
    }

    /**
     * 获取物品稀有度对应的文字格式。
     */
    private static ChatFormatting getRarityFormat(ItemStack stack) {
        return switch (stack.getRarity()) {
            case COMMON -> ChatFormatting.WHITE;
            case UNCOMMON -> ChatFormatting.YELLOW;
            case RARE -> ChatFormatting.AQUA;
            case EPIC -> ChatFormatting.LIGHT_PURPLE;
        };
    }

    @Override
    public int getWidth() {
        Minecraft client = Minecraft.getInstance();
        int textWidth = client.font.width(displayComponent);
        return ICON_SIZE + TEXT_ICON_MARGIN + textWidth + 6;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof ItemDisplayEntry other)) return false;
        return ItemStack.isSameItem(data, other.data)
                && data.getRarity() == other.data.getRarity()
                && data.isEnchanted() == other.data.isEnchanted();
    }

    @Override
    public int hashCode() {
        int result = data.getItem().hashCode();
        result = 31 * result + data.getRarity().hashCode();
        result = 31 * result + (data.isEnchanted() ? 1 : 0);
        return result;
    }
}
