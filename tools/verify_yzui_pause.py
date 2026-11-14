"""执行实际暂停菜单、按钮装饰和布局源码，验证原版回调、响应式排版和焦点。

复用菜单回归的 Minecraft 绘制替身；不启动游戏、不运行 Gradle、不下载依赖。
"""

from pathlib import Path
import argparse
import json
import os
import subprocess
import tempfile
from verify_yzui_menu import STUBS as MENU_STUBS

ROOT = Path(__file__).resolve().parents[1]
CLIENT = ROOT / "src/client/java/top/csituka/youzaiworldcore/client"
P = "top.csituka.youzaiworldcore"
C = P + ".client"
STUBS = {}
for name in (
    "org.joml.Vector2f", "net.minecraft.resources.Identifier", "net.minecraft.client.gui.Font",
    "net.minecraft.client.renderer.RenderPipelines", "net.minecraft.client.input.KeyEvent",
    "net.minecraft.client.input.MouseButtonEvent", "com.mojang.blaze3d.platform.InputConstants",
    "net.minecraft.client.gui.narration.NarrationElementOutput", "net.minecraft.client.gui.components.Tooltip",
    "net.minecraft.client.gui.components.AbstractWidget", "net.minecraft.client.gui.components.EditBox",
    "net.minecraft.client.gui.GuiGraphicsExtractor", "net.minecraft.client.gui.screens.inventory.InventoryScreen",
    P + ".util.DebugLogger", C + ".render.YzuiTheme",
):
    path = name.replace(".", "/") + ".java"
    STUBS[path] = MENU_STUBS[path]


def stub(name, body):
    package, _, _ = name.rpartition(".")
    STUBS[name.replace(".", "/") + ".java"] = "package " + package + ";\n" + body


for name in ("net.minecraft.client.gui.components.events.GuiEventListener",
             "net.minecraft.client.gui.components.Renderable", "net.minecraft.client.gui.narration.NarratableEntry",
             "net.minecraft.network.chat.ComponentContents"):
    stub(name, "public interface " + name.rpartition(".")[2] + " {}")

stub("net.minecraft.network.chat.contents.TranslatableContents", """
public record TranslatableContents(String key) implements net.minecraft.network.chat.ComponentContents {
    public String getKey(){return key;}
}
""")
stub("net.minecraft.network.chat.Component", """
import net.minecraft.network.chat.contents.TranslatableContents;
public record Component(String value,ComponentContents contents) {
    public static Component empty(){return literal("");}
    public static Component literal(String value){return new Component(value,new ComponentContents(){});}
    public static Component translatable(String key,Object...args){return new Component(key,new TranslatableContents(key));}
    public String getString(){return value;}
    public Component getVisualOrderText(){return this;}
    public ComponentContents getContents(){return contents;}
    public java.util.List<Component> getSiblings(){return java.util.List.of();}
}
""")
widget_path = "net/minecraft/client/gui/components/AbstractWidget.java"
STUBS[widget_path] = STUBS[widget_path].replace("public class AbstractWidget {", """public class AbstractWidget implements
    net.minecraft.client.gui.components.events.GuiEventListener, Renderable,
    net.minecraft.client.gui.narration.NarratableEntry {""")
STUBS[widget_path] = STUBS[widget_path].replace("public void setX(int x)",
    "public void setWidth(int w){width=w;} public void setHeight(int h){height=h;} public void setX(int x)")
stub("net.minecraft.client.gui.components.AbstractButton", """
public class AbstractButton extends AbstractWidget {
    public AbstractButton(int x,int y,int w,int h,net.minecraft.network.chat.Component message){super(x,y,w,h,message);}
}
""")
stub("net.minecraft.client.gui.components.Button", """
import net.minecraft.network.chat.Component;
public class Button extends AbstractButton {
    private final OnPress action;
    public Button(Component message,OnPress action){super(0,0,98,20,message);this.action=action;}
    public void press(){if(active&&visible)action.onPress(this);}
    public static Builder builder(Component message,OnPress action){return new Builder(message,action);}
    public interface OnPress {void onPress(Button button);}
    public static class Builder {
        private final Button button;
        public Builder(Component message,OnPress action){button=new Button(message,action);}
        public Builder bounds(int x,int y,int w,int h){button.setRectangle(w,h,x,y);return this;}
        public Button build(){return button;}
    }
}
""")
stub(P + ".mixin.client.ScreenAccessor", """
public interface ScreenAccessor {
    java.util.List<net.minecraft.client.gui.components.events.GuiEventListener> youzaiworldcore$getChildren();
    java.util.List<net.minecraft.client.gui.components.Renderable> youzaiworldcore$getRenderables();
    java.util.List<net.minecraft.client.gui.narration.NarratableEntry> youzaiworldcore$getNarratables();
    net.minecraft.client.gui.Font youzaiworldcore$getFont();
}
""")
stub("net.minecraft.client.gui.screens.Screen", """
public class Screen {
    public final net.minecraft.network.chat.Component title;
    public Screen(net.minecraft.network.chat.Component title){this.title=title;}
}
""")
stub("net.minecraft.client.gui.screens.PauseScreen", """
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
public class PauseScreen extends Screen implements top.csituka.youzaiworldcore.mixin.client.ScreenAccessor {
    public int width,height;
    public final java.util.List<GuiEventListener> children=new java.util.ArrayList<>();
    public final java.util.List<Renderable> renderables=new java.util.ArrayList<>();
    public final java.util.List<NarratableEntry> narratables=new java.util.ArrayList<>();
    public Button focused;
    public PauseScreen(int w,int h){super(net.minecraft.network.chat.Component.translatable("menu.game"));width=w;height=h;}
    public java.util.List<GuiEventListener> children(){return children;}
    public net.minecraft.network.chat.Component getTitle(){return title;}
    public void setFocused(Button button){
        if(focused!=null)focused.setFocused(false);
        focused=button;if(button!=null)button.setFocused(true);
    }
    public void add(AbstractWidget widget){children.add(widget);renderables.add(widget);narratables.add(widget);}
    public java.util.List<GuiEventListener> youzaiworldcore$getChildren(){return children;}
    public java.util.List<Renderable> youzaiworldcore$getRenderables(){return renderables;}
    public java.util.List<NarratableEntry> youzaiworldcore$getNarratables(){return narratables;}
    public net.minecraft.client.gui.Font youzaiworldcore$getFont(){return net.minecraft.client.Minecraft.getInstance().font;}
}
""")
stub("net.minecraft.client.player.LocalPlayer", """
public class LocalPlayer {
    public String name="LongPlayerNameForPauseMenu";
    public String getScoreboardName(){return name;}
    public net.minecraft.network.chat.Component getName(){return net.minecraft.network.chat.Component.literal(name);}
}
""")
stub("net.minecraft.client.multiplayer.ServerData", """
public class ServerData {public String name="Long server name for the pause screen";}
""")
stub("net.minecraft.client.Minecraft", """
public class Minecraft {
    private static final Minecraft INSTANCE=new Minecraft();
    public final net.minecraft.client.gui.Font font=new net.minecraft.client.gui.Font();
    public net.minecraft.client.player.LocalPlayer player=new net.minecraft.client.player.LocalPlayer();
    public boolean singleplayer,paused;
    public net.minecraft.client.multiplayer.ServerData server;
    public net.minecraft.client.gui.screens.Screen screen;
    public static Minecraft getInstance(){return INSTANCE;}
    public boolean hasSingleplayerServer(){return singleplayer;}
    public boolean isPaused(){return paused;}
    public net.minecraft.client.multiplayer.ServerData getCurrentServer(){return server;}
    public void setScreenAndShow(net.minecraft.client.gui.screens.Screen next){screen=next;}
}
""")
stub(C + ".config.ClientExternalSettings", """
public class ClientExternalSettings {
    public static boolean enabled=true,dev;
    public static boolean isYzuiEnabled(){return enabled;}
    public static boolean isDevModeEnabled(){return dev;}
}
""")
stub(P + ".account.util.AuthPlayerHelper", """
public class AuthPlayerHelper {public static boolean isDeveloperPlayerName(String name){return name.equals("Developer");}}
""")
stub(C + ".screen.AccountManagementScreen", """
public class AccountManagementScreen extends net.minecraft.client.gui.screens.Screen {
    public final net.minecraft.client.gui.screens.Screen parent;
    public AccountManagementScreen(net.minecraft.client.gui.screens.Screen parent){super(parent.title);this.parent=parent;}
}
""")
theme_path = C.replace(".", "/") + "/render/YzuiTheme.java"
for method in ("text", "textMuted", "primary", "primaryContainer", "outline"):
    STUBS[theme_path] = STUBS[theme_path].replace(f"int {method}(){{return -1;}}", f"int {method}(){{return palette().{method}();}}")
STUBS[theme_path] = STUBS[theme_path].replace("public enum ButtonStyle", """
    public static int onPrimary(){return palette().onPrimary();}
    public static void hudText(GuiGraphicsExtractor g,Font f,Component c,int x,int y,int color,float alpha){g.text(f,c,x,y,color,true);}
    public enum ButtonStyle""").replace('int used=Math.min(w,f.width(c));', 'g.textColors.add(color); int used=Math.min(w,f.width(c));')
graphics_path = "net/minecraft/client/gui/GuiGraphicsExtractor.java"
STUBS[graphics_path] = STUBS[graphics_path].replace("public int scissors;", "public int scissors; public final List<Integer> fills=new ArrayList<>(),textColors=new ArrayList<>();")
STUBS[graphics_path] = STUBS[graphics_path].replace('box("fill",x,y,r-x,b-y,"");', 'fills.add(color);box("fill",x,y,r-x,b-y,"");')

HARNESS = r"""
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import top.csituka.youzaiworldcore.client.config.*;
import top.csituka.youzaiworldcore.client.render.*;
import top.csituka.youzaiworldcore.client.screen.pause.*;
import top.csituka.youzaiworldcore.client.screen.widget.PauseButtonStyle;
public class PauseChecks {
    static int assertions;
    static void check(boolean condition,String message){assertions++;if(!condition)throw new AssertionError(message);}
    static final String[] KEYS={"menu.returnToGame","gui.advancements","gui.stats","menu.options","options.worldOptions.button","menu.disconnect"};
    static PauseScreen screen(int w,int h){
        var screen=new PauseScreen(w,h);
        for(String key:KEYS)screen.add(new Button(Component.translatable(key),button->button.setMessage(Component.literal("clicked"))));
        screen.add(new Button(Component.literal("unwanted"),button->{}));
        screen.add(new AbstractWidget(0,40,80,9,Component.translatable("menu.game")));
        return screen;
    }
    static void geometry(){
        for(int[] size:new int[][]{{320,180},{320,210},{320,240},{320,260},{320,320},{460,260},{480,360},{854,480},{960,540},{1920,1080}}){
            for(boolean account:new boolean[]{false,true}){
                var layout=PauseMenuLayout.of(size[0],size[1],account);
                var widgets=new ArrayList<PauseMenuLayout.Rect>(List.of(layout.resume(),layout.advancements(),layout.statistics(),layout.options(),layout.disconnect()));
                if(account)widgets.add(layout.account());
                check(!account||layout.account().y()==layout.options().y(),"账户必须与选项并排");
                for(var r:widgets){
                    check(r.width()>0&&r.height()>=20,"控件必须可点击");
                    check(r.x()>=0&&r.y()>=0&&r.right()<=size[0]&&r.bottom()<=size[1],"按钮越出窗口 "+Arrays.toString(size));
                    for(var other:widgets)if(r!=other)check(r.right()<=other.x()||other.right()<=r.x()||r.bottom()<=other.y()||other.bottom()<=r.y(),"控件相互覆盖");
                }
                check(layout.actions().bottom()<=size[1],"底板越出窗口");
                if(layout.profile().width()>0){
                    check(layout.model().bottom()<=layout.profile().bottom()-16,"模型越出玩家卡片");
                    check(layout.model().x()>=layout.profile().x()&&layout.model().right()<=layout.profile().right(),"模型越出卡片");
                }
            }
        }
    }
    static void behavior(){
        var client=Minecraft.getInstance();var screen=screen(480,360);
        var originals=screen.children.stream().filter(c->c instanceof Button).map(c->(Button)c).limit(6).toList();
        originals.get(4).active=false;
        var restriction=Tooltip.create(Component.literal("server restriction"));originals.get(4).setTooltip(restriction);
        var menu=new YzuiPauseMenu(screen,null);
        check(screen.children.size()==6&&screen.renderables.size()==6&&screen.narratables.size()==6,"控件列表未同步");
        for(int i=0;i<originals.size();i++)if(i!=4)check(screen.children.contains(originals.get(i))&&screen.renderables.contains(originals.get(i)),"原版按钮被替换");
        check(!screen.children.contains(originals.get(4))&&!screen.renderables.contains(originals.get(4))&&!screen.narratables.contains(originals.get(4)),"世界选项按钮未完全移除");
        originals.get(5).press();check(originals.get(5).getMessage().getString().equals("clicked"),"原版退出回调丢失");
        var account=(Button)screen.children.stream().filter(value->value instanceof Button button&&button.getMessage().getString().equals("screen.youzaiworldcore.account_management.button_open")).findFirst().orElseThrow();account.press();
        check(account.getY()==originals.get(3).getY()&&account.getX()>originals.get(3).getX(),"账户按钮没有替换世界选项位置");
        check(client.screen instanceof top.csituka.youzaiworldcore.client.screen.AccountManagementScreen a&&a.parent==screen,"账户页面没有返回暂停菜单");
        var fresh=screen(320,240);menu=new YzuiPauseMenu(fresh,null);
        menu.moveFocus(new KeyEvent(81,false));check(fresh.focused.getMessage().getString().equals(KEYS[0]),"方向键未建立焦点");
        menu.moveFocus(new KeyEvent(81,false));check(fresh.focused.getMessage().getString().equals(KEYS[1]),"向下未进入进度");
        menu.moveFocus(new KeyEvent(79,false));check(fresh.focused.getMessage().getString().equals(KEYS[2]),"向右未进入统计");
        menu.render(new GuiGraphicsExtractor(),0,0);fresh.setFocused(null);
        String previous=menu.focusedKey();
        var resized=screen(854,480);var next=new YzuiPauseMenu(resized,previous);
        resized.setFocused(null);check(next.restoreFocus()&&resized.focused.getMessage().getString().equals(KEYS[2]),"窗口重建丢失焦点");
        var graphic=new GuiGraphicsExtractor();next.render(graphic,0,0);
        check(graphic.scissors==0&&graphic.boxes.stream().anyMatch(b->b.kind().equals("model")),"模型未裁剪或裁剪未恢复");
        client.player=null;next.render(new GuiGraphicsExtractor(),0,0);client.player=new net.minecraft.client.player.LocalPlayer();
        for(boolean paused:new boolean[]{true,false}){
            client.paused=paused;var rendered=new GuiGraphicsExtractor();next.render(rendered,0,0);
            check(rendered.boxes.stream().noneMatch(b->b.text().contains("pause.paused")||b.text().contains("pause.live")),"仍然绘制暂停状态句子");
        }
        client.singleplayer=true;
        ClientExternalSettings.dev=true;client.player.name="Developer";
        var developer=screen(480,360);new YzuiPauseMenu(developer,null);
        check(developer.children.size()==5,"开发者单人测试账户入口未隐藏");
        client.singleplayer=false;var multiplayer=screen(480,360);new YzuiPauseMenu(multiplayer,null);
        check(multiplayer.children.size()==6,"多人游戏错误隐藏账户入口");
        ClientExternalSettings.dev=false;
    }
    static void contrast(){
        for(var palette:new YzuiPalette[]{YzuiPalette.LIGHT,YzuiPalette.DARK})for(var preset:YzuiVisualStyle.values()){
            YzuiTheme.colors=palette;YzuiTheme.style=preset;
            for(var style:new YzuiTheme.ButtonStyle[]{YzuiTheme.ButtonStyle.FILLED,YzuiTheme.ButtonStyle.TONAL,YzuiTheme.ButtonStyle.DANGER})for(float hover:new float[]{0,1})for(boolean focused:new boolean[]{false,true}){
                var button=new Button(Component.literal("label"),b->{});button.setFocused(focused);
                PauseButtonStyle.set(button,MenuIcon.GAME,style);
                var g=new GuiGraphicsExtractor();check(PauseButtonStyle.background(g,button,hover)&&PauseButtonStyle.label(g,button,hover),"按钮装饰未接管");
                int fill=g.fills.getFirst(),text=g.textColors.getLast();
                for(int world:new int[]{0,0xffffff}){
                    int surface=YzuiPalette.composite(fill,world,(fill>>>24)/255f);
                    check(YzuiPalette.contrast(text,surface)>=4.5,"按钮文字对比度不足 "+style+" "+preset+" "+focused);
                }
                ClientExternalSettings.enabled=false;
                check(!PauseButtonStyle.background(g,button,hover)&&!PauseButtonStyle.label(g,button,hover),"YZUI 关闭后装饰仍生效");
                ClientExternalSettings.enabled=true;
            }
        }
    }
    static void icons(){
        for(var icon:MenuIcon.values()){
            var original=new GuiGraphicsExtractor();icon.render(original,0,0,-1);
            var scaled=new GuiGraphicsExtractor();icon.render(scaled,23,31,12,-1);
            check(original.boxes.size()==scaled.boxes.size(),"等比绘制改变图标几何");
            for(int i=0;i<original.boxes.size();i++){
                var source=original.boxes.get(i);var target=scaled.boxes.get(i);
                check(Math.abs(target.x()-(23+source.x()*0.75))<0.001&&Math.abs(target.y()-(31+source.y()*0.75))<0.001,"图标坐标缩放不一致");
                check(Math.abs(target.w()-source.w()*0.75)<0.001&&Math.abs(target.h()-source.h()*0.75)<0.001,"图标纵横缩放不一致");
                check(target.x()>=23&&target.y()>=31&&target.x()+target.w()<=35&&target.y()+target.h()<=43,"图标越出正方形边界");
            }
            check(scaled.pose().stack.isEmpty()&&scaled.pose().m00()==1&&scaled.pose().m11()==1,"图标变换影响后续文字");
        }
        var button=new Button(Component.literal("disconnect"),b->{});
        PauseButtonStyle.set(button,MenuIcon.EXIT,YzuiTheme.ButtonStyle.DANGER);
        var decorated=new GuiGraphicsExtractor();PauseButtonStyle.label(decorated,button,0);
        var expected=new GuiGraphicsExtractor();MenuIcon.EXIT.render(expected,10,4,12,0);
        check(decorated.boxes.subList(0,expected.boxes.size()).equals(expected.boxes),"退出按钮图标未采用门与箭头");
    }
    public static void main(String[] args){geometry();behavior();contrast();icons();System.out.println("暂停屏幕回归通过："+assertions+" 项断言。");}
}
"""


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--java-home")
    args = parser.parse_args()
    binary = Path(args.java_home) / "bin" if args.java_home else Path("")
    suffix = ".exe" if os.name == "nt" else ""
    production = ["screen/pause/PauseMenuLayout.java", "screen/pause/YzuiPauseMenu.java",
                  "screen/widget/PauseButtonStyle.java", "screen/widget/WidgetFocus.java",
                  "render/YzuiMenuPanel.java", "render/MenuIcon.java", "render/RoundedRect.java",
                  "render/YzuiPalette.java", "config/YzuiVisualStyle.java"]
    with tempfile.TemporaryDirectory(prefix=".tmp-yzui-pause-", dir=ROOT) as directory:
        temporary = Path(directory)
        sources = []
        for path, source in STUBS.items():
            file = temporary / path
            file.parent.mkdir(parents=True, exist_ok=True)
            file.write_text(source, encoding="utf-8")
            sources.append(str(file))
        harness = temporary / "PauseChecks.java"
        harness.write_text(HARNESS, encoding="utf-8")
        subprocess.run([str(binary / ("javac" + suffix)), "--release", "25", "-encoding", "UTF-8",
                        "-d", str(temporary), *sources, *[str(CLIENT / path) for path in production], str(harness)], check=True)
        subprocess.run([str(binary / ("java" + suffix)), "-cp", str(temporary), "PauseChecks"], check=True)
    names = {"screen.youzaiworldcore.pause." + key for key in ("profile", "singleplayer", "hint")}
    for path in (ROOT / "src/main/resources/assets/youzaiworldcore/lang").glob("*.json"):
        def unique(pairs):
            result = {}
            for key, value in pairs:
                if key in result:
                    raise AssertionError(f"重复语言键：{path}: {key}")
                result[key] = value
            return result
        language = json.loads(path.read_text(encoding="utf-8"), object_pairs_hook=unique)
        assert names <= language.keys(), path
        assert "screen.youzaiworldcore.pause.paused" not in language, path
        assert "screen.youzaiworldcore.pause.live" not in language, path
    print("暂停屏幕三条文案的十种语言、移除状态提示与重复键检查通过。")


if __name__ == "__main__":
    main()
