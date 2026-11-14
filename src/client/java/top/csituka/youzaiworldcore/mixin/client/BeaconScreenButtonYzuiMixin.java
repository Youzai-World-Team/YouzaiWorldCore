package top.csituka.youzaiworldcore.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import top.csituka.youzaiworldcore.client.animation.YzuiHover;
import top.csituka.youzaiworldcore.client.render.YzuiTheme;
import top.csituka.youzaiworldcore.client.screen.YzuBeaconScreen;

/** 信标按钮保留原版图标、提示和操作，仅将底板替换为主题的选中、悬停和禁用样式。 */
@Mixin(targets = "net.minecraft.client.gui.screens.inventory.BeaconScreen$BeaconScreenButton")
public abstract class BeaconScreenButtonYzuiMixin {
    @Unique
    private final YzuiHover youzaiworldcore$hover = new YzuiHover();

    @WrapOperation(method = "extractContents", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;blitSprite(Lcom/mojang/renderpearl/api/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIII)V"))
    private void youzaiworldcore$themeBeaconButton(GuiGraphicsExtractor g, RenderPipeline pipeline, Identifier sprite,
            int x, int y, int w, int h, Operation<Void> original) {
        if (!(Minecraft.getInstance().gui.screen() instanceof YzuBeaconScreen)) {
            original.call(g, pipeline, sprite, x, y, w, h);
            return;
        }
        AbstractButton button = (AbstractButton) (Object) this;
        boolean selected = sprite.getPath().equals("container/beacon/button_selected");
        YzuiTheme.button(g, x, y, w, h,
                youzaiworldcore$hover.sample(button.active && button.isHoveredOrFocused()),
                button.isFocused(), button.active, button.getAlpha(),
                selected ? YzuiTheme.ButtonStyle.FILLED : YzuiTheme.ButtonStyle.TONAL);
    }
}
