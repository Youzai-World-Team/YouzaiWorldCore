package top.csituka.youzaiworldcore.client.map;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import top.csituka.youzaiworldcore.client.render.RoundedRect;
import top.csituka.youzaiworldcore.client.render.YzuiTheme;
import top.csituka.youzaiworldcore.map.MapLayer;
import top.csituka.youzaiworldcore.map.MapTileKey;
import top.csituka.youzaiworldcore.util.DebugLogger;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 后台合成地图像素，主线程只上传完成的纹理；每张画布至多一项待处理任务。
 * 普通地图返回纹理对应的视口；交互地图实时投影旧纹理并返回当前视口，保持地形、标记和命中对齐。
 */
public final class MapCanvas implements AutoCloseable {
    private static final ExecutorService RASTER = Executors.newSingleThreadExecutor(
            Thread.ofPlatform().daemon().name("yzwc-map-raster").factory());
    private static int sequence;
    private final Identifier id = Identifier.fromNamespaceAndPath("youzaiworldcore", "map/canvas_" + sequence++);
    private DynamicTexture texture;
    private int textureWidth, textureHeight;
    private long lastRequest;
    private Frame displayed;
    private volatile Ready ready;
    private volatile boolean pending;
    private volatile long epoch;
    private record Frame(MapView view, String dimension, MapLayer layer, int height, long revision, MapRaster.Style style, float pixelScale) { }
    private record Ready(long epoch, Frame frame, int width, int height, byte[] pixels) { }

    /**
     * 绘制圆形或圆角地图，边框与标记由地图渲染器统一处理。
     *
     * @param pixelScale 视口设计单位到物理像素的比例，纹理按此提高分辨率避免缩放后发虚
     */
    public MapView draw(GuiGraphicsExtractor graphics, MapView view, String dimension, MapLayer layer, int height,
                     int left, int top, int radius, float opacity, float pixelScale) {
        return draw(graphics, view, dimension, layer, height, left, top, radius, opacity, pixelScale, false);
    }

    /** 全屏地图每帧变换已有纹理，交互不等待后台地形重绘；命中和标记使用当前视口。 */
    public MapView drawInteractive(GuiGraphicsExtractor graphics, MapView view, String dimension, MapLayer layer, int height,
                                   int left, int top, float pixelScale) {
        return draw(graphics, view, dimension, layer, height, left, top, 0, 1, pixelScale, true);
    }

    private MapView draw(GuiGraphicsExtractor graphics, MapView view, String dimension, MapLayer layer, int height,
                         int left, int top, int radius, float opacity, float pixelScale, boolean interactive) {
        double resolution = Math.min(pixelScale, 768.0 / Math.max(view.width(), view.height()));
        int tw = Math.max(1, (int) Math.ceil(view.width() * resolution));
        int th = Math.max(1, (int) Math.ceil(view.height() * resolution));
        if (texture != null && (textureWidth != tw || textureHeight != th
                || displayed != null && (!displayed.dimension.equals(dimension) || displayed.layer != layer || displayed.height != height
                || displayed.view.width() != view.width() || displayed.view.height() != view.height()
                || Float.compare(displayed.pixelScale, pixelScale) != 0))) close();
        Ready result = ready;
        if (result != null) {
            ready = null;
            if (result.epoch == epoch && result.width == tw && result.height == th && result.frame.dimension.equals(dimension)
                    && result.frame.layer == layer && result.frame.height == height
                    && Float.compare(result.frame.pixelScale, pixelScale) == 0) {
                if (texture == null) {
                    textureWidth = tw; textureHeight = th;
                    texture = new DynamicTexture("悠哉地图", tw, th, false);
                    Minecraft.getInstance().getTextureManager().register(id, texture);
                }
                var pixels = texture.getPixels();
                if (pixels != null) {
                    // NativeImage 使用 RGBA 字节；颜色转换已在后台完成，主线程只做一次连续复制。
                    pixels.getPixelBytes().put(result.pixels);
                    texture.upload(); displayed = result.frame;
                }
            }
        }
        var frame = new Frame(view, dimension, layer, height, MapClient.cache().revision(), MapClient.rasterStyle(dimension), pixelScale);
        long now = System.nanoTime();
        if (!pending && !frame.equals(displayed) && (displayed == null || now - lastRequest >= 100_000_000L)) {
            var tiles = MapClient.cache().snapshot(dimension, layer, height);
            long ticket = epoch;
            pending = true; lastRequest = now;
            RASTER.execute(() -> {
                try {
                    byte[] pixels = new byte[tw * th * 4];
                    double cos = Math.cos(view.angle()), sin = Math.sin(view.angle());
                    for (int py = 0; py < th && epoch == ticket; py++) {
                        double dz = ((py + 0.5) / th * view.height() - view.height() / 2.0) / view.scale();
                        for (int px = 0; px < tw; px++) {
                            double dx = ((px + 0.5) / tw * view.width() - view.width() / 2.0) / view.scale();
                            double wx = view.centerX() + dx * cos - dz * sin, wz = view.centerZ() + dx * sin + dz * cos;
                            int color = Math.abs(wx) > MapTileKey.WORLD_LIMIT || Math.abs(wz) > MapTileKey.WORLD_LIMIT
                                    ? frame.style.empty() : MapRaster.color(tiles, (int) Math.floor(wx), (int) Math.floor(wz), frame.style);
                            int offset = (py * tw + px) * 4;
                            pixels[offset] = (byte) (color >>> 16);
                            pixels[offset + 1] = (byte) (color >>> 8);
                            pixels[offset + 2] = (byte) color;
                            pixels[offset + 3] = (byte) (color >>> 24);
                        }
                    }
                    if (epoch == ticket) ready = new Ready(ticket, frame, tw, th, pixels);
                } catch (Exception error) { DebugLogger.exception("MapCanvas", "合成地图纹理", error); }
                finally { pending = false; }
            });
        }
        if (texture != null && displayed != null) {
            if (interactive) {
                drawTransformed(graphics, view, left, top, opacity);
                return view;
            }
            RoundedRect.texture(graphics, id, left, top, view.width(), view.height(), radius,
                    textureWidth, textureHeight, YzuiTheme.alpha(0xFFFFFFFF, opacity));
            return displayed.view;
        }
        RoundedRect.fill(graphics, left, top, view.width(), view.height(), radius,
                YzuiTheme.multiplyAlpha(YzuiTheme.surface(), opacity));
        return view;
    }

    private void drawTransformed(GuiGraphicsExtractor graphics, MapView view, int left, int top, float opacity) {
        // 新露出的区域先铺底色；后台纹理到达后仍按当前视口投影，避免回跳到旧位置。
        graphics.fill(left, top, left + view.width(), top + view.height(),
                YzuiTheme.multiplyAlpha(displayed.style.unknown(), opacity));
        MapView source = displayed.view;
        MapView.Point center = view.screen(source.centerX(), source.centerZ());
        float zoom = (float) (view.scale() / source.scale());
        graphics.enableScissor(left, top, left + view.width(), top + view.height());
        graphics.pose().pushMatrix();
        try {
            graphics.pose().translate((float) (left + center.x()), (float) (top + center.y()));
            graphics.pose().rotate((float) (source.angle() - view.angle()));
            graphics.pose().scale(zoom, zoom);
            graphics.pose().translate(-source.width() / 2f, -source.height() / 2f);
            graphics.blit(RenderPipelines.GUI_TEXTURED, id, 0, 0, 0, 0,
                    source.width(), source.height(), textureWidth, textureHeight,
                    textureWidth, textureHeight, YzuiTheme.alpha(0xFFFFFFFF, opacity));
        } finally {
            graphics.pose().popMatrix();
            graphics.disableScissor();
        }
    }

    /** 页面关闭、切服或尺寸变化时释放旧 GPU 纹理与 NativeImage。 */
    @Override public void close() {
        epoch++; ready = null;
        if (texture != null) Minecraft.getInstance().getTextureManager().release(id);
        texture = null; displayed = null;
    }
}
