package top.csituka.youzaiworldcore.mixin.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.tooltip.TooltipRenderUtil;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.csituka.youzaiworldcore.client.render.YzuiTheme;

/** 物品和控件提示使用同一圆角卡片，位置与原版提示定位器保持一致。 */
@Mixin(TooltipRenderUtil.class)
public class TooltipRenderUtilYzuiMixin {
    @Inject(method = "extractTooltipBackground", at = @At("HEAD"), cancellable = true)
    private static void youzaiworldcore$tooltip(GuiGraphicsExtractor g, int x, int y,
            int width, int height, Identifier texture, CallbackInfo ci) {
        if (!YzuiTheme.enabled()) return;
        YzuiTheme.card(g, x - 5, y - 5, width + 10, height + 10);
        ci.cancel();
    }
}
