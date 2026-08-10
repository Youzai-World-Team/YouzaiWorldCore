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
import top.csituka.youzaiworldcore.client.render.RoundedRect;

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
@SuppressWarnings("null")
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
        // 圆角绘制统一走 RoundedRect（行扫描：r=6 时 135 次 fill -> 13 次）。
        // 点亮像素与原逐像素实现一致（45253 组尺寸/半径已逐一比对）；
        // 原实现未做尺寸校验，r > min(w,h)/2 时会画出坐标反转/重叠的结果，此处会钳制半径。
        RoundedRect.fill(g, x, y, w, h, r, color);
    }
}
