"""验证真实 Minecraft 组件编码、聊天分行及 Fabric 补全合并；不构建模组或启动游戏。"""
import argparse
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
from zipfile import ZipFile

ROOT = Path(__file__).resolve().parents[1]
P = "top/csituka/youzaiworldcore"

# 字体只提供固定字宽，Minecraft 的组件、编解码、分行及 Fabric 合并逻辑使用实际 jar。
STUBS = {
    P + "/util/DebugLogger.java": """
package top.csituka.youzaiworldcore.util;
public final class DebugLogger { public static final int LEVEL_OFF=0,LEVEL_BASIC=1,LEVEL_DETAILED=2,LEVEL_DEBUG=3; public static final DebugLogger SERVER=new DebugLogger(),CLIENT=new DebugLogger(); private boolean developer; private int level; public void configure(boolean d,int l){developer=d;level=l;} public boolean isDevModeEnabled(){return developer;} public int getLogLevel(){return level;} public static boolean isEnabled(int l){return false;} public static boolean isEnabled(){return false;} public static DebugLogger bootstrap(){return CLIENT;} public static DebugLogger forFile(java.nio.file.Path p){return SERVER;} public ModuleLogger module(String m){return new ModuleLogger();} public static void notice(String m,String s,Object...a){} public static void warn(String m,String s,Object...a){} public static void error(String m,String s,Object...a){} public static void exception(String m,String s,Object...a){} public static void exceptionSummary(String m,String s,Object...a){} public static void info(String m,String s,Object...a){} public static void stateChange(String m,String s,Object...a){} public static void debug(String m,String s,Object...a){} public static void branch(String m,String s,Object...a){} public static void debugStateChange(String m,String s,Object...a){} public static void trace(String m,String s,Object...a){} public static void entering(String m,String s,Object...a){} public static void exiting(String m,String s,Object...a){} public static void traceBranch(String m,String s,Object...a){} public static void traceStateChange(String m,String s,Object...a){} public static final class ModuleLogger { public void notice(String s,Object...a){} public void info(String s,Object...a){} public void debug(String s,Object...a){} public void trace(String s,Object...a){} public void warn(String s,Object...a){} public void error(String s,Object...a){} public boolean isInfoEnabled(){return false;} public boolean isDebugEnabled(){return false;} public boolean isTraceEnabled(){return false;} } }
""",
    P + "/chat/ModMessageSource.java": """
package top.csituka.youzaiworldcore.chat;
public final class ModMessageSource {
 public static java.util.Optional<String> caller(){return java.util.Optional.empty();}
}
""",
    P + "/client/config/ChatStyleSettings.java": """
package top.csituka.youzaiworldcore.client.config;
public final class ChatStyleSettings {
 private static top.csituka.youzaiworldcore.client.chat.ChatStyleMode mode=top.csituka.youzaiworldcore.client.chat.ChatStyleMode.GRADIENT;
 public static top.csituka.youzaiworldcore.client.chat.ChatStyleMode getMode(){return mode;}
 public static void setMode(top.csituka.youzaiworldcore.client.chat.ChatStyleMode value){mode=value;}
}
""",
    "net/minecraft/client/gui/Font.java": """
package net.minecraft.client.gui;
public final class Font {
 private final net.minecraft.client.StringSplitter splitter=new net.minecraft.client.StringSplitter((cp,style)->1);
 public net.minecraft.client.StringSplitter getSplitter(){return splitter;}
}
""",
    "net/minecraft/client/OptionInstance.java": """
package net.minecraft.client;
public final class OptionInstance<T> {private final T value;public OptionInstance(T value){this.value=value;}public T get(){return value;}}
""",
    "net/minecraft/client/Options.java": """
package net.minecraft.client;
public final class Options {public boolean colors=true;public OptionInstance<Boolean> chatColors(){return new OptionInstance<>(colors);}}
""",
    "net/minecraft/client/Minecraft.java": """
package net.minecraft.client;
public final class Minecraft {
 private static final Minecraft INSTANCE=new Minecraft();public final Options options=new Options();
 public final net.minecraft.client.gui.Gui gui=null;
 public static Minecraft getInstance(){return INSTANCE;}
}
""",
}

CHECKS = r"""
import java.util.*;
import net.minecraft.network.chat.*;
import net.minecraft.client.gui.Font;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.fabricmc.fabric.api.client.command.v2.*;
import net.fabricmc.fabric.impl.command.client.ClientCommandInternals;
import com.mojang.brigadier.CommandDispatcher;
import top.csituka.youzaiworldcore.chat.ChatMessageStyle;
import top.csituka.youzaiworldcore.client.chat.*;
import top.csituka.youzaiworldcore.command.ChatStyleClientCommand;
import top.csituka.youzaiworldcore.mixin.client.ClientCommandSuggestionsMixin;

public final class NativeChatChecks {
 static int checks;
 record Glyph(int codePoint,int color) {}
 static void check(boolean result,String label){checks++;if(!result)throw new AssertionError(label);}
 static Component line(Component header,Component body){return Component.translatable("%s >> %s",header,body)
  .withStyle(style->style.withColor(0xF3EFEF));}
 static Component roundTrip(Component original){
  var buffer=io.netty.buffer.Unpooled.buffer();
  try{
   ComponentSerialization.TRUSTED_CONTEXT_FREE_STREAM_CODEC.encode(buffer,original);
   return ComponentSerialization.TRUSTED_CONTEXT_FREE_STREAM_CODEC.decode(buffer);
  }finally{buffer.release();}
 }
 static List<Glyph> glyphs(Component component){
  List<Glyph> result=new ArrayList<>();component.visit((style,text)->{
   text.codePoints().forEach(cp->result.add(new Glyph(cp,style.getColor()==null?-1:style.getColor().getValue())));
   return Optional.empty();
  },Style.EMPTY);return result;
 }
 static List<Glyph> wrapped(Component component,int width){
  List<Glyph> result=new ArrayList<>();
  for(var line:new GuiMessage(1,component,null,null,null).splitLines(new Font(),width)){
   line.accept((index,style,cp)->{
    // 原版续行缩进使用空样式；只排除这种额外加入的空格。
    if(cp!=' '||style.getColor()!=null)result.add(new Glyph(cp,style.getColor()==null?-1:style.getColor().getValue()));return true;
   });
  }
  return result;
 }
 static String text(List<Glyph> glyphs){StringBuilder result=new StringBuilder();glyphs.forEach(g->result.appendCodePoint(g.codePoint()));return result.toString();}
 static List<Glyph> span(List<Glyph> chars,String target){
  String full=text(chars);int offset=full.indexOf(target);if(offset<0)throw new AssertionError("未找到字段："+target+"，实际："+full);
  int start=full.codePointCount(0,offset);return chars.subList(start,start+target.codePointCount(0,target.length()));
 }
 static void color(List<Glyph> chars,String target,int expected,String label){check(span(chars,target).stream().allMatch(g->g.color()==expected),label);}
 static void gradient(List<Glyph> chars,String target,int start,int end,String label){var part=span(chars,target);
  check(part.getFirst().color()==start&&part.getLast().color()==end,label);}

 static void colors(){
  Component body=Component.literal("Hello World!");Component title=Component.literal("{管理员}").withStyle(net.minecraft.ChatFormatting.WHITE);
  Component[] original={line(ChatMessageStyle.playerHeader(false,"Player595",16,title),body),
   line(ChatMessageStyle.playerHeader(true,"Player595",16,title),body),ChatMessageStyle.game("Player595",body),ChatMessageStyle.mod("YouzaiWorldCore",body)};
  String[] labels={"[聊天]","[私信]","[游戏]","[模组]"};String[] names={"Player595","Player595","Player595","YouzaiWorldCore"};
  int[] plain={0xE77280,0xF36B7B,0xF47B8A,0x007AFF};
  int[] start={0xFF3364,0xC03AFF,0xFF8A00,0x007AFF},end={0xD0529E,0xDF61FF,0xE79D0E,0xC03AFF};
  for(ChatStyleMode mode:ChatStyleMode.values())for(boolean vanillaColors:List.of(true,false))for(int i=0;i<original.length;i++){
   net.minecraft.client.Minecraft.getInstance().options.colors=vanillaColors;
   Component decoded=roundTrip(original[i]);check(decoded.getString().equals(original[i].getString()),"原版网络编码保留格式文字");
   Component rendered=roundTrip(ChatMessageStyle.repeated(ChatMessageRenderer.render(decoded,mode),3));
   List<Glyph> chars=wrapped(rendered,1000);
   check(text(chars).equals(original[i].getString()+" [!3]"),"实际聊天分行保留整条文字");
   check(chars.stream().filter(g->!Character.isWhitespace(g.codePoint())).allMatch(g->g.color()>=0&&g.color()!=0xFFFFFF),"所有可见字段有明确色板颜色，无默认纯白");
   if(mode==ChatStyleMode.PLAIN)color(chars,labels[i],plain[i],"实际分行后的纯色标签");
   else gradient(chars,labels[i],start[i],end[i],"实际分行后的渐变标签");
   if(mode==ChatStyleMode.GRADIENT&&i<2)gradient(chars,names[i],start[i],end[i],"实际分行后的玩家名渐变");
   else color(chars,names[i],0xF7F7F7,"实际分行后的近白色来源名称");
   color(chars,"[!3]",0xE7E4E4,"网络编码和分行后计数仍是指定灰色");
   color(chars,"Hello World!",0xF3EFEF,"实际分行后的正文色");color(chars,"|",0x555555,"实际分行后的竖线颜色");
   if(i<2){color(chars,"Lv.16",0xF36B7B,"实际分行后的等级色");color(chars,"{管理员}",0xF47B8A,"目录原白色称号被显示色板覆盖");}
   List<Glyph> narrow=wrapped(rendered,13);color(narrow,"[!3]",0xE7E4E4,"窄聊天窗口换行后仍保留计数色");
   check(narrow.stream().filter(g->!Character.isWhitespace(g.codePoint())).allMatch(g->g.color()>=0&&g.color()!=0xFFFFFF),"窄聊天窗口换行不引入纯白字段");
  }
  var empty=roundTrip(line(ChatMessageStyle.playerHeader(true,"Player595",6,Component.empty()),Component.literal("1")));
  check(empty.getString().equals("[私信]|Lv.6·Player595 >> 1"),"用户单人私信示例保留空称号前点号");
  gradient(glyphs(empty),"Player595",0xC03AFF,0xDF61FF,"发送组件本身已携带私信玩家名渐变");
  color(glyphs(ChatMessageStyle.repeated(empty,3)),"[!3]",0xE7E4E4,"未经客户端重绘的计数也使用指定色板");
 }

 static void completions()throws Exception{
  var local=new CommandDispatcher<FabricClientCommandSource>();ChatStyleClientCommand.addCompletionNodes(local);
  ClientCommandInternals.setActiveDispatcher(local);
  var suggestions=new CommandDispatcher<FabricClientCommandSource>();
  suggestions.register(ClientCommands.literal("yzwc").then(ClientCommands.literal("server_only").executes(c->7)));
  var serverNode=suggestions.getRoot().getChild("yzwc").getChild("server_only");
  ClientCommandInternals.addCommands(suggestions,null);
  check(suggestions.getRoot().getChild("yzwc").getChild("chat_style")==null,"真实 Fabric 合并重现同名根丢失客户端补全");
  var hook=ClientCommandSuggestionsMixin.class.getDeclaredMethod("youzaiworldcore$restoreChatStyleSuggestions",CommandDispatcher.class,
   FabricClientCommandSource.class,org.spongepowered.asm.mixin.injection.callback.CallbackInfo.class);
  hook.setAccessible(true);hook.invoke(null,suggestions,null,null);
  check(suggestions.getRoot().getChild("yzwc").getChild("chat_style")!=null,"实际修复挂上此前丢失的节点");
  var first=suggestions.getCompletionSuggestions(suggestions.parse("yzwc ",null)).get().getList();
  check(first.stream().anyMatch(s->s.getText().equals("chat_style")),"原版一级补全出现 chat_style");
  var names=suggestions.getCompletionSuggestions(suggestions.parse("yzwc chat_style ",null)).get().getList();
  check(names.stream().map(com.mojang.brigadier.suggestion.Suggestion::getText).collect(java.util.stream.Collectors.toSet())
   .equals(Set.of("plain","gradient")),"原版名称补全包含两套配色");
  check(suggestions.getRoot().getChild("yzwc").getChild("server_only")==serverNode&&suggestions.execute("yzwc server_only",null)==7,"修复保留服务端原有节点和执行器");
  ClientCommandInternals.addCommands(suggestions,null);hook.invoke(null,suggestions,null,null);
  check(suggestions.getRoot().getChild("yzwc").getChild("chat_style").getChildren().size()==2,"重复收到命令树时补全修复不重复新增节点");
 }
 public static void main(String[] args)throws Exception{
  // 原版组件编解码器引用静态注册表，在离线进程内初始化，不创建世界或游戏窗口。
  net.minecraft.SharedConstants.tryDetectVersion();net.minecraft.server.Bootstrap.bootStrap();
  colors();completions();System.out.println("真实 Minecraft 编解码、分行与 Fabric 补全回归通过："+checks+" 项断言。");
 }
}
"""


def verify_native_runtime(binary):
    cache = Path.home() / ".gradle/caches/modules-2/files-2.1"
    libraries = sorted(p for p in cache.glob("*/*/*/*/*.jar")
                       if not p.name.endswith(("-sources.jar", "-javadoc.jar")))
    minecraft = ROOT / "minecraft_jar/26.3/26.3.jar"
    assert minecraft.is_file(), "需要 Minecraft 26.3 原版 jar"
    assert any(p.name.startswith("fabric-command-api-v2-") for p in libraries), "需要缓存的 Fabric 命令 API"
    production = [
        ROOT / ("src/main/java/" + P + "/chat/ChatMessageStyle.java"),
        ROOT / ("src/client/java/" + P + "/client/chat/ChatMessageRenderer.java"),
        ROOT / ("src/client/java/" + P + "/client/chat/ChatStyleMode.java"),
        ROOT / ("src/client/java/" + P + "/command/ChatStyleClientCommand.java"),
        ROOT / ("src/client/java/" + P + "/mixin/client/ClientCommandSuggestionsMixin.java"),
    ]
    with tempfile.TemporaryDirectory(prefix="yzwc-native-chat-") as directory:
        temporary = Path(directory)
        # 官方 jar 带签名，不能和同包的测试替身混用。只在临时副本删除签名元数据，类字节保持原样。
        unsigned = temporary / "minecraft-fixture.jar"
        with ZipFile(minecraft) as original, ZipFile(unsigned, "w") as fixture:
            for entry in original.infolist():
                name = entry.filename.upper()
                if name == "META-INF/MANIFEST.MF" or (name.startswith("META-INF/") and name.endswith((".SF", ".RSA", ".DSA", ".EC"))):
                    continue
                fixture.writestr(entry, original.read(entry.filename))
        classpath = os.pathsep.join([str(unsigned), *map(str, libraries)])
        files = []
        for name, source in {**STUBS, "NativeChatChecks.java": CHECKS}.items():
            file = temporary / name
            file.parent.mkdir(parents=True, exist_ok=True)
            file.write_text(source)
            files.append(str(file))
        compiled = subprocess.run(
            [str(binary / "javac"), "-J-XX:-UsePerfData", "--release", "25", "-proc:none", "-encoding", "UTF-8",
             "-cp", classpath, "-d", str(temporary), *files, *map(str, production), str(Path(__file__).resolve().parents[1] / "src/main/java/top/csituka/youzaiworldcore/util/LogValues.java")],
            cwd=ROOT, capture_output=True, text=True,
        )
        if compiled.returncode:
            print(compiled.stderr)
            raise SystemExit(compiled.returncode)
        runtime = subprocess.run([str(binary / "java"), "-XX:-UsePerfData", "-cp", str(temporary) + os.pathsep + classpath,
                                  "NativeChatChecks"], cwd=temporary, capture_output=True, text=True)
        if runtime.stdout:
            print(runtime.stdout, end="")
        if runtime.returncode:
            print(runtime.stderr)
            raise SystemExit(runtime.returncode)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--java-home")
    args = parser.parse_args()
    if args.java_home:
        binary = Path(args.java_home) / "bin"
    elif os.environ.get("JAVA_HOME"):
        binary = Path(os.environ["JAVA_HOME"]) / "bin"
    elif shutil.which("javac"):
        binary = Path(shutil.which("javac")).parent
    else:
        candidates = list((Path.home() / ".gradle/jdks").glob("*/bin/javac"))
        assert candidates, "需要本地 JDK 25，或通过 --java-home 指定路径"
        binary = candidates[0].parent
    verify_native_runtime(binary)


if __name__ == "__main__":
    main()
