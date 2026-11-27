package top.csituka.youzaiworldcore.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.CrafterScreen;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import top.csituka.youzaiworldcore.client.render.YzuiWorkstationStyle;
import top.csituka.youzaiworldcore.client.screen.YzuiWorkstationScreen;

/** 根据原版合成器选定的图标绘制主题槽位标记和红石箭头。 */
@Mixin(CrafterScreen.class)
public abstract class CrafterScreenYzuiMixin {
    @WrapOperation(method = {"extractDisabledSlot", "extractRedstone"}, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;blitSprite(Lcom/mojang/renderpearl/api/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIII)V"),
            require = 2)
    private void youzaiworldcore$themeIndicators(GuiGraphicsExtractor g, RenderPipeline pipeline, Identifier sprite,
            int x, int y, int w, int h, Operation<Void> original) {
        if (!((Object) this instanceof YzuiWorkstationScreen)
                || !YzuiWorkstationStyle.drawSprite(g, sprite, x, y, w, h)) {
            original.call(g, pipeline, sprite, x, y, w, h);
        }
    }
}
