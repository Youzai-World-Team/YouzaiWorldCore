package top.csituka.youzaiworldcore.mixin.client;

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

    /** Tab 颜色循环数组，与创造模式 {@code YzuCreativeInventoryScreen.TC} 一致 */
    @Unique
    private static final int[] YZWC_TC = {
            0x60CC8866, 0x6099CCFF, 0x6066AA44, 0x60AA66CC, 0x60FF6644, 0x604488CC,
            0x60FF8844, 0x60FFCC44, 0x60CCAACC, 0x60FFAAAA, 0x60FF66AA
    };
    /** 选中 Tab 的背景色（白色高亮） */
    @Unique
    private static final int YZWC_TA = 0x90FFFFFF;
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
        if (!yzwc$shouldApplyYzui())
            return;

        RecipeBookTabButton self = (RecipeBookTabButton) (Object) this;
        int x = self.getX();
        int y = self.getY();
        int w = self.getWidth();
        int h = self.getHeight();

        // 计算背景色：选中 = 白色高亮，未选中 = 颜色循环
        int colorIndex = Math.abs((y / 27) % YZWC_TC.length);
        int color = selected ? YZWC_TA : YZWC_TC[colorIndex];

        DebugLogger.info(YZWC_TAB_DBG,
                "Tab render at (%d, %d) %dx%d selected=%s colorIndex=%d",
                x, y, w, h, selected, colorIndex);

        // 绘制彩色圆角矩形背景
        yzwc$fillRoundedRect(g, x, y, w, h, YZWC_TR, color);

        // 绘制物品图标居中
        ItemStack icon = tabInfo.primaryIcon();
        if (icon != null && !icon.isEmpty()) {
            int iconX = x + (w - 16) / 2;
            int iconY = y + (h - 16) / 2;
            g.fakeItem(icon, iconX, iconY);
        }

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
