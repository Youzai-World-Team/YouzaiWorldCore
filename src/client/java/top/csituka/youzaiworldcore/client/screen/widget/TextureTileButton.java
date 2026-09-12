package top.csituka.youzaiworldcore.client.screen.widget;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import top.csituka.youzaiworldcore.client.animation.YzuiHover;
import top.csituka.youzaiworldcore.client.render.RoundedRect;
import top.csituka.youzaiworldcore.client.render.YzuiTheme;

/** 整图导航按钮：图片覆盖全部按钮，名称仅用于悬停提示和无障碍朗读。 */
public class TextureTileButton extends TransparentButton {
    private final Identifier texture;
    private final YzuiHover hoverState = new YzuiHover();

    public TextureTileButton(int x, int y, int width, int height, Identifier texture, Runnable onPress) {
        super(x, y, width, height, label(texture), onPress);
        this.texture = texture;
        setTooltip(Tooltip.create(getMessage()));
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        if (!visible) return;
        int x = getX(), y = getY(), w = getWidth(), h = getHeight();
        isHovered = mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
        float hover = hoverState.sample(active && isHovered);
        float opacity = externalAlpha() * getAlpha() * (active ? 1f : 0.45f);
        int radius = Math.min(12, h / 4);
        var image = Minecraft.getInstance().getTextureManager().getTexture(texture).getTexture();
        RoundedRect.texture(g, texture, x, y, w, h, radius, image.getWidth(0), image.getHeight(0),
                YzuiTheme.alpha(0xFFFFFFFF, opacity));
        if (active && (hover > 0f || isFocused())) {
            RoundedRect.fill(g, x, y, w, h, radius,
                    YzuiTheme.alpha(0xFFFFFFFF, opacity * (0.08f * hover + (isFocused() ? 0.04f : 0f))));
        }
        YzuiTheme.border(g, x, y, w, h, radius,
                YzuiTheme.alpha(isFocused() ? YzuiTheme.primary() : YzuiTheme.outlineVariant(),
                        opacity * (isFocused() ? 1f : 0.35f + hover * 0.25f)));
    }


    private static Component label(Identifier texture) {
        String path = texture.getPath();
        String name = path.substring(path.lastIndexOf('/') + 1).replace(".png", "").replace('-', '_');
        return Component.translatable("screen.youzaiworldcore.navigation." + name);
    }
}
