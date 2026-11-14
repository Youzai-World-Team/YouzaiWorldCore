package top.csituka.youzaiworldcore.client.screen.options;

import net.minecraft.client.gui.narration.NarrationTrigger;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.components.ResettableOptionWidget;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.AccessibilityOptionsScreen;
import net.minecraft.client.gui.screens.options.ChatOptionsScreen;
import net.minecraft.client.gui.screens.options.FontOptionsScreen;
import net.minecraft.client.gui.screens.options.HasDifficultyReaction;
import net.minecraft.client.gui.screens.options.MouseSettingsScreen;
import net.minecraft.client.gui.screens.options.OnlineOptionsScreen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.client.gui.screens.options.SkinCustomizationScreen;
import net.minecraft.client.gui.screens.options.SoundOptionsScreen;
import net.minecraft.client.gui.screens.options.VideoSettingsScreen;
import net.minecraft.client.gui.screens.WorldOptionsScreen;
import net.minecraft.client.gui.screens.options.controls.ControlsScreen;
import net.minecraft.client.gui.screens.telemetry.TelemetryInfoScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import top.csituka.youzaiworldcore.client.screen.CoreSettingsSection;
import top.csituka.youzaiworldcore.client.screen.YouzaiWorldCoreSettingsScreen;
import top.csituka.youzaiworldcore.client.screen.widget.TransparentButton;
import top.csituka.youzaiworldcore.util.DebugLogger;

/**
 * 汇总普通设置子页的实际控件，供外壳按分区展示和全局搜索。原版页面只作为选项与回调模型存在。
 * 页面离开时统一提交延迟滑条和视频设置；返回后重新读取最新配置。
 */
@SuppressWarnings("null")
final class UnifiedSettingsPage {
    private record Page(Screen screen, Component title) { }

    // 在线页仅保留路由别名；外部入口回到设置中心，不创建在线分类或控件。
    private static final Set<Class<? extends Screen>> INLINE_PAGES = Set.of(
            SkinCustomizationScreen.class, SoundOptionsScreen.class, VideoSettingsScreen.class,
            ControlsScreen.class, MouseSettingsScreen.class, FontOptionsScreen.class,
            ChatOptionsScreen.class, AccessibilityOptionsScreen.class, OnlineOptionsScreen.class, WorldOptionsScreen.class);
    private static final Set<String> INLINE_LINKS = Set.of(
            "options.skinCustomisation", "options.sounds", "options.video", "options.controls", "options.chat",
            "options.accessibility", "options.online", "options.worldOptions.button", "options.mouse_settings", "options.font");

    private final YzuiOptionsScreen host;
    private final OptionsScreen home;
    private final Minecraft minecraft = Minecraft.getInstance();
    private final List<Page> pages = new ArrayList<>();
    private final Map<String, SettingsList.Row> anchors = new LinkedHashMap<>();
    private final Map<Screen, List<AbstractWidget>> captured = new IdentityHashMap<>();
    private final Map<OptionInstance<?>, List<AbstractWidget>> bindings = new IdentityHashMap<>();
    private final Map<OptionInstance<?>, Object> values = new IdentityHashMap<>();
    private List<AbstractWidget> displayed = List.of();
    private VideoSettingsScreen video;
    private TelemetryInfoScreen telemetry;
    private boolean fresh = true;
    private boolean refreshWorld;

    UnifiedSettingsPage(YzuiOptionsScreen host, OptionsScreen home) {
        this.host = host;
        this.home = home;
    }

    static boolean contains(Screen screen) { return INLINE_PAGES.contains(screen.getClass()); }

    static String anchor(Screen screen) {
        return screen instanceof YouzaiWorldCoreSettingsScreen core
                ? "core." + core.getSelectedSection() : screen.getClass().getName();
    }

    SettingsList.Row anchorRow(String anchor) { return anchors.get(anchor); }
    String anchorKey(SettingsList.Row row) {
        return anchors.entrySet().stream().filter(entry -> entry.getValue() == row)
                .map(Map.Entry::getKey).findFirst().orElse(null);
    }
    VideoSettingsScreen video() { return video; }

    void added() { fresh = true; }

    private void add(Screen screen) { add(screen, screen.getTitle()); }

    private void add(Screen screen, Component title) {
        pages.add(new Page(screen, title));
        YzuiSettingsRouter.remember(screen, host);
        screen.added();
    }

    private void initialize() {
        if (fresh) {
            pages.clear();
            video = null;
            // Sodium 与附属模组自己管理视频控件及应用流程，不创建另一套原版视频模型。
            if (!SettingsCompatibility.sodiumAvailable()) {
                video = new VideoSettingsScreen(host, minecraft, minecraft.options);
                add(video);
            }
            add(new SoundOptionsScreen(host, minecraft.options));
            add(new ControlsScreen(host, minecraft.options));
            add(new MouseSettingsScreen(host, minecraft.options));
            add(new SkinCustomizationScreen(host, minecraft.options));
            FontOptionsScreen fonts = new FontOptionsScreen(host, minecraft.options);
            add(fonts, Component.translatable("options.language"));
            add(new ChatOptionsScreen(host, minecraft.options));
            add(new AccessibilityOptionsScreen(host, minecraft.options));
            if (host.inWorld() && minecraft.level != null) add(new WorldOptionsScreen(host, minecraft.level));
            telemetry = new TelemetryInfoScreen(host, minecraft.options);
            add(telemetry);
            for (CoreSettingsSection section : CoreSettingsSection.values()) {
                YouzaiWorldCoreSettingsScreen core = new YouzaiWorldCoreSettingsScreen(host);
                core.configureEmbedded(0, 0, Math.max(100, host.width - 80), Math.max(40, host.height), section.id());
                add(core, section.title());
            }
            fresh = false;
            refreshWorld = false;
            DebugLogger.info("YzuiSettings", "已加载 %d 个设置分区，按选中分区独立展示", pages.size());
        } else if (refreshWorld) {
            for (int index = 0; index < pages.size(); index++) {
                Page previous = pages.get(index);
                if (!(previous.screen instanceof WorldOptionsScreen) || minecraft.level == null) continue;
                previous.screen.removed();
                WorldOptionsScreen replacement = new WorldOptionsScreen(host, minecraft.level);
                pages.set(index, new Page(replacement, replacement.getTitle()));
                YzuiSettingsRouter.remember(replacement, host);
                replacement.added();
            }
            refreshWorld = false;
        }
        for (Page page : pages) {
            if (page.screen instanceof YouzaiWorldCoreSettingsScreen core) {
                core.configureEmbedded(0, 0, Math.max(100, host.width - 80), Math.max(40, host.height), core.getSelectedSection());
            }
            page.screen.init(host.width, host.height);
        }
    }

    void appendRows(List<SettingsList.Row> rows, List<AbstractWidget> footer) {
        initialize();
        anchors.clear();
        captured.clear();
        bindings.clear();
        values.clear();
        Map<OptionInstance<?>, SettingsList.Row> primary = new IdentityHashMap<>();
        List<AbstractWidget> resources = new ArrayList<>();
        AbstractWidget language = null;

        heading(rows, anchor(home), YzuiOptionsScreen.text("game")).navigationGroup = Component.literal("Minecraft");
        for (AbstractWidget widget : SettingsWidgets.flatten(home)) {
            if (isTitle(home, widget)) continue;
            // 通用分类不显示视场角；按选项实例识别，不依赖语言或滑块当前数值。
            if (widget instanceof OptionInstance.OptionInstanceSliderButton<?> slider
                    && SettingsWidgets.read(slider, "instance", OptionInstance.class) == minecraft.options.fov()) continue;
            if (isFooter(widget)) { footer.add(widget); continue; }
            if (isInlineLink(widget) || SettingsWidgets.key(widget.getMessage(), "options.telemetry")) continue;
            if (SettingsWidgets.key(widget.getMessage(), "options.language")) language = widget;
            else if (widget instanceof Button) resources.add(widget);
            else rows.add(SettingsList.Row.widget(widget));
        }

        if (SettingsCompatibility.sodiumAvailable()) {
            heading(rows, VideoSettingsScreen.class.getName(), Component.translatable("options.video"));
            rows.add(SettingsList.Row.controls(YzuiOptionsScreen.text("sodium"), YzuiOptionsScreen.text("sodium.description"),
                    button(YzuiOptionsScreen.text("open"), () -> SettingsCompatibility.openSodium(host))));
        }
        for (Page page : pages) {
            if (page.screen instanceof YouzaiWorldCoreSettingsScreen || page.screen == telemetry) continue;
            heading(rows, anchor(page.screen), page.title);
            if (page.screen instanceof FontOptionsScreen && language != null) rows.add(SettingsList.Row.widget(language));
            appendNative(page, rows, primary);
        }

        heading(rows, "resources", YzuiOptionsScreen.text("resources"));
        Checkbox optIn = SettingsWidgets.read(telemetry, "checkbox", Checkbox.class);
        if (optIn != null) {
            OptionInstance<Boolean> option = minecraft.options.telemetryOptInExtra();
            AbstractWidget control = option.createButton(minecraft.options);
            control.active = optIn.active;
            rows.add(SettingsList.Row.controls(optIn.getMessage(), Component.empty(), control));
            bindings.put(option, List.of(control));
            values.put(option, option.get());
        }
        for (AbstractWidget widget : resources) rows.add(SettingsList.Row.widget(widget));
        heading(rows, "mods", YzuiOptionsScreen.text("mods"));
        SettingsCompatibility.addModRows(host, rows);
        rows.add(SettingsList.Row.controls(YzuiOptionsScreen.text("compatibility"), YzuiOptionsScreen.text("compatibility.description"),
                button(YzuiOptionsScreen.text("open"), () -> YzuiSettingsRouter.openCompatibility(host))));
        boolean firstCoreSection = true;
        for (Page page : pages) {
            if (!(page.screen instanceof YouzaiWorldCoreSettingsScreen core)) continue;
            SettingsList.Row section = heading(rows, anchor(core), page.title);
            if (firstCoreSection) {
                section.navigationGroup = Component.literal("YouzaiWorldCore");
                firstCoreSection = false;
            }
            appendCoreOptions(rows, core);
        }
        for (Page page : pages) captured.put(page.screen, SettingsWidgets.flatten(page.screen));
        displayed = rows.stream().flatMap(row -> row.widgets.stream()).toList();
    }

    private void appendCoreOptions(List<SettingsList.Row> rows, YouzaiWorldCoreSettingsScreen core) {
        if (core.getSelectedSection() == CoreSettingsSection.APPEARANCE.id()) SettingsDetails.appearance(rows);
        if (core.getSelectedSection() == CoreSettingsSection.MAP.id()) {
            SettingsDetails.map(rows, host, null);
            return;
        }
        for (var option : core.embeddedOptions()) {
            if (option.control() != null && SettingsWidgets.key(option.control().getMessage(), "screen.youzaiworldcore.appearance.title")) continue;
            Component description = option.control() != null && SettingsWidgets.key(option.control().getMessage(), "screen.youzaiworldcore.settings.yzhud_customize")
                    ? YzuiOptionsScreen.text("hud.save_hint") : option.description();
            rows.add(option.control() == null ? SettingsList.Row.text(option.label(), option.description())
                    : SettingsList.Row.controls(option.label(), description, option.control()));
        }
    }

    private void appendNative(Page page, List<SettingsList.Row> rows, Map<OptionInstance<?>, SettingsList.Row> primary) {
        Map<AbstractWidget, OptionInstance<?>> options = SettingsWidgets.options(page.screen);
        Set<AbstractWidget> sectionHeaders = SettingsWidgets.sectionHeaders(page.screen);
        for (var binding : options.entrySet()) {
            if (isHiddenAccessibilityOption(page.screen, binding.getValue())) continue;
            bindings.computeIfAbsent(binding.getValue(), ignored -> new ArrayList<>()).add(binding.getKey());
            values.put(binding.getValue(), binding.getValue().get());
        }
        for (AbstractWidget widget : SettingsWidgets.flatten(page.screen)) {
            if (isTitle(page.screen, widget) || isFooter(widget) || isInlineLink(widget)) continue;
            OptionInstance<?> option = options.get(widget);
            if (isHiddenAccessibilityOption(page.screen, option)
                    || (page.screen instanceof AccessibilityOptionsScreen
                    && SettingsWidgets.key(widget.getMessage(), "options.accessibility.link"))) continue;
            if (widget instanceof StringWidget || sectionHeaders.contains(widget)) {
                Component title = sectionHeaders.contains(widget) ? widget.getMessage().copy().setStyle(Style.EMPTY) : widget.getMessage();
                rows.add(SettingsList.Row.subheading(title));
                continue;
            }
            SettingsList.Row existing = option == null ? null : primary.get(option);
            if (existing != null) {
                // 原版在声音、控制、辅助功能等页面重复放置的同一选项只显示一次，搜索仍覆盖这些分类。
                existing.addSearchTerms(page.title.getString());
                continue;
            }
            SettingsList.Row row = SettingsList.Row.widget(widget);
            row.addSearchTerms(page.title.getString());
            rows.add(row);
            if (option != null) primary.put(option, row);
        }
    }

    private boolean isHiddenAccessibilityOption(Screen screen, OptionInstance<?> option) {
        if (!(screen instanceof AccessibilityOptionsScreen) || option == null) return false;
        // 按原版选项身份过滤，避免译名变化或误删「高对比度方块轮廓」。
        return option == minecraft.options.highContrast() || option == minecraft.options.darkMojangStudiosBackground()
                || option == minecraft.options.narrator() || option == minecraft.options.narratorHotkey();
    }

    private SettingsList.Row heading(List<SettingsList.Row> rows, String anchor, Component title) {
        SettingsList.Row row = SettingsList.Row.heading(title);
        row.sectionId = anchor;
        anchors.put(anchor, row);
        rows.add(row);
        return row;
    }

    private static boolean isTitle(Screen screen, AbstractWidget widget) {
        return widget instanceof StringWidget && (widget.getMessage().equals(screen.getTitle()) || widget.getMessage().getString().isBlank());
    }

    private static boolean isInlineLink(AbstractWidget widget) {
        return widget instanceof Button && INLINE_LINKS.stream().anyMatch(key -> SettingsWidgets.key(widget.getMessage(), key));
    }

    private static boolean isFooter(AbstractWidget widget) {
        return widget instanceof Button && List.of("gui.done", "gui.back", "gui.cancel").stream()
                .anyMatch(key -> SettingsWidgets.key(widget.getMessage(), key));
    }

    private static TransparentButton button(Component title, Runnable action) {
        return new TransparentButton(0, 0, 160, 24, title, action);
    }

    void tick() {
        for (Page page : pages) page.screen.tick();
        synchronizeOptions();
    }

    private void synchronizeOptions() {
        for (var entry : values.entrySet()) {
            Object current = entry.getKey().get();
            if (Objects.equals(entry.getValue(), current)) continue;
            entry.setValue(current);
            for (AbstractWidget widget : bindings.get(entry.getKey())) {
                if (widget instanceof ResettableOptionWidget resettable) resettable.resetValue();
            }
        }
    }

    boolean structureChanged() {
        return refreshWorld || pages.stream().anyMatch(page -> !Objects.equals(captured.get(page.screen), SettingsWidgets.flatten(page.screen)));
    }

    Component busyMessage() {
        for (Page page : pages) {
            if (page.screen instanceof YouzaiWorldCoreSettingsScreen core && core.isConfigOperationActive()) return core.embeddedOperationMessage();
        }
        return null;
    }

    void flush() {
        // added() 后即将重建模型，此时旧控件的值不能写回来自 Sodium、语言或配置导入页的新设置。
        if (fresh) return;
        synchronizeOptions();
        SettingsWidgets.flushPending(displayed);
        synchronizeOptions();
    }

    void removed() {
        SettingsDetails.commitNumbers(displayed);
        flush();
        if (video != null) minecraft.getWindow().changeFullscreenVideoMode();
        for (Page page : pages) page.screen.removed();
    }

    void difficultyChanged() {
        for (Page page : pages) if (page.screen instanceof HasDifficultyReaction reaction) reaction.onDifficultyChanged();
    }

    void permissionsChanged() { refreshWorld = true; }

    void narratorChanged(boolean active, NarrationTrigger trigger) { for (Page page : pages) page.screen.updateNarratorStatus(active, trigger); }
}
