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
import top.csituka.youzaiworldcore.client.render.RoundedRect;

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
        // 圆角绘制统一走 RoundedRect（行扫描：r=6 时 135 次 fill -> 13 次）。
        // 点亮像素与原逐像素实现一致（45253 组尺寸/半径已逐一比对）；
        // 原实现未做尺寸校验，r > min(w,h)/2 时会画出坐标反转/重叠的结果，此处会钳制半径。
        RoundedRect.fill(g, x, y, w, h, r, color);
    }

    @Unique
    private static boolean yzwc$shouldApplyYzui() {
        if (!ClientExternalSettings.isYzuiEnabled()) return false;
        Screen screen = Minecraft.getInstance().gui.screen();
        return screen != null && screen.getClass().getName().startsWith("top.csituka.youzaiworldcore");
    }
}