package top.csituka.youzaiworldcore.mixin.client;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.recipebook.RecipeButton;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import top.csituka.youzaiworldcore.client.config.ClientExternalSettings;
import top.csituka.youzaiworldcore.client.render.RoundedRect;

/**
 * Mixin 替换 {@link RecipeButton#extractWidgetRenderState} 中的槽位背景 blitSprite，
 * 改为绘制 YZUI 半透明圆角背景。
 * <p>
 * 使用 {@link Redirect} 而非 {@code @Inject(cancellable=true)}：
 * {@code ci.cancel()} 在 INVOKE 点会取消方法剩余部分，导致物品图标（fakeItem）
 * 不渲染。{@code @Redirect} 只替换 blitSprite 调用本身，物品图标正常显示。
 * </p>
 * <ul>
 *   <li>YZUI 开启：替换为半透明圆角槽位背景</li>
 *   <li>YZUI 关闭：调用原版 blitSprite，保留原版槽位纹理</li>
 * </ul>
 */
@SuppressWarnings("null")
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
        if (!yzwc$shouldApplyYzui()) {
            // YZUI 关闭：原版 blitSprite 正常执行
            g.blitSprite(pipeline, sprite, x, y, w, h);
            return;
        }

        // YZUI 开启：绘制圆角背景。@Redirect 只替换 blitSprite，
        // 方法后续的物品图标（fakeItem）渲染正常继续。
        yzwc$fillRoundedRect(g, x, y, w, h, 3, YZWC_SLOT_BG);
    }

    @Unique
    private static void yzwc$fillRoundedRect(GuiGraphicsExtractor g, int iX, int iY, int iW, int iH, int r, int color) {
        // 圆角绘制统一走 RoundedRect（行扫描：r=6 时 135 次 fill -> 13 次）。
        // 点亮像素与原逐像素实现一致（45253 组尺寸/半径已逐一比对）；
        // 原实现未做尺寸校验，r > min(w,h)/2 时会画出坐标反转/重叠的结果，此处会钳制半径。
        RoundedRect.fill(g, iX, iY, iW, iH, r, color);
    }

    @Unique
    private static boolean yzwc$shouldApplyYzui() {
        if (!ClientExternalSettings.isYzuiEnabled()) return false;
        Screen screen = Minecraft.getInstance().gui.screen();
        return screen != null && screen.getClass().getName().startsWith("top.csituka.youzaiworldcore");
    }
}
