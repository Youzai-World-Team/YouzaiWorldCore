"""验证真实 Minecraft 组件编码、聊天分行、字体颜色及 Fabric 补全；不构建模组或启动游戏。"""
import argparse
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
from zipfile import ZipFile

ROOT = Path(__file__).resolve().parents[1]
P = "top/csituka/youzaiworldcore"

# 字形只提供固定字宽并记录绘制颜色，Minecraft 字体及其余链路均使用实际 jar。
STUBS = {
    P + "/skill/PlayerLevelStorage.java": """
package top.csituka.youzaiworldcore.skill;
public final class PlayerLevelStorage {
 public static Data get(java.util.UUID id){return null;}
 public record Data(int getLevel,int getCurrentLevelExp,int getExpForNextLevel) {}
}
""",
    P + "/skill/AdventureLevelManager.java": """
package top.csituka.youzaiworldcore.skill;
public final class AdventureLevelManager {public static int expForNextLevel(int level){return 200;}}
""",
    P + "/title/TitleManager.java": """
package top.csituka.youzaiworldcore.title;
public final class TitleManager {
 public static net.minecraft.network.chat.Component getEquippedComponent(net.minecraft.server.level.ServerPlayer p){return net.minecraft.network.chat.Component.empty();}
}
""",
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
    "TestGlyphs.java": """
import java.util.*;
import net.minecraft.client.gui.*;
import net.minecraft.client.gui.font.glyphs.*;
import net.minecraft.client.gui.font.TextRenderable;
import net.minecraft.network.chat.*;
public final class TestGlyphs {
 record Ink(int codePoint,int argb,Style style,float x,float y) {}
 record Area(float left,float top,float right,float bottom,Style style) implements TextRenderable.Styled {
  public void render(org.joml.Matrix4fc pose,com.mojang.blaze3d.vertex.VertexConsumer consumer,int light,boolean inverse){}
  public net.minecraft.client.renderer.rendertype.RenderType renderType(Font.DisplayMode mode){return null;}
  public com.mojang.renderpearl.api.textures.GpuTextureView textureView(){return null;}
  public com.mojang.renderpearl.api.pipeline.RenderPipeline guiPipeline(){return null;}
 }
 static final List<Ink> ink=new ArrayList<>();
 static final GlyphSource glyphs=new GlyphSource(){
  public BakedGlyph getGlyph(int cp){return new BakedGlyph(){
   private final int width=cp==0xFFFC?8:1;
   public com.mojang.blaze3d.font.GlyphInfo info(){return com.mojang.blaze3d.font.GlyphInfo.simple(width);}
   public TextRenderable.Styled createGlyph(float x,float y,int color,int shadow,Style style,float bold,float offset){
    ink.add(new Ink(cp,color,style,x,y));return new Area(x,y,x+width,y+9,style);
   }
  };}
  public BakedGlyph getRandomGlyph(net.minecraft.util.RandomSource random,int cp){return getGlyph(cp);}
 };
 static Font font(){return new Font(new Font.Provider(){
  public GlyphSource glyphs(FontDescription description){return glyphs;}
  public EffectGlyph effect(){throw new AssertionError("此检查不绘制装饰线或背景");}
 });}
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
import top.csituka.youzaiworldcore.chat.ChatEventType;
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
  for(var line:new GuiMessage(1,component,null,null,null).splitLines(TestGlyphs.font(),width)){
   line.accept((index,style,cp)->{
    // 原版续行缩进使用空样式；只排除这种额外加入的空格。
    if(cp!=' '||style.getColor()!=null)result.add(new Glyph(cp,style.getColor()==null?-1:style.getColor().getValue()));return true;
   });
  }
  return result;
 }
 static List<Glyph> drawn(Component component,int width,float opacity){
  Font font=TestGlyphs.font();TestGlyphs.ink.clear();
  int defaultColor=net.minecraft.util.ARGB.white(opacity);
  for(var line:new GuiMessage(1,component,null,null,null).splitLines(font,width)){
   // 原版 GUI 文字状态以白色为默认色；真实 Font 必须使用每个字形的样式颜色覆盖 RGB。
   new net.minecraft.client.renderer.state.gui.GuiTextRenderState(font,line,new org.joml.Matrix3x2f(),
    0,0,defaultColor,0,true,true,null).ensurePrepared();
  }
  check(TestGlyphs.ink.stream().allMatch(g->(g.argb()>>>24)==(defaultColor>>>24)),"字体保留聊天透明度");
  return TestGlyphs.ink.stream().filter(g->g.codePoint()!=' '||g.style().getColor()!=null)
   .map(g->new Glyph(g.codePoint(),g.argb()&0xFFFFFF)).toList();
 }
 static String text(List<Glyph> glyphs){StringBuilder result=new StringBuilder();glyphs.forEach(g->result.appendCodePoint(g.codePoint()));return result.toString();}
 static List<Glyph> span(List<Glyph> chars,String target){
  String full=text(chars);int offset=full.indexOf(target);if(offset<0)throw new AssertionError("未找到字段："+target+"，实际："+full);
  int start=full.codePointCount(0,offset);return chars.subList(start,start+target.codePointCount(0,target.length()));
 }
 static void color(List<Glyph> chars,String target,int expected,String label){
  var field=span(chars,target);check(field.stream().allMatch(g->g.color()==expected),
   label+"，字段="+target+"，预期="+Integer.toHexString(expected)+"，实际="+field);
 }
 static void gradient(List<Glyph> chars,String target,int start,int end,String label){var part=span(chars,target);
  check(part.getFirst().color()==start&&part.getLast().color()==end,label);}

 static void colors(){
  Component body=Component.literal("Hello World!");Component title=Component.literal("{管理员}").withStyle(net.minecraft.ChatFormatting.WHITE);
  Component[] original={line(ChatMessageStyle.playerHeader(false,"Player595",16,title),body),
   line(ChatMessageStyle.playerHeader(true,"Player595",16,title),body),ChatMessageStyle.game("Player595",body),
   ChatMessageStyle.mod("YouzaiWorldCore",body),line(ChatMessageStyle.gameHeader("Player595"),body)};
  String[] labels={"[聊天]","[私信]","[游戏]","[模组]","[游戏]"};
  String[] names={"Player595","Player595","Player595","YouzaiWorldCore","Player595"};
  int[] plain={0xE77280,0xF36B7B,0xF47B8A,0x007AFF,0xF47B8A};
  int[] start={0xFF3364,0xC03AFF,0xFF8A00,0x007AFF,0xFF8A00},end={0xD0529E,0xDF61FF,0xE79D0E,0xC03AFF,0xE79D0E};
  int[] namePlain={0xF7F7F7,0xF7F7F7,0xFF8A00,0x5B8CFF,0xFF8A00},
   nameStart={0xFF3364,0xC03AFF,0xFF8A00,0x5B8CFF,0xFF8A00};
  for(ChatStyleMode mode:ChatStyleMode.values())for(boolean vanillaColors:List.of(true,false))for(int i=0;i<original.length;i++){
   net.minecraft.client.Minecraft.getInstance().options.colors=vanillaColors;
   Component decoded=roundTrip(original[i]);check(decoded.getString().equals(original[i].getString()),"原版网络编码保留格式文字");
   Component rendered=roundTrip(ChatMessageStyle.repeated(ChatMessageRenderer.render(decoded,mode),3));
   List<Glyph> chars=wrapped(rendered,1000);
   check(text(chars).equals(original[i].getString()+" [!3]"),"实际聊天分行保留整条文字");
   check(chars.stream().filter(g->!Character.isWhitespace(g.codePoint())).allMatch(g->g.color()>=0&&g.color()!=0xFFFFFF),"所有可见字段有明确色板颜色，无默认纯白");
   if(mode==ChatStyleMode.PLAIN)color(chars,labels[i],plain[i],"实际分行后的纯色标签");
   else gradient(chars,labels[i],start[i],end[i],"实际分行后的渐变标签");
   if(mode==ChatStyleMode.GRADIENT)gradient(chars,names[i],nameStart[i],end[i],"实际分行后的玩家名和来源名渐变");
   else color(chars,names[i],namePlain[i],"实际分行后的名称纯色");
   color(chars,"[!3]",0x2DD4BF,"网络编码和分行后计数仍是指定青色");
   color(chars,"Hello World!",0xF3EFEF,"实际分行后的正文色");color(chars,"|",0x555555,"实际分行后的竖线颜色");
   if(i<2){color(chars,"Lv.16",0xF36B7B,"实际分行后的等级色");color(chars,"{管理员}",0xF47B8A,"目录原白色称号被显示色板覆盖");}
   List<Glyph> narrow=wrapped(rendered,13);color(narrow,"[!3]",0x2DD4BF,"窄聊天窗口换行后仍保留计数色");
   check(narrow.stream().filter(g->!Character.isWhitespace(g.codePoint())).allMatch(g->g.color()>=0&&g.color()!=0xFFFFFF),"窄聊天窗口换行不引入纯白字段");
   for(int width:List.of(1000,13))for(float opacity:List.of(1f,0.5f)){
    List<Glyph> ink=drawn(rendered,width,opacity);
    color(ink,"[!3]",0x2DD4BF,"真实字体生成的计数绘制 RGB 是指定青色");
    // 自动换行会吃掉行尾空格，分别检查两段可见正文。
    color(ink,"Hello",0xF3EFEF,"真实字体生成的正文前段绘制 RGB");
    color(ink,"World!",0xF3EFEF,"真实字体生成的正文后段绘制 RGB");
    if(mode==ChatStyleMode.GRADIENT)gradient(ink,names[i],nameStart[i],end[i],"真实字体生成的玩家名和来源名渐变");
    else color(ink,names[i],namePlain[i],"真实字体生成的名称纯色 RGB");
   }
  }
  var comparison=drawn(Component.literal("W").withStyle(style->style.withColor(0xFFFFFF))
   .append(Component.literal("G").withStyle(style->style.withColor(0xFF8A00)))
   .append(Component.literal("M").withStyle(style->style.withColor(0x5B8CFF)))
   .append(Component.literal("C").withStyle(style->style.withColor(0x2DD4BF))),1000,1f);
  color(comparison,"W",0xFFFFFF,"对照字形使用原版纯白");
  color(comparison,"G",0xFF8A00,"游戏名称橙色字形与原版纯白保持不同 RGB");
  color(comparison,"M",0x5B8CFF,"模组名称蓝色字形与原版纯白保持不同 RGB");
  color(comparison,"C",0x2DD4BF,"计数青色字形与原版纯白保持不同 RGB");
  System.out.println("字体绘制颜色对照：原版白=#FFFFFF，发布者=#FF8A00，模组名=#5B8CFF，重复计数=#2DD4BF");
  var empty=roundTrip(line(ChatMessageStyle.playerHeader(true,"Player595",6,Component.empty()),Component.literal("1")));
  check(empty.getString().equals("[私信]|Lv.6·Player595 >> 1"),"用户单人私信示例保留空称号前点号");
  gradient(glyphs(empty),"Player595",0xC03AFF,0xDF61FF,"发送组件本身已携带私信玩家名渐变");
  color(glyphs(ChatMessageStyle.repeated(empty,3)),"[!3]",0x2DD4BF,"未经客户端重绘的计数也使用指定色板");
  var receipt=roundTrip(ChatMessageStyle.privateSent(ChatMessageStyle.playerHeader(true,"Player595",6,Component.empty()),
   Component.literal("PeerOne, PeerTwo")));
  check(ChatMessageStyle.isPrivateReceipt(receipt)&&ChatMessageStyle.isFormatted(receipt),"网络编码后仍能结构化识别发送回执");
  for(ChatStyleMode mode:ChatStyleMode.values()){
   var shown=ChatMessageRenderer.render(receipt,mode);
   check(shown.getString().equals("[私信]|Lv.6·Player595 >> 已发送给 PeerOne, PeerTwo"),"发送回执包含全部收件人且不重复正文");
   var ink=drawn(shown,1000,1f);
   color(ink,"已发送给",0xF3EFEF,"真实字体保留发送确认颜色");
   color(ink,"PeerOne, PeerTwo",0xF3EFEF,"真实字体保留回执收件人名称");
   if(mode==ChatStyleMode.GRADIENT)gradient(ink,"Player595",0xC03AFF,0xDF61FF,"真实字体保留回执的发送者私信渐变");
   else color(ink,"Player595",0xF7F7F7,"真实字体保留纯色回执发送者名称");
  }
 }

 static void originalBodyColors(){
  var rich=Style.EMPTY.withColor(0x112233).withBold(true).withItalic(true)
   .withClickEvent(new ClickEvent.RunCommand("/help"))
   .withHoverEvent(new HoverEvent.ShowText(Component.literal("详情")))
   .withInsertion("原文");
  Component body=Component.translatableWithFallback("example.message","Base %s %s",
   Component.literal("Red").withStyle(net.minecraft.ChatFormatting.RED),Component.literal("Inherited"))
   .setStyle(rich).append(Component.literal(" White").withStyle(net.minecraft.ChatFormatting.WHITE))
   .append(Component.literal(" Tail"));
  Component[] originals={line(ChatMessageStyle.playerHeader(false,"Player",16,Component.empty()),body),
   line(ChatMessageStyle.playerHeader(true,"Player",16,Component.empty()),body),
   ChatMessageStyle.game("Server",body),ChatMessageStyle.mod("Mod",body),line(ChatMessageStyle.gameHeader("Server"),body)};
  for(ChatStyleMode mode:ChatStyleMode.values())for(Component original:originals){
   Component decoded=roundTrip(original);
   color(glyphs(decoded),"Base",0x112233,"服务端包装及网络编码保留正文根颜色");
   Component rendered=roundTrip(ChatMessageRenderer.render(decoded,mode));
   check(rendered.getString().equals(original.getString()),"保留正文翻译和原始文字");
   Component shownBody=rendered.getSiblings().getLast();
   check(shownBody.getStyle().equals(rich),"保留正文颜色和完整交互样式");
   check(shownBody.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents translated
    &&translated.getKey().equals("example.message")&&translated.getFallback().equals("Base %s %s")
    &&translated.getArgs().length==2,"保留翻译键、回退及参数");
   color(glyphs(rendered),"Base",0x112233,"保留正文根颜色");
   color(glyphs(rendered),"Red",0xFF5555,"保留翻译参数原色");
   color(glyphs(rendered),"Inherited",0x112233,"未染色翻译参数继承正文颜色");
   color(glyphs(rendered),"White",0xFFFFFF,"保留子组件显式白色");
   color(glyphs(rendered),"Tail",0x112233,"未染色子组件继承正文颜色");
   for(int width:List.of(1000,13))for(float opacity:List.of(1f,0.5f)){
    var ink=drawn(roundTrip(ChatMessageStyle.repeated(rendered,3)),width,opacity);
    color(ink,"Base",0x112233,"真实分行和字体绘制保留正文根颜色");
    color(ink,"Red",0xFF5555,"真实字体绘制保留翻译参数红色");
    color(ink,"Inherited",0x112233,"真实字体绘制保留参数继承色");
    color(ink,"White",0xFFFFFF,"真实字体绘制保留显式白色");
    color(ink,"Tail",0x112233,"真实字体绘制保留子组件继承色");
    color(ink,"[!3]",0x2DD4BF,"带原色正文的消息计数保持青色");
   }
   var suffix=ChatMessageRenderer.render(decoded.copy().append(
    Component.literal(" Suffix").withStyle(net.minecraft.ChatFormatting.RED)),mode);
   color(glyphs(roundTrip(suffix)),"Suffix",0xFF5555,"附加正文原色经重绘和网络编码仍保留");
   var rerendered=ChatMessageRenderer.render(ChatMessageStyle.repeated(decoded,3),mode);
   color(glyphs(roundTrip(rerendered)),"[!3]",0x2DD4BF,"重绘已有后缀的消息保留计数色");
   check(originals[0].getString().endsWith(body.getString())&&body.getStyle().equals(rich),"显示配色不修改原始正文");
  }
 }

 static void eventColors(){
  for(ChatEventType type:ChatEventType.values())for(ChatStyleMode mode:ChatStyleMode.values()){
   var original=ChatMessageStyle.event(type,"Player",16,Component.literal("{管理员}"),
    Component.literal("Original").withStyle(style->style.withColor(0x55FF55)
     .withHoverEvent(new HoverEvent.ShowText(Component.literal("详情")))));
   var decoded=roundTrip(original);
   check(ChatMessageStyle.isFormatted(decoded)&&ChatMessageStyle.system(decoded,null)==decoded,"事件网络编码后不叠加来源前缀");
   var rendered=roundTrip(ChatMessageRenderer.render(decoded,mode));
   check(rendered.getString().equals(type.label()+"|Lv.16·{管理员}Player >> Original"),"真实事件文字排版");
   for(int width:List.of(1000,13))for(float opacity:List.of(1f,0.5f)){
    var ink=drawn(ChatMessageStyle.repeated(rendered,3),width,opacity);
    if(mode==ChatStyleMode.PLAIN){color(ink,type.label(),type.plainColor(),"事件标签实际绘制纯色");
     color(ink,"Player",0xF7F7F7,"事件玩家名实际绘制纯色");}
    else{gradient(ink,type.label(),type.gradientStart(),type.gradientEnd(),"事件标签实际绘制渐变");
     gradient(ink,"Player",type.gradientStart(),type.gradientEnd(),"事件玩家名实际绘制渐变");}
    color(ink,"Lv.16",0xF36B7B,"事件等级实际绘制颜色");
    color(ink,"{管理员}",0xF47B8A,"事件称号实际绘制颜色");
    color(ink,"Original",0x55FF55,"事件正文实际绘制保留原色");
    color(ink,"[!3]",0x2DD4BF,"事件计数实际绘制颜色");
   }
  }
 }

 static void playerHover(){
  Component tooltip=Component.literal("玩家：A🙂B\n冒险等级：Lv.16\n升级经验：120 / 500\n佩戴称号：{管理员}\n游戏模式：生存");
  var profile=new com.mojang.authlib.GameProfile(UUID.randomUUID(),"Alex");
  var resolvable=net.minecraft.world.item.component.ResolvableProfile.createResolved(profile);
  var avatar=Component.object(new net.minecraft.network.chat.contents.objects.PlayerSprite(resolvable,true),Component.empty())
   .withStyle(style->style.withColor(0xFFFFFF));
  Component name=Component.empty().withStyle(style->style.withHoverEvent(new HoverEvent.ShowText(tooltip)))
   .append(avatar).append("A🙂B");
  Component body=Component.literal("Body").withStyle(style->style.withColor(0x112233));
  var originals=new ArrayList<Component>(List.of(line(ChatMessageStyle.playerHeader(false,name,16,Component.empty()),body),
   line(ChatMessageStyle.playerHeader(true,name,16,Component.empty()),body),ChatMessageStyle.game(name,body),
   line(ChatMessageStyle.gameHeader(name),body),ChatMessageStyle.privateSent(ChatMessageStyle.playerHeader(true,name,16,Component.empty()),name)));
  for(ChatEventType type:ChatEventType.values())originals.add(ChatMessageStyle.event(type,name,16,Component.empty(),body));
  for(ChatStyleMode mode:ChatStyleMode.values())for(Component original:originals){
   Component shown=roundTrip(ChatMessageStyle.repeated(ChatMessageRenderer.render(roundTrip(original),mode),3));
   check(shown.getString().equals(original.getString()+" [!3]"),"头像无纯文本占位符");
   for(int width:List.of(1000,13)){
    List<Style> playerStyles=new ArrayList<>();StringBuilder text=new StringBuilder();
    for(var line:new GuiMessage(1,shown,null,null,null).splitLines(TestGlyphs.font(),width)){
     line.accept((index,style,cp)->{
      if(style.getHoverEvent() instanceof HoverEvent.ShowText hover){
       check(hover.value().getString().equals(tooltip.getString()),"网络编码和真实分行保留资料全文");
       if(cp==0xFFFC)check(style.getFont() instanceof FontDescription.PlayerSprite sprite
        &&sprite.profile().partialProfile().id().equals(profile.id())&&sprite.hat()
        &&style.getColor().getValue()==0xFFFFFF,"头像经编解码、着色及分行保留正确的皮肤和原色");
       playerStyles.add(style);text.appendCodePoint(cp);
      }
      return true;
     });
    }
    String expected=ChatMessageStyle.isPrivateReceipt(original)?"\uFFFCA🙂B\uFFFCA🙂B":"\uFFFCA🙂B";
    check(text.toString().equals(expected)&&playerStyles.size()==expected.codePointCount(0,expected.length()),"头像始终排在玩家名前，头像和名称共享资料悬停");
    for(float scale:List.of(1f,1.5f))for(var line:new GuiMessage(1,shown,null,null,null).splitLines(TestGlyphs.font(),width)){
     TestGlyphs.ink.clear();var pose=new org.joml.Matrix3x2f().translate(7,9).scale(scale);
     var state=new net.minecraft.client.renderer.state.gui.GuiTextRenderState(TestGlyphs.font(),line,pose,
      0,0,0xFFFFFFFF,0,true,true,null);
     state.ensurePrepared();
     for(var glyph:TestGlyphs.ink){
      var point=pose.transformPosition(new org.joml.Vector2f(glyph.x()+0.5f,glyph.y()+4.5f));
      List<Style> hits=new ArrayList<>();
      net.minecraft.client.gui.ActiveTextCollector.findElementUnderCursor(state,point.x,point.y,hits::add);
      if(glyph.style().getHoverEvent() instanceof HoverEvent.ShowText){
       check(hits.stream().anyMatch(style->style.getHoverEvent() instanceof HoverEvent.ShowText hover
        &&hover.value().getString().equals(tooltip.getString())),"原版鼠标检测在缩放和换行后命中玩家资料");
      }else{
       check(hits.stream().noneMatch(style->style.getHoverEvent() instanceof HoverEvent.ShowText),"非玩家字段不误显示玩家资料");
      }
     }
    }
    var ink=drawn(shown,width,1f);color(ink,"[!3]",0x2DD4BF,"玩家悬停不影响真实绘制的计数颜色");
    if(!ChatMessageStyle.isPrivateReceipt(original))color(ink,"Body",0x112233,"玩家悬停不影响正文原色");
   }
  }
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
  colors();originalBodyColors();eventColors();playerHover();completions();System.out.println("真实 Minecraft 编解码、分行、字体颜色与 Fabric 补全回归通过："+checks+" 项断言。");
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
        ROOT / ("src/main/java/" + P + "/chat/ChatEventType.java"),
        ROOT / ("src/main/java/" + P + "/chat/ChatPlayerInfo.java"),
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
