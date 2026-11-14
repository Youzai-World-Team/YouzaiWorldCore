"""Shift+F 回归：执行实际 Java 菜单、卡片绘制、关闭权限和表单排版代码，不启动游戏。

Minecraft 绘制/网络依赖使用记录型替身；检查几何、状态和资源清单，不代替 GPU 视觉验收。
运行：python -B tools/verify_yzui_menu.py --java-home "C:/Program Files/Java/25"
"""

from pathlib import Path
import argparse
import os
import re
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[1]
CLIENT = ROOT / "src/client/java/top/csituka/youzaiworldcore/client"
P = "top.csituka.youzaiworldcore"
C = P + ".client"
STUBS = {}


def stub(name, body):
    package, _, short = name.rpartition(".")
    STUBS[name.replace(".", "/") + ".java"] = "package " + package + ";\n" + body


stub("org.joml.Vector2f", "public class Vector2f { public float x,y; }")

stub("net.minecraft.resources.Identifier", """
public record Identifier(String namespace, String path) {
    public static Identifier fromNamespaceAndPath(String n, String p) { return new Identifier(n,p); }
    public String getPath() { return path; }
}
""")
stub("net.minecraft.network.chat.Component", """
public record Component(String value) {
    public static Component empty() { return new Component(""); }
    public Component copy() { return this; }
    public Component append(Component other) { return append(other.getString()); }
    public static Component literal(String s) { return new Component(s); }
    public static Component translatable(String s, Object... args) { return new Component(s); }
    public String getString() { return value; }
    public Component append(String text) { return new Component(value+text); }
}
""")
stub("net.minecraft.client.resources.language.I18n", """
public class I18n { public static String get(String s, Object... args) { return s; } }
""")
stub("net.minecraft.client.input.KeyEvent", """
public record KeyEvent(int key, boolean hasShiftDown) {}
""")
stub("net.minecraft.client.input.MouseButtonEvent", """
public record MouseButtonEvent(double x, double y, int button) {}
""")
stub("com.mojang.blaze3d.platform.InputConstants", """
// 26.3 改用 SDL：键值为 SDL 扫描码，鼠标左/中/右键为 1/2/3；数值与原版 InputConstants 一致。
public class InputConstants {
    public static final int KEY_ESCAPE=41, KEY_RETURN=40, KEY_NUMPADENTER=88, KEY_SPACE=44, KEY_TAB=43, KEY_UP=82, KEY_DOWN=81,
        KEY_LEFT=80, KEY_RIGHT=79, KEY_BACKSPACE=42, KEY_DELETE=76, KEY_HOME=74, KEY_END=77, KEY_PAGEUP=75,
        KEY_PAGEDOWN=78, MOUSE_BUTTON_LEFT=1, MOUSE_BUTTON_MIDDLE=2, MOUSE_BUTTON_RIGHT=3,
        PRESS=1, RELEASE=0, REPEAT=-1;
}
""")
stub("net.minecraft.client.gui.Font", """
public class Font {
    public final int lineHeight = 9;
    public int width(String s) { return s.codePoints().map(c -> c > 127 ? 9 : 6).sum(); }
    public int width(net.minecraft.network.chat.Component s) { return width(s.getString()); }
}
""")
stub("net.minecraft.client.renderer.RenderPipelines", """
public class RenderPipelines { public static final Object GUI_TEXTURED = new Object(); }
""")
stub("net.minecraft.client.gui.narration.NarrationElementOutput", """
public interface NarrationElementOutput {}
""")
stub("net.minecraft.client.gui.components.Tooltip", """
public record Tooltip(net.minecraft.network.chat.Component message) {
    public static Tooltip create(net.minecraft.network.chat.Component c) { return new Tooltip(c); }
}
""")
stub("net.minecraft.client.gui.components.AbstractWidget", """
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.*;
import net.minecraft.network.chat.Component;
import net.minecraft.client.gui.narration.NarrationElementOutput;
public class AbstractWidget {
    protected int x,y,width,height;
    protected boolean isHovered;
    public boolean active = true, visible = true, placed;
    private float alpha = 1;
    private boolean focused;
    private Component message;
    private Tooltip tooltip;
    public int nativeRenders, tooltipRenders;
    public AbstractWidget(int x,int y,int w,int h,Component c) { this.x=x;this.y=y;width=w;height=h;message=c; }
    // 26.2 AbstractWidget 字节码已核对：setSize(前两个参数)，再 setPosition(后两个参数)。
    public void setRectangle(int w,int h,int x,int y) { setSize(w,h);setPosition(x,y);placed=true; }
    public void setSize(int w,int h) { width=w;height=h; }
    public void setPosition(int x,int y) { this.x=x;this.y=y; }
    public void setX(int x) { this.x=x; }
    public void setY(int y) { this.y=y; }
    public int getX(){return x;} public int getY(){return y;}
    public int getWidth(){return width;} public int getHeight(){return height;}
    public int getRight(){return x+width;} public int getBottom(){return y+height;}
    public void setAlpha(float a){alpha=a;} public float getAlpha(){return alpha;}
    public void setFocused(boolean f){focused=f;} public boolean isFocused(){return focused;}
    public boolean isMouseOver(double a,double b){return a>=x&&a<x+width&&b>=y&&b<y+height;}
    public Component getMessage(){return message;}
    public void setMessage(Component value){message=value;}
    public void setTooltip(Tooltip t){tooltip=t;} public Tooltip getTooltip(){return tooltip;}
    public final void extractRenderState(GuiGraphicsExtractor g,int mx,int my,float tick){
        if(!visible)return;
        nativeRenders++;isHovered=isMouseOver(mx,my);
        extractWidgetRenderState(g,mx,my,tick);
        if(tooltip!=null&&(isHovered||focused))tooltipRenders++;
    }
    protected void extractWidgetRenderState(GuiGraphicsExtractor g,int mx,int my,float tick){}
    public void onClick(MouseButtonEvent e,boolean actual){}
    public boolean mouseClicked(MouseButtonEvent e,boolean actual){
        if(!active||!visible||!isMouseOver(e.x(),e.y()))return false;
        onClick(e,actual);return true;
    }
    public boolean keyPressed(KeyEvent e){return false;}
    public boolean mouseScrolled(double x,double y,double a,double b){return false;}
    public void playDownSound(Object o){}
    protected void updateWidgetNarration(NarrationElementOutput o){}
    protected void defaultButtonNarrationText(NarrationElementOutput o){}
}
""")
stub("net.minecraft.client.gui.screens.Screen", """
import net.minecraft.client.gui.*;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.input.*;
import net.minecraft.network.chat.Component;
public class Screen {
    public int width=480,height=360;
    protected final Font font = new Font();
    public final java.util.List<AbstractWidget> registered = new java.util.ArrayList<>();
    public Screen(Component c){}
    protected void init(){}
    protected void clearWidgets(){registered.clear();}
    protected <T extends AbstractWidget> T addWidget(T w){registered.add(w);return w;}
    public void extractRenderState(GuiGraphicsExtractor g,int x,int y,float tick){}
    public void extractBackground(GuiGraphicsExtractor g,int x,int y,float tick){}
    public void onClose(){}
    public void removed(){}
    public boolean shouldCloseOnEsc(){return true;}
    public boolean isPauseScreen(){return false;}
    public boolean keyPressed(KeyEvent e){return false;}
    public boolean mouseClicked(MouseButtonEvent e,boolean b){return false;}
    public boolean mouseScrolled(double x,double y,double a,double b){return false;}
}
""")
stub("net.minecraft.client.gui.components.EditBox", """
public class EditBox extends AbstractWidget {
    public EditBox(){super(0,0,1,1,net.minecraft.network.chat.Component.literal(""));}
}
""")
stub("net.minecraft.client.gui.GuiGraphicsExtractor", """
import java.util.*;
import net.minecraft.resources.Identifier;
import net.minecraft.network.chat.Component;
public class GuiGraphicsExtractor {
    public record Box(String kind,double x,double y,double w,double h,String text) {}
    public record Blit(int x,int y,int w,int h,float u,float v,int tw,int th,int tint) {}
    public final List<Box> boxes=new ArrayList<>();
    public final List<Blit> blits=new ArrayList<>();
    public final List<String> trace=new ArrayList<>();
    public int scissors;
    private final Pose pose=new Pose();
    public Pose pose(){return pose;}
    public void enableScissor(int x,int y,int r,int b){scissors++;}
    public void disableScissor(){if(--scissors<0)throw new AssertionError("裁剪栈不平衡");}
    public void nextStratum(){}
    public void box(String kind,double x,double y,double w,double h,String text){
        boxes.add(new Box(kind,pose.tx+x*pose.sx,pose.ty+y*pose.sy,w*pose.sx,h*pose.sy,text));
    }
    public void fill(int x,int y,int r,int b,int color){box("fill",x,y,r-x,b-y,"");}
    public void text(Font f,String s,int x,int y,int c,boolean shadow){box("text",x,y,f.width(s),f.lineHeight,s);}
    public void text(Font f,Component s,int x,int y,int c,boolean shadow){text(f,s.getString(),x,y,c,shadow);}
    public void blit(Object p,Identifier id,int x,int y,float u,float v,int w,int h,int tw,int th,int tint){
        blits.add(new Blit(x,y,w,h,u,v,tw,th,tint));box("image",x,y,w,h,"");
    }
    public void blit(Object p,Identifier id,int x,int y,float u,float v,int w,int h,int tw,int th){
        blit(p,id,x,y,u,v,w,h,tw,th,-1);
    }
    public static class Pose {
        double sx=1,sy=1,tx,ty;
        public final Deque<double[]> stack=new ArrayDeque<>();
        public float m00(){return (float)sx;} public float m11(){return (float)sy;}
        public void identity(){sx=sy=1;tx=ty=0;}
        public org.joml.Vector2f transformPosition(float x,float y,org.joml.Vector2f out){out.x=(float)(x*sx+tx);out.y=(float)(y*sy+ty);return out;}
        public void pushMatrix(){stack.push(new double[]{sx,sy,tx,ty});}
        public void popMatrix(){var a=stack.pop();sx=a[0];sy=a[1];tx=a[2];ty=a[3];}
        public void translate(float x,float y){tx+=x*sx;ty+=y*sy;}
        public void scale(float x,float y){sx*=x;sy*=y;}
    }
}
""")
stub("net.minecraft.stats.Stats", """
public class Stats {
    public static final Object PLAY_TIME=new Object();
    public static final Stats CUSTOM=new Stats();
    public Object get(Object x){return x;}
}
""")
stub("net.minecraft.client.player.LocalPlayer", """
import net.minecraft.network.chat.Component;
public class LocalPlayer {
    public final Connection connection=new Connection();
    public java.util.UUID getUUID(){return new java.util.UUID(1,2);}
    public Component getName(){return Component.literal("LongPlayerNameForLayoutCheck");}
    public void sendSystemMessage(Component c){}
    public Statistics getStats(){return new Statistics();}
    public static class Statistics {public int getValue(Object o){return 1000000;}}
    public static class Connection {public String command;public void sendCommand(String s){command=s;}}
}
""")
stub("net.minecraft.client.Minecraft", """
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.Identifier;
public class Minecraft {
    private static final Minecraft INSTANCE=new Minecraft();
    public Object connection=new Object();
    public Object getConnection(){return connection;}
    public final Font font=new Font();
    public LocalPlayer player=new LocalPlayer();
    public Level level=new Level();
    public final Gui gui=new Gui();
    public static int imageWidth=512,imageHeight=256;
    public static Minecraft getInstance(){return INSTANCE;}
    public void setScreenAndShow(Screen s){gui.setScreen(s);}
    public Object getSoundManager(){return this;}
    public Textures getTextureManager(){return new Textures();}
    public static class Gui {public Screen screen;public void setScreen(Screen s){screen=s;}}
    public static class Level {public Level dimension(){return this;} public Identifier identifier(){return new Identifier("minecraft","overworld");}}
    public static class Textures {public Texture getTexture(Identifier id){return new Texture();}}
    public static class Texture {public Texture getTexture(){return this;} public int getWidth(int i){return imageWidth;} public int getHeight(int i){return imageHeight;}}
}
""")
stub("net.minecraft.client.gui.screens.ConfirmLinkScreen", """
public class ConfirmLinkScreen {public static void confirmLinkNow(Screen s,java.net.URI uri){}}
""")
stub("net.minecraft.client.gui.screens.inventory.InventoryScreen", """
public class InventoryScreen {
    public static void extractEntityInInventoryFollowsMouse(net.minecraft.client.gui.GuiGraphicsExtractor g,
            int x,int y,int r,int b,int size,float p,int mx,int my,net.minecraft.client.player.LocalPlayer player){
        g.box("model",x,y,r-x,b-y,"");
    }
}
""")
stub("net.fabricmc.loader.api.FabricLoader", """
public class FabricLoader {
    public static FabricLoader getInstance(){return new FabricLoader();}
    public java.util.Optional<Container> getModContainer(String id){return java.util.Optional.of(new Container());}
    public static class Container {public Metadata getMetadata(){return new Metadata();}}
    public static class Metadata {public Version getVersion(){return new Version();}}
    public static class Version {public String getFriendlyString(){return "test";}}
}
""")
stub("net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking", """
public class ClientPlayNetworking {public static Object last;public static boolean supported=true;public static boolean canSend(Object id){return supported;}public static void send(Object p){last=p;}}
""")
stub(P + ".YouzaiworldCore", """public class YouzaiworldCore {public static final String MOD_ID="youzaiworldcore";}""")
stub(P + ".util.DebugLogger", """public class DebugLogger {public static void debug(String m,String s,Object...args){} public static void info(String m,String s,Object...args){}}""")
stub(P + ".network.AttributeUpgradePayload", """public record AttributeUpgradePayload(String key, long requestId) {}""")
stub(P + ".network.AttributeUpgradeResultPayload", "public record AttributeUpgradeResultPayload(long requestId,boolean success) {}")
stub(C + ".screen.PetScreen", "public class PetScreen extends net.minecraft.client.gui.screens.Screen {public PetScreen(net.minecraft.client.gui.screens.Screen p){super(net.minecraft.network.chat.Component.empty());}}")
stub(P + ".dimensionalinventories.WorldPoolTeleportPayload", """public record WorldPoolTeleportPayload(String poolId) {}""")
stub(P + ".network.WorldPoolMenuRequestPayload", """
public record WorldPoolMenuRequestPayload(java.util.UUID requestId, String poolId) { public static final Object ID = new Object(); }
""")
stub(P + ".network.WorldPoolMenuStatePayload", """
public record WorldPoolMenuStatePayload(java.util.UUID requestId, String currentPoolId, String currentName, java.util.List<Entry> entries) {
    public record Entry(String poolId, String name, String gameMode, boolean available) {}
}
""")
stub(P + ".network.WorldPoolTeleportResultPayload", """
public record WorldPoolTeleportResultPayload(java.util.UUID requestId, boolean success, net.minecraft.network.chat.Component message) {}
""")
stub(C + ".title.TitleClientState", """
public class TitleClientState { public static net.minecraft.network.chat.Component equippedComponent(java.util.UUID id) { return net.minecraft.network.chat.Component.empty(); } }
""")
stub(C + ".screen.map.YzWorldMapScreen", """
public class YzWorldMapScreen extends net.minecraft.client.gui.screens.Screen {
    public final net.minecraft.client.gui.screens.Screen parent;
    public static YzWorldMapScreen withWaypoints(net.minecraft.client.gui.screens.Screen parent){return new YzWorldMapScreen(parent);}
    public YzWorldMapScreen(net.minecraft.client.gui.screens.Screen parent) { super(net.minecraft.network.chat.Component.empty()); this.parent=parent; }
}
""")
stub(C + ".MailClientState", """public class MailClientState {public static int unreadCount=100;}""")
stub(C + ".hud.AdventureLevelHudRenderer", """
public class AdventureLevelHudRenderer {
    public static int getLevel(){return Integer.MAX_VALUE;}
    public static int getCurrentExp(){return Integer.MAX_VALUE-1;}
    public static int getNeededExp(){return Integer.MAX_VALUE;}
}
""")
stub(C + ".skill.ClientAttributeData", """
public class ClientAttributeData {
    public static int level=19,points=1,resistance=0;
    public static int getPlayerLevel(){return level;}
    public static int getSkillPointsAvailable(){return points;}
    public static int get(String k){return k.equals("damageResistance")?resistance:1000000;}
}
""")
stub(C + ".animation.GuiAnimationController", """
public class GuiAnimationController {
    public static boolean enabled=true, exiting, background;
    private static float opacity=1;
    public record ContentState(float opacity) {}
    public static boolean isEnabled(){return enabled;}
    public static boolean isDisabled(){return !enabled;}
    public static boolean isExiting(Object o){return exiting;}
    public static boolean isBackgroundRendering(){return background;}
    public static ContentState pushContent(YzuiMotion.Frame f){var old=new ContentState(opacity);opacity*=f.opacity();return old;}
    public static void restoreContent(ContentState s){opacity=s.opacity();}
    public static float contentOpacity(){return opacity;}
    public static void setScreenImmediately(net.minecraft.client.Minecraft.Gui gui, net.minecraft.client.gui.screens.Screen screen){exiting=false;gui.setScreen(screen);}
}
""")
stub(C + ".render.YzuiTheme", """
import net.minecraft.client.gui.*;
import net.minecraft.network.chat.Component;
import top.csituka.youzaiworldcore.client.config.YzuiVisualStyle;
public class YzuiTheme {
    public enum ButtonStyle {TONAL,FILLED,TEXT,DANGER}
    public static YzuiVisualStyle style=YzuiVisualStyle.STANDARD;
    public static YzuiVisualStyle visualStyle(){return style;}
    public static boolean frosted(){return style==YzuiVisualStyle.FROSTED;}
    public static int alpha(int c,float a){return (Math.round(Math.clamp(a,0f,1f)*255)<<24)|(c&0xffffff);}
    public static int multiplyAlpha(int c,float a){return alpha(c,(c>>>24)/255f*a);}
    public static int text(){return -1;} public static int textMuted(){return -1;}
    public static int primary(){return -1;} public static int success(){return -1;}
    public static int surfaceHigh(){return -1;} public static int outlineVariant(){return -1;}
    public static int error(){return -1;} public static int errorContainer(){return -1;}
    public static int legacyText(int c){return c;}
    public static int outline(){return -1;}
    public static int primaryContainer(){return -1;}
    public static YzuiPalette colors=YzuiPalette.LIGHT;
    public static YzuiPalette palette(){return colors;}
    public static int mix(int from,int to,float t) {
        int result=0;
        for(int shift=0;shift<=24;shift+=8) {
            int a=(from>>>shift)&255,b=(to>>>shift)&255;
            result|=Math.round(a+(b-a)*t)<<shift;
        }
        return result;
    }
    public static void wrapped(GuiGraphicsExtractor g,Font f,Component c,int x,int y,int w,int rows,int color){label(g,f,c,x,y,w,color,false);}

    public static int buttonText(ButtonStyle s,boolean active){return -1;}
    public static void label(GuiGraphicsExtractor g,Font f,Component c,int x,int y,int w,int color,boolean centered){
        int used=Math.min(w,f.width(c));
        g.box("text",centered?x+(w-used)/2:x,y,used,f.lineHeight,c.getString());
    }
    public static void card(GuiGraphicsExtractor g,int x,int y,int w,int h,float a){g.box("card",x,y,w,h,"");}
    public static void button(GuiGraphicsExtractor g,int x,int y,int w,int h,float hover,boolean focus,boolean active,float a,ButtonStyle s){g.box("button",x,y,w,h,"");}
    public static void border(GuiGraphicsExtractor g,int x,int y,int w,int h,int r,int c){g.box("border",x,y,w,h,"");}
}
""")
stub(C + ".screen.widget.ConfirmationDialog", """
public class ConfirmationDialog {
    public final Runnable confirm;private boolean visible;
    public ConfirmationDialog(String t,String[] lines,String label,Runnable c){confirm=c;}
    public ConfirmationDialog(String t,String[] lines,Runnable c,Runnable cancel){confirm=c;}
    public ConfirmationDialog(String t,String[] lines,String yes,String no,Runnable c,Runnable cancel){confirm=c;}
    public void init(int w,int h){} public void show(){visible=true;} public void hide(){visible=false;}
    public boolean isVisible(){return visible;}
    public void render(net.minecraft.client.gui.GuiGraphicsExtractor g,int w,int h){}
    public void renderButtons(net.minecraft.client.gui.GuiGraphicsExtractor g,int x,int y,float t){}
    public boolean keyPressed(net.minecraft.client.input.KeyEvent e){return true;}
    public boolean mouseClicked(double x,double y){return true;}
    public boolean mouseScrolled(double x,double y,double a,double b){return true;}
}
""")
stub(C + ".screen.widget.CheckboxButton", """
public class CheckboxButton extends TransparentButton {
    public CheckboxButton(int x,int y,int w,int h,net.minecraft.network.chat.Component c,boolean b,Runnable r){super(x,y,w,h,c,r);}
}
""")
stub(C + ".screen.widget.DropdownButton", """
import net.minecraft.network.chat.Component;
public class DropdownButton extends TransparentButton {
    private boolean open;
    public DropdownButton(int x,int y,int w,int pw,int h,Component c,java.util.List<String> options,int selected,
            boolean open,java.util.function.IntConsumer choose,Runnable toggle){super(x,y,w,h,c,toggle);this.open=open;}
    public boolean isOpen(){return open;}
    public void closePopup(){open=false;}
    public void setCanvasSize(int w,int h){}
    public void renderPopup(net.minecraft.client.gui.GuiGraphicsExtractor g,int x,int y,float t,int top,int bottom,double scroll){}
}
""")
for screen in ("MailScreen", "TitleManagementScreen", "YouzaiWorldCoreSettingsScreen", "YzuiAppearanceScreen"):
    stub(C + ".screen." + screen, "public class " + screen + """ extends net.minecraft.client.gui.screens.Screen {
    public """ + screen + """(net.minecraft.client.gui.screens.Screen... parent){super(net.minecraft.network.chat.Component.literal(""));}
}""")


def method(source, name):
    """原样提取实际生产方法，避免在测试中重新实现排版公式。"""
    found = re.search(r"    (?:private|public) (?:static )?[\w<>]+ " + re.escape(name) + r"\([^)]*\)\s*\{", source)
    assert found, name
    start = source.index("{", found.start())
    depth = 1
    end = start + 1
    while depth:
        depth += (source[end] == "{") - (source[end] == "}")
        end += 1
    return source[found.start():end]


def form_sources():
    result = []
    for name in ("LoginScreen", "RegisterScreen", "RegistrationEmailScreen", "PasswordResetScreen", "AccountManagementScreen"):
        source = (CLIENT / "screen" / (name + ".java")).read_text(encoding="utf-8")
        arrange = method(source, "arrangeForm")
        fields = sorted(set(re.findall(r"(\w+)\.setRectangle\(", arrange)))
        constants = "\n".join(re.findall(r"    private static final int (?:CARD_WIDTH|CARD_HEIGHT|CONTAINER_HEIGHT)\s*=\s*\d+;", source))
        body = """
import net.minecraft.network.chat.Component;
import net.minecraft.client.gui.components.AbstractWidget;
import top.csituka.youzaiworldcore.client.screen.widget.TransparentButton;
import top.csituka.youzaiworldcore.client.render.YzuiTheme;
import top.csituka.youzaiworldcore.client.animation.GuiAnimationController;
import net.minecraft.client.Minecraft;
public class NAME extends net.minecraft.client.gui.screens.Screen implements YzuiMenuScreen {
    public NAME(net.minecraft.client.gui.screens.Screen... parent){super(Component.literal(""));}
    public enum Mode {HOME,CHANGE_PASSWORD,CHANGE_EMAIL,DEACTIVATE}
    public Mode mode=Mode.HOME;
    public boolean processing, completed;
    private final net.minecraft.client.gui.screens.Screen previewParent = null;
    private final net.minecraft.client.gui.screens.Screen parent = null;
    private top.csituka.youzaiworldcore.client.screen.widget.ConfirmationDialog currentDialog;
    private void switchMode(Mode next) { mode=next; }
CLOSING
CONSTANTS
FIELDS
METHOD
EXTRA
    public java.util.List<AbstractWidget> arrange(int w,int h,Mode next) {
        width=w;height=h;mode=next;
        var all=java.util.List.<AbstractWidget>of(FIELD_NAMES);
        for(var widget:all)widget.placed=false;
        arrangeForm();
        return all.stream().filter(widget->widget.placed).toList();
    }
}
"""
        body = body.replace("class NAME", "class " + name).replace("public NAME(", "public " + name + "(")
        body = body.replace("CLOSING", method(source, "shouldCloseOnEsc") + "\n" + method(source, "onClose") + ("\n" + method(source, "closeMenu") if name in ("LoginScreen", "RegisterScreen") else ""))
        body = body.replace("CONSTANTS", constants)
        body = body.replace("FIELD_NAMES", ",".join(fields))
        body = body.replace("FIELDS", "\n".join("    private final TransparentButton " + f +
                    '=new TransparentButton(0,0,1,1,Component.literal(""),null);' for f in fields))
        body = body.replace("METHOD", arrange).replace("EXTRA",
                    method(source, "containerTop") if name == "AccountManagementScreen" else "")
        stub(C + ".screen." + name, body)
        result.append(name)
    return result


HARNESS = r"""
import java.util.*;
import java.lang.reflect.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.input.*;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import top.csituka.youzaiworldcore.client.animation.*;
import top.csituka.youzaiworldcore.client.render.*;
import top.csituka.youzaiworldcore.client.screen.*;
import top.csituka.youzaiworldcore.client.screen.element.*;
import top.csituka.youzaiworldcore.client.screen.widget.*;
import top.csituka.youzaiworldcore.client.skill.ClientAttributeData;

public class MenuChecks {
    static int checks,viewports;
    static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    static boolean intersects(double x,double y,double w,double h,double a,double b,double c,double d){
        return Math.min(x+w,a+c)>Math.max(x,a)+0.01 && Math.min(y+h,b+d)>Math.max(y,b)+0.01;
    }
    static void widgets(List<AbstractWidget> buttons,int w,int top,int bottom){
        for(int i=0;i<buttons.size();i++){
            var b=buttons.get(i);
            check(b.getWidth()>0&&b.getHeight()>0,"控件尺寸必须为正");
            check(b.getX()>=12&&b.getRight()<=w-12&&b.getY()>=top&&b.getBottom()<=bottom,"控件越界 "+b.getClass()+" "+b.getX()+","+b.getY()+","+b.getWidth()+","+b.getHeight());
            for(int j=0;j<i;j++){
                var a=buttons.get(j);
                check(!intersects(b.getX(),b.getY(),b.getWidth(),b.getHeight(),a.getX(),a.getY(),a.getWidth(),a.getHeight()),"控件重叠");
            }
        }
    }
    static class Menu extends MenuScreen {
        Menu(MenuElementGroup group){super(group);}
        void setup(int w,int h){width=w;height=h;init();}
    }
    static void geometry(){
        int[][] physical={{320,180},{427,240},{480,360},{619,360},{620,360},{640,361},{854,480},{960,540},{1280,720},{1920,1080},{3840,2160},{900,1600}};
        for(int[] size:physical){
            var viewport=YzuiLayout.fit(size[0],size[1],480,360);
            int w=viewport.width(),h=viewport.height();
            MenuLayout layout=new MenuLayout(w,h);
            check(layout.titleX()>=layout.backX()+24+8,"返回与标题没有间距");
            check(layout.titleX()+layout.titleWidth()<=layout.closeX()-8,"标题侵入关闭按钮");
            for(MenuElementGroup group:List.of(new MainMenuElements(),new SwitchWorldMenuElements(),
                    new AboutMeMenuElements(),new AdventureLevelMenuElements(),new SettingsMenuElements())){
                Menu menu=new Menu(group);menu.setup(w,h);
                List<AbstractWidget> buttons=group.createButtons(menu,w,h,1,1);
                widgets(buttons,w,MenuLayout.CONTENT_TOP,layout.contentBottom());
                if(group instanceof MainMenuElements||group instanceof SwitchWorldMenuElements){
                    check(buttons.size()==10,"导航数量不匹配");
                    if(group instanceof SwitchWorldMenuElements) for(var button:buttons)check(button.getWidth()==button.getHeight()*2,"世界按钮比例变形");
                }
                GuiGraphicsExtractor g=new GuiGraphicsExtractor();
                group.renderCustomBackground(g,w,h,1,0);
                group.renderCustomContent(g,w,h,1,0,w/2,h/2);
                check(g.scissors==0&&g.pose().stack.isEmpty(),"页面裁剪/矩阵未恢复");
                for(var box:g.boxes){
                    check(box.x()>=12&&box.x()+box.w()<=w-12&&box.y()>=MenuLayout.CONTENT_TOP&&box.y()+box.h()<=layout.contentBottom(),"内容越过保留区域 "+box);
                    if(!(group instanceof MainMenuElements)&&(box.kind().equals("text")||box.kind().equals("model"))){
                        for(var button:buttons)check(!intersects(box.x(),box.y(),box.w(),box.h(),button.getX(),button.getY(),button.getWidth(),button.getHeight()),"正文/模型遮挡控件 "+box);
                    }
                }
                var models=g.boxes.stream().filter(b->b.kind().equals("model")).toList();
                for(var model:models)for(var text:g.boxes)if(text.kind().equals("text"))
                    check(!intersects(model.x(),model.y(),model.w(),model.h(),text.x(),text.y(),text.w(),text.h()),"玩家模型与信息重叠");
                viewports++;
            }
        }
        ClientAttributeData.level=19;ClientAttributeData.points=1;
        var page=new AdventureLevelMenuElements();
        var buttons=page.createButtons(new Menu(page),480,360,1,1);
        check(buttons.stream().filter(b->b.active).count()==1&&buttons.get(8).active,"低等级抗性例外改变");
        ClientAttributeData.level=20;page.updateButtons(buttons);
        check(buttons.stream().allMatch(b->b.active),"等级变化后旧按钮未更新");
        ClientAttributeData.points=0;page.updateButtons(buttons);
        check(buttons.stream().noneMatch(b->b.active),"点数耗尽后旧按钮未禁用");
    }
    static void forms() throws Exception {
        String[] names={"LoginScreen","RegisterScreen","RegistrationEmailScreen","PasswordResetScreen","AccountManagementScreen"};
        for(String name:names){
            Class<?> type=Class.forName("top.csituka.youzaiworldcore.client.screen."+name);
            var instance=type.getConstructor(net.minecraft.client.gui.screens.Screen[].class).newInstance((Object)new net.minecraft.client.gui.screens.Screen[0]);
            var screen=(net.minecraft.client.gui.screens.Screen)instance;
            var client=Minecraft.getInstance();client.gui.setScreen(screen);
            if(!name.equals("AccountManagementScreen")) {
                check(!screen.shouldCloseOnEsc(),"认证页面禁止 Esc 关闭");
                screen.onClose();
                check(client.gui.screen==screen,"认证页面 onClose 不得绕过关闭限制");
            } else {
                for(String flag:new String[]{"processing","completed"}) {
                    Field field=type.getField(flag);field.setBoolean(instance,true);
                    check(!screen.shouldCloseOnEsc(),"账户操作期间禁止关闭");
                    screen.onClose();check(client.gui.screen==screen,"账户操作期间 onClose 不得关闭");
                    field.setBoolean(instance,false);
                }
                screen.onClose();check(client.gui.screen==null,"账户页面空闲时立即关闭");
            }
            Class<?> mode=Class.forName(type.getName()+"$Mode");
            Method arrange=type.getMethod("arrange",int.class,int.class,mode);
            for(int[] size:new int[][]{{480,360},{620,360},{854,480},{1920,1080}}){
                for(Object value:mode.getEnumConstants()){
                    if(!name.equals("AccountManagementScreen")&&!value.toString().equals("HOME"))continue;
                    @SuppressWarnings("unchecked") var buttons=(List<AbstractWidget>)arrange.invoke(instance,size[0],size[1],value);
                    check(!buttons.isEmpty(),"表单未定位控件");
                    widgets(buttons,size[0],8,size[1]-8);
                    for(var b:buttons)check(b.getHeight()>=20&&b.getHeight()<=32&&b.getWidth()>=48,"setRectangle 宽高和坐标再次交换");
                }
            }
        }
    }
    static Set<String> pixels(GuiGraphicsExtractor g,boolean texture){
        Set<String> result=new HashSet<>();
        if(texture){
            for(var b:g.blits)for(int y=b.y();y<b.y()+b.h();y++)for(int x=b.x();x<b.x()+b.w();x++)
                check(result.add(x+","+y),"图片圆角重复混合");
        }else{
            for(var b:g.boxes)if(b.kind().equals("fill"))for(int y=(int)b.y();y<b.y()+b.h();y++)for(int x=(int)b.x();x<b.x()+b.w();x++)
                check(result.add(x+","+y),"基准圆角重复混合");
        }
        return result;
    }
    static void images(){
        var id=Identifier.fromNamespaceAndPath("youzaiworldcore","textures/gui/mail.png");
        for(int h:new int[]{28,48,56,76})for(int[] image:new int[][]{{512,256},{512,512},{512,1024},{1024,512}}){
            int w=h*2,r=Math.min(12,h/4);
            var base=new GuiGraphicsExtractor();var tex=new GuiGraphicsExtractor();
            RoundedRect.fill(base,7,9,w,h,r,-1);
            RoundedRect.texture(tex,id,7,9,w,h,r,image[0],image[1],0x80ffffff);
            check(pixels(base,false).equals(pixels(tex,true)),"圆角图片边界与状态层不一致");
            for(var b:tex.blits){
                check(b.u()>=0&&b.v()>=0&&b.u()+b.w()<=b.tw()&&b.v()+b.h()<=b.th(),"图片 UV 越界");
                check(b.tint()==0x80ffffff,"条带丢失透明度");
                check(Math.abs(b.tw()/(double)b.th()-image[0]/(double)image[1])<0.02,"旧图被非等比拉伸");
            }
            if(image[0]==2*image[1])for(var b:tex.blits)check(b.tw()==w&&b.th()==h,"2:1 图片不应额外裁切");
        }
        int[] presses={0};var tile=new TextureTileButton(12,12,112,56,id,()->presses[0]++);
        tile.setExternalAlpha(0.4f);
        var g=new GuiGraphicsExtractor();
        tile.extractRenderState(g,16,16,0);
        check(g.boxes.stream().noneMatch(b->b.kind().equals("text")),"图片按钮仍绘制文字");
        check(tile.getTooltip()!=null&&tile.tooltipRenders==1,"名称提示没有走原版入口");
        check(g.blits.stream().allMatch(b->(b.tint()>>>24)==102),"图片未继承外部透明度");
        tile.setFocused(true);tile.keyPressed(new KeyEvent(40,false));
        tile.mouseClicked(new MouseButtonEvent(16,16,1),true);
        check(presses[0]==2,"图片按钮的鼠标/键盘激活丢失");
        tile.active=false;tile.keyPressed(new KeyEvent(40,false));
        tile.mouseClicked(new MouseButtonEvent(16,16,1),true);
        check(presses[0]==2,"禁用图片按钮仍能触发");
    }
    static class TrackingPage implements MenuElementGroup {
        final String name; final boolean root; int builds,presses; float opacity; boolean fail;
        TrackingPage(String name,boolean root){this.name=name;this.root=root;}
        public String getTitleText(){return name;} public String getSubtitleText(){return null;}
        public boolean isRoot(){return root;}
        public List<AbstractWidget> createButtons(MenuScreen s,int w,int h,float scale,float a){
            builds++;return List.of(new TextureTileButton(80,100,112,56,
                    Identifier.fromNamespaceAndPath("youzaiworldcore","textures/gui/mail.png"),()->presses++));
        }
        public void renderCustomContent(GuiGraphicsExtractor g,int w,int h,float a,float x,int mx,int my){
            opacity=GuiAnimationController.contentOpacity();g.trace.add(name);if(fail)throw new IllegalStateException("expected");
        }
    }
    static void time(Menu s,long elapsed) throws Exception {
        Field f=MenuScreen.class.getDeclaredField("transitionStartTime");f.setAccessible(true);f.setLong(s,System.currentTimeMillis()-elapsed);
    }
    static void lifecycle() throws Exception {
        var root=new TrackingPage("root",true);var child=new TrackingPage("child",false);
        var menu=new Menu(root);menu.setup(480,360);
        var original=menu.registered.get(0);
        for(int i=0;i<5;i++)menu.extractRenderState(new GuiGraphicsExtractor(),90,110,0);
        check(root.builds==1&&menu.registered.get(0)==original,"每帧重建按钮");
        check(original.nativeRenders==5&&original.tooltipRenders==5,"原版提示/渲染入口被绕过");
        menu.switchTo(child);
        menu.mouseClicked(new MouseButtonEvent(90,110,1),true);
        check(root.presses==0,"过渡期间可以点击旧页面");
        time(menu,100);var mid=new GuiGraphicsExtractor();menu.extractRenderState(mid,90,110,0);
        check(mid.trace.equals(List.of("child")),"过渡期间两页内容重叠");
        check(child.opacity>0&&child.opacity<1,"模型/自定义内容未继承页面淡入");
        check(GuiAnimationController.contentOpacity()==1&&mid.scissors==0&&mid.pose().stack.isEmpty(),"页面状态未恢复");
        time(menu,1000);menu.extractRenderState(new GuiGraphicsExtractor(),90,110,0);
        menu.goBack();time(menu,1000);menu.extractRenderState(new GuiGraphicsExtractor(),90,110,0);
        check(root.builds==1&&menu.registered.get(0)==original,"返回页面丢失控件状态");
        Field cache=MenuScreen.class.getDeclaredField("pageButtons");cache.setAccessible(true);
        check(((Map<?,?>)cache.get(menu)).size()==1,"返回后保留了无用子页面");
        menu.setup(854,480);
        check(root.builds==2&&menu.registered.get(0)!=original,"调整窗口后未重新排版");
        root.fail=true;var failed=new GuiGraphicsExtractor();
        try{menu.extractRenderState(failed,0,0,0);throw new AssertionError("测试异常未抛出");}catch(IllegalStateException expected){}
        check(failed.scissors==0&&failed.pose().stack.isEmpty()&&GuiAnimationController.contentOpacity()==1,"渲染异常未恢复状态");
        root.fail=false;GuiAnimationController.enabled=false;
        menu.switchTo(child);var instant=new GuiGraphicsExtractor();menu.extractRenderState(instant,90,110,0);
        check(instant.trace.equals(List.of("child")),"关闭动效后切换未立即完成");
        GuiAnimationController.enabled=true;
    }
    static void worldActions() throws Exception {
        var client=Minecraft.getInstance();
        var page=new SwitchWorldMenuElements();var menu=new Menu(page);menu.setup(480,360);client.gui.setScreen(menu);
        var request=(top.csituka.youzaiworldcore.network.WorldPoolMenuRequestPayload)net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.last;
        check(request.poolId().isEmpty(),"打开世界页只能请求目录");
        String[] pools={"survival_world_pool","main_city_pool","gameplay_pool","creation_pool","building_pool","tutorial_world_pool",null,null,"commands_pool",null};
        var entries=new ArrayList<top.csituka.youzaiworldcore.network.WorldPoolMenuStatePayload.Entry>();
        for(String pool:pools)if(pool!=null)entries.add(new top.csituka.youzaiworldcore.network.WorldPoolMenuStatePayload.Entry(pool,"Test world","survival",true));
        page.applyState(new top.csituka.youzaiworldcore.network.WorldPoolMenuStatePayload(request.requestId(),"","",entries));
        var buttons=page.createButtons(menu,480,360,1,1);
        Field dialogField=MenuScreen.class.getDeclaredField("currentDialog");dialogField.setAccessible(true);
        for(int i=0;i<pools.length;i++)if(pools[i]!=null){
            net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.last=null;
            page.updateButtons(buttons);
            var b=buttons.get(i);b.mouseClicked(new MouseButtonEvent(b.getX()+1,b.getY()+1,1),true);
            check(net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.last==null,"确认之前发起传送");
            var dialog=(ConfirmationDialog)dialogField.get(menu);check(dialog.isVisible(),"传送确认未打开");dialog.confirm.run();
            var sent=(top.csituka.youzaiworldcore.network.WorldPoolMenuRequestPayload)net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.last;
            check(sent.poolId().equals(pools[i]),"世界入口的维度池映射改变");
            page.applyResult(menu,new top.csituka.youzaiworldcore.network.WorldPoolTeleportResultPayload(UUID.randomUUID(),true,Component.literal("late")));
            check(client.gui.screen==menu,"迟到结果关闭当前页面");
            page.updateButtons(buttons);check(!b.active,"等待期间允许重复传送");
            page.applyResult(menu,new top.csituka.youzaiworldcore.network.WorldPoolTeleportResultPayload(sent.requestId(),false,Component.literal("failed")));
            check(client.gui.screen==menu,"失败必须保留世界页");
            page.updateButtons(buttons);check(b.active,"失败后不能重新选择世界");
        }
        page.applyState(new top.csituka.youzaiworldcore.network.WorldPoolMenuStatePayload(request.requestId(),pools[0],"Test world",entries));
        page.updateButtons(buttons);check(!buttons.get(0).active,"当前世界仍可重复选择");
        page.applyState(new top.csituka.youzaiworldcore.network.WorldPoolMenuStatePayload(request.requestId(),"","",List.of()));
        page.updateButtons(buttons);check(!buttons.get(1).active,"未配置世界仍可选择");
        page.applyState(new top.csituka.youzaiworldcore.network.WorldPoolMenuStatePayload(request.requestId(),"","",entries));
        page.updateButtons(buttons);var destination=buttons.get(0);
        destination.mouseClicked(new MouseButtonEvent(destination.getX()+1,destination.getY()+1,1),true);
        ((ConfirmationDialog)dialogField.get(menu)).confirm.run();
        var oldRequest=(top.csituka.youzaiworldcore.network.WorldPoolMenuRequestPayload)net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.last;
        menu.setup(854,480);
        check(net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.last==oldRequest,"调整窗口重新发起请求");
        page.updateButtons(buttons);check(!buttons.get(0).active,"调整窗口丢失等待状态");
        page.onShown();
        var reopened=(top.csituka.youzaiworldcore.network.WorldPoolMenuRequestPayload)net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.last;
        page.applyResult(menu,new top.csituka.youzaiworldcore.network.WorldPoolTeleportResultPayload(oldRequest.requestId(),true,Component.literal("old success")));
        check(client.gui.screen==menu,"重新进入后旧请求关闭新页面");
        page.applyState(new top.csituka.youzaiworldcore.network.WorldPoolMenuStatePayload(oldRequest.requestId(),"","",entries));
        page.updateButtons(buttons);check(!buttons.get(0).active,"旧目录污染重新打开的页面");
        Field metadataTime=SwitchWorldMenuElements.class.getDeclaredField("metadataRequestedAt");metadataTime.setAccessible(true);
        metadataTime.setLong(page,System.currentTimeMillis()-16000);page.updateButtons(buttons);
        Field statusField=SwitchWorldMenuElements.class.getDeclaredField("status");statusField.setAccessible(true);
        check(((Component)statusField.get(page)).getString().endsWith("load_timeout"),"世界信息加载超时无反馈");
        page.applyState(new top.csituka.youzaiworldcore.network.WorldPoolMenuStatePayload(reopened.requestId(),"","",entries));
        page.updateButtons(buttons);check(buttons.get(0).active,"超时后收到有效目录未恢复");
        destination.mouseClicked(new MouseButtonEvent(destination.getX()+1,destination.getY()+1,1),true);
        ((ConfirmationDialog)dialogField.get(menu)).confirm.run();
        var newRequest=(top.csituka.youzaiworldcore.network.WorldPoolMenuRequestPayload)net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.last;
        page.applyResult(menu,new top.csituka.youzaiworldcore.network.WorldPoolTeleportResultPayload(newRequest.requestId(),true,Component.literal("success")));
        check(client.gui.screen==null,"服务端确认成功后未关闭菜单");
        var tooltip=new MenuNavigationButton(0,0,100,50,"survival_world",MenuIcon.WORLD,()->{});
        tooltip.setMessage(Component.literal("Old name"));tooltip.setDetail(Component.literal("survival"));
        tooltip.setMessage(Component.literal("New name"));tooltip.setDetail(Component.literal("survival"));
        check(tooltip.getTooltip().message().getString().contains("New name"),"世界改名后提示仍显示旧名称");
        var home=new MainMenuElements();var root=new Menu(home);root.setup(480,360);
        var homeButtons=home.createButtons(root,480,360,1,1);
        var map=homeButtons.get(2);map.mouseClicked(new MouseButtonEvent(map.getX()+1,map.getY()+1,1),true);
        check(client.gui.screen instanceof top.csituka.youzaiworldcore.client.screen.map.YzWorldMapScreen value && value.parent==root,"地图入口未保留返回目标");
    }
    static void closure() {
        var client=Minecraft.getInstance();
        var menu=new Menu(new MainMenuElements());menu.setup(480,360);client.gui.setScreen(menu);
        menu.showDialog(new ConfirmationDialog("",new String[]{""},"",null));
        menu.keyPressed(new KeyEvent(41,false));
        check(client.gui.screen==menu && !menu.hasDialog(),"Esc 应先取消弹窗并保留菜单");
        menu.closeDialog();
        client.gui.setScreen(menu);menu.switchTo(new SwitchWorldMenuElements());
        menu.keyPressed(new KeyEvent(41,false));
        check(client.gui.screen==menu,"前进动画期间 Esc 应返回原页面");
        menu.keyPressed(new KeyEvent(41,false));check(client.gui.screen==null,"首页 Esc 应回到游戏");
        class Locked extends net.minecraft.client.gui.screens.Screen implements YzuiMenuScreen {
            boolean locked=true;
            Locked(){super(Component.literal(""));}
            public boolean shouldCloseOnEsc(){return !locked;}
        }
        var locked=new Locked();client.gui.setScreen(locked);
        var close=new MenuCloseButton(0,0,locked);
        check(!close.visible&&!close.active,"受限页面不得显示关闭按钮");
        check(!YzuiMenuScreen.handleEscape(locked,new KeyEvent(41,false)),"受限页面 Esc 不能关闭");
        close.onClick(new MouseButtonEvent(2,2,1),true);
        check(client.gui.screen==locked,"受限页面不得通过按钮关闭");
        locked.locked=false;close.refresh();
        check(close.visible&&close.active,"解除锁定后恢复关闭按钮");
        close.onClick(new MouseButtonEvent(2,2,1),true);
        check(client.gui.screen==null,"关闭按钮应回到游戏");
        for(MenuIcon icon:MenuIcon.values()) {
            var button=new MenuNavigationButton(20,20,112,56,"mail",icon,()->{});
            var g=new GuiGraphicsExtractor();button.extractRenderState(g,25,25,0);
            check(g.blits.isEmpty(),"菜单入口不再依赖图片中的文字");
            check(g.boxes.stream().anyMatch(b->b.kind().equals("text")),"导航必须显示本地化文字");
        }
    }
    static void contrast() {
        for(var style:top.csituka.youzaiworldcore.client.config.YzuiVisualStyle.values()) {
            YzuiTheme.style=style;
            float opacity=YzuiMenuPanel.opacity();
            check(opacity<=0.74f,"菜单表面不应退回厚重底板");
            for(var palette:new YzuiPalette[]{YzuiPalette.LIGHT,YzuiPalette.DARK}) {
                YzuiTheme.colors=palette;
                for(float state:new float[]{0.08f,0.26f,0.34f}) {
                    int surface=YzuiTheme.mix(palette.surface(),palette.primaryContainer(),state);
                    int foreground=YzuiMenuPanel.textOn(palette.text(),surface);
                    for(int world:new int[]{0,0xffffff}) {
                        check(YzuiPalette.contrast(foreground,YzuiPalette.composite(surface,world,opacity))>=4.5,
                                "悬停和焦点状态的卡片文字对比度不足");
                    }
                }
                for(int color:new int[]{palette.text(),palette.textMuted(),palette.error(),palette.success(),palette.warning(),palette.info()}) {
                    int readable=palette.translucentText(color,opacity);
                    for(int background:new int[]{0,0xFFFFFF,0x336633,0x6699FF}) {
                        int surface=YzuiPalette.composite(palette.surface(),background,opacity);
                        check(YzuiPalette.contrast(readable,surface)>=4.5,"通透表面上的文字对比度不足");
                    }
                }
            }
        }
    }
    static void keyboard()throws Exception{
        var home=new MainMenuElements();var menu=new Menu(home);menu.setup(854,480);
        var client=Minecraft.getInstance();client.gui.setScreen(menu);
        menu.keyPressed(new KeyEvent(79,false));check(menu.registered.get(0).isFocused(),"方向键未建立初始焦点");
        menu.keyPressed(new KeyEvent(79,false));check(menu.registered.get(1).isFocused(),"向右未移到下一张卡片");
        menu.keyPressed(new KeyEvent(81,false));check(menu.registered.get(4).isFocused(),"向下未移到同列卡片");
        menu.keyPressed(new KeyEvent(80,false));check(menu.registered.get(3).isFocused(),"向左未移到同排卡片");
        menu.setup(480,360);check(menu.registered.get(3).isFocused(),"窗口重排丢失当前入口焦点");
        GuiAnimationController.enabled=false;
        menu.switchTo(new AboutMeMenuElements());menu.onClose();
        check(menu.registered.get(3).isFocused(),"返回父菜单后焦点未恢复原入口");
        var plain=new ArrayList<AbstractWidget>();
        for(int i=0;i<3;i++)plain.add(new TransparentButton(i*60,0,50,24,Component.literal("item"+i),()->{}));
        plain.get(0).setFocused(true);plain.get(1).active=false;
        WidgetFocus.moveInGrid(new KeyEvent(79,false),plain);
        check(plain.get(2).isFocused(),"方向键没有跳过禁用控件");
        WidgetFocus.moveInGrid(new KeyEvent(79,false),plain);
        check(plain.get(2).isFocused(),"边缘方向键跳到无关控件");
        var direct=new Menu(new AboutMeMenuElements());direct.setup(480,360);client.gui.setScreen(direct);
        var back=MenuScreen.class.getDeclaredField("backButton");back.setAccessible(true);
        var button=(AbstractWidget)back.get(direct);
        button.mouseClicked(new MouseButtonEvent(button.getX()+1,button.getY()+1,1),true);
        check(client.gui.screen==null,"直接打开子页的返回按钮无效");
        GuiAnimationController.enabled=true;
    }
    static void upgrades()throws Exception{
        var client=Minecraft.getInstance();
        var data=top.csituka.youzaiworldcore.client.skill.ClientAttributeData.class;
        var group=new AdventureLevelMenuElements();var menu=new Menu(group);menu.setup(480,360);
        ClientAttributeData.resistance=40;group.updateButtons(menu.registered);
        check(!menu.registered.get(8).active,"满抗性仍可消耗点数");ClientAttributeData.resistance=0;
        top.csituka.youzaiworldcore.client.skill.AttributeUpgradeRequests.submit("damageResistance");
        var sent=(top.csituka.youzaiworldcore.network.AttributeUpgradePayload)net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.last;
        check(top.csituka.youzaiworldcore.client.skill.AttributeUpgradeRequests.waiting(),"加点没有等待服务端结果");
        top.csituka.youzaiworldcore.client.skill.AttributeUpgradeRequests.submit("luck");
        check(net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.last==sent,"加点等待期间重复提交");
        top.csituka.youzaiworldcore.client.skill.AttributeUpgradeRequests.receive(new top.csituka.youzaiworldcore.network.AttributeUpgradeResultPayload(sent.requestId()+1,true),client.getConnection());
        check(top.csituka.youzaiworldcore.client.skill.AttributeUpgradeRequests.waiting(),"迟到加点结果误匹配");
        top.csituka.youzaiworldcore.client.skill.AttributeUpgradeRequests.receive(new top.csituka.youzaiworldcore.network.AttributeUpgradeResultPayload(sent.requestId(),true),new Object());
        check(top.csituka.youzaiworldcore.client.skill.AttributeUpgradeRequests.waiting(),"旧连接加点结果误匹配");
        top.csituka.youzaiworldcore.client.skill.AttributeUpgradeRequests.receive(new top.csituka.youzaiworldcore.network.AttributeUpgradeResultPayload(sent.requestId(),false),client.getConnection());
        check(!top.csituka.youzaiworldcore.client.skill.AttributeUpgradeRequests.waiting(),"拒绝加点后按钮未恢复");
        top.csituka.youzaiworldcore.client.skill.AttributeUpgradeRequests.submit("luck");
        var deadline=top.csituka.youzaiworldcore.client.skill.AttributeUpgradeRequests.class.getDeclaredField("deadline");deadline.setAccessible(true);deadline.setLong(null,0);
        check(!top.csituka.youzaiworldcore.client.skill.AttributeUpgradeRequests.waiting(),"加点请求未超时");
    }
    public static void main(String[] args)throws Exception{
        geometry();forms();images();lifecycle();worldActions();closure();contrast();keyboard();upgrades();
        System.out.println("Shift+F 回归通过："+viewports+" 组页面/窗口组合；表单定位、卡片图标/文字、控件复用、即时关闭/锁定、分区过渡与 7 个维度池入口。");
    }
}
"""


def check_navigation():
    import json
    names = []
    for filename in ("MainMenuElements.java", "MoreMenuElements.java", "SwitchWorldMenuElements.java"):
        source = (CLIENT / "screen/element" / filename).read_text(encoding="utf-8")
        entries = re.search(r"String\[\] ENTRIES = \{(.*?)\};", source, re.S).group(1)
        names.extend(re.findall(r'"([^"]+)"', entries))
    assert len(names) == len(set(names)) == 22, "导航入口数量不符"
    for path in (ROOT / "src/main/resources/assets/youzaiworldcore/lang").glob("*.json"):
        language = json.loads(path.read_text(encoding="utf-8"))
        assert all("screen.youzaiworldcore.navigation." + name in language for name in names), path
    print("22 个导航入口的十种语言检查通过。")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--java-home")
    args = parser.parse_args()
    binary = Path(args.java_home) / "bin" if args.java_home else None
    suffix = ".exe" if os.name == "nt" else ""
    javac = str(binary / ("javac" + suffix)) if binary else "javac"
    java = str(binary / ("java" + suffix)) if binary else "java"
    form_sources()
    production = [
        "animation/YzuiHover.java", "animation/YzuiMotion.java", "config/YzuiVisualStyle.java",
        "render/YzuiLayout.java", "render/RoundedRect.java", "render/YzuiPalette.java",
        "render/MenuIcon.java", "render/YzuiMenuPanel.java", "screen/YzuiMenuScreen.java",
        "screen/widget/MenuNavigationButton.java", "screen/widget/MenuCloseButton.java",
        "screen/MenuScreen.java", "screen/widget/TransparentButton.java", "screen/widget/TextureTileButton.java",
        "screen/widget/WidgetFocus.java", "skill/AttributeUpgradeRequests.java",
        *["screen/element/" + name + ".java" for name in (
            "MenuLayout", "MenuElementGroup", "MainMenuElements", "MoreMenuElements", "SwitchWorldMenuElements",
            "AboutMeMenuElements", "AdventureLevelMenuElements", "SettingsMenuElements")],
    ]
    with tempfile.TemporaryDirectory(prefix=".tmp-yzui-menu-", dir=ROOT) as directory:
        temporary = Path(directory)
        sources = []
        for path, source in STUBS.items():
            file = temporary / path
            file.parent.mkdir(parents=True, exist_ok=True)
            file.write_text(source, encoding="utf-8")
            sources.append(str(file))
        driver = temporary / "MenuChecks.java"
        driver.write_text(HARNESS, encoding="utf-8")
        subprocess.run([javac, "--release", "25", "-encoding", "UTF-8", "-d", str(temporary),
                        *sources, *[str(CLIENT / path) for path in production], str(driver)], cwd=ROOT, check=True)
        subprocess.run([java, "-Dstdout.encoding=UTF-8", "-Dstderr.encoding=UTF-8",
                        "-cp", str(temporary), "MenuChecks"], cwd=ROOT, check=True)
    check_navigation()


if __name__ == "__main__":
    main()
