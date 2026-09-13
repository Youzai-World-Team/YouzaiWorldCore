package top.csituka.youzaiworldcore.mixin.client.map;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.csituka.youzaiworldcore.client.map.MapRenderer;

/** HUD 完成原版提取后绘制地图，地图内部负责 F1 和服务器开关检查。 */
@Mixin(Hud.class)
public abstract class MapHudMixin {
    @Inject(method = "extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/DeltaTracker;)V", at = @At("RETURN"))
    private void youzaiworldcore$map(GuiGraphicsExtractor graphics, DeltaTracker delta, CallbackInfo ci) {
        MapRenderer.hud(graphics);
    }
}
