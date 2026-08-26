package top.csituka.youzaiworldcore.mixin.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ProgressScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.ARGB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.csituka.youzaiworldcore.client.render.LoadingCircleRenderer;

/**
 * 将资源重载界面（ProgressScreen）改为左下角加载圈与状态文字布局。
 * 背景继续使用原版 {@link ProgressScreen} 继承的屏幕背景；进入世界前会显示
 * 标题界面的旋转全景，而不会切换到启动阶段的淡绿色渐变。
 */
@Mixin(ProgressScreen.class)
public class ProgressScreenMixin {

    @Unique
    private static final int youzaiworldcore$LOADING_MARGIN = 16;
    @Unique
    private static final int youzaiworldcore$TEXT_GAP = 12;

    @Shadow
    private Component header;
    @Shadow
    private Component stage;
    @Shadow
    private int progress;

    @Unique
    private LoadingCircleRenderer youzaiworldcore$loadingCircleRenderer;

    /** 屏蔽原版居中的标题和阶段文字，改由左下角状态组件统一绘制。 */
    @Redirect(method = "extractRenderState", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;centeredText(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;III)V"))
    private void youzaiworldcore$suppressCenteredLoadingText(GuiGraphicsExtractor graphics,
            Font font, Component text,
            int x, int y, int color) {
        // 左下角组件会绘制同一份状态信息。
    }

    /** 在资源准备、保存世界等通用进度屏幕左下角绘制加载组件。 */
    @SuppressWarnings("null")
    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void youzaiworldcore$drawLoadingStatus(GuiGraphicsExtractor graphics,
            int mouseX, int mouseY, float partialTick,
            CallbackInfo ci) {
        if (this.youzaiworldcore$loadingCircleRenderer == null) {
            this.youzaiworldcore$loadingCircleRenderer = new LoadingCircleRenderer();
        }

        int centerX = youzaiworldcore$LOADING_MARGIN + LoadingCircleRenderer.VISUAL_RADIUS;
        int centerY = graphics.guiHeight() - youzaiworldcore$LOADING_MARGIN
                - LoadingCircleRenderer.VISUAL_RADIUS;
        this.youzaiworldcore$loadingCircleRenderer.render(graphics, centerX, centerY, 1.0f);

        Font font = Minecraft.getInstance().font;
        int textX = centerX + LoadingCircleRenderer.VISUAL_RADIUS + youzaiworldcore$TEXT_GAP;
        Component stageText = youzaiworldcore$stageText();
        int textColor = ARGB.white(255);

        if (this.header != null && stageText != null) {
            int headerY = centerY - font.lineHeight - 1;
            graphics.text(font, this.header, textX, headerY, textColor, true);
            graphics.text(font, stageText, textX, centerY + 1, textColor, true);
        } else if (this.header != null) {
            graphics.text(font, this.header, textX, centerY - font.lineHeight / 2,
                    textColor, true);
        } else if (stageText != null) {
            graphics.text(font, stageText, textX, centerY - font.lineHeight / 2,
                    textColor, true);
        }
    }

    /** 构造阶段名称和当前百分比组成的状态文本。 */
    @Unique
    private Component youzaiworldcore$stageText() {
        if (this.stage == null) {
            return null;
        }

        @SuppressWarnings("null")
        MutableComponent result = Component.empty().append(this.stage);
        if (this.progress != 0) {
            result.append(" " + this.progress + "%");
        }
        return result;
    }
}
