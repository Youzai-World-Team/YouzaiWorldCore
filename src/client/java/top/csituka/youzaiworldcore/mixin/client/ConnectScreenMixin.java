package top.csituka.youzaiworldcore.mixin.client;

import io.netty.channel.ChannelFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ARGB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import top.csituka.youzaiworldcore.client.accessor.ConnectScreenCancelAccess;
import top.csituka.youzaiworldcore.client.render.LoadingCircleRenderer;
import top.csituka.youzaiworldcore.util.DebugLogger;

/** 将连接服务器时的等待提示也统一为左下角加载圈与状态文字。 */
@Mixin(ConnectScreen.class)
public class ConnectScreenMixin implements ConnectScreenCancelAccess {

    @Unique private static final int youzaiworldcore$LOADING_MARGIN = 16;
    @Unique private static final int youzaiworldcore$TEXT_GAP = 12;

    @Shadow private Component status;
    @Shadow private volatile Connection connection;
    @Shadow private ChannelFuture channelFuture;
    @Shadow private boolean aborted;

    @Unique private LoadingCircleRenderer youzaiworldcore$loadingCircleRenderer;

    /** 移除原版居中的取消按钮，连接状态改由 ESC 操作。 */
    @Inject(method = "init", at = @At("HEAD"), cancellable = true)
    private void youzaiworldcore$removeCancelButton(CallbackInfo ci) {
        ci.cancel();
    }

    /** 标记连接界面允许使用 ESC 取消。 */
    @Inject(method = "shouldCloseOnEsc", at = @At("HEAD"), cancellable = true)
    private void youzaiworldcore$allowEscapeCancel(CallbackInfoReturnable<Boolean> cir) {
        cir.setReturnValue(true);
    }

    /** 屏蔽连接界面原版的居中状态文字。 */
    @Redirect(
            method = "extractRenderState",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;centeredText(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;III)V"
            )
    )
    private void youzaiworldcore$suppressCenteredStatus(GuiGraphicsExtractor graphics,
                                                         Font font, Component text,
                                                         int x, int y, int color) {
        // 左下角组件会绘制连接状态。
    }

    /** 在连接等待界面左下角绘制加载圈与实时状态。 */
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

        if (this.status != null) {
            int textX = centerX + LoadingCircleRenderer.VISUAL_RADIUS + youzaiworldcore$TEXT_GAP;
            int textY = centerY - Minecraft.getInstance().font.lineHeight / 2;
            graphics.text(Minecraft.getInstance().font, this.status, textX, textY,
                    ARGB.white(255), true);
        }
    }

    /** 执行原版取消按钮的完整连接中止流程，由 Screen 的 ESC 处理器调用。 */
    @Override
    public void youzaiworldcore$cancelConnection() {
        synchronized (this) {
            this.aborted = true;
            if (this.channelFuture != null) {
                this.channelFuture.cancel(true);
                this.channelFuture = null;
            }
            if (this.connection != null) {
                this.connection.disconnect(ConnectScreen.ABORT_CONNECTION);
            }
        }

        DebugLogger.info("ConnectScreen", "已通过 ESC 取消服务器连接");
    }
}
