package top.csituka.youzaiworldcore.mixin.client;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import top.csituka.youzaiworldcore.client.config.ClientExternalSettings;
import top.csituka.youzaiworldcore.util.DebugLogger;

/**
 * Mixin 替换 {@link RecipeBookComponent#extractRenderState} 中的背景纹理 blit。
 * <p>
 * 使用 {@link Redirect} 而非 {@code @Inject(cancellable=true)}：
 * {@code ci.cancel()} 在 INVOKE 点会取消整个方法剩余部分（Tab/搜索框/配方网格全部不渲染），
 * 导致配方书只有背景没有控件。{@code @Redirect} 只替换 blit 调用本身，后续渲染正常执行。
 * </p>
 * <ul>
 *   <li>YZUI 开启：替换为 YZUI 半透明白色圆角扩展面板（覆盖 Tab 列）</li>
 *   <li>YZUI 关闭：调用原版 blit，保留原版背景</li>
 * </ul>
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

    @Redirect(
            method = "extractRenderState",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;blit(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIFFIIII)V"),
            require = 1
    )
    private void yzwc$recipeBookBackground(
            GuiGraphicsExtractor g,
            RenderPipeline pipeline,
            Identifier texture,
            int x, int y,
            float u, float v,
            int w, int h,
            int texW, int texH
    ) {
        if (!yzwc$shouldApplyYzui()) {
            // YZUI 关闭：原版 blit 正常执行
            g.blit(pipeline, texture, x, y, u, v, w, h, texW, texH);
            return;
        }

        // YZUI 开启：替换为扩展圆角面板（覆盖 Tab 列）。@Redirect 只替换 blit，
        // 方法后续的搜索框/Tab/配方网格渲染正常继续。
        int combX = x - YZWC_TAB_STRIP_W;
        int combW = 147 + YZWC_TAB_STRIP_W;
        yzwc$fillRoundedRect(g, combX, y, combW, 166, YZWC_RECIPE_BG_RADIUS, YZWC_RECIPE_BOOK_BG);
        DebugLogger.info(YZWC_BG_DBG,
                "YZUI recipe book bg at (%d, %d) %dx%d (controls still render)", combX, y, combW, 166);
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
