package top.csituka.youzaiworldcore.mixin.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.SubtitleOverlay;
import net.minecraft.client.sounds.SoundManager;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.csituka.youzaiworldcore.client.pickup.SubtitleCaptureHandler;

/**
 * 字幕叠加层的 Mixin，用于将声音字幕渲染合并到拾取提示系统。
 * <p>
 * 取消原版 {@link SubtitleOverlay#extractRenderState} 的渲染，
 * 由 {@link SubtitleCaptureHandler} 独立捕获声音事件并创建显示条目，
 * 与拾取通知共享同一渲染区域。
 * </p>
 */
@SuppressWarnings("null")
@Mixin(SubtitleOverlay.class)
public abstract class SubtitleOverlayMixin {

    @Shadow
    @Final
    private Minecraft minecraft;

    /**
     * 取消原版字幕叠加层的渲染输出。
     * <p>
     * 原版 {@code extractRenderState} 负责管理监听器注册和渲染字幕文本。
     * 此处取消其渲染，改由 {@link SubtitleCaptureHandler} 在监听声音事件时
     * 直接创建显示条目，在 {@link DrawEntriesHandler} 中统一渲染。
     * 监听器注册逻辑改为在客户端初始化时由 Client.java 完成。
     * </p>
     */
    @Inject(method = "extractRenderState", at = @At("HEAD"), cancellable = true)
    private void onExtractRenderState(GuiGraphicsExtractor graphics, CallbackInfo ci) {
        // 每次渲染帧将字幕捕获处理器注册到 SoundManager（若尚未注册）
        SoundManager soundManager = minecraft.getSoundManager();
        if (!SubtitleCaptureHandler.INSTANCE.isRegistered()
                && minecraft.options.showSubtitles().get()) {
            SubtitleCaptureHandler.INSTANCE.register(soundManager);
        } else if (SubtitleCaptureHandler.INSTANCE.isRegistered()
                && !minecraft.options.showSubtitles().get()) {
            SubtitleCaptureHandler.INSTANCE.unregister(soundManager);
        }

        // 跳过原版字幕渲染，让其与拾取提示共享同一区域
        ci.cancel();
    }
}
