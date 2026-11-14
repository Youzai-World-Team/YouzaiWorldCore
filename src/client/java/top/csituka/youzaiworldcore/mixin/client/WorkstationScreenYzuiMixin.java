package top.csituka.youzaiworldcore.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CartographyTableScreen;
import net.minecraft.client.gui.screens.inventory.EnchantmentScreen;
import net.minecraft.client.gui.screens.inventory.GrindstoneScreen;
import net.minecraft.client.gui.screens.inventory.LoomScreen;
import net.minecraft.client.gui.screens.inventory.StonecutterScreen;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import top.csituka.youzaiworldcore.client.render.YzuiWorkstationStyle;
import top.csituka.youzaiworldcore.client.screen.YzuiWorkstationScreen;

/** 仅替换五种 YZUI 工作方块的底板，继续执行原版的书本、图案和地图预览逻辑。 */
@Mixin({ EnchantmentScreen.class, LoomScreen.class, CartographyTableScreen.class,
        StonecutterScreen.class, GrindstoneScreen.class })
public abstract class WorkstationScreenYzuiMixin {
    // super 调用的接收者是各具体屏幕；允许五种屏幕共用其父类参数。
    @WrapOperation(method = "extractBackground", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/screens/inventory/AbstractContainerScreen;extractBackground(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIF)V"))
    private void youzaiworldcore$skipDuplicateBackdrop(@Coerce AbstractContainerScreen<?> screen, GuiGraphicsExtractor g,
            int mouseX, int mouseY, float partialTick, Operation<Void> original) {
        if (!(screen instanceof YzuiWorkstationScreen)) {
            original.call(screen, g, mouseX, mouseY, partialTick);
        }
    }

    @WrapOperation(method = "extractBackground", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;blit(Lcom/mojang/renderpearl/api/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIFFIIII)V"))
    private void youzaiworldcore$skipNativePanel(GuiGraphicsExtractor g, RenderPipeline pipeline, Identifier texture,
            int x, int y, float u, float v, int w, int h, int textureWidth, int textureHeight, Operation<Void> original) {
        if (!((Object) this instanceof YzuiWorkstationScreen)) {
            original.call(g, pipeline, texture, x, y, u, v, w, h, textureWidth, textureHeight);
        }
    }

    @WrapOperation(method = "extractBackground", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;blitSprite(Lcom/mojang/renderpearl/api/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIII)V"))
    private void youzaiworldcore$themeControls(GuiGraphicsExtractor g, RenderPipeline pipeline, Identifier sprite,
            int x, int y, int w, int h, Operation<Void> original) {
        if (!((Object) this instanceof YzuiWorkstationScreen)
                || !YzuiWorkstationStyle.drawSprite(g, sprite, x, y, w, h)) {
            original.call(g, pipeline, sprite, x, y, w, h);
        }
    }
}
