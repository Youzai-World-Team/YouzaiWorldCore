package top.csituka.youzaiworldcore.client.render;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

/** 官网彩色标志的共用资源和绘制；复用启动窗口已有图片，仅裁去透明留白。 */
public final class YzuiBrandLogo {
    public static final String CLASSPATH_RESOURCE = "/assets/youzaiworldcore/textures/gui/startup/logo.png";
    public static final Identifier TEXTURE = Identifier.fromNamespaceAndPath(
            "youzaiworldcore", "textures/gui/startup/logo.png");
    public static final int TEXTURE_WIDTH = 4883;
    public static final int TEXTURE_HEIGHT = 1234;
    public static final int CONTENT_X = 153;
    public static final int CONTENT_Y = 120;
    public static final int CONTENT_WIDTH = 4383;
    public static final int CONTENT_HEIGHT = 1024;

    private YzuiBrandLogo() { }

    /** 初次资源重载前直接读取模组 classpath；不依赖资源管理器是否就绪。 */
    public static InputStream openStream() throws IOException {
        InputStream stream = YzuiBrandLogo.class.getResourceAsStream(CLASSPATH_RESOURCE);
        if (stream == null) throw new FileNotFoundException(CLASSPATH_RESOURCE);
        return stream;
    }

    public static int heightForWidth(int width) {
        return Math.max(1, Math.round(width * (float) CONTENT_HEIGHT / CONTENT_WIDTH));
    }

    /** 保留标志原色，只叠加页面淡入淡出透明度；提前注册的纹理可使用原版资源 ID。 */
    public static void render(GuiGraphicsExtractor g, Identifier texture, int x, int y, int width, float alpha) {
        if (width <= 0 || alpha <= 0f) return;
        g.blit(RenderPipelines.GUI_TEXTURED, texture, x, y, CONTENT_X, CONTENT_Y,
                width, heightForWidth(width), CONTENT_WIDTH, CONTENT_HEIGHT, TEXTURE_WIDTH, TEXTURE_HEIGHT,
                YzuiTheme.alpha(0xFFFFFFFF, alpha));
    }
}
