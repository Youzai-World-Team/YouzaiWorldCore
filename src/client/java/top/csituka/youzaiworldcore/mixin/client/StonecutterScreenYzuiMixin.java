package top.csituka.youzaiworldcore.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.StonecutterScreen;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import top.csituka.youzaiworldcore.client.render.YzuiWorkstationStyle;
import top.csituka.youzaiworldcore.client.screen.YzuiWorkstationScreen;

/** 切石机配方格使用主题底色，配方图标和点击行为仍由原版处理。 */
@Mixin(StonecutterScreen.class)
public abstract class StonecutterScreenYzuiMixin {
    @WrapOperation(method = "extractButtons", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;blitSprite(Lcom/mojang/renderpearl/api/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIII)V"))
    private void youzaiworldcore$themeRecipe(GuiGraphicsExtractor g, RenderPipeline pipeline, Identifier sprite,
            int x, int y, int w, int h, Operation<Void> original) {
        if (!((Object) this instanceof YzuiWorkstationScreen)
                || !YzuiWorkstationStyle.drawSprite(g, sprite, x, y, w, h)) {
            original.call(g, pipeline, sprite, x, y, w, h);
        }
    }
}
