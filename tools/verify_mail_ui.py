"""执行实际邮件及宠物页面：小窗口几何、详情滚动、表单恢复、本机草稿与请求隔离。
原样运行宠物响应接收器的主线程回调，Gui.screen 保持 26.3 的私有可见性。
游戏绘制、物品注册表、网络及配置使用替身；不启动游戏，不构建模组。
"""
from pathlib import Path
import argparse, copy, subprocess, tempfile
import verify_mail_navigation as nav

ROOT=Path(__file__).resolve().parents[1]
CLIENT=ROOT/'src/client/java/top/csituka/youzaiworldcore/client'
MAIN=ROOT/'src/main/java/top/csituka/youzaiworldcore'
P='top.csituka.youzaiworldcore'; C=P+'.client'
STUBS=copy.deepcopy(nav.STUBS)
def stub(name,body):
    package=name.rpartition('.')[0]; STUBS[name.replace('.','/')+'.java']='package '+package+';\n'+body
for name in ['MailScreen','MailSentScreen','MailComposeScreen','MailDraftStore','MailUi']:
    STUBS.pop((C+'.screen.'+name).replace('.','/')+'.java',None)
for name in ['Mail','MailType']:
    STUBS.pop((P+'.mail.'+name).replace('.','/')+'.java',None)
stub('org.jetbrains.annotations.Nullable','public @interface Nullable {}')
stub('net.minecraft.util.FormattedCharSequence','public record FormattedCharSequence(String value) {}')
font='net/minecraft/client/gui/Font.java'
STUBS[font]=STUBS[font].replace('public final int lineHeight', '''public String plainSubstrByWidth(String text,int width){int end=0;while(end<text.length()&&width(text.substring(0,end+1))<=width)end++;return text.substring(0,end);}
 public java.util.List<net.minecraft.util.FormattedCharSequence> split(net.minecraft.network.chat.Component text,int width){
 java.util.List<net.minecraft.util.FormattedCharSequence> result=new java.util.ArrayList<>();
 for(String line:text.getString().split("\\n",-1)){while(width(line)>Math.max(1,width)){String part=plainSubstrByWidth(line,Math.max(9,width));result.add(new net.minecraft.util.FormattedCharSequence(part));line=line.substring(part.length());}result.add(new net.minecraft.util.FormattedCharSequence(line));}return result;}
 public final int lineHeight''')
screen='net/minecraft/client/gui/screens/Screen.java'
STUBS[screen]=STUBS[screen].replace('protected void init(){}','''protected void init(){}
 protected Object focused;
 public void setFocused(Object value){focused=value;} public Object getFocused(){return focused;}
 protected void rebuildWidgets(){registered.clear();init();}
 public void tick(){} public boolean charTyped(net.minecraft.client.input.CharacterEvent event){return false;}
 public net.minecraft.network.chat.Component getTitle(){return net.minecraft.network.chat.Component.literal("Test");}
''')
stub('net.minecraft.client.input.CharacterEvent','public record CharacterEvent(int codepoint) {public String codepointAsString(){return Character.toString(codepoint);}}')
mc='net/minecraft/client/Minecraft.java'
STUBS[mc]=STUBS[mc].replace('public Object getConnection(){return connection;}','''public Object getConnection(){return connection;}
 public ServerData serverData=new ServerData(); public ServerData getCurrentServer(){return serverData;}
 public Server getSingleplayerServer(){return new Server();} public Object getFixerUpper(){return this;}
 public static class ServerData {public String ip="server-a";} public static class Server {public java.nio.file.Path getWorldPath(Object resource){return java.nio.file.Path.of("test-world");}}
''').replace('public static class Level {','public static class Level {public Level registryAccess(){return this;} public Object createSerializationContext(Object ops){return ops;} ')
stub('net.minecraft.world.level.storage.LevelResource','public class LevelResource {public static final Object ROOT=new Object();}')
stub('net.minecraft.nbt.NbtOps','public class NbtOps {public static final Object INSTANCE=new Object();}')
stub('net.minecraft.nbt.TagParser','public class TagParser {public static Object parseCompoundFully(String value){return value;}}')
stub(P+'.util.ItemStackNbtUpgrader','public class ItemStackNbtUpgrader {public static final int LEGACY_DATA_VERSION=4903; public static int currentDataVersion(){return 5000;} public static Object upgrade(Object fixer,Object tag,int version){return tag;}}')
stub('net.minecraft.world.item.ItemStack','''public class ItemStack {
 public static final ItemStack EMPTY=new ItemStack("",0,""); public static final Codec CODEC=new Codec();
 public final String item,components; public int count;
 public ItemStack(String item,int count,String components){this.item=item;this.count=count;this.components=components;}
 public boolean isEmpty(){return count==0;} public int getCount(){return count;} public int getMaxStackSize(){return 64;}
 public void setCount(int value){count=value;} public ItemStack copy(){return new ItemStack(item,count,components);}
 public static boolean matches(ItemStack a,ItemStack b){return a.item.equals(b.item)&&a.count==b.count&&a.components.equals(b.components);}
 public static class Result<T> {final T value;Result(T value){this.value=value;}public java.util.Optional<T> result(){return java.util.Optional.ofNullable(value);}public T getOrThrow(){return value;}}
 public static class Codec {
 public Result<String> encodeStart(Object context,ItemStack stack){return new Result<>(stack.item+"|"+stack.count+"|"+stack.components);}
 public Result<ItemStack> parse(Object context,Object input){String[] parts=input.toString().split("\\\\|",-1);return new Result<>(parts.length==3?new ItemStack(parts[0],Integer.parseInt(parts[1]),parts[2]):EMPTY);}
 }
}''')
stub('net.minecraft.client.gui.components.EditBox','''public class EditBox extends AbstractWidget {
 private String value=""; private java.util.function.Consumer<String> responder=s->{};
 public EditBox(net.minecraft.client.gui.Font font,int x,int y,int width,int height,net.minecraft.network.chat.Component label){super(x,y,width,height,label);}
 public void setValue(String value){this.value=value;responder.accept(value);}public String getValue(){return value;}
 public void setResponder(java.util.function.Consumer<String> listener){responder=listener;}public void setEditable(boolean value){}
 public void setBordered(boolean value){} public void setTextColor(int value){}public void setTextColorUneditable(int value){}
 public void setTextShadow(boolean value){}public void setHint(net.minecraft.network.chat.Component value){}public void setMaxLength(int value){}
}''')
stub('net.minecraft.client.gui.components.MultiLineEditBox','''public class MultiLineEditBox extends EditBox {
 public MultiLineEditBox(net.minecraft.client.gui.Font font,int x,int y,int width,int height,net.minecraft.network.chat.Component label){super(font,x,y,width,height,label);}
 public static Builder builder(){return new Builder();}public void setCharacterLimit(int limit){}
 public static class Builder {
 int x,y;public Builder setX(int value){x=value;return this;}public Builder setY(int value){y=value;return this;}
 public Builder setPlaceholder(Object value){return this;}public Builder setTextColor(int value){return this;}public Builder setTextShadow(boolean value){return this;}
 public Builder setCursorColor(int value){return this;}public Builder setShowBackground(boolean value){return this;}public Builder setShowDecorations(boolean value){return this;}
 public MultiLineEditBox build(net.minecraft.client.gui.Font font,int width,int height,net.minecraft.network.chat.Component label){return new MultiLineEditBox(font,x,y,width,height,label);}
 }
}''')
graphics='net/minecraft/client/gui/GuiGraphicsExtractor.java'
STUBS[graphics]=STUBS[graphics].replace('public class GuiGraphicsExtractor {','''public class GuiGraphicsExtractor {
 public void text(Font font,net.minecraft.util.FormattedCharSequence line,int x,int y,int color,boolean shadow){text(font,line.value(),x,y,color,shadow);}
 public void item(Object stack,int x,int y,int seed){}public void itemDecorations(Font font,Object stack,int x,int y){}
 public void setTooltipForNextFrame(Font font,Object stack,int x,int y){}
''')
stub(C+'.render.YzuiTheme',"""public class YzuiTheme {
 public static int alpha(int color,float alpha){return color;}public static int multiplyAlpha(int color,float alpha){return color;}public static int mix(int a,int b,float f){return a;}
 public static int surface(){return -1;} public static int surfaceLow(){return -1;} public static int surfaceHigh(){return -1;}public static int slot(){return -1;}
 public static int primary(){return -1;}public static int primaryContainer(){return -1;}public static int onPrimary(){return -1;}public static int onPrimaryContainer(){return -1;}public static int secondaryContainer(){return -1;}
 public static int text(){return -1;}public static int textMuted(){return -1;}public static int success(){return -1;}public static int warning(){return -1;}public static int error(){return -1;}public static int errorContainer(){return -1;}public static int outline(){return -1;}public static int outlineVariant(){return -1;}
 public static class Palette {public int surfaceLow(){return -1;}}public static Palette palette(){return new Palette();}
 public static class Style {public float controlOpacity(){return 1;}}public static Style visualStyle(){return new Style();}
 public static void field(net.minecraft.client.gui.GuiGraphicsExtractor g,int x,int y,int w,int h,boolean f,boolean e,float a){}public static void border(net.minecraft.client.gui.GuiGraphicsExtractor g,int x,int y,int w,int h,int r,int c){}
 public static void label(net.minecraft.client.gui.GuiGraphicsExtractor g,net.minecraft.client.gui.Font font,net.minecraft.network.chat.Component label,int x,int y,int w,int color,boolean center){g.text(font,label,x,y,color,false);}
}""")
stub(C+'.render.RoundedRect','public class RoundedRect {public static void fill(net.minecraft.client.gui.GuiGraphicsExtractor g,int x,int y,int w,int h,int r,int c){g.fill(x,y,x+w,y+h,c);}}')
stub(C+'.render.YzuiMenuPanel','public class YzuiMenuPanel {public static void card(net.minecraft.client.gui.GuiGraphicsExtractor g,int x,int y,int w,int h){g.fill(x,y,x+w,y+h,0);}}')
stub(C+'.screen.widget.TransparentButton','''public class TransparentButton extends net.minecraft.client.gui.components.AbstractWidget {
 final Runnable action; public TransparentButton(int x,int y,int w,int h,net.minecraft.network.chat.Component c,Runnable action){super(x,y,w,h,c);this.action=action;}
 @Override public void onClick(net.minecraft.client.input.MouseButtonEvent e,boolean actual){action.run();}
}''')
stub(C+'.screen.widget.MailCheckboxButton','''public class MailCheckboxButton extends TransparentButton {
 private boolean checked;public MailCheckboxButton(int x,int y,int width,net.minecraft.network.chat.Component c,boolean checked,Runnable callback){super(x,y,width,18,c,callback);this.checked=checked;}
 public boolean isChecked(){return checked;}public void setChecked(boolean value){checked=value;}
}''')
stub(C+'.screen.widget.DropdownButton','''public class DropdownButton extends TransparentButton {
 public boolean open;private int index;
 public DropdownButton(int x,int y,int w,int pw,int h,net.minecraft.network.chat.Component c,java.util.List<String> options,int index,boolean open,java.util.function.IntConsumer choose,Runnable action){super(x,y,w,h,c,action);this.index=index;this.open=open;}
 public boolean isOpen(){return open;}public void closePopup(){open=false;}public int getSelectedIndex(){return index;}
 public void setCanvasSize(int w,int h){}public void renderPopup(net.minecraft.client.gui.GuiGraphicsExtractor g,int x,int y,float t){}
 public boolean isPositionInsidePopup(double x,double y){return false;}
}''')
key=(C+'.screen.widget.ConfirmationDialog').replace('.','/')+'.java'
STUBS[key]=STUBS[key].replace('    public void init(int w,int h){}','''    public void render(net.minecraft.client.gui.GuiGraphicsExtractor g,int w,int h){}public void renderButtons(net.minecraft.client.gui.GuiGraphicsExtractor g,int x,int y,float t){}
 public boolean keyPressed(net.minecraft.client.input.KeyEvent event){return true;}public boolean mouseClicked(double x,double y){return true;}public boolean mouseScrolled(double x,double y,double a,double b){return true;}
 public void init(int w,int h){}''')
stub(C+'.animation.YzuiPopupAnimation','''public class YzuiPopupAnimation {private boolean visible; public boolean isVisible(){return visible;}public boolean update(int w,int h){return visible;}public void hide(){visible=false;}public void show(int w,int h){visible=true;} public boolean acceptsInput(){return true;}public double toLocalX(double x){return x;}public double toLocalY(double y){return y;}public void begin(net.minecraft.client.gui.GuiGraphicsExtractor g){}public void end(net.minecraft.client.gui.GuiGraphicsExtractor g){} }''')
stub(C+'.screen.MailPlayerPicker','''public class MailPlayerPicker {public boolean isOpen(){return false;}public void close(){}public void setQuery(String query){}public void render(net.minecraft.client.gui.GuiGraphicsExtractor g,net.minecraft.client.gui.Font font,int x,int y,int w,int h){} public void mouseClicked(double x,double y){}public boolean mouseScrolled(double x,double y,double scroll){return true;}public boolean keyPressed(net.minecraft.client.input.KeyEvent event){return false;}public boolean charTyped(net.minecraft.client.input.CharacterEvent event){return false;} public void open(java.util.List<String> names,java.util.function.Consumer<java.util.List<String>> listener){} }''')
stub(P+'.itemborder.ItemBorderRenderer','public class ItemBorderRenderer {public static void renderSlotBorder(net.minecraft.client.gui.GuiGraphicsExtractor g,int x,int y,Object stack){}}')
stub(P+'.util.DebugLogger','public class DebugLogger {public static void debug(String a,String b,Object...args){}public static void info(String a,String b,Object...args){}public static void exception(String a,String b,Exception e){}}')
stub(C+'.config.ClientGlobalSettings','''public class ClientGlobalSettings {
 public static final String MAIL_MODULE="mail_module"; public static int writes; public static final Section store=new Section();public static Section section(String name){return store;}public static void save(){writes++;}
 public static class Section {final java.util.Map<String,String> data=new java.util.HashMap<>();public String getString(String key,String def){return data.getOrDefault(key,def);}public void set(String key,String value){data.put(key,value);}public void remove(String key){data.remove(key);}}
}''')
stub(P+'.network.AttachmentData','public record AttachmentData('+P+'.mail.AttachmentType type,String data,int amount,net.minecraft.world.item.ItemStack stack){}')
stub(P+'.network.MailAdminSendPayload','public record MailAdminSendPayload(java.util.List<'+P+'.mail.TargetSpec> targets,'+P+'.mail.MailType type,String title,String body,byte expire,java.util.List<AttachmentData> attachments){}')
stub(P+'.network.MailAdminEditPayload','public record MailAdminEditPayload(java.util.UUID id,boolean cancel,java.util.List<'+P+'.mail.TargetSpec> targets,'+P+'.mail.MailType type,String title,String body,byte expire,java.util.List<AttachmentData> attachments){}')
stub(P+'.network.MailOpenPayload','public record MailOpenPayload(){}')
stub(P+'.network.MailRecallPayload','public record MailRecallPayload(java.util.UUID id){}')
stub(P+'.network.MailActionPayload','public record MailActionPayload(java.util.UUID id,int action){public static final int ACTION_READ=0,ACTION_CLAIM=1,ACTION_STAR=2,ACTION_UNSTAR=3,ACTION_DELETE=4;}')
stub(P+'.network.MailStreamCodecs','''public class MailStreamCodecs {
 public record MailRefAndMail(top.csituka.youzaiworldcore.mail.MailRef ref,top.csituka.youzaiworldcore.mail.Mail mail){}
 public record MailSummary(java.util.UUID mailId,String title,String scopeSummary,long sentTime,Long expireTime){}
}''')
stub(C+'.MailClientState','''public class MailClientState {
 public static boolean canSend=true;public static int unreadCount;
 public static top.csituka.youzaiworldcore.network.MailStreamCodecs.MailRefAndMail pendingEditData;
 public static java.util.List<top.csituka.youzaiworldcore.network.MailStreamCodecs.MailRefAndMail> currentInbox=new java.util.ArrayList<>();
 public static java.util.List<top.csituka.youzaiworldcore.network.MailStreamCodecs.MailSummary> currentSentList=new java.util.ArrayList<>();
 public static java.util.List<String> registeredPlayers=java.util.List.of();public static void recalculateUnreadCount(){}
}''')
stub(C+'.screen.MailNavigationRequests','public class MailNavigationRequests {public static void cancel(Object page){}public static void compose(Object page){}public static void sent(Object page){}public static void edit(Object page,java.util.UUID id){}}')
stub(P+'.network.PetMenuRequestPayload','public record PetMenuRequestPayload(long requestId,String action,String petId,String value){}')
stub(P+'.network.PetMenuStatePayload','public record PetMenuStatePayload(long requestId,boolean success,net.minecraft.network.chat.Component message,java.util.List<Entry> pets){public record Entry(String id,String name,String mode){}}')

# Interfaces used by the complete compose page and its picker.
k=(P+'.util.DebugLogger').replace('.','/')+'.java'
STUBS[k]=STUBS[k].replace('public class DebugLogger {','public class DebugLogger {public static void entering(String a,String b,Object...args){}public static void exiting(String a,String b,Object...args){}public static void stateChange(String a,String b,Object...args){}')
k=(C+'.animation.YzuiPopupAnimation').replace('.','/')+'.java'
STUBS[k]=STUBS[k].replace('public void begin(', 'public Object begin(').replace('GuiGraphicsExtractor g){}public void end','GuiGraphicsExtractor g){return null;}public void end').replace('public void end(net.minecraft.client.gui.GuiGraphicsExtractor g){}','public void end(net.minecraft.client.gui.GuiGraphicsExtractor g,Object previous){}public void scrim(net.minecraft.client.gui.GuiGraphicsExtractor g){}')
k=(C+'.render.YzuiTheme').replace('.','/')+'.java'
STUBS[k]=STUBS[k].replace('public class YzuiTheme {','public class YzuiTheme {public static int slotHover(){return -1;}public static void card(net.minecraft.client.gui.GuiGraphicsExtractor g,int x,int y,int w,int h){}')
k='net/minecraft/client/player/LocalPlayer.java'
STUBS[k]=STUBS[k].replace('public class LocalPlayer {','public class LocalPlayer {public Inventory getInventory(){return new Inventory();}public static class Inventory {public net.minecraft.world.item.ItemStack getItem(int slot){return net.minecraft.world.item.ItemStack.EMPTY;}}')
k='net/minecraft/world/item/ItemStack.java'
STUBS[k]=STUBS[k].replace('public int getCount(){','public net.minecraft.network.chat.Component getDisplayName(){return net.minecraft.network.chat.Component.literal(item);}public int getCount(){')
k='net/minecraft/client/input/KeyEvent.java'
STUBS[k]=STUBS[k].replace('{}','{public boolean isPaste(){return false;}}')
k=(C+'.screen.MailPlayerPicker').replace('.','/')+'.java'
STUBS[k]=STUBS[k].replace('keyPressed(net.minecraft.client.input.KeyEvent event)','keyPressed(int event)')
stub('net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking','public class ClientPlayNetworking {public static Object last;public static void send(Object payload){last=payload;}}')

k='net/minecraft/client/Minecraft.java'
STUBS[k]=STUBS[k].replace('public class Minecraft {','public class Minecraft {public final Keyboard keyboardHandler=new Keyboard();public static class Keyboard {public String getClipboard(){return "";}}')
k=(C+'.screen.MailPlayerPicker').replace('.','/')+'.java'
STUBS[k]=STUBS[k].replace('public class MailPlayerPicker {','public class MailPlayerPicker {public boolean acceptsInput(){return true;}')

stub(C+'.screen.PetMenuReceiverChecks','''public class PetMenuReceiverChecks {
 private record Context(net.minecraft.client.Minecraft client) {}
 public static void receive(top.csituka.youzaiworldcore.network.PetMenuStatePayload payload,Object connection) {
  var context=new Context(net.minecraft.client.Minecraft.getInstance());
'''+nav.receiver_body('PetMenuStatePayload')+'''
 }
}''')

CHECK='''package top.csituka.youzaiworldcore.client.screen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.ItemStack;
import top.csituka.youzaiworldcore.client.config.ClientGlobalSettings;
import top.csituka.youzaiworldcore.network.*;
public class MailUiChecks {
 static int checks;static void check(boolean condition,String message){checks++;if(!condition)throw new AssertionError(message);}
 static Object field(Object object,String name)throws Exception{var field=object.getClass().getDeclaredField(name);field.setAccessible(true);return field.get(object);}
 static void value(Object object,String name,Object value)throws Exception{var field=object.getClass().getDeclaredField(name);field.setAccessible(true);field.set(object,value);}
 static void call(Object object,String name)throws Exception{var method=object.getClass().getDeclaredMethod(name);method.setAccessible(true);method.invoke(object);}
 public static void main(String[] args)throws Exception {
  for(int[] size:new int[][]{{320,240},{427,240},{480,360},{640,360},{854,480},{960,540},{1920,1080}}){
   var form=new MailComposeScreen();form.width=size[0];form.height=size[1];form.init();form.extractRenderState(new GuiGraphicsExtractor(),-1,-1,0);
   check(form.viewport.scale()==1,"邮件字号被整体缩小");
   var area=(MailUi.Rect)field(form,"formRect");
   for(var widget:form.children())if(widget instanceof net.minecraft.client.gui.components.AbstractWidget w&&!(w instanceof top.csituka.youzaiworldcore.client.screen.widget.MenuCloseButton))
    check(w.getX()>=area.x()&&w.getRight()<=area.right(),"表单控件横向溢出："+size[0]);
   var title=(net.minecraft.client.gui.components.EditBox)field(form,"titleInput");title.setValue("窗口恢复");form.init();
   check(((net.minecraft.client.gui.components.EditBox)field(form,"titleInput")).getValue().equals("窗口恢复"),"重排丢失输入");
  }
  var client=Minecraft.getInstance();
  var mail=new top.csituka.youzaiworldcore.mail.Mail(java.util.UUID.randomUUID(),top.csituka.youzaiworldcore.mail.MailType.REWARD,"Sender",java.util.List.of(top.csituka.youzaiworldcore.mail.TargetSpec.all()),"All","Long mail","正文".repeat(2000),System.currentTimeMillis(),null,false,false,java.util.List.of());
  var ref=new top.csituka.youzaiworldcore.mail.MailRef(mail.getId());
  top.csituka.youzaiworldcore.client.MailClientState.currentInbox.add(new MailStreamCodecs.MailRefAndMail(ref,mail));
  var inbox=new MailScreen();inbox.width=320;inbox.height=240;inbox.init();inbox.extractRenderState(new GuiGraphicsExtractor(),-1,-1,0);
  check(((MailUi.Rect)field(inbox,"detailRect")).width()==0,"窄屏同时挤入列表和详情");
  var filterField=inbox.getClass().getDeclaredField("activeFilter");filterField.setAccessible(true);filterField.set(inbox,filterField.getType().getEnumConstants()[1]);
  inbox.selectEntry(mail,ref);inbox.extractRenderState(new GuiGraphicsExtractor(),-1,-1,0);
  check(((MailUi.Rect)field(inbox,"listRect")).width()==0,"查看详情时没有隐藏列表");
  check((boolean)field(inbox,"detailVisible"),"未读分类选中后立即丢失详情");
  check((int)field(inbox,"maxDetailScroll")>0,"长邮件无法滚动");
  var detail=(MailUi.Rect)field(inbox,"detailRect");inbox.mouseScrolled(detail.x()+1,detail.y()+1,0,-4);
  check((int)field(inbox,"detailScroll")>0,"滚轮未移动正文");
  filterField.set(inbox,filterField.getType().getEnumConstants()[0]);
  inbox.onClose();inbox.extractRenderState(new GuiGraphicsExtractor(),-1,-1,0);
  check(((MailUi.Rect)field(inbox,"listRect")).width()>0&&field(inbox,"selectedMailId").equals(mail.getId()),"返回列表丢失选择");
  var form=new MailComposeScreen();form.width=320;form.height=240;form.init();
  ((net.minecraft.client.gui.components.EditBox)field(form,"titleInput")).setValue("草稿标题");
  ((net.minecraft.client.gui.components.EditBox)field(form,"bodyInput")).setValue("草稿正文");
  ((ItemStack[])field(form,"itemSlots"))[0]=new ItemStack("diamond",4,"named");
  ((top.csituka.youzaiworldcore.client.screen.widget.MailCheckboxButton)field(form,"cbItem")).setChecked(false);
  call(form,"saveDraft");check(ClientGlobalSettings.writes>0,"草稿未持久化");
  var restored=new MailComposeScreen();restored.width=320;restored.height=240;restored.init();call(restored,"restoreDraft");
  ((top.csituka.youzaiworldcore.client.screen.widget.ConfirmationDialog)field(restored,"discardDialog")).confirm.run();
  check(((net.minecraft.client.gui.components.EditBox)field(restored,"titleInput")).getValue().equals("草稿标题"),"未恢复标题");
  check(((net.minecraft.client.gui.components.EditBox)field(restored,"bodyInput")).getValue().equals("草稿正文"),"未恢复正文");
  check(((ItemStack[])field(restored,"itemSlots"))[0].getCount()==4,"附件数量未恢复");
  check(!((top.csituka.youzaiworldcore.client.screen.widget.MailCheckboxButton)field(restored,"cbItem")).isChecked(),"附件勾选被修改");
  String savedKey=MailDraftStore.key(null);ClientGlobalSettings.store.set(savedKey,"{}");
  ((net.minecraft.client.gui.components.EditBox)field(restored,"titleInput")).setValue("必须保留当前内容");
  call(restored,"restoreDraft");((top.csituka.youzaiworldcore.client.screen.widget.ConfirmationDialog)field(restored,"discardDialog")).confirm.run();
  check(((net.minecraft.client.gui.components.EditBox)field(restored,"titleInput")).getValue().equals("必须保留当前内容"),"无效草稿覆盖当前表单");
  String a=MailDraftStore.key(null);client.serverData.ip="server-b";check(!a.equals(MailDraftStore.key(null)),"跨服务器混用草稿");client.serverData.ip="server-a";
  check(!a.equals(MailDraftStore.key(java.util.UUID.randomUUID())),"不同邮件混用草稿");
  var pet=new PetScreen(null);pet.width=320;pet.height=240;pet.init();var request=(PetMenuRequestPayload)net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.last;
  var response=new PetMenuStatePayload(request.requestId(),true,net.minecraft.network.chat.Component.empty(),java.util.List.of(new PetMenuStatePayload.Entry("DOG1","Wolf","guard")));
  client.gui.setScreen(null);PetMenuReceiverChecks.receive(response,client.getConnection());check((long)field(pet,"pending")==request.requestId()&&client.gui.screen()==null,"离开宠物页后响应仍更新页面或强制切页");
  client.gui.setScreen(pet);
  PetMenuReceiverChecks.receive(new PetMenuStatePayload(request.requestId()+1,true,net.minecraft.network.chat.Component.empty(),java.util.List.of()),client.getConnection());check((long)field(pet,"pending")==request.requestId(),"迟到宠物响应误匹配");
  PetMenuReceiverChecks.receive(response,new Object());check((long)field(pet,"pending")==request.requestId(),"旧连接响应更新宠物页面");
  PetMenuReceiverChecks.receive(response,client.getConnection());check((long)field(pet,"pending")==0,"宠物列表未结束等待");
  value(pet,"deadline",0L);value(pet,"pending",1L);pet.tick();check((long)field(pet,"pending")==0,"宠物请求未超时");
  System.out.println("邮件/宠物界面回归通过："+checks+" 项断言。");
 }
}
'''

def main():
 parser=argparse.ArgumentParser();parser.add_argument('--java-home');args=parser.parse_args();binary=Path(args.java_home)/'bin' if args.java_home else Path('/usr/bin')
 gson=list((Path.home()/'.gradle/caches/modules-2/files-2.1/com.google.code.gson/gson').glob('*/*/*.jar'));assert gson,'需要已缓存的 Gson'
 prod=[CLIENT/'screen'/f'{name}.java' for name in ['YzuiMenuScreen','MailBaseScreen','MailViewport','MailUi','MailScreen','MailSentScreen','MailComposeScreen','MailDraftStore','PetScreen']]
 prod += [MAIN/'mail'/f'{name}.java' for name in ['Mail','MailRef','MailType','MailAttachment','AttachmentType','TargetSpec']]
 with tempfile.TemporaryDirectory(prefix='.tmp-mail-ui-',dir=ROOT) as directory:
  temp=Path(directory);files=[]
  for path,source in {**STUBS,'MailUiChecks.java':CHECK}.items():
   file=temp/path;file.parent.mkdir(parents=True,exist_ok=True);file.write_text(source);files.append(str(file))
  result=subprocess.run([str(binary/'javac'),'--release','25','-encoding','UTF-8','-cp',str(gson[0]),'-d',str(temp),*files,*map(str,prod)],cwd=ROOT,capture_output=True,text=True)
  if result.returncode: print(result.stderr);raise SystemExit(result.returncode)
  subprocess.run([str(binary/'java'),'-cp',str(temp)+':'+str(gson[0]),C+'.screen.MailUiChecks'],cwd=ROOT,check=True)
if __name__=='__main__':main()
