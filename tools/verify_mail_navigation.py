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
mc = "net/minecraft/client/Minecraft.java"
# Minecraft 26.3 的 Gui.screen 是私有字段，只能通过 screen() 读取。
STUBS[mc] = STUBS[mc].replace("public Screen screen;", "private Screen screen; public Screen screen(){return screen;}")
stub("net.minecraft.network.protocol.common.custom.CustomPacketPayload", """
public interface CustomPacketPayload {
    record Type<T>(String id){}
    Type<? extends CustomPacketPayload> type();
}
""")
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
    public static String message="";
    public static void show(String text,boolean success){message=text;}
    public static void clear(){}
    public static void render(net.minecraft.client.gui.GuiGraphicsExtractor g,net.minecraft.client.gui.Font f,int width){}
}
""")
for name in ("MailScreen", "MailSentScreen"):
    stub(C + ".screen." + name, "public class " + name + """ extends MailBaseScreen {
        public """ + name + """(){this(null);}
        public """ + name + """(net.minecraft.client.gui.screens.Screen parent){super(net.minecraft.network.chat.Component.empty(),parent);}
        protected void renderMailContent(net.minecraft.client.gui.GuiGraphicsExtractor g,int x,int y,float t){}
    }""")
stub(C + ".screen.MailUi", """
public class MailUi {
    public record Rect(int x,int y,int width,int height){public int right(){return x+width;} public int bottom(){return y+height;}}
    public static Rect centeredPage(int width,int height,int cw,int ch){return new Rect(0,0,Math.min(width,cw-28),Math.min(height,ch-28));}
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
    public static top.csituka.youzaiworldcore.network.MailStreamCodecs.MailRefAndMail pendingEditData;
    public static java.util.List<String> currentSentList=new java.util.ArrayList<>();
    public static java.util.List<top.csituka.youzaiworldcore.network.MailStreamCodecs.MailRefAndMail> currentInbox=new java.util.ArrayList<>();
    public static void recalculateUnreadCount(){}
}
""")
stub("net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking", """
public class ClientPlayNetworking {
    public static final java.util.List<Object> sent=new java.util.ArrayList<>();
    public static boolean supported=true;
    public static boolean canSend(Object type){return supported;}
    public static void send(Object payload){sent.add(payload);}
}
""")
stub(P + ".mail.MailType", "public enum MailType {ANNOUNCEMENT}")
stub(P + ".network.MailAdminEditPayload", """
public record MailAdminEditPayload(java.util.UUID mailId,boolean cancel,java.util.List<?> targets,
    top.csituka.youzaiworldcore.mail.MailType mailType,String title,String body,byte expiration,java.util.List<?> attachments)
    implements net.minecraft.network.protocol.common.custom.CustomPacketPayload {
    public Type<MailAdminEditPayload> type(){return new Type<>("mail_admin_edit");}
}
""")
stub(P + ".network.MailPlayerListRequestPayload", "public record MailPlayerListRequestPayload() {}")
for name, fields in (("MailComposeOpenPayload", ""), ("MailSentListRequestPayload", ""),
                     ("MailFetchPayload", "java.util.UUID mailId")):
    stub(P + ".network." + name, "public record " + name + "(" + fields + """)
        implements net.minecraft.network.protocol.common.custom.CustomPacketPayload {
        public Type<""" + name + """> type(){return new Type<>("request");}
    }""")
stub(P + ".network.MailSentListPayload", "public record MailSentListPayload(java.util.List<String> summaries) {}")
stub(P + ".mail.Mail", """
public record Mail(java.util.UUID id) {public java.util.UUID getId(){return id;}}
""")
stub(P + ".network.MailStreamCodecs", """
public class MailStreamCodecs {
    public record MailRefAndMail(Object ref,top.csituka.youzaiworldcore.mail.Mail mail){}
}
""")
stub(P + ".network.MailUpdatePayload", """
public record MailUpdatePayload(int mode,Object ref,top.csituka.youzaiworldcore.mail.Mail mail,
        java.util.UUID removedMailId,boolean canEdit) {
    public static final int MODE_UPDATE=0,MODE_REMOVE=1,MODE_EDIT_PREFILL=2;
}
""")

stub(C + ".screen.MailDraftStore", "public class MailDraftStore {static String key(java.util.UUID id){return \"test\";} static void delete(String key){}}")
screen_key="net/minecraft/client/gui/screens/Screen.java"
STUBS[screen_key]=STUBS[screen_key].replace("protected void init(){}", "protected void init(){} public java.util.List<net.minecraft.client.gui.components.AbstractWidget> children(){return registered;}")
widget_key="net/minecraft/client/gui/components/AbstractWidget.java"


source = (CLIENT / "screen/MailComposeScreen.java").read_text(encoding="utf-8")
# protected init 的签名转换仅供提取；方法体仍逐字使用生产代码。
methods = "\n".join(fixtures.method(source.replace("protected void init()", "public void init()"), name) for name in (
    "init", "capturePrefill", "captureOptions", "restoreOptions", "onClose", "closeMenu",
    "hasDiscardDialog", "isEditing", "requestLeave", "hasUnsavedChanges", "cancelEditing", "navigateBack", "setFormScroll", "narrow"))
records = source[source.index("    private record FormOptions"):source.rfind("\n}")]
stub(C + ".screen.MailComposeScreen", """
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
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
    private final UUID editMailId;
    private boolean finished,cancelPacketSent,initialized;
    private Object editSource;
    private Prefill originalPrefill;
    private FormOptions originalOptions;
    private ConfirmationDialog discardDialog;
    private MenuCloseButton closeButton;
    private String draftKey;
    private int formScroll;
    private MailUi.Rect formViewport,pickItemRect=new MailUi.Rect(0,0,0,0);
    private MailUi.Rect[] slotRects=new MailUi.Rect[2];
    private static final int SLOT_SIZE=18;
    private List<AbstractWidget> formWidgets=List.of();
    private record DraftSnapshot(Prefill form,FormOptions options,List<ItemStack> items){}
    private DraftSnapshot restoringDraft;
    private ItemStack decodeItem(ItemStack item){return item;}

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
    public MailComposeScreen(boolean edit,Screen parent){this(edit,UUID.randomUUID(),parent);}
    public MailComposeScreen(boolean edit,UUID mailId,Screen parent){super(Component.empty(),parent);editMode=edit;editMailId=mailId;}
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

network = (CLIENT.parent / "network/ClientNetworking.java").read_text(encoding="utf-8")


def receiver_body(payload):
    """原样运行实际接收器的主线程回调，避免在检查中重写导航条件。"""
    start = network.index("ClientPlayNetworking.registerGlobalReceiver(" + payload + ".ID")
    start = network.index("context.client().execute(() -> {", start)
    start = network.index("{", start)
    depth, end = 1, start + 1
    while depth:
        depth += (network[end] == "{") - (network[end] == "}")
        end += 1
    return network[start + 1:end - 1]


receivers = """
import net.minecraft.client.Minecraft;
import top.csituka.youzaiworldcore.network.*;
import top.csituka.youzaiworldcore.client.screen.MailNavigationRequests;
import top.csituka.youzaiworldcore.util.DebugLogger;
public class MailReceivers {
    private record Context(Minecraft client){}
    private static void notifyMailResult(Minecraft client,String text,boolean success){}
"""
for name, payload in (("compose", "OpenMailComposePayload"), ("sent", "MailSentListPayload"), ("update", "MailUpdatePayload")):
    kind = "Object" if name == "compose" else payload
    receivers += "public static void " + name + "(" + kind + " payload){var context=new Context(Minecraft.getInstance());"
    receivers += receiver_body(payload) + "}\n"
receivers += "}\n"

CHECK = """
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import top.csituka.youzaiworldcore.client.screen.*;
import top.csituka.youzaiworldcore.network.*;
public class MailNavigationChecks {
    static int checks;
    static void check(boolean condition,String message){checks++;if(!condition)throw new AssertionError(message);}
    static long cancels(){return ClientPlayNetworking.sent.stream().filter(p->p instanceof MailAdminEditPayload e&&e.cancel()).count();}
    static MailComposeScreen open(Screen parent){
        var form=new MailComposeScreen(true,parent);form.init();Minecraft.getInstance().gui.setScreen(form);return form;
    }
    static void requests()throws Exception{
        var client=Minecraft.getInstance();MailNavigationRequests.clear();ClientPlayNetworking.sent.clear();
        var origin=new MailScreen();client.gui.setScreen(origin);
        check(MailNavigationRequests.compose(origin),"发布请求未发出");
        check(!MailNavigationRequests.compose(origin)&&ClientPlayNetworking.sent.size()==1,"连续点击重复发送");
        MailReceivers.compose(null);
        check(client.gui.screen() instanceof MailComposeScreen,"匹配发布请求未进入编辑");
        var editor=client.gui.screen();
        MailReceivers.sent(new MailSentListPayload(java.util.List.of("latest")));
        check(client.gui.screen()==editor&&top.csituka.youzaiworldcore.client.MailClientState.currentSentList.equals(java.util.List.of("latest")),
                "后台列表刷新替换编辑器或丢失最新列表");
        MailReceivers.compose(null);check(client.gui.screen()==editor,"重复发布响应覆盖编辑器");
        client.gui.setScreen(origin);MailNavigationRequests.sent(origin);origin.removed();client.gui.setScreen(editor);
        MailReceivers.sent(new MailSentListPayload(java.util.List.of("late")));
        check(client.gui.screen()==editor,"离开页面后列表响应强制切页");
        var sent=new MailSentScreen();client.gui.setScreen(sent);var id=java.util.UUID.randomUUID();
        MailNavigationRequests.edit(sent,id);sent.removed();client.gui.setScreen(editor);long before=cancels();
        var prefill=new MailUpdatePayload(MailUpdatePayload.MODE_EDIT_PREFILL,new Object(),
                new top.csituka.youzaiworldcore.mail.Mail(id),null,true);
        MailReceivers.update(prefill);
        check(client.gui.screen()==editor&&cancels()==before+1,"迟到预填覆盖编辑器或未恢复隐藏邮件");
        check(top.csituka.youzaiworldcore.client.MailClientState.pendingEditData==null,"迟到预填污染后续表单");
        var active=new MailComposeScreen(true,id,sent);client.gui.setScreen(active);before=cancels();
        MailReceivers.update(prefill);check(cancels()==before&&client.gui.screen()==active,"重复预填取消了当前编辑");
        client.gui.setScreen(sent);MailNavigationRequests.edit(sent,id);MailReceivers.update(prefill);
        check(client.gui.screen() instanceof MailComposeScreen form&&form.isEditing(id),"匹配编辑请求未打开对应邮件");
        check(top.csituka.youzaiworldcore.client.MailClientState.pendingEditData.mail().getId().equals(id),"有效预填丢失");
        MailNavigationRequests.clear();client.gui.setScreen(sent);MailNavigationRequests.edit(sent,id);
        MailNavigationRequests.failed(java.util.UUID.randomUUID());
        check(MailNavigationRequests.take(MailNavigationRequests.Kind.EDIT,id)!=null,"无关邮件失败取消了当前请求");
        MailNavigationRequests.compose(sent);MailNavigationRequests.failed(null);
        check(MailNavigationRequests.take(MailNavigationRequests.Kind.COMPOSE,null)==null,"请求失败未解除等待");
        check(MailNavigationRequests.compose(sent),"失败后不能重新请求");
        var field=MailNavigationRequests.class.getDeclaredField("pending");field.setAccessible(true);
        var pending=(MailNavigationRequests.Request)field.get(null);
        field.set(null,new MailNavigationRequests.Request(pending.kind(),pending.origin(),pending.mailId(),pending.connection(),System.currentTimeMillis()-16000));
        MailNavigationRequests.tick();
        check(MailToast.message.endsWith("request_timeout")&&MailNavigationRequests.take(MailNavigationRequests.Kind.COMPOSE,null)==null,"超时未提示/未撤销切页");
        MailNavigationRequests.compose(sent);client.connection=new Object();MailNavigationRequests.tick();
        check(MailNavigationRequests.take(MailNavigationRequests.Kind.COMPOSE,null)==null,"旧连接响应仍可切页");
        client.gui.setScreen(null);check(MailNavigationRequests.compose(null),"游戏内命令请求被禁用");
        MailReceivers.compose(null);check(client.gui.screen() instanceof MailComposeScreen,"命令发起的发布请求未打开");
        MailNavigationRequests.clear();client.gui.setScreen(null);MailNavigationRequests.sent(null);
        client.gui.setScreen(editor);MailReceivers.sent(new MailSentListPayload(java.util.List.of()));
        check(client.gui.screen()==editor,"命令的迟到响应覆盖新打开的页面");
        client.connection=null;check(!MailNavigationRequests.compose(null),"断线仍可发请求");
        client.connection=new Object();MailNavigationRequests.clear();
    }
    public static void main(String[] args)throws Exception{
        var mc=Minecraft.getInstance();var parent=new Screen(Component.empty());
        var form=open(parent);check(!form.dirty(),"空表单误报修改");
        form.onClose();check(mc.gui.screen()==parent&&cancels()==1,"未修改表单未返回原父页");
        form.onClose();check(cancels()==1,"重复退出发送重复取消包");
        form=open(parent);form.titleInput.value="邮件主题";form.bodyInput.value="正文";
        check(form.dirty(),"正文修改未识别");long before=cancels();
        form.playerPicker.open=true;form.onClose();check(!form.playerPicker.open&&form.dialog()==null,"Esc 未先关闭玩家选择器");
        form.inventoryPicker.open=true;form.onClose();check(!form.inventoryPicker.open&&form.dialog()==null,"Esc 未先关闭物品选择器");
        form.typeDropdown.open=true;form.onClose();check(!form.typeDropdown.open&&form.dialog()==null,"Esc 未先关闭下拉框");
        form.onClose();check(form.dialog().isVisible()&&mc.gui.screen()==form&&cancels()==before,"显示确认时已丢弃编辑");
        form.dialog().cancel.run();check(mc.gui.screen()==form&&form.bodyInput.value.equals("正文")&&cancels()==before,"继续编辑丢失内容");
        form.onClose();form.keyPressed(new KeyEvent(41,false));
        check(!form.dialog().isVisible()&&mc.gui.screen()==form,"Esc 未取消丢弃提示");
        form.onClose();form.dialog().confirm.run();
        check(mc.gui.screen()==parent&&cancels()==before+1,"放弃修改未返回原父页/未取消服务端编辑");
        form.closeMenu();check(cancels()==before+1,"再次退出重复取消");
        form=open(parent);form.bodyInput.value="正文";before=cancels();form.closeMenu();
        check(form.dialog().isVisible()&&mc.gui.screen()==form,"关闭按钮绕过丢弃确认");
        form.dialog().confirm.run();check(mc.gui.screen()==null&&cancels()==before+1,"关闭按钮确认后未回到游戏");
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
        requests();
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
        "screen/YzuiMenuScreen.java", "screen/MailBaseScreen.java", "screen/MailViewport.java", "screen/MailNavigationRequests.java")]
    with tempfile.TemporaryDirectory(prefix=".tmp-mail-navigation-", dir=ROOT) as directory:
        temporary = Path(directory)
        files = []
        for path, content in {**STUBS, "MailReceivers.java": receivers, "MailNavigationChecks.java": CHECK}.items():
            file = temporary / path
            file.parent.mkdir(parents=True, exist_ok=True)
            file.write_text(content, encoding="utf-8")
            files.append(str(file))
        subprocess.run([javac, "--release", "25", "-encoding", "UTF-8", "-d", str(temporary),
                        *files, *map(str, production)], cwd=ROOT, check=True)
        subprocess.run([java, "-cp", str(temporary), "MailNavigationChecks"], cwd=ROOT, check=True)


if __name__ == "__main__":
    main()
