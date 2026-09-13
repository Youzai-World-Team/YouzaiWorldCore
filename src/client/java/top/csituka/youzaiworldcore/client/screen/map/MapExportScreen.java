package top.csituka.youzaiworldcore.client.screen.map;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import top.csituka.youzaiworldcore.client.map.MapExport;
import top.csituka.youzaiworldcore.client.map.MapTexts;
import top.csituka.youzaiworldcore.client.render.YzuiTheme;
import top.csituka.youzaiworldcore.map.MapLayer;

/** 可调整矩形和分辨率的异步导出页面，显示进度并允许取消。 */
public final class MapExportScreen extends MapScreen {
    private final String dimension;
    private final MapLayer layer;
    private final int sampleHeight;
    private final String[] coordinates;
    private int step = 1;
    private boolean annotations = true;
    private MapExport.Job job;
    private boolean observedCompletion;
    public MapExportScreen(Screen parent, String dimension, MapLayer layer, int sampleHeight, MapExport.Area area) {
        super(parent, "export"); this.dimension = dimension; this.layer = layer; this.sampleHeight = sampleHeight;
        coordinates = new String[] {Integer.toString(area.minX()), Integer.toString(area.minZ()), Integer.toString(area.maxX()), Integer.toString(area.maxZ())};
    }
    @Override protected void init() {
        super.init(); int x = panelX + 16, w = (panelWidth - 40) / 2;
        String[] keys = {"min_x", "min_z", "max_x", "max_z"};
        for (int i = 0; i < 4; i++) {
            int index = i;
            field(x + i % 2 * (w + 8), panelY + 60 + i / 2 * 42, w, keys[i], coordinates[i], 10, value -> coordinates[index] = value).setEditable(job == null || job.complete());
        }
        button(x, panelY + 134, w, MapTexts.text("export_resolution", step), () -> { step = step >= 16 ? 1 : step * 2; init(); }).active = job == null || job.complete();
        button(x + w + 8, panelY + 134, w, MapTexts.text(annotations ? "export_annotations_on" : "export_annotations_off"), () -> { annotations = !annotations; init(); }).active = job == null || job.complete();
        button(x, panelY + panelHeight - 34, w, MapTexts.text(job != null && !job.complete() ? "cancel" : "export_start"), () -> {
            if (job != null && !job.complete()) { job.cancel(); return; }
            try {
                var area = new MapExport.Area(Integer.parseInt(coordinates[0]), Integer.parseInt(coordinates[1]), Integer.parseInt(coordinates[2]), Integer.parseInt(coordinates[3]));
                job = MapExport.start(dimension, layer, sampleHeight, area, step, annotations); observedCompletion = false; error = Component.empty(); init();
            } catch (IllegalArgumentException error) { this.error = MapTexts.text(error instanceof NumberFormatException ? "export_area_invalid" : error.getMessage()); }
        });
        button(x + w + 8, panelY + panelHeight - 34, w, MapTexts.text("done"), this::onClose);
    }
    @Override public void tick() {
        if (job != null && job.complete()) {
            error = job.result() != null ? MapTexts.text("export_saved", job.result().getFileName().toString()) : MapTexts.text(job.error());
            if (!observedCompletion) { observedCompletion = true; init(); }
        }
    }
    @Override protected void content(GuiGraphicsExtractor g, int x, int y, float delta) {
        int w = (panelWidth - 40) / 2;
        String[] keys = {"min_x", "min_z", "max_x", "max_z"};
        for (int i = 0; i < 4; i++) label(g, MapTexts.text(keys[i]), panelX + 16 + i % 2 * (w + 8), panelY + 45 + i / 2 * 42, w);
        if (job != null && !job.complete()) label(g, MapTexts.text("export_progress", job.progress()), panelX + 16, panelY + 169, panelWidth - 32);
        else if (job == null) YzuiTheme.wrapped(g, font, MapTexts.text("export_hint"), panelX + 16, panelY + 165, panelWidth - 32,
                Math.max(1, (panelHeight - 211) / 11), YzuiTheme.textMuted());
        if (job != null && job.result() != null) {
            YzuiTheme.label(g, font, Component.literal(job.result().toString()), panelX + 16, panelY + 164, panelWidth - 32, YzuiTheme.textMuted(), false);
        }
    }
    @Override public void onClose() { if (job != null && !job.complete()) job.cancel(); super.onClose(); }
}
