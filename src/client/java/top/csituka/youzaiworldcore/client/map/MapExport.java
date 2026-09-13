package top.csituka.youzaiworldcore.client.map;

import top.csituka.youzaiworldcore.config.ModPaths;
import top.csituka.youzaiworldcore.map.MapLayer;
import top.csituka.youzaiworldcore.map.MapTileKey;
import top.csituka.youzaiworldcore.util.DebugLogger;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/** 已探索地图的 PNG 导出；像素和标注均使用主线程捕获的不可变快照。 */
public final class MapExport {
    public static final long MAX_PIXELS = 16_777_216;
    private static final AtomicBoolean BUSY = new AtomicBoolean();
    private static final java.util.concurrent.ExecutorService WORKER = Executors.newSingleThreadExecutor(
            Thread.ofPlatform().daemon().name("yzwc-map-export").factory());
    private MapExport() { }

    /** 导出边界使用左上包含、右下不包含的世界坐标。 */
    public record Area(int minX, int minZ, int maxX, int maxZ) {
        public Area {
            if (minX < -MapTileKey.WORLD_LIMIT || minZ < -MapTileKey.WORLD_LIMIT || maxX > MapTileKey.WORLD_LIMIT
                    || maxZ > MapTileKey.WORLD_LIMIT || maxX <= minX || maxZ <= minZ) throw new IllegalArgumentException("export_area_invalid");
        }
        /** 将地图拖选端点规范化，保证至少一格且不会越过世界边界。 */
        public static Area of(double x1, double z1, double x2, double z2) {
            int x = (int) Math.clamp(Math.floor(Math.min(x1, x2)), -MapTileKey.WORLD_LIMIT, MapTileKey.WORLD_LIMIT - 1);
            int z = (int) Math.clamp(Math.floor(Math.min(z1, z2)), -MapTileKey.WORLD_LIMIT, MapTileKey.WORLD_LIMIT - 1);
            return new Area(x, z, (int) Math.clamp(Math.ceil(Math.max(x1, x2)), x + 1, MapTileKey.WORLD_LIMIT),
                    (int) Math.clamp(Math.ceil(Math.max(z1, z2)), z + 1, MapTileKey.WORLD_LIMIT));
        }
        public int width(int step) { return Math.ceilDiv(maxX - minX, step); }
        public int height(int step) { return Math.ceilDiv(maxZ - minZ, step); }
    }

    /** 导出状态；取消只影响本次未发布的图片，不删除任何已有文件。 */
    public static final class Job {
        private volatile int progress;
        private volatile boolean cancelled, complete;
        private volatile Path result;
        private volatile String error;
        public int progress() { return progress; }
        public boolean complete() { return complete; }
        public Path result() { return result; }
        public String error() { return error; }
        public void cancel() { cancelled = true; }
    }

    /** 在客户端线程发起，随后工作线程不访问世界、注册表、GPU 或 Minecraft 字体。 */
    public static Job start(String dimension, MapLayer layer, int height, Area area, int blocksPerPixel, boolean annotations) {
        if (blocksPerPixel < 1 || blocksPerPixel > 16) throw new IllegalArgumentException("export_area_invalid");
        int width = area.width(blocksPerPixel), length = area.height(blocksPerPixel);
        if (width > 8192 || length > 8192 || (long) width * length > MAX_PIXELS) throw new IllegalArgumentException("export_too_large");
        if (!BUSY.compareAndSet(false, true)) throw new IllegalArgumentException("export_busy");
        Job job = new Job();
        try {
            submit(job, dimension, layer, height, area, blocksPerPixel, annotations, width, length);
        } catch (RuntimeException error) {
            job.error = "export_failed";
            job.complete = true;
            BUSY.set(false);
            DebugLogger.exception("MapExport", "准备导出快照", error);
        }
        return job;
    }

    private static void submit(Job job, String dimension, MapLayer layer, int height, Area area,
                               int blocksPerPixel, boolean annotations, int width, int length) {
        var tiles = MapClient.cache().snapshot(dimension, layer, height);
        var original = MapClient.rasterStyle(dimension);
        var style = new MapRaster.Style(original.overlay(), original.lighting(), original.skyDarken(), original.gamma(), 0, 0, original.loadLevels());
        var points = annotations ? MapClient.waypoints().stream().filter(point -> point.enabled() && point.dimension().equals(dimension)).toList() : java.util.List.<top.csituka.youzaiworldcore.map.MapWaypoint>of();
        var drawings = annotations ? MapPersonalData.drawings().stream().filter(d -> d.dimension().equals(dimension)).toList() : java.util.List.<top.csituka.youzaiworldcore.map.MapDrawing>of();
        var names = points.stream().map(point -> MapTexts.waypoint(point).getString()).toList();
        Path directory = ModPaths.mapExports();
        WORKER.execute(() -> {
            Path staging = null;
            try {
                BufferedImage image = new BufferedImage(width, length, BufferedImage.TYPE_INT_ARGB);
                int[] row = new int[width];
                for (int y = 0; y < length; y++) {
                    if (job.cancelled) return;
                    for (int x = 0; x < width; x++) row[x] = MapRaster.color(tiles, area.minX + x * blocksPerPixel, area.minZ + y * blocksPerPixel, style);
                    image.setRGB(0, y, width, 1, row, 0, width); job.progress = (int) ((long) (y + 1) * 85 / length);
                }
                var graphics = image.createGraphics();
                try {
                    graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    graphics.setStroke(new BasicStroke(2, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                    graphics.setFont(new Font(Font.DIALOG, Font.PLAIN, 12));
                    for (var drawing : drawings) {
                        if (job.cancelled) return;
                        graphics.setColor(new Color(drawing.color(), true));
                        var path = MapShapes.path(drawing); var first = path.getFirst();
                        double x = (first.x() - area.minX) / blocksPerPixel, y = (first.z() - area.minZ) / blocksPerPixel;
                        if (drawing.kind() == top.csituka.youzaiworldcore.map.MapDrawing.Kind.LABEL) graphics.drawString(drawing.label(), (float) x, (float) y);
                        else {
                            var shape = new Path2D.Double(); shape.moveTo(x, y);
                            for (int i = 1; i < path.size(); i++) shape.lineTo((path.get(i).x() - area.minX) / blocksPerPixel, (path.get(i).z() - area.minZ) / blocksPerPixel);
                            graphics.draw(shape);
                        }
                    }
                    for (int i = 0; i < points.size(); i++) {
                        var point = points.get(i);
                        if (point.x() < area.minX || point.x() >= area.maxX || point.z() < area.minZ || point.z() >= area.maxZ) continue;
                        int x = (point.x() - area.minX) / blocksPerPixel, y = (point.z() - area.minZ) / blocksPerPixel;
                        graphics.setColor(Color.BLACK); graphics.fillOval(x - 4, y - 4, 9, 9);
                        graphics.setColor(new Color(point.color(), true)); graphics.fillOval(x - 3, y - 3, 7, 7);
                        graphics.drawString(names.get(i), x + 7, y + 4);
                    }
                } finally { graphics.dispose(); }
                if (job.cancelled) return;
                Files.createDirectories(directory);
                String name = "map-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")) + "-" + UUID.randomUUID().toString().substring(0, 8);
                staging = directory.resolve(name + ".tmp"); Path target = directory.resolve(name + ".png");
                job.progress = 90;
                try (var output = Files.newOutputStream(staging, java.nio.file.StandardOpenOption.CREATE_NEW)) {
                    if (!ImageIO.write(image, "PNG", output)) throw new java.io.IOException("PNG 编码器不可用");
                }
                if (job.cancelled) return;
                Files.move(staging, target); staging = null; job.result = target; job.progress = 100;
                DebugLogger.info("MapExport", "地图已导出：%s，%d × %d", target, width, length);
            } catch (Exception error) { job.error = "export_failed"; DebugLogger.exception("MapExport", "导出 PNG", error); }
            finally {
                if (staging != null) try { Files.deleteIfExists(staging); } catch (java.io.IOException error) { DebugLogger.exception("MapExport", "清理未完成图片", error); }
                if (job.cancelled && job.result == null) job.error = "export_cancelled";
                job.complete = true; BUSY.set(false);
            }
        });
    }
}
