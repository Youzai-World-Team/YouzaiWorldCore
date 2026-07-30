package top.csituka.youzaiworldcore.mixin.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.csituka.youzaiworldcore.client.config.ClientExternalSettings;
import top.csituka.youzaiworldcore.util.DebugLogger;

/**
 * Mixin 替换 {@link RecipeBookComponent#extractRenderState} 中的背景纹理 blit，
 * 改为绘制 YZUI 半透明白色圆角面板。
 * <p>
 * 当 YZUI 关闭时不拦截 blit，让原版纹理正常绘制。
 * 当 YZUI 开启时，面板从 Tab 列左侧延伸至配方内容右侧，将 Tab 按钮包裹在内。
 * </p>
 */
@Mixin(RecipeBookComponent.class)
public class RecipeBookBackgroundMixin {

    @Unique
    private static final int YZWC_RECIPE_BOOK_BG = 0x80FFFFFF;
    @Unique
    private static final int YZWC_TAB_STRIP_W = 39;
    @Unique
    private static final int YZWC_RECIPE_BG_RADIUS = 6;
    @Unique
    private static final String YZWC_BG_DBG = "RecipeBookBg";

    @Shadow
    private int getXOrigin() {
        return 0;
    }

    @Shadow
    private int getYOrigin() {
        return 0;
    }

    @Inject(method = "extractRenderState", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;blit(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIFFIIII)V"), cancellable = true)
    private void yzwc$recipeBookBackground(
            GuiGraphicsExtractor g,
            int mx, int my, float pt,
            CallbackInfo ci) {
        if (!yzwc$shouldApplyYzui())
            return; // 不取消 → 原版 blit 正常执行

        ci.cancel(); // 取消原版 blit，改绘 YZUI 面板

        // @Shadow 方法直接通过 this 访问
        int x = getXOrigin();
        int y = getYOrigin();

        // 向左固定扩展，覆盖 Tab 列
        int combX = x - YZWC_TAB_STRIP_W;
        int combW = 147 + YZWC_TAB_STRIP_W;

        yzwc$fillRoundedRect(g, combX, y, combW, 166, YZWC_RECIPE_BG_RADIUS, YZWC_RECIPE_BOOK_BG);
        DebugLogger.info(YZWC_BG_DBG,
                "Extended panel at (%d, %d) %dx%d", combX, y, combW, 166);
    }

    @Unique
    private static boolean yzwc$shouldApplyYzui() {
        if (!ClientExternalSettings.isYzuiEnabled())
            return false;
        Screen screen = Minecraft.getInstance().gui.screen();
        return screen != null && screen.getClass().getName().startsWith("top.csituka.youzaiworldcore");
    }

    @Unique
    private static void yzwc$fillRoundedRect(GuiGraphicsExtractor g, int x, int y, int w, int h, int r, int color) {
        r = Math.min(r, Math.min(w / 2, h / 2));
        g.fill(x + r, y, x + w - r, y + h, color);
        g.fill(x, y + r, x + r, y + h - r, color);
        g.fill(x + w - r, y + r, x + w, y + h - r, color);
        for (int i = 0; i < r; i++) {
            for (int j = 0; j < r; j++) {
                if ((r - 1 - i) * (r - 1 - i) + (r - 1 - j) * (r - 1 - j) < r * r) {
                    g.fill(x + i, y + j, x + i + 1, y + j + 1, color);
                    g.fill(x + w - 1 - i, y + j, x + w - i, y + j + 1, color);
                    g.fill(x + i, y + h - 1 - j, x + i + 1, y + h - j, color);
                    g.fill(x + w - 1 - i, y + h - 1 - j, x + w - i, y + h - j, color);
                }
            }
        }
    }
}
