package top.csituka.youzaiworldcore.client.screen.map;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.events.AbstractContainerEventHandler;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import org.lwjgl.glfw.GLFW;
import top.csituka.youzaiworldcore.client.effect.TeleportFovEffect;
import top.csituka.youzaiworldcore.client.map.*;
import top.csituka.youzaiworldcore.client.render.YzuiTheme;
import top.csituka.youzaiworldcore.client.screen.widget.TransparentButton;
import top.csituka.youzaiworldcore.map.MapWaypoint;
import top.csituka.youzaiworldcore.network.*;
import top.csituka.youzaiworldcore.util.DebugLogger;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** 右侧半屏覆盖层：按服务端维度池切图、新建标记及标记详情，底图继续实时绘制。 */
@SuppressWarnings("null")
final class MapDetailPanel extends AbstractContainerEventHandler implements Renderable, NarratableEntry {
    private final YzWorldMapScreen owner;
    private final boolean dimensions;
    private final boolean anchor;
    private boolean creating, editing, pending, confirmingDelete, editingNote, shared;
    private MapWaypoint point;
    private String name, group, note, newCategory = "";
    private Component message = Component.empty();
    private final List<AbstractWidget> widgets = new ArrayList<>();
    private final List<AbstractWidget> body = new ArrayList<>();
    private final List<Line> lines = new ArrayList<>();
    private int x, y, width, height, top, bottom, scroll, contentHeight;
    private record Line(Component text, int x, int y, int width) { }

    MapDetailPanel(YzWorldMapScreen owner) {
        this.owner = owner; dimensions = true; anchor = false;
    }
    MapDetailPanel(YzWorldMapScreen owner, MapWaypoint point, boolean creating) {
        this.owner = owner; this.point = point; this.creating = creating; shared = point.shared(); dimensions = false;
        anchor = MapClient.isAnchor(point); editing = creating; editingNote = creating;
        name = MapTexts.waypoint(point).getString(); group = point.group(); note = MapPersonalData.note(point.id());
    }

    private void close() { if (owner.isDetails(this)) owner.closeDetails(); }

    void layout(int screenWidth, int screenHeight) {
        width = Math.min(screenWidth - 16, Math.max(180, screenWidth / 2));
        x = screenWidth - width - 8; y = 8; height = screenHeight - 16;
        top = y + 38; bottom = y + height - 62; rebuild();
    }

    private TransparentButton button(int bx, int by, int bw, Component label, Runnable action, boolean inBody) {
        var widget = new TransparentButton(bx, by, Math.max(16, bw), 22, label, action);
        widget.setTooltip(Tooltip.create(label)); widgets.add(widget); if (inBody) body.add(widget); return widget;
    }
    private EditBox field(int bx, int by, int bw, String key, String value, int limit, java.util.function.Consumer<String> setter) {
        var field = new EditBox(Minecraft.getInstance().font, bx, by, Math.max(20, bw), 20, MapTexts.text(key));
        field.setMaxLength(limit); field.setValue(value); field.setResponder(setter);
        widgets.add(field); body.add(field); return field;
    }
    private void line(Component label, int row) { lines.add(new Line(label, x + 12, top + row - scroll, width - 24)); }
    private void rebuild() {
        setFocused(null); widgets.clear(); body.clear(); lines.clear();
        button(x + width - 32, y + 7, 22, Component.literal("×"), this::close, false);
        if (dimensions) buildDimensions(); else buildPoint();
        int maxScroll = Math.max(0, contentHeight - (bottom - top));
        if (scroll > maxScroll) { scroll = maxScroll; rebuild(); return; }
        // 部分露出的控件也不参与命中，防止点击越过滚动区域。
        for (var widget : body) widget.visible = widget.getY() >= top && widget.getY() + widget.getHeight() <= bottom;
    }
    private void buildDimensions() {
        Map<String, List<String>> groups = new LinkedHashMap<>();
        Map<String, String> names = new LinkedHashMap<>();
        var session = MapClient.session();
        for (String dimension : MapClient.dimensions()) {
            var metadata = session == null ? null : session.dimensions().stream().filter(d -> d.id().equals(dimension)).findFirst().orElse(null);
            String id = metadata == null ? "" : metadata.poolId();
            groups.computeIfAbsent(id, unused -> new ArrayList<>()).add(dimension);
            names.put(id, metadata == null ? "" : metadata.poolName());
        }
        int row = 0, cell = (width - 30) / 2;
        for (var entry : groups.entrySet()) {
            line(entry.getKey().isEmpty() ? MapTexts.text("other_dimensions") : Component.literal(names.get(entry.getKey())), row + 3);
            row += 23;
            for (int i = 0; i < entry.getValue().size(); i++) {
                String dimension = entry.getValue().get(i);
                boolean current = dimension.equals(MapClient.dimension());
                Component label = MapTexts.dimension(dimension).copy().append(current ? "  ◆" : "");
                var button = button(x + 12 + i % 2 * (cell + 6), top + row + i / 2 * 32 - scroll, cell, label,
                        () -> { owner.switchDimension(dimension); close(); }, true);
                if (dimension.equals(owner.mapDimension())) button.setStyle(YzuiTheme.ButtonStyle.FILLED);
            }
            row += (entry.getValue().size() + 1) / 2 * 32 + 12;
        }
        contentHeight = row;
        button(x + 12, y + height - 32, width - 24, MapTexts.text("done"), this::close, false);
    }
    private boolean editable() {
        if (anchor || !point.shared()) return true;
        var session = MapClient.session(); var player = Minecraft.getInstance().player;
        return session != null && (session.allows(MapSessionPayload.MANAGE) || session.allows(MapSessionPayload.PUBLISH)
                && player != null && point.owner().equals(player.getUUID()) && point.kind() == MapWaypoint.Kind.NORMAL);
    }
    private void buildPoint() {
        int row = 0, inner = width - 24;
        if (editing) {
            line(MapTexts.text("name"), row); row += 15;
            field(x + 12, top + row - scroll, inner, "name", name, 64, value -> name = value).setEditable(editable() && !pending);
            row += 28;
        } else {
            button(x + 12, top + row - scroll, inner - (anchor ? 52 : 78), MapTexts.waypoint(point).copy().append("  ✎"),
                    () -> { editing = true; rebuild(); }, true).active = editable() && !pending;
            button(x + width - 58, top + row - scroll, 46, MapTexts.text(confirmingDelete ? "confirm_delete" : "delete"), this::delete, true).active = editable() && !pending;
            if (!anchor) button(x + width - 84, top + row - scroll, 22, Component.literal("↪"), () -> MapTransfer.share(point), true)
                    .setTooltip(Tooltip.create(MapTexts.text("share_chat")));
            row += 29;
        }
        lines.add(new Line(Component.literal("X " + point.x() + "   Y " + point.y() + "   Z " + point.z()), x + 12, top + row + 6 - scroll, inner - 52));
        button(x + width - 58, top + row - scroll, 46, MapTexts.text("copy"), () -> {
            Minecraft.getInstance().keyboardHandler.setClipboard(MapTransfer.coordinate(point)); message = MapTexts.text("copied");
        }, true); row += 29;
        line(MapTexts.dimension(point.dimension()), row); row += 20;
        if (!creating) {
            String date = point.createdAt() <= 0 ? "—" : DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(point.createdAt()));
            line(MapTexts.text("created_at").copy().append("  " + date), row); row += 24;
        }
        if (!anchor && !editing) {
            button(x + 12, top + row - scroll, inner, MapTexts.text("group").copy().append(" · " + group + "  ✎"),
                    () -> { editing = true; rebuild(); }, true).active = editable(); row += 28;
        }
        if (!anchor && editing) {
            line(MapTexts.text("group"), row); row += 15;
            field(x + 12, top + row - scroll, inner, "group", group, 32, value -> group = value).setEditable(!pending); row += 27;
            var categories = MapPersonalData.categories(point.dimension());
            int cell = (inner - 8) / 3;
            for (int i = 0; i < categories.size(); i++) {
                String category = categories.get(i);
                button(x + 12 + i % 3 * (cell + 4), top + row + i / 3 * 26 - scroll, cell, Component.literal(category),
                        () -> { group = category; rebuild(); }, true).setStyle(category.equals(group) ? YzuiTheme.ButtonStyle.FILLED : YzuiTheme.ButtonStyle.TONAL);
            }
            row += (categories.size() + 2) / 3 * 26;
            field(x + 12, top + row - scroll, inner - 30, "new_category", newCategory, 32, value -> newCategory = value);
            button(x + width - 36, top + row - scroll, 24, Component.literal("+"), () -> {
                if (MapPersonalData.addCategory(point.dimension(), newCategory)) { group = newCategory.strip(); newCategory = ""; rebuild(); }
                else message = MapTexts.text("limit");
            }, true); row += 30;
        }
        if (creating) {
            button(x + 12, top + row - scroll, inner, MapTexts.text(shared ? "shared_point" : "private_point"),
                    () -> { shared = !shared; rebuild(); }, true).active = !pending && (shared || MapClient.session() != null
                    && MapClient.session().allows(MapSessionPayload.PUBLISH)); row += 28;
        }
        line(MapTexts.text("private_description"), row + 6);
        button(x + width - 36, top + row - scroll, 24, Component.literal(editingNote ? "✓" : "✎"), () -> {
            if (editingNote && !creating && !MapPersonalData.note(point.id(), note)) { message = MapTexts.text("limit"); return; }
            editingNote = !editingNote; rebuild();
        }, true).active = !pending;
        row += 28;
        int noteHeight = Math.min(140, Math.max(36, (bottom - top) / 2));
        if (editingNote) {
            var description = MultiLineEditBox.builder().setX(x + 12).setY(top + row - scroll)
                    .setPlaceholder(MapTexts.text("private_description"))
                    .build(Minecraft.getInstance().font, inner, noteHeight, MapTexts.text("private_description"));
            description.setCharacterLimit(1024); description.setValue(note); description.setValueListener(value -> note = value);
            description.active = !pending; widgets.add(description); body.add(description); row += noteHeight + 6;
        } else {
            for (String part : note.split("\\n", -1)) {
                for (var text : Minecraft.getInstance().font.split(Component.literal(part), inner)) {
                    var plain = new StringBuilder();
                    text.accept((index, style, codepoint) -> { plain.appendCodePoint(codepoint); return true; });
                    line(Component.literal(plain.toString()), row); row += 12;
                }
                row += 4;
            }
            row = Math.max(row, noteHeight);
        }
        if (anchor) {
            row += 14;
            var player = Minecraft.getInstance().player;
            int cost = player != null && player.getAbilities().instabuild ? 0 : point.dimension().equals(MapClient.dimension()) ? 1 : 2;
            line(Component.translatable("screen.youzaiworldcore.teleport_anchor.cost_xp", cost), row); row += 20;
            line(MapTexts.text("anchor_teleport_hint"), row); row += 24;
            button(x + 12, top + row - scroll, inner, MapTexts.text("save_as_waypoint"), this::copyAsWaypoint, true); row += 28;
        }
        contentHeight = row;
        int footer = y + height - 32, half = (inner - 6) / 2;
        if (editing) {
            button(x + 12, footer, half, MapTexts.text(creating ? "create" : "save"), () -> save(false), false).active = editable() && !pending;
            button(x + 18 + half, footer, half, MapTexts.text(creating ? "create_track" : "cancel"), () -> {
                if (creating) save(true); else { editing = false; name = MapTexts.waypoint(point).getString(); group = point.group(); rebuild(); }
            }, false).active = !pending;
        } else {
            if (anchor) button(x + 12, footer, half, MapTexts.text("teleport"), this::teleport, false).active = !pending && (anchor || MapClient.session() != null && MapClient.session().allows(MapSessionPayload.TELEPORT));
            button(anchor ? x + 18 + half : x + 12, footer, anchor ? half : inner, MapTexts.text(point.id().equals(MapClient.navigation()) ? "stop_navigation" : "navigate"), () -> {
                MapClient.navigate(point.id().equals(MapClient.navigation()) ? null : point.id()); close();
            }, false);
        }
    }
    private MapWaypoint changed() {
        return new MapWaypoint(point.id(), point.owner(), name, group, point.dimension(), point.x(), point.y(), point.z(),
                point.color(), point.enabled(), shared, point.kind(), point.createdAt());
    }
    private void save(boolean track) {
        try {
            MapWaypoint value = changed();
            if (anchor) {
                ClientPlayNetworking.send(new TeleportAnchorRenamePayload(new BlockPos(point.x(), point.y(), point.z()),
                        ResourceKey.create(Registries.DIMENSION, Identifier.parse(point.dimension())), value.name()));
                finish(value, track); message = MapTexts.text("request_sent"); return;
            }
            if (!value.shared()) {
                if (!MapPersonalData.put(value)) { message = MapTexts.text("limit"); return; }
                finish(value, track); return;
            }
            pending = MapClient.send(creating ? MapActionPayload.Action.ADD : MapActionPayload.Action.UPDATE, value, (ok, key) -> {
                pending = false; message = MapTexts.text(key); if (ok) finish(value, track); else rebuild();
            }); rebuild();
        } catch (IllegalArgumentException invalid) { message = MapTexts.text("invalid_point"); }
    }
    private void finish(MapWaypoint value, boolean track) {
        point = value; creating = false; editing = false;
        message = MapTexts.text(MapPersonalData.note(point.id(), note) ? "saved" : "limit");
        if (track && owner.isDetails(this)) { MapClient.navigate(point.id()); close(); } else rebuild();
        DebugLogger.info("WorldMap", "保存地图侧栏标记：%s", point.id());
    }
    private void delete() {
        if (!confirmingDelete) { confirmingDelete = true; rebuild(); return; }
        if (anchor) {
            ClientPlayNetworking.send(new TeleportAnchorDeletePayload(new BlockPos(point.x(), point.y(), point.z()),
                    ResourceKey.create(Registries.DIMENSION, Identifier.parse(point.dimension())))); close();
        } else if (!point.shared()) { MapPersonalData.remove(point.id()); close(); }
        else {
            pending = MapClient.send(MapActionPayload.Action.DELETE, point, (ok, key) -> {
                pending = false; message = MapTexts.text(key); if (ok) close(); else rebuild();
            }); rebuild();
        }
    }
    private void copyAsWaypoint() {
        var copy = new MapWaypoint(UUID.randomUUID(), point.owner(), name, "", point.dimension(), point.x(), point.y(), point.z(),
                point.color(), true, false, MapWaypoint.Kind.NORMAL, System.currentTimeMillis());
        owner.openPoint(copy, true);
    }
    private void teleport() {
        if (anchor) {
            TeleportFovEffect.startTeleport(new BlockPos(point.x(), point.y(), point.z()),
                    ResourceKey.create(Registries.DIMENSION, Identifier.parse(point.dimension())));
            owner.onClose();
        } else {
            pending = MapClient.send(MapActionPayload.Action.TELEPORT, point, (ok, key) -> {
                pending = false; message = MapTexts.text(key); if (ok) close(); else rebuild();
            }); rebuild();
        }
    }
    @Override public List<? extends GuiEventListener> children() { return widgets.stream().filter(w -> w.visible).toList(); }
    @Override public boolean isMouseOver(double mx, double my) { return mx >= x && mx < x + width && my >= y && my < y + height; }
    boolean covers(AbstractWidget widget) { return widget.getX() < x + width && widget.getX() + widget.getWidth() > x && widget.getY() < y + height && widget.getY() + widget.getHeight() > y; }
    @Override public ScreenRectangle getRectangle() { return new ScreenRectangle(x, y, width, height); }
    @Override public NarrationPriority narrationPriority() { return getFocused() == null ? NarrationPriority.NONE : NarrationPriority.FOCUSED; }
    @Override public void updateNarration(NarrationElementOutput output) {
        output.add(NarratedElementType.TITLE, MapTexts.text(dimensions ? "dimension" : creating ? "new_point" : "point_actions"));
        if (getFocused() instanceof NarratableEntry entry) entry.updateNarration(output.nest());
    }
    @Override public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
        g.nextStratum(); YzuiTheme.card(g, x, y, width, height);
        YzuiTheme.label(g, Minecraft.getInstance().font, MapTexts.text(dimensions ? "dimension" : creating ? "new_point" : "point_actions"), x + 12, y + 14, width - 48, YzuiTheme.text(), false);
        g.fill(x + 12, top - 7, x + width - 12, top - 6, YzuiTheme.outlineVariant());
        g.enableScissor(x, top, x + width, bottom);
        for (var line : lines) YzuiTheme.label(g, Minecraft.getInstance().font, line.text, line.x, line.y, line.width, YzuiTheme.text(), false);
        for (var widget : body) if (widget.visible) widget.extractRenderState(g, mx, my, delta);
        g.disableScissor();
        for (var widget : widgets) if (!body.contains(widget)) widget.extractRenderState(g, mx, my, delta);
        YzuiTheme.label(g, Minecraft.getInstance().font, message, x + 12, y + height - 50, width - 24, YzuiTheme.textMuted(), false);
        if (contentHeight > bottom - top) {
            int track = bottom - top, thumb = Math.max(10, track * track / contentHeight);
            int sy = top + (track - thumb) * scroll / Math.max(1, contentHeight - track);
            g.fill(x + width - 5, sy, x + width - 3, sy + thumb, YzuiTheme.primary());
        }
    }
    @Override public boolean mouseClicked(MouseButtonEvent event, boolean actual) {
        if (!isMouseOver(event.x(), event.y())) return false;
        super.mouseClicked(event, actual); return true;
    }
    @Override public boolean mouseScrolled(double mx, double my, double horizontal, double vertical) {
        if (!isMouseOver(mx, my)) return false;
        for (var widget : body) if (widget.visible && widget instanceof MultiLineEditBox && widget.isMouseOver(mx, my)
                && widget.mouseScrolled(mx, my, horizontal, vertical)) return true;
        scroll = Math.clamp(scroll - (int) (vertical * 24), 0, Math.max(0, contentHeight - (bottom - top))); rebuild(); return true;
    }
    @Override public boolean keyPressed(KeyEvent event) {
        if (event.key() == GLFW.GLFW_KEY_ESCAPE) { close(); return true; }
        return super.keyPressed(event);
    }
}
