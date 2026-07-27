package top.csituka.youzaiworldcore.mixin.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.recipebook.RecipeButton;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import com.mojang.blaze3d.pipeline.RenderPipeline;

/**
 * Mixin 替换 {@link RecipeButton#extractWidgetRenderState} 中的 blitSprite 调用，
 * 将原版配方槽纹理替换为 YZUI 半透明圆角背景。
 */
@Mixin(RecipeButton.class)
public class RecipeButtonMixin {

    @Unique
    private static final int YZWC_SLOT_BG = 0x50FFFFFF;

    @Redirect(
            method = "extractWidgetRenderState",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;blitSprite(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIII)V"),
            require = 1
    )
    private void yzwc$recipeSlotBackground(
            GuiGraphicsExtractor g,
            RenderPipeline pipeline,
            Identifier sprite,
            int x, int y,
            int w, int h
    ) {
        yzwc$fillRoundedRect(g, x, y, w, h, 3, YZWC_SLOT_BG);
    }

    @Unique
    private static void yzwc$fillRoundedRect(GuiGraphicsExtractor g, int x, int y, int w, int h, int r, int color) {
        g.fill(x + r, y, x + w - r, y + h, color);
        g.fill(x, y + r, x + r, y + h - r, color);
        g.fill(x + w - r, y + r, x + w, y + h - r, color);
        for (int i = 0; i < r; i++) {
            for (int j = 0; j < r; j++) {
                if (i * i + j * j < r * r) {
                    g.fill(x + r - i - 1, y + r - j - 1, x + r - i, y + r - j, color);
                    g.fill(x + w - r + i, y + r - j - 1, x + w - r + i + 1, y + r - j, color);
                    g.fill(x + r - i - 1, y + h - r + j, x + r - i, y + h - r + j + 1, color);
                    g.fill(x + w - r + i, y + h - r + j, x + w - r + i + 1, y + h - r + j + 1, color);
                }
            }
        }
    }
}
