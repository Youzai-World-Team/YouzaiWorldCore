package top.csituka.youzaiworldcore.client.screen.map;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import top.csituka.youzaiworldcore.client.map.MapClient;
import top.csituka.youzaiworldcore.client.map.MapPersonalData;
import top.csituka.youzaiworldcore.client.map.MapTexts;
import top.csituka.youzaiworldcore.client.map.MapTransfer;
import top.csituka.youzaiworldcore.map.MapWaypoint;

import java.util.List;

/** 从剪贴板解析并预览路径点，明确确认后才批量写入私人配置。 */
public final class MapImportScreen extends MapScreen {
    private String dimension = MapClient.dimension();
    private List<MapWaypoint> preview = List.of();
    public MapImportScreen(Screen parent) { super(parent, "import"); }
    @Override protected void init() {
        super.init();
        field(panelX + 16, panelY + 66, panelWidth - 32, "dimension", dimension, 128, value -> { dimension = value; preview = List.of(); });
        button(panelX + 16, panelY + 96, panelWidth - 32, MapTexts.text("read_clipboard"), () -> {
            var player = Minecraft.getInstance().player;
            if (player == null) { error = MapTexts.text("world_required"); return; }
            try { preview = MapTransfer.decode(Minecraft.getInstance().keyboardHandler.getClipboard(), dimension, player.getUUID()); error = MapTexts.text("import_preview", preview.size()); }
            catch (IllegalArgumentException failure) { preview = List.of(); error = MapTexts.text(failure.getMessage()); }
        });
        button(panelX + 16, panelY + panelHeight - 34, (panelWidth - 38) / 2, MapTexts.text("confirm_import"), () -> {
            if (preview.isEmpty()) { error = MapTexts.text("import_invalid"); return; }
            int added = MapPersonalData.importPoints(preview);
            if (added < 0) error = MapTexts.text("limit");
            else { MapClient.message("imported", added); onClose(); }
        });
        button(panelX + panelWidth / 2 + 3, panelY + panelHeight - 34, (panelWidth - 38) / 2, MapTexts.text("cancel"), this::onClose);
    }
    @Override protected void content(GuiGraphicsExtractor g, int x, int y, float delta) {
        label(g, MapTexts.text("import_dimension"), panelX + 16, panelY + 46, panelWidth - 32);
        if (preview.isEmpty()) top.csituka.youzaiworldcore.client.render.YzuiTheme.wrapped(g, font, MapTexts.text("import_hint"), panelX + 16, panelY + 133, panelWidth - 32, Math.max(1, (panelHeight - 186) / 11),
                top.csituka.youzaiworldcore.client.render.YzuiTheme.textMuted());
        else for (int row = 0; row < Math.min(preview.size(), Math.max(1, (panelHeight - 187) / 12)); row++) {
            var point = preview.get(row);
            label(g, net.minecraft.network.chat.Component.literal(point.name() + "  " + point.x() + "/" + point.y() + "/" + point.z())
                    .append(" · ").append(MapTexts.dimension(point.dimension())), panelX + 16, panelY + 133 + row * 12, panelWidth - 32);
        }
    }
}
