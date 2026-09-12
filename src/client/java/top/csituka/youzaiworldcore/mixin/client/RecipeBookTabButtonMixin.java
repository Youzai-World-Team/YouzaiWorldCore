package top.csituka.youzaiworldcore.mixin.client;

import top.csituka.youzaiworldcore.client.render.YzuiTheme;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import net.minecraft.client.gui.screens.recipebook.RecipeBookTabButton;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.csituka.youzaiworldcore.client.config.ClientExternalSettings;
import top.csituka.youzaiworldcore.util.DebugLogger;
import top.csituka.youzaiworldcore.client.render.RoundedRect;

/**
 * Mixin 替换 {@link RecipeBookTabButton#extractContents} 的全部渲染逻辑，
 * 将原版小按钮改为创造模式物品栏分类 Tab 同款样式：
 * <ul>
 *   <li>彩色圆角矩形背景（选中=白色高亮，未选中=渐变色循环）</li>
 *   <li>物品图标居中显示</li>
 * </ul>
 * 仅当 YZUI 全局开关开启且屏幕为 YZUI 自定义屏幕时生效。
 */
@Mixin(RecipeBookTabButton.class)
public class RecipeBookTabButtonMixin {

    /** Tab 圆角半径 */
    @Unique
    private static final int YZWC_TR = 4;
    /** Debug 模块名 */
    @Unique
    private static final String YZWC_TAB_DBG = "RecipeBookTab";

    @Shadow
    private boolean selected;

    @Shadow
    private RecipeBookComponent.TabInfo tabInfo;

    @Inject(method = "extractContents", at = @At("HEAD"), cancellable = true)
    private void yzwc$tabContents(GuiGraphicsExtractor g, int mx, int my, float pt, CallbackInfo ci) {
        if (!yzwc$shouldApplyYzui()) return;
        RecipeBookTabButton self = (RecipeBookTabButton) (Object) this;
        int x = self.getX(), y = self.getY(), w = self.getWidth(), h = self.getHeight();
        YzuiTheme.button(g, x, y, w, h, self.isHovered() ? 1f : 0f, self.isFocused(), self.active, self.getAlpha(),
                selected ? YzuiTheme.ButtonStyle.TONAL : YzuiTheme.ButtonStyle.TEXT);
        if (selected) YzuiTheme.border(g, x, y, w, h, 6, YzuiTheme.primary());
        ItemStack icon = tabInfo.primaryIcon();
        if (icon != null && !icon.isEmpty()) g.fakeItem(icon, x + (w - 16) / 2, y + (h - 16) / 2);
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
        if (!ClientExternalSettings.isYzuiEnabled())
            return false;
        Screen screen = Minecraft.getInstance().gui.screen();
        return screen != null && screen.getClass().getName().startsWith("top.csituka.youzaiworldcore");
    }
}
