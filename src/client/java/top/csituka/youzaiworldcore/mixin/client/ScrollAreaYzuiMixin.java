package top.csituka.youzaiworldcore.mixin.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractScrollArea;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 在悠哉模组屏幕中跳过 AbstractScrollArea 的滚动条绘制，
 * 避免小输入框（如邮件正文的 MultiLineEditBox）上出现灰色滚动条背景。
 */
@Mixin(AbstractScrollArea.class)
@SuppressWarnings("null")
public class ScrollAreaYzuiMixin {

    @Inject(method = "extractScrollbar(Lnet/minecraft/client/gui/GuiGraphicsExtractor;II)V",
            at = @At("HEAD"), cancellable = true)
    private void yzwc$skipScrollbar(GuiGraphicsExtractor graphics, int mouseX, int mouseY, CallbackInfo ci) {
        if (yzwc$shouldApply()) {
            ci.cancel();
        }
    }

    @Unique
    private static boolean yzwc$shouldApply() {
        var screen = Minecraft.getInstance().gui.screen();
        if (screen == null) {
            return false;
        }
        return screen.getClass().getName().startsWith("top.csituka.youzaiworldcore");
    }
}
