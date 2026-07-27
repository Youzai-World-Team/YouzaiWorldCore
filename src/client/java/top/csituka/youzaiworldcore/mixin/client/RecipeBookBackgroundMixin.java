package top.csituka.youzaiworldcore.mixin.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import com.mojang.blaze3d.pipeline.RenderPipeline;

/**
 * Mixin 替换 {@link RecipeBookComponent#extractRenderState} 中的背景纹理 blit，
 * 改为绘制 YZUI 半透明白色圆角面板。
 * <p>
 * 原版 blit 调用：{@code g.blit(GUI_TEXTURED, RECIPE_BOOK_LOCATION, x, y, 1f, 1f, 147, 166, 256, 256)}
 * 替换为：YZUI 风格的半透明圆角矩形。
 */
@Mixin(RecipeBookComponent.class)
public class RecipeBookBackgroundMixin {

    @Unique
    private static final int YZWC_RECIPE_BOOK_BG = 0xC0FFFFFF;

    /**
     * 拦截 RecipeBookComponent.extractRenderState 中的 blit 调用，
     * 改为绘制 YZUI 圆角面板。
     */
    @Redirect(
            method = "extractRenderState",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;blit(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIFFIIII)V"),
            require = 1
    )
    private void yzwc$recipeBookBackground(
            GuiGraphicsExtractor g,
            RenderPipeline pipeline,
            Identifier tex,
            int x, int y,
            float u, float v,
            int w, int h,
            int tw, int th
    ) {
        yzwc$fillRoundedRect(g, x, y, w, h, 6, YZWC_RECIPE_BOOK_BG);
    }

    @Unique
    private static void yzwc$fillRoundedRect(GuiGraphicsExtractor g, int x, int y, int w, int h, int r, int color) {
        // 中间区域
        g.fill(x + r, y, x + w - r, y + h, color);
        // 左右边
        g.fill(x, y + r, x + r, y + h - r, color);
        g.fill(x + w - r, y + r, x + w, y + h - r, color);
        // 四角
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
