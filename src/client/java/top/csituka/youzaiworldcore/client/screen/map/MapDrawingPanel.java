package top.csituka.youzaiworldcore.client.screen.map;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import top.csituka.youzaiworldcore.client.config.MapSettings;
import top.csituka.youzaiworldcore.client.map.MapPersonalData;
import top.csituka.youzaiworldcore.client.map.MapShapes;
import top.csituka.youzaiworldcore.client.map.MapTexts;
import top.csituka.youzaiworldcore.client.render.YzuiTheme;
import top.csituka.youzaiworldcore.client.screen.widget.TransparentButton;
import top.csituka.youzaiworldcore.map.MapDrawing;
import top.csituka.youzaiworldcore.map.MapVertex;
import top.csituka.youzaiworldcore.util.DebugLogger;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** 画笔管理器：工具、色板、绘图卡片及显示选项，复用原绘图数据和撤销历史。 */
@SuppressWarnings("null")
final class MapDrawingPanel extends MapOverlayPanel {
    private enum Tab { TOOLS, LIBRARY, DISPLAY }
    private enum Page { MAIN, DETAIL, COLOR }
    private Tab tab = Tab.TOOLS;
    private Page page = Page.MAIN;
    private final List<TransparentButton> rows = new ArrayList<>(), selectionActions = new ArrayList<>();
    private List<MapDrawing> snapshot = List.of(), filtered = List.of();
    private UUID selected, colorTarget;
    private String query = "", colorText = "";
    private boolean currentDimension = true, dirty, compact;
    private int footer, contentTop, listTop, listBottom, rowHeight, capacity, listPage, actionTop;
    private EditBox searchBox;
    private TransparentButton previous, next, undo, redo;

    MapDrawingPanel(YzWorldMapScreen owner) {
        super(owner, "drawing_manager");
        var drawing = owner.selectedDrawing(); if (drawing != null) { selected = drawing.id(); tab = Tab.LIBRARY; }
        DebugLogger.debug("MapDrawings", "打开画笔与绘图管理器");
    }

    @Override protected void init() {
        boolean restoreSearch = searchBox != null && getFocused() == searchBox;
        super.init(); rows.clear(); selectionActions.clear(); searchBox = null; undo = null; redo = null;
        panelWidth = Math.min(width - 16, Math.max(304, Math.min(460, width / 2)));
        compact = panelHeight < 340; footer = panelY + panelHeight - 32;
        control(panelX + panelWidth - 32, panelY + 7, 22, Component.literal("×"), "done", this::onClose);
        if (page == Page.COLOR) { buildColor(); return; }
        if (page == Page.DETAIL && selection() != null) { buildDetail(); return; }
        page = Page.MAIN;
        int tabY = panelY + (compact ? 36 : 46), cell = (panelWidth - 32) / 3;
        for (var value : Tab.values()) {
            String key = switch (value) { case TOOLS -> "drawing_tools"; case LIBRARY -> "drawing_library"; case DISPLAY -> "settings"; };
            var button = control(panelX + 12 + value.ordinal() * (cell + 4), tabY, cell, MapTexts.text(key), key, () -> { tab = value; error = Component.empty(); init(); });
            button.setStyle(value == tab ? YzuiTheme.ButtonStyle.FILLED : YzuiTheme.ButtonStyle.TONAL);
        }
        contentTop = tabY + 30;
        switch (tab) { case TOOLS -> buildTools(); case LIBRARY -> buildLibrary(); case DISPLAY -> buildDisplay(); }
        if (restoreSearch && searchBox != null) setFocused(searchBox);
    }

    private TransparentButton control(int x, int y, int width, Component label, String key, Runnable action) {
        var button = button(x, y, width, label, action); button.setTooltip(Tooltip.create(MapTexts.text(key))); return button;
    }
    private static String toolKey(YzWorldMapScreen.Tool tool) { return "tool." + tool.name().toLowerCase(Locale.ROOT); }
    private static Component name(MapDrawing drawing) { return drawing.label().isBlank() ? MapTexts.text("tool." + drawing.kind().name().toLowerCase(Locale.ROOT)) : Component.literal(drawing.label()); }
    private MapDrawing selection() { return MapPersonalData.drawings().stream().filter(value -> value.id().equals(selected)).findFirst().orElse(null); }

    private void buildTools() {
        int cell = (panelWidth - 36) / 3, height = compact ? 36 : 60;
        for (var tool : YzWorldMapScreen.Tool.values()) {
            int x = panelX + 12 + tool.ordinal() % 3 * (cell + 6), y = contentTop + tool.ordinal() / 3 * (height + 6);
            var button = addRenderableWidget(new TransparentButton(x, y, cell, height, MapTexts.text(toolKey(tool)), () -> owner.chooseDrawingTool(tool)) {
                @Override protected void extractWidgetRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
                    var style = tool == owner.drawingTool() ? YzuiTheme.ButtonStyle.FILLED : YzuiTheme.ButtonStyle.TONAL;
                    YzuiTheme.button(g, getX(), getY(), getWidth(), getHeight(), isMouseOver(mx, my) ? 1 : 0, isFocused(), active, 1, style);
                    int color = YzuiTheme.buttonText(style, true), iconSize = compact ? 14 : 24;
                    toolIcon(g, tool, getX() + (getWidth() - iconSize) / 2, getY() + 5, iconSize, color);
                    YzuiTheme.label(g, font, getMessage(), getX() + 3, getY() + getHeight() - 13, getWidth() - 6, color, true);
                }
            });
            button.setTooltip(Tooltip.create(MapTexts.text(toolKey(tool))));
        }
        int paletteY = contentTop + (height + 6) * 2 + (compact ? 0 : 12);
        int swatchWidth = (panelWidth - 48) / 7;
        for (int i = 0; i < YzWorldMapScreen.COLORS.length; i++) {
            int color = YzWorldMapScreen.COLORS[i];
            swatch(panelX + 12 + i * (swatchWidth + 4), paletteY, swatchWidth, color, () -> owner.setDrawingColor(color));
        }
        control(panelX + 12 + 6 * (swatchWidth + 4), paletteY, swatchWidth, Component.literal("#"), "drawing_custom_color", () -> openColor(null));
        historyButtons();
    }

    private void swatch(int x, int y, int width, int color, Runnable action) {
        var button = addRenderableWidget(new TransparentButton(x, y, width, 22, Component.literal(String.format(Locale.ROOT, "#%06X", color & 0xFFFFFF)), action) {
            @Override protected void extractWidgetRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
                YzuiTheme.button(g, getX(), getY(), getWidth(), getHeight(), isMouseOver(mx, my) ? 1 : 0, isFocused(), true, 1, YzuiTheme.ButtonStyle.TONAL);
                g.fill(getX() + 5, getY() + 5, getX() + getWidth() - 5, getY() + 17, color);
                if (page == Page.MAIN && color == owner.drawingColor()) g.outline(getX() + 2, getY() + 2, getWidth() - 4, 18, YzuiTheme.primary());
            }
        }); button.setTooltip(Tooltip.create(button.getMessage()));
    }

    private void historyButtons() {
        int half = (panelWidth - 30) / 2;
        undo = control(panelX + 12, footer, half, MapTexts.text("undo"), "undo", () -> { MapPersonalData.undo(); dirty = true; });
        redo = control(panelX + 18 + half, footer, half, MapTexts.text("redo"), "redo", () -> { MapPersonalData.redo(); dirty = true; });
        undo.active = MapPersonalData.canUndo(); redo.active = MapPersonalData.canRedo();
    }

    private void buildLibrary() {
        searchBox = field(panelX + 12, contentTop, panelWidth - 124, "drawing_search", query, 96, value -> { query = value; listPage = 0; dirty = true; });
        searchBox.setHint(MapTexts.text("drawing_search"));
        String key = currentDimension ? "drawing_current_dimension" : "drawing_all_dimensions";
        control(panelX + panelWidth - 106, contentTop - 1, 94, MapTexts.text(key), key, () -> { currentDimension = !currentDimension; listPage = 0; init(); });
        listTop = contentTop + 28; actionTop = footer - 60; listBottom = actionTop - 6;
        rowHeight = compact ? 36 : 52; capacity = Math.max(1, (listBottom - listTop) / rowHeight);
        previous = control(panelX + 12, footer, 24, Component.literal("‹"), "previous", () -> { listPage--; refreshRows(); });
        next = control(panelX + panelWidth - 36, footer, 24, Component.literal("›"), "next", () -> { listPage++; refreshRows(); });
        refreshRows();
    }
    private int maxPage() { return Math.max(0, (filtered.size() - 1) / capacity); }
    private void refreshRows() {
        snapshot = MapPersonalData.drawings(); dirty = false;
        String search = query.strip().toLowerCase(Locale.ROOT);
        filtered = snapshot.reversed().stream().filter(d -> !currentDimension || d.dimension().equals(owner.mapDimension()))
                .filter(d -> (name(d).getString() + " " + MapTexts.text("tool." + d.kind().name().toLowerCase(Locale.ROOT)).getString() + " " + d.dimension()).toLowerCase(Locale.ROOT).contains(search)).toList();
        listPage = Math.clamp(listPage, 0, maxPage());
        if (selected != null && filtered.stream().noneMatch(d -> d.id().equals(selected))) selected = null;
        for (var row : rows) removeWidget(row); rows.clear();
        for (int i = 0; i < capacity && listPage * capacity + i < filtered.size(); i++) rows.add(addRenderableWidget(new DrawingRow(filtered.get(listPage * capacity + i), listTop + i * rowHeight)));
        previous.active = listPage > 0; next.active = listPage < maxPage(); refreshSelection();
    }
    private void refreshSelection() {
        for (var action : selectionActions) removeWidget(action); selectionActions.clear();
        var drawing = selection(); if (drawing == null) return;
        int cell = (panelWidth - 32) / 3;
        String[] keys = {"locate", "color", "manager_more"};
        Runnable[] actions = {() -> owner.locateDrawing(drawing), () -> openColor(drawing.id()), () -> { page = Page.DETAIL; init(); }};
        for (int i = 0; i < keys.length; i++) selectionActions.add(control(panelX + 12 + i * (cell + 4), actionTop + 15, cell, MapTexts.text(keys[i]), keys[i], actions[i]));
    }

    private void buildDisplay() {
        int y = contentTop;
        String drawings = "option.drawings";
        button(panelX + 12, y, panelWidth - 24, MapTexts.text("setting_value", MapTexts.text(drawings), MapTexts.text(MapSettings.enabled(MapSettings.Toggle.DRAWINGS) ? "on" : "off")), () -> { MapSettings.toggle(MapSettings.Toggle.DRAWINGS); init(); });
        button(panelX + 12, y + 30, panelWidth - 24, MapTexts.text("layer." + owner.drawingLayer().name().toLowerCase(Locale.ROOT)), () -> { owner.cycleDrawingLayer(); init(); });
        button(panelX + 12, y + 60, panelWidth - 24, MapTexts.text("overlay." + MapSettings.overlay().name().toLowerCase(Locale.ROOT)), () -> {
            var values = MapSettings.Overlay.values(); MapSettings.setOverlay(values[(MapSettings.overlay().ordinal() + 1) % values.length]); init();
        });
        historyButtons();
    }

    private void buildDetail() {
        var drawing = selection(); int cell = (panelWidth - 30) / 2, y = footer - 70;
        control(panelX + 12, y, cell, MapTexts.text("drawing_move"), "drawing_move", () -> owner.locateDrawing(drawing));
        control(panelX + 18 + cell, y, cell, MapTexts.text("drawing_name"), "drawing_name", () -> {
            Minecraft.getInstance().gui.setScreen(new MapTextScreen(owner, "drawing_name", drawing.label(), 96, value -> {
                var current = selection();
                if (current != null && !value.equals(current.label())
                        && !MapPersonalData.putDrawing(new MapDrawing(current.id(), current.dimension(), current.kind(), current.vertices(), current.color(), value))) error = MapTexts.text("limit");
                dirty = true;
            }));
        });
        control(panelX + 12, y + 28, cell, MapTexts.text("color"), "color", () -> openColor(drawing.id()));
        control(panelX + 18 + cell, y + 28, cell, MapTexts.text("delete"), "delete", () -> { MapPersonalData.removeDrawing(drawing.id()); selected = null; page = Page.MAIN; error = MapTexts.text("drawing_deleted"); init(); });
        control(panelX + 12, footer, panelWidth - 24, MapTexts.text("manager_back"), "manager_back", () -> { page = Page.MAIN; init(); });
    }

    private void openColor(UUID target) {
        colorTarget = target; var drawing = selection();
        int color = target == null || drawing == null ? owner.drawingColor() : drawing.color();
        colorText = String.format(Locale.ROOT, "#%06X", color & 0xFFFFFF); page = Page.COLOR; error = Component.empty(); init();
    }
    private static Integer parseColor(String value) {
        if (!value.matches("#?[0-9a-fA-F]{6}")) return null;
        return 0xFF000000 | Integer.parseInt(value.startsWith("#") ? value.substring(1) : value, 16);
    }
    private void buildColor() {
        var input = field(panelX + 12, panelY + 64, panelWidth - 24, "color", colorText, 7, value -> colorText = value); setFocused(input);
        int cell = (panelWidth - 44) / 6;
        for (int i = 0; i < YzWorldMapScreen.COLORS.length; i++) {
            int color = YzWorldMapScreen.COLORS[i];
            swatch(panelX + 12 + i * (cell + 4), panelY + 98, cell, color, () -> input.setValue(String.format(Locale.ROOT, "#%06X", color & 0xFFFFFF)));
        }
        int half = (panelWidth - 30) / 2;
        button(panelX + 12, footer, half, MapTexts.text("save"), () -> {
            Integer color = parseColor(colorText.strip());
            if (color == null) { error = MapTexts.text("drawing_invalid_color"); return; }
            if (colorTarget != null) {
                var drawing = MapPersonalData.drawings().stream().filter(d -> d.id().equals(colorTarget)).findFirst().orElse(null);
                if (drawing == null) { page = Page.MAIN; init(); return; }
                if (color != drawing.color() && !MapPersonalData.putDrawing(new MapDrawing(drawing.id(), drawing.dimension(), drawing.kind(), drawing.vertices(), color, drawing.label()))) { error = MapTexts.text("limit"); return; }
            } else owner.setDrawingColor(color);
            page = Page.MAIN; error = Component.empty(); init();
        }).setStyle(YzuiTheme.ButtonStyle.FILLED);
        button(panelX + 18 + half, footer, half, MapTexts.text("cancel"), () -> { page = Page.MAIN; error = Component.empty(); init(); });
    }

    @Override public void onClose() { if (page != Page.MAIN) { page = Page.MAIN; error = Component.empty(); init(); } else super.onClose(); }
    @Override public void tick() {
        if (undo != null) undo.active = MapPersonalData.canUndo();
        if (redo != null) redo.active = MapPersonalData.canRedo();
        if (page == Page.MAIN && tab == Tab.LIBRARY && (dirty || !snapshot.equals(MapPersonalData.drawings()))) refreshRows();
        if (page == Page.DETAIL && selection() == null) { page = Page.MAIN; init(); }
    }

    @Override protected void content(GuiGraphicsExtractor g, int mx, int my, float delta) {
        if (page == Page.COLOR) {
            label(g, MapTexts.text("drawing_custom_color"), panelX + 12, panelY + 44, panelWidth - 24);
            Integer color = parseColor(colorText.strip());
            if (color != null) g.fill(panelX + 12, panelY + 134, panelX + panelWidth - 12, panelY + 154, color);
            return;
        }
        if (page == Page.DETAIL) {
            var drawing = selection(); if (drawing == null) return;
            label(g, name(drawing), panelX + 12, panelY + 40, panelWidth - 24);
            preview(g, drawing, panelX + 18, panelY + 58, panelWidth - 36, Math.max(40, footer - 138)); return;
        }
        if (tab == Tab.LIBRARY) {
            if (filtered.isEmpty()) label(g, MapTexts.text("drawing_empty"), panelX + 12, listTop + 8, panelWidth - 24);
            var drawing = selection(); label(g, drawing == null ? MapTexts.text("manager_select") : name(drawing), panelX + 12, actionTop, panelWidth - 24);
            label(g, MapTexts.text("page", listPage + 1, maxPage() + 1).copy().append(" · " + filtered.size()), panelX + 44, footer + 7, panelWidth - 88);
        } else if (tab == Tab.TOOLS && !compact) label(g, MapTexts.text("drawing_hint"), panelX + 12, contentTop + 182, panelWidth - 24);
    }

    private final class DrawingRow extends TransparentButton {
        private final MapDrawing drawing;
        DrawingRow(MapDrawing drawing, int y) {
            super(panelX + 12, y, panelWidth - 24, rowHeight - 4, name(drawing), () -> { selected = drawing.id(); refreshSelection(); }); this.drawing = drawing;
            setTooltip(Tooltip.create(name(drawing).copy().append("\n").append(MapTexts.dimension(drawing.dimension()))));
        }
        @Override protected void extractWidgetRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
            var style = drawing.id().equals(selected) ? YzuiTheme.ButtonStyle.FILLED : YzuiTheme.ButtonStyle.TONAL;
            YzuiTheme.button(g, getX(), getY(), getWidth(), getHeight(), isMouseOver(mx, my) ? 1 : 0, isFocused(), true, 1, style);
            int textColor = YzuiTheme.buttonText(style, true), size = getHeight() - 8;
            preview(g, drawing, getX() + 4, getY() + 4, size, size);
            YzuiTheme.label(g, font, name(drawing), getX() + size + 12, getY() + 5, getWidth() - size - 20, textColor, false);
            var first = drawing.vertices().getFirst();
            YzuiTheme.label(g, font, MapTexts.dimension(drawing.dimension()).copy().append(" · " + (int) first.x() + ", " + (int) first.z()), getX() + size + 12, getY() + 20, getWidth() - size - 20, textColor, false);
        }
    }

    private static void preview(GuiGraphicsExtractor g, MapDrawing drawing, int x, int y, int width, int height) {
        if (drawing.kind() == MapDrawing.Kind.LABEL) { toolIcon(g, YzWorldMapScreen.Tool.LABEL, x + (width - 20) / 2, y + (height - 20) / 2, 20, drawing.color()); return; }
        var points = MapShapes.path(drawing);
        double minX = points.stream().mapToDouble(MapVertex::x).min().orElse(0), maxX = points.stream().mapToDouble(MapVertex::x).max().orElse(0);
        double minZ = points.stream().mapToDouble(MapVertex::z).min().orElse(0), maxZ = points.stream().mapToDouble(MapVertex::z).max().orElse(0);
        double scale = Math.min((width - 8) / Math.max(1, maxX - minX), (height - 8) / Math.max(1, maxZ - minZ));
        double left = x + width / 2.0 - (minX + maxX) / 2 * scale, top = y + height / 2.0 - (minZ + maxZ) / 2 * scale;
        for (int i = 1; i < points.size(); i++) MapShapes.rawLine(g, left + points.get(i - 1).x() * scale, top + points.get(i - 1).z() * scale, left + points.get(i).x() * scale, top + points.get(i).z() * scale, drawing.color(), 2);
    }
    private static void toolIcon(GuiGraphicsExtractor g, YzWorldMapScreen.Tool tool, int x, int y, int size, int color) {
        if (tool == YzWorldMapScreen.Tool.RECTANGLE) { g.outline(x + 1, y + 2, size - 2, size - 4, color); return; }
        if (tool == YzWorldMapScreen.Tool.ELLIPSE) {
            for (int i = 0; i < 24; i++) { double a = Math.PI * i / 12, b = Math.PI * (i + 1) / 12; MapShapes.rawLine(g, x + size / 2.0 + Math.cos(a) * (size / 2.0 - 1), y + size / 2.0 + Math.sin(a) * (size / 2.0 - 2), x + size / 2.0 + Math.cos(b) * (size / 2.0 - 1), y + size / 2.0 + Math.sin(b) * (size / 2.0 - 2), color, 1); } return;
        }
        if (tool == YzWorldMapScreen.Tool.LABEL) { g.fill(x + 1, y + 2, x + size - 1, y + 4, color); g.fill(x + size / 2 - 1, y + 3, x + size / 2 + 1, y + size - 2, color); return; }
        if (tool == YzWorldMapScreen.Tool.SELECT) {
            MapShapes.rawLine(g, x + 2, y + 1, x + 4, y + size - 2, color, 2); MapShapes.rawLine(g, x + 2, y + 1, x + size - 2, y + size / 2.0, color, 2); MapShapes.rawLine(g, x + 4, y + size - 2, x + size - 2, y + size / 2.0, color, 2); return;
        }
        MapShapes.rawLine(g, x + 2, y + size - 2, x + size - 2, y + 2, color, 2);
        if (tool == YzWorldMapScreen.Tool.PEN) MapShapes.rawLine(g, x + 2, y + size - 2, x + size / 2.0, y + size - 2, color, 2);
    }
    @Override public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        if (!isMouseOver(x, y)) return false;
        if (page == Page.MAIN && tab == Tab.LIBRARY && y >= listTop && y < listBottom && vertical != 0) { listPage += vertical > 0 ? -1 : 1; refreshRows(); }
        return true;
    }
}
