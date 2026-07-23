package top.csituka.youzaiworldcore.mixin.client.itemborder;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import top.csituka.youzaiworldcore.itemborder.ItemBorderRenderer;

/**
 * 为 {@link Hud} 中快捷栏物品槽位绘制稀有度边框。
 * <p>
 * 注入点：{@code extractSlot(GuiGraphicsExtractor, int, int, DeltaTracker, Player, ItemStack, int)}
 * 的 RETURN，此时单个热栏物品已绘制完成。
 * </p>
 *
 * <p>该注入仅当配置 {@code hotbar = true} 时生效（由 {@link ItemBorderRenderer} 内部判断）。</p>
 */
@Mixin(Hud.class)
public abstract class HudHotbarMixin {

    /**
     * 在热栏槽位渲染后绘制边框。
     *
     * @param gui     GuiGraphicsExtractor 实例
     * @param x       槽位屏幕 X 坐标
     * @param y       槽位屏幕 Y 坐标
     * @param tracker 帧时间追踪器
     * @param player  当前玩家
     * @param item    槽位中物品
     * @param something 未知整数参数（原版内部传递）
     * @param ci      回调信息
     */
    @Inject(method = "extractSlot",
            at = @At("RETURN"))
    private void afterExtractSlot(GuiGraphicsExtractor gui, int x, int y,
                                  DeltaTracker tracker, Player player,
                                  ItemStack item, int something,
                                  CallbackInfo ci) {
        ItemBorderRenderer.renderBorder(gui, x, y, item);
    }
}
