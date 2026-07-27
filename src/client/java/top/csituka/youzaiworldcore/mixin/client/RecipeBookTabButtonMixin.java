package top.csituka.youzaiworldcore.mixin.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.recipebook.RecipeBookTabButton;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import com.mojang.blaze3d.pipeline.RenderPipeline;

/**
 * Mixin 替换 {@link RecipeBookTabButton#extractContents} 中的 blitSprite 调用，
 * 将原版木质感 Tab 按钮替换为 YZUI 半透明圆角面板。
 */
@Mixin(RecipeBookTabButton.class)
public class RecipeBookTabButtonMixin {

    @Unique
    private static final int YZWC_TAB_BG = 0x80FFFFFF;

    @Redirect(
            method = "extractContents",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;blitSprite(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIII)V"),
            require = 1
    )
    private void yzwc$tabBackground(
            GuiGraphicsExtractor g,
            RenderPipeline pipeline,
            Identifier sprite,
            int x, int y,
            int w, int h
    ) {
        yzwc$fillRoundedRect(g, x, y, w, h, 4, YZWC_TAB_BG);
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
