"""设置中心离线检查：执行路由、开关/下拉框、设置模型、侧栏与配方书初始化，核验接口和翻译。

不启动 Minecraft，不运行 Gradle，也不访问网络。测试替身提供客户端环境、原版列表坐标与绘制空实现；
真实控件绘制、网络服务和模组组合的视觉交互仍需按 docs/YZUI_SETTINGS.md 手动验收。
"""

from pathlib import Path
import argparse
import io
import json
import re
import struct
import subprocess
import sys
import tempfile
import zipfile

ROOT = Path(__file__).resolve().parents[1]
MINECRAFT_VERSION = dict(re.findall(r"^(\w+)=(.+)$", (ROOT / "gradle.properties").read_text(encoding="utf-8"),
                                    re.M))["minecraft_version"]
PACKAGE = "top.csituka.youzaiworldcore"
CLIENT = ROOT / "src/client/java" / PACKAGE.replace(".", "/")
OPTIONS = CLIENT / "client/screen/options"
SCREEN = "net/minecraft/client/gui/screens/Screen"
LOCALES = ("zh_cn", "zh_hk", "zh_tw", "lzh", "en_us", "en_gb", "de_de", "es_es", "fr_fr", "ru_ru")


def unique_object(pairs):
    result = {}
    for key, value in pairs:
        if key in result:
            raise AssertionError(f"JSON 重复键：{key}")
        result[key] = value
    return result


def read_json(path):
    return json.loads(path.read_text(encoding="utf-8-sig"), object_pairs_hook=unique_object)


def check_core_entry_mixin():
    """核验物品分组入口依赖的无参实例方法和影子字段，不执行 Mixin 变换。"""
    config_name = "youzaiworldcore.item_groups.client.mixins.json"
    metadata = read_json(ROOT / "src/main/resources/fabric.mod.json")
    registered = [entry if isinstance(entry, str) else entry["config"] for entry in metadata["mixins"]]
    assert config_name in registered, "物品分组 Mixin 配置未注册"
    config = read_json(ROOT / "src/client/resources" / config_name)
    assert config["client"].count("ItemGroupsSettingsEntryMixin") == 1, "分组设置入口必须且只能注册一次"
    mixin_path = ROOT / "src/client/java" / config["package"].replace(".", "/") / "ItemGroupsSettingsEntryMixin.java"
    mixin = mixin_path.read_text(encoding="utf-8")
    screen = (CLIENT / "client/screen/YouzaiWorldCoreSettingsScreen.java").read_text(encoding="utf-8")
    assert re.search(r"@Mixin\(value\s*=\s*YouzaiWorldCoreSettingsScreen\.class", mixin), "分组入口目标类已改变"
    targets = re.findall(r'@Inject\(\s*method\s*=\s*"([^"]+)"', mixin)
    assert targets, "未找到分组入口的注入目标"
    for target in targets:
        selector = re.fullmatch(r"(\w+)(?:\(\)V)?", target)
        assert selector, f"注入选择器需要扩展检查：{target}"
        declarations = re.findall(r"\b(?:private|protected|public)\s+(static\s+)?void\s+"
                                  + re.escape(selector[1]) + r"\(\s*\)\s*\{", screen)
        assert declarations == [""], f"Mixin 注入目标缺失或签名改变：{target}（应为无参 void 实例方法）"
    shadows = re.findall(r"@Shadow\s+private\s+(\w+)\s+(\w+)\s*;", mixin)
    assert shadows, "未找到分组入口的影子字段"
    for kind, name in shadows:
        assert re.search(r"\bprivate\s+" + re.escape(kind) + r"\s+" + re.escape(name)
                         + r"\s*(?:;|=)", screen), f"Mixin 影子字段缺失或类型改变：{kind} {name}"
    print(f"PASS Core 分组入口 Mixin：注册、{len(targets)} 个注入方法与 {len(shadows)} 个影子字段（源码核验）")


def check_resources():
    prefix = "screen.youzaiworldcore.options."
    source = "\n".join(path.read_text(encoding="utf-8") for path in OPTIONS.glob("*.java"))
    required = {prefix + key for key in re.findall(r'(?:YzuiOptionsScreen\.|(?<![\w.]))text\("([a-z_.]+)"(?:\)|,)', source)}
    assert required, "没有找到设置中心的语言引用"
    section_source = (CLIENT / "client/screen/CoreSettingsSection.java").read_text(encoding="utf-8")
    section_keys = {"screen.youzaiworldcore.settings.sidebar_" + suffix
                    for suffix in re.findall(r'\w+\(\d+, "([a-z_]+)"\)', section_source)}
    assert len(section_keys) == 6, "Core 应提供六个共用分类"
    for locale in LOCALES:
        values = read_json(ROOT / f"src/main/resources/assets/youzaiworldcore/lang/{locale}.json")
        supplied = {key for key in values if key.startswith(prefix)}
        assert supplied == required, f"{locale} 设置翻译键不一致：{supplied ^ required}"
        assert all(isinstance(values[key], str) and values[key].strip() for key in required), locale
        assert all(isinstance(values.get(key), str) and values[key].strip() for key in section_keys), f"{locale} 分类翻译缺失"
    config = read_json(ROOT / "src/client/resources/youzaiworldcore.client.mixins.json")
    entries = config.get("client", []) + config.get("mixins", [])
    assert entries.count("YzuiSettingsScreenSwitchMixin") == 1, "设置路由 Mixin 必须且只能注册一次"
    assert (CLIENT / "mixin/client/YzuiSettingsScreenSwitchMixin.java").is_file()
    print(f"PASS 资源：{len(LOCALES)} 种语言 × {len(required)} 个设置中心键与 {len(section_keys)} 个分类名称，路由注册完整")


class ClassFile:
    """只读取 JVM 类结构，不加载或执行游戏和模组代码。"""

    def __init__(self, data):
        stream = io.BytesIO(data)

        def take(size):
            value = stream.read(size)
            if len(value) != size:
                raise AssertionError("class 文件截断")
            return value

        def u2():
            return struct.unpack(">H", take(2))[0]

        def u4():
            return struct.unpack(">I", take(4))[0]

        assert u4() == 0xCAFEBABE
        take(4)
        pool = [None] * u2()
        index = 1
        while index < len(pool):
            tag = take(1)[0]
            if tag == 1:
                pool[index] = take(u2()).decode("utf-8", errors="replace")
            elif tag in (7, 8, 16, 19, 20):
                pool[index] = u2()
            elif tag in (3, 4, 9, 10, 11, 12, 17, 18):
                take(4)
            elif tag in (5, 6):
                take(8)
                index += 1
            elif tag == 15:
                take(3)
            else:
                raise AssertionError(f"未知常量池类型：{tag}")
            index += 1
        self.access = u2()
        self.name = pool[pool[u2()]]
        parent = u2()
        self.parent = pool[pool[parent]] if parent else None
        take(u2() * 2)

        def members():
            result = {}
            for _ in range(u2()):
                access, name, descriptor = u2(), pool[u2()], pool[u2()]
                result[(name, descriptor)] = access
                for _ in range(u2()):
                    u2()
                    take(u4())
            return result

        self.fields = members()
        self.methods = members()


def check_vanilla(jar):
    base = "net/minecraft/client/gui/screens/"
    component = "Lnet/minecraft/network/chat/Component;"
    fields = {
        "options/OptionsSubScreen": {"lastScreen": f"L{SCREEN};"},
        "WorldOptionsScreen": {"lastScreen": f"L{SCREEN};"},
        "options/LanguageSelectScreen": {"languageSelectionList": f"L{base}options/LanguageSelectScreen$LanguageSelectionList;"},
        "options/LanguageSelectScreen$LanguageSelectionList$Entry": {"code": "Ljava/lang/String;"},
        "options/controls/KeyBindsScreen": {"resetButton": "Lnet/minecraft/client/gui/components/Button;"},
        "packs/PackSelectionScreen": {"model": f"L{base}packs/PackSelectionModel;"},
        "telemetry/TelemetryInfoScreen": {"checkbox": "Lnet/minecraft/client/gui/components/Checkbox;"},
        "options/VideoSettingsScreen": {"WARNING_MESSAGE": component},
        "ConfirmScreen": {"noButton": "Lnet/minecraft/client/gui/components/Button;"},
        "options/UnsupportedGraphicsWarningScreen": {"message": "Ljava/util/List;"},
        "WinScreen": {"poem": "Z", "lines": "Ljava/util/List;", "centeredLines": "Lit/unimi/dsi/fastutil/ints/IntSet;"},
        "worldselection/AbstractGameRulesScreen$GameRuleEntry": {"label": "Ljava/util/List;", "tooltip": "Ljava/util/List;"},
    }
    with zipfile.ZipFile(jar) as archive:
        cache = {}

        def load(name):
            if name not in cache:
                cache[name] = ClassFile(archive.read(name + ".class"))
            return cache[name]

        for owner, expected in fields.items():
            for name, descriptor in expected.items():
                current = load(base + owner)
                while (name, descriptor) not in current.fields:
                    assert current.parent and current.parent != "java/lang/Object", f"字段失配：{owner}.{name} {descriptor}"
                    current = load(current.parent)
        for simple in ("SkinCustomizationScreen", "SoundOptionsScreen", "LanguageSelectScreen", "FontOptionsScreen",
                       "VideoSettingsScreen", "ChatOptionsScreen", "AccessibilityOptionsScreen", "OnlineOptionsScreen",
                       "MouseSettingsScreen", "controls/ControlsScreen", "controls/KeyBindsScreen"):
            current = load(base + "options/" + simple)
            while current.name != base + "options/OptionsSubScreen":
                assert current.parent != SCREEN, f"原版子页的继承关系发生变化：{simple}"
                current = load(current.parent)
        assert load(base + "friends/FriendsListConfirmScreen").parent == base + "ConfirmScreen"
        assert load(base + "ConfirmLinkScreen").parent == base + "ConfirmScreen"
        entries = load("net/minecraft/client/gui/components/OptionsList$Entry")
        assert ("children", "Ljava/util/List;") in entries.fields, "原版选项与控件对应关系失配"
        slider = load("net/minecraft/client/OptionInstance$OptionInstanceSliderButton")
        assert ("delayedApplyAt", "Ljava/lang/Long;") in slider.fields, "原版滑条的待应用标记失配"
        cycle = load("net/minecraft/client/gui/components/CycleButton")
        for field in (("name", component), ("values", "Lnet/minecraft/client/gui/components/CycleButton$ValueListSupplier;"),
                      ("valueStringifier", "Ljava/util/function/Function;"),
                      ("onValueChange", "Lnet/minecraft/client/gui/components/CycleButton$OnValueChange;")):
            assert field in cycle.fields, f"原版选择控件字段失配：{field}"
        assert ("tooltip", "Lnet/minecraft/client/gui/components/WidgetTooltipHolder;") in load("net/minecraft/client/gui/components/AbstractWidget").fields
        assert ("get", "()Lnet/minecraft/client/gui/components/Tooltip;") in load("net/minecraft/client/gui/components/WidgetTooltipHolder").methods
        header = load("net/minecraft/client/gui/components/OptionsList$HeaderEntry")
        assert header.parent == "net/minecraft/client/gui/components/OptionsList$AbstractEntry"
        assert ("children", "()Ljava/util/List;") in header.methods, "原版分组标题未提供子控件"
        assert load("net/minecraft/client/gui/components/FocusableTextWidget").parent == "net/minecraft/client/gui/components/MultiLineTextWidget"
        assert ("visitLines", "(Lnet/minecraft/client/gui/ActiveTextCollector;)V") in load("net/minecraft/client/gui/components/MultiLineTextWidget").methods
        assert ("textRendererForWidget", "(Lnet/minecraft/client/gui/components/AbstractWidget;Lnet/minecraft/client/gui/GuiGraphicsExtractor$HoveredTextEffects;)Lnet/minecraft/client/gui/ActiveTextCollector;") in load("net/minecraft/client/gui/GuiGraphicsExtractor").methods
    print(f"PASS 原版接口：{sum(map(len, fields.values())) + 7} 个反射字段、11 类设置子页、好友确认页与分组标题接口")


def check_mods(directory):
    patterns = {"modmenu": "*modmenu-*.jar", "sodium": "*sodium-fabric-*.jar",
                "sodium-extra": "*sodium-extra-fabric-*.jar", "reeses-sodium-options": "*reeses-sodium-options-fabric-*.jar",
                "iris": "iris-fabric-*.jar", "sodium-fullbright": "sodium-fullbright-*.jar"}
    signatures = {
        "modmenu": ("com/terraformersmc/modmenu/ModMenu", (
            ("hasConfigScreen", "(Ljava/lang/String;)Z"),
            ("getConfigScreen", f"(Ljava/lang/String;L{SCREEN};)L{SCREEN};"))),
        "sodium": ("net/caffeinemc/mods/sodium/client/gui/VideoSettingsScreen", (
            ("createScreen", f"(L{SCREEN};)L{SCREEN};"),)),
    }
    for mod_id, pattern in patterns.items():
        matches = list(directory.glob(pattern))
        assert len(matches) == 1, f"{mod_id} 应有一个匹配 jar，实际 {len(matches)} 个"
        with zipfile.ZipFile(matches[0]) as archive:
            metadata = json.loads(archive.read("fabric.mod.json"))
            assert metadata["id"] == mod_id
            if mod_id in signatures:
                owner, methods = signatures[mod_id]
                info = ClassFile(archive.read(owner + ".class"))
                for method in methods:
                    assert info.methods.get(method, 0) & 0x0009 == 0x0009, f"公开静态接口失配：{mod_id} {method}"
            if mod_id == "reeses-sodium-options":
                owner = "me/flashyreese/mods/reeses_sodium_options/client/gui/SodiumVideoOptionsScreen"
                info = ClassFile(archive.read(owner + ".class"))
                assert ("<init>", f"(L{SCREEN};)V") in info.methods, "Reese 返回屏幕接口失配"
            print(f"PASS 兼容依赖：{mod_id} {metadata['version']}")


def check_java(java_home):
    sources = {}

    def stub(name, body):
        package, _, _ = name.rpartition(".")
        sources[name.replace(".", "/") + ".java"] = "package " + package + ";\n" + body

    event = "net.minecraft.client.gui.components.events"
    widgets = "net.minecraft.client.gui.components"
    screens = "net.minecraft.client.gui.screens"
    settings = PACKAGE + ".client.screen.options"
    stub(event + ".GuiEventListener", "public interface GuiEventListener {}")
    stub(event + ".ContainerEventHandler", """
public interface ContainerEventHandler extends GuiEventListener {
    java.util.List<? extends GuiEventListener> children();
    GuiEventListener getFocused();
}
""")
    stub("net.minecraft.network.chat.contents.TranslatableContents", """
public record TranslatableContents(String key) { public String getKey() { return key; } }
""")
    stub("net.minecraft.network.chat.Style", """
public record Style(boolean bold, boolean underlined, Object clickEvent) {
    public static final Style EMPTY = new Style(false, false, null);
    public boolean isBold() { return bold; } public boolean isUnderlined() { return underlined; }
    public Style withBold(Boolean value) { return new Style(value, underlined, clickEvent); }
    public Style withUnderlined(Boolean value) { return new Style(bold, value, clickEvent); }
    public Object getClickEvent() { return clickEvent; }
    public Style withClickEvent(Object value) { return new Style(bold, underlined, value); }
    public Component.Color getColor() { return null; }
}
""")
    stub("net.minecraft.network.chat.FormattedText", """
public interface FormattedText {
    interface StyledContentConsumer<T> { java.util.Optional<T> accept(Style style, String text); }
}
""")
    stub("net.minecraft.network.chat.Component", """
public class Component {
    private final Object contents;
    protected Style style = Style.EMPTY;
    private final java.util.List<Component> siblings = new java.util.ArrayList<>();
    public Component(Object contents) { this.contents = contents; }
    public Object getContents() { return contents; }
    public String getString() { return (contents instanceof net.minecraft.network.chat.contents.TranslatableContents t ? t.key() : String.valueOf(contents)) + siblings.stream().map(Component::getString).reduce("", String::concat); }
    public static Component literal(String value) { return new Component(value); }
    public static Component empty() { return literal(""); }
    public static Component translatable(String key, Object... args) {
        var result = new Component(new net.minecraft.network.chat.contents.TranslatableContents(key));
        for (Object arg : args) result.getSiblings().add(literal(" " + (arg instanceof Component component ? component.getString() : String.valueOf(arg))));
        return result;
    }
    public Component append(String text) { return literal(getString() + text); }
    public Component append(Component text) { return append(text.getString()); }
    public MutableComponent copy() { var result = new MutableComponent(contents).setStyle(style); result.getSiblings().addAll(siblings); return result; }
    public java.util.List<Component> getSiblings() { return siblings; }
    public <T> java.util.Optional<T> visit(FormattedText.StyledContentConsumer<T> consumer, Style parent) {
        Style inherited = style.getClickEvent() == null ? style.withClickEvent(parent.getClickEvent()) : style;
        var result = consumer.accept(inherited, getString());
        if (result.isPresent()) return result;
        for (Component child : siblings) { result = child.visit(consumer, inherited); if (result.isPresent()) return result; }
        return java.util.Optional.empty();
    }
    public Component withColor(int color) { return this; }
    public Style getStyle() { return style; }
    public record Color(int value) { public int getValue() { return value; } }
    @Override public boolean equals(Object other) { return other instanceof Component c && java.util.Objects.equals(contents, c.contents); }
    @Override public int hashCode() { return java.util.Objects.hashCode(contents); }
}
""")
    stub("net.minecraft.network.chat.MutableComponent", "public class MutableComponent extends Component { public MutableComponent(Object contents) { super(contents); } public MutableComponent setStyle(Style value) { style = value; return this; } }")
    stub("net.minecraft.util.FormattedCharSequence", """
public interface FormattedCharSequence {
    boolean accept(Sink sink);
    interface Sink { boolean accept(int index, Object style, int codePoint); }
}
""")
    stub(widgets + ".AbstractWidget", """
public class AbstractWidget implements net.minecraft.client.gui.components.events.GuiEventListener,
        net.minecraft.client.gui.narration.NarratableEntry {
    public boolean visible = true, active = true;
    private int x, y;
    protected int width, height;
    private float alpha = 1;
    private int tabOrderGroup;
    private final WidgetTooltipHolder tooltip = new WidgetTooltipHolder();
    private boolean focused;
    private net.minecraft.network.chat.Component message = net.minecraft.network.chat.Component.empty();
    public AbstractWidget() {}
    public AbstractWidget(net.minecraft.network.chat.Component message) { this.message = message; }
    public AbstractWidget(int x, int y, int width, int height, net.minecraft.network.chat.Component message) { this(message); setRectangle(width, height, x, y); }
    public net.minecraft.network.chat.Component getMessage() { return message; }
    public void setMessage(net.minecraft.network.chat.Component message) { this.message = message; }
    public int getX() { return x; }
    public int getY() { return y; }
    public int getWidth() { return width; }
    public int getHeight() { return height; }
    public int getRight() { return x + width; }
    public int getBottom() { return y + height; }
    public void setX(int value) { x = value; }
    public void setY(int value) { y = value; }
    public void setWidth(int value) { width = value; }
    public void setHeight(int value) { height = value; }
    public void setRectangle(int width, int height, int x, int y) { setWidth(width); setHeight(height); setX(x); setY(y); }
    public boolean isMouseOver(double x, double y) { return x >= getX() && x < getRight() && y >= getY() && y < getBottom(); }
    public void setTooltip(Tooltip value) { tooltip.set(value); }
    public float getAlpha() { return alpha; }
    public void setAlpha(float value) { alpha = value; }
    public int getTabOrderGroup() { return tabOrderGroup; }
    public void setTabOrderGroup(int value) { tabOrderGroup = value; }
    public void setFocused(boolean value) { focused = value; }
    public boolean isFocused() { return focused; }
    public void setDragging(boolean value) {}
    public void extractRenderState(net.minecraft.client.gui.GuiGraphicsExtractor g, int x, int y, float tick) { extractWidgetRenderState(g, x, y, tick); }
    protected void extractWidgetRenderState(net.minecraft.client.gui.GuiGraphicsExtractor g, int x, int y, float tick) {}
    public void onClick(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {}
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
        if (!active || !visible || event.button() != 1 || !isMouseOver(event.x(), event.y())) return false;
        onClick(event, doubleClick); return true;
    }
    public boolean mouseScrolled(double x, double y, double dx, double dy) { return false; }
    public boolean keyPressed(net.minecraft.client.input.KeyEvent event) { return false; }
    public void playDownSound(Object manager) {}
    protected net.minecraft.network.chat.MutableComponent createNarrationMessage() { return getMessage().copy(); }
    protected void defaultButtonNarrationText(net.minecraft.client.gui.narration.NarrationElementOutput output) { output.add(net.minecraft.client.gui.narration.NarratedElementType.TITLE, createNarrationMessage()); }
    protected void updateWidgetNarration(net.minecraft.client.gui.narration.NarrationElementOutput output) { defaultButtonNarrationText(output); }
    public void updateNarration(net.minecraft.client.gui.narration.NarrationElementOutput output) { updateWidgetNarration(output); }
}
""")
    stub("net.minecraft.client.gui.narration.NarratableEntry", "public interface NarratableEntry { enum NarrationPriority { NONE, FOCUSED } default NarrationPriority narrationPriority() { return NarrationPriority.NONE; } default void updateNarration(NarrationElementOutput output) {} }")
    stub("net.minecraft.client.gui.narration.NarratedElementType", "public enum NarratedElementType { TITLE, HINT }")
    stub("net.minecraft.client.gui.narration.NarrationElementOutput", """
public class NarrationElementOutput {
    public final java.util.List<String> messages = new java.util.ArrayList<>();
    public NarrationElementOutput nest() { return this; }
    public void add(NarratedElementType type, net.minecraft.network.chat.Component message) { messages.add(message.getString()); }
}
""")
    stub("net.minecraft.resources.Identifier", "public record Identifier(String value) {}")
    stub("net.minecraft.client.renderer.RenderPipelines", "public class RenderPipelines { public static final Object GUI_TEXTURED = new Object(); }")
    stub("net.minecraft.client.gui.GuiGraphicsExtractor", """
public class GuiGraphicsExtractor {
    public enum HoveredTextEffects { NONE, TOOLTIP_ONLY, TOOLTIP_AND_CURSOR }
    public HoveredTextEffects hoveredTextEffects;
    public ActiveTextCollector textRendererForWidget(net.minecraft.client.gui.components.AbstractWidget widget, HoveredTextEffects effects) {
        hoveredTextEffects = effects;
        return (text, x, y) -> text(null, text, x, y);
    }
    public record Text(String value, int x, int y) {}
    public final java.util.List<Text> texts = new java.util.ArrayList<>();
    public void text(Object... args) {
        Object value = args[1];
        texts.add(new Text(value instanceof net.minecraft.network.chat.Component component ? component.getString() : value.toString(), (int) args[2], (int) args[3]));
    }
    public void blit(Object... args) {}
    public void fill(int left, int top, int right, int bottom, int color) {}
    public void enableScissor(Object... args) {} public void disableScissor() {} public void nextStratum() {}
    public int guiWidth() { return 900; } public int guiHeight() { return 420; }
}
""")
    stub("net.minecraft.client.gui.ActiveTextCollector", "public interface ActiveTextCollector { void accept(net.minecraft.network.chat.Component text, int x, int y); }")
    stub("net.minecraft.client.gui.Font", """
public class Font {
    public final int lineHeight = 9;
    public int width(String text) { return text.length() * 6; }
    public java.util.List<String> split(net.minecraft.network.chat.Component text, int width) {
        String value = text.getString();
        java.util.List<String> lines = new java.util.ArrayList<>();
        int size = Math.max(1, width / 6);
        for (int start = 0; start < value.length(); start += size) lines.add(value.substring(start, Math.min(value.length(), start + size)));
        return lines;
    }
}
""")
    stub("net.minecraft.client.input.KeyEvent", "public record KeyEvent(int key, int scanCode, int modifiers) { public boolean isEscape() { return key == 41; } }")
    # 26.3 改用 SDL：鼠标左键为 1，键值为 SDL 扫描码（数值与原版 InputConstants 一致）。
    stub("net.minecraft.client.input.MouseButtonEvent", "public record MouseButtonEvent(double x, double y, int button) { public MouseButtonEvent(double x, double y) { this(x, y, 1); } }")
    stub("com.mojang.blaze3d.platform.InputConstants", "public class InputConstants { public static final int KEY_ESCAPE=41, KEY_RETURN=40, KEY_NUMPADENTER=88, KEY_SPACE=44, KEY_TAB=43, KEY_UP=82, KEY_DOWN=81, KEY_LEFT=80, KEY_RIGHT=79, MOUSE_BUTTON_LEFT=1, MOUSE_BUTTON_MIDDLE=2, MOUSE_BUTTON_RIGHT=3, PRESS=1, RELEASE=0, REPEAT=-1; }")
    stub("net.minecraft.client.gui.narration.NarrationTrigger", "public enum NarrationTrigger { MOUSE, KEYBOARD, SYSTEM }")
    stub(widgets + ".Tooltip", "public record Tooltip(net.minecraft.network.chat.Component message) { public static Tooltip create(net.minecraft.network.chat.Component text) { return new Tooltip(text); } }")
    stub(widgets + ".WidgetTooltipHolder", """
public class WidgetTooltipHolder {
    private Tooltip value;
    public Tooltip get() { return value; }
    public void set(Tooltip value) { this.value = value; }
}
""")
    stub(widgets + ".CycleButton", """
import java.util.List;
import java.util.function.Function;
import net.minecraft.network.chat.Component;
public class CycleButton<T> extends Button implements ResettableOptionWidget {
    private final Component name;
    private final ValueListSupplier<T> values;
    private final Function<T, Component> valueStringifier;
    private final OnValueChange<T> onValueChange;
    private T value;
    public int applications;
    public CycleButton(Component name, ValueListSupplier<T> values, Function<T, Component> labels, T value, OnValueChange<T> callback) {
        super(name); this.name = name; this.values = values; valueStringifier = labels; onValueChange = callback; this.value = value;
    }
    public T getValue() { return value; }
    public void setValue(T value) { this.value = value; applications++; }
    public void resetValue() {}
    public net.minecraft.network.chat.MutableComponent createDefaultNarrationMessage() {
        return Component.literal(name.getString() + ": " + valueStringifier.apply(value).getString()).copy();
    }
    public interface ValueListSupplier<T> { List<T> getDefaultList(); List<T> getSelectedList(); }
    public interface OnValueChange<T> { void onValueChange(CycleButton<T> button, T value); }
}
""")
    stub(widgets + ".AbstractSliderButton", """
public abstract class AbstractSliderButton extends AbstractWidget {
    protected double value;
    public AbstractSliderButton(int x, int y, int width, int height, net.minecraft.network.chat.Component label, double value) { super(x, y, width, height, label); this.value = value; }
    protected void setValue(double value) { this.value = Math.max(0, Math.min(1, value)); applyValue(); updateMessage(); }
    public void onClick(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) { setValue((event.x() - getX()) / getWidth()); }
    protected abstract void applyValue();
    protected abstract void updateMessage();
    public void extractWidgetRenderState(net.minecraft.client.gui.GuiGraphicsExtractor g, int x, int y, float tick) {}
}
""")
    stub(widgets + ".EditBox", """
public class EditBox extends AbstractWidget {
    private String value = "";
    private java.util.function.Consumer<String> responder;
    public EditBox(net.minecraft.client.gui.Font font, int x, int y, int width, int height, net.minecraft.network.chat.Component label) { super(label); setRectangle(width, height, x, y); }
    public void setHint(net.minecraft.network.chat.Component text) {}
    public void setMaxLength(int length) {}
    public void setValue(String value) { this.value = value; if (responder != null) responder.accept(value); }
    public String getValue() { return value; }
    public void setResponder(java.util.function.Consumer<String> responder) { this.responder = responder; }
}
""")
    stub(widgets + ".MultiLineTextWidget", """
public class MultiLineTextWidget extends AbstractWidget {
    public MultiLineTextWidget setMaxWidth(int width) { setWidth(width); setHeight(net.minecraft.client.Minecraft.getInstance().font.split(getMessage(), width).size() * 9); return this; }
    public MultiLineTextWidget setMaxRows(int rows) { return this; }
    public void visitLines(net.minecraft.client.gui.ActiveTextCollector collector) { collector.accept(getMessage(), getX(), getY()); }
}
""")
    stub(widgets + ".FocusableTextWidget", """
public class FocusableTextWidget extends MultiLineTextWidget {
    public int renders;
    private java.util.function.Consumer<net.minecraft.network.chat.Style> clickHandler;
    public FocusableTextWidget(net.minecraft.network.chat.Component label) { setMessage(label); }
    public FocusableTextWidget setComponentClickHandler(java.util.function.Consumer<net.minecraft.network.chat.Style> value) { clickHandler = value; return this; }
    public void onClick(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
        if (clickHandler != null) getMessage().<net.minecraft.network.chat.Style>visit((style, text) -> java.util.Optional.ofNullable(style.getClickEvent() == null ? null : style), net.minecraft.network.chat.Style.EMPTY).ifPresent(clickHandler);
    }
    public boolean keyPressed(net.minecraft.client.input.KeyEvent event) { if (!isFocused() || event.key() != 40) return false; onClick(new net.minecraft.client.input.MouseButtonEvent(getX(), getY()), false); return true; }
    protected void extractWidgetRenderState(net.minecraft.client.gui.GuiGraphicsExtractor g, int x, int y, float tick) { renders++; }
}
""")
    # 26.2 列表替身只提供已核验的坐标、滚动上限和“保证可见”语义；分组算法使用实际源码。
    stub(widgets + ".AbstractSelectionList", """
import net.minecraft.client.gui.components.events.*;
public abstract class AbstractSelectionList<E extends AbstractSelectionList.Entry<E>> extends AbstractWidget implements ContainerEventHandler {
    protected boolean centerListVertically;
    private final java.util.List<E> entries = new java.util.ArrayList<>();
    private E selected, focused;
    private double scroll;
    public AbstractSelectionList(net.minecraft.client.Minecraft minecraft, int width, int height, int y, int entryHeight) { setRectangle(width, height, 0, y); }
    public java.util.List<E> children() { return entries; }
    public E getSelected() { return selected; }
    public void setSelected(E value) { selected = value; }
    public E getFocused() { return focused; }
    @SuppressWarnings("unchecked")
    public void setFocused(GuiEventListener value) { focused = (E) value; }
    protected E getHovered() { return null; }
    protected void clearEntries() { entries.clear(); selected = null; }
    protected int addEntry(E entry, int height) { entry.setHeight(height); entries.add(entry); positionEntries(); return entries.size() - 1; }
    public double scrollAmount() { return scroll; }
    public int maxScrollAmount() { return Math.max(0, entries.stream().mapToInt(Entry::getHeight).sum() + 4 - getHeight()); }
    public void setScrollAmount(double value) { scroll = Math.max(0, Math.min(value, maxScrollAmount())); positionEntries(); }
    private void positionEntries() {
        int y = getY() + 2 - (int) scroll;
        for (E entry : entries) { entry.setX(getRowLeft()); entry.setWidth(getRowWidth()); entry.setY(y); y += entry.getHeight(); }
    }
    protected void scrollToEntry(E entry) {
        int above = entry.getY() - getY() - 2;
        if (above < 0) setScrollAmount(scroll + above);
        int below = getBottom() - entry.getY() - entry.getHeight() - 2;
        if (below < 0) setScrollAmount(scroll - below);
    }
    public boolean mouseScrolled(double x, double y, double dx, double dy) { setScrollAmount(scroll - dy * 20); return true; }
    public int getRowWidth() { return getWidth(); }
    public int getRowLeft() { return getX(); }
    protected int scrollBarX() { return getRight(); }
    protected void extractListBackground(net.minecraft.client.gui.GuiGraphicsExtractor g) {}
    protected void extractListSeparators(net.minecraft.client.gui.GuiGraphicsExtractor g) {}
    public void updateWidgetNarration(net.minecraft.client.gui.narration.NarrationElementOutput output) {}
    public abstract static class Entry<E extends Entry<E>> extends AbstractWidget {
        public abstract void extractContent(net.minecraft.client.gui.GuiGraphicsExtractor g, int mouseX, int mouseY, boolean hovered, float tick);
    }
}
""")
    stub(widgets + ".ContainerObjectSelectionList", """
import net.minecraft.client.gui.components.events.*;
public abstract class ContainerObjectSelectionList<E extends ContainerObjectSelectionList.Entry<E>> extends AbstractSelectionList<E> {
    public ContainerObjectSelectionList(net.minecraft.client.Minecraft minecraft, int width, int height, int y, int entryHeight) { super(minecraft, width, height, y, entryHeight); }
    public abstract static class Entry<E extends Entry<E>> extends AbstractSelectionList.Entry<E> implements ContainerEventHandler {
        private GuiEventListener focused;
        public GuiEventListener getFocused() { return focused; }
        public void setFocused(GuiEventListener value) { focused = value; }
        public abstract java.util.List<? extends net.minecraft.client.gui.narration.NarratableEntry> narratables();
    }
}
""")
    stub("net.minecraft.client.OptionInstance", """
public class OptionInstance<T> {
    public final String name;
    private T value;
    public OptionInstance(String name, T value) { this.name = name; this.value = value; }
    public T get() { return value; }
    public void set(T value) { this.value = value; }
    public net.minecraft.client.gui.components.AbstractWidget createButton(Object options) { return new net.minecraft.client.gui.components.FixtureOptionWidget(this); }
    public static final class OptionInstanceSliderButton<N> extends net.minecraft.client.gui.components.FixtureOptionWidget {
        private final OptionInstance<N> instance;
        private Long delayedApplyAt;
        private N desired;
        public int commits;
        public OptionInstanceSliderButton(OptionInstance<N> instance) { super(instance); this.instance = instance; desired = instance.get(); }
        public void edit(N value) { desired = value; delayedApplyAt = 1L; }
        public void applyUnsavedValue() { commits++; instance.set(desired); displayed = desired; }
        @Override public void resetValue() { super.resetValue(); desired = instance.get(); delayedApplyAt = null; }
    }
}
""")
    for name in ("Button", "StringWidget", "Checkbox"):
        stub(widgets + "." + name, "public class " + name + " extends AbstractWidget { public " + name
             + "(net.minecraft.network.chat.Component message) { super(message); } }")
    stub(widgets + ".ResettableOptionWidget", "public interface ResettableOptionWidget { void resetValue(); }")
    stub(widgets + ".FixtureOptionWidget", """
public class FixtureOptionWidget extends AbstractWidget implements ResettableOptionWidget {
    public final net.minecraft.client.OptionInstance<?> option;
    public Object displayed;
    public FixtureOptionWidget(net.minecraft.client.OptionInstance<?> option) {
        super(net.minecraft.network.chat.Component.literal(option.name)); this.option = option; displayed = option.get();
    }
    public void resetValue() { displayed = option.get(); }
}
""")
    stub(widgets + ".OptionsList", """
import net.minecraft.client.gui.components.events.*;
public class OptionsList extends AbstractWidget implements ContainerEventHandler {
    public record OptionInstanceWidget(AbstractWidget widget, net.minecraft.client.OptionInstance<?> optionInstance) {}
    public abstract static class AbstractEntry implements ContainerEventHandler { public GuiEventListener getFocused() { return null; } }
    protected static class Entry extends AbstractEntry {
        private final java.util.List<OptionInstanceWidget> children;
        Entry(OptionInstanceWidget binding) { children = java.util.List.of(binding); }
        public java.util.List<? extends GuiEventListener> children() { return children.stream().map(OptionInstanceWidget::widget).toList(); }
        public GuiEventListener getFocused() { return null; }
    }
    public static class HeaderEntry extends AbstractEntry {
        private final AbstractWidget widget;
        HeaderEntry(net.minecraft.network.chat.Component label) {
            widget = new FocusableTextWidget(label.copy().setStyle(net.minecraft.network.chat.Style.EMPTY.withBold(true).withUnderlined(true)));
        }
        public java.util.List<? extends GuiEventListener> children() { return java.util.List.of(widget); }
    }
    private final java.util.List<AbstractEntry> rows = new java.util.ArrayList<>();
    public java.util.List<AbstractEntry> children() { return rows; }
    public GuiEventListener getFocused() { return null; }
    public void add(AbstractWidget widget, net.minecraft.client.OptionInstance<?> option) { rows.add(new Entry(new OptionInstanceWidget(widget, option))); }
    public void addHeader(net.minecraft.network.chat.Component label) { rows.add(new HeaderEntry(label)); }
    public int flushes;
    public void applyUnsavedChanges() { flushes++; }
}
""")
    stub(screens + ".Screen", """
import net.minecraft.client.gui.components.events.*;
public class Screen implements ContainerEventHandler {
    public static final java.util.List<Screen> created = new java.util.ArrayList<>();
    public final java.util.List<GuiEventListener> nodes = new java.util.ArrayList<>();
    public GuiEventListener focused;
    public int width = 800, height = 480, additions, removals, ticks;
    public final net.minecraft.client.Minecraft minecraft = net.minecraft.client.Minecraft.getInstance();
    public final net.minecraft.client.gui.Font font = minecraft.font;
    private net.minecraft.network.chat.Component title;
    public Screen() { created.add(this); }
    public Screen(net.minecraft.network.chat.Component title) { this(); this.title = title; }
    protected void init() {}
    protected void repositionElements() {}
    public void clearWidgets() { nodes.clear(); }
    public void clearFocus() { focused = null; }
    public <T extends net.minecraft.client.gui.components.AbstractWidget> T addRenderableWidget(T widget) { nodes.add(widget); return widget; }
    public void extractRenderState(net.minecraft.client.gui.GuiGraphicsExtractor g, int x, int y, float tick) {}
    public boolean keyPressed(net.minecraft.client.input.KeyEvent event) { if (event.isEscape()) { onClose(); return true; } return false; }
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean actual) { return false; }
    public boolean isPauseScreen() { return false; }
    public net.minecraft.network.chat.Component getTitle() { return title == null ? net.minecraft.network.chat.Component.literal(getClass().getSimpleName()) : title; }
    public void init(int width, int height) { this.width = width; this.height = height; }
    public void added() { additions++; }
    public void removed() { removals++; }
    public void tick() { ticks++; }
    public void updateNarratorStatus(boolean active, net.minecraft.client.gui.narration.NarrationTrigger trigger) {}
    public java.util.List<GuiEventListener> children() { return nodes; }
    public GuiEventListener getFocused() { return focused; }
    public void setFocused(GuiEventListener value) { focused = value; }
    public void onClose() {}
    public boolean shouldCloseOnEsc() { return true; }
}
""")
    for name in ("TitleScreen", "PauseScreen", "CreditsAndAttributionScreen", "ConfirmScreen",
                 "multiplayer.RestrictionsScreen", "options.InWorldGameRulesScreen",
                 "options.UnsupportedGraphicsWarningScreen", "packs.PackSelectionScreen"):
        stub(screens + "." + name, "public class " + name.rsplit(".", 1)[-1] + " extends " + screens + ".Screen {}")
    stub(screens + ".ConfirmLinkScreen", "public class ConfirmLinkScreen extends ConfirmScreen {}")
    stub("example.CustomConfirmLink", "public class CustomConfirmLink extends net.minecraft.client.gui.screens.ConfirmLinkScreen {}")
    stub(screens + ".WinScreen", """
public class WinScreen extends Screen { private final boolean poem; public WinScreen(boolean poem) { this.poem = poem; } }
""")
    stub(screens + ".options.OptionsSubScreen", """
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.OptionInstance;
import net.minecraft.network.chat.Component;
public class OptionsSubScreen extends Screen {
    public static final OptionInstance<Boolean> SHARED = new OptionInstance<>("shared.control", false);
    public static final OptionInstance<Integer> RANGE = new OptionInstance<>("shared.slider", 1);
    protected final Screen lastScreen;
    public OptionsSubScreen(Screen parent) { lastScreen = parent; }
    public void init(int width, int height) {
        super.init(width, height);
        if (!nodes.isEmpty()) return;
        OptionsList list = new OptionsList();
        String name = getClass().getSimpleName();
        OptionInstance<?> option = name.equals("ControlsScreen") || name.equals("AccessibilityOptionsScreen")
                ? SHARED : new OptionInstance<>(name + ".value", "initial");
        list.add(new FixtureOptionWidget(option), option);
        if (name.equals("VideoSettingsScreen") || name.equals("AccessibilityOptionsScreen")) {
            list.add(new OptionInstance.OptionInstanceSliderButton<>(RANGE), RANGE);
        }
        nodes.add(new StringWidget(getTitle())); nodes.add(list); nodes.add(new Button(Component.translatable("gui.done")));
        if (name.equals("ControlsScreen")) {
            nodes.add(new Button(Component.translatable("options.mouse_settings")));
            nodes.add(new Button(Component.translatable("controls.keybinds")));
        }
    }
}
""")
    for name in ("SkinCustomizationScreen", "SoundOptionsScreen", "FontOptionsScreen", "ChatOptionsScreen",
                 "AccessibilityOptionsScreen", "OnlineOptionsScreen", "MouseSettingsScreen", "controls.ControlsScreen"):
        simple = name.rsplit('.', 1)[-1]
        stub(screens + ".options." + name, "public class " + simple + " extends " + screens + ".options.OptionsSubScreen { public "
             + simple + "(" + screens + ".Screen parent, Object options) { super(parent); } }")
    stub(screens + ".options.AccessibilityOptionsScreen", """
public class AccessibilityOptionsScreen extends OptionsSubScreen {
    private boolean populated;
    public AccessibilityOptionsScreen(net.minecraft.client.gui.screens.Screen parent, Object options) { super(parent); }
    public void init(int width, int height) {
        super.init(width, height); if (populated) return; populated = true;
        var options = net.minecraft.client.Minecraft.getInstance().options;
        var list = (net.minecraft.client.gui.components.OptionsList) nodes.stream().filter(net.minecraft.client.gui.components.OptionsList.class::isInstance).findFirst().orElseThrow();
        for (var option : java.util.List.of(options.highContrast(), options.darkMojangStudiosBackground(), options.narrator(), options.narratorHotkey(), options.highContrastBlockOutline(), options.showSubtitles())) {
            list.add(new net.minecraft.client.gui.components.FixtureOptionWidget(option), option);
        }
        nodes.add(new net.minecraft.client.gui.components.Button(net.minecraft.network.chat.Component.translatable("options.accessibility.link")));
        nodes.add(new net.minecraft.client.gui.components.Button(net.minecraft.network.chat.Component.translatable("options.controls")));
        nodes.add(new net.minecraft.client.gui.components.Button(net.minecraft.network.chat.Component.literal("kept.customAccessibility")));
    }
}
""")
    stub(screens + ".options.VideoSettingsScreen", """
public class VideoSettingsScreen extends OptionsSubScreen {
    private boolean headersAdded;
    public final net.minecraft.client.gui.components.FocusableTextWidget note = new net.minecraft.client.gui.components.FocusableTextWidget(net.minecraft.network.chat.Component.literal("video.note").copy().setStyle(net.minecraft.network.chat.Style.EMPTY.withClickEvent(new Object())));
    public VideoSettingsScreen(net.minecraft.client.gui.screens.Screen parent, net.minecraft.client.Minecraft minecraft, Object options) { super(parent); }
    public void init(int width, int height) {
        super.init(width, height);
        if (headersAdded) return;
        headersAdded = true;
        var list = (net.minecraft.client.gui.components.OptionsList) nodes.stream().filter(net.minecraft.client.gui.components.OptionsList.class::isInstance).findFirst().orElseThrow();
        int index = 0;
        for (String key : java.util.List.of("display", "quality", "preferences")) {
            list.addHeader(net.minecraft.network.chat.Component.translatable("options.video." + key + ".header"));
            var option = new net.minecraft.client.OptionInstance<>("video.section.option." + index++, false);
            list.add(new net.minecraft.client.gui.components.FixtureOptionWidget(option), option);
        }
        nodes.add(note);
    }
}
""")
    stub(screens + ".options.HasDifficultyReaction", "public interface HasDifficultyReaction { void onDifficultyChanged(); }")
    stub(screens + ".WorldOptionsScreen", """
import net.minecraft.client.gui.screens.options.HasDifficultyReaction;
public class WorldOptionsScreen extends net.minecraft.client.gui.screens.Screen implements HasDifficultyReaction {
    private final net.minecraft.client.gui.screens.Screen lastScreen;
    public int difficultyUpdates;
    public WorldOptionsScreen(net.minecraft.client.gui.screens.Screen parent, Object level) { lastScreen = parent; }
    public void onDifficultyChanged() { difficultyUpdates++; }
}
""")
    stub(screens + ".options.OptionsScreen", """
import net.minecraft.client.gui.screens.Screen;
public class OptionsScreen extends Screen {
    private final Screen lastScreen;
    public OptionsScreen(Screen parent, Object options) { lastScreen = parent; }
    public Screen getLastScreen() { return lastScreen; }
    public void init(int width, int height) {
        super.init(width, height);
        if (!nodes.isEmpty()) return;
        nodes.add(new net.minecraft.client.gui.components.AbstractWidget(net.minecraft.network.chat.Component.literal("options.fov")));
        for (String key : java.util.List.of("options.video", "options.sounds", "options.controls", "options.chat",
                "options.accessibility", "options.online", "options.skinCustomisation",
                "options.language", "options.resourcepack", "options.telemetry", "options.credits_and_attribution", "thirdparty.entry", "gui.done")) {
            nodes.add(new net.minecraft.client.gui.components.Button(net.minecraft.network.chat.Component.translatable(key)));
        }
    }
}
""")
    stub("example.OtherOptions", """
public class OtherOptions extends net.minecraft.client.gui.screens.options.OptionsSubScreen {
    public OtherOptions(net.minecraft.client.gui.screens.Screen parent) { super(parent); }
}
""")
    stub("example.OtherHome", """
public class OtherHome extends net.minecraft.client.gui.screens.options.OptionsScreen {
    public OtherHome(net.minecraft.client.gui.screens.Screen parent) { super(parent, null); }
}
""")
    stub("example.OtherVideo", """
public class OtherVideo extends net.minecraft.client.gui.screens.options.VideoSettingsScreen {
    public OtherVideo(net.minecraft.client.gui.screens.Screen parent) { super(parent, net.minecraft.client.Minecraft.getInstance(), null); }
}
""")
    stub("example.SodiumScreen", """
public class SodiumScreen extends net.minecraft.client.gui.screens.Screen {
    public static SodiumScreen last;
    public final net.minecraft.client.gui.screens.Screen parent;
    public SodiumScreen(net.minecraft.client.gui.screens.Screen parent) { this.parent = parent; last = this; }
}
""")
    stub("net.minecraft.client.gui.Gui", """
import net.minecraft.client.gui.screens.Screen;
public class Gui {
    public Screen current;
    public Screen screen() { return current; }
    public void setScreen(Screen next) {
        current = top.csituka.youzaiworldcore.client.screen.options.YzuiSettingsRouter.resolve(next);
    }
}
""")
    stub("net.minecraft.client.Minecraft", """
public class Minecraft {
    private static final Minecraft INSTANCE = new Minecraft();
    public final net.minecraft.client.gui.Gui gui = new net.minecraft.client.gui.Gui();
    public final Options options = new Options();
    public final net.minecraft.client.gui.Font font = new net.minecraft.client.gui.Font();
    public Object getSoundManager() { return this; }
    public Object level, connection = new Object();
    public static Minecraft getInstance() { return INSTANCE; }
    public final net.minecraft.client.resources.language.LanguageManager languages = new net.minecraft.client.resources.language.LanguageManager();
    public net.minecraft.client.resources.language.LanguageManager getLanguageManager() { return languages; }
    public Object getConnection() { return connection; }
    public final Window window = new Window();
    public Window getWindow() { return window; }
    public static class Window { public int modeUpdates; public void changeFullscreenVideoMode() { modeUpdates++; } }
}
""")
    stub("net.minecraft.client.Options", """
public final class Options {
    private final OptionInstance<Boolean> optIn = new OptionInstance<>("telemetry.opt_in", false);
    private final OptionInstance<Boolean> contrast = new OptionInstance<>("hidden.highContrast", true);
    private final OptionInstance<Boolean> logo = new OptionInstance<>("hidden.darkLogo", true);
    private final OptionInstance<Boolean> narrator = new OptionInstance<>("hidden.narrator", true);
    private final OptionInstance<Boolean> hotkey = new OptionInstance<>("hidden.narratorHotkey", true);
    private final OptionInstance<Boolean> outline = new OptionInstance<>("kept.blockOutline", true);
    private final OptionInstance<Boolean> subtitles = new OptionInstance<>("kept.subtitles", true);
    public OptionInstance<Boolean> highContrast() { return contrast; }
    public OptionInstance<Boolean> darkMojangStudiosBackground() { return logo; }
    public OptionInstance<Boolean> narrator() { return narrator; }
    public OptionInstance<Boolean> narratorHotkey() { return hotkey; }
    public OptionInstance<Boolean> highContrastBlockOutline() { return outline; }
    public OptionInstance<Boolean> showSubtitles() { return subtitles; }
    public OptionInstance<Boolean> telemetryOptInExtra() { return optIn; }
}
""")
    stub(screens + ".telemetry.TelemetryInfoScreen", """
public class TelemetryInfoScreen extends net.minecraft.client.gui.screens.Screen {
    public static boolean available = true;
    private net.minecraft.client.gui.components.Checkbox checkbox;
    public TelemetryInfoScreen() {}
    public TelemetryInfoScreen(net.minecraft.client.gui.screens.Screen parent, Object options) {}
    public void init(int width, int height) {
        super.init(width, height);
        if (available && checkbox == null) { checkbox = new net.minecraft.client.gui.components.Checkbox(net.minecraft.network.chat.Component.literal("opt in")); nodes.add(checkbox); }
    }
}
""")
    stub(PACKAGE + ".client.config.ClientExternalSettings", """
public class ClientExternalSettings {
    public static boolean enabled;
    public static boolean hud = true, font = true;
    public static int uiSaves, hudSaves, fontSaves, animationSaves;
    private static YzuiThemeMode theme = YzuiThemeMode.LIGHT;
    private static YzuiVisualStyle style = YzuiVisualStyle.STANDARD;
    public static YzuiThemeMode getYzuiTheme() { return theme; } public static void setYzuiTheme(YzuiThemeMode v) { theme = v; }
    public static YzuiVisualStyle getYzuiVisualStyle() { return style; } public static void setYzuiVisualStyle(YzuiVisualStyle v) { style = v; }
    public static boolean isYzuiEnabled() { return enabled; }
    public static void setYzuiEnabled(boolean value) { enabled = value; uiSaves++; }
    public static void setLeftHudEnabled(boolean value) { hud = value; hudSaves++; }
    public static void setCustomFontEnabled(boolean value) { font = value; fontSaves++; }
    public static void setGuiAnimationMode(GuiAnimationMode value) { animationSaves++; }
}
""")
    stub(PACKAGE + ".util.DebugLogger", "public class DebugLogger { public static void info(String module, String text, Object... args) {} public static void debug(String module, String text, Object... args) {} public static void exception(String module, String action, Throwable error) {} }")
    stub(PACKAGE + ".client.screen.widget.TransparentButton", """
public class TransparentButton extends net.minecraft.client.gui.components.Button {
    public final Runnable action;
    public top.csituka.youzaiworldcore.client.render.YzuiTheme.ButtonStyle style;
    public TransparentButton(int x, int y, int width, int height, net.minecraft.network.chat.Component label, Runnable action) {
        super(label); this.action = action; setRectangle(width, height, x, y);
    }
    public TransparentButton setStyle(top.csituka.youzaiworldcore.client.render.YzuiTheme.ButtonStyle value) { style = value; return this; }
    public void setTextLeftAligned(boolean value) {}
}
""")
    stub(PACKAGE + ".client.render.YzuiTheme", """
public class YzuiTheme {
    public enum ButtonStyle { FILLED, TEXT, TONAL }
    public static final java.util.List<int[]> cards = new java.util.ArrayList<>();
    public static void card(Object... args) { cards.add(new int[]{(int) args[1], (int) args[2], (int) args[3], (int) args[4]}); }
    public static void wrapped(net.minecraft.client.gui.GuiGraphicsExtractor g, net.minecraft.client.gui.Font font, net.minecraft.network.chat.Component text, int x, int y, int width, int maxLines, int color) {
        int line = 0; for (var value : font.split(text, width)) { if (line >= maxLines) break; g.text(font, value, x, y + line++ * (font.lineHeight + 2)); }
    }
    public static void label(Object... args) {
        ((net.minecraft.client.gui.GuiGraphicsExtractor) args[0]).text(args[1], args[2], args[3], args[4]);
    }
    public static void field(Object... args) {} public static void button(Object... args) {} public static void border(Object... args) {}
    public static boolean minimal() { return true; } public static boolean frosted() { return false; }
    public static int alpha(int color, float alpha) { return color; } public static int multiplyAlpha(int color, float alpha) { return color; }
    public static int onPrimary() { return 0; } public static int outlineVariant() { return 0; }
    public static int surface() { return 0; } public static int slotHover() { return 0; }
    public static int text() { return 0; }
    public static int textMuted() { return 0; }
    public static int primary() { return 0; }
    public static int primaryContainer() { return 0; }
    public static int onPrimaryContainer() { return 0; }
    public static int surfaceLow() { return 0; }
    public static int surfaceHigh() { return 0; }
}
""")
    stub(PACKAGE + ".client.render.RoundedRect", "public class RoundedRect { public static void fill(Object... args) {} }")
    stub(PACKAGE + ".client.animation.GuiAnimationController", "public class GuiAnimationController { public static boolean isEnabled() { return false; } }")
    stub(PACKAGE + ".client.config.YzuiThemeMode", "public enum YzuiThemeMode { LIGHT, DARK }")
    stub(PACKAGE + ".client.config.YzuiVisualStyle", "public enum YzuiVisualStyle { STANDARD, FROSTED, MINIMAL }")
    stub(PACKAGE + ".client.map.MapTexts", "public class MapTexts { public static net.minecraft.network.chat.Component text(String key, Object... args) { return net.minecraft.network.chat.Component.translatable(\"map.youzaiworldcore.\" + key, args); } }")
    stub(PACKAGE + ".map.MapLayer", "public enum MapLayer { AUTO, SURFACE, CAVE, FIXED, ROOF }")
    stub(PACKAGE + ".client.map.MapClient", "import top.csituka.youzaiworldcore.map.MapLayer; import top.csituka.youzaiworldcore.client.config.MapSettings; public class MapClient { public static Object sampling = new Object(); public static int scanCursor = 42;" + '    public static void selectLayer(MapLayer layer) {\n        MapSettings.setLayer(layer);\n        sampling = null;\n        scanCursor = 0;\n    }' + "}")
    stub(PACKAGE + ".client.screen.map.YzWorldMapScreen", "public class YzWorldMapScreen extends net.minecraft.client.gui.screens.Screen { public final net.minecraft.client.gui.screens.Screen parent; public YzWorldMapScreen(net.minecraft.client.gui.screens.Screen parent) { this.parent = parent; } }")
    stub(PACKAGE + ".client.screen.YzHudSettingsScreen", "public class YzHudSettingsScreen extends net.minecraft.client.gui.screens.Screen { public YzHudSettingsScreen(net.minecraft.client.gui.screens.Screen parent) {} }")
    stub(PACKAGE + ".client.config.MapSettings", """
public class MapSettings {
    public enum Shape { ROUNDED, CIRCLE, SQUARE }
    public enum MarkerDisplay { HIDDEN, ICONS, ICONS_AND_LABELS }
    public enum Overlay { TERRAIN, BIOME, LOAD_STATE }
    public enum Toggle { MINIMAP, ROTATE, COORDINATES, BIOME_LABEL, LAYER_LABEL, WAYPOINTS, WAYPOINT_HUD, PORTAL_PROJECTION,
        RADAR_PLAYERS, RADAR_HOSTILE, RADAR_FRIENDLY, RADAR_OTHER, ANCHORS, REMEMBER_VIEW, GRID, EXACT_LOAD_LEVEL;
        public String key() { return name().toLowerCase(java.util.Locale.ROOT); }
    }
    public static int saves;
    private static final java.util.EnumMap<Toggle, Boolean> toggles = new java.util.EnumMap<>(Toggle.class);
    public static boolean enabled(Toggle toggle) { return toggles.getOrDefault(toggle, true); }
    public static void toggle(Toggle toggle) { toggles.put(toggle, !enabled(toggle)); saves++; }
    private static int size = 144, height = 64, deaths = 5, distance = 10000;
    private static double zoom = 1;
    private static Shape shape = Shape.ROUNDED;
    private static MarkerDisplay markers = MarkerDisplay.ICONS_AND_LABELS;
    private static Overlay overlay = Overlay.TERRAIN;
    private static top.csituka.youzaiworldcore.map.MapLayer layer = top.csituka.youzaiworldcore.map.MapLayer.AUTO;
    public static int size() { return size; } public static void setSize(int v) { size = v; saves++; }
    public static int fixedHeight() { return height; } public static void setFixedHeight(int v) { height = v; saves++; }
    public static int deathLimit() { return deaths; } public static void setDeathLimit(int v) { deaths = v; saves++; }
    public static int waypointDistance() { return distance; } public static void setWaypointDistance(int v) { distance = v; saves++; }
    public static double zoom() { return zoom; } public static void setZoom(double v) { zoom = v; saves++; }
    public static Shape shape() { return shape; } public static void setShape(Shape v) { shape = v; saves++; }
    public static MarkerDisplay markerDisplay() { return markers; } public static void setMarkerDisplay(MarkerDisplay v) { markers = v; saves++; }
    public static Overlay overlay() { return overlay; } public static void setOverlay(Overlay v) { overlay = v; saves++; }
    public static top.csituka.youzaiworldcore.map.MapLayer layer() { return layer; }
    public static void setLayer(top.csituka.youzaiworldcore.map.MapLayer v) { layer = v; saves++; }
}
""")
    stub(PACKAGE + ".client.screen.YouzaiWorldCoreSettingsScreen", """
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Component;
public class YouzaiWorldCoreSettingsScreen extends net.minecraft.client.gui.screens.Screen {
    private int section;
    public boolean operating;
    public YouzaiWorldCoreSettingsScreen(net.minecraft.client.gui.screens.Screen parent) {}
    public void configureEmbedded(int x, int y, int width, int height, int section) { this.section = section; }
    public int getSelectedSection() { return section; }
    public void init(int width, int height) {
        super.init(width, height);
        if (nodes.isEmpty()) nodes.add(new AbstractWidget(Component.literal("core." + section)));
    }
    public record EmbeddedOption(Component label, Component description, AbstractWidget control) {}
    public java.util.List<EmbeddedOption> embeddedOptions() {
        return java.util.List.of(new EmbeddedOption(Component.empty(), Component.empty(), (AbstractWidget) nodes.getFirst()));
    }
    public boolean isConfigOperationActive() { return operating; }
    public Component embeddedOperationMessage() { return operating ? Component.literal("working") : null; }
}
""")
    stub("net.fabricmc.loader.api.ModContainer", """
public record ModContainer(Metadata getMetadata) {
    public record Metadata(String getId, String getName, Version getVersion) {}
    public record Version(String getFriendlyString) {}
    public ModContainer(String id, String name) { this(new Metadata(id, name, new Version("1.0"))); }
}
""")
    stub("net.fabricmc.loader.api.FabricLoader", """
public class FabricLoader {
    public static boolean sodium;
    public static final java.util.List<ModContainer> mods = new java.util.ArrayList<>();
    private static final FabricLoader INSTANCE = new FabricLoader();
    public static FabricLoader getInstance() { return INSTANCE; }
    public boolean isModLoaded(String id) { return id.equals("sodium") && sodium; }
    public java.util.List<ModContainer> getAllMods() { return mods; }
}
""")
    stub("com.terraformersmc.modmenu.ModMenu", """
public class ModMenu {
    public static final java.util.Set<String> configurable = new java.util.HashSet<>();
    public static String opened;
    public static net.minecraft.client.gui.screens.Screen parent;
    public static boolean hasConfigScreen(String id) { return configurable.contains(id); }
    public static net.minecraft.client.gui.screens.Screen getConfigScreen(String id, net.minecraft.client.gui.screens.Screen origin) {
        opened = id; parent = origin; return new net.minecraft.client.gui.screens.Screen();
    }
}
""")
    stub("com.terraformersmc.modmenu.gui.ModsScreen", "public class ModsScreen extends net.minecraft.client.gui.screens.Screen { public ModsScreen(net.minecraft.client.gui.screens.Screen parent) {} }")
    stub("net.caffeinemc.mods.sodium.client.gui.VideoSettingsScreen", "public class VideoSettingsScreen { public static net.minecraft.client.gui.screens.Screen createScreen(net.minecraft.client.gui.screens.Screen parent) { return new example.SodiumScreen(parent); } }")
    stub(settings + ".YzuiOptionsScreen", """
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
public class YzuiOptionsScreen extends Screen {
    final Screen source, origin;
    final boolean inWorld;
    private final YzuiOptionsScreen root;
    String revealed;
    YzuiOptionsScreen(Screen source, Screen origin, boolean inWorld) {
        this(source, origin, inWorld, null);
    }
    YzuiOptionsScreen(Screen source, Screen origin, boolean inWorld, YzuiOptionsScreen root) {
        this.source = source; this.origin = origin; this.inWorld = inWorld;
        this.root = root;
        if (source != null) YzuiSettingsRouter.remember(source, this);
    }
    Screen origin() { return origin; }
    boolean inWorld() { return inWorld; }
    boolean isUnified() { return source instanceof OptionsScreen; }
    boolean isLinkConfirmation() { return source != null && source.getClass() == net.minecraft.client.gui.screens.ConfirmLinkScreen.class; }
    YzuiOptionsScreen rootSettings() { return isUnified() ? this : root; }
    void reveal(String anchor) { revealed = anchor; }
    static net.minecraft.network.chat.Component text(String key, Object... args) { return net.minecraft.network.chat.Component.translatable("screen.youzaiworldcore.options." + key, args); }
    Screen legacyScreen() { return new OptionsScreen(origin, null); }
}
""")
    stub(settings + ".YzuiGameRulesScreen", """
public class YzuiGameRulesScreen extends net.minecraft.client.gui.screens.options.InWorldGameRulesScreen {
    private final YzuiOptionsScreen parent;
    YzuiGameRulesScreen(YzuiOptionsScreen parent) { this.parent = parent; }
    YzuiOptionsScreen parent() { return parent; }
}
""")
    stub(settings + ".SettingsRouterChecks", r"""
import java.lang.ref.WeakReference;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.*;
import net.minecraft.client.gui.screens.options.*;
import top.csituka.youzaiworldcore.client.config.ClientExternalSettings;

public final class SettingsRouterChecks {
    private static int count;
    private static void check(boolean value, String message) {
        count++;
        if (!value) throw new AssertionError(message);
    }
    public static void main(String[] args) throws Exception {
        var client = Minecraft.getInstance();
        Screen origin = new Screen();
        client.level = new Object();
        OptionsScreen nativeHome = new OptionsScreen(origin, client.options);
        ClientExternalSettings.enabled = false;
        check(YzuiSettingsRouter.resolve(null) == null, "null destination");
        check(YzuiSettingsRouter.resolve(nativeHome) == nativeHome, "disabled native home");
        ClientExternalSettings.enabled = true;
        var home = (YzuiOptionsScreen) YzuiSettingsRouter.resolve(nativeHome);
        check(home.origin() == origin && home.inWorld(), "home return context");
        check(YzuiSettingsRouter.resolve(nativeHome) == home, "native callback returns to host");
        check(YzuiSettingsRouter.resolve(home) == home, "routing is idempotent");
        client.gui.current = home;
        for (Screen page : new Screen[]{new OptionsSubScreen(nativeHome),
                new net.minecraft.client.gui.screens.packs.PackSelectionScreen(),
                new net.minecraft.client.gui.screens.telemetry.TelemetryInfoScreen(),
                new CreditsAndAttributionScreen(), new net.minecraft.client.gui.screens.multiplayer.RestrictionsScreen(),
                new ConfirmScreen(), new UnsupportedGraphicsWarningScreen(), new WinScreen(false)}) {
            var host = (YzuiOptionsScreen) YzuiSettingsRouter.resolve(page);
            check(host.source == page && host.origin() == origin, "settings child context: " + page.getClass());
            check(host.rootSettings() == home, "dedicated workflow keeps unified parent");
        }
        for (Screen page : new Screen[]{new VideoSettingsScreen(home, client, client.options), new SoundOptionsScreen(home, client.options),
                new SkinCustomizationScreen(home, client.options), new ChatOptionsScreen(home, client.options),
                new MouseSettingsScreen(home, client.options), new FontOptionsScreen(home, client.options),
                new AccessibilityOptionsScreen(home, client.options), new OnlineOptionsScreen(home, client.options),
                new net.minecraft.client.gui.screens.options.controls.ControlsScreen(home, client.options), new WorldOptionsScreen(home, client.level)}) {
            check(YzuiSettingsRouter.resolve(page) == home && home.revealed.equals(page.getClass().getName()),
                    "ordinary settings navigate within one screen: " + page.getClass());
        }
        net.fabricmc.loader.api.FabricLoader.sodium = true;
        Screen vanillaVideo = new VideoSettingsScreen(home, client, client.options);
        Screen sodiumVideo = YzuiSettingsRouter.resolve(vanillaVideo);
        check(sodiumVideo instanceof example.SodiumScreen sodium && sodium.parent == home, "installed Sodium receives video navigation and the original parent");
        check(client.gui.current == home, "resolving Sodium does not recursively change the current screen");
        check(YzuiSettingsRouter.resolve(sodiumVideo) == sodiumVideo, "Sodium and addon screens remain native");
        Screen otherVideo = new example.OtherVideo(home);
        check(YzuiSettingsRouter.resolve(otherVideo) == otherVideo, "third-party video subclass remains native");
        ClientExternalSettings.enabled = false;
        check(YzuiSettingsRouter.resolve(vanillaVideo) == vanillaVideo, "YZUI-disabled video routing is unchanged");
        ClientExternalSettings.enabled = true;
        client.gui.current = new TitleScreen();
        Screen directVideo = YzuiSettingsRouter.resolve(new VideoSettingsScreen(nativeHome, client, client.options));
        check(directVideo instanceof example.SodiumScreen sodium && sodium.parent == nativeHome, "direct video shortcut keeps its native parent");
        client.gui.current = home;
        net.fabricmc.loader.api.FabricLoader.sodium = false;
        check(YzuiSettingsRouter.resolve(new VideoSettingsScreen(home, client, client.options)) == home,
                "without Sodium video settings remain inline");
        Screen poem = new WinScreen(true);
        check(YzuiSettingsRouter.resolve(poem) == poem, "end poem is outside settings");
        Screen other = new example.OtherOptions(home);
        check(YzuiSettingsRouter.resolve(other) == other, "third-party subclass remains native");
        Screen otherHome = new example.OtherHome(home);
        check(YzuiSettingsRouter.resolve(otherHome) == otherHome, "third-party options home remains native");

        client.gui.current = new TitleScreen();
        client.level = null;
        var shortcut = (YzuiOptionsScreen) YzuiSettingsRouter.resolve(new AccessibilityOptionsScreen(origin, client.options));
        check(shortcut.origin() == origin && !shortcut.inWorld() && shortcut.isUnified()
                && shortcut.revealed.equals(AccessibilityOptionsScreen.class.getName()), "direct accessibility shortcut reveals unified section");
        OptionsScreen initialized = new OptionsScreen(origin, client.options);
        initialized.nodes.add(new AbstractWidget());
        var refreshed = (YzuiOptionsScreen) YzuiSettingsRouter.resolve(initialized);
        check(refreshed.source != initialized && refreshed.origin() == origin, "hot-enable refreshes old hidden options");

        client.gui.current = home;
        Screen source = new OptionsSubScreen(nativeHome);
        var firstHost = (YzuiOptionsScreen) YzuiSettingsRouter.resolve(source);
        client.gui.current = other;
        check(YzuiSettingsRouter.resolve(source) == firstHost, "third-party return uses existing host");
        var hostsField = YzuiSettingsRouter.class.getDeclaredField("HOSTS");
        hostsField.setAccessible(true);
        Object link = ((Map<?, ?>) hostsField.get(null)).get(source);
        var referenceField = link.getClass().getDeclaredField("screen");
        referenceField.setAccessible(true);
        ((WeakReference<?>) referenceField.get(link)).clear();
        var restored = (YzuiOptionsScreen) YzuiSettingsRouter.resolve(source);
        check(restored != firstHost && restored.source == source && restored.origin() == origin && restored.inWorld(),
                "collected host is restored with return context");
        client.gui.current = home;
        Screen inlineSource = new SoundOptionsScreen(home, client.options);
        YzuiSettingsRouter.resolve(inlineSource);
        Object inlineLink = ((Map<?, ?>) hostsField.get(null)).get(inlineSource);
        ((WeakReference<?>) referenceField.get(inlineLink)).clear();
        var restoredRoot = (YzuiOptionsScreen) YzuiSettingsRouter.resolve(inlineSource);
        check(restoredRoot.isUnified() && restoredRoot.origin() == origin
                && restoredRoot.revealed.equals(SoundOptionsScreen.class.getName()), "collected unified parent restores the whole settings page");

        YzuiSettingsRouter.openCompatibility(home);
        Screen legacy = client.gui.current;
        check(legacy.getClass() == OptionsScreen.class, "explicit compatibility home");
        net.fabricmc.loader.api.FabricLoader.sodium = true;
        Screen compatibilityVideo = new VideoSettingsScreen(legacy, client, client.options);
        check(YzuiSettingsRouter.resolve(compatibilityVideo) == compatibilityVideo, "explicit compatibility flow retains native mod routing");
        net.fabricmc.loader.api.FabricLoader.sodium = false;
        Screen compatibilityChild = new OptionsSubScreen(legacy);
        client.gui.setScreen(compatibilityChild);
        check(client.gui.current == compatibilityChild, "compatibility propagates into native children");
        Screen compatibilitySound = new SoundOptionsScreen(compatibilityChild, client.options);
        client.gui.setScreen(compatibilitySound);
        check(client.gui.current == compatibilitySound, "compatibility bypasses ordinary-page flattening");
        client.gui.setScreen(origin);
        check(client.gui.current == origin, "compatibility returns to custom origin");
        check(YzuiSettingsRouter.resolve(new OptionsScreen(origin, null)) instanceof YzuiOptionsScreen,
                "compatibility does not leak after returning to origin");
        client.gui.current = legacy;
        check(YzuiSettingsRouter.resolve(home) == home, "compatibility returns to MD3 parent");
        ClientExternalSettings.enabled = false;
        check(YzuiSettingsRouter.resolve(home) instanceof OptionsScreen, "hot-disable returns to native options");
        Screen disabledLegacy = YzuiSettingsRouter.resolve(legacy);
        check(disabledLegacy != legacy && ((OptionsScreen) disabledLegacy).getLastScreen() == origin,
                "disabled compatibility rebuilds original behavior");
        ClientExternalSettings.enabled = true;

        client.gui.current = home;
        client.connection = null;
        Screen nativeRules = new InWorldGameRulesScreen();
        check(YzuiSettingsRouter.resolve(nativeRules) == nativeRules, "no rules protocol without a connection");
        client.connection = new Object();
        var rules = (YzuiGameRulesScreen) YzuiSettingsRouter.resolve(nativeRules);
        check(rules.parent() == home, "rules screen preserves packet listener type and parent");
        client.gui.current = rules;
        check(YzuiSettingsRouter.resolve(new ConfirmScreen()) instanceof YzuiOptionsScreen, "rules discard confirmation");
        ClientExternalSettings.enabled = false;
        check(YzuiSettingsRouter.resolve(rules) instanceof OptionsScreen, "disabled rules return chain");

        Screen tree = new Screen(), nested = new Screen();
        AbstractWidget visible = new AbstractWidget(), hidden = new AbstractWidget();
        hidden.visible = false;
        tree.nodes.add(nested); nested.nodes.add(tree); nested.nodes.add(visible); tree.nodes.add(visible); tree.nodes.add(hidden);
        check(SettingsWidgets.flatten(tree).equals(java.util.List.of(visible)), "widget capture ignores cycles, duplicates and hidden entries");
        tree.focused = nested; nested.focused = visible;
        check(SettingsWidgets.focused(tree) == visible, "nested keyboard focus");
        OptionsList pending = new OptionsList(); tree.nodes.add(pending);
        SettingsWidgets.flush(tree);
        check(pending.flushes == 1, "pending vanilla slider values are committed");
        check(SettingsWidgets.read(new WinScreen(true), "poem", Boolean.class), "private primitive data access");
        net.minecraft.util.FormattedCharSequence unicode = sink -> sink.accept(0, null, 0x1F600);
        check(SettingsWidgets.plainText(unicode).equals("\uD83D\uDE00"), "narration conversion preserves code points");
        System.out.println("PASS Java routing and widget contracts: " + count + " assertions");
    }
}
""")
    stub(settings + ".UnifiedSettingsChecks", r"""
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.WorldOptionsScreen;
import net.minecraft.client.gui.screens.options.*;
import top.csituka.youzaiworldcore.client.config.ClientExternalSettings;
import top.csituka.youzaiworldcore.client.screen.CoreSettingsSection;
import top.csituka.youzaiworldcore.client.screen.YouzaiWorldCoreSettingsScreen;

public final class UnifiedSettingsChecks {
    private static int count;
    private static void check(boolean value, String message) { count++; if (!value) throw new AssertionError(message); }
    public static void main(String[] args) {
        var client = Minecraft.getInstance();
        client.level = new Object();
        ClientExternalSettings.enabled = true;
        var home = new OptionsScreen(new Screen(), client.options);
        var host = new YzuiOptionsScreen(home, home.getLastScreen(), true);
        home.init(host.width, host.height);
        var model = new UnifiedSettingsPage(host, home);
        List<SettingsList.Row> rows = new ArrayList<>();
        List<AbstractWidget> footer = new ArrayList<>();
        int before = Screen.created.size();
        model.appendRows(rows, footer);
        List<Screen> models = List.copyOf(Screen.created.subList(before, Screen.created.size()));
        List<AbstractWidget> widgets = rows.stream().flatMap(row -> row.widgets.stream()).toList();
        check(models.size() == 16 && models.stream().allMatch(screen -> screen.additions == 1), "all vanilla, telemetry and Core models are initialized once");
        check(model.video() != null, "without Sodium a native video model is available");
        check(models.stream().noneMatch(OnlineOptionsScreen.class::isInstance) && model.anchorRow(OnlineOptionsScreen.class.getName()) == null, "online model and category are entirely absent");
        for (String hidden : List.of("hidden.highContrast", "hidden.darkLogo", "hidden.narrator", "hidden.narratorHotkey", "options.accessibility.link", "options.telemetry", "OnlineOptionsScreen")) {
            check(widgets.stream().noneMatch(widget -> widget.getMessage().getString().equals(hidden)), "removed option or entry absent: " + hidden);
            var hiddenSearch = new SettingsList(0, 0, 700, 400); hiddenSearch.setRows(rows, hidden);
            check(hiddenSearch.children().isEmpty(), "removed item cannot reappear through global search: " + hidden);
        }
        for (String kept : List.of("kept.blockOutline", "kept.subtitles", "kept.customAccessibility")) {
            check(widgets.stream().anyMatch(widget -> widget.getMessage().getString().equals(kept)), "other accessibility option retained: " + kept);
        }
        var accessibility = models.stream().filter(AccessibilityOptionsScreen.class::isInstance).findFirst().orElseThrow();
        var hiddenNative = (FixtureOptionWidget) SettingsWidgets.flatten(accessibility).stream().filter(widget -> widget.getMessage().getString().equals("hidden.highContrast")).findFirst().orElseThrow();
        client.options.highContrast().set(false); model.tick();
        check(Boolean.TRUE.equals(hiddenNative.displayed), "hidden controls are not retained in synchronization bindings");
        model.flush();
        check(!client.options.highContrast().get() && client.options.darkMojangStudiosBackground().get()
                && client.options.narrator().get() && client.options.narratorHotkey().get(), "removing UI does not reset configuration values");

        check(SettingsWidgets.key(model.anchorRow(FontOptionsScreen.class.getName()).label, "options.language"), "language category title omits the font suffix");
        var videoSubheadings = rows.stream().filter(row -> row.label.getString().startsWith("options.video.")
                && row.label.getString().endsWith(".header")).toList();
        check(videoSubheadings.size() == 3 && videoSubheadings.stream().allMatch(row -> row.heading && !row.navigation
                && row.widgets.isEmpty() && row.children().isEmpty()), "video subcategories are static theme headings without focusable native widgets");
        var nativeVideoHeaders = SettingsWidgets.flatten(model.video()).stream().filter(widget -> widget instanceof net.minecraft.client.gui.components.FocusableTextWidget
                && widget.getMessage().getString().endsWith(".header")).toList();
        check(nativeVideoHeaders.size() == 3 && nativeVideoHeaders.stream().noneMatch(widgets::contains), "all native header widgets are removed from displayed controls");
        check(videoSubheadings.stream().allMatch(row -> !row.label.getStyle().isBold() && !row.label.getStyle().isUnderlined()),
                "theme headings omit native bold and underline formatting");
        check(nativeVideoHeaders.stream().allMatch(widget -> widget.getMessage().getStyle().isBold() && widget.getMessage().getStyle().isUnderlined()),
                "extracting a clean heading does not mutate the native model's text");
        check(widgets.contains(model.video().note), "focusable rich text outside a native header keeps its interaction");
        var headerGraphics = new net.minecraft.client.gui.GuiGraphicsExtractor();
        nativeVideoHeaders.forEach(widget -> widget.setFocused(true));
        videoSubheadings.forEach(row -> { row.setWidth(600); row.setHeight(row.heightFor(600)); row.extractContent(headerGraphics, 0, 0, false, 0); });
        check(nativeVideoHeaders.stream().allMatch(widget -> ((net.minecraft.client.gui.components.FocusableTextWidget) widget).renders == 0), "focusing the original headers never invokes their black-background renderer");
        check(headerGraphics.texts.stream().map(net.minecraft.client.gui.GuiGraphicsExtractor.Text::value).toList()
                .equals(List.of("options.video.display.header", "options.video.quality.header", "options.video.preferences.header")), "theme renderer retains all video subgroup names and order");
        var videoSearch = new SettingsList(0, 0, 700, 400);
        videoSearch.setRows(rows, "", true, model.anchorRow(VideoSettingsScreen.class.getName()));
        videoSearch.filter("options.video.quality.header");
        check(videoSearch.children().size() == 1 && videoSearch.children().getFirst().widgets.getFirst().getMessage().getString().equals("video.section.option.1"),
                "subgroup search still finds its associated options");
        check(footer.size() == 1 && SettingsWidgets.key(footer.getFirst().getMessage(), "gui.done"), "one shared footer");
        for (String hidden : List.of("options.video", "options.sounds", "options.controls", "options.chat", "options.accessibility",
                "options.online", "options.skinCustomisation", "options.worldOptions.button", "options.mouse_settings")) {
            check(widgets.stream().noneMatch(widget -> SettingsWidgets.key(widget.getMessage(), hidden)), "ordinary category button removed: " + hidden);
        }
        for (String kept : List.of("options.language", "options.resourcepack", "options.credits_and_attribution", "thirdparty.entry", "controls.keybinds")) {
            check(widgets.stream().anyMatch(widget -> SettingsWidgets.key(widget.getMessage(), kept)), "special workflow or third-party entry retained: " + kept);
        }
        for (String value : List.of("VideoSettingsScreen.value", "SoundOptionsScreen.value", "MouseSettingsScreen.value",
                "SkinCustomizationScreen.value", "FontOptionsScreen.value", "ChatOptionsScreen.value",
                "core.0", "core.1", "core.2", "core.3", "core.4", "map.youzaiworldcore.option.coordinates", "telemetry.opt_in")) {
            check(widgets.stream().anyMatch(widget -> widget.getMessage().getString().equals(value)), "actual setting is present on root: " + value);
        }
        List<SettingsList.Row> coreHeadings = rows.stream().filter(row -> row.navigation
                && model.anchorKey(row) != null && model.anchorKey(row).startsWith("core.")).toList();
        check(coreHeadings.stream().map(row -> model.anchorKey(row)).toList()
                .equals(List.of("core.0", "core.4", "core.5", "core.1", "core.2", "core.3")),
                "Core categories have the shared player-first order");
        check(coreHeadings.getFirst().navigationGroup.getString().equals("YouzaiWorldCore"), "Core group starts at appearance");
        for (CoreSettingsSection section : CoreSettingsSection.values()) {
            var anchor = model.anchorRow("core." + section.id());
            check(anchor != null && anchor.label.equals(section.title()) && anchor != model.anchorRow(VideoSettingsScreen.class.getName()),
                    "Core category has its own name and navigation anchor: " + section);
            var categoryList = new SettingsList(0, 0, 700, 400);
            categoryList.setRows(rows, "", true, anchor);
            var names = categoryList.children().stream().flatMap(row -> row.widgets.stream()).map(widget -> widget.getMessage().getString()).toList();
            check(section == CoreSettingsSection.MAP ? names.contains("map.youzaiworldcore.option.coordinates")
                    : section == CoreSettingsSection.APPEARANCE ? names.contains("core.0") && names.contains("screen.youzaiworldcore.appearance.theme")
                    : names.equals(List.of("core." + section.id())), "selecting a Core category excludes other categories: " + section);
            categoryList.filter(section == CoreSettingsSection.MAP ? "map.youzaiworldcore.option.coordinates" : "core." + section.id());
            check(categoryList.children().size() == 1 && categoryList.activeSection() == anchor, "search retains the category owner: " + section);
        }
        var nativeVideoList = new SettingsList(0, 0, 700, 400);
        nativeVideoList.setRows(rows, "", true, model.anchorRow(VideoSettingsScreen.class.getName()));
        check(nativeVideoList.children().stream().flatMap(row -> row.widgets.stream())
                .noneMatch(widget -> widget.getMessage().getString().startsWith("core.")), "native video excludes Core settings");
        List<SettingsList.Row> shared = rows.stream().filter(row -> row.widgets.stream()
                .anyMatch(widget -> widget.getMessage().getString().equals("shared.control"))).toList();
        check(shared.size() == 1, "duplicate native option is shown once");
        var searchList = new SettingsList(0, 0, 700, 400);
        searchList.setRows(rows, "ControlsScreen");
        check(searchList.children().contains(shared.getFirst()), "deduplicated option is searchable in controls");
        searchList.filter("AccessibilityOptionsScreen");
        check(searchList.children().contains(shared.getFirst()), "deduplicated option is searchable in accessibility");
        check(!model.structureChanged(), "initial option structure is stable");
        OptionsSubScreen.SHARED.set(true);
        model.tick();
        check(shared.getFirst().widgets.getFirst() instanceof FixtureOptionWidget widget && Boolean.TRUE.equals(widget.displayed),
                "option changes synchronize the displayed control");
        check(models.stream().allMatch(screen -> screen.ticks == 2), "native tick callbacks keep running");
        @SuppressWarnings("unchecked")
        var slider = (net.minecraft.client.OptionInstance.OptionInstanceSliderButton<Integer>) widgets.stream()
                .filter(widget -> widget.getMessage().getString().equals("shared.slider")).findFirst().orElseThrow();
        slider.edit(7);
        model.flush();
        check(OptionsSubScreen.RANGE.get() == 7 && slider.commits == 1, "pending visible slider is committed exactly once without a stale duplicate overwriting it");
        OptionsSubScreen.RANGE.set(9);
        model.flush();
        check(OptionsSubScreen.RANGE.get() == 9 && slider.commits == 1, "external option updates are not overwritten by inactive slider copies");
        slider.edit(11);
        OptionsSubScreen.RANGE.set(13);
        model.flush();
        check(OptionsSubScreen.RANGE.get() == 13, "a newer preset cancels an older pending slider value");
        var core = (YouzaiWorldCoreSettingsScreen) models.stream().filter(screen -> screen instanceof YouzaiWorldCoreSettingsScreen c
                && c.getSelectedSection() == 1).findFirst().orElseThrow();
        core.operating = true;
        check(model.busyMessage() != null, "Core export locks the unified page");
        core.operating = false;
        check(model.busyMessage() == null, "Core completion releases the lock");
        Screen dynamic = models.stream().filter(screen -> screen instanceof ChatOptionsScreen).findFirst().orElseThrow();
        dynamic.nodes.add(new AbstractWidget(net.minecraft.network.chat.Component.literal("new mod setting")));
        check(model.structureChanged(), "dynamic native or mod controls trigger a refresh");
        rows.clear(); footer.clear(); model.appendRows(rows, footer);
        check(rows.stream().flatMap(row -> row.widgets.stream()).anyMatch(widget -> widget.getMessage().getString().equals("new mod setting")),
                "refresh includes a newly registered control");
        var world = (WorldOptionsScreen) models.stream().filter(screen -> screen instanceof WorldOptionsScreen).findFirst().orElseThrow();
        model.difficultyChanged();
        check(world.difficultyUpdates == 1, "difficulty updates reach native controls");
        model.permissionsChanged();
        check(model.structureChanged(), "permission change requests native control refresh");
        rows.clear(); footer.clear(); model.appendRows(rows, footer);
        check(world.removals == 1 && model.anchorRow(WorldOptionsScreen.class.getName()) != null, "permission refresh replaces only the world model");
        model.removed();
        check(models.stream().allMatch(screen -> screen.removals == 1), "every retained native model is removed exactly once");
        check(client.window.modeUpdates == 1, "leaving commits the fullscreen video mode");
        check(models.stream().flatMap(screen -> screen.children().stream()).filter(OptionsList.class::isInstance)
                .map(OptionsList.class::cast).allMatch(list -> list.flushes == 0), "unified commit does not blindly flush hidden native copies");
        int restart = Screen.created.size();
        OptionsSubScreen.RANGE.set(17);
        net.minecraft.client.gui.screens.telemetry.TelemetryInfoScreen.available = false;
        model.added(); model.flush(); rows.clear(); footer.clear(); model.appendRows(rows, footer);
        check(Screen.created.size() - restart == 16, "returning recreates models from current settings");
        check(OptionsSubScreen.RANGE.get() == 17, "returning from Sodium preserves its latest value");
        check(rows.stream().flatMap(row -> row.widgets.stream()).noneMatch(widget -> widget.getMessage().getString().equals("telemetry.opt_in")),
                "unavailable optional telemetry is not exposed as an editable setting");
        model.removed();
        net.fabricmc.loader.api.FabricLoader.sodium = true;
        int sodiumStart = Screen.created.size();
        model.added(); rows.clear(); footer.clear(); model.appendRows(rows, footer);
        List<Screen> sodiumModels = List.copyOf(Screen.created.subList(sodiumStart, Screen.created.size()));
        check(model.video() == null && sodiumModels.size() == 15, "Sodium replaces the native video model");
        check(sodiumModels.stream().noneMatch(VideoSettingsScreen.class::isInstance), "no native video screen is even constructed with Sodium");
        check(rows.stream().flatMap(row -> row.widgets.stream()).noneMatch(widget -> widget.getMessage().getString().equals("VideoSettingsScreen.value")),
                "native video controls are absent when Sodium is installed");
        var videoAnchor = model.anchorRow(VideoSettingsScreen.class.getName());
        check(videoAnchor != null && rows.contains(videoAnchor) && SettingsWidgets.key(videoAnchor.label, "options.video"),
                "video navigation still has a section for the Sodium entry");
        var sodiumEntries = rows.stream().filter(row -> row.label.equals(YzuiOptionsScreen.text("sodium"))).toList();
        check(sodiumEntries.size() == 1, "video section contains one Sodium entry");
        var sodiumVideoList = new SettingsList(0, 0, 700, 400);
        sodiumVideoList.setRows(rows, "", true, videoAnchor);
        check(sodiumVideoList.children().size() == 2 && sodiumVideoList.children().getLast() == sodiumEntries.getFirst(),
                "Sodium video contains its heading and entry, without Core visual controls");
        check(model.anchorRow("core.0") != videoAnchor && model.anchorRow("core.4") != null && model.anchorRow("core.5") != null,
                "Sodium installation retains independent appearance, HUD and map categories");
        ((top.csituka.youzaiworldcore.client.screen.widget.TransparentButton) sodiumEntries.getFirst().widgets.getFirst()).action.run();
        check(client.gui.current == example.SodiumScreen.last && example.SodiumScreen.last.parent == host,
                "Sodium entry opens the native screen with a return path to unified settings");
        int modeUpdates = client.window.modeUpdates;
        var retiredVideo = models.stream().filter(VideoSettingsScreen.class::isInstance).findFirst().orElseThrow();
        int oldTicks = retiredVideo.ticks;
        model.tick();
        check(retiredVideo.ticks == oldTicks, "retired vanilla video controls no longer receive ticks");
        OptionsSubScreen.RANGE.set(23);
        model.removed();
        check(client.window.modeUpdates == modeUpdates, "Sodium owns fullscreen application without a vanilla duplicate");
        model.added(); OptionsSubScreen.RANGE.set(31); model.flush(); rows.clear(); footer.clear(); model.appendRows(rows, footer);
        check(OptionsSubScreen.RANGE.get() == 31 && model.video() == null, "return from Sodium keeps its saved settings and the replacement entry");
        System.out.println("PASS Unified settings: " + count + " assertions");
    }
}
""")
    stub(settings + ".SettingsStaticTextChecks", r"""
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.*;
import net.minecraft.network.chat.*;
public class SettingsStaticTextChecks {
    private static int count;
    private static void check(boolean value, String message) { count++; if (!value) throw new AssertionError(message); }
    public static void main(String[] args) {
        var worldTitle = new FocusableTextWidget(Component.literal("World general").copy().setStyle(Style.EMPTY.withBold(true).withUnderlined(true)));
        var heading = SettingsList.Row.widget(worldTitle);
        check(heading.heading && !heading.navigation && heading.widgets.isEmpty(), "direct layout category headers are static subheadings");
        check(!heading.label.getStyle().isBold() && !heading.label.getStyle().isUnderlined(), "native category formatting is removed");
        var explanation = new FocusableTextWidget(Component.literal("Read only explanation ".repeat(10)));
        var plain = SettingsList.Row.widget(explanation);
        check(plain.widgets.isEmpty() && !plain.heading && plain.children().isEmpty(), "plain explanations have no clickable or focusable controls");
        var list = new SettingsList(20, 30, 260, 400); list.setRows(List.of(heading, plain), "");
        var g = new GuiGraphicsExtractor(); explanation.setFocused(true); plain.extractContent(g, 0, 0, false, 0);
        check(explanation.renders == 0 && g.texts.stream().map(GuiGraphicsExtractor.Text::value).reduce("", String::concat).equals(explanation.getMessage().getString()),
                "all explanation text renders without its native focus background");
        var message = Component.literal("Read ");
        message.getSiblings().add(Component.literal("privacy information").copy().setStyle(Style.EMPTY.withClickEvent(new Object())));
        check(SettingsWidgets.hasClickableText(message) && !SettingsWidgets.hasClickableText(explanation.getMessage()), "links are detected in nested components");
        int[] clicks = {0};
        var linked = new FocusableTextWidget(message).setComponentClickHandler(style -> clicks[0]++);
        var rich = SettingsList.Row.widget(linked); list.setRows(List.of(rich), "");
        check(rich.widgets.equals(List.of(linked)), "rich text keeps the original interactive control");
        linked.setFocused(true); g.texts.clear(); rich.extractContent(g, linked.getX() + 2, linked.getY() + 2, false, 0);
        check(linked.renders == 0 && g.texts.stream().anyMatch(text -> text.value().equals(message.getString())), "rich text renders through the text collector without native boxes");
        check(g.hoveredTextEffects == GuiGraphicsExtractor.HoveredTextEffects.TOOLTIP_AND_CURSOR, "link hover tooltip and cursor effects are retained");
        linked.mouseClicked(new MouseButtonEvent(linked.getX() + 2, linked.getY() + 2), false);
        linked.keyPressed(new KeyEvent(40, 0, 0));
        check(clicks[0] == 2, "mouse and keyboard still invoke the original link callback");
        var narration = new NarrationElementOutput(); linked.updateNarration(narration);
        check(narration.messages.contains(message.getString()), "interactive text retains narration");
        var action = new Button(Component.literal("Confirm"));
        check(SettingsList.Row.widget(action).widgets.equals(List.of(action)), "action buttons retain their behavior");
        var rules = SettingsList.Row.heading(worldTitle.getMessage());
        check(!rules.label.getStyle().isUnderlined() && rules.widgets.isEmpty(), "game-rule style category titles also use static clean text");
        System.out.println("PASS Actual static and linked settings text: " + count + " assertions (native text layout and callbacks stubbed)");
    }
}
""")
    stub(settings + ".SettingsRowLayoutChecks", r"""
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import top.csituka.youzaiworldcore.client.screen.widget.CheckboxButton;
import top.csituka.youzaiworldcore.client.screen.widget.DropdownButton;
public class SettingsRowLayoutChecks {
    private static int count;
    private static void check(boolean value, String message) { count++; if (!value) throw new AssertionError(message); }
    public static void main(String[] args) {
        int[] changes = {0};
        var toggle = new CheckboxButton(0, 0, 160, 24, Component.literal("Show HUD"), false, () -> changes[0]++);
        var dropdown = new DropdownButton(0, 0, 160, 160, 24, Component.literal("Animation"), List.of("Basic", "Full"), 0, false, index -> {}, null);
        var toggleRow = SettingsList.Row.widget(toggle);
        var choiceRow = SettingsList.Row.controls(Component.empty(), Component.literal("Changes the screen animation"), dropdown);
        var wide = new SettingsList(20, 30, 640, 300); wide.setRows(List.of(toggleRow, choiceRow), "");
        check(toggleRow.label.getString().equals("Show HUD") && choiceRow.label.getString().equals("Animation"), "native and Core option names are separate row labels");
        check(toggle.getWidth() == 44 && toggle.getRight() == toggleRow.getX() + toggleRow.getWidth() - 10, "wide switches are compact and right aligned");
        check(dropdown.getWidth() >= 140 && dropdown.getWidth() <= 216 && dropdown.getRight() == toggle.getRight(), "wide dropdowns share the right column");
        var g = new GuiGraphicsExtractor();
        toggleRow.extractContent(g, 0, 0, false, 0); choiceRow.extractContent(g, 0, 0, false, 0);
        check(g.texts.stream().filter(text -> text.value().equals("Show HUD")).count() == 1
                && g.texts.stream().filter(text -> text.value().equals("Animation")).count() == 1, "names render once rather than inside controls as well");
        check(g.texts.stream().anyMatch(text -> text.value().equals("Basic")), "dropdown current value remains visible");
        check(g.texts.stream().filter(text -> !text.value().equals("Basic") && !text.value().equals("▾"))
                .allMatch(text -> text.x() < dropdown.getX() && text.y() + 9 < choiceRow.getY() + choiceRow.getHeight() - 5), "label and description stay in the text column and row bounds");
        var narration = new NarrationElementOutput(); toggle.updateNarration(narration); dropdown.updateNarration(narration);
        check(narration.messages.contains("Show HUD") && narration.messages.stream().anyMatch(value -> value.contains("Animation")), "hiding visual labels retains narration");
        toggle.mouseClicked(new MouseButtonEvent(toggle.getX() + 10, toggle.getY() + 10), false);
        check(toggle.isChecked() && changes[0] == 1, "compact control remains clickable with one callback");
        var narrow = new SettingsList(20, 30, 260, 300); narrow.setRows(List.of(toggleRow, choiceRow), "");
        g.texts.clear(); choiceRow.extractContent(g, 0, 0, false, 0);
        check(dropdown.getWidth() == choiceRow.getWidth() - 20 && toggle.getWidth() == 44, "narrow dropdown spans the row while switch stays compact");
        check(g.texts.stream().filter(text -> !text.value().equals("Basic") && !text.value().equals("▾"))
                .allMatch(text -> text.y() + 9 < dropdown.getY()), "narrow labels and descriptions stack above controls without overlap");
        var longLabel = Component.literal("Long translated setting ".repeat(15));
        var longToggle = new CheckboxButton(0, 0, 160, 24, longLabel, false, () -> {});
        var longRow = SettingsList.Row.widget(longToggle);
        narrow.setRows(List.of(longRow), ""); g.texts.clear(); longRow.extractContent(g, 0, 0, false, 0);
        check(g.texts.stream().map(GuiGraphicsExtractor.Text::value).reduce("", String::concat).equals(longLabel.getString()), "long translations render completely without duplicate or clipped text");
        check(g.texts.stream().allMatch(text -> text.y() + 9 < longToggle.getY()), "tall translated rows reserve separate space for controls");
        var legacy = new CheckboxButton(0, 0, 200, 24, Component.literal("Legacy label"), false, () -> {});
        g.texts.clear(); legacy.extractRenderState(g, 0, 0, 0);
        check(g.texts.stream().anyMatch(text -> text.value().equals("Legacy label")), "standalone controls retain their default label rendering");
        System.out.println("PASS Actual settings row layout: " + count + " assertions (drawing commands recorded, game rendering stubbed)");
    }
}
""")
    stub(settings + ".SettingsModEntryChecks", r"""
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.loader.api.*;
import com.terraformersmc.modmenu.ModMenu;
import net.minecraft.client.gui.screens.Screen;
import top.csituka.youzaiworldcore.client.screen.widget.TransparentButton;
public class SettingsModEntryChecks {
    private static int count;
    private static void check(boolean value, String message) { count++; if (!value) throw new AssertionError(message); }
    public static void main(String[] args) {
        var parent = new Screen(); FabricLoader.sodium = false;
        for (var mod : List.of(new ModContainer("minecraft", "Renamed game"), new ModContainer("moogs_structures", "Renamed library"),
                new ModContainer("youzaiworldcore", "Core"), new ModContainer("other", "Minecraft"),
                new ModContainer("modmenu", "Mod Menu"), new ModContainer("no_config", "No Config"))) {
            FabricLoader.mods.add(mod);
            if (!mod.getMetadata().getId().equals("no_config")) ModMenu.configurable.add(mod.getMetadata().getId());
        }
        List<SettingsList.Row> rows = new ArrayList<>(); SettingsCompatibility.addModRows(parent, rows);
        check(rows.size() == 3, "installed-mods entry and exactly two eligible config buttons");
        check(rows.getFirst().widgets.size() == 1, "installed-mods browser remains available");
        check(rows.stream().map(row -> row.label.getString()).toList().equals(List.of("", "Mod Menu", "Minecraft")), "IDs filter renamed libraries while preserving another mod with the same display name");
        ((TransparentButton) rows.getLast().widgets.getFirst()).action.run();
        check(ModMenu.opened.equals("other") && ModMenu.parent == parent, "retained config button invokes its own factory with the original parent");
        ((TransparentButton) rows.getFirst().widgets.getFirst()).action.run();
        check(net.minecraft.client.Minecraft.getInstance().gui.screen() instanceof com.terraformersmc.modmenu.gui.ModsScreen, "installed-mods button opens the full browser");
        FabricLoader.sodium = true; rows.clear(); SettingsCompatibility.addModRows(parent, rows);
        check(rows.stream().anyMatch(row -> row.label.getString().startsWith("Sodium /")), "Sodium group remains independent of the hidden IDs");
        System.out.println("PASS Mod settings entries: " + count + " assertions (actual compatibility class, factories stubbed)");
    }
}
""")
    link_source = (OPTIONS / "YzuiOptionsScreen.java").read_text(encoding="utf-8")
    link_methods = []
    for name in ("buildDialog", "isTitle", "addWidgetRow"):
        match = re.search(r"    private [^\n]+\b" + name + r"\([^\n]*\) \{[\s\S]*?\n    \}", link_source)
        assert match, f"未找到链接确认真实方法：{name}"
        link_methods.append(match.group())
    link_predicate = re.search(r"    boolean isLinkConfirmation\(\) \{[^\n]+\}", link_source)
    assert link_predicate
    stub(settings + ".SettingsLinkDialogChecks", r"""
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.*;
import net.minecraft.client.gui.screens.options.UnsupportedGraphicsWarningScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import top.csituka.youzaiworldcore.client.config.ClientExternalSettings;
import top.csituka.youzaiworldcore.client.render.YzuiTheme;
public class SettingsLinkDialogChecks {
    private final Screen source;
    private SettingsLinkDialogChecks(Screen source) { this.source = source; }
    private static int count;
    private static void check(boolean value, String message) { count++; if (!value) throw new AssertionError(message); }
    public static void main(String[] args) {
        var client = net.minecraft.client.Minecraft.getInstance(); var parent = new TitleScreen(); client.gui.current = parent;
        var source = new ConfirmLinkScreen(); ClientExternalSettings.enabled = false;
        check(YzuiSettingsRouter.resolve(source) == source, "disabled YZUI keeps native link confirmation");
        ClientExternalSettings.enabled = true;
        var screen = (YzuiOptionsScreen) YzuiSettingsRouter.resolve(source);
        check(screen.source == source && screen.origin() == parent && screen.isLinkConfirmation(), "link from title page gets the compact popup without requiring a settings parent");
        check(YzuiSettingsRouter.resolve(source) == screen, "native callbacks preserve the existing popup host");
        check(YzuiSettingsRouter.resolve(new example.CustomConfirmLink()) instanceof example.CustomConfirmLink, "custom third-party confirmation screens keep their own layout");
        String url = "https://example.invalid/settings?section=privacy", warning = "Only open links from sources you trust.";
        source.nodes.add(new StringWidget(source.getTitle())); source.nodes.add(new StringWidget(Component.literal(url)));
        source.nodes.add(new StringWidget(Component.literal(warning)));
        int[] callbacks = new int[3]; var buttons = new ArrayList<AbstractWidget>();
        for (int index = 0; index < 3; index++) {
            final int action = index;
            buttons.add(new Button(Component.literal(List.of("Open", "Copy", "Cancel").get(index))) {
                public void onClick(MouseButtonEvent event, boolean doubleClick) { callbacks[action]++; }
            });
        }
        source.nodes.addAll(buttons); var rows = new ArrayList<SettingsList.Row>(); var footer = new ArrayList<AbstractWidget>();
        new SettingsLinkDialogChecks(source).buildDialog(rows, footer);
        check(rows.size() == 2 && rows.stream().allMatch(row -> !row.heading && row.widgets.isEmpty()), "URL and native warning become static wrapped theme text, excluding the duplicate title");
        check(rows.getFirst().detail.getString().equals(url) && rows.getLast().detail.getString().equals(warning), "full URL and security notice are retained");
        check(footer.equals(buttons), "open, copy and cancel retain original native button instances and callbacks");
        screen.width = 900; screen.height = 480; var widgets = new ArrayList<AbstractWidget>();
        var frame = new SettingsFrame(screen, rows, footer, widgets::add, "", 0, false);
        YzuiTheme.cards.clear(); var graphics = new GuiGraphicsExtractor(); frame.render(graphics); var card = YzuiTheme.cards.getLast();
        check(card[2] == 400 && card[3] >= 184 && card[3] < screen.height - 32 && card[0] * 2 + card[2] == screen.width
                && Math.abs(card[1] * 2 + card[3] - screen.height) <= 1, "compact dialog is centered and matches the quit card width and padding");
        check(!widgets.contains(frame.search) && widgets.stream().filter(SettingsList.class::isInstance).count() == 1
                && widgets.size() == 4, "dialog has no search, sidebar or redundant back arrow");
        for (int index = 0; index < buttons.size(); index++) {
            var button = buttons.get(index);
            check(button.getHeight() == 28 && button.getY() == card[1] + card[3] - 52 && button.getX() >= card[0] + 24
                    && button.getRight() <= card[0] + card[2] - 24 && frame.content.getBottom() < button.getY(), "footer button has quit-style dimensions without overlapping content: " + index);
            button.mouseClicked(new MouseButtonEvent(button.getX() + 1, button.getY() + 1), false);
            check(callbacks[index] == 1, "original callback fires once after repositioning: " + index);
        }
        check(graphics.texts.stream().anyMatch(text -> text.value().equals(screen.getTitle().getString())), "wrapped title is rendered within the card");
        rows.clear(); rows.add(SettingsList.Row.text(Component.empty(), Component.literal(url.repeat(50))));
        screen.width = 300; screen.height = 240; widgets.clear();
        var narrow = new SettingsFrame(screen, rows, footer, widgets::add, "", 0, false);
        YzuiTheme.cards.clear(); narrow.render(graphics); card = YzuiTheme.cards.getLast();
        check(card[0] >= 16 && card[1] >= 16 && card[0] + card[2] <= screen.width - 16 && card[1] + card[3] <= screen.height - 16,
                "narrow dialog stays inside the viewport");
        check(narrow.content.maxScrollAmount() > 0 && narrow.content.getBottom() < footer.getFirst().getY(), "long URL scrolls while action buttons remain visible");
        check(footer.stream().allMatch(button -> button.getX() >= 40 && button.getRight() <= screen.width - 40), "all three actions fit the narrow card");
        System.out.println("PASS External link dialog: " + count + " assertions (actual routing, text extraction and frame; native callbacks stubbed)");
    }
""" + "\n".join(link_methods) + "\n" + link_predicate.group() + "\n}\n")
    stub("net.minecraft.client.gui.components.Renderable", "public interface Renderable { void extractRenderState(net.minecraft.client.gui.GuiGraphicsExtractor g, int x, int y, float tick); }")
    stub("net.minecraft.client.gui.navigation.ScreenRectangle", "public record ScreenRectangle(int x, int y, int width, int height) {}")
    stub("net.minecraft.client.gui.components.events.AbstractContainerEventHandler", """
public abstract class AbstractContainerEventHandler implements ContainerEventHandler {
    private GuiEventListener focused;
    public GuiEventListener getFocused() { return focused; }
    public void setFocused(GuiEventListener value) { focused = value; }
    public void setDragging(boolean value) {}
    public boolean isMouseOver(double x, double y) { return false; }
    public net.minecraft.client.gui.navigation.ScreenRectangle getRectangle() { return null; }
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean actual) {
        for (var child : children()) if (child instanceof net.minecraft.client.gui.components.AbstractWidget widget && widget.mouseClicked(event, actual)) { focused = widget; widget.setFocused(true); return true; }
        return false;
    }
    public boolean mouseScrolled(double x, double y, double horizontal, double vertical) { return false; }
    public boolean keyPressed(net.minecraft.client.input.KeyEvent event) { return focused instanceof net.minecraft.client.gui.components.AbstractWidget widget && widget.keyPressed(event); }
}
""")
    stub(PACKAGE + ".client.screen.map.MapAdvancedSettingsScreen", "public class MapAdvancedSettingsScreen extends net.minecraft.client.gui.screens.Screen { public MapAdvancedSettingsScreen(net.minecraft.client.gui.screens.Screen parent) {} }")
    stub(PACKAGE + ".client.screen.map.MapPanelChecks", r"""
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.*;
import top.csituka.youzaiworldcore.client.config.MapSettings;
import top.csituka.youzaiworldcore.client.screen.widget.DropdownButton;
public class MapPanelChecks {
    private static int count;
    private static void check(boolean value, String message) { count++; if (!value) throw new AssertionError(message); }
    public static void run() {
        var owner = new YzWorldMapScreen(new net.minecraft.client.gui.screens.Screen());
        var panel = new MapSettingsPanel(owner); panel.layout(800, 480);
        var dropdown = (DropdownButton) panel.children().stream().filter(DropdownButton.class::isInstance).findFirst().orElseThrow();
        int baseline = MapSettings.saves;
        dropdown.setFocused(true);
        check(dropdown.keyPressed(new KeyEvent(40, 0, 0)) && dropdown.isOpen() && MapSettings.saves == baseline, "marker picker opens without changing display mode");
        panel.extractRenderState(new GuiGraphicsExtractor(), -1, -1, 0);
        check(MapSettings.saves == baseline, "map overlay render and popup layout are read-only");
        check(panel.keyPressed(new KeyEvent(41, 0, 0)) && !dropdown.isOpen() && owner.closeRequests == 0, "first Escape closes only the picker");
        panel.keyPressed(new KeyEvent(41, 0, 0));
        check(owner.closeRequests == 1, "Escape closes settings once the picker is closed");
        MapSettings.setMarkerDisplay(MapSettings.MarkerDisplay.ICONS_AND_LABELS); baseline = MapSettings.saves;
        dropdown.setFocused(true); dropdown.keyPressed(new KeyEvent(40, 0, 0));
        panel.keyPressed(new KeyEvent(82, 0, 0)); panel.keyPressed(new KeyEvent(82, 0, 0)); panel.keyPressed(new KeyEvent(40, 0, 0));
        check(MapSettings.markerDisplay() == MapSettings.MarkerDisplay.HIDDEN && MapSettings.saves == baseline + 1,
                "marker picker selects the requested mode once instead of cycling intermediate values");
        var category = new MapSettingsCategoryScreen(owner, MapSettingsCategoryScreen.Category.APPEARANCE);
        category.onClose();
        check(net.minecraft.client.Minecraft.getInstance().gui.screen() == owner, "map category adapter preserves the existing canvas parent");
        System.out.println("PASS Map panel dropdown: " + count + " assertions (actual panel and category adapter; canvas and event container stubbed)");
    }
}
""")
    stub(settings + ".SettingsMapPanelChecks", "public class SettingsMapPanelChecks { public static void main(String[] args) { top.csituka.youzaiworldcore.client.screen.map.MapPanelChecks.run(); } }")
    stub(settings + ".SettingsUsabilityChecks", r"""
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.*;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.client.input.*;
import net.minecraft.network.chat.Component;
import top.csituka.youzaiworldcore.client.config.MapSettings;
import top.csituka.youzaiworldcore.client.map.MapClient;
import top.csituka.youzaiworldcore.client.screen.widget.*;
public class SettingsUsabilityChecks {
    private static int count;
    private static void check(boolean value, String message) { count++; if (!value) throw new AssertionError(message); }
    private static AbstractWidget control(List<SettingsList.Row> rows, String key) {
        return rows.stream().flatMap(row -> row.widgets.stream()).filter(widget -> widget.getMessage().getString().contains(key)).findFirst().orElseThrow();
    }
    private static void choose(DropdownButton control, int target) {
        control.setFocused(true); control.keyPressed(new KeyEvent(40, 0, 0));
        int current = control.getSelectedIndex();
        for (int index = 0; index < Math.abs(target - current); index++) control.keyPressed(new KeyEvent(target < current ? 82 : 81, 0, 0));
        control.keyPressed(new KeyEvent(40, 0, 0));
    }
    private static TransparentButton nav(SettingsList sidebar, String text) {
        return sidebar.children().stream().flatMap(row -> row.widgets.stream()).filter(TransparentButton.class::isInstance)
                .map(TransparentButton.class::cast).filter(button -> button.getMessage().getString().contains(text)).findFirst().orElseThrow();
    }
    public static void main(String[] args) {
        var parent = new Screen(); var rows = new ArrayList<SettingsList.Row>();
        int baseline = MapSettings.saves;
        rows.add(SettingsList.Row.heading(Component.literal("Appearance"))); SettingsDetails.appearance(rows);
        var map = SettingsList.Row.heading(Component.literal("Map")); rows.add(map); SettingsDetails.map(rows, parent, null);
        check(MapSettings.saves == baseline, "building all searchable map settings does not write configuration");
        var list = new SettingsList(10, 10, 640, 350); list.setRows(rows, "option.coordinates", true, map);
        check(list.children().size() == 1 && list.children().getFirst().widgets.getFirst() instanceof CheckboxButton, "coordinate search finds the real editable switch");
        var graphics = new GuiGraphicsExtractor(); var result = list.children().getFirst();
        result.extractContent(graphics, 0, 0, false, 0);
        check(graphics.texts.stream().anyMatch(text -> text.value().equals("Map › map.youzaiworldcore.map_information")), "search displays the full section and subgroup path");
        check(graphics.texts.stream().filter(text -> text.value().startsWith("map.youzaiworldcore.option.coordinates")).count() == 1,
                "breadcrumb does not duplicate the option name or native control text");
        var toggle = (CheckboxButton) result.widgets.getFirst(); boolean original = MapSettings.enabled(MapSettings.Toggle.COORDINATES);
        toggle.onClick(new MouseButtonEvent(toggle.getX() + 1, toggle.getY() + 1), false);
        check(MapSettings.enabled(MapSettings.Toggle.COORDINATES) != original && MapSettings.saves == baseline + 1, "search result toggles and saves exactly once");
        list.filter("appearance.theme.dark");
        check(list.children().size() == 1 && list.children().getFirst().widgets.getFirst() instanceof DropdownButton, "unselected dark theme is indexed and selectable from search");
        choose((DropdownButton) list.children().getFirst().widgets.getFirst(), 1);
        check(top.csituka.youzaiworldcore.client.config.ClientExternalSettings.getYzuiTheme() == top.csituka.youzaiworldcore.client.config.YzuiThemeMode.DARK,
                "theme search result applies the requested target");
        var shape = (DropdownButton) control(rows, "map.youzaiworldcore.shape"); baseline = MapSettings.saves;
        shape.setFocused(true); shape.keyPressed(new KeyEvent(40, 0, 0)); shape.keyPressed(new KeyEvent(81, 0, 0));
        check(MapSettings.saves == baseline && MapSettings.shape() == MapSettings.Shape.ROUNDED, "opening and navigating map choices does not apply intermediate values");
        shape.keyPressed(new KeyEvent(81, 0, 0)); shape.keyPressed(new KeyEvent(40, 0, 0));
        check(MapSettings.shape() == MapSettings.Shape.SQUARE && MapSettings.saves == baseline + 1, "map shape can be selected directly with one save");
        baseline = MapSettings.saves; choose((DropdownButton) control(rows, "map.youzaiworldcore.layer"), 4);
        check(MapSettings.layer() == top.csituka.youzaiworldcore.map.MapLayer.ROOF && MapSettings.saves == baseline + 1
                && MapClient.sampling == null && MapClient.scanCursor == 0, "direct layer selection preserves sampling reset without saving skipped layers");
        var size = control(rows, "map.youzaiworldcore.size"); size.setRectangle(200, 24, 0, 0); baseline = MapSettings.saves;
        size.onClick(new MouseButtonEvent(100, 10), false);
        check(MapSettings.size() == 160 && MapSettings.saves == baseline + 1, "size slider applies the requested position once");
        var zoom = control(rows, "map.youzaiworldcore.zoom"); zoom.setRectangle(200, 24, 0, 0);
        zoom.onClick(new MouseButtonEvent(130, 10), false);
        check(Math.abs(MapSettings.zoom() - 2.38) < 0.01, "zoom supports values between the old cycle steps");
        baseline = MapSettings.saves; size.extractRenderState(graphics, -1, -1, 0); zoom.extractRenderState(graphics, -1, -1, 0);
        check(MapSettings.saves == baseline, "drawing and synchronization do not rewrite map settings");
        var height = (EditBox) control(rows, "map.youzaiworldcore.fixed_height"); baseline = MapSettings.saves;
        height.setFocused(true); height.setValue("-"); height.keyPressed(new KeyEvent(40, 0, 0));
        check(MapSettings.fixedHeight() == 64 && MapSettings.saves == baseline && height.getValue().equals("64"), "unfinished numeric input preserves configuration and restores the effective value");
        height.setValue("999999"); height.keyPressed(new KeyEvent(40, 0, 0));
        check(MapSettings.fixedHeight() == 64 && MapSettings.saves == baseline, "out-of-range input never relies on silent clamping");
        height.setValue("-128");
        check(MapSettings.saves == baseline, "typing a valid number does not write each intermediate keystroke");
        height.keyPressed(new KeyEvent(40, 0, 0));
        check(MapSettings.fixedHeight() == -128 && MapSettings.saves == baseline + 1, "Enter applies the entered numeric target once");
        var distance = (EditBox) control(rows, "map.youzaiworldcore.waypoint_distance"); baseline = MapSettings.saves;
        distance.setFocused(true); distance.setValue("640"); distance.setFocused(false);
        check(MapSettings.waypointDistance() == 640 && MapSettings.saves == baseline + 1, "leaving a numeric field commits the complete target");
        baseline = MapSettings.saves; SettingsDetails.commitNumbers(rows.stream().flatMap(row -> row.widgets.stream()).toList());
        check(MapSettings.saves == baseline, "closing after a committed field does not save again");
        var mapPage = new YzuiMapSettingsScreen(parent, "APPEARANCE"); mapPage.init(); mapPage.onClose();
        check(net.minecraft.client.Minecraft.getInstance().gui.screen() == parent, "map categories return to their existing parent screen");
        // Use the real frame and sidebar to exercise position restoration, search transitions and folds.
        var host = new YzuiOptionsScreen(new OptionsScreen(parent, null), parent, false); host.width = 800; host.height = 480;
        var navigationRows = new ArrayList<SettingsList.Row>(); var sound = SettingsList.Row.heading(Component.literal("Sound")); sound.sectionId = "sound";
        var video = SettingsList.Row.heading(Component.literal("Video")); video.sectionId = "video";
        var advanced = SettingsList.Row.heading(Component.literal("Developer")); advanced.sectionId = "dev"; advanced.advanced = true;
        for (var category : List.of(sound, video, advanced)) {
            navigationRows.add(category);
            for (int index = 0; index < 30; index++) navigationRows.add(SettingsList.Row.widget(new AbstractWidget(Component.literal(category.label.getString() + " value " + index))));
        }
        var view = new SettingsViewState(); var widgets = new ArrayList<AbstractWidget>();
        var frame = new SettingsFrame(host, navigationRows, List.of(), widgets::add, "", 0, true, sound, view);
        var sidebar = widgets.stream().filter(SettingsList.class::isInstance).map(SettingsList.class::cast).filter(value -> value != frame.content).findFirst().orElseThrow();
        check(sidebar.children().stream().flatMap(row -> row.widgets.stream()).noneMatch(widget -> widget.getMessage().getString().equals("Developer")), "low-frequency category is initially folded");
        frame.content.setScrollAmount(220); nav(sidebar, "Video").action.run(); frame.content.setScrollAmount(75); nav(sidebar, "Sound").action.run();
        check(frame.content.scrollAmount() == 220, "switching back restores Sound's independent scroll position");
        nav(sidebar, "Video").action.run();
        check(frame.content.scrollAmount() == 75, "Video keeps its own position instead of sharing Sound's offset");
        frame.search.setValue("value"); frame.content.setScrollAmount(90); frame.search.setValue("");
        check(frame.content.scrollAmount() == 75 && frame.content.selectedSection() == video, "clearing search restores the original category and position");
        frame.search.setValue("value");
        check(frame.content.scrollAmount() == 90, "repeating the same query restores its search position");
        frame.search.setValue("Developer"); frame.render(graphics);
        check(view.moreExpanded && nav(sidebar, "Developer") != null, "search remains global and automatically reveals a folded result's category");
        nav(sidebar, "Developer").action.run();
        check(frame.content.selectedSection() == advanced && frame.search.getValue().isEmpty(), "folded category remains directly navigable from search");
        nav(sidebar, "options.more").action.run();
        check(!view.moreExpanded && sidebar.getFocused() != null && host.getFocused() == sidebar,
                "folding retains keyboard focus on the disclosure control");
        nav(sidebar, "options.more").action.run();
        check(view.moreExpanded && nav(sidebar, "Developer") != null, "disclosure can reopen low-frequency categories");
        nav(sidebar, "Sound").action.run(); frame.content.setScrollAmount(220); frame.content.rememberView();
        var renamed = SettingsList.Row.heading(Component.literal("声音")); renamed.sectionId = "sound";
        var rebuiltRows = new ArrayList<SettingsList.Row>(); rebuiltRows.add(renamed);
        for (int index = 0; index < 30; index++) rebuiltRows.add(SettingsList.Row.widget(new AbstractWidget(Component.literal("recreated " + index))));
        var rebuilt = new SettingsFrame(host, rebuiltRows, List.of(), widget -> {}, "", 0, true, renamed, view);
        check(rebuilt.content.scrollAmount() == 220, "rebuilding translated controls restores by stable ID instead of row identity or display name");
        System.out.println("PASS Settings usability: " + count + " assertions (actual details, navigation and drawing commands; storage and slider input stubbed)");
    }
}
""")
    stub(settings + ".SettingsNavigationChecks", r"""
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import top.csituka.youzaiworldcore.client.render.YzuiTheme;
import top.csituka.youzaiworldcore.client.screen.widget.TransparentButton;

public final class SettingsNavigationChecks {
    private static int count;
    private static void check(boolean value, String message) { count++; if (!value) throw new AssertionError(message); }
    private static TransparentButton button(SettingsList navigation, Component title) {
        return navigation.children().stream().flatMap(row -> row.widgets.stream()).filter(TransparentButton.class::isInstance)
                .map(TransparentButton.class::cast).filter(button -> button.getMessage().equals(title)).findFirst().orElseThrow();
    }
    private static SettingsList sidebar(List<AbstractWidget> widgets, SettingsFrame frame) {
        return widgets.stream().filter(SettingsList.class::isInstance).map(SettingsList.class::cast)
                .filter(list -> list != frame.content).findFirst().orElseThrow();
    }
    private static boolean selected(SettingsList navigation, SettingsList.Row heading) {
        return button(navigation, heading.label).style == YzuiTheme.ButtonStyle.FILLED;
    }
    public static void main(String[] args) {
        Screen screen = new Screen(); screen.width = 900; screen.height = 420;
        List<SettingsList.Row> rows = new ArrayList<>(), headings = new ArrayList<>();
        for (int index = 0; index < 12; index++) {
            var heading = SettingsList.Row.heading(Component.literal("Section " + index));
            rows.add(heading); headings.add(heading);
            rows.add(SettingsList.Row.controls(index == 4 ? Component.literal("Long setting label ".repeat(28)) : Component.empty(),
                    index == 4 ? Component.literal("Detailed help ".repeat(12)) : Component.empty(),
                    new AbstractWidget(Component.literal("item " + index))));
            if (index == 4) rows.add(SettingsList.Row.subheading(Component.literal("nested group")));
        }
        List<AbstractWidget> chrome = new ArrayList<>();
        var frame = new SettingsFrame(screen, rows, List.of(), chrome::add, "", 0, true);
        var navigation = sidebar(chrome, frame);
        var g = new GuiGraphicsExtractor();
        check(navigation.children().size() == 12, "sidebar contains only actual sections");
        check(selected(navigation, headings.getFirst()), "initial highlight reflects the first actual section");
        check(navigation.children().getFirst().widgets.getFirst().getMessage().equals(headings.getFirst().label),
                "the first sidebar item is the first settings section, without All");
        check(headings.get(1).getY() < frame.content.getBottom(), "next section is already visible before navigation");
        button(navigation, headings.get(1).label).action.run();
        check(headings.get(1).getY() == frame.bodyY + 2, "clicking an already visible section still aligns its title to the top");
        check(selected(navigation, headings.get(1)) && !selected(navigation, headings.getFirst()), "click switches the sidebar highlight");
        button(navigation, headings.get(6).label).action.run();
        check(headings.get(6).getY() == frame.bodyY + 2, "navigation uses actual variable row heights, including wrapped text");
        check(selected(navigation, headings.get(6)), "distant click selects its own section");
        button(navigation, headings.getFirst().label).action.run();
        check(frame.content.scrollAmount() == 0 && selected(navigation, headings.getFirst()), "first-section button returns to the top");
        frame.content.setScrollAmount(headings.get(6).getY() - frame.bodyY - 2);
        frame.render(g);
        check(selected(navigation, headings.get(6)), "scroll position alone updates the highlight without a sidebar click");
        frame.content.setScrollAmount(frame.content.scrollAmount() + 36);
        frame.render(g);
        check(selected(navigation, headings.get(6)), "scrolling within section controls retains their owning section");
        frame.content.mouseScrolled(frame.bodyX + 20, frame.bodyY + 20, 0, -1000);
        frame.render(g);
        check(selected(navigation, headings.getLast()), "wheel scrolling to the bottom selects the final short section");
        var lastButton = button(navigation, headings.getLast().label);
        check(navigation.scrollAmount() > 0 && lastButton.getY() >= navigation.getY() && lastButton.getBottom() <= navigation.getBottom(),
                "sidebar scrolls just enough to reveal the newly active section");
        navigation.setScrollAmount(0); frame.render(g);
        check(navigation.scrollAmount() == 0, "unchanged content section does not fight manual sidebar scrolling");
        frame.content.mouseScrolled(frame.bodyX + 20, frame.bodyY + 20, 0, 1000);
        frame.render(g);
        check(selected(navigation, headings.getFirst()), "reverse wheel scrolling restores the first-section highlight");
        button(navigation, headings.get(10).label).action.run();
        frame.render(g);
        check(frame.content.scrollAmount() == frame.content.maxScrollAmount() && selected(navigation, headings.get(10)),
                "a bottom-clamped click retains the requested visible section");
        frame.content.setScrollAmount(frame.content.maxScrollAmount()); frame.render(g);
        check(selected(navigation, headings.getLast()), "manual scrolling releases the clamped click selection");
        frame.search.setValue("item 8"); frame.render(g);
        check(frame.content.children().size() == 1 && !frame.content.children().getFirst().heading,
                "search can hide headings while retaining a matching control");
        check(selected(navigation, headings.get(8)), "filtered results still highlight their original section");
        frame.search.setValue("does-not-exist"); frame.render(g);
        check(frame.content.children().isEmpty() && headings.stream().noneMatch(heading -> selected(navigation, heading)),
                "empty results clear the stale section highlight");
        button(navigation, headings.get(6).label).action.run();
        check(frame.search.getValue().isEmpty() && headings.get(6).getY() == frame.bodyY + 2 && selected(navigation, headings.get(6)),
                "section click clears a filter before calculating its actual position");
        frame.search.setValue("item"); frame.content.setScrollAmount(frame.content.maxScrollAmount()); frame.render(g);
        check(selected(navigation, headings.getLast()), "multi-section search highlights the last result group at the bottom");
        frame.search.setValue(""); frame.content.reveal(headings.get(5));
        double savedScroll = frame.content.scrollAmount();
        List<AbstractWidget> restoredChrome = new ArrayList<>();
        var restored = new SettingsFrame(screen, rows, List.of(), restoredChrome::add, "", savedScroll, true);
        check(selected(sidebar(restoredChrome, restored), headings.get(5)), "restoring scroll also restores the active sidebar section");
        screen.width = 700;
        List<AbstractWidget> resizedChrome = new ArrayList<>();
        var resized = new SettingsFrame(screen, rows, List.of(), resizedChrome::add, "", savedScroll, true);
        var resizedNavigation = sidebar(resizedChrome, resized);
        button(resizedNavigation, headings.get(5).label).action.run();
        check(headings.get(5).getY() == resized.bodyY + 2 && selected(resizedNavigation, headings.get(5)),
                "resized and rewrapped rows use their new navigation coordinates");
        double beforeMissing = resized.content.scrollAmount();
        resized.content.reveal(SettingsList.Row.heading(Component.literal("missing")));
        check(resized.content.scrollAmount() == beforeMissing, "a stale or filtered-out row cannot move the list");
        screen.width = 500;
        List<AbstractWidget> narrowChrome = new ArrayList<>();
        var narrow = new SettingsFrame(screen, rows, List.of(), narrowChrome::add, "", 0, true);
        narrow.content.reveal(headings.get(5)); narrow.render(g);
        check(narrowChrome.stream().filter(SettingsList.class::isInstance).count() == 1 && narrow.content.activeSection() == headings.get(5),
                "narrow layouts retain anchor navigation without a sidebar");
        System.out.println("PASS Actual settings list and sidebar: " + count + " assertions (native layout and rendering stubbed)");
    }
}
""")
    stub(settings + ".SettingsChoiceChecks", r"""
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import top.csituka.youzaiworldcore.client.screen.widget.CheckboxButton;
import top.csituka.youzaiworldcore.client.screen.widget.DropdownButton;

public final class SettingsChoiceChecks {
    private static int count;
    private static void check(boolean value, String message) { count++; if (!value) throw new AssertionError(message); }
    private static final class Values<T> implements CycleButton.ValueListSupplier<T> {
        private final List<T> defaults;
        private List<T> selected;
        @SafeVarargs private Values(T... values) { defaults = List.of(values); selected = defaults; }
        public List<T> getDefaultList() { return defaults; }
        public List<T> getSelectedList() { return selected; }
    }
    private static MouseButtonEvent click(AbstractWidget widget) { return new MouseButtonEvent(widget.getX() + 4, widget.getY() + 4); }
    public static void main(String[] args) {
        var booleanValues = new Values<>(true, false);
        List<Boolean> changes = new ArrayList<>();
        var original = new CycleButton<>(Component.literal("Flag"), booleanValues,
                value -> Component.translatable(value ? "options.on" : "options.off"), false, (button, value) -> changes.add(value));
        var flagRow = SettingsList.Row.widget(original);
        check(flagRow.widgets.getFirst() instanceof CheckboxButton, "on/off options use the same toggle class as Core");
        var toggle = (CheckboxButton) flagRow.widgets.getFirst();
        var qualityValues = new Values<>("low", "medium", "ultra");
        List<String> qualityChanges = new ArrayList<>();
        List<CycleButton<String>> callbackSources = new ArrayList<>();
        var quality = new CycleButton<>(Component.literal("Quality"), qualityValues, Component::literal, "low",
                (button, value) -> { callbackSources.add(button); qualityChanges.add(value); });
        var qualityRow = SettingsList.Row.widget(quality);
        check(qualityRow.widgets.getFirst() instanceof DropdownButton, "multiple values use the Core dropdown class");
        var dropdown = (DropdownButton) qualityRow.widgets.getFirst();
        var list = new SettingsList(20, 30, 640, 320);
        list.setRows(List.of(SettingsList.Row.heading(Component.literal("Settings")), flagRow, qualityRow), "");
        check(!toggle.isChecked() && dropdown.getSelectedIndex() == 0, "initial state is copied without applying settings");
        check(changes.isEmpty() && qualityChanges.isEmpty(), "creating controls has no configuration side effects");
        check(toggle.mouseClicked(click(toggle), false) && original.getValue() && toggle.isChecked(), "mouse toggle updates native and displayed values");
        check(changes.equals(List.of(true)) && original.applications == 1, "toggle invokes exactly one native callback");
        original.setValue(false); flagRow.children();
        check(!toggle.isChecked() && changes.size() == 1, "external reset updates the switch without another callback");
        original.active = false; flagRow.children();
        check(!toggle.active && !toggle.mouseClicked(click(toggle), false) && changes.size() == 1, "disabled options cannot be changed");
        original.active = true; original.visible = false; flagRow.children();
        check(!toggle.visible && !toggle.mouseClicked(click(toggle), false), "native visibility is preserved");
        original.visible = true; original.setAlpha(0.6f); original.setTabOrderGroup(3);
        var tooltip = Tooltip.create(Component.literal("state help")); original.setTooltip(tooltip); flagRow.children();
        check(SettingsWidgets.read(toggle, "tooltip", WidgetTooltipHolder.class).get() == tooltip
                && toggle.getAlpha() == 0.6f && toggle.getTabOrderGroup() == 3, "tooltip, alpha and tab order follow the native control");
        toggle.setFocused(true);
        check(toggle.keyPressed(new KeyEvent(44, 0, 0)) && original.getValue() && changes.equals(List.of(true, true)),
                "Space toggles the setting through its real keyboard handler");
        var narration = new NarrationElementOutput(); toggle.updateNarration(narration);
        check(narration.messages.stream().anyMatch(message -> message.contains("Flag") && message.contains("options.on")),
                "toggle narration includes both option name and current state");
        check(dropdown.mouseClicked(click(dropdown), false) && dropdown.isOpen() && qualityChanges.isEmpty(),
                "opening a dropdown does not cycle or save a value");
        list.renderPopups(new GuiGraphicsExtractor(), -1, -1, 0);
        int popupX = SettingsWidgets.read(dropdown, "popupLeft", Integer.class);
        int popupY = SettingsWidgets.read(dropdown, "popupTop", Integer.class);
        check(list.popupClicked(new MouseButtonEvent(popupX + 10, popupY + 2 + 24 * 2 + 10), false),
                "popup hit testing reaches an option outside its source row");
        check(quality.getValue().equals("ultra") && dropdown.getSelectedIndex() == 2 && !dropdown.isOpen(),
                "mouse selection applies the chosen value and closes the menu");
        check(qualityChanges.equals(List.of("ultra")) && quality.applications == 1 && callbackSources.getFirst() == quality,
                "direct selection calls the original callback once without passing through intermediate choices");
        dropdown.setFocused(true); dropdown.keyPressed(new KeyEvent(40, 0, 0)); dropdown.keyPressed(new KeyEvent(40, 0, 0));
        check(qualityChanges.size() == 1, "selecting the existing value does not reapply the setting");
        dropdown.keyPressed(new KeyEvent(40, 0, 0)); dropdown.keyPressed(new KeyEvent(82, 0, 0));
        dropdown.keyPressed(new KeyEvent(82, 0, 0)); dropdown.keyPressed(new KeyEvent(40, 0, 0));
        check(quality.getValue().equals("low") && qualityChanges.equals(List.of("ultra", "low")), "keyboard arrows select a target without intermediate writes");
        dropdown.keyPressed(new KeyEvent(40, 0, 0));
        check(list.popupKeyPressed(new KeyEvent(41, 0, 0)) && !dropdown.isOpen() && qualityChanges.size() == 2,
                "Escape closes a menu without changing the setting");
        quality.setValue("custom"); qualityRow.children();
        check(dropdown.getSelectedIndex() == -1 && SettingsWidgets.read(dropdown, "currentValueLabel", String.class).equals("custom"),
                "a current custom value remains visible even when it is not selectable");
        check(qualityChanges.size() == 2, "synchronizing a preset never calls the native change callback");
        dropdown.keyPressed(new KeyEvent(40, 0, 0)); qualityValues.selected = List.of("medium", "ultra"); qualityRow.children();
        check(!dropdown.isOpen() && SettingsWidgets.read(dropdown, "options", List.class).equals(List.of("medium", "ultra")),
                "dynamic allowed values replace the menu and close stale choices");
        qualityValues.selected = qualityValues.defaults; quality.setValue("low"); qualityRow.children();
        dropdown.keyPressed(new KeyEvent(40, 0, 0)); dropdown.keyPressed(new KeyEvent(81, 0, 0));
        qualityValues.selected = List.of("ultra"); dropdown.keyPressed(new KeyEvent(40, 0, 0));
        check(quality.getValue().equals("low") && qualityChanges.size() == 2, "a stale selection is revalidated before applying");
        qualityValues.selected = List.of(); qualityRow.children();
        check(!dropdown.active && !dropdown.keyPressed(new KeyEvent(40, 0, 0)) && !dropdown.isOpen(), "empty choice ranges are disabled");
        qualityValues.selected = qualityValues.defaults; quality.setValue("ultra"); qualityRow.children(); list.filter("quality ultra");
        check(list.children().equals(List.of(qualityRow)), "search still includes a dropdown's current value");
        var mode = new CycleButton<>(Component.literal("Sneak"), booleanValues, value -> Component.literal(value ? "Toggle" : "Hold"),
                false, (button, value) -> {});
        check(SettingsList.Row.widget(mode).widgets.getFirst() instanceof DropdownButton, "Hold/Toggle is a choice rather than an on/off switch");
        var rejected = new CycleButton<>(Component.literal("Restricted"), booleanValues,
                value -> Component.translatable(value ? "options.on" : "options.off"), false, (button, value) -> button.setValue(false));
        var rejectedToggle = (CheckboxButton) SettingsChoiceControl.adapt(rejected);
        rejectedToggle.setRectangle(100, 24, 0, 0); rejectedToggle.mouseClicked(click(rejectedToggle), false);
        check(!rejectedToggle.isChecked() && !rejected.getValue(), "native callback corrections immediately synchronize the switch");
        var ordinary = new Button(Component.literal("Open screen"));
        var core = new CheckboxButton(0, 0, 100, 24, Component.literal("Core"), false, () -> {});
        check(SettingsChoiceControl.adapt(ordinary) == ordinary && SettingsChoiceControl.adapt(core) == core,
                "action buttons and existing Core switches are preserved");
        var replacement = SettingsList.Row.widget(original);
        var frame = new SettingsFrame(new Screen(), List.of(replacement), List.of(), widget -> {}, "", 0, true);
        frame.restoreFocus(toggle);
        check(replacement.getFocused() == replacement.widgets.getFirst(), "rebuilding an adapter restores focus through the native control identity");
        System.out.println("PASS Actual settings toggles and dropdowns: " + count + " assertions (native callbacks and rendering stubbed)");
    }
}
""")
    # 执行语言页的真实收集、选择与搜索逻辑；原版应用回调使用替身。
    options_source = (OPTIONS / "YzuiOptionsScreen.java").read_text(encoding="utf-8")
    language_methods = []
    for name in ("collectRows", "isTitle", "takeFooter", "addWidgetRow", "buildLanguages", "action"):
        match = re.search(r"    private [^\n]+\b" + name + r"\([^\n]*\) \{[\s\S]*?\n    \}", options_source)
        assert match, f"未找到语言页真实方法：{name}"
        language_methods.append(match.group())
    stub("net.minecraft.client.resources.language.LanguageManager", """
public class LanguageManager {
    public record LanguageInfo(String name) { public net.minecraft.network.chat.Component toComponent() { return net.minecraft.network.chat.Component.literal(name); } }
    public String selected = "en_us";
    public int applications;
    public java.util.Map<String, LanguageInfo> getLanguages() { return java.util.Map.of("en_us", new LanguageInfo("English"), "zh_cn", new LanguageInfo("简体中文")); }
}
""")
    stub(screens + ".options.LanguageSelectScreen", """
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
public class LanguageSelectScreen extends OptionsSubScreen {
    private LanguageSelectionList languageSelectionList;
    public final Button done = new Button(Component.translatable("gui.done")) {
        public void onClick(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
            var manager = net.minecraft.client.Minecraft.getInstance().getLanguageManager();
            manager.selected = languageSelectionList.getSelected().code; manager.applications++;
        }
    };
    public LanguageSelectScreen() { super(new net.minecraft.client.gui.screens.Screen()); }
    public void init(int width, int height) {
        nodes.clear(); languageSelectionList = new LanguageSelectionList();
        nodes.add(new StringWidget(getTitle())); nodes.add(new EditBox(net.minecraft.client.Minecraft.getInstance().font, 0, 0, 200, 20, Component.empty()));
        nodes.add(languageSelectionList); nodes.add(new StringWidget(Component.translatable("options.languageAccuracyWarning")));
        nodes.add(new Button(Component.translatable("options.font"))); nodes.add(done);
    }
    public static class LanguageSelectionList extends AbstractSelectionList<LanguageEntry> {
        public LanguageSelectionList() {
            super(net.minecraft.client.Minecraft.getInstance(), 400, 250, 40, 24);
            for (String code : java.util.List.of("en_us", "zh_cn", "unknown")) addEntry(new LanguageEntry(code), 24);
            setSelected(children().getFirst());
        }
    }
    public static class LanguageEntry extends AbstractSelectionList.Entry<LanguageEntry> {
        private final String code;
        public LanguageEntry(String code) { this.code = code; }
        public void extractContent(GuiGraphicsExtractor g, int x, int y, boolean hovered, float tick) {}
    }
}
""")
    stub(screens + ".options.controls.KeyBindsScreen", "public class KeyBindsScreen extends net.minecraft.client.gui.screens.Screen {}")
    stub(screens + ".telemetry.TelemetryEventWidget", "public class TelemetryEventWidget extends net.minecraft.client.gui.components.AbstractWidget {}")
    stub(settings + ".LanguagePageChecks", """
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.LanguageSelectScreen;
import net.minecraft.client.gui.screens.options.controls.KeyBindsScreen;
import net.minecraft.client.gui.screens.packs.PackSelectionScreen;
import net.minecraft.client.gui.screens.telemetry.*;
import net.minecraft.network.chat.Component;
import top.csituka.youzaiworldcore.client.render.YzuiTheme;
import top.csituka.youzaiworldcore.client.screen.widget.TransparentButton;
public class LanguagePageChecks {
    private final Minecraft minecraft = Minecraft.getInstance();
    private final Screen source = new LanguageSelectScreen();
    private String pendingLanguageCode;
    private static int count;
    private static void check(boolean value, String message) { count++; if (!value) throw new AssertionError(message); }
    public static void main(String[] args) {
        var page = new LanguagePageChecks(); page.source.init(800, 480);
        var rows = new ArrayList<SettingsList.Row>(); var footer = new ArrayList<AbstractWidget>();
        page.collectRows(rows, footer);
        var nativePage = (LanguageSelectScreen) page.source;
        check(rows.isEmpty(), "font entry, native search and warning are absent");
        check(footer.equals(List.of(nativePage.done)), "native Done callback is retained exactly once");
        nativePage.done.setMessage(Component.translatable("screen.youzaiworldcore.options.language.apply"));
        page.buildLanguages(rows);
        check(rows.size() == 2 && rows.stream().noneMatch(row -> row.heading), "only known languages, without duplicate title or sidebar");
        check(rows.stream().allMatch(row -> row.label.getString().isEmpty() && row.detail.getString().isEmpty()), "codes are not displayed as separate labels");
        var list = new SettingsList(0, 0, 500, 300); list.setRows(rows, "");
        list.filter("ＥＮ＿ＵＳ"); check(list.children().equals(List.of(rows.getFirst())), "Unicode code search is retained");
        list.filter("简体"); check(list.children().equals(List.of(rows.getLast())), "language name search is retained");
        var english = (TransparentButton) rows.getFirst().widgets.getFirst();
        var chinese = (TransparentButton) rows.getLast().widgets.getFirst();
        check(english.style == YzuiTheme.ButtonStyle.FILLED && chinese.style == YzuiTheme.ButtonStyle.TONAL, "initial selection is highlighted once");
        chinese.action.run();
        check(chinese.style == YzuiTheme.ButtonStyle.FILLED && english.style == YzuiTheme.ButtonStyle.TONAL, "selection updates both styles without rebuilding");
        check(page.minecraft.languages.selected.equals("en_us") && page.minecraft.languages.applications == 0, "choosing a language does not apply it prematurely");
        page.source.init(500, 300); rows.clear(); footer.clear(); page.collectRows(rows, footer);
        check(rows.isEmpty() && footer.equals(List.of(nativePage.done)), "relabeled Apply button remains the native footer after rebuilding");
        page.buildLanguages(rows);
        check(((TransparentButton) rows.getLast().widgets.getFirst()).style == YzuiTheme.ButtonStyle.FILLED, "pending choice survives rebuilding with new native entries");
        nativePage.done.onClick(new net.minecraft.client.input.MouseButtonEvent(0, 0), false);
        check(page.minecraft.languages.selected.equals("zh_cn") && page.minecraft.languages.applications == 1, "Done applies the pending native selection exactly once");
        System.out.println("PASS Actual language page: " + count + " assertions (native application stubbed)");
    }
""" + "\n".join(language_methods) + "\n}")

    # 执行真实分类导航和内容构建方法；配置存储、编辑器与绘制使用替身。
    core_source = (CLIENT / "client/screen/YouzaiWorldCoreSettingsScreen.java").read_text(encoding="utf-8")
    core_methods = []
    for name in ("sectionOptions", "buildSectionNavigation", "selectSection", "configureEmbedded", "setEmbeddedSection",
                 "buildContentWidgets", "buildVisualSection", "buildHudSection", "buildMapSection"):
        match = re.search(r"    (?:private|public) [^\n]+\b" + name + r"\([^\n]*\) \{[\s\S]*?\n    \}", core_source)
        assert match, f"未找到真实分类方法：{name}"
        core_methods.append(match.group())
    stub(PACKAGE + ".client.config.GuiAnimationMode", "public enum GuiAnimationMode { OFF, BASIC, FULL }")
    stub(PACKAGE + ".client.resource.CustomFontResourcePack", "public class CustomFontResourcePack { public static boolean enabled = true; public static void setEnabled(boolean value) { enabled = value; } }")
    stub(PACKAGE + ".client.screen.widget.TitleScreenTextButton", """
public class TitleScreenTextButton extends TransparentButton {
    public boolean selected;
    public TitleScreenTextButton(int x, int y, int width, int height, net.minecraft.network.chat.Component label, Runnable action) {
        super(x, y, width, height, label, action);
    }
    public void setSelected(boolean value) { selected = value; }
}
""")
    for name in ("YzuiAppearanceScreen", "YzHudSettingsScreen"):
        stub(PACKAGE + ".client.screen." + name, "public class " + name + " extends net.minecraft.client.gui.screens.Screen { public final net.minecraft.client.gui.screens.Screen parent; public "
             + name + "(net.minecraft.client.gui.screens.Screen parent) { this.parent = parent; } }")
    stub(PACKAGE + ".client.map.MapTexts", "public class MapTexts { public static net.minecraft.network.chat.Component text(String key, Object... args) { return net.minecraft.network.chat.Component.translatable(\"map.youzaiworldcore.\" + key, args); } }")
    stub(PACKAGE + ".client.screen.map.YzWorldMapScreen", """
public class YzWorldMapScreen extends net.minecraft.client.gui.screens.Screen {
    public final net.minecraft.client.gui.screens.Screen parent;
    public YzWorldMapScreen(net.minecraft.client.gui.screens.Screen parent) { this.parent = parent; }
    public int closeRequests; public void closeSettings() { closeRequests++; }
    public static YzWorldMapScreen withSettings(net.minecraft.client.gui.screens.Screen parent) { return new YzWorldMapScreen(parent); }
}
""")
    stub(settings + ".CoreCategoryChecks", """
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import top.csituka.youzaiworldcore.client.config.ClientExternalSettings;
import top.csituka.youzaiworldcore.client.config.GuiAnimationMode;
import top.csituka.youzaiworldcore.client.render.YzuiTheme;
import top.csituka.youzaiworldcore.client.resource.CustomFontResourcePack;
import top.csituka.youzaiworldcore.client.screen.*;
import top.csituka.youzaiworldcore.client.screen.widget.*;
import top.csituka.youzaiworldcore.util.DebugLogger;

public final class CoreCategoryChecks extends Screen {
    private static int count;
    private final Screen parentScreen = new Screen();
    private int selectedSection = CoreSettingsSection.APPEARANCE.id();
    private boolean embedded, compactLayout, configOpActive;
    private int embeddedX, embeddedY, embeddedWidth, embeddedHeight;
    private int contentLeft = 160, contentTop = 80, contentWidth = 320, maxContentY;
    private int sidebarX = 12, sidebarY = 80, sectionNavY = 52;
    private static final int SIDEBAR_WIDTH = 120;
    private final List<TitleScreenTextButton> sectionButtons = new ArrayList<>();
    private DropdownButton sectionDropdown, guiAnimationDropdown;
    private boolean yzuiEnabled = true, leftHudEnabled = true, customFontEnabled = true;
    private GuiAnimationMode guiAnimationMode = GuiAnimationMode.FULL;
    private static final List<String> GUI_ANIMATION_OPTIONS = List.of("Off", "Basic", "Full");
    private CoreSettingsSection advanced;
    public <T extends AbstractWidget> T addRenderableWidget(T widget) { nodes.add(widget); return widget; }
    private int wrappedTextHeight(Component text, int width) { return 12; }
    private int checkboxHeight(Component text) { return 24; }
    private void calculateLayout() {}
    private void rebuildWidgets() {
        nodes.clear(); sectionButtons.clear(); sectionDropdown = null; guiAnimationDropdown = null; advanced = null;
        if (!embedded) buildSectionNavigation();
        buildContentWidgets();
    }
    private void buildConfigIoSection(int x, int y) { advanced = CoreSettingsSection.CONFIG_IO; }
    private void buildAboutSection() { advanced = CoreSettingsSection.ABOUT; }
    private void buildDeveloperSection() { advanced = CoreSettingsSection.DEVELOPER; }
""" + "\n".join(core_methods) + """
    private static void check(boolean value, String message) { count++; if (!value) throw new AssertionError(message); }
    private List<AbstractWidget> content() { return nodes.stream().filter(AbstractWidget.class::isInstance).map(AbstractWidget.class::cast)
            .filter(widget -> !sectionButtons.contains(widget) && widget != sectionDropdown).toList(); }
    private AbstractWidget control(String key) { return content().stream().filter(widget -> widget.getMessage().getString().equals(key)).findFirst().orElseThrow(); }
    private void choose(DropdownButton dropdown, int index) {
        dropdown.setFocused(true);
        dropdown.keyPressed(new KeyEvent(40, 0, 0));
        int current = dropdown.getSelectedIndex();
        for (int i = 0; i < Math.abs(index - current); i++) dropdown.keyPressed(new KeyEvent(index < current ? 82 : 81, 0, 0));
        dropdown.keyPressed(new KeyEvent(40, 0, 0));
    }
    public static void main(String[] args) {
        var page = new CoreCategoryChecks();
        var sections = CoreSettingsSection.values();
        ClientExternalSettings.enabled = true;
        page.rebuildWidgets();
        check(page.sectionButtons.size() == 6, "wide Core page has six categories");
        check(page.sectionButtons.getFirst().selected, "appearance is selected initially");
        for (CoreSettingsSection section : sections) {
            var button = page.sectionButtons.get(section.ordinal());
            check(button.getMessage().equals(section.title()), "sidebar name follows shared categories");
            button.action.run();
            check(page.selectedSection == section.id() && page.sectionButtons.get(section.ordinal()).selected,
                    "sidebar selects the stable ID and highlights its position: " + section);
        }
        page.compactLayout = true;
        page.rebuildWidgets();
        check(page.sectionButtons.isEmpty() && page.sectionDropdown.getSelectedIndex() == 5, "narrow page preserves developer selection by navigation index");
        check(page.sectionOptions().equals(Arrays.stream(sections).map(section -> section.title().getString()).toList()),
                "dropdown uses the same translated names and order");
        for (CoreSettingsSection section : sections) {
            page.choose(page.sectionDropdown, section.ordinal());
            check(page.selectedSection == section.id() && page.sectionDropdown.getSelectedIndex() == section.ordinal(),
                    "keyboard dropdown selection maps to stable category ID: " + section);
        }
        page.selectSection(CoreSettingsSection.APPEARANCE);
        check(page.content().size() == 4, "appearance contains only theme, YZUI, animation and font controls");
        var theme = (TransparentButton) page.control("screen.youzaiworldcore.appearance.title");
        theme.action.run();
        check(Minecraft.getInstance().gui.current instanceof YzuiAppearanceScreen editor && editor.parent == page,
                "appearance editor returns to the standalone Core page");
        var ui = (CheckboxButton) page.control("screen.youzaiworldcore.settings.toggle_yzui");
        ui.onClick(null, false);
        check(!ClientExternalSettings.enabled && ClientExternalSettings.uiSaves == 1, "YZUI toggle applies and saves once");
        var font = (CheckboxButton) page.control("screen.youzaiworldcore.settings.toggle_custom_font");
        font.onClick(null, false);
        check(!ClientExternalSettings.font && !CustomFontResourcePack.enabled && ClientExternalSettings.fontSaves == 1,
                "font toggle keeps resource-pack and persisted state together");
        page.choose(page.guiAnimationDropdown, 0);
        check(page.guiAnimationMode == GuiAnimationMode.OFF && ClientExternalSettings.animationSaves == 1, "animation selection saves once");
        page.selectSection(CoreSettingsSection.HUD);
        check(page.content().size() == 2, "HUD contains its display toggle and editor entry");
        ((CheckboxButton) page.control("screen.youzaiworldcore.settings.toggle_left_hud")).onClick(null, false);
        check(!ClientExternalSettings.hud && ClientExternalSettings.hudSaves == 1, "HUD toggle applies and saves once");
        ((TransparentButton) page.control("screen.youzaiworldcore.settings.yzhud_customize")).action.run();
        check(Minecraft.getInstance().gui.current instanceof YzHudSettingsScreen editor && editor.parent == page, "HUD editor returns to its Core category");
        page.selectSection(CoreSettingsSection.MAP);
        check(page.content().size() == 1, "map entry is isolated from appearance and HUD");
        ((TransparentButton) page.content().getFirst()).action.run();
        check(Minecraft.getInstance().gui.current instanceof top.csituka.youzaiworldcore.client.screen.map.YzWorldMapScreen map && map.parent == page,
                "map settings return to their Core category");
        page.compactLayout = false;
        page.rebuildWidgets();
        check(page.sectionButtons.get(2).selected, "returning to wide navigation preserves map selection");
        page.configOpActive = true;
        page.rebuildWidgets();
        check(page.sectionButtons.stream().noneMatch(button -> button.active), "configuration operation disables every sidebar entry");
        page.sectionButtons.getFirst().action.run();
        check(page.selectedSection == CoreSettingsSection.MAP.id(), "busy navigation callback cannot change category");
        page.compactLayout = true;
        page.rebuildWidgets();
        check(!page.sectionDropdown.active, "configuration operation also disables narrow navigation");
        page.configOpActive = false;
        for (CoreSettingsSection section : List.of(CoreSettingsSection.CONFIG_IO, CoreSettingsSection.ABOUT, CoreSettingsSection.DEVELOPER)) {
            page.selectSection(section);
            check(page.advanced == section, "existing advanced category uses its original content: " + section);
        }
        var embedded = new CoreCategoryChecks();
        embedded.configureEmbedded(0, 0, 400, 300, CoreSettingsSection.HUD.id());
        embedded.rebuildWidgets();
        ((TransparentButton) embedded.control("screen.youzaiworldcore.settings.yzhud_customize")).action.run();
        check(Minecraft.getInstance().gui.current instanceof YzHudSettingsScreen editor && editor.parent == embedded.parentScreen,
                "embedded HUD editor returns to the unified settings host");
        embedded.setEmbeddedSection(CoreSettingsSection.MAP.id());
        ((TransparentButton) embedded.content().getFirst()).action.run();
        check(Minecraft.getInstance().gui.current instanceof top.csituka.youzaiworldcore.client.screen.map.YzWorldMapScreen map && map.parent == embedded.parentScreen,
                "embedded map editor returns to the unified settings host");
        embedded.configureEmbedded(0, 0, 200, 300, CoreSettingsSection.APPEARANCE.id());
        check(embedded.selectedSection == CoreSettingsSection.MAP.id(), "resizing embedded content preserves the selected category");
        embedded.configOpActive = true;
        embedded.setEmbeddedSection(CoreSettingsSection.HUD.id());
        check(embedded.selectedSection == CoreSettingsSection.MAP.id(), "busy embedded content cannot change category");
        check(CoreSettingsSection.fromId(-1) == CoreSettingsSection.APPEARANCE && CoreSettingsSection.fromId(99) == CoreSettingsSection.APPEARANCE,
                "unknown category IDs fall back to appearance");
        System.out.println("PASS Actual Core category navigation and content: " + count + " assertions (editors and persistence stubbed)");
    }
}
""")
    mixin = (CLIENT / "mixin/client/ImageButtonYzuiMixin.java").read_text(encoding="utf-8")
    assert "youzaiworldcore$getImageButtonHover().sample(" in mixin, "配方书绘制必须通过初始化入口获取动画"
    field = re.search(r"private YzuiHover youzaiworldcore\$imageButtonHover;", mixin).group()
    method = re.search(r"private YzuiHover youzaiworldcore\$getImageButtonHover\(\) \{[\s\S]*?\n    \}", mixin).group()
    stub(PACKAGE + ".client.animation.YzuiHover", "public final class YzuiHover { public float sample(boolean hover) { return hover ? 1 : 0; } }")
    stub(settings + ".HoverInitializationChecks", """
import top.csituka.youzaiworldcore.client.animation.YzuiHover;
import top.csituka.youzaiworldcore.util.DebugLogger;
public final class HoverInitializationChecks {
""" + field + "\n" + method + """
    public static void main(String[] args) {
        var first = new HoverInitializationChecks();
        var hover = first.youzaiworldcore$getImageButtonHover();
        if (hover.sample(false) != 0 || hover.sample(true) != 1) throw new AssertionError("first render without constructor-injected state");
        if (first.youzaiworldcore$getImageButtonHover() != hover) throw new AssertionError("animation state must survive frames");
        if (new HoverInitializationChecks().youzaiworldcore$getImageButtonHover() == hover) throw new AssertionError("buttons must not share animation state");
        System.out.println("PASS Recipe-book hover initialization: 3 assertions (actual initializer, rendering stubbed)");
    }
}
""")
    for name in ("YzuiSettingsRouter", "SettingsWidgets", "UnifiedSettingsPage", "SettingsList", "SettingsFrame", "SettingsChoiceControl", "SettingsCompatibility", "SettingsViewState", "SettingsDetails", "YzuiMapSettingsScreen"):
        relative = (settings + "." + name).replace(".", "/") + ".java"
        sources[relative] = (OPTIONS / (name + ".java")).read_text(encoding="utf-8")
    for name in ("MapSettingsPanel", "MapSettingsCategoryScreen"):
        relative = (PACKAGE + ".client.screen.map." + name).replace(".", "/") + ".java"
        sources[relative] = (CLIENT / "client/screen/map" / (name + ".java")).read_text(encoding="utf-8")
    for name in ("CheckboxButton", "DropdownButton"):
        relative = (PACKAGE + ".client.screen.widget." + name).replace(".", "/") + ".java"
        sources[relative] = (CLIENT / "client/screen/widget" / (name + ".java")).read_text(encoding="utf-8")
    relative = (PACKAGE + ".client.screen.CoreSettingsSection").replace(".", "/") + ".java"
    sources[relative] = (CLIENT / "client/screen/CoreSettingsSection.java").read_text(encoding="utf-8")
    suffix = ".exe" if sys.platform == "win32" else ""
    javac, java = java_home / ("bin/javac" + suffix), java_home / ("bin/java" + suffix)
    assert javac.is_file() and java.is_file(), "需要 JDK 25"
    with tempfile.TemporaryDirectory(prefix="yzui_settings_checks_") as directory:
        work = Path(directory).resolve()
        assert work.parent == Path(tempfile.gettempdir()).resolve() and work.name.startswith("yzui_settings_checks_")
        files = []
        for relative, source in sources.items():
            path = work / relative
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(source, encoding="utf-8")
            files.append(str(path))
        compile_args = work / "sources.args"
        compile_args.write_text("\n".join('"' + path.replace("\\", "/") + '"' for path in files), encoding="utf-8")
        result = subprocess.run([str(javac), "-encoding", "UTF-8", "--release", "25", "-d", str(work / "classes"),
                                 "@" + str(compile_args)], capture_output=True)
        assert result.returncode == 0, result.stderr.decode("utf-8", errors="replace")
        for check in ("SettingsRouterChecks", "UnifiedSettingsChecks", "SettingsNavigationChecks", "SettingsChoiceChecks", "SettingsStaticTextChecks", "SettingsRowLayoutChecks", "LanguagePageChecks", "CoreCategoryChecks", "HoverInitializationChecks", "SettingsModEntryChecks", "SettingsLinkDialogChecks", "SettingsUsabilityChecks", "SettingsMapPanelChecks"):
            result = subprocess.run([str(java), "-cp", str(work / "classes"), settings + "." + check], capture_output=True)
            assert result.returncode == 0, result.stderr.decode("utf-8", errors="replace")
            print(result.stdout.decode("utf-8").strip())


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--java-home", type=Path, default=Path("C:/Program Files/Java/25"))
    parser.add_argument("--minecraft-jar", type=Path, default=ROOT / f"minecraft_jar/{MINECRAFT_VERSION}/{MINECRAFT_VERSION}.jar")
    parser.add_argument("--mods-dir", type=Path)
    args = parser.parse_args()
    if Path.cwd().resolve() != ROOT:
        raise SystemExit("工作目录不符：请从 YouzaiWorldCore 目录执行")
    check_resources()
    check_core_entry_mixin()
    check_vanilla(args.minecraft_jar)
    if args.mods_dir:
        check_mods(args.mods_dir)
    check_java(args.java_home)


if __name__ == "__main__":
    main()
