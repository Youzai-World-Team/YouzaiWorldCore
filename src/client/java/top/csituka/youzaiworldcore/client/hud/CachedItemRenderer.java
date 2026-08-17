package top.csituka.youzaiworldcore.client.hud;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.item.TrackingItemStackRenderState;
import net.minecraft.client.renderer.state.gui.GuiItemRenderState;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix3x2f;

/**
 * 缓存式物品图标渲染器。
 *
 * <p>26.2 的 {@link GuiGraphicsExtractor#item} 每次调用都会执行一次
 * {@link ItemModelResolver#updateForTopItem}（模型解析，含纹理查找/层构建/附魔光检测），
 * 并新建 3 个对象。HUD 每帧渲染 36 个物品时开销显著。</p>
 *
 * <p>本类缓存解析结果 {@link TrackingItemStackRenderState}，仅当物品身份
 * （物品类型 + 组件，忽略数量）变化时才重新解析。渲染阶段复用缓存的
 * render state 提交到 {@link GuiRenderState}，与 vanilla 一致是延迟渲染。</p>
 *
 * <p>每个槽位应持有独立的 {@link CachedItemRenderer} 实例。</p>
 */
@SuppressWarnings("null")
public final class CachedItemRenderer {

    private final TrackingItemStackRenderState state = new TrackingItemStackRenderState();
    private ItemStack resolvedStack = ItemStack.EMPTY;

    /**
     * 渲染一个物品图标（缓存模型解析结果）。
     *
     * @param g     GuiGraphicsExtractor 实例
     * @param stack 要渲染的物品（非空）
     * @param x     屏幕 X 坐标
     * @param y     屏幕 Y 坐标
     */
    public void render(GuiGraphicsExtractor g, ItemStack stack, int x, int y) {
        render(g, stack, x, y, 1.0F);
    }

    /**
     * 渲染一个带透明度的物品图标。
     *
     * @param g GuiGraphicsExtractor 实例
     * @param stack 要渲染的物品（非空）
     * @param x 屏幕 X 坐标
     * @param y 屏幕 Y 坐标
     * @param opacity 最终透明度，范围 0..1
     */
    public void render(GuiGraphicsExtractor g, ItemStack stack, int x, int y, float opacity) {
        if (stack.isEmpty())
            return;

        // 仅在物品身份变化时重新解析模型
        if (!ItemStack.isSameItemSameComponents(stack, resolvedStack)) {
            resolve(stack);
        }

        submit(g, x, y, opacity);
    }

    /**
     * 强制重新解析模型（当物品组件在本地被修改时调用）。
     */
    public void invalidate() {
        resolvedStack = ItemStack.EMPTY;
    }

    // ===== 内部 =====

    private void resolve(ItemStack stack) {
        Minecraft mc = Minecraft.getInstance();
        ItemModelResolver resolver = mc.getItemModelResolver();

        // 复用 state 前必须清空，等效于 vanilla 每次 new TrackingItemStackRenderState()
        state.clear();
        resolver.updateForTopItem(state, stack,
                ItemDisplayContext.GUI, mc.level, mc.player, 0);
        resolvedStack = stack.copy();
    }

    private void submit(GuiGraphicsExtractor g, int x, int y, float opacity) {
        GuiRenderState renderState = g.guiRenderState;
        GuiItemRenderState itemState = new GuiItemRenderState(
                new Matrix3x2f(g.pose()),
                state,
                x, y,
                g.scissorStack.peek());
        ((YzHudItemOpacityAccess) (Object) itemState)
                .youzaiworldcore$setOpacity(opacity);
        renderState.addItem(itemState);
    }

    /**
     * 供调试使用：获取缓存的解析状态（如物品变化检测）。
     */
    public ItemStackRenderState debugState() {
        return state;
    }
}
