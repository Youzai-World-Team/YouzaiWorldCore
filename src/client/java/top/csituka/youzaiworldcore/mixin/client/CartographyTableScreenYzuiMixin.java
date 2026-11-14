package top.csituka.youzaiworldcore.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.CartographyTableScreen;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import top.csituka.youzaiworldcore.client.render.YzuiWorkstationStyle;
import top.csituka.youzaiworldcore.client.screen.YzuiWorkstationScreen;

/** 制图台地图预览共用主题底板；地图数据、复制层次和锁定标识沿用原版。 */
@Mixin(CartographyTableScreen.class)
public abstract class CartographyTableScreenYzuiMixin {
    @WrapOperation(method = "extractResultingMap", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;blitSprite(Lcom/mojang/renderpearl/api/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIII)V"))
    private void youzaiworldcore$themeMapFrame(GuiGraphicsExtractor g, RenderPipeline pipeline, Identifier sprite,
            int x, int y, int w, int h, Operation<Void> original) {
        if (!((Object) this instanceof YzuiWorkstationScreen)
                || !YzuiWorkstationStyle.drawSprite(g, sprite, x, y, w, h)) {
            original.call(g, pipeline, sprite, x, y, w, h);
        }
    }
}
