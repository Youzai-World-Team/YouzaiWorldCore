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
