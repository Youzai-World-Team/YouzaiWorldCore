package top.csituka.youzaiworldcore.mixin.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.GenericMessageScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentContents;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.util.ARGB;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import top.csituka.youzaiworldcore.client.accessor.ConnectScreenCancelAccess;
import top.csituka.youzaiworldcore.client.render.LoadingCircleRenderer;

/**
 * 将没有进度条的通用加载提示（准备资源、保存世界等）统一改为左下角状态组件。
 * <p>
 * {@link GenericMessageScreen} 继承 {@link Screen#extractRenderState}，因此在基类
 * 的头部拦截可以同时移除原版居中文字，并保留该屏幕独立绘制的背景。
 */
@Mixin(Screen.class)
public class ScreenMixinForLoadingStatus {

    @Unique private static final int youzaiworldcore$LOADING_MARGIN = 16;
    @Unique private static final int youzaiworldcore$TEXT_GAP = 12;

    @Shadow @Final protected Component title;

    @Unique private LoadingCircleRenderer youzaiworldcore$loadingCircleRenderer;

    /** 将连接界面的 ESC 输入转发到原版取消连接逻辑，并消费该按键。 */
    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void youzaiworldcore$cancelConnectionOnEscape(KeyEvent keyEvent,
                                                          CallbackInfoReturnable<Boolean> cir) {
        if (!keyEvent.isEscape() || !((Object) this instanceof ConnectScreenCancelAccess access)) {
            return;
        }

        access.youzaiworldcore$cancelConnection();
        cir.setReturnValue(true);
    }

    /** 在保存世界或准备资源的通用提示屏上绘制左下角加载圈与文字。 */
    @Inject(method = "extractRenderState", at = @At("HEAD"), cancellable = true)
    private void youzaiworldcore$drawLoadingStatus(GuiGraphicsExtractor graphics,
                                                    int mouseX, int mouseY, float partialTick,
                                                    CallbackInfo ci) {
        if (!((Object) this instanceof GenericMessageScreen)
                || !youzaiworldcore$isLoadingMessage()) {
            return;
        }

        if (this.youzaiworldcore$loadingCircleRenderer == null) {
            this.youzaiworldcore$loadingCircleRenderer = new LoadingCircleRenderer();
        }

        int centerX = youzaiworldcore$LOADING_MARGIN + LoadingCircleRenderer.VISUAL_RADIUS;
        int centerY = graphics.guiHeight() - youzaiworldcore$LOADING_MARGIN
                - LoadingCircleRenderer.VISUAL_RADIUS;
        this.youzaiworldcore$loadingCircleRenderer.render(graphics, centerX, centerY, 1.0f);

        int textX = centerX + LoadingCircleRenderer.VISUAL_RADIUS + youzaiworldcore$TEXT_GAP;
        int textY = centerY - Minecraft.getInstance().font.lineHeight / 2;
        graphics.text(Minecraft.getInstance().font, this.title, textX, textY,
                ARGB.white(255), true);

        // GenericMessageScreen 的标题由 Screen.extractRenderState 绘制；取消后避免重复居中显示。
        ci.cancel();
    }

    /** 只拦截原版用于加载流程的两个无进度提示，避免影响普通消息屏幕。 */
    @Unique
    private boolean youzaiworldcore$isLoadingMessage() {
        ComponentContents contents = this.title.getContents();
        if (!(contents instanceof TranslatableContents translated)) {
            return false;
        }

        String key = translated.getKey();
        return "gui.loadingMinecraft".equals(key) || "menu.savingLevel".equals(key)
                || "selectWorld.resource_load".equals(key);
    }
}
