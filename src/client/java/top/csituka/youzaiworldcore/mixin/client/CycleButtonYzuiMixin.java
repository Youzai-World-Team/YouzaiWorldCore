package top.csituka.youzaiworldcore.mixin.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.jspecify.annotations.NonNull;
import top.csituka.youzaiworldcore.client.config.ClientExternalSettings;

/**
 * Mixin 为 {@link CycleButton}（配方书过滤按钮）添加 YZUI 样式替换：
 * <ul>
 *   <li>选中态：显示绿色勾选图（recipe_filter_craftable.png）</li>
 *   <li>未选中态：显示红色 X 图（recipe_filter_all.png）</li>
 * </ul>
 * 仅当 YZUI 全局开关开启且屏幕为 YZUI 自定义屏幕时生效。
 */
@Mixin(CycleButton.class)
public class CycleButtonYzuiMixin {

    @Unique
    private static final @NonNull Identifier YZWC_FILTER_CRAFTABLE =
            Identifier.fromNamespaceAndPath("youzaiworldcore", "textures/gui/recipe_filter_craftable.png");
    @Unique
    private static final @NonNull Identifier YZWC_FILTER_ALL =
            Identifier.fromNamespaceAndPath("youzaiworldcore", "textures/gui/recipe_filter_all.png");
    @Unique
    private static final int YZWC_TEX_W = 32;
    @Unique
    private static final int YZWC_TEX_H = 16;

    @Inject(method = "extractContents", at = @At("HEAD"), cancellable = true)
    private void yzwc$cycleButton(GuiGraphicsExtractor g, int mx, int my, float pt, CallbackInfo ci) {
        if (!yzwc$shouldApplyYzui()) return;

        CycleButton<?> self = (CycleButton<?>) (Object) this;
        int x = self.getX(), y = self.getY(), w = self.getWidth(), h = self.getHeight();
        boolean hovered = self.isHovered();
        Object val = self.getValue();
        boolean selected = val instanceof Boolean && (Boolean) val;

        @NonNull Identifier tex = selected ? YZWC_FILTER_CRAFTABLE : YZWC_FILTER_ALL;

        // 计算居中缩放：贴图比按钮略小一些（避免贴图贴到按钮边缘）
        int drawW = YZWC_TEX_W;
        int drawH = YZWC_TEX_H;
        int offsetX = (w - drawW) / 2;
        int offsetY = (h - drawH) / 2;
        int drawX = x + offsetX;
        int drawY = y + offsetY;

        // 悬浮时稍微提高亮度（在纹理下方铺一层浅色填充）
        if (hovered) {
            yzwc$fillRoundedRect(g, x, y, w, h, 4, 0x60FFFFFF);
        }

        // 绘制 32×16 贴图（按钮在贴图上比贴图大约大几像素用于"按钮>贴图"边距）
        g.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, tex,
                drawX, drawY, 0f, 0f, drawW, drawH, YZWC_TEX_W, YZWC_TEX_H);

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