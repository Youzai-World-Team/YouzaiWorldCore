package top.csituka.youzaiworldcore.client.screen.options;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import it.unimi.dsi.fastutil.ints.IntSet;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSelectionList;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.WinScreen;
import net.minecraft.client.gui.screens.options.HasDifficultyReaction;
import net.minecraft.client.gui.screens.options.HasGamemasterPermissionReaction;
import net.minecraft.client.gui.screens.options.LanguageSelectScreen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.client.gui.screens.options.VideoSettingsScreen;
import net.minecraft.client.gui.screens.options.UnsupportedGraphicsWarningScreen;
import net.minecraft.client.gui.screens.options.controls.KeyBindsScreen;
import net.minecraft.client.gui.screens.packs.PackSelectionModel;
import net.minecraft.client.gui.screens.packs.PackSelectionScreen;
import net.minecraft.client.gui.screens.telemetry.TelemetryEventWidget;
import net.minecraft.client.gui.screens.telemetry.TelemetryInfoScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.telemetry.TelemetryEventType;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.Music;
import net.minecraft.util.FormattedCharSequence;
import top.csituka.youzaiworldcore.client.config.ClientExternalSettings;
import top.csituka.youzaiworldcore.client.render.YzuiTheme;
import top.csituka.youzaiworldcore.client.screen.YouzaiWorldCoreSettingsScreen;
import top.csituka.youzaiworldcore.client.screen.widget.TransparentButton;
import top.csituka.youzaiworldcore.util.DebugLogger;

/**
 * 独立的 MD3 设置页面。原版 Screen 只提供选项与操作回调，不参与页面绘制。
 * 客户端配置仍使用原版 options.txt 和 YZUI 已有的 yzwc/client/global_settings.json。
 */
@SuppressWarnings("null")
public final class YzuiOptionsScreen extends Screen implements HasDifficultyReaction, HasGamemasterPermissionReaction {
    private final Screen source;
    private final Screen origin;
    private final Screen back;
    private final boolean inWorld;
    private final int coreSection;
    private final UnifiedSettingsPage unified;
    private final YzuiOptionsScreen settingsRoot;
    private String pendingAnchor;
    private SettingsFrame frame;
    private EmbeddedCoreSettings corePanel;
    private final List<KeyControls> keyControls = new ArrayList<>();
    private List<AbstractWidget> captured = List.of();
    private String query = "";
    private double scroll;
    private List<Object> packEntries = List.of();
    private boolean telemetryOptIn;
    private Object videoPreset;
    private boolean rebuilding;
    private boolean updatingPermissions;
    private boolean showingGraphicsWarning;
    private boolean leavingForLegacy;

    YzuiOptionsScreen(Screen source, Screen origin, boolean inWorld) {
        this(source, origin, inWorld, null, -1);
    }

    YzuiOptionsScreen(Screen source, Screen origin, boolean inWorld, YzuiOptionsScreen settingsRoot) {
        this(source, origin, inWorld, null, -1, settingsRoot);
    }

    private YzuiOptionsScreen(Screen source, Screen origin, boolean inWorld, Screen back, int coreSection) {
        this(source, origin, inWorld, back, coreSection, null);
    }

    private YzuiOptionsScreen(Screen source, Screen origin, boolean inWorld, Screen back, int coreSection, YzuiOptionsScreen settingsRoot) {
        super(source == null ? text("mods") : source instanceof OptionsScreen ? text("title") : source instanceof WinScreen
                ? Component.translatable("credits_and_attribution.button.credits") : source.getTitle());
        this.source = source;
        this.origin = origin;
        this.inWorld = inWorld;
        this.back = back;
        this.coreSection = coreSection;
        this.settingsRoot = settingsRoot;
        this.unified = source instanceof OptionsScreen options ? new UnifiedSettingsPage(this, options) : null;
        if (source != null) YzuiSettingsRouter.remember(source, this);
        DebugLogger.info("YzuiSettings", "创建设置页面：%s", getTitle().getString());
    }

    Screen origin() { return origin; }
    boolean inWorld() { return inWorld; }
    boolean isUnified() { return unified != null; }
    YzuiOptionsScreen rootSettings() { return isUnified() ? this : settingsRoot; }
    void reveal(String anchor) { pendingAnchor = anchor; }
    Screen legacyScreen() { return new OptionsScreen(origin, minecraft.options, inWorld); }

    private VideoSettingsScreen videoPage() {
        return source instanceof VideoSettingsScreen video ? video : unified == null ? null : unified.video();
    }

    private boolean busy() {
        return unified != null && unified.busyMessage() != null
                || source instanceof YouzaiWorldCoreSettingsScreen core && core.isConfigOperationActive();
    }

    static Component text(String key) { return Component.translatable("screen.youzaiworldcore.options." + key); }

    @Override
    protected void init() {
        rebuilding = true;
        try {
            if (unified != null) unified.flush();
            AbstractWidget focused = SettingsWidgets.focused(this);
            boolean searchFocused = frame != null && frame.search.isFocused();
            if (frame != null) {
                query = frame.search.getValue();
                scroll = frame.content.scrollAmount();
            }
            clearFocus();
            clearWidgets();
            keyControls.clear();
            if (source instanceof YouzaiWorldCoreSettingsScreen core) {
                initCore(core);
                return;
            }
            if (source != null) source.init(width, height);
            List<SettingsList.Row> rows = new ArrayList<>();
            List<AbstractWidget> footer = new ArrayList<>();
            if (source == null) {
                SettingsCompatibility.addModRows(this, rows);
            } else if (source instanceof OptionsScreen) {
                buildHome(rows, footer);
            } else if (source instanceof ConfirmScreen || source instanceof UnsupportedGraphicsWarningScreen) {
                buildDialog(rows, footer);
            } else if (source instanceof WinScreen) {
                buildCredits(rows);
            } else {
                collectRows(rows, footer);
                if (source instanceof KeyBindsScreen keys) buildKeys(keys, rows);
                if (source instanceof LanguageSelectScreen) buildLanguages(rows);
                if (source instanceof PackSelectionScreen) buildPacks(rows);
                if (source instanceof TelemetryInfoScreen) buildTelemetry(rows);
                if (source instanceof VideoSettingsScreen && SettingsCompatibility.sodiumAvailable()) {
                    rows.addFirst(SettingsList.Row.controls(text("sodium"), text("sodium.description"),
                            action(text("open"), () -> SettingsCompatibility.openSodium(this))));
                }
            }
            if (footer.isEmpty()) footer.add(action(Component.translatable("gui.done"), this::onClose));
            boolean searchable = !(source instanceof ConfirmScreen || source instanceof UnsupportedGraphicsWarningScreen);
            frame = new SettingsFrame(this, rows, footer, this::addRenderableWidget, query, scroll, searchable);
            if (source != null) captured = SettingsWidgets.flatten(source);
            if (videoPage() != null) videoPreset = minecraft.options.graphicsPreset().get();
            refreshKeyLabels();
            if (searchFocused && searchable) setInitialFocus(frame.search);
            else frame.restoreFocus(focused);
            if (pendingAnchor != null && unified != null) {
                SettingsList.Row target = unified.anchorRow(pendingAnchor);
                if (target != null) {
                    frame.search.setValue("");
                    frame.content.reveal(target);
                }
                pendingAnchor = null;
            }
        } finally {
            rebuilding = false;
        }
    }

    private void buildHome(List<SettingsList.Row> rows, List<AbstractWidget> footer) {
        unified.appendRows(rows, footer);
    }

    private void collectRows(List<SettingsList.Row> rows, List<AbstractWidget> footer) {
        boolean special = source instanceof KeyBindsScreen || source instanceof LanguageSelectScreen
                || source instanceof PackSelectionScreen || source instanceof TelemetryInfoScreen;
        List<AbstractWidget> widgets = special
                ? source.children().stream().filter(AbstractWidget.class::isInstance).map(AbstractWidget.class::cast).toList()
                : SettingsWidgets.flatten(source);
        for (AbstractWidget widget : widgets) {
            if (!widget.visible) continue;
            if (widget instanceof AbstractSelectionList<?> || widget instanceof EditBox
                    || widget instanceof TelemetryEventWidget || widget instanceof Checkbox && source instanceof TelemetryInfoScreen) continue;
            if (takeFooter(widget, footer) || isTitle(widget)) continue;
            addWidgetRow(rows, widget);
        }
    }

    private boolean isTitle(AbstractWidget widget) {
        return widget instanceof StringWidget && (widget.getMessage().equals(source.getTitle())
                || widget.getMessage().getString().isBlank());
    }

    private static boolean takeFooter(AbstractWidget widget, List<AbstractWidget> footer) {
        if (!(widget instanceof Button)) return false;
        for (String key : List.of("gui.done", "gui.back", "gui.cancel")) {
            if (SettingsWidgets.key(widget.getMessage(), key)) {
                footer.add(widget);
                return true;
            }
        }
        return false;
    }

    private static void addWidgetRow(List<SettingsList.Row> rows, AbstractWidget widget) {
        if (widget instanceof StringWidget) rows.add(SettingsList.Row.heading(widget.getMessage()));
        else rows.add(SettingsList.Row.widget(widget));
    }

    private void buildDialog(List<SettingsList.Row> rows, List<AbstractWidget> footer) {
        if (source instanceof UnsupportedGraphicsWarningScreen) {
            for (Object line : SettingsWidgets.read(source, "message", List.class)) {
                rows.add(SettingsList.Row.text(Component.empty(), (Component) line));
            }
        }
        for (AbstractWidget widget : SettingsWidgets.flatten(source)) {
            if (widget instanceof Button) footer.add(widget);
            else if (!isTitle(widget)) addWidgetRow(rows, widget);
        }
    }

    private void buildCredits(List<SettingsList.Row> rows) {
        List<?> lines = SettingsWidgets.read(source, "lines", List.class);
        IntSet centered = SettingsWidgets.read(source, "centeredLines", IntSet.class);
        StringBuilder paragraph = new StringBuilder();
        for (int i = 0; i < lines.size(); i++) {
            String line = SettingsWidgets.plainText((FormattedCharSequence) lines.get(i));
            if (line.isBlank() || centered.contains(i)) {
                if (!paragraph.isEmpty()) {
                    rows.add(SettingsList.Row.text(Component.literal(paragraph.toString()), Component.empty()));
                    paragraph.setLength(0);
                }
                if (!line.isBlank()) rows.add(SettingsList.Row.heading(Component.literal(line)));
            } else {
                if (!paragraph.isEmpty()) paragraph.append('\n');
                paragraph.append(line);
            }
        }
        if (!paragraph.isEmpty()) rows.add(SettingsList.Row.text(Component.literal(paragraph.toString()), Component.empty()));
    }

    // ===== 键位与语言：新布局保留原版按键捕获、默认值和语言应用流程 =====

    private record KeyControls(KeyMapping key, TransparentButton change, TransparentButton reset) { }

    private void buildKeys(KeyBindsScreen keys, List<SettingsList.Row> rows) {
        KeyMapping.Category lastCategory = null;
        for (KeyMapping key : Arrays.stream(minecraft.options.keyMappings).sorted().toList()) {
            if (!key.getCategory().equals(lastCategory)) {
                lastCategory = key.getCategory();
                rows.add(SettingsList.Row.heading(lastCategory.label()));
            }
            TransparentButton change = action(key.getTranslatedKeyMessage(), () -> {
                keys.selectedKey = key;
                refreshKeyLabels();
            });
            TransparentButton reset = action(Component.translatable("controls.reset"), () -> {
                key.setKey(key.getDefaultKey());
                KeyMapping.resetMapping();
                minecraft.options.save();
                refreshKeyLabels();
            });
            keyControls.add(new KeyControls(key, change, reset));
            rows.add(SettingsList.Row.controls(Component.translatable(key.getName()), Component.empty(), change, reset));
        }
    }

    private void refreshKeyLabels() {
        if (!(source instanceof KeyBindsScreen keys)) return;
        SettingsWidgets.read(keys, "resetButton", Button.class).active = Arrays.stream(minecraft.options.keyMappings)
                .anyMatch(key -> !key.isDefault());
        for (KeyControls control : keyControls) {
            KeyMapping key = control.key;
            boolean conflict = !key.isUnbound() && Arrays.stream(minecraft.options.keyMappings)
                    .anyMatch(other -> other != key && key.same(other));
            boolean selected = keys.selectedKey == key;
            Component label = selected ? text("key.waiting") : key.getTranslatedKeyMessage();
            control.change.setMessage(conflict && !selected ? Component.literal("! ").append(label) : label);
            control.change.setStyle(selected ? YzuiTheme.ButtonStyle.FILLED
                    : conflict ? YzuiTheme.ButtonStyle.DANGER : YzuiTheme.ButtonStyle.TONAL);
            control.change.setTooltip(Tooltip.create(selected ? text("key.capture")
                    : conflict ? text("key.conflict") : Component.translatable(key.getName())));
            control.reset.active = !key.isDefault();
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void buildLanguages(List<SettingsList.Row> rows) {
        AbstractSelectionList languageList = SettingsWidgets.read(source, "languageSelectionList", AbstractSelectionList.class);
        rows.add(SettingsList.Row.heading(Component.translatable("options.language")));
        for (Object item : languageList.children()) {
            String code = SettingsWidgets.read(item, "code", String.class);
            var language = minecraft.getLanguageManager().getLanguages().get(code);
            if (language == null) continue;
            TransparentButton choose = action(language.toComponent(), () -> {
                SettingsList.selectNativeEntry(languageList, item);
                rebuildView();
            });
            if (languageList.getSelected() == item) choose.setStyle(YzuiTheme.ButtonStyle.FILLED);
            rows.add(SettingsList.Row.controls(Component.literal(code), Component.empty(), choose));
        }
    }

    // ===== 资源包：直接使用原版选择模型，保留固定包、不兼容确认、顺序和提交行为 =====

    private PackSelectionModel packModel() { return SettingsWidgets.read(source, "model", PackSelectionModel.class); }

    private void buildPacks(List<SettingsList.Row> rows) {
        PackSelectionModel model = packModel();
        rows.add(SettingsList.Row.heading(Component.translatable("pack.selected.title")));
        model.getSelected().forEach(entry -> addPack(rows, entry));
        rows.add(SettingsList.Row.heading(Component.translatable("pack.available.title")));
        model.getUnselected().forEach(entry -> addPack(rows, entry));
        packEntries = nativePackEntries();
    }

    private void addPack(List<SettingsList.Row> rows, PackSelectionModel.Entry entry) {
        List<AbstractWidget> buttons = new ArrayList<>();
        if (entry.isSelected()) {
            TransparentButton remove = action(text("pack.disable"), () -> { entry.unselect(); rebuildView(); });
            remove.active = entry.canUnselect();
            buttons.add(remove);
            TransparentButton up = action(text("pack.up"), () -> { entry.moveUp(); rebuildView(); });
            up.active = entry.canMoveUp();
            buttons.add(up);
            TransparentButton down = action(text("pack.down"), () -> { entry.moveDown(); rebuildView(); });
            down.active = entry.canMoveDown();
            buttons.add(down);
        } else {
            TransparentButton select = action(text("pack.enable"), () -> {
                if (entry.getCompatibility().isCompatible()) { entry.select(); rebuildView(); }
                else minecraft.gui.setScreen(new ConfirmScreen(confirmed -> {
                    if (confirmed) entry.select();
                    minecraft.gui.setScreen(this);
                }, Component.translatable("pack.incompatible"), entry.getCompatibility().getConfirmation()));
            });
            select.active = entry.canSelect();
            buttons.add(select);
        }
        rows.add(SettingsList.Row.pack(entry.getTitle(), entry.getExtendedDescription(), entry.getIconTexture(),
                entry.getId(), buttons.toArray(AbstractWidget[]::new)));
    }

    private List<Object> nativePackEntries() {
        List<Object> entries = new ArrayList<>();
        for (GuiEventListener child : source.children()) {
            if (child instanceof AbstractSelectionList<?> list) entries.addAll(list.children());
        }
        return entries;
    }

    private void buildTelemetry(List<SettingsList.Row> rows) {
        Checkbox original = SettingsWidgets.read(source, "checkbox", Checkbox.class);
        // 账号或环境不支持可选遥测时，原版不会创建复选框，不能补出可用开关。
        if (original != null) {
            AbstractWidget optIn = minecraft.options.telemetryOptInExtra().createButton(minecraft.options);
            optIn.active = original.active;
            rows.add(SettingsList.Row.controls(original.getMessage(), Component.empty(), optIn));
        }
        telemetryOptIn = minecraft.extraTelemetryAvailable() && minecraft.options.telemetryOptInExtra().get();
        for (TelemetryEventType event : TelemetryEventType.values().stream()
                .sorted(Comparator.comparing(TelemetryEventType::isOptIn)).toList()) {
            String status = !event.isOptIn() ? "telemetry.event.required"
                    : telemetryOptIn ? "telemetry.event.optional" : "telemetry.event.optional.disabled";
            rows.add(SettingsList.Row.heading(Component.translatable(status, event.title())));
            rows.add(SettingsList.Row.text(event.description(), Component.empty()));
            for (var property : event.properties()) {
                rows.add(SettingsList.Row.text(property.title(), Component.empty()));
            }
        }
    }

    // ===== YouzaiWorldCore：同一内容实现同时用于嵌入页与原有 ModMenu 页面 =====

    private static Component coreTitle(String section) {
        return Component.translatable("screen.youzaiworldcore.settings.sidebar_" + section);
    }

    private void initCore(YouzaiWorldCoreSettingsScreen core) {
        frame = new SettingsFrame(this, List.of(), List.of(action(Component.translatable("gui.done"), this::onClose)),
                this::addRenderableWidget, "", 0, false);
        removeWidget(frame.content);
        frame.content.visible = false;
        String[] titles = {"visual", "config_io", "about", "developer"};
        int tabWidth = (frame.bodyWidth - 18) / 4;
        for (int index = 0; index < titles.length; index++) {
            int section = index;
            TransparentButton tab = new TransparentButton(frame.bodyX + index * (tabWidth + 6), frame.bodyY - 29,
                    tabWidth, 23, coreTitle(titles[index]), () -> {
                        if (!core.isConfigOperationActive()) {
                            core.setEmbeddedSection(section);
                            initCoreTabs();
                        }
                    });
            tab.setTooltip(Tooltip.create(tab.getMessage()));
            addRenderableWidget(tab);
        }
        core.configureEmbedded(frame.bodyX + 10, frame.bodyY + 8, frame.bodyWidth - 28,
                frame.bodyHeight - 16, coreSection);
        core.init(width, height);
        corePanel = new EmbeddedCoreSettings(core, frame.bodyX, frame.bodyY, frame.bodyWidth, frame.bodyHeight);
        addRenderableWidget(corePanel);
        initCoreTabs();
    }

    private void initCoreTabs() {
        if (!(source instanceof YouzaiWorldCoreSettingsScreen core)) return;
        int index = 0;
        for (GuiEventListener child : children()) {
            if (child instanceof TransparentButton tab && tab.getY() == frame.bodyY - 29) {
                tab.setStyle(index++ == core.getSelectedSection() ? YzuiTheme.ButtonStyle.FILLED : YzuiTheme.ButtonStyle.TONAL);
            }
        }
    }

    private TransparentButton action(Component label, Runnable onPress) {
        return new TransparentButton(0, 0, 160, 24, label, onPress);
    }

    private void rebuildView() {
        if (!rebuilding) init();
    }

    @Override protected void repositionElements() { init(); }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        frame.render(g);
        super.extractRenderState(g, mouseX, mouseY, partialTick);
        frame.content.renderPopups(g, mouseX, mouseY, partialTick);
    }

    @Override
    public void tick() {
        if (!ClientExternalSettings.isYzuiEnabled()) {
            if (!leavingForLegacy) {
                leavingForLegacy = true;
                minecraft.gui.setScreen(legacyScreen());
            }
            return;
        }
        leavingForLegacy = false;
        if (source != null) source.tick();
        if (unified != null) unified.tick();
        VideoSettingsScreen video = videoPage();
        if (video != null && videoPreset != minecraft.options.graphicsPreset().get()) {
            // Options 的原版通知只识别 OptionsSubScreen，独立外壳主动同步“自定义”预设标签。
            videoPreset = minecraft.options.graphicsPreset().get();
            video.resetOption(minecraft.options.graphicsPreset());
        }
        if (video != null) {
            video.updateFullscreenButton(minecraft.getWindow().isFullscreen());
            video.updateTransparencyButton();
        }
        frame.updateBackState();
        if (unified != null) {
            frame.setBusy(unified.busyMessage());
            if (unified.structureChanged() || !captured.equals(SettingsWidgets.flatten(source))) rebuildView();
        } else if (source instanceof YouzaiWorldCoreSettingsScreen core) frame.setChromeActive(!core.isConfigOperationActive());
        else if (source instanceof PackSelectionScreen) {
            // 目录内同名包也可能被替换；比较原版行实例，同时识别顺序、元数据和图标更新。
            if (!packEntries.equals(nativePackEntries())) rebuildView();
        } else if (source instanceof TelemetryInfoScreen && telemetryOptIn
                != (minecraft.extraTelemetryAvailable() && minecraft.options.telemetryOptInExtra().get())) {
            rebuildView();
        } else if (source != null && !(source instanceof KeyBindsScreen) && !(source instanceof LanguageSelectScreen)
                && !captured.equals(SettingsWidgets.flatten(source))) rebuildView();
        checkGraphicsWarning();
    }

    private void checkGraphicsWarning() {
        VideoSettingsScreen video = videoPage();
        if (video == null || minecraft.gui.screen() != this || showingGraphicsWarning) return;
        var warnings = minecraft.getGpuWarnlistManager();
        if (!warnings.isShowingWarning()) return;
        showingGraphicsWarning = true;
        var message = SettingsWidgets.read(video, "WARNING_MESSAGE", Component.class).copy();
        if (warnings.getRendererWarnings() != null) message.append("\n\n").append(
                Component.translatable("options.graphics.warning.renderer", warnings.getRendererWarnings()));
        if (warnings.getVendorWarnings() != null) message.append("\n\n").append(
                Component.translatable("options.graphics.warning.vendor", warnings.getVendorWarnings()));
        if (warnings.getVersionWarnings() != null) message.append("\n\n").append(
                Component.translatable("options.graphics.warning.version", warnings.getVersionWarnings()));
        minecraft.gui.setScreen(new ConfirmScreen(accepted -> {
            showingGraphicsWarning = false;
            warnings.dismissWarning();
            minecraft.options.improvedTransparency().set(accepted);
            if (accepted) minecraft.levelExtractor.allChanged();
            video.resetOption(minecraft.options.improvedTransparency());
            minecraft.gui.setScreen(this);
        }, Component.translatable("options.graphics.warning.title"), message,
                Component.translatable("options.graphics.warning.accept"), Component.translatable("options.graphics.warning.cancel")));
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (busy()) return true;
        if (unified != null) unified.flush();
        if (frame.content.popupClicked(event, doubleClick)) return true;
        if (source instanceof KeyBindsScreen keys && keys.selectedKey != null) {
            boolean handled = keys.mouseClicked(event, doubleClick);
            refreshKeyLabels();
            return handled;
        }
        boolean handled = super.mouseClicked(event, doubleClick);
        if (handled) refreshKeyLabels();
        return handled;
    }

    @Override
    public boolean mouseScrolled(double x, double y, double dx, double dy) {
        if (busy()) return true;
        if (unified != null) unified.flush();
        VideoSettingsScreen video = videoPage();
        if (video != null && minecraft.hasControlDown()) {
            // 保留原版视频设置 Ctrl + 滚轮调整 GUI 比例的快捷操作。
            boolean handled = video.mouseScrolled(x, y, dx, dy);
            if (handled) rebuildView();
            return handled;
        }
        return super.mouseScrolled(x, y, dx, dy);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (busy()) return true;
        if (unified != null && (event.key() == 258 || event.isEscape()
                || !(SettingsWidgets.focused(this) instanceof AbstractSliderButton))) unified.flush();
        if (frame.content.popupKeyPressed(event)) return true;
        if (source instanceof ConfirmScreen && event.isEscape()) return source.keyPressed(event);
        if (source instanceof KeyBindsScreen keys && keys.selectedKey != null) {
            boolean handled = keys.keyPressed(event);
            refreshKeyLabels();
            return handled;
        }
        if (event.key() == 256 && !frame.search.getValue().isEmpty()) {
            frame.search.setValue("");
            return true;
        }
        boolean handled = super.keyPressed(event);
        if (handled) refreshKeyLabels();
        return handled;
    }

    @Override public boolean charTyped(CharacterEvent event) { return busy() || super.charTyped(event); }
    @Override public boolean mouseDragged(MouseButtonEvent event, double x, double y) {
        return busy() || super.mouseDragged(event, x, y);
    }
    @Override public boolean mouseReleased(MouseButtonEvent event) { return busy() || super.mouseReleased(event); }

    @Override public void onFilesDrop(List<java.nio.file.Path> paths) { if (source != null) source.onFilesDrop(paths); }
    @Override public boolean isPauseScreen() { return source == null || source.isPauseScreen(); }
    @Override public Music getBackgroundMusic() { return source == null ? super.getBackgroundMusic() : source.getBackgroundMusic(); }
    @Override public Component getNarrationMessage() {
        return source == null ? super.getNarrationMessage() : source.getNarrationMessage();
    }
    @Override public boolean shouldCloseOnEsc() {
        if (busy()) return false;
        if (source instanceof ConfirmScreen) {
            Button cancel = SettingsWidgets.read(source, "noButton", Button.class);
            return cancel != null && cancel.active;
        }
        if (source instanceof YouzaiWorldCoreSettingsScreen core) return !core.isConfigOperationActive();
        return source == null || source.shouldCloseOnEsc();
    }
    @Override public void updateNarratorStatus(boolean active) {
        super.updateNarratorStatus(active);
        if (source != null) source.updateNarratorStatus(active);
        if (unified != null) unified.narratorChanged(active);
    }

    @Override
    public void onClose() {
        // ConfirmScreen 的 onClose 会直接离开游戏界面，取消必须走其 Escape 回调及延时校验。
        if (source instanceof ConfirmScreen) source.keyPressed(new KeyEvent(256, 0, 0));
        else if (!shouldCloseOnEsc()) return;
        else if (source != null) {
            if (unified != null) unified.flush();
            SettingsWidgets.flush(source);
            source.onClose();
        }
        else minecraft.gui.setScreen(back);
    }

    @Override
    public void removed() {
        if (frame != null) frame.content.closePopups();
        if (unified != null) unified.removed();
        if (source != null) {
            SettingsWidgets.flush(source);
            source.removed();
        }
        minecraft.options.save();
    }

    @Override public void added() {
        if (source != null) source.added();
        if (unified != null) unified.added();
    }
    @Override public void onDifficultyChanged() {
        if (source instanceof HasDifficultyReaction reaction) reaction.onDifficultyChanged();
        if (unified != null) unified.difficultyChanged();
    }
    @Override public void onGamemasterPermissionChanged(boolean value) {
        if (updatingPermissions) return;
        updatingPermissions = true;
        try {
            if (source instanceof HasGamemasterPermissionReaction reaction) reaction.onGamemasterPermissionChanged(value);
            if (unified != null) unified.permissionsChanged();
        } finally {
            updatingPermissions = false;
        }
    }
}
