package top.csituka.youzaiworldcore.mixin.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import net.minecraft.world.scores.Objective;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import top.csituka.youzaiworldcore.client.config.ClientExternalSettings;
import top.csituka.youzaiworldcore.client.hud.ScoreboardSidebarRenderer;
import top.csituka.youzaiworldcore.util.DebugLogger;

/**
 * 将原版记分板侧边栏替换为 YZUI 风格圆角面板。
 *
 * <p>YZUI 关闭时完全保留原版记分板，避免影响资源包对原版 HUD 的替换。</p>
 */
@SuppressWarnings("null")
@Mixin(Hud.class)
public abstract class ScoreboardSidebarMixin {

    private static final String LOG_TAG = "ScoreboardSidebarMixin";

    @Inject(method = "displayScoreboardSidebar(Lnet/minecraft/client/gui/GuiGraphicsExtractor;"
            + "Lnet/minecraft/world/scores/Objective;)V",
            at = @At("HEAD"), cancellable = true)
    private void yzwc$renderScoreboardSidebar(
            GuiGraphicsExtractor graphics, Objective objective, CallbackInfo ci) {
        if (!yzwc$shouldApplyYzui()) {
            return;
        }

        try {
            ScoreboardSidebarRenderer.render(graphics, objective);
            ci.cancel();
        } catch (Exception exception) {
            DebugLogger.exception(LOG_TAG, "renderScoreboardSidebar", exception);
        }
    }

    @Unique
    private static boolean yzwc$shouldApplyYzui() {
        if (ClientExternalSettings.isYzuiEnabled()) {
            return true;
        }
        var screen = Minecraft.getInstance().gui.screen();
        return screen != null
                && screen.getClass().getName().startsWith("top.csituka.youzaiworldcore");
    }
}
