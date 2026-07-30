package top.csituka.youzaiworldcore.mixin.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.csituka.youzaiworldcore.client.config.ClientExternalSettings;

/**
 * 调整配方书内部布局：搜索框左移、过滤按钮加宽。
 */
@Mixin(RecipeBookComponent.class)
public class RecipeBookLayoutMixin {

    @Shadow private EditBox searchBox;
    @Shadow protected CycleButton<Boolean> filterButton;

    @Inject(method = "initVisuals", at = @At("TAIL"))
    private void yzwc$adjustLayout(CallbackInfo ci) {
        if (!yzwc$shouldApplyYzui()) return;
        if (this.searchBox != null) {
            // 搜索框左移 14px
            this.searchBox.setX(this.searchBox.getX() - 14);
        }
        if (this.filterButton != null) {
            // 按钮同步左移 14px，加宽 10px（贴图 32×16 不变，居中显示）
            this.filterButton.setX(this.filterButton.getX() - 14);
            this.filterButton.setWidth(this.filterButton.getWidth() + 10);
        }
    }

    @Unique
    private static boolean yzwc$shouldApplyYzui() {
        if (!ClientExternalSettings.isYzuiEnabled()) return false;
        Screen screen = Minecraft.getInstance().gui.screen();
        return screen != null && screen.getClass().getName().startsWith("top.csituka.youzaiworldcore");
    }
}
