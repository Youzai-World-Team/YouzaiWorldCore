"""编译并执行实际 JadeThemeBridge 的生命周期回归检查。

用依赖替身模拟 Jade 的空主题注册表及懒初始化，不启动游戏或下载依赖。
实际 Jade API 兼容性由客户端编译另行检查。
"""

from pathlib import Path
import argparse
import os
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[1]
CLIENT = ROOT / "src/client/java/top/csituka/youzaiworldcore/client"

STUBS = {
    "net/minecraft/resources/Identifier.java": """
package net.minecraft.resources;
public record Identifier(String namespace, String path) {
    public static Identifier fromNamespaceAndPath(String namespace, String path) {
        return new Identifier(namespace, path);
    }
    public String getNamespace() { return namespace; }
    public String getPath() { return path; }
}
""",
    "snownee/jade/api/JadeIds.java": """
package snownee.jade.api;
import net.minecraft.resources.Identifier;
public interface JadeIds {
    Identifier DEFAULT_THEME = Identifier.fromNamespaceAndPath("jade", "dark");
}
""",
    "snownee/jade/api/theme/Theme.java": """
package snownee.jade.api.theme;
import net.minecraft.resources.Identifier;
public record Theme(Identifier fullId) { }
""",
    "snownee/jade/api/theme/IThemeHelper.java": """
package snownee.jade.api.theme;
import net.minecraft.resources.Identifier;
public interface IThemeHelper {
    static IThemeHelper get() { return regression.JadeBridgeCheck.HELPER; }
    boolean hasTheme(Identifier id);
}
""",
    "snownee/jade/api/config/IWailaConfig.java": """
package snownee.jade.api.config;
import net.minecraft.resources.Identifier;
import snownee.jade.api.theme.Theme;
public interface IWailaConfig {
    static IWailaConfig get() { return regression.JadeBridgeCheck.CONFIG; }
    Overlay overlay();
    void save();
    interface Overlay {
        Theme getTheme();
        void applyTheme(Identifier id);
    }
}
""",
    "top/csituka/youzaiworldcore/client/config/ClientExternalSettings.java": """
package top.csituka.youzaiworldcore.client.config;
public final class ClientExternalSettings {
    public static YzuiThemeMode mode = YzuiThemeMode.LIGHT;
    public static YzuiVisualStyle style = YzuiVisualStyle.STANDARD;
    public static YzuiThemeMode getYzuiTheme() { return mode; }
    public static YzuiVisualStyle getYzuiVisualStyle() { return style; }
}
""",
    "top/csituka/youzaiworldcore/util/DebugLogger.java": """
package top.csituka.youzaiworldcore.util;
public final class DebugLogger {
    public static void info(String module, String format, Object... args) { }
    public static void stateChange(String module, String target, String key, Object... values) { }
}
""",
    "regression/JadeBridgeCheck.java": """
package regression;
import java.util.*;
import net.minecraft.resources.Identifier;
import snownee.jade.api.JadeIds;
import snownee.jade.api.config.IWailaConfig;
import snownee.jade.api.theme.*;
import top.csituka.youzaiworldcore.client.config.*;
import top.csituka.youzaiworldcore.client.jade.JadeThemeBridge;

public final class JadeBridgeCheck {
    public static final FakeHelper HELPER = new FakeHelper();
    public static final FakeConfig CONFIG = new FakeConfig();
    private static final Identifier USER_THEME = id("jade", "waila");
    private static final Identifier[] MANAGED = {
        id("youzaiworldcore", "yzui"), id("youzaiworldcore", "yzui_frosted"),
        id("youzaiworldcore", "yzui_minimal"), id("youzaiworldcore", "yzui_dark"),
        id("youzaiworldcore", "yzui_dark_frosted"), id("youzaiworldcore", "yzui_dark_minimal")
    };
    public static final class FakeHelper implements IThemeHelper {
        final Set<Identifier> available = new HashSet<>();
        public boolean hasTheme(Identifier id) { return available.contains(id); }
    }
    public static final class FakeConfig implements IWailaConfig {
        final FakeOverlay overlay = new FakeOverlay();
        int saves;
        public Overlay overlay() { return overlay; }
        public void save() { saves++; }
    }
    public static final class FakeOverlay implements IWailaConfig.Overlay {
        Identifier active;
        int reads, applies;
        public Theme getTheme() {
            reads++;
            // 与已核对的 Jade 26.2.11 一致：默认主题未加载时，懒初始化抛空指针。
            Objects.requireNonNull(HELPER.hasTheme(JadeIds.DEFAULT_THEME) ? Boolean.TRUE : null,
                    "Jade 默认主题未初始化");
            if (!HELPER.hasTheme(active)) active = JadeIds.DEFAULT_THEME;
            return new Theme(active);
        }
        public void applyTheme(Identifier id) {
            require(HELPER.hasTheme(JadeIds.DEFAULT_THEME) && HELPER.hasTheme(id), "不能应用尚未就绪的主题");
            active = id;
            applies++;
        }
    }
    private static Identifier id(String namespace, String path) {
        return Identifier.fromNamespaceAndPath(namespace, path);
    }
    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
    private static void loadThemes() {
        HELPER.available.add(JadeIds.DEFAULT_THEME);
        HELPER.available.add(USER_THEME);
        HELPER.available.addAll(Arrays.asList(MANAGED));
    }
    public static void main(String[] args) {
        FakeOverlay overlay = CONFIG.overlay;
        overlay.active = USER_THEME;

        // 首次启动：总开关开启与关闭，都不能触发空注册表的懒初始化。
        JadeThemeBridge.tick(true);
        JadeThemeBridge.tick(false);
        require(overlay.reads == 0 && overlay.applies == 0 && CONFIG.saves == 0, "启动等待必须无副作用");
        HELPER.available.addAll(Arrays.asList(MANAGED));
        JadeThemeBridge.tick(true);
        JadeThemeBridge.tick(false);
        require(overlay.reads == 0, "只有自定义主题而没有默认主题时仍需等待");

        // 仅原版 Jade 主题就绪时，不覆盖玩家选择。
        HELPER.available.clear();
        HELPER.available.add(JadeIds.DEFAULT_THEME);
        HELPER.available.add(USER_THEME);
        JadeThemeBridge.tick(true);
        require(USER_THEME.equals(overlay.active) && overlay.applies == 0, "目标主题缺失时保留用户选择");
        loadThemes();
        JadeThemeBridge.tick(true);
        require(MANAGED[0].equals(overlay.active), "资源就绪后应用默认浅色");
        int applies = overlay.applies;
        JadeThemeBridge.tick(true);
        require(overlay.applies == applies && CONFIG.saves == 0, "稳定 tick 不重复应用或保存");

        // 六套外观切换期间，最初的用户主题必须保持可恢复。
        int index = 0;
        for (YzuiThemeMode mode : YzuiThemeMode.values()) {
            for (YzuiVisualStyle style : YzuiVisualStyle.values()) {
                ClientExternalSettings.mode = mode;
                ClientExternalSettings.style = style;
                JadeThemeBridge.tick(true);
                require(MANAGED[index++].equals(overlay.active), "外观切换目标不正确");
            }
        }
        int reads = overlay.reads;
        applies = overlay.applies;
        HELPER.available.clear();
        JadeThemeBridge.tick(true);
        JadeThemeBridge.tick(false);
        require(overlay.reads == reads && overlay.applies == applies && CONFIG.saves == 0,
                "重载等待期间不读取、应用或保存主题");
        loadThemes();
        JadeThemeBridge.tick(false);
        require(USER_THEME.equals(overlay.active) && CONFIG.saves == 1, "重载后关闭应恢复原用户主题");
        applies = overlay.applies;
        JadeThemeBridge.tick(false);
        require(overlay.applies == applies && CONFIG.saves == 1, "关闭状态不重复恢复或保存");

        // 目标主题延迟出现，以及原主题被资源包移除。
        HELPER.available.remove(MANAGED[5]);
        JadeThemeBridge.tick(true);
        require(USER_THEME.equals(overlay.active) && overlay.applies == applies, "目标缺失时等待");
        HELPER.available.add(MANAGED[5]);
        JadeThemeBridge.tick(true);
        require(MANAGED[5].equals(overlay.active), "目标出现后恢复同步");
        HELPER.available.remove(USER_THEME);
        JadeThemeBridge.tick(false);
        require(JadeIds.DEFAULT_THEME.equals(overlay.active) && CONFIG.saves == 2,
                "原用户主题被移除后使用 Jade 默认主题");

        // 上次运行留下托管主题，但本次总开关已关闭。
        overlay.active = MANAGED[0];
        JadeThemeBridge.tick(false);
        require(JadeIds.DEFAULT_THEME.equals(overlay.active) && CONFIG.saves == 3,
                "关闭状态应释放上次运行的托管主题");
    }
}
""",
}


def check_jade_bridge(java_home, bridge_source=None):
    binary = Path(java_home) / "bin" if java_home else None
    suffix = ".exe" if os.name == "nt" else ""
    javac = str(binary / ("javac" + suffix)) if binary else "javac"
    java = str(binary / ("java" + suffix)) if binary else "java"
    bridge = Path(bridge_source).resolve() if bridge_source else CLIENT / "jade/JadeThemeBridge.java"
    with tempfile.TemporaryDirectory(prefix=".tmp-yzui-jade-check-", dir=ROOT) as directory:
        temporary = Path(directory).resolve()
        assert temporary.is_relative_to(ROOT.resolve())
        sources = []
        for relative, content in STUBS.items():
            source = temporary / relative
            source.parent.mkdir(parents=True, exist_ok=True)
            source.write_text(content, encoding="utf-8")
            sources.append(source)
        sources.extend([bridge, CLIENT / "config/YzuiThemeMode.java", CLIENT / "config/YzuiVisualStyle.java"])
        classes = temporary / "classes"
        classes.mkdir()
        subprocess.run([javac, "--release", "25", "-encoding", "UTF-8", "-proc:none", "-d", str(classes),
                        *map(str, sources)], cwd=ROOT, check=True)
        subprocess.run([java, "-cp", str(classes), "regression.JadeBridgeCheck"], cwd=ROOT, check=True)
    print("Jade 源码回归检查通过：启动开/关、默认与目标主题延迟加载、资源重载、六种外观切换、原主题恢复及缺失回退。")


def main():
    assert Path.cwd().resolve() == ROOT.resolve(), "请在 YouzaiWorldCore 仓库根目录执行"
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--java-home", default=os.environ.get("JAVA_HOME"))
    parser.add_argument("--bridge-source", type=Path, help="可选的旧版桥接源码，用于确认回归检查能够重现问题")
    options = parser.parse_args()
    check_jade_bridge(options.java_home, options.bridge_source)


if __name__ == "__main__":
    main()
