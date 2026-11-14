"""邮件返回与丢弃确认回归：提取实际初始化、快照和退出方法运行。

编译实际 MailBaseScreen/MailViewport；控件、绘制及网络依赖使用替身。
不启动游戏，也不验证邮件发布接口或实际物品序列化。
"""
from pathlib import Path
import argparse
import os
import subprocess
import tempfile
import verify_yzui_menu as fixtures

ROOT, CLIENT, P, C = fixtures.ROOT, fixtures.CLIENT, fixtures.P, fixtures.C
STUBS = {path: source for path, source in fixtures.STUBS.items()
         if path.startswith(("net/minecraft/", "com/mojang/", "org/joml/"))}


def stub(name, body):
    package, _, _ = name.rpartition(".")
    STUBS[name.replace(".", "/") + ".java"] = "package " + package + ";\n" + body


screen = "net/minecraft/client/gui/screens/Screen.java"
STUBS[screen] = STUBS[screen].replace("public boolean mouseScrolled", """
    protected <T extends AbstractWidget> T addRenderableWidget(T widget){return addWidget(widget);}
    public boolean mouseReleased(MouseButtonEvent event){return false;}
    public boolean mouseDragged(MouseButtonEvent event,double x,double y){return false;}
    public boolean mouseScrolled""")
event = "net/minecraft/client/input/MouseButtonEvent.java"
STUBS[event] = STUBS[event].replace("{}", "{public int buttonInfo(){return button;}}")
stub("net.minecraft.world.item.ItemStack", """
public class ItemStack {
    public static final ItemStack EMPTY=new ItemStack("",0,"");
    public final String item,components;
    public final int count;
    public ItemStack(String item,int count,String components){this.item=item;this.count=count;this.components=components;}
    public boolean isEmpty(){return count==0;}
    public ItemStack copy(){return new ItemStack(item,count,components);}
    public static boolean matches(ItemStack a,ItemStack b){
        return a.item.equals(b.item)&&a.count==b.count&&a.components.equals(b.components);
    }
}
""")
stub(C + ".animation.GuiAnimationController", """
public class GuiAnimationController {
    public static boolean isExiting(Object o){return false;}
    public static boolean isBackgroundRendering(){return false;}
    public static void setScreenImmediately(net.minecraft.client.Minecraft.Gui gui,net.minecraft.client.gui.screens.Screen screen){gui.setScreen(screen);}
}
""")
stub(P + ".util.DebugLogger", """
public class DebugLogger {
    public static void debug(String m,String s,Object...args){}
    public static void info(String m,String s,Object...args){}
}
""")
stub(C + ".render.YzuiTheme", "public class YzuiTheme {}")
stub(C + ".screen.element.MainMenuElements", "public class MainMenuElements {}")
stub(C + ".screen.MenuScreen", """
public class MenuScreen extends net.minecraft.client.gui.screens.Screen {
    public MenuScreen(Object group){super(net.minecraft.network.chat.Component.empty());}
}
""")
stub(C + ".screen.MailToast", """
public class MailToast {
    public static void clear(){}
    public static void render(net.minecraft.client.gui.GuiGraphicsExtractor g,net.minecraft.client.gui.Font f,int width){}
}
""")
for name in ("MailScreen", "MailSentScreen"):
    stub(C + ".screen." + name, "public class " + name + """ extends MailBaseScreen {
        public """ + name + """(){super(net.minecraft.network.chat.Component.empty());}
        protected void renderMailContent(net.minecraft.client.gui.GuiGraphicsExtractor g,int x,int y,float t){}
    }""")
stub(C + ".screen.MailUi", """
public class MailUi {
    public record Rect(int x,int y,int width,int height){public int right(){return x+width;}}
    public static Rect centeredPage(int width,int height){return new Rect(0,0,width,height);}
}
""")
stub(C + ".screen.widget.MenuCloseButton", """
public class MenuCloseButton extends net.minecraft.client.gui.components.AbstractWidget {
    public MenuCloseButton(int x,int y,Object owner){super(x,y,24,24,net.minecraft.network.chat.Component.empty());}
}
""")
stub(C + ".screen.widget.DropdownButton", """
public class DropdownButton {
    public boolean open;
    public int index;
    public DropdownButton(int value){index=value;}
    public boolean isOpen(){return open;}
    public void closePopup(){open=false;}
    public int getSelectedIndex(){return index;}
}
""")
stub(C + ".screen.widget.ConfirmationDialog", """
public class ConfirmationDialog {
    public final Runnable confirm,cancel;
    private boolean visible;
    public ConfirmationDialog(String title,String[] messages,String yes,String no,Runnable confirm,Runnable cancel){
        this.confirm=()->{hide();if(confirm!=null)confirm.run();};
        this.cancel=()->{hide();if(cancel!=null)cancel.run();};
    }
    public void init(int w,int h){}
    public void show(){visible=true;}
    public void hide(){visible=false;}
    public boolean isVisible(){return visible;}
}
""")
stub(C + ".MailClientState", """
public class MailClientState {
    public static Pending pendingEditData;
    public record Pending(Object mail){}
}
""")
stub("net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking", """
public class ClientPlayNetworking {
    public static final java.util.List<Object> sent=new java.util.ArrayList<>();
    public static void send(Object payload){sent.add(payload);}
}
""")
stub(P + ".mail.MailType", "public enum MailType {ANNOUNCEMENT}")
stub(P + ".network.MailAdminEditPayload", """
public record MailAdminEditPayload(java.util.UUID mailId,boolean cancel,java.util.List<?> targets,
    top.csituka.youzaiworldcore.mail.MailType type,String title,String body,byte expiration,java.util.List<?> attachments){}
""")
stub(P + ".network.MailPlayerListRequestPayload", "public record MailPlayerListRequestPayload() {}")

source = (CLIENT / "screen/MailComposeScreen.java").read_text(encoding="utf-8")
# protected init 的签名转换仅供提取；方法体仍逐字使用生产代码。
methods = "\n".join(fixtures.method(source.replace("protected void init()", "public void init()"), name) for name in (
    "init", "capturePrefill", "captureOptions", "restoreOptions", "onClose", "closeMenu",
    "hasDiscardDialog", "requestLeave", "hasUnsavedChanges", "cancelEditing", "navigateBack"))
records = source[source.index("    private record FormOptions"):source.rfind("\n}")]
stub(C + ".screen.MailComposeScreen", """
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import top.csituka.youzaiworldcore.client.MailClientState;
import top.csituka.youzaiworldcore.client.screen.widget.*;
import top.csituka.youzaiworldcore.mail.MailType;
import top.csituka.youzaiworldcore.network.*;
import top.csituka.youzaiworldcore.util.DebugLogger;
public class MailComposeScreen extends MailBaseScreen {
    private static final String MODULE="MailCompose";
    private final boolean editMode;
    private final UUID editMailId=UUID.randomUUID();
    private boolean finished,cancelPacketSent,initialized;
    private Object editSource;
    private Prefill originalPrefill;
    private FormOptions originalOptions;
    private ConfirmationDialog discardDialog;
    private MailUi.Rect pageRect,formRect;
    public Check cbAll,cbPlayer,cbItem,cbCommand,cbVanillaExp,cbVanillaLevel,cbAdventureExp,cbAdventureLevel;
    public Text titleInput,bodyInput,commandInput,vanillaExpInput,vanillaLevelInput,adventureExpInput,adventureLevelInput;
    public DropdownButton typeDropdown,expireDropdown;
    public final Picker playerPicker=new Picker(),inventoryPicker=new Picker();
    public ItemStack[] itemSlots=new ItemStack[]{ItemStack.EMPTY,ItemStack.EMPTY};
    public final List<String> selectedPlayers=new ArrayList<>();
    private final List<TargetSpec> legacyTargets=new ArrayList<>();
    private record TargetSpec(String value){}
    public static class Check {
        public boolean value;
        Check(boolean v){value=v;}
        public boolean isChecked(){return value;}
        public void setChecked(boolean v){value=v;}
    }
    public static class Text {
        public String value;
        Text(String v){value=v;}
        public String getValue(){return value;}
    }
    public static class Picker {
        public boolean open;
        public boolean isOpen(){return open;}
        public boolean isVisible(){return open;}
        public void close(){open=false;}
        public void hide(){open=false;}
    }
    public MailComposeScreen(boolean edit,Screen parent){super(Component.empty(),parent);editMode=edit;}
    protected void renderMailContent(GuiGraphicsExtractor g,int x,int y,float t){}
    private Prefill createPrefill(Object mail){return Prefill.empty();}
    private void createScopeWidgets(Prefill p){
        cbAll=new Check(p.all());cbPlayer=new Check(p.playersEnabled());
        selectedPlayers.clear();selectedPlayers.addAll(p.players());
    }
    private void createMainFields(Prefill p){
        typeDropdown=new DropdownButton(p.typeIndex());expireDropdown=new DropdownButton(p.expireIndex());
        titleInput=new Text(p.title());bodyInput=new Text(p.body());
    }
    private void createAttachmentWidgets(Prefill p){
        cbItem=new Check(p.itemEnabled());cbCommand=new Check(p.commandEnabled());
        cbVanillaExp=new Check(p.vanillaExpEnabled());cbVanillaLevel=new Check(p.vanillaLevelEnabled());
        cbAdventureExp=new Check(p.adventureExpEnabled());cbAdventureLevel=new Check(p.adventureLevelEnabled());
        commandInput=new Text(p.command());vanillaExpInput=new Text(p.vanillaExpValue());vanillaLevelInput=new Text(p.vanillaLevelValue());
        adventureExpInput=new Text(p.adventureExpValue());adventureLevelInput=new Text(p.adventureLevelValue());
        Arrays.fill(itemSlots,ItemStack.EMPTY);for(int i=0;i<p.items().size();i++)itemSlots[i]=p.items().get(i).copy();
    }
    private void syncAttachmentState(){}
    public void acceptBaseline(){originalPrefill=capturePrefill();originalOptions=captureOptions();}
    public boolean dirty(){return hasUnsavedChanges();}
    public ConfirmationDialog dialog(){return discardDialog;}
""" + methods + records + "\n}")

CHECK = """
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import top.csituka.youzaiworldcore.client.screen.*;
import top.csituka.youzaiworldcore.network.MailAdminEditPayload;
public class MailNavigationChecks {
    static int checks;
    static void check(boolean condition,String message){checks++;if(!condition)throw new AssertionError(message);}
    static long cancels(){return ClientPlayNetworking.sent.stream().filter(p->p instanceof MailAdminEditPayload e&&e.cancel()).count();}
    static MailComposeScreen open(Screen parent){
        var form=new MailComposeScreen(true,parent);form.init();Minecraft.getInstance().gui.setScreen(form);return form;
    }
    public static void main(String[] args){
        var mc=Minecraft.getInstance();var parent=new Screen(Component.empty());
        var form=open(parent);check(!form.dirty(),"空表单误报修改");
        form.onClose();check(mc.gui.screen==parent&&cancels()==1,"未修改表单未返回原父页");
        form.onClose();check(cancels()==1,"重复退出发送重复取消包");
        form=open(parent);form.titleInput.value="邮件主题";form.bodyInput.value="正文";
        check(form.dirty(),"正文修改未识别");long before=cancels();
        form.playerPicker.open=true;form.onClose();check(!form.playerPicker.open&&form.dialog()==null,"Esc 未先关闭玩家选择器");
        form.inventoryPicker.open=true;form.onClose();check(!form.inventoryPicker.open&&form.dialog()==null,"Esc 未先关闭物品选择器");
        form.typeDropdown.open=true;form.onClose();check(!form.typeDropdown.open&&form.dialog()==null,"Esc 未先关闭下拉框");
        form.onClose();check(form.dialog().isVisible()&&mc.gui.screen==form&&cancels()==before,"显示确认时已丢弃编辑");
        form.dialog().cancel.run();check(mc.gui.screen==form&&form.bodyInput.value.equals("正文")&&cancels()==before,"继续编辑丢失内容");
        form.onClose();form.keyPressed(new KeyEvent(41,false));
        check(!form.dialog().isVisible()&&mc.gui.screen==form,"Esc 未取消丢弃提示");
        form.onClose();form.dialog().confirm.run();
        check(mc.gui.screen==parent&&cancels()==before+1,"放弃修改未返回原父页/未取消服务端编辑");
        form.closeMenu();check(cancels()==before+1,"再次退出重复取消");
        form=open(parent);form.bodyInput.value="正文";before=cancels();form.closeMenu();
        check(form.dialog().isVisible()&&mc.gui.screen==form,"关闭按钮绕过丢弃确认");
        form.dialog().confirm.run();check(mc.gui.screen==null&&cancels()==before+1,"关闭按钮确认后未回到游戏");
        form=open(parent);form.itemSlots[0]=new ItemStack("diamond",4,"custom-name");form.cbItem.setChecked(true);form.acceptBaseline();
        form.itemSlots[0]=form.itemSlots[0].copy();check(!form.dirty(),"复制物品误判改动");
        form.itemSlots[0]=new ItemStack("diamond",5,"custom-name");check(form.dirty(),"附件数量修改未识别");
        form.itemSlots[0]=new ItemStack("diamond",4,"other-name");check(form.dirty(),"附件组件修改未识别");
        form.itemSlots[0]=new ItemStack("diamond",4,"custom-name");form.cbItem.setChecked(false);
        check(form.dirty(),"取消物品附件勾选未识别");
        form=open(parent);form.cbCommand.setChecked(true);check(form.dirty(),"空附件勾选未识别");
        form.cbCommand.setChecked(false);form.commandInput.value="give %player% diamond";
        check(form.dirty(),"未勾选的输入未受保护");form.cbPlayer.setChecked(true);form.selectedPlayers.add("Alice");
        form.titleInput.value="调整窗口";form.bodyInput.value="保留正文";form.init();
        check(form.titleInput.value.equals("调整窗口")&&form.bodyInput.value.equals("保留正文"),"调整窗口丢失主表单");
        check(!form.cbCommand.isChecked()&&form.commandInput.value.equals("give %player% diamond"),"调整窗口丢失附件勾选/输入");
        check(form.cbPlayer.isChecked()&&form.selectedPlayers.equals(java.util.List.of("Alice")),"调整窗口丢失收件人");
        check(form.dirty(),"调整窗口重置了原始基线");
        System.out.println("邮件导航回归通过："+checks+" 项断言。");
    }
}
"""


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--java-home")
    args = parser.parse_args()
    suffix = ".exe" if os.name == "nt" else ""
    binary = Path(args.java_home) / "bin" if args.java_home else None
    javac = str(binary / ("javac" + suffix)) if binary else "javac"
    java = str(binary / ("java" + suffix)) if binary else "java"
    production = [CLIENT / path for path in (
        "screen/YzuiMenuScreen.java", "screen/MailBaseScreen.java", "screen/MailViewport.java")]
    with tempfile.TemporaryDirectory(prefix=".tmp-mail-navigation-", dir=ROOT) as directory:
        temporary = Path(directory)
        files = []
        for path, content in {**STUBS, "MailNavigationChecks.java": CHECK}.items():
            file = temporary / path
            file.parent.mkdir(parents=True, exist_ok=True)
            file.write_text(content, encoding="utf-8")
            files.append(str(file))
        subprocess.run([javac, "--release", "25", "-encoding", "UTF-8", "-d", str(temporary),
                        *files, *map(str, production)], cwd=ROOT, check=True)
        subprocess.run([java, "-cp", str(temporary), "MailNavigationChecks"], cwd=ROOT, check=True)


if __name__ == "__main__":
    main()
