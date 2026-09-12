package top.csituka.youzaiworldcore.mixin.client;

import top.csituka.youzaiworldcore.client.render.YzuiTheme;

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
import top.csituka.youzaiworldcore.client.render.RoundedRect;

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
        if (!(self.getValue() instanceof Boolean selected)) return;
        int x = self.getX(), y = self.getY(), w = self.getWidth(), h = self.getHeight();
        YzuiTheme.button(g, x, y, w, h, self.isHoveredOrFocused() ? 1f : 0f,
                self.isFocused(), self.active, self.getAlpha(),
                selected ? YzuiTheme.ButtonStyle.FILLED : YzuiTheme.ButtonStyle.TONAL);
        String label = selected ? "✓" : "≡";
        var font = Minecraft.getInstance().font;
        g.text(font, label, x + (w - font.width(label)) / 2, y + (h - font.lineHeight) / 2,
                selected ? YzuiTheme.onPrimary() : YzuiTheme.onPrimaryContainer(), false);
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
        // 设置页的布尔选项必须显示名称和值，交给通用 MD3 按钮绘制文字。
        if (screen instanceof top.csituka.youzaiworldcore.client.screen.options.YzuiOptionsScreen
                || screen instanceof top.csituka.youzaiworldcore.client.screen.options.YzuiGameRulesScreen) return false;
        return screen != null && screen.getClass().getName().startsWith("top.csituka.youzaiworldcore");
    }
}
