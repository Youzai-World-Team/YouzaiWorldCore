package top.csituka.youzaiworldcore.mixin.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.LogoRenderer;
import net.minecraft.client.gui.components.SplashRenderer;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.csituka.youzaiworldcore.client.animation.GuiAnimationController;
import top.csituka.youzaiworldcore.client.render.YzuiTheme;
import top.csituka.youzaiworldcore.client.screen.title.YzuiTitleMenu;

/** 保留原版全景及生命周期，把标题页内容交给统一主题布局。 */
@Mixin(TitleScreen.class)
public abstract class TitleScreenMixin extends Screen {
    @Shadow private SplashRenderer splash;
    @Shadow private boolean fading;
    @Shadow private long fadeInStart;
    @Unique private YzuiTitleMenu youzaiworldcore$menu;

    protected TitleScreenMixin(Component title) { super(title); }

    @Inject(method = "init", at = @At("TAIL"))
    private void youzaiworldcore$initMenu(CallbackInfo ci) {
        splash = null;
        youzaiworldcore$menu = new YzuiTitleMenu((TitleScreen) (Object) this);
    }

    @Redirect(method = "extractRenderState", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/components/LogoRenderer;extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IF)V"))
    private void youzaiworldcore$hideVanillaLogo(LogoRenderer renderer, GuiGraphicsExtractor g, int width, float alpha) {
        // 品牌标志由标题页卡片布局按真实比例绘制。
    }

    @Redirect(method = "extractRenderState", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;text(Lnet/minecraft/client/gui/Font;Ljava/lang/String;III)V"))
    private void youzaiworldcore$versionColor(GuiGraphicsExtractor g, Font font, String text, int x, int y, int color) {
        var palette = YzuiTheme.palette();
        g.text(font, text, x, y, YzuiTheme.alpha(palette.translucentText(palette.textMuted(), 0.72f),
                (color >>> 24) / 255f), false);
    }

    @Inject(method = "extractRenderState", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/screens/TitleScreen;extractPanorama(Lnet/minecraft/client/gui/GuiGraphicsExtractor;F)V",
            shift = At.Shift.AFTER))
    private void youzaiworldcore$renderMenu(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        if (youzaiworldcore$menu == null) return;
        float alpha = !GuiAnimationController.isEnabled() || !fading ? 1f
                : Math.clamp((Util.getMillis() - fadeInStart) / 1000f - 1f, 0f, 1f);
        youzaiworldcore$menu.render(g, alpha);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (!GuiAnimationController.isExiting((TitleScreen) (Object) this)
                && youzaiworldcore$menu != null && youzaiworldcore$menu.keyPressed(event.key())) return true;
        return super.keyPressed(event);
    }
}
