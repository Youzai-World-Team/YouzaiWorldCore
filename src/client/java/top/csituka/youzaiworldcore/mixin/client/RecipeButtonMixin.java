package top.csituka.youzaiworldcore.mixin.client;

import top.csituka.youzaiworldcore.client.render.YzuiTheme;

import com.mojang.renderpearl.api.pipeline.RenderPipeline;
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

    @Redirect(
            method = "extractWidgetRenderState",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;blitSprite(Lcom/mojang/renderpearl/api/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIII)V"),
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

        if (YzuiTheme.light()) {
            RecipeButton button = (RecipeButton) (Object) this;
            YzuiTheme.drawSlot(g, x, y, w, h, 3, button.isHoveredOrFocused(),
                    button.getCollection().hasCraftable() ? YzuiTheme.SlotStyle.ACCENT : YzuiTheme.SlotStyle.NORMAL);
        } else {
            RoundedRect.fill(g, x, y, w, h, 3, YzuiTheme.slot());
        }
    }

    @Unique
    private static boolean yzwc$shouldApplyYzui() {
        if (!ClientExternalSettings.isYzuiEnabled()) return false;
        Screen screen = Minecraft.getInstance().gui.screen();
        return screen != null && screen.getClass().getName().startsWith("top.csituka.youzaiworldcore");
    }
}
