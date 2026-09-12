package top.csituka.youzaiworldcore.mixin.client;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractScrollArea;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import top.csituka.youzaiworldcore.client.render.RoundedRect;
import top.csituka.youzaiworldcore.client.render.YzuiTheme;

/** 以细圆角轨道替换滚动条纹理，保留原版滚动、拖拽和鼠标提示区域。 */
@Mixin(AbstractScrollArea.class)
public class ScrollAreaYzuiMixin {
    @Redirect(method = "extractScrollbar", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;blitSprite(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIII)V"))
    private void youzaiworldcore$scrollbar(GuiGraphicsExtractor g, RenderPipeline pipeline, Identifier sprite,
            int x, int y, int width, int height) {
        if (!YzuiTheme.enabled()) {
            g.blitSprite(pipeline, sprite, x, y, width, height);
            return;
        }
        boolean background = sprite.getPath().contains("background");
        int color = background ? YzuiTheme.outlineVariant() : YzuiTheme.primary();
        int thickness = Math.min(width, background ? 2 : 3);
        RoundedRect.fill(g, x + (width - thickness) / 2, y, thickness, height, 1, color);
    }
}
