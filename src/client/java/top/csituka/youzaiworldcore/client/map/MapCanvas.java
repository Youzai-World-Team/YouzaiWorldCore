package top.csituka.youzaiworldcore.client.map;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.texture.DynamicTexture;
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
 * 返回真正已显示的视口，使标记、鼠标命中与旋转后的地形始终对齐。
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
    private record Frame(MapView view, String dimension, MapLayer layer, int height, long revision, MapRaster.Style style) { }
    private record Ready(long epoch, Frame frame, int width, int height, int[] pixels) { }

    /** 绘制圆形或圆角地图，边框与标记由地图渲染器统一处理。 */
    public MapView draw(GuiGraphicsExtractor graphics, MapView view, String dimension, MapLayer layer, int height,
                     int left, int top, int radius, float opacity) {
        double resolution = Math.min(1, 768.0 / Math.max(view.width(), view.height()));
        int tw = Math.max(1, (int) Math.ceil(view.width() * resolution));
        int th = Math.max(1, (int) Math.ceil(view.height() * resolution));
        if (texture != null && (textureWidth != tw || textureHeight != th
                || displayed != null && (!displayed.dimension.equals(dimension) || displayed.layer != layer || displayed.height != height
                || displayed.view.width() != view.width() || displayed.view.height() != view.height()))) close();
        Ready result = ready;
        if (result != null) {
            ready = null;
            if (result.epoch == epoch && result.width == tw && result.height == th && result.frame.dimension.equals(dimension)
                    && result.frame.layer == layer && result.frame.height == height) {
                if (texture == null) {
                    textureWidth = tw; textureHeight = th;
                    texture = new DynamicTexture("悠哉地图", tw, th, false);
                    Minecraft.getInstance().getTextureManager().register(id, texture);
                }
                var pixels = texture.getPixels();
                if (pixels != null) {
                    for (int y = 0; y < th; y++) for (int x = 0; x < tw; x++) pixels.setPixel(x, y, result.pixels[y * tw + x]);
                    texture.upload(); displayed = result.frame;
                }
            }
        }
        var frame = new Frame(view, dimension, layer, height, MapClient.cache().revision(), MapClient.rasterStyle(dimension));
        long now = System.nanoTime();
        if (!pending && !frame.equals(displayed) && (displayed == null || now - lastRequest >= 100_000_000L)) {
            var tiles = MapClient.cache().snapshot(dimension, layer, height);
            long ticket = epoch;
            pending = true; lastRequest = now;
            RASTER.execute(() -> {
                try {
                    int[] pixels = new int[tw * th];
                    double cos = Math.cos(view.angle()), sin = Math.sin(view.angle());
                    for (int py = 0; py < th && epoch == ticket; py++) {
                        double dz = ((py + 0.5) / th * view.height() - view.height() / 2.0) / view.scale();
                        for (int px = 0; px < tw; px++) {
                            double dx = ((px + 0.5) / tw * view.width() - view.width() / 2.0) / view.scale();
                            double wx = view.centerX() + dx * cos - dz * sin, wz = view.centerZ() + dx * sin + dz * cos;
                            pixels[py * tw + px] = Math.abs(wx) > MapTileKey.WORLD_LIMIT || Math.abs(wz) > MapTileKey.WORLD_LIMIT
                                    ? frame.style.empty() : MapRaster.color(tiles, (int) Math.floor(wx), (int) Math.floor(wz), frame.style);
                        }
                    }
                    if (epoch == ticket) ready = new Ready(ticket, frame, tw, th, pixels);
                } catch (Exception error) { DebugLogger.exception("MapCanvas", "合成地图纹理", error); }
                finally { pending = false; }
            });
        }
        if (texture != null && displayed != null) {
            RoundedRect.texture(graphics, id, left, top, view.width(), view.height(), radius,
                    textureWidth, textureHeight, YzuiTheme.alpha(0xFFFFFFFF, opacity));
            return displayed.view;
        }
        RoundedRect.fill(graphics, left, top, view.width(), view.height(), radius,
                YzuiTheme.multiplyAlpha(YzuiTheme.surface(), opacity));
        return view;
    }

    /** 页面关闭、切服或尺寸变化时释放旧 GPU 纹理与 NativeImage。 */
    @Override public void close() {
        epoch++; ready = null;
        if (texture != null) Minecraft.getInstance().getTextureManager().release(id);
        texture = null; displayed = null;
    }
}
