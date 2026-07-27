package top.csituka.youzaiworldcore.mixin.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.ImageButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.Identifier;
import net.minecraft.world.inventory.RecipeBookType;
import org.jspecify.annotations.NonNull;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.csituka.youzaiworldcore.client.config.ClientExternalSettings;

/**
 * Mixin 为 YZUI 物品栏屏幕中的 {@link ImageButton} 添加 YZUI 样式。
 * <p>YZUI 物品栏屏幕中唯一的 ImageButton 就是配方书开关按钮，
 * 使用自定义 20×20 贴图（绿勾/红 X）替换原版纹理。</p>
 */
@Mixin(ImageButton.class)
public class ImageButtonYzuiMixin {

    @Unique
    private static final @NonNull Identifier YZWC_RECIPE_BOOK_SHOW =
            Identifier.fromNamespaceAndPath("youzaiworldcore", "textures/gui/recipe_book_show.png");
    @Unique
    private static final @NonNull Identifier YZWC_RECIPE_BOOK_HIDE =
            Identifier.fromNamespaceAndPath("youzaiworldcore", "textures/gui/recipe_book_hide.png");

    @Inject(method = "extractContents", at = @At("HEAD"), cancellable = true)
    private void yzwc$imageButton(GuiGraphicsExtractor g, int mx, int my, float pt, CallbackInfo ci) {
        if (!yzwc$shouldApplyYzui()) return;

        ImageButton self = (ImageButton) (Object) this;
        int x = self.getX(), y = self.getY(), w = self.getWidth(), h = self.getHeight();
        boolean hovered = self.isHovered();

        // 配方书关闭时显示红 X（表示配方书当前是"隐藏"状态），打开时显示绿勾（表示"显示"状态）
        LocalPlayer player = Minecraft.getInstance().player;
        boolean bookOpen = player != null
                && player.getRecipeBook().isOpen(RecipeBookType.CRAFTING);
        @NonNull Identifier tex = bookOpen ? YZWC_RECIPE_BOOK_SHOW : YZWC_RECIPE_BOOK_HIDE;

        // 悬浮高亮
        if (hovered) {
            yzwc$fillRoundedRect(g, x, y, w, h, 4, 0x60FFFFFF);
        }

        // 居中绘制 20×20 贴图（按钮 20×18，贴图略高 2px 容许）
        g.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, tex,
                x, y, 0f, 0f, 20, 20, 20, 20);

        ci.cancel();
    }

    @Unique
    private static void yzwc$fillRoundedRect(GuiGraphicsExtractor g, int x, int y, int w, int h, int r, int color) {
        r = Math.min(r, Math.min(w / 2, h / 2));
        g.fill(x + r, y, x + w - r, y + h, color);
        g.fill(x, y + r, x + r, y + h - r, color);
        g.fill(x + w - r, y + r, x + w, y + h - r, color);
        for (int i = 0; i < r; i++)
            for (int j = 0; j < r; j++) {
                if ((r - 1 - i) * (r - 1 - i) + (r - 1 - j) * (r - 1 - j) < r * r) {
                    g.fill(x + i, y + j, x + i + 1, y + j + 1, color);
                    g.fill(x + w - 1 - i, y + j, x + w - i, y + j + 1, color);
                    g.fill(x + i, y + h - 1 - j, x + i + 1, y + h - j, color);
                    g.fill(x + w - 1 - i, y + h - 1 - j, x + w - i, y + h - j, color);
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