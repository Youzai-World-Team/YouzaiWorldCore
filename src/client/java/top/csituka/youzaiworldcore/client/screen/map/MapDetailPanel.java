package top.csituka.youzaiworldcore.client.screen.map;

import com.mojang.blaze3d.platform.InputConstants;
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
import top.csituka.youzaiworldcore.client.effect.TeleportFovEffect;
import top.csituka.youzaiworldcore.client.map.*;
import top.csituka.youzaiworldcore.client.render.MenuIcon;
import top.csituka.youzaiworldcore.client.render.RoundedRect;
import top.csituka.youzaiworldcore.client.render.YzuiTheme;
import top.csituka.youzaiworldcore.client.screen.widget.TransparentButton;
import top.csituka.youzaiworldcore.client.screen.widget.YzuiCloseButton;
import top.csituka.youzaiworldcore.map.MapWaypoint;
import top.csituka.youzaiworldcore.map.MapWaypointGroup;
import top.csituka.youzaiworldcore.network.*;
import top.csituka.youzaiworldcore.util.DebugLogger;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** 地图覆盖层：新建路径点使用居中小弹窗，维度选择和已有路径点详情使用右侧面板。 */
@SuppressWarnings("null")
final class MapDetailPanel extends AbstractContainerEventHandler implements Renderable, NarratableEntry {
    private final YzWorldMapScreen owner;
    private final boolean dimensions;
    private final boolean anchor;
    private final boolean dialog;
    private boolean creating, editing, pending, confirmingDelete, editingNote;
    private MapWaypoint point;
    private String name, note, color;
    private MapWaypointGroup group;
    private Component message = Component.empty();
    private boolean messageError;
    private final List<AbstractWidget> widgets = new ArrayList<>();
    private final List<AbstractWidget> body = new ArrayList<>();
    private final List<Line> lines = new ArrayList<>();
    private int x, y, width, height, top, bottom, scroll, contentHeight;
    private int pointLocationY = -1;
    private int creationLocationY = -1;
    private EditBox creationName;
    private TransparentButton createButton, createTrackButton;
    private TransparentButton navigationButton;
    private UUID navigationSnapshot;
    private record Line(Component text, int x, int y, int width) { }

    MapDetailPanel(YzWorldMapScreen owner) {
        this.owner = owner; dimensions = true; anchor = false; dialog = false;
    }
    MapDetailPanel(YzWorldMapScreen owner, MapWaypoint point, boolean creating) {
        this.owner = owner; this.point = point; this.creating = creating; dimensions = false;
        anchor = MapClient.isAnchor(point); dialog = creating; editing = creating; editingNote = creating;
        name = MapTexts.waypoint(point).getString(); group = MapPersonalData.groupOf(point); note = MapPersonalData.note(point.id());
        color = MapWaypointColors.hex(point.color());
    }

    private void close() { if (owner.isDetails(this)) owner.closeDetails(); }

    void layout(int screenWidth, int screenHeight) {
        if (dialog) {
            width = Math.min(360, screenWidth - 32); height = Math.min(320, screenHeight - 32);
            x = (screenWidth - width) / 2; y = (screenHeight - height) / 2;
            DebugLogger.CLIENT.debug("WorldMap", "新建路径点弹窗布局：{}x{}，位置={},{}", width, height, x, y);
        } else {
            width = Math.min(screenWidth - 16, Math.max(180, screenWidth / 2));
            x = screenWidth - width - 8; y = 8; height = screenHeight - 16;
        }
        top = y + (dialog && creating ? 48 : dimensions ? 38 : 62);
        bottom = y + height - (dialog && creating ? 60 : 62);
        rebuild();
    }

    private TransparentButton button(int bx, int by, int bw, Component label, Runnable action, boolean inBody) {
        return button(bx, by, bw, 22, label, action, inBody);
    }
    private TransparentButton button(int bx, int by, int bw, int bh, Component label, Runnable action, boolean inBody) {
        var widget = new TransparentButton(bx, by, Math.max(16, bw), Math.max(16, bh), label, action);
        widget.setTooltip(Tooltip.create(label)); widgets.add(widget); if (inBody) body.add(widget); return widget;
    }
    private EditBox field(int bx, int by, int bw, String key, String value, int limit, java.util.function.Consumer<String> setter) {
        var field = new EditBox(Minecraft.getInstance().font, bx, by, Math.max(20, bw), 20, MapTexts.text(key));
        field.setMaxLength(limit); field.setValue(value); field.setResponder(setter);
        widgets.add(field); body.add(field); return field;
    }
    private void line(Component label, int row) { lines.add(new Line(label, x + 12, top + row - scroll, width - 24)); }
    private void rebuild() {
        int focusedIndex = widgets.indexOf(getFocused());
        int cursor = creationName == null ? -1 : creationName.getCursorPosition();
        setFocused(null); widgets.clear(); body.clear(); lines.clear();
        pointLocationY = -1;
        creationLocationY = -1; creationName = null; createButton = null; createTrackButton = null;
        navigationButton = null; navigationSnapshot = MapClient.navigation();
        if (dialog && creating) {
            widgets.add(new YzuiCloseButton(x + width - 40, y + 12, 24, MapTexts.text("cancel"), this::close));
            buildCreation();
        } else {
            widgets.add(new YzuiCloseButton(x + width - 32, y + 7, 22, this::close));
            if (dimensions) buildDimensions(); else buildPoint();
        }
        int maxScroll = Math.max(0, contentHeight - (bottom - top));
        if (scroll > maxScroll) { scroll = maxScroll; rebuild(); return; }
        // 部分露出的控件也不参与命中，防止点击越过滚动区域。
        updateBodyVisibility();
        if (creationName != null) {
            if (cursor >= 0) creationName.setCursorPosition(Math.min(cursor, name.length()));
            else creationName.setHighlightPos(0);
            var focus = focusedIndex >= 0 && focusedIndex < widgets.size() ? widgets.get(focusedIndex) : creationName;
            if (focus.visible) setFocused(focus);
        }
    }

    // ===== 新建路径点弹窗 =====

    /** 独立表单布局，位置只读；颜色、分组及描述继续使用原有草稿与保存逻辑。 */
    private void buildCreation() {
        int left = x + 16, inner = width - 32, row = 0;
        lines.add(new Line(MapTexts.text("name"), left, top - scroll, inner - 44));
        creationName = new EditBox(Minecraft.getInstance().font, left, top + 12 - scroll, inner, 26, MapTexts.text("name"));
        creationName.setMaxLength(64);
        creationName.setValue(name);
        creationName.setResponder(value -> {
            name = value;
            updateCreationActions();
        });
        creationName.setEditable(!pending);
        widgets.add(creationName); body.add(creationName);
        row += 44;

        creationLocationY = top + row - scroll;
        var copy = button(left + inner - 30, creationLocationY + 4, 24, MapTexts.text("copy"), () -> {
            Minecraft.getInstance().keyboardHandler.setClipboard(MapCoordinates.coordinate(point));
            message = MapTexts.text("copied"); messageError = false;
        }, true);
        copy.setHeight(24);
        copy.setStyle(YzuiTheme.ButtonStyle.TEXT).setLineIcon(MenuIcon.COPY);
        row += 56;

        int gap = 10, colorWidth = Math.min(132, (inner - gap) / 2), groupX = left + colorWidth + gap;
        lines.add(new Line(MapTexts.text("color"), left, top + row - scroll, colorWidth));
        lines.add(new Line(MapTexts.text("group"), groupX, top + row - scroll, inner - colorWidth - gap));
        var colorChoice = new ColorChoiceButton(left, top + row + 12 - scroll, colorWidth, 26, false);
        colorChoice.active = !pending;
        widgets.add(colorChoice); body.add(colorChoice);
        MapGroupButton.selector(groupX, top + row + 12 - scroll, inner - colorWidth - gap, () -> group,
                value -> { group = value; rebuild(); }, choice -> {
                    choice.setHeight(26);
                    widgets.add(choice); body.add(choice);
                }, !pending && point.kind() != MapWaypoint.Kind.DEATH);
        row += 46;

        lines.add(new Line(MapTexts.text("private_description"), left, top + row - scroll, inner));
        var description = MultiLineEditBox.builder().setX(left).setY(top + row + 12 - scroll)
                .setPlaceholder(MapTexts.text("private_description"))
                .build(Minecraft.getInstance().font, inner, 48, MapTexts.text("private_description"));
        description.setCharacterLimit(1024); description.setValue(note);
        description.setValueListener(value -> note = value);
        description.active = !pending;
        widgets.add(description); body.add(description);
        contentHeight = row + 64;

        int half = (inner - 8) / 2, footer = y + height - 38;
        createTrackButton = button(left, footer, half, MapTexts.text("create_track"), () -> save(true), false);
        createTrackButton.setHeight(26);
        createTrackButton.setLeadingLineIcon(MenuIcon.ROUTE);
        createButton = button(left + half + 8, footer, inner - half - 8, MapTexts.text("create"), () -> save(false), false);
        createButton.setHeight(26);
        createButton.setStyle(YzuiTheme.ButtonStyle.FILLED).setLeadingLineIcon(MenuIcon.PLUS);
        updateCreationActions();
    }

    private void updateCreationActions() {
        boolean valid = !pending && name != null && !name.isBlank();
        if (createButton != null) createButton.active = valid;
        if (createTrackButton != null) createTrackButton.active = valid;
    }

    /** 色块与编辑入口合为同一个命中区域，显示颜色值并保留完整提示。 */
    private final class ColorChoiceButton extends TransparentButton {
        ColorChoiceButton(int bx, int by, int bw, int bh, boolean markEditing) {
            super(bx, by, bw, bh, Component.literal("#" + color), () -> {
                int initial = previewColor();
                Minecraft.getInstance().gui.setScreen(new MapWaypointColorScreen(owner, initial, selected -> {
                    color = MapWaypointColors.hex(selected);
                    if (markEditing) editing = true;
                    rebuild();
                }));
            });
            setTextLeftAligned(true);
            setTextInsets(30, 6);
            setTrailingLineIcon(MenuIcon.CHEVRON_RIGHT);
            setTooltip(Tooltip.create(MapTexts.text("color").copy().append("  #" + color)));
        }

        @Override protected void extractWidgetRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
            super.extractWidgetRenderState(g, mx, my, delta);
            MapWaypointColors.render(g, previewColor(), getX() + 16, getY() + getHeight() / 2,
                    getAlpha() * externalAlpha());
        }
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
        return anchor || !point.shared();
    }

    /** 当前颜色的安全预览；草稿非法时回退到已保存颜色。 */
    private int previewColor() {
        try {
            return MapWaypointColors.parse(color);
        } catch (IllegalArgumentException ignored) {
            return point.color();
        }
    }

    private void copyCoordinate() {
        Minecraft.getInstance().keyboardHandler.setClipboard(MapCoordinates.coordinate(point));
        message = MapTexts.text("copied");
        messageError = false;
    }

    private void createNoteField(int bx, int by, int bw, int bh) {
        var description = MultiLineEditBox.builder().setX(bx).setY(by)
                .setPlaceholder(MapTexts.text("private_description"))
                .build(Minecraft.getInstance().font, bw, bh, MapTexts.text("private_description"));
        description.setCharacterLimit(1024);
        description.setValue(note);
        description.setValueListener(value -> note = value);
        description.active = !pending;
        widgets.add(description); body.add(description);
    }

    /** 详情标题：颜色圆点、名称与「维度 · 类型」，编辑态直接反映草稿名称。 */
    private void renderPointHeader(GuiGraphicsExtractor g) {
        var font = Minecraft.getInstance().font;
        int avatar = 34;
        RoundedRect.fillRound(g, x + 12, y + 10, avatar, avatar, 10, YzuiTheme.primaryContainer());
        MapWaypointColors.render(g, previewColor(), x + 12 + avatar / 2, y + 10 + avatar / 2, 1.0F);
        Component displayName = Component.literal(editing && name != null && !name.isBlank() ? name : point.name());
        YzuiTheme.label(g, font, displayName, x + 12 + avatar + 10, y + 12, width - avatar - 62,
                YzuiTheme.text(), false);
        Component subtitle = (editing ? MapTexts.text("edit_point")
                : anchor ? Component.translatable("advancements.youzaiworldcore.youzaiworld.discover_teleport_anchor.title")
                : MapTexts.text("private_point")).copy().append(" · ")
                .append(MapTexts.dimension(point.dimension()));
        YzuiTheme.label(g, font, subtitle, x + 12 + avatar + 10, y + 28, width - avatar - 62,
                YzuiTheme.textMuted(), false);
        g.fill(x + 12, top - 7, x + width - 12, top - 6, YzuiTheme.outlineVariant());
    }

    /** 只读位置卡片：维度与 XYZ 分栏，复制和分享作为卡片内图标按钮。 */
    private void renderPointLocation(GuiGraphicsExtractor g) {
        if (pointLocationY < 0) return;
        int left = x + 12, inner = width - 24;
        var font = Minecraft.getInstance().font;
        RoundedRect.fill(g, left, pointLocationY, inner, 52, 8, YzuiTheme.surfaceLow());
        MenuIcon.WORLD.render(g, left + 10, pointLocationY + 8, 12, YzuiTheme.textMuted());
        YzuiTheme.label(g, font, MapTexts.dimension(point.dimension()), left + 28, pointLocationY + 10,
                inner - 92, YzuiTheme.textMuted(), false);
        int cell = (inner - 20) / 3;
        String[] axes = { "X", "Y", "Z" };
        int[] coordinates = { point.x(), point.y(), point.z() };
        for (int index = 0; index < axes.length; index++) {
            int column = left + 10 + index * cell;
            if (index > 0) g.fill(column - 6, pointLocationY + 30, column - 5, pointLocationY + 41,
                    YzuiTheme.alpha(YzuiTheme.outlineVariant(), 0.5F));
            YzuiTheme.label(g, font, Component.literal(axes[index]), column, pointLocationY + 32, 10,
                    YzuiTheme.textMuted(), false);
            YzuiTheme.label(g, font, Component.literal(Integer.toString(coordinates[index])), column + 14,
                    pointLocationY + 32, cell - 22, YzuiTheme.text(), false);
        }
    }

    private void buildPoint() {
        int left = x + 12, inner = width - 24, row = 0;
        if (editing) {
            lines.add(new Line(MapTexts.text("name"), left, top + row - scroll, inner));
            row += 15;
            field(left, top + row - scroll, inner, "name", name, 64, value -> name = value)
                    .setEditable(editable() && !pending);
            row += 31;
        }

        // ===== 只读位置卡片 =====
        pointLocationY = top + row - scroll;
        int icon = 22;
        var copy = button(x + width - 12 - icon, pointLocationY + 4, icon, MapTexts.text("copy"),
                this::copyCoordinate, true);
        copy.setHeight(icon);
        copy.setStyle(YzuiTheme.ButtonStyle.TEXT).setLineIcon(MenuIcon.COPY);
        if (!anchor) {
            var share = button(x + width - 12 - icon * 2 - 4, pointLocationY + 4, icon,
                    MapTexts.text("share_chat"), () -> MapCoordinates.share(point), true);
            share.setHeight(icon);
            share.setStyle(YzuiTheme.ButtonStyle.TEXT).setLineIcon(MenuIcon.SHARE);
            share.active = !pending;
        }
        row += 62;

        // ===== 编辑与删除 =====
        if (!editing) {
            int half = (inner - 6) / 2;
            button(left, top + row - scroll, half, MapTexts.text("edit_point"),
                    () -> { editing = true; rebuild(); }, true)
                    .setStyle(YzuiTheme.ButtonStyle.TONAL).setLeadingLineIcon(MenuIcon.EDIT)
                    .active = editable() && !pending;
            button(left + half + 6, top + row - scroll, half,
                    MapTexts.text(confirmingDelete ? "confirm_delete" : "delete"), this::delete, true)
                    .setStyle(YzuiTheme.ButtonStyle.DANGER).setLeadingLineIcon(MenuIcon.TRASH)
                    .active = editable() && !pending;
            row += 32;
        }

        // ===== 颜色与分组 =====
        if (!anchor) {
            int gap = 8, colorWidth = Math.min(124, (inner - gap) / 2), groupX = left + colorWidth + gap;
            lines.add(new Line(MapTexts.text("color"), left, top + row - scroll, colorWidth));
            lines.add(new Line(MapTexts.text("group"), groupX, top + row - scroll, inner - colorWidth - gap));
            row += 14;
            var colorChoice = new ColorChoiceButton(left, top + row - scroll, colorWidth, 26, editing);
            colorChoice.active = editable() && !pending;
            widgets.add(colorChoice); body.add(colorChoice);
            MapGroupButton.selector(groupX, top + row - scroll, inner - colorWidth - gap, () -> group, value -> {
                group = value; editing = true; rebuild();
            }, choice -> { choice.setHeight(26); widgets.add(choice); body.add(choice); },
                    editable() && !pending && point.kind() != MapWaypoint.Kind.DEATH);
            row += 36;
        }

        // ===== 私人描述 =====
        lines.add(new Line(MapTexts.text("private_description"), left, top + row + 7 - scroll, inner - 36));
        if (!editing) {
            var toggle = button(x + width - 12 - 24, top + row - scroll, 24,
                    MapTexts.text(editingNote ? "save" : "private_description"), () -> {
                        if (editingNote && !MapPersonalData.note(point.id(), note)) {
                            message = MapTexts.text("limit"); return;
                        }
                        editingNote = !editingNote; rebuild();
                    }, true);
            toggle.setStyle(YzuiTheme.ButtonStyle.TEXT)
                    .setLineIcon(editingNote ? MenuIcon.CHECK : MenuIcon.EDIT)
                    .active = !pending;
        }
        row += 30;
        int noteHeight = Math.min(120, Math.max(48, (bottom - top) / 3));
        if (editing || editingNote) {
            createNoteField(left, top + row - scroll, inner, noteHeight);
            row += noteHeight + 8;
        } else {
            int noteY = top + row - scroll, cursor = noteY;
            for (String part : note.split("\\n", -1)) {
                for (var text : Minecraft.getInstance().font.split(Component.literal(part), inner - 8)) {
                    var plain = new StringBuilder();
                    text.accept((index, style, codepoint) -> { plain.appendCodePoint(codepoint); return true; });
                    lines.add(new Line(Component.literal(plain.toString()), left + 4, cursor, inner - 8));
                    cursor += 11;
                }
                cursor += 3;
            }
            var area = button(left, noteY, inner, noteHeight, Component.empty(),
                    () -> { editingNote = true; rebuild(); }, true);
            area.setStyle(YzuiTheme.ButtonStyle.TEXT);
            area.setTooltip(Tooltip.create(MapTexts.text("private_description")));
            row += Math.max(noteHeight, cursor - noteY) + 6;
        }

        // ===== 锚点附加信息 =====
        if (anchor) {
            row += 10;
            var player = Minecraft.getInstance().player;
            int cost = player != null && player.getAbilities().instabuild ? 0
                    : point.dimension().equals(MapClient.dimension()) ? 1 : 2;
            line(Component.translatable("screen.youzaiworldcore.teleport_anchor.cost_xp", cost), row); row += 18;
            line(MapTexts.text("anchor_teleport_hint"), row); row += 22;
            button(left, top + row - scroll, inner, MapTexts.text("save_as_waypoint"), this::copyAsWaypoint, true)
                    .setLeadingLineIcon(MenuIcon.PIN);
            row += 28;
        }
        contentHeight = row;

        // ===== 底部主操作 =====
        int footer = y + height - 32, half = (inner - 6) / 2;
        if (editing) {
            button(left, footer, half, MapTexts.text(creating ? "create" : "save"), () -> save(false), false)
                    .setStyle(YzuiTheme.ButtonStyle.FILLED)
                    .setLeadingLineIcon(creating ? MenuIcon.PLUS : MenuIcon.CHECK)
                    .active = editable() && !pending;
            button(left + half + 6, footer, half, MapTexts.text(creating ? "create_track" : "cancel"), () -> {
                if (creating) { save(true); return; }
                editing = false; editingNote = false;
                name = MapTexts.waypoint(point).getString();
                color = MapWaypointColors.hex(point.color());
                group = MapPersonalData.groupOf(point);
                note = MapPersonalData.note(point.id());
                rebuild();
            }, false).setLeadingLineIcon(creating ? MenuIcon.ROUTE : MenuIcon.CLOSE).active = !pending;
        } else {
            if (anchor) {
                button(left, footer, half, MapTexts.text("teleport"), this::teleport, false)
                        .setStyle(YzuiTheme.ButtonStyle.FILLED).setLeadingLineIcon(MenuIcon.TARGET)
                        .active = !pending;
                navigationButton = button(left + half + 6, footer, half,
                        MapTexts.text(point.id().equals(MapClient.navigation()) ? "stop_navigation" : "navigate"),
                        () -> {
                            MapClient.navigate(point.id().equals(MapClient.navigation()) ? null : point.id()); close();
                        }, false)
                        .setLeadingLineIcon(point.id().equals(MapClient.navigation()) ? MenuIcon.STOP : MenuIcon.NAVIGATE);
            } else {
                navigationButton = button(left, footer, inner,
                        MapTexts.text(point.id().equals(MapClient.navigation()) ? "stop_navigation" : "navigate"),
                        () -> {
                            MapClient.navigate(point.id().equals(MapClient.navigation()) ? null : point.id()); close();
                        }, false)
                        .setLeadingLineIcon(point.id().equals(MapClient.navigation()) ? MenuIcon.STOP : MenuIcon.NAVIGATE);
                navigationButton.setStyle(YzuiTheme.ButtonStyle.FILLED);
            }
        }
    }

    /** 玩家到达时导航可在界面外自动取消，详情按钮同步真实状态。 */
    void tick() {
        if (navigationButton == null || Objects.equals(navigationSnapshot, MapClient.navigation())) return;
        navigationSnapshot = MapClient.navigation();
        var label = MapTexts.text(point.id().equals(navigationSnapshot) ? "stop_navigation" : "navigate");
        navigationButton.setMessage(label);
        navigationButton.setTooltip(Tooltip.create(label));
        navigationButton.setLeadingLineIcon(point.id().equals(navigationSnapshot) ? MenuIcon.STOP : MenuIcon.NAVIGATE);
    }
    private MapWaypoint changed() {
        return new MapWaypoint(point.id(), point.owner(), name, point.kind() == MapWaypoint.Kind.DEATH ? MapWaypointGroup.DEATH.id() : group.id(), point.dimension(), point.x(), point.y(), point.z(),
                anchor ? point.color() : MapWaypointColors.parse(color), point.enabled(), anchor, point.kind(), point.createdAt());
    }
    private void save(boolean track) {
        messageError = false;
        try {
            MapWaypoint value = changed();
            if (anchor) {
                ClientPlayNetworking.send(new TeleportAnchorRenamePayload(new BlockPos(point.x(), point.y(), point.z()),
                        ResourceKey.create(Registries.DIMENSION, Identifier.parse(point.dimension())), value.name()));
                finish(value, track); message = MapTexts.text("request_sent"); return;
            }
            if (!MapPersonalData.put(value)) { message = MapTexts.text("limit"); messageError = true; return; }
            finish(value, track);
        } catch (IllegalArgumentException invalid) { message = MapTexts.text("invalid_point"); messageError = true; }
    }
    private void finish(MapWaypoint value, boolean track) {
        point = value; creating = false; editing = false; color = MapWaypointColors.hex(point.color()); group = MapPersonalData.groupOf(point);
        boolean noteSaved = MapPersonalData.note(point.id(), note);
        message = MapTexts.text(noteSaved ? "saved" : "limit");
        messageError = !noteSaved;
        if (track && owner.isDetails(this)) { MapClient.navigate(point.id()); close(); }
        else if (dialog && noteSaved) close();
        else rebuild();
        DebugLogger.CLIENT.debug("WorldMap", "保存地图路径点：{}", point.id());
    }
    private void delete() {
        if (!confirmingDelete) { confirmingDelete = true; rebuild(); return; }
        if (anchor) {
            ClientPlayNetworking.send(new TeleportAnchorDeletePayload(new BlockPos(point.x(), point.y(), point.z()),
                    ResourceKey.create(Registries.DIMENSION, Identifier.parse(point.dimension())))); close();
        } else { MapPersonalData.remove(point.id()); close(); }
    }
    private void copyAsWaypoint() {
        var copy = new MapWaypoint(UUID.randomUUID(), point.owner(), name, MapWaypointGroup.UNGROUPED.id(), point.dimension(), point.x(), point.y(), point.z(),
                MapWaypointColors.nextColor(), true, false, MapWaypoint.Kind.NORMAL, System.currentTimeMillis());
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
    private boolean insidePanel(double mx, double my) { return mx >= x && mx < x + width && my >= y && my < y + height; }
    /** 小弹窗以整个屏幕拦截地图操作，表单滚动仍只在卡片内部生效。 */
    @Override public boolean isMouseOver(double mx, double my) {
        return dialog ? mx >= 0 && mx < owner.width && my >= 0 && my < owner.height : insidePanel(mx, my);
    }
    boolean covers(AbstractWidget widget) { return dialog || widget.getX() < x + width && widget.getX() + widget.getWidth() > x && widget.getY() < y + height && widget.getY() + widget.getHeight() > y; }
    @Override public ScreenRectangle getRectangle() { return dialog ? new ScreenRectangle(0, 0, owner.width, owner.height) : new ScreenRectangle(x, y, width, height); }
    @Override public NarrationPriority narrationPriority() { return getFocused() == null ? NarrationPriority.NONE : NarrationPriority.FOCUSED; }
    @Override public void updateNarration(NarrationElementOutput output) {
        output.add(NarratedElementType.TITLE, MapTexts.text(dimensions ? "dimension" : creating ? "new_point" : "point_actions"));
        if (getFocused() instanceof NarratableEntry entry) entry.updateNarration(output.nest());
    }
    @Override public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
        g.nextStratum();
        if (dialog) g.fill(0, 0, owner.width, owner.height, YzuiTheme.scrim());
        YzuiTheme.card(g, x, y, width, height);
        if (dialog && creating) renderCreationHeader(g);
        else if (dimensions) {
            YzuiTheme.label(g, Minecraft.getInstance().font, MapTexts.text("dimension"), x + 12, y + 14,
                    width - 48, YzuiTheme.text(), false);
            g.fill(x + 12, top - 7, x + width - 12, top - 6, YzuiTheme.outlineVariant());
        } else {
            renderPointHeader(g);
        }
        g.enableScissor(x, top, x + width, bottom);
        if (creationLocationY >= 0) renderCreationLocation(g);
        if (pointLocationY >= 0) renderPointLocation(g);
        for (var line : lines) YzuiTheme.label(g, Minecraft.getInstance().font, line.text, line.x, line.y, line.width,
                dialog && creating ? YzuiTheme.textMuted() : YzuiTheme.text(), false);
        if (creationName != null) YzuiTheme.label(g, Minecraft.getInstance().font,
                Component.literal(name.length() + "/64"), x + width - 56, creationName.getY() - 12, 40, YzuiTheme.textMuted(), false);
        for (var widget : body) if (widget.visible) widget.extractRenderState(g, mx, my, delta);
        g.disableScissor();
        for (var widget : widgets) if (!body.contains(widget)) widget.extractRenderState(g, mx, my, delta);
        if (dialog && creating) g.fill(x + 16, bottom + 1, x + width - 16, bottom + 2,
                YzuiTheme.alpha(YzuiTheme.outlineVariant(), 0.5F));
        YzuiTheme.label(g, Minecraft.getInstance().font, message, x + 16, y + height - 53, width - 32,
                messageError ? YzuiTheme.error() : YzuiTheme.textMuted(), false);
        if (contentHeight > bottom - top) {
            int track = bottom - top, thumb = Math.max(10, track * track / contentHeight);
            int sy = top + (track - thumb) * scroll / Math.max(1, contentHeight - track);
            g.fill(x + width - 5, sy, x + width - 3, sy + thumb, YzuiTheme.primary());
        }
    }

    /** 标题以当前标点颜色作预览，明暗主题共用一套层级与间距。 */
    private void renderCreationHeader(GuiGraphicsExtractor g) {
        var font = Minecraft.getInstance().font;
        RoundedRect.fillRound(g, x + 16, y + 10, 30, 30, 10, YzuiTheme.primaryContainer());
        MapWaypointColors.render(g, MapWaypointColors.parse(color), x + 31, y + 25, 1.0F);
        YzuiTheme.label(g, font, MapTexts.text("new_point"), x + 56, y + 13, width - 104, YzuiTheme.text(), false);
        YzuiTheme.label(g, font, MapTexts.text("private_point"), x + 56, y + 27, width - 104, YzuiTheme.textMuted(), false);
    }

    /** 维度及 XYZ 保持只读，以独立卡片和等宽坐标栏显示预填的位置。 */
    private void renderCreationLocation(GuiGraphicsExtractor g) {
        int left = x + 16, inner = width - 32;
        var font = Minecraft.getInstance().font;
        RoundedRect.fill(g, left, creationLocationY, inner, 48, 7, YzuiTheme.surfaceLow());
        MenuIcon.WORLD.render(g, left + 10, creationLocationY + 8, 12, YzuiTheme.textMuted());
        YzuiTheme.label(g, font, MapTexts.dimension(point.dimension()), left + 28, creationLocationY + 10,
                inner - 64, YzuiTheme.textMuted(), false);
        int cell = (inner - 20) / 3;
        String[] axes = { "X", "Y", "Z" };
        int[] coordinates = { point.x(), point.y(), point.z() };
        for (int index = 0; index < axes.length; index++) {
            int column = left + 10 + index * cell;
            if (index > 0) g.fill(column - 6, creationLocationY + 30, column - 5, creationLocationY + 41,
                    YzuiTheme.alpha(YzuiTheme.outlineVariant(), 0.5F));
            YzuiTheme.label(g, font, Component.literal(axes[index]), column, creationLocationY + 32, 10, YzuiTheme.textMuted(), false);
            YzuiTheme.label(g, font, Component.literal(Integer.toString(coordinates[index])), column + 14,
                    creationLocationY + 32, cell - 22, YzuiTheme.text(), false);
        }
    }

    private void updateBodyVisibility() {
        for (var widget : body) widget.visible = widget.getY() >= top && widget.getY() + widget.getHeight() <= bottom;
        if (getFocused() instanceof AbstractWidget focused && !focused.visible) setFocused(null);
    }

    /** 滚动只移动已有控件，保留文本、光标及多行输入框自身的滚动位置。 */
    private void scrollCreation(int value) {
        int next = Math.clamp(value, 0, Math.max(0, contentHeight - (bottom - top)));
        int offset = scroll - next;
        if (offset == 0) return;
        scroll = next;
        for (var widget : body) widget.setY(widget.getY() + offset);
        for (int index = 0; index < lines.size(); index++) {
            var line = lines.get(index);
            lines.set(index, new Line(line.text, line.x, line.y + offset, line.width));
        }
        creationLocationY += offset;
        updateBodyVisibility();
    }

    /** Tab 在弹窗内循环，自动滚动到目标控件，窄窗口也可完整填写表单。 */
    private void cycleCreationFocus(boolean backwards) {
        int current = widgets.indexOf(getFocused()), direction = backwards ? -1 : 1;
        for (int step = 1; step <= widgets.size(); step++) {
            var target = widgets.get(Math.floorMod(current + step * direction, widgets.size()));
            if (!target.active) continue;
            if (body.contains(target)) {
                if (target.getHeight() > bottom - top) continue;
                if (target.getY() < top) scrollCreation(scroll + target.getY() - top);
                else if (target.getBottom() > bottom) scrollCreation(scroll + target.getBottom() - bottom);
            }
            if (target.visible) { setFocused(target); return; }
        }
    }
    @Override public boolean mouseClicked(MouseButtonEvent event, boolean actual) {
        if (!insidePanel(event.x(), event.y())) return dialog;
        super.mouseClicked(event, actual); return true;
    }
    @Override public boolean mouseScrolled(double mx, double my, double horizontal, double vertical) {
        if (!insidePanel(mx, my)) return dialog;
        if (dialog && creating && (my < top || my >= bottom)) return true;
        for (var widget : body) if (widget.visible && widget instanceof MultiLineEditBox && widget.isMouseOver(mx, my)
                && widget.mouseScrolled(mx, my, horizontal, vertical)) return true;
        if (dialog && creating) { scrollCreation(scroll - (int) (vertical * 24)); return true; }
        scroll = Math.clamp(scroll - (int) (vertical * 24), 0, Math.max(0, contentHeight - (bottom - top))); rebuild(); return true;
    }
    @Override public boolean keyPressed(KeyEvent event) {
        if (event.key() == InputConstants.KEY_ESCAPE) { close(); return true; }
        if (dialog && creating && event.key() == InputConstants.KEY_TAB) {
            cycleCreationFocus(event.hasShiftDown()); return true;
        }
        return super.keyPressed(event);
    }
}
