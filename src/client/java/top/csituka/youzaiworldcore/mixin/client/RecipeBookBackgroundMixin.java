package top.csituka.youzaiworldcore.mixin.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import top.csituka.youzaiworldcore.client.config.ClientExternalSettings;
import top.csituka.youzaiworldcore.util.DebugLogger;

/**
 * Mixin 替换 {@link RecipeBookComponent#extractRenderState} 中的背景纹理 blit，
 * 改为绘制 YZUI 半透明白色圆角面板。
 * <p>
 * 当 YZUI 开启时，面板从 Tab 列左侧延伸至配方内容右侧，将 Tab 按钮包裹在内。
 * </p>
 * <p>
 * 原版 blit 调用：{@code g.blit(GUI_TEXTURED, RECIPE_BOOK_LOCATION, x, y, 1f, 1f, 147, 166, 256, 256)}
 * </p>
 */
@Mixin(RecipeBookComponent.class)
public class RecipeBookBackgroundMixin {

    @Unique
    private static final int YZWC_RECIPE_BOOK_BG = 0x80FFFFFF;
    /** Tab 列宽度（按钮 35 + 左右内边距 4），面板向左扩展的固定量 */
    @Unique
    private static final int YZWC_TAB_STRIP_W = 39;
    /** 面板圆角半径 */
    @Unique
    private static final int YZWC_RECIPE_BG_RADIUS = 6;
    /** Debug 模块名 */
    @Unique
    private static final String YZWC_BG_DBG = "RecipeBookBg";

    /**
     * 拦截 RecipeBookComponent.extractRenderState 中的 blit 调用，
     * 绘制涵盖 Tab 列的合并圆角面板。
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
        if (yzwc$shouldApplyYzui()) {
            // 向左固定扩展 YZWC_TAB_STRIP_W px，覆盖 Tab 列区域
            // 不采用按 xOffset 动态计算的方式（宽屏时 tab 可距面板 200px+，会覆盖屏幕左侧）
            int combX = x - YZWC_TAB_STRIP_W;
            int combW = w + YZWC_TAB_STRIP_W;

            yzwc$fillRoundedRect(g, combX, y, combW, h, YZWC_RECIPE_BG_RADIUS, YZWC_RECIPE_BOOK_BG);
            DebugLogger.info(YZWC_BG_DBG,
                    "Extended panel at (%d, %d) %dx%d (extended %dpx left)",
                    combX, y, combW, h, YZWC_TAB_STRIP_W);
        } else {
            // 非 YZUI 模式仅绘制原尺寸主面板
            yzwc$fillRoundedRect(g, x, y, w, h, YZWC_RECIPE_BG_RADIUS, YZWC_RECIPE_BOOK_BG);
        }
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
