"""用实际页面控制器、背景层与弹窗源码验证生命周期；不启动游戏或下载依赖。"""

from pathlib import Path
import argparse
import os
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[1]
CLIENT = ROOT / "src/client/java/top/csituka/youzaiworldcore/client"
STUBS = {
    "org/jspecify/annotations/Nullable.java": """
package org.jspecify.annotations;
@java.lang.annotation.Target(java.lang.annotation.ElementType.TYPE_USE)
public @interface Nullable {}
""",
    "net/minecraft/client/gui/screens/Screen.java": """
package net.minecraft.client.gui.screens;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import top.csituka.youzaiworldcore.client.animation.GuiAnimationController;
public class Screen {
    public int width = 854, height = 480, inits, renders;
    public Runnable onRender = () -> {};
    public void init() { inits++; }
    public boolean shouldCloseOnEsc() { return true; }
    public void onClose() {}
    public void extractRenderState(GuiGraphicsExtractor g, int x, int y, float tick) {
        renders++;
        if (!GuiAnimationController.isBackgroundRendering() || x >= 0 || y >= 0)
            throw new AssertionError("背景页面不能获得正常鼠标坐标");
        if (GuiAnimationController.contentOpacity() != 1f)
            throw new AssertionError("父页面不应继承子页面淡入透明度");
        g.trace.add(getClass().getSimpleName());
        onRender.run();
    }
}
""",
    "net/minecraft/client/gui/screens/inventory/AbstractContainerScreen.java": """
package net.minecraft.client.gui.screens.inventory;
public class AbstractContainerScreen<T> extends net.minecraft.client.gui.screens.Screen {}
""",
    "net/minecraft/client/gui/Gui.java": """
package net.minecraft.client.gui;
import net.minecraft.client.gui.screens.Screen;
import top.csituka.youzaiworldcore.client.animation.GuiAnimationController;
public class Gui {
    private Screen screen;
    public Screen screen() { return screen; }
    public void setScreen(Screen next) {
        if (GuiAnimationController.interceptScreenChange(this, next)) return;
        screen = next;
        GuiAnimationController.onScreenChanged(next);
        if (next != null) next.init();
    }
}
""",
    "net/minecraft/client/gui/GuiGraphicsExtractor.java": """
package net.minecraft.client.gui;
import java.util.ArrayList;
import java.util.List;
import top.csituka.youzaiworldcore.client.render.YzuiViewport;
public class GuiGraphicsExtractor {
    public final List<String> trace = new ArrayList<>();
    public int strata, blurs, panoramas, lastFill;
    private final Pose pose = new Pose();
    public int guiWidth() { var s = YzuiViewport.renderingScreen(); return s == null ? 854 : s.width; }
    public int guiHeight() { var s = YzuiViewport.renderingScreen(); return s == null ? 480 : s.height; }
    public Pose pose() { return pose; }
    public void nextStratum() { strata++; }
    public void blurBeforeThisStratum() { blurs++; }
    public void fill(int x, int y, int right, int bottom, int color) {
        lastFill = color;
        if ((color >>> 24) >= 255) throw new AssertionError("背景不得使用纯色覆盖");
    }
    public static final class Pose {
        public int depth;
        public void pushMatrix() { depth++; }
        public void popMatrix() { if (--depth < 0) throw new AssertionError("矩阵栈不平衡"); }
        public void translate(float x, float y) {}
        public void scale(float x, float y) {}
    }
}
""",
    "net/minecraft/client/Minecraft.java": """
package net.minecraft.client;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphicsExtractor;
public class Minecraft {
    private static final Minecraft INSTANCE = new Minecraft();
    public Object level, player;
    public final Gui gui = new Gui();
    public final Renderer gameRenderer = new Renderer();
    public static Minecraft getInstance() { return INSTANCE; }
    public static final class Renderer {
        private final Panorama panorama = new Panorama();
        public Panorama panorama() { return panorama; }
    }
    public static final class Panorama {
        public void extractRenderState(GuiGraphicsExtractor g, int width, int height) {
            if (width != 854 || height != 480) throw new AssertionError("全景图使用物理 GUI 尺寸");
            g.panoramas++;
        }
    }
}
""",
    "top/csituka/youzaiworldcore/client/config/ClientExternalSettings.java": """
package top.csituka.youzaiworldcore.client.config;
public class ClientExternalSettings {
    public static GuiAnimationMode mode = GuiAnimationMode.BASIC;
    public static YzuiVisualStyle style = YzuiVisualStyle.STANDARD;
    public static GuiAnimationMode getGuiAnimationMode() { return mode; }
    public static YzuiVisualStyle getYzuiVisualStyle() { return style; }
}
""",
    "top/csituka/youzaiworldcore/client/render/YzuiTheme.java": """
package top.csituka.youzaiworldcore.client.render;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import top.csituka.youzaiworldcore.client.config.*;
public class YzuiTheme {
    public static boolean isCustomScreen(Screen s) {
        return s != null && s.getClass().getName().startsWith("top.csituka.youzaiworldcore.client.screen.");
    }
    public static YzuiVisualStyle visualStyle() { return ClientExternalSettings.style; }
    public static boolean frosted() { return visualStyle() == YzuiVisualStyle.FROSTED; }
    public static int scrim() { return 0x480B1912; }
    public static int multiplyAlpha(int color, float opacity) {
        return Math.round((color >>> 24) * opacity) << 24 | color & 0xFFFFFF;
    }
    public static void screenCard(GuiGraphicsExtractor g, Screen s) {}
}
""",
    "top/csituka/youzaiworldcore/util/DebugLogger.java": """
package top.csituka.youzaiworldcore.util;
public class DebugLogger {
    public static void info(String module, String format, Object... args) {}
    public static void debug(String module, String format, Object... args) {}
}
""",
}
for name in ("MailBaseScreen", "YzHudSettingsScreen", "RootPage", "ChildPage", "DialogPage"):
    popup = " implements YzuiPopupScreen" if name == "DialogPage" else ""
    STUBS[f"top/csituka/youzaiworldcore/client/screen/{name}.java"] = f"""
package top.csituka.youzaiworldcore.client.screen;
public class {name} extends net.minecraft.client.gui.screens.Screen{popup} {{}}
"""

STUBS["top/csituka/youzaiworldcore/client/screen/MixedPage.java"] = """
package top.csituka.youzaiworldcore.client.screen;
public class MixedPage extends net.minecraft.client.gui.screens.Screen implements YzuiPopupScreen {
    private final boolean popup;
    public MixedPage(boolean popup) { this.popup = popup; }
    @Override public boolean isPopup() { return popup; }
}
"""

STUBS["net/minecraft/client/input/KeyEvent.java"] = """
package net.minecraft.client.input;
public record KeyEvent(int key) {}
"""
STUBS["com/mojang/blaze3d/platform/InputConstants.java"] = """
package com.mojang.blaze3d.platform;
// 26.3 改用 SDL：键值为 SDL 扫描码，鼠标左/中/右键为 1/2/3；数值与原版 InputConstants 一致。
public class InputConstants {
    public static final int KEY_ESCAPE=41, KEY_RETURN=40, KEY_NUMPADENTER=88, KEY_SPACE=44, KEY_TAB=43, KEY_UP=82, KEY_DOWN=81,
        KEY_LEFT=80, KEY_RIGHT=79, KEY_BACKSPACE=42, KEY_DELETE=76, KEY_HOME=74, KEY_END=77, KEY_PAGEUP=75,
        KEY_PAGEDOWN=78, MOUSE_BUTTON_LEFT=1, MOUSE_BUTTON_MIDDLE=2, MOUSE_BUTTON_RIGHT=3,
        PRESS=1, RELEASE=0, REPEAT=-1;
}
"""
STUBS["top/csituka/youzaiworldcore/client/screen/MenuPage.java"] = """
package top.csituka.youzaiworldcore.client.screen;
public class MenuPage extends net.minecraft.client.gui.screens.Screen implements YzuiMenuScreen {
    public boolean locked;
    public boolean shouldCloseOnEsc() { return !locked; }
    public void onClose() { closeMenu(); }
}
"""

CHECK = """
import java.lang.reflect.Field;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import top.csituka.youzaiworldcore.client.animation.*;
import top.csituka.youzaiworldcore.client.config.*;
import top.csituka.youzaiworldcore.client.render.*;
import top.csituka.youzaiworldcore.client.screen.*;

public final class YzuiTransitionCheck {
    private static int checks;
    private static void require(boolean value, String reason) {
        checks++;
        if (!value) throw new AssertionError(reason);
    }
    private static void age(Object target, String name, long elapsed) throws Exception {
        Class<?> type = target == null ? GuiAnimationController.class : target.getClass();
        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        field.setLong(target, System.currentTimeMillis() - elapsed);
    }
    private static void finishExit() throws Exception {
        age(null, "exitStartedAt", 10000);
        GuiAnimationController.tick();
    }
    public static void main(String[] args) throws Exception {
        var client = Minecraft.getInstance();
        var gui = client.gui;
        Screen title = new Screen();
        RootPage root = new RootPage();
        ChildPage child = new ChildPage();
        DialogPage dialog = new DialogPage();
        gui.setScreen(title);
        require(!GuiAnimationController.animates(title), "基础范围保留原版页面布局和过渡设置");
        gui.setScreen(root);
        require(YzuiBackdrop.parent(root) == null && GuiAnimationController.animates(root), "普通页面不保留标题屏幕控件");
        var graphics = new GuiGraphicsExtractor();
        YzuiBackdrop.render(graphics, root, 0);
        require(graphics.panoramas == 1 && graphics.trace.isEmpty(), "游戏外普通页面只使用全景背景");
        age(null, "enterStartedAt", 80);
        var rendered = GuiAnimationController.sampleFrame(root);
        age(null, "enterStartedAt", 10000);
        require(GuiAnimationController.inputFrame(root).equals(rendered), "点击沿用最后显示的帧，不重新采样时钟");
        gui.setScreen(child);
        require(gui.screen() == root && GuiAnimationController.isExiting(root), "普通页面切换先完成旧页面的退出动画");
        finishExit();
        require(gui.screen() == child && YzuiBackdrop.parent(child) == null, "普通子页面开始独立背景，不叠加上一页");
        graphics = new GuiGraphicsExtractor();
        YzuiBackdrop.render(graphics, child, 0);
        require(graphics.panoramas == 1 && graphics.trace.isEmpty(), "游戏外连续打开普通页面仍只绘制全景背景");
        gui.setScreen(dialog);
        require(gui.screen() == dialog && YzuiBackdrop.parent(dialog) == child, "弹窗立即进入并保留直接父页面");
        graphics = new GuiGraphicsExtractor();
        int titleInits = title.inits, rootInits = root.inits, childInits = child.inits;
        child.onRender = () -> gui.setScreen(null);
        YzuiBackdrop.render(graphics, dialog, 0);
        require(gui.screen() == dialog, "背景渲染不能触发页面导航");
        require(graphics.panoramas == 1 && graphics.trace.equals(java.util.List.of("ChildPage")),
                "弹窗显示直接父页，不重新叠加父页之前的普通窗口");
        require(title.inits == titleInits && root.inits == rootInits && child.inits == childInits,
                "背景绘制不重新 init，不重新请求数据或清空表单");
        require(graphics.pose().depth == 0 && YzuiViewport.renderingScreen() == null
                && !GuiAnimationController.isBackgroundRendering(), "恢复视口、矩阵和背景标志");

        // 背景绘制中异常也必须恢复上下文，避免下一帧永久变暗或失去输入。
        var outer = GuiAnimationController.pushContent(new YzuiMotion.Frame(0.6f, 1, 0));
        child.onRender = () -> { throw new IllegalStateException("expected"); };
        try { YzuiBackdrop.render(graphics, dialog, 0); throw new AssertionError("应传播渲染异常"); }
        catch (IllegalStateException expected) {}
        require(Math.abs(GuiAnimationController.contentOpacity() - 0.6f) < 1e-6
                && graphics.pose().depth == 0 && YzuiViewport.renderingScreen() == null,
                "异常路径恢复原来的透明度与矩阵");
        GuiAnimationController.restoreContent(outer);
        child.onRender = () -> {};

        DialogPage nested = new DialogPage();
        gui.setScreen(nested);
        require(gui.screen() == nested && YzuiBackdrop.parent(nested) == dialog,
                "同一类的不同弹窗可以嵌套，不误判为返回或替换");
        graphics = new GuiGraphicsExtractor();
        YzuiBackdrop.render(graphics, nested, 0);
        require(graphics.panoramas == 1 && graphics.trace.equals(java.util.List.of("ChildPage", "DialogPage")),
                "嵌套弹窗按从页面到父弹窗的顺序绘制");
        gui.setScreen(dialog);
        require(gui.screen() == nested && GuiAnimationController.isExiting(nested), "返回同类父弹窗仍完成退出动画");
        finishExit();
        require(gui.screen() == dialog && YzuiBackdrop.parent(dialog) == child
                && GuiAnimationController.frame(dialog).equals(YzuiMotion.Frame.IDENTITY), "返回实际父弹窗保留其背景且不重复淡入");

        age(null, "enterStartedAt", 50);
        var interrupted = GuiAnimationController.sampleFrame(dialog);
        age(null, "enterStartedAt", 10000);
        gui.setScreen(child);
        age(null, "exitStartedAt", -1000);
        require(gui.screen() == dialog && GuiAnimationController.isExiting(dialog), "返回父页前保留退出卡片");
        require(GuiAnimationController.frame(dialog).equals(interrupted), "实际控制器打断淡入时从最后显示帧淡出");
        finishExit();
        require(gui.screen() == child && !GuiAnimationController.isExiting(dialog), "退出完成才提交页面切换");
        require(GuiAnimationController.frame(child).equals(YzuiMotion.Frame.IDENTITY), "父层返回时不重新淡入");
        ChildPage replacement = new ChildPage();
        gui.setScreen(replacement);
        require(gui.screen() == child, "同类页面替换保留退出过渡");
        finishExit();
        require(YzuiBackdrop.parent(replacement) == null, "普通页面同类替换也不保留旧窗口背景");
        gui.setScreen(null);
        ClientExternalSettings.style = YzuiVisualStyle.MINIMAL;
        GuiAnimationController.tick();
        require(gui.screen() == null && YzuiBackdrop.parent(replacement) == null, "切换低特效后立即结束遗留过渡");

        // 设置页面和确认框共用同一屏幕类，必须按实例内容判断是否为弹窗。
        ClientExternalSettings.style = YzuiVisualStyle.STANDARD;
        var options = new MixedPage(false);
        var confirmation = new MixedPage(true);
        var secondConfirmation = new MixedPage(true);
        var nextOptions = new MixedPage(false);
        gui.setScreen(options);
        gui.setScreen(confirmation);
        require(gui.screen() == confirmation && YzuiBackdrop.parent(confirmation) == options,
                "普通设置页面能打开同类确认框并保留父页面");
        gui.setScreen(secondConfirmation);
        require(YzuiBackdrop.parent(secondConfirmation) == confirmation, "同类确认框嵌套按实例保留父层");
        gui.setScreen(nextOptions);
        require(gui.screen() == secondConfirmation, "确认后进入普通页面仍先关闭弹窗");
        finishExit();
        require(gui.screen() == nextOptions && YzuiBackdrop.parent(nextOptions) == null,
                "从多层弹窗进入普通页面清空整条旧背景链");
        graphics = new GuiGraphicsExtractor();
        YzuiBackdrop.render(graphics, nextOptions, 0);
        require(graphics.panoramas == 1 && graphics.trace.isEmpty(), "共用屏幕类的普通页面不绘制任何旧窗口");
        gui.setScreen(null);
        finishExit();

        ClientExternalSettings.mode = GuiAnimationMode.OFF;
        gui.setScreen(root);
        gui.setScreen(child);
        require(gui.screen() == child && YzuiBackdrop.parent(child) == null, "关闭动画仍区分普通页面背景");
        gui.setScreen(dialog);
        require(gui.screen() == dialog && YzuiBackdrop.parent(dialog) == child, "关闭动画仍保留弹窗父页面");
        gui.setScreen(root);
        require(gui.screen() == root && YzuiBackdrop.parent(root) == null, "关闭动画时离开弹窗也清除旧背景");
        gui.setScreen(null);

        ClientExternalSettings.style = YzuiVisualStyle.FROSTED;
        ClientExternalSettings.mode = GuiAnimationMode.FULL;
        gui.setScreen(title);
        require(GuiAnimationController.animates(title), "完整范围也覆盖原版页面");
        gui.setScreen(root);
        finishExit();
        client.level = new Object();
        client.player = new Object();
        gui.setScreen(child);
        finishExit();
        require(YzuiBackdrop.parent(child) == null, "进入新世界清空标题页面历史");
        graphics = new GuiGraphicsExtractor();
        YzuiBackdrop.render(graphics, child, 0);
        require(graphics.panoramas == 0 && graphics.trace.isEmpty() && graphics.blurs == 1,
                "游戏内普通页面只保留世界背景，磨砂预设使用原版模糊");
        gui.setScreen(dialog);
        graphics = new GuiGraphicsExtractor();
        YzuiBackdrop.render(graphics, dialog, 0);
        require(graphics.panoramas == 0 && graphics.trace.equals(java.util.List.of("ChildPage")),
                "游戏内弹窗以世界和直接父页面为背景");
        client.level = null;
        client.player = null;
        graphics = new GuiGraphicsExtractor();
        YzuiBackdrop.render(graphics, dialog, 0);
        require(graphics.panoramas == 1 && graphics.trace.isEmpty(), "离开世界后清空弹窗父层并恢复全景背景");
        gui.setScreen(null);
        finishExit();

        // 弹窗的变换和 Alpha 可嵌套；关闭动画期间保持模态，不向表单泄漏输入。
        ClientExternalSettings.style = YzuiVisualStyle.STANDARD;
        var popup = new YzuiPopupAnimation();
        popup.show(960, 540);
        require(popup.isVisible() && popup.acceptsInput(), "弹窗打开可操作");
        age(popup, "startedAt", 100);
        require(popup.update(960, 540), "弹窗淡入可见");
        double localX = popup.toLocalX(390), localY = popup.toLocalY(245);
        require(Double.isFinite(localX) && Double.isFinite(localY), "动画中的命中坐标有效");
        outer = GuiAnimationController.pushContent(new YzuiMotion.Frame(0.5f, 1, 0));
        var prior = popup.begin(graphics);
        float opacity = GuiAnimationController.contentOpacity();
        require(opacity > 0 && opacity < 0.5f, "弹窗 Alpha 与页面 Alpha 叠乘一次");
        Object picture = new Object();
        GuiAnimationController.capturePictureOpacity(picture);
        popup.end(graphics, prior);
        require(GuiAnimationController.pictureOpacity(picture) == opacity
                && GuiAnimationController.contentOpacity() == 0.5f, "延迟图片保留采样透明度，绘制上下文正确恢复");
        GuiAnimationController.restoreContent(outer);
        popup.hide();
        require(popup.isVisible() && !popup.acceptsInput(), "弹窗淡出保留模态，禁止重复确认");
        require(popup.toLocalX(390) == localX && popup.toLocalY(245) == localY, "退出从最后显示的帧开始");
        age(popup, "startedAt", 10000);
        require(!popup.update(960, 540) && !popup.isVisible(), "淡出结束解除模态");
        ClientExternalSettings.style = YzuiVisualStyle.MINIMAL;
        popup.show(960, 540);
        require(popup.toLocalX(390) == 390 && popup.toLocalY(245) == 245, "低特效不改变命中坐标");
        popup.hide();
        require(!popup.isVisible(), "低特效立即关闭弹窗");
        var hover = new YzuiHover();
        require(hover.sample(true) == 1 && hover.sample(false) == 0, "低特效仍有即时悬停反馈");
        for (var style : YzuiVisualStyle.values()) {
            ClientExternalSettings.style = style;
            client.level = new Object();
            var menu = new MenuPage();
            GuiAnimationController.setScreenImmediately(gui, menu);
            age(null, "enterStartedAt", 10000);
            GuiAnimationController.sampleFrame(menu);
            graphics = new GuiGraphicsExtractor();
            YzuiBackdrop.render(graphics, menu, 0);
            require(graphics.blurs == 0 && graphics.panoramas == 0,
                    "游戏菜单在所有效果预设中都保留清晰游戏画面");
            require((graphics.lastFill >>> 24) <= 12, "菜单遮罩不应遮住世界");
            gui.setScreen(new ChildPage());
            require(YzuiMenuScreen.handleEscape(menu, new net.minecraft.client.input.KeyEvent(41)),
                    "Esc 在退出动画期间仍可关闭菜单");
            require(gui.screen() == null && !GuiAnimationController.isExiting(menu), "关闭立即生效");
            finishExit();
            require(gui.screen() == null, "下一帧不得重新打开已取消的目标页面");
            GuiAnimationController.setScreenImmediately(gui, menu);
            menu.locked = true;
            require(!YzuiMenuScreen.handleEscape(menu, new net.minecraft.client.input.KeyEvent(41)),
                    "受限页面不能通过 Esc 退出");
            menu.closeMenu();
            require(gui.screen() == menu, "受限页面直接关闭调用也必须被拒绝");
            menu.locked = false;
            menu.closeMenu();
            gui.setScreen(new ChildPage());
            require(gui.screen() instanceof ChildPage, "立即关闭不能遗留动画旁路状态");
            GuiAnimationController.setScreenImmediately(gui, null);
        }
        System.out.println("PASS YZUI backdrop lifecycle: " + checks + " assertions (native client and rendering stubbed)");
    }
}
"""


def main():
    assert Path.cwd().resolve() == ROOT.resolve(), "请在 YouzaiWorldCore 仓库根目录执行"
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--java-home", default=os.environ.get("JAVA_HOME"))
    options = parser.parse_args()
    binary = Path(options.java_home) / "bin" if options.java_home else None
    suffix = ".exe" if os.name == "nt" else ""
    javac = str(binary / ("javac" + suffix)) if binary else "javac"
    java = str(binary / ("java" + suffix)) if binary else "java"
    sources = [CLIENT / path for path in (
        "animation/GuiAnimationController.java", "animation/YzuiMotion.java", "animation/YzuiPopupAnimation.java",
        "animation/YzuiHover.java", "render/YzuiBackdrop.java", "render/YzuiLayerHistory.java",
        "render/YzuiViewport.java", "render/YzuiLayout.java", "screen/YzuiPopupScreen.java", "screen/YzuiMenuScreen.java",
        "config/GuiAnimationMode.java", "config/YzuiVisualStyle.java")]
    with tempfile.TemporaryDirectory(prefix=".tmp-yzui-transition-", dir=ROOT) as directory:
        temporary = Path(directory).resolve()
        assert temporary.is_relative_to(ROOT.resolve())
        for relative, source in {**STUBS, "YzuiTransitionCheck.java": CHECK}.items():
            path = temporary / relative
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(source, encoding="utf-8")
        subprocess.run([javac, "--release", "25", "-encoding", "UTF-8", "-d", str(temporary),
                        *map(str, sources), *map(str, temporary.rglob("*.java"))], cwd=ROOT, check=True)
        subprocess.run([java, "-cp", str(temporary), "YzuiTransitionCheck"], cwd=ROOT, check=True)
    print("页面生命周期回归通过：普通页面场景背景、弹窗与同类窗口嵌套、延迟切换、返回/替换、模式与世界切换、异常恢复、模态及图片透明度。")


if __name__ == "__main__":
    main()
