"""YZUI 离线检查：资源、Mixin 目标签名，以及实际 Java 配色和坐标变换。

在仓库根目录运行：python tools/verify_yzui.py --java-home "C:/Program Files/Java/25"
不调用 Gradle，不启动 Minecraft，不下载任何文件。
"""

from pathlib import Path
import argparse
import json
import os
import re
import struct
import subprocess
import tempfile
import zipfile
import zlib
from generate_yzui_jade import composite as composite_rgb, contrast as color_contrast

ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / "src/client/java/top/csituka/youzaiworldcore"
GRADLE_PROPERTIES = dict(re.findall(r"^(\w+)=(.+)$", (ROOT / "gradle.properties").read_text(encoding="utf-8"), re.M))
MINECRAFT_VERSION = GRADLE_PROPERTIES["minecraft_version"]
ASSETS = ROOT / "src/main/resources/assets/youzaiworldcore"


def unique_object(pairs):
    result = {}
    for key, value in pairs:
        if key in result:
            raise AssertionError(f"JSON 重复键：{key}")
        result[key] = value
    return result


def read_json(path):
    return json.loads(path.read_text(encoding="utf-8"), object_pairs_hook=unique_object)


def check_resources():
    languages = {path.stem: read_json(path) for path in (ASSETS / "lang").glob("*.json")}
    assert len(languages) == 10, "应有 10 种语言"
    expected_keys = set(languages["zh_cn"])
    for name, language in languages.items():
        assert set(language) == expected_keys, f"{name} 语言键不齐全：{expected_keys ^ set(language)}"
        assert all(isinstance(value, str) for value in language.values()), f"{name} 含非文本翻译"
    for key in expected_keys:
        if key.startswith(("screen.youzaiworldcore.appearance.", "screen.youzaiworldcore.navigation.",
                           "jade.theme.youzaiworldcore.yzui")):
            assert all(language[key].strip() for language in languages.values()), f"空翻译：{key}"

    names = {"yzui", "yzui_dark", "yzui_frosted", "yzui_dark_frosted", "yzui_minimal", "yzui_dark_minimal"}
    assert {p.stem for p in (ASSETS / "jade_themes").glob("yzui*.json")} == names
    sprites = set()

    def visit(value):
        if isinstance(value, dict):
            for child in value.values():
                visit(child)
        elif isinstance(value, list):
            for child in value:
                visit(child)
        elif isinstance(value, str) and value.startswith("youzaiworldcore:yzui"):
            sprites.add(value.partition(":")[2])

    for name in names:
        theme = read_json(ASSETS / "jade_themes" / f"{name}.json")
        assert theme["version"] == 200 and not theme["autoEnable"]
        assert theme["lightColorScheme"] == ("dark" not in name)
        assert theme["styleName"] in expected_keys
        assert theme["text"]["shadow"] is False
        visit(theme)

    assert len(sprites) == 24, "六套 Jade 主题各需四张圆角贴图"
    centers = {}
    for name in sprites:
        path = ASSETS / "textures/gui/sprites" / f"{name}.png"
        assert path.resolve().is_relative_to(ASSETS.resolve())
        content = path.read_bytes()
        assert content[:8] == b"\x89PNG\r\n\x1a\n", path
        offset, compressed, size = 8, bytearray(), None
        while offset < len(content):
            length = struct.unpack_from(">I", content, offset)[0]
            kind = content[offset + 4:offset + 8]
            data = content[offset + 8:offset + 8 + length]
            crc = struct.unpack_from(">I", content, offset + 8 + length)[0]
            assert zlib.crc32(kind + data) & 0xFFFFFFFF == crc, f"PNG 校验失败：{path}"
            if kind == b"IHDR":
                size = struct.unpack(">IIBBBBB", data)
            if kind == b"IDAT":
                compressed.extend(data)
            offset += length + 12
        assert size == (32, 32, 8, 6, 0, 0, 0), f"PNG 尺寸或格式错误：{path}"
        pixels = zlib.decompress(compressed)
        assert len(pixels) == 32 * (32 * 4 + 1), path
        assert all(pixels[row * 129] == 0 for row in range(32)), f"预期无滤波的生成 PNG：{path}"
        center = tuple(pixels[16 * 129 + 65:16 * 129 + 69])
        centers[name] = (center[0] << 16 | center[1] << 8 | center[2], center[3] / 255)
        metadata = read_json(path.with_suffix(".png.mcmeta"))["gui"]["scaling"]
        assert metadata["type"] == "nine_slice"
        assert metadata["width"] == metadata["height"] == 32 and metadata["border"] == 8
    for name in names:
        theme = read_json(ASSETS / "jade_themes" / f"{name}.json")
        surface, opacity = centers[name]
        assert 0.64 <= opacity <= 0.73, f"Jade 主卡片应保持透景：{name}"
        for suffix in ("_nested", "_group", "_slot"):
            assert centers[name + suffix][1] <= 0.15, f"Jade 内层不得重新遮住世界：{name}{suffix}"
        text_colors = [*theme["text"]["colors"].values(), theme["text"]["modNameStyle"]["color"],
                       theme["text"]["itemAmountColor"]]
        for world in (0, 0xFFFFFF):
            base = composite_rgb(surface, world, opacity)
            backgrounds = [base, *[composite_rgb(centers[name + suffix][0], base, centers[name + suffix][1])
                                  for suffix in ("_nested", "_group", "_slot")]]
            for color in text_colors:
                assert all(color_contrast(int(color[1:], 16), background) >= 4.5 for background in backgrounds), (
                    f"Jade 文字对比不足：{name} {color}")
    print(f"资源检查通过：10 种语言（各 {len(expected_keys)} 键）、6 套 Jade 主题、24 张 PNG 与九宫格元数据；透明度和文字对比度通过。")


class ClassSignatures:
    """只读取本地 class 的方法、字段和常量池引用，不加载或运行游戏类。"""

    def __init__(self, data):
        self.data, self.offset = data, 0
        assert self.take(4) == b"\xca\xfe\xba\xbe"
        self.take(4)
        count = self.number(2)
        pool = [None] * count
        index = 1
        while index < count:
            tag = self.number(1)
            if tag == 1:
                pool[index] = self.take(self.number(2)).decode("utf-8", errors="replace")
            elif tag in (7, 8, 16, 19, 20):
                pool[index] = (tag, self.number(2))
            elif tag in (9, 10, 11, 12, 17, 18):
                pool[index] = (tag, self.number(2), self.number(2))
            elif tag in (3, 4):
                self.take(4)
            elif tag in (5, 6):
                self.take(8)
                index += 1
            elif tag == 15:
                self.take(3)
            else:
                raise AssertionError(f"未知常量池类型 {tag}")
            index += 1
        self.references = set()
        for item in pool:
            if isinstance(item, tuple) and item[0] in (9, 10, 11):
                owner = pool[pool[item[1]][1]]
                pair = pool[item[2]]
                self.references.add((owner, pool[pair[1]], pool[pair[2]]))
        self.take(6)
        self.take(2 * self.number(2))
        self.fields, self.methods = set(), set()
        for target in (self.fields, self.methods):
            for _ in range(self.number(2)):
                self.take(2)
                target.add((pool[self.number(2)], pool[self.number(2)]))
                for _ in range(self.number(2)):
                    self.take(2)
                    self.take(self.number(4))

    def take(self, count):
        value = self.data[self.offset:self.offset + count]
        self.offset += count
        return value

    def number(self, count):
        return int.from_bytes(self.take(count), "big")


def check_mixins():
    config = read_json(ROOT / "src/client/resources/youzaiworldcore.client.mixins.json")
    names = config["client"]
    assert len(names) == len(set(names)), "重复注册客户端 Mixin"
    for name in names:
        assert (JAVA / "mixin/client" / (name.replace(".", "/") + ".java")).is_file(), name
    for name in ("TooltipRenderUtilYzuiMixin", "ClientTextTooltipYzuiMixin", "GuiAnimationKeyboardMixin",
                 "GuiRenderStateYzuiOpacityMixin", "PictureInPictureYzuiOpacityMixin"):
        assert name in names, f"缺少注册：{name}"

    # 这些类包含本轮新增或变更的 Minecraft 注入点。
    checked = ["GuiAnimationScreenMixin", "GuiAnimationMouseHandlerMixin", "GuiAnimationGuiGraphicsExtractorMixin",
               "GuiAnimationGuiMixin", "GuiAnimationKeyboardMixin", "MouseHandlerScrollMixin",
               "GuiRenderStateYzuiOpacityMixin", "PictureInPictureYzuiOpacityMixin", "GuiItemRenderStateMixin",
               "MultiLineEditBoxYzuiMixin", "MultiLineEditBoxYzuiCursorMixin", "TooltipRenderUtilYzuiMixin",
               "ClientTextTooltipYzuiMixin", "ScrollAreaYzuiMixin", "AbstractButtonMixin",
               "AbstractSliderButtonMixin", "CycleButtonYzuiMixin", "EditBoxMixin",
               "RecipeBookTabButtonMixin", "ImageButtonYzuiMixin", "RecipeBookBackgroundMixin", "RecipeButtonMixin"]
    jar = ROOT / f"minecraft_jar/{MINECRAFT_VERSION}/{MINECRAFT_VERSION}.jar"
    assert jar.is_file(), f"缺少本地 Minecraft {MINECRAFT_VERSION} 参考 JAR；检查不会下载文件。"
    method_count, reference_count = 0, 0
    with zipfile.ZipFile(jar) as archive:
        for name in checked:
            source = (JAVA / "mixin/client" / f"{name}.java").read_text(encoding="utf-8")
            simple = re.search(r"@Mixin\((\w+)\.class\)", source).group(1)
            owner = re.search(r"import ([\w.]+\." + simple + r");", source).group(1).replace(".", "/")
            signatures = ClassSignatures(archive.read(owner + ".class"))
            for spec in re.findall(r'method\s*=\s*(\{[^}]*\}|"[^"\n]+")', source):
                for target in re.findall(r'"([^"\n]+)"', spec):
                    method_name, _, descriptor_tail = target.partition("(")
                    descriptor = "(" + descriptor_tail if descriptor_tail else None
                    assert any(n == method_name and (descriptor is None or d == descriptor)
                               for n, d in signatures.methods), f"{name} 的目标方法不存在：{target}"
                    method_count += 1
            for target in re.findall(r'target\s*=\s*"L([^;]+);([^"\n]+)"', source):
                target_owner, member = target
                if "(" in member:
                    member_name, tail = member.split("(", 1)
                    descriptor = "(" + tail
                else:
                    member_name, descriptor = member.split(":", 1)
                assert (target_owner, member_name, descriptor) in signatures.references, (
                    f"{name} 的目标类不包含调用或字段引用：{target}")
                reference_count += 1
    print(f"Mixin 静态检查通过：{len(names)} 项注册、{method_count} 个目标方法、{reference_count} 个调用或字段签名；运行时注入仍需游戏验证。")


JAVA_CHECK = r'''
import top.csituka.youzaiworldcore.client.render.YzuiLayout;
import top.csituka.youzaiworldcore.client.render.YzuiPalette;
import top.csituka.youzaiworldcore.client.render.YzuiLayerHistory;
import top.csituka.youzaiworldcore.client.animation.YzuiMotion;
import top.csituka.youzaiworldcore.client.config.YzuiVisualStyle;
import java.util.List;

public final class YzuiInvariantCheck {
    private static void require(boolean condition, String reason) {
        if (!condition) throw new AssertionError(reason);
    }
    private static int composite(int color, int background, double opacity) {
        int result = 0xFF000000;
        for (int shift = 0; shift < 24; shift += 8) {
            int channel = (int) Math.round(((color >>> shift) & 255) * opacity
                    + ((background >>> shift) & 255) * (1 - opacity));
            result |= channel << shift;
        }
        return result;
    }
    private static void checkTranslucency() {
        for (YzuiVisualStyle style : YzuiVisualStyle.values()) {
            require(style.surfaceOpacity() < 0.9f && style.layerOpacity() < 0.6f, "页面与内层均需透景");
            double coverage = 1 - (1 - style.hudPanelOpacity()) * (1 - style.hudSlotOpacity())
                    * (1 - style.hudSelectionOpacity());
            require(coverage < 0.6, "HUD 底板、槽位与选中填充叠加后至少保留四成视野");
            require(style.hudLabelOpacity() <= style.hudSlotOpacity(), "文字底衬不能比 HUD 槽位更实");
            require(style.hudLabelOpacity() > 0 && style.hudLabelOpacity() <= 0.2f, "文字底衬至少保留八成透景");
            double labelCoverage = 1 - (1 - coverage) * (1 - style.hudLabelOpacity());
            require(labelCoverage < 0.67, "文字矩形叠加后仍保留至少三分之一世界画面");
            for (YzuiPalette palette : new YzuiPalette[] {YzuiPalette.LIGHT, YzuiPalette.DARK}) {
                for (float opacity : new float[] {style.surfaceOpacity()}) {
                    float actual = Math.round(opacity * 255) / 255f;
                    for (int color : new int[] {palette.text(), palette.textMuted(), palette.error(), palette.success(),
                            palette.warning(), palette.info(), 0xFFFF55, 0x55FF55, 0xFF5555, 0x5555FF}) {
                        int foreground = palette.translucentText(color, actual);
                        for (int world : new int[] {0, 0xFFFFFF, 0x00FF00, 0xFF0000, 0x0000FF}) {
                            int background = YzuiPalette.composite(palette.surface(), world, actual);
                            require(YzuiPalette.contrast(foreground, background) >= 4.5, "实际透明页面卡片对比度");
                        }
                    }
                }
            }
        }
    }
    private static void checkMotion() {
        require(YzuiMotion.progress(-10, 200) == 0f && YzuiMotion.progress(400, 200) == 1f,
                "过渡进度必须限制在区间内");
        require(YzuiMotion.progress(0, 0) == 1f, "零时长立即完成");
        for (YzuiVisualStyle style : YzuiVisualStyle.values()) {
            for (int[] canvas : new int[][] {{380,280}, {480,360}, {960,540}, {2560,1080}}) {
                float lastOpacity = -1f;
                for (int step = 0; step <= 100; step++) {
                    var frame = YzuiMotion.enter(style, step / 100f, canvas[1]);
                    require(frame.opacity() >= lastOpacity && frame.opacity() <= 1f, "淡入单调且无回弹");
                    require(frame.scale() > 0.97f && frame.scale() <= 1f, "缩放保持轻微");
                    require(frame.offsetY() >= 0f && frame.offsetY() <= 12f, "位移不大幅遮挡底景");
                    for (double x : new double[] {0, 0.5, 37.25, canvas[0] - 1.0}) {
                        double displayed = frame.toScreenX(x, canvas[0]);
                        require(Math.abs(frame.toLocalX(displayed, canvas[0]) - x) < 1e-6, "动画点击 X 往返");
                        double y = x / 2;
                        require(Math.abs(frame.toLocalY(frame.toScreenY(y, canvas[1]), canvas[1]) - y) < 1e-6,
                                "动画点击 Y 往返");
                        double drag = (frame.toScreenX(x + 9.75, canvas[0]) - displayed) / frame.scale();
                        require(Math.abs(drag - 9.75) < 1e-6, "拖拽只缩放差值，不叠加位移");
                    }
                    require(YzuiMotion.exit(style, frame, 0, canvas[1]).equals(frame), "打断淡入后，淡出起点无跳变");
                    lastOpacity = frame.opacity();
                }
                require(YzuiMotion.enter(style, 1, canvas[1]).equals(YzuiMotion.Frame.IDENTITY), "进入终点精确归位");
                var hidden = YzuiMotion.exit(style, YzuiMotion.Frame.IDENTITY, 1, canvas[1]);
                require(style == YzuiVisualStyle.MINIMAL ? hidden.equals(YzuiMotion.Frame.IDENTITY) : hidden.opacity() == 0,
                        "退出终点及低特效立即生效");
            }
        }
    }
    private record Page(String key) { }
    private static void checkLayers() {
        YzuiLayerHistory<Page> history = new YzuiLayerHistory<>();
        java.util.function.BiPredicate<Page, Page> same = (a, b) -> a.key().equals(b.key());
        Page root = new Page("root"), child = new Page("child"), modal = new Page("modal");
        history.change(null, root, true, same);
        history.change(root, child, true, same);
        history.change(child, modal, true, same);
        require(history.parent(modal) == child && history.parents(modal).equals(List.of(root, child)), "背景从根到直接父层绘制");
        Page replacement = new Page("child");
        history.change(modal, replacement, true, same);
        require(history.parent(replacement) == root && history.parents(modal).isEmpty(), "同类返回裁掉子层");
        history.change(replacement, replacement, true, same);
        require(history.parents(replacement).size() == 1, "重复显示不增加层级");
        Page next = replacement;
        for (int i = 0; i < 24; i++) {
            Page previous = next;
            next = new Page("layer" + i);
            history.change(previous, next, true, same);
            require(history.parents(next).size() <= 7, "历史有界，不无限保留页面");
        }
        history.change(next, root, false, same);
        require(history.parents(root).isEmpty(), "无覆盖层的新页面清空旧层级");
        history.change(root, null, false, same);
        require(history.parent(root) == null && history.parents(root).isEmpty(), "关闭页面释放历史");
    }
    public static void main(String[] args) {
        checkTranslucency();
        checkMotion();
        checkLayers();
        require(Math.abs(YzuiPalette.contrast(0, 0xFFFFFF) - 21) < 1e-9, "黑白对比度基准");
        for (YzuiPalette palette : new YzuiPalette[] {YzuiPalette.LIGHT, YzuiPalette.DARK}) {
            require(YzuiPalette.contrast(palette.primary(), palette.onPrimary()) >= 4.5, "主按钮文字");
            require(YzuiPalette.contrast(palette.primaryContainer(), palette.onPrimaryContainer()) >= 4.5, "色调按钮文字");
            require(YzuiPalette.contrast(palette.errorContainer(), palette.error()) >= 4.5, "危险操作文字");
            int[] surfaces = {palette.surface(), palette.surfaceLow(), palette.surfaceHigh(),
                    composite(palette.surface(), 0, 224 / 255.0),
                    composite(palette.surface(), 0xFFFFFF, 224 / 255.0)};
            for (int background : surfaces) {
                require(YzuiPalette.contrast(palette.text(), background) >= 4.5, "正文对比度");
                require(YzuiPalette.contrast(palette.textMuted(), background) >= 4.5, "次要文字对比度");
                for (int semantic : new int[] {palette.primary(), palette.success(), palette.error(), palette.info(), palette.warning()}) {
                    require(YzuiPalette.contrast(semantic, background) >= 4.5, "状态标签与通知文字对比度");
                }
                for (int color : new int[] {0, 0xAAAAAA, 0xFFFFFF, 0xFF5555, 0xFFFF55, 0x55FF55,
                        0x55FFFF, 0x5555FF, 0xFF55FF, 0x00AA00, 0x0000AA, 0xAA00AA, 0xFFAA00}) {
                    int readable = YzuiPalette.readable(color, background);
                    require(YzuiPalette.contrast(readable, background) >= 4.5, "有色名称/稀有度对比度");
                    require((readable >>> 24) == 255, "颜色透明度约定");
                }
            }
        }
        int[][] windows = {{1,1}, {0,0}, {-5,20}, {320,180}, {320,240}, {480,270}, {640,360},
                {800,600}, {854,480}, {960,540}, {1280,720}, {1920,1080}, {3840,2160}, {2560,1080}, {600,1000}};
        for (int[] minimum : new int[][] {{380,280}, {480,360}}) {
            for (int[] window : windows) {
                YzuiLayout layout = YzuiLayout.fit(window[0], window[1], minimum[0], minimum[1]);
                require(layout.scale() > 0 && layout.scale() <= 1, "禁止零比例或强制放大");
                require(layout.width() >= minimum[0] && layout.height() >= minimum[1], "最小逻辑尺寸");
                require(Math.abs(layout.width() * layout.scale() - Math.max(1, window[0])) <= 0.55, "水平贴合");
                require(Math.abs(layout.height() * layout.scale() - Math.max(1, window[1])) <= 0.55, "垂直贴合");
                for (double pointer : new double[] {0, 0.5, 1.25, 37, 159.875, 1000.5}) {
                    require(Math.abs(layout.toLogical(pointer) * layout.scale() - pointer) < 1e-7, "点击坐标往返");
                }
            }
        }
    }
}
'''


def check_java(java_home):
    binary = Path(java_home) / "bin" if java_home else None
    javac = str(binary / "javac.exe") if binary and os.name == "nt" else str(binary / "javac") if binary else "javac"
    java = str(binary / "java.exe") if binary and os.name == "nt" else str(binary / "java") if binary else "java"
    # 临时目录固定创建在仓库内；清理前确认其解析后的绝对路径仍在仓库中。
    with tempfile.TemporaryDirectory(prefix=".tmp-yzui-check-", dir=ROOT) as directory:
        temporary = Path(directory).resolve()
        assert temporary.is_relative_to(ROOT.resolve())
        source = temporary / "YzuiInvariantCheck.java"
        source.write_text(JAVA_CHECK, encoding="utf-8")
        subprocess.run([javac, "--release", "25", "-encoding", "UTF-8", "-d", str(temporary),
                        str(JAVA / "client/render/YzuiPalette.java"), str(JAVA / "client/render/YzuiLayout.java"),
                        str(JAVA / "client/render/YzuiLayerHistory.java"), str(JAVA / "client/animation/YzuiMotion.java"),
                        str(JAVA / "client/config/YzuiVisualStyle.java"),
                        str(source)], cwd=ROOT, check=True)
        subprocess.run([java, "-cp", str(temporary), "YzuiInvariantCheck"], cwd=ROOT, check=True)
    print("Java 检查通过：6 种外观的实际透明度/文字对比、30 组视口、1212 帧动画与点击/拖拽往返、淡入打断连续性、有界页面层级。")


def check_creative_layout():
    source = (JAVA / "client/screen/YzuCreativeInventoryScreen.java").read_text(encoding="utf-8")
    values = {name: int(value) for name, value in re.findall(r"\b([A-Z][A-Z_]+)\s*=\s*(\d+)", source)}
    preview = (values["PM_X"], values["PM_Y"], values["PM_W"], values["PM_H"])
    assert preview[0] + preview[2] <= values["PW"]
    assert preview[1] >= values["SY"] + values["SH"] + 4
    assert preview[1] + preview[3] <= values["INV_Y"] - 8
    slot_size, step = values["SS"], values["SS"] + values["SG"]
    slots = [(values["OFF_X"], values["OFF_Y"])]
    for prefix, rows, columns in (("ARM", 2, 2), ("INV", 3, 9), ("HB", 1, 9)):
        slots.extend((values[prefix + "_X"] + col * step, values[prefix + "_Y"] + row * step)
                     for row in range(rows) for col in range(columns))
    x, y, w, h = preview
    assert all(x + w <= sx or sx + slot_size <= x or y + h <= sy or sy + slot_size <= y for sx, sy in slots)
    assert "enableScissor(lp + PM_X" in source, "玩家预览需限制在独立区域"
    print(f"创造布局检查通过：玩家预览与 {len(slots)} 个装备/副手/物品格均不重叠，搜索框与背包间距通过。")


def compile_client_from_cache(java_home, gradle_cache):
    """Gradle 配置插件缺失时，用已有 Loom 游戏 JAR 补做客户端类型检查。"""
    launch = (ROOT / ".gradle/loom-cache/launch.cfg").read_text(encoding="utf-8")
    game_jars = [Path(value.strip()) for value in re.findall(r"fabric\.gameJarPath(?:\.client)?=(.+)", launch)]
    assert len(game_jars) == 2 and all(path.is_file() for path in game_jars), "缺少现有 Loom 游戏 JAR"
    common_classes = ROOT / "build/classes/java/main"
    assert common_classes.is_dir(), "需要现有通用代码编译输出"
    properties = GRADLE_PROPERTIES
    build_script = (ROOT / "build.gradle").read_text(encoding="utf-8")
    declared = lambda coordinate: re.search(re.escape(coordinate) + r":([^'\"]+)['\"]", build_script).group(1)
    fixed = {
        ("net.fabricmc", "fabric-loader"): properties["loader_version"],
        ("net.fabricmc.fabric-api", "fabric-api"): properties["fabric_version"],
        ("eu.pb4", "trinkets"): properties["trinkets_version"],
        ("com.terraformersmc", "modmenu"): properties["modmenu_version"],
        ("com.geckolib", f"geckolib-fabric-{MINECRAFT_VERSION}"): properties["geckolib_version"],
        ("eu.pb4", "placeholder-api"): declared("eu.pb4:placeholder-api"),
        ("maven.modrinth", "jade"): declared("maven.modrinth:jade"),
    }
    cache = Path(gradle_cache).resolve() / "caches/modules-2/files-2.1"
    selected = {}
    for path in cache.rglob("*.jar"):
        if path.name.endswith(("-sources.jar", "-javadoc.jar")):
            continue
        parts = path.relative_to(cache).parts
        if len(parts) != 5:
            continue
        # Minecraft 26.x 的 LWJGL 核心 Java 类可能使用 unsafe 分类器；其余 native 分类器不含 API。
        java_names = {f"{parts[1]}-{parts[2]}.jar"}
        if parts[:2] == ("org.lwjgl", "lwjgl"):
            java_names.add(f"{parts[1]}-{parts[2]}-unsafe.jar")
        if path.name not in java_names:
            continue
        key = parts[:2]
        if key in fixed and parts[2] != fixed[key]:
            continue
        if key not in selected or path.stat().st_mtime > selected[key].stat().st_mtime:
            selected[key] = path
    assert all(key in selected for key in fixed), f"缺少所声明版本的依赖：{set(fixed) - set(selected)}"
    sources = sorted(JAVA.rglob("*.java"))
    javac = Path(java_home) / "bin" / ("javac.exe" if os.name == "nt" else "javac") if java_home else "javac"
    with tempfile.TemporaryDirectory(prefix=".tmp-yzui-client-check-", dir=ROOT) as directory:
        temporary = Path(directory).resolve()
        assert temporary.is_relative_to(ROOT.resolve())
        classes = temporary / "classes"
        classes.mkdir()
        classpath = os.pathsep.join(str(path) for path in [*game_jars, common_classes, *selected.values()])
        args = ["--release", "25", "-encoding", "UTF-8", "-proc:none", "-implicit:none", "-Xprefer:source",
                "-sourcepath", str(ROOT / "src/main/java"), "-Xmaxerrs", "20", "-d", str(classes),
                "-classpath", classpath, *map(str, sources)]
        argfile = temporary / "compile.args"
        argfile.write_text("\n".join('"' + value.replace("\\", "/").replace('"', '\\"') + '"' for value in args), encoding="utf-8")
        subprocess.run([str(javac), "-J-Duser.language=en", "-J-Duser.country=US", "@" + str(argfile)], cwd=ROOT, check=True)
        print(f"客户端本地编译通过：当前 {len(sources)} 个 Java 源文件，生成 {len(list(classes.rglob('*.class')))} 个临时 class；检查后已清理。")


def main():
    assert Path.cwd().resolve() == ROOT.resolve(), "请在 YouzaiWorldCore 仓库根目录执行"
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--java-home", default=os.environ.get("JAVA_HOME"))
    parser.add_argument("--client-compile-only", action="store_true", help="仅使用已有 Loom/依赖缓存编译客户端，不调用 Gradle")
    parser.add_argument("--gradle-cache", default=str(Path.home() / ".gradle"))
    options = parser.parse_args()
    if options.client_compile_only:
        compile_client_from_cache(options.java_home, options.gradle_cache)
        return
    check_resources()
    check_mixins()
    check_java(options.java_home)
    check_creative_layout()


if __name__ == "__main__":
    main()
