package top.csituka.youzaiworldcore.mixin.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.LevelLoadingScreen;
import net.minecraft.client.multiplayer.LevelLoadTracker;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.csituka.youzaiworldcore.client.render.LoadingCircleRenderer;

/**
 * 将 26.2 的世界加载界面改为左下角加载圈与状态文字布局。
 * <p>
 * 原版的区块可视化、居中提示和居中进度条均由此界面的集中式状态组件替代，
 * 背景绘制仍交由原版 {@link LevelLoadingScreen} 保持不变。
 */
@Mixin(LevelLoadingScreen.class)
public class LevelLoadingScreenMixin {

    @Unique
    private static final int youzaiworldcore$LOADING_MARGIN = 16;
    @Unique
    private static final int youzaiworldcore$TEXT_GAP = 12;

    @Shadow
    private LevelLoadTracker loadTracker;

    @Unique
    private LoadingCircleRenderer youzaiworldcore$loadingCircleRenderer;

    /**
     * 在原版开始绘制区块状态前切换为左下角加载组件。
     * 该注入点位于 Screen 背景和旁白状态之后，因此不会破坏背景及无障碍播报。
     */
    @SuppressWarnings("null")
    @Inject(method = "extractRenderState", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/LevelLoadTracker;statusView()Lnet/minecraft/server/level/progress/ChunkLoadStatusView;"), cancellable = true)
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

        int textX = centerX + LoadingCircleRenderer.VISUAL_RADIUS + youzaiworldcore$TEXT_GAP;
        int textY = centerY - Minecraft.getInstance().font.lineHeight / 2;
        graphics.text(
                Minecraft.getInstance().font,
                youzaiworldcore$loadingText(),
                textX,
                textY,
                ARGB.white(255),
                true);

        // 阻止原版继续绘制中央区块地图、提示文字和进度条。
        ci.cancel();
    }

    /** 构造世界加载状态文字，并在可用时追加服务器加载百分比。 */
    @Unique
    private Component youzaiworldcore$loadingText() {
        Component base = Component.translatable("multiplayer.downloadingTerrain");
        if (!this.loadTracker.hasProgress()) {
            return base;
        }

        int percent = Mth.floor(this.loadTracker.serverProgress() * 100.0f);
        percent = Math.max(0, Math.min(100, percent));
        return Component.empty().append(base).append(" " + percent + "%");
    }
}
