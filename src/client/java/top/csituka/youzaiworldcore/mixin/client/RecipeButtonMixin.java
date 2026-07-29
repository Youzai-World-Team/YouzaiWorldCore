package top.csituka.youzaiworldcore.mixin.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.recipebook.RecipeButton;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.csituka.youzaiworldcore.client.config.ClientExternalSettings;

/**
 * Mixin 替换 {@link RecipeButton#extractWidgetRenderState} 中背景纹理 blitSprite，
 * 改为绘制 YZUI 半透明圆角背景。仅替换背景 blit，不干涉物品图标渲染。
 * YZUI 关闭时不拦截，保留原版。
 */
@Mixin(RecipeButton.class)
public class RecipeButtonMixin {

    @Unique
    private static final int YZWC_SLOT_BG = 0x50FFFFFF;

    /**
     * 在 blitSprite 调用点注入，YZUI 开启时取消原调用并绘制圆角背景。
     * YZUI 关闭时不取消，原版 blitSprite 正常执行。
     */
    @Inject(
            method = "extractWidgetRenderState",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;blitSprite(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIII)V"),
            cancellable = true
    )
    private void yzwc$recipeSlotBackground(
            GuiGraphicsExtractor g,
            int mx, int my, float pt,
            CallbackInfo ci
    ) {
        if (!yzwc$shouldApplyYzui())
            return;

        ci.cancel();
        AbstractWidget self = (AbstractWidget) (Object) this;
        yzwc$fillRoundedRect(g, self.getX(), self.getY(), self.getWidth(), self.getHeight(), 3, YZWC_SLOT_BG);
    }

    @Unique
    private static void yzwc$fillRoundedRect(GuiGraphicsExtractor g, int iX, int iY, int iW, int iH, int r, int color) {
        r = Math.min(r, Math.min(iW / 2, iH / 2));
        g.fill(iX + r, iY, iX + iW - r, iY + iH, color);
        g.fill(iX, iY + r, iX + r, iY + iH - r, color);
        g.fill(iX + iW - r, iY + r, iX + iW, iY + iH - r, color);
        for (int i = 0; i < r; i++) {
            for (int j = 0; j < r; j++) {
                if ((r - 1 - i) * (r - 1 - i) + (r - 1 - j) * (r - 1 - j) < r * r) {
                    g.fill(iX + i, iY + j, iX + i + 1, iY + j + 1, color);
                    g.fill(iX + iW - 1 - i, iY + j, iX + iW - i, iY + j + 1, color);
                    g.fill(iX + i, iY + iH - 1 - j, iX + i + 1, iY + iH - j, color);
                    g.fill(iX + iW - 1 - i, iY + iH - 1 - j, iX + iW - i, iY + iH - j, color);
                }
            }
        }
    }

    @Unique
    private static boolean yzwc$shouldApplyYzui() {
        if (!ClientExternalSettings.isYzuiEnabled()) return false;
        Screen screen = Minecraft.getInstance().gui.screen();
        return screen != null && screen.getClass().getName().startsWith("top.csituka.youzaiworldcore");
    }
}
