package top.csituka.youzaiworldcore.mixin.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import top.csituka.youzaiworldcore.client.screen.YzuCreativeInventoryScreen;
import top.csituka.youzaiworldcore.client.screen.YzuInventoryScreen;

/**
 * 在 YZUI 物品栏屏幕上屏蔽 Trinkets 注入到 InventoryMenu 的 SurvivalTrinketSlot。
 * <p>
 * Trinkets 4.x 通过 InventoryMenuMixin 把真实槽位注入物品栏菜单，其 {@code isActive()} 依赖
 * TrinketsClient 的 {@code activeGroup}/{@code quickMoveGroup} 状态：shift+点击（QUICK_MOVE）
 * 放入饰品后，这些状态会被设置，导致槽位在胸甲位等原版装备位两侧渲染出"无用格子"（需重开
 * 物品栏才会因菜单关闭清理状态而消失）。
 * <p>
 * YZUI 使用自己的指示器系统交互，不需要这些注入槽位渲染或参与点击。因此当当前屏幕为
 * YzuInventoryScreen / YzuCreativeInventoryScreen 时，强制 {@code isActive()} 返回 false：
 * <ul>
 * <li>渲染：AbstractContainerScreen.extractSlots / YZUI drawSlotBackgrounds 均按 isActive 过滤，
 * 格子不再出现；</li>
 * <li>命中：AbstractContainerScreen.getHoveredSlot / YZUI getSlotAt 均按 isActive 过滤，
 * 点击不会误命中注入槽位（其坐标恰与 YZUI 指示器重叠）；</li>
 * <li>Trinkets 原生物品栏（yzuiEnabled 关闭时）不受影响——屏幕不是 YZUI 子类，不拦截。</li>
 * </ul>
 * 目标类通过 {@code @Mixin(targets = ...)} 字符串形式声明（eu.pb4.trinkets.impl.SurvivalTrinketSlot），
 * 避免编译期强依赖 Trinkets 实现包，运行时由 Mixin 系统按类名解析。
 */
@Mixin(targets = "eu.pb4.trinkets.impl.SurvivalTrinketSlot")
public abstract class SurvivalTrinketSlotYzuiMixin {

    @Inject(method = "isActive", at = @At("HEAD"), cancellable = true)
    private void yzwc$forceInactiveInYzui(CallbackInfoReturnable<Boolean> cir) {
        Screen screen = Minecraft.getInstance().gui.screen();
        if (screen instanceof YzuInventoryScreen || screen instanceof YzuCreativeInventoryScreen) {
            cir.setReturnValue(false);
        }
    }
}
