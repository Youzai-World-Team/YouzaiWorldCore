"""执行实际聊天格式、入口和合并代码；以替身隔离 Minecraft，不构建模组或启动游戏。"""
import argparse
import json
import os
from pathlib import Path
import shutil
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[1]
P = "top/csituka/youzaiworldcore"
STUBS = {}


def stub(name, text):
    package, _, cls = name.rpartition(".")
    STUBS[name.replace(".", "/") + ".java"] = "package " + package + ";\n" + text


stub("org.jspecify.annotations.Nullable", """
@java.lang.annotation.Target({java.lang.annotation.ElementType.TYPE_USE,java.lang.annotation.ElementType.FIELD,java.lang.annotation.ElementType.PARAMETER})
public @interface Nullable {}
""")
stub("org.jspecify.annotations.NonNull", """
@java.lang.annotation.Target({java.lang.annotation.ElementType.TYPE_USE,java.lang.annotation.ElementType.FIELD,java.lang.annotation.ElementType.PARAMETER})
public @interface NonNull {}
""")
stub("net.minecraft.ChatFormatting", """
public enum ChatFormatting { WHITE(0xffffff),GRAY(0xaaaaaa),RED(0xff5555);
 public final int color; ChatFormatting(int color){this.color=color;} }
""")
stub("net.minecraft.network.chat.Style", """
public record Style(int color,boolean bold,boolean italic) {
 public static final Style EMPTY=new Style(0xffffff,false,false);
 public Style withColor(int v){return new Style(v,bold,italic);}
 public Style withBold(boolean v){return new Style(color,v,italic);}
 public Style withItalic(boolean v){return new Style(color,bold,v);}
}
""")
stub("net.minecraft.network.chat.ComponentContents", "public interface ComponentContents { String text(); }")
stub("net.minecraft.network.chat.contents.TranslatableContents", """
import java.util.*;
import net.minecraft.network.chat.*;
public final class TranslatableContents implements ComponentContents {
 private final String key,fallback; private final Object[] args;
 public TranslatableContents(String k,String f,Object[] a){key=k;fallback=f;args=a.clone();}
 public String getKey(){return key;} public Object[] getArgs(){return args;}
 public String text(){Object[] a=Arrays.stream(args).map(v->v instanceof Component c?c.getString():v).toArray();
  return String.format(java.util.Locale.ROOT,fallback==null?key:fallback,a);}
 public boolean equals(Object o){return o instanceof TranslatableContents t&&key.equals(t.key)&&Objects.equals(fallback,t.fallback)&&Arrays.deepEquals(args,t.args);}
 public int hashCode(){return Objects.hash(key,fallback,Arrays.deepHashCode(args));}
}
""")
stub("net.minecraft.network.chat.Component", """
import java.util.*;
import net.minecraft.network.chat.contents.TranslatableContents;
public interface Component {
 ComponentContents getContents(); Style getStyle(); List<Component> getSiblings(); MutableComponent copy();
 default String getString(){StringBuilder b=new StringBuilder(getContents().text());getSiblings().forEach(c->b.append(c.getString()));return b.toString();}
 static MutableComponent literal(String s){return new MutableComponent(new Literal(s));}
 static MutableComponent empty(){return literal("");}
 static MutableComponent translatable(String k,Object... a){return translatableWithFallback(k,null,a);}
 static MutableComponent translatableWithFallback(String k,String f,Object... a){return new MutableComponent(new TranslatableContents(k,f,a));}
 record Literal(String text) implements ComponentContents {}
}
""")
stub("net.minecraft.network.chat.MutableComponent", """
import java.util.*;
import java.util.function.UnaryOperator;
import net.minecraft.ChatFormatting;
public final class MutableComponent implements Component {
 private final ComponentContents contents; private Style style=Style.EMPTY;private final List<Component> siblings=new ArrayList<>();
 public MutableComponent(ComponentContents c){contents=c;}
 public ComponentContents getContents(){return contents;} public Style getStyle(){return style;}
 public List<Component> getSiblings(){return siblings;}
 public MutableComponent append(Component c){siblings.add(c);return this;}
 public MutableComponent withStyle(ChatFormatting c){style=style.withColor(c.color);return this;}
 public MutableComponent withStyle(UnaryOperator<Style> op){style=op.apply(style);return this;}
 public MutableComponent copy(){var c=new MutableComponent(contents);c.style=style;c.siblings.addAll(siblings);return c;}
 public boolean equals(Object o){return o instanceof Component c&&contents.equals(c.getContents())&&style.equals(c.getStyle())&&siblings.equals(c.getSiblings());}
 public int hashCode(){return Objects.hash(contents,style,siblings);}
}
""")
stub("net.minecraft.resources.Identifier", """
public record Identifier(String value) { public static Identifier fromNamespaceAndPath(String ns,String p){return new Identifier(ns+":"+p);} }
""")
stub("net.minecraft.resources.ResourceKey", """
public record ResourceKey<T>(Identifier id){ public static <T> ResourceKey<T> create(Object r,Identifier id){return new ResourceKey<T>(id);} }
""")
stub("net.minecraft.core.registries.Registries", "public final class Registries { public static final Object CHAT_TYPE=new Object(); }")
stub("net.minecraft.network.chat.MessageSignature", "public record MessageSignature(String value) {}")
stub("net.minecraft.network.chat.ChatType", """
import net.minecraft.resources.ResourceKey;
import net.minecraft.commands.CommandSourceStack;
public class ChatType {
 public static final ResourceKey<ChatType> CHAT=ResourceKey.create(null,new net.minecraft.resources.Identifier("minecraft:chat"));
 public static final ResourceKey<ChatType> SAY_COMMAND=ResourceKey.create(null,new net.minecraft.resources.Identifier("minecraft:say_command"));
 public static final ResourceKey<ChatType> MSG_COMMAND_INCOMING=ResourceKey.create(null,new net.minecraft.resources.Identifier("minecraft:msg_incoming"));
 public static final ResourceKey<ChatType> MSG_COMMAND_OUTGOING=ResourceKey.create(null,new net.minecraft.resources.Identifier("minecraft:msg_outgoing"));
 public static Bound bind(ResourceKey<ChatType> key,Object registry,Component name){return new Bound(key,name);}
 public static Bound bind(ResourceKey<ChatType> key,CommandSourceStack source){return bind(key,source.getServer().registryAccess(),Component.literal(source.getTextName()));}
 public record Bound(ResourceKey<ChatType> type,Component name) {
  public Component decorate(Component body){return Component.translatable("%s >> %s",name,body);}
  public Bound withTargetName(Component target){return this;}
 }
}
""")
stub("net.minecraft.network.chat.PlayerChatMessage", """
import java.util.*;
import top.csituka.youzaiworldcore.chat.*;
public class PlayerChatMessage implements ExtPlayerChatMessage {
 public enum Filter { NONE,PARTIAL,FULL }
 private final String text; private final Component content; private final MessageSignature signature;
 private final Filter filter; private final boolean filtering;
 private final Map<String,Component> args=new HashMap<>();
 public PlayerChatMessage(String t,MessageSignature s,Filter f){this(t,Component.literal(t),s,f,false);}
 private PlayerChatMessage(String t,Component c,MessageSignature s,Filter f,boolean b){text=t;content=c;signature=s;filter=f;filtering=b;}
 public String signedContent(){return text;} public Component decoratedContent(){return filtering&&filter==Filter.PARTIAL?Component.literal("###"):content;}
 public MessageSignature signature(){return signature;}
 public PlayerChatMessage withUnsignedContent(Component c){var r=new PlayerChatMessage(text,c,signature,filter,filtering);r.args.putAll(args);return r;}
 public PlayerChatMessage filter(boolean b){var r=new PlayerChatMessage(text,content,signature,filter,b);r.args.putAll(args);return r;}
 public boolean isFullyFiltered(){return filtering&&filter==Filter.FULL;}
 public String youzaiworldcore_getOriginal(){return text;}
 public void youzaiworldcore_setArg(String k,Component v){args.put(k,v);}
 public Component youzaiworldcore_getArg(String k){return args.getOrDefault(k,ChatFormatHelper.EMPTY_TEXT);}
}
""")
stub("net.minecraft.network.chat.OutgoingChatMessage", """
public interface OutgoingChatMessage {
 Component content(); void sendToPlayer(net.minecraft.server.level.ServerPlayer p,boolean filter,ChatType.Bound b);
 static OutgoingChatMessage create(PlayerChatMessage message){return new Player(message);}
 record Player(PlayerChatMessage message) implements OutgoingChatMessage {
  public Component content(){return message.decoratedContent();}
  public void sendToPlayer(net.minecraft.server.level.ServerPlayer p,boolean filter,ChatType.Bound b) {
   PlayerChatMessage filtered=message.filter(filter);
   if(!filtered.isFullyFiltered())p.connection.sendPlayerChatMessage(filtered,b);
  }
 }
}
""")
stub("net.minecraft.server.MinecraftServer", "public class MinecraftServer { public Object registryAccess(){return this;} }")
stub("net.minecraft.server.level.ServerLevel", """
public record ServerLevel(net.minecraft.server.MinecraftServer getServer) {}
""")
stub("net.minecraft.server.level.ServerPlayer", """
import java.util.UUID;
import net.minecraft.network.chat.*;
public class ServerPlayer {
 private final UUID id;private final String name;private final ServerLevel level;
 public final Connection connection=new Connection();
 public boolean textFiltering;
 public ServerPlayer(UUID id,String n,net.minecraft.server.MinecraftServer s){this.id=id;name=n;level=new ServerLevel(s);}
 public UUID getUUID(){return id;} public String getScoreboardName(){return name;}
 public Component getName(){return Component.literal(name);} public Component getDisplayName(){return Component.literal("DISPLAY "+name);}
 public ServerLevel level(){return level;} public Stats getStats(){return new Stats();}
 public void sendChatMessage(OutgoingChatMessage message,boolean filtered,ChatType.Bound parameters){message.sendToPlayer(this,filtered,parameters);}
 public static class Stats { public int getValue(Object v){return 1;} }
 public static class Connection {
  public int sends;public Component displayed;public MessageSignature signature;
  public final java.util.List<Component> messages=new java.util.ArrayList<>();
  public final java.util.List<MessageSignature> signatures=new java.util.ArrayList<>();
  public void sendPlayerChatMessage(PlayerChatMessage m,ChatType.Bound b){sends++;displayed=b.decorate(m.decoratedContent());signature=m.signature();messages.add(displayed);signatures.add(signature);}
  public void clear(){sends=0;messages.clear();signatures.clear();}
 }
}
""")
stub("net.minecraft.commands.CommandSourceStack", """
public class CommandSourceStack extends top.csituka.youzaiworldcore.mixin.chat.CommandSourceStackChatMixin {
 private final net.minecraft.server.MinecraftServer server;private final net.minecraft.server.level.ServerPlayer player;private final String name;
 public CommandSourceStack(net.minecraft.server.MinecraftServer s,net.minecraft.server.level.ServerPlayer p,String n){server=s;player=p;name=n;}
 public net.minecraft.server.MinecraftServer getServer(){return server;}
 public net.minecraft.server.level.ServerPlayer getPlayer(){return player;} public String getTextName(){return name;}
 public boolean silent;
 public final java.util.List<net.minecraft.network.chat.Component> feedback=new java.util.ArrayList<>();
 public void sendChatMessage(net.minecraft.network.chat.OutgoingChatMessage message,boolean filtered,net.minecraft.network.chat.ChatType.Bound parameters){
  if(silent)return;
  if(player!=null)player.sendChatMessage(message,filtered,parameters);else feedback.add(parameters.decorate(message.content()));
 }
 public boolean shouldFilterMessageTo(net.minecraft.server.level.ServerPlayer target){return target!=player&&((player!=null&&player.textFiltering)||target.textFiltering);}
 public void sendSystemMessage(net.minecraft.network.chat.Component message){if(!silent)feedback.add(message);}
}
""")
stub("net.minecraft.network.Connection", "public class Connection {}")
stub("net.minecraft.server.network.CommonListenerCookie", "public class CommonListenerCookie {}")
stub("net.minecraft.server.players.PlayerList", """public class PlayerList {
 public static final net.minecraft.network.chat.Component CHAT_FILTERED_FULL=net.minecraft.network.chat.Component.literal("已完全过滤");
}""")
stub("net.minecraft.stats.Stats", """
public class Stats { public static final Object LEAVE_GAME=new Object();public static final Custom CUSTOM=new Custom();
 public static class Custom { public Object get(Object value){return value;} } }
""")
stub("net.minecraft.world.entity.Entity", "public class Entity {}")
stub("net.minecraft.commands.arguments.ComponentArgument", """
public class ComponentArgument {
 public static net.minecraft.network.chat.Component getResolvedComponent(com.mojang.brigadier.context.CommandContext<net.minecraft.commands.CommandSourceStack> c,String n,net.minecraft.world.entity.Entity e) throws com.mojang.brigadier.exceptions.CommandSyntaxException {return c.message;}
}
""")
stub("com.mojang.brigadier.context.CommandContext", """
public class CommandContext<T> { private final T source;public final net.minecraft.network.chat.Component message;
 public CommandContext(T s,net.minecraft.network.chat.Component m){source=s;message=m;}public T getSource(){return source;} }
""")
stub("com.mojang.brigadier.exceptions.CommandSyntaxException", "public class CommandSyntaxException extends Exception {}")
stub("net.minecraft.server.commands.MsgCommand", "public class MsgCommand {}")
stub("net.minecraft.server.commands.TellRawCommand", "public class TellRawCommand {}")
stub("net.minecraft.server.network.ServerCommonPacketListenerImpl", "public class ServerCommonPacketListenerImpl {}")
stub("net.minecraft.network.protocol.Packet", "public interface Packet<T> {}")
stub("net.minecraft.network.protocol.game.ClientboundSystemChatPacket", """
public record ClientboundSystemChatPacket(net.minecraft.network.chat.Component content,boolean overlay) implements net.minecraft.network.protocol.Packet<Object> {}
""")
stub("net.minecraft.client.gui.components.ChatComponent", "public class ChatComponent {}")
stub("net.minecraft.client.multiplayer.chat.GuiMessageSource", "public enum GuiMessageSource { PLAYER,SYSTEM_SERVER,SYSTEM_CLIENT }")
stub("net.minecraft.client.multiplayer.chat.GuiMessageTag", "public record GuiMessageTag(String text) {}")
stub("net.minecraft.client.multiplayer.chat.GuiMessage", """
public record GuiMessage(int addedTime,net.minecraft.network.chat.Component content,net.minecraft.network.chat.MessageSignature signature,GuiMessageSource source,GuiMessageTag tag) {
 public record Line(GuiMessage parent,String content,boolean endOfEntry) {}
}
""")
stub("net.fabricmc.loader.api.ModContainer", """
import java.util.*;
public record ModContainer(Metadata getMetadata,Set<String> resources) {
 public static int lookups;
 public ModContainer(String id,String name,Set<String> r){this(new Metadata(id,name),r);}
 public Optional<java.nio.file.Path> findPath(String name){lookups++;return resources.contains(name)?Optional.of(java.nio.file.Path.of(name)):Optional.empty();}
 public record Metadata(String getId,String getName) {}
}
""")
stub("net.fabricmc.loader.api.FabricLoader", """
public class FabricLoader {
 public static final java.util.List<ModContainer> mods=new java.util.ArrayList<>();
 private static final FabricLoader INSTANCE=new FabricLoader();
 public static FabricLoader getInstance(){return INSTANCE;}public java.util.List<ModContainer> getAllMods(){return mods;}
}
""")
stub("eu.pb4.placeholders.api.ParserContext", """
public class ParserContext { public static class Key<T>{}public <T> ParserContext with(Key<T> k,T value){return this;} }
""")
stub("eu.pb4.placeholders.api.ServerPlaceholderContext", """
public class ServerPlaceholderContext {
 public static ServerPlaceholderContext of(net.minecraft.server.level.ServerPlayer p){return new ServerPlaceholderContext();}
 public ParserContext asParserContext(){return new ParserContext();}
}
""")
stub("eu.pb4.placeholders.api.node.DynamicTextNode", """
public class DynamicTextNode {
 public static eu.pb4.placeholders.api.ParserContext.Key<java.util.function.Function<String,net.minecraft.network.chat.Component>> key(String n){return new eu.pb4.placeholders.api.ParserContext.Key<>();}
}
""")
stub("eu.pb4.placeholders.api.node.TextNode", """
public interface TextNode { net.minecraft.network.chat.Component toComponent(eu.pb4.placeholders.api.ParserContext c); }
""")
stub("eu.pb4.placeholders.api.parsers.TagLikeParser", "public class TagLikeParser { public static final Object PLACEHOLDER_USER=new Object(); }")
stub("eu.pb4.placeholders.api.parsers.NodeParser", """
public class NodeParser {
 public static Builder builder(){return new Builder();}
 public eu.pb4.placeholders.api.node.TextNode parseNode(String s){return c->net.minecraft.network.chat.Component.literal(s);}
 public static class Builder {
  public Builder simplifiedTextFormat(){return this;} public Builder serverPlaceholders(){return this;}
  public Builder placeholders(Object p,Object k){return this;}public Builder staticPreParsing(){return this;}
  public NodeParser build(){return new NodeParser();}
 }
}
""")
stub("top.csituka.youzaiworldcore.util.DebugLogger", """
public class DebugLogger {
 public static final int LEVEL_DEBUG=3,LEVEL_DETAILED=2;
 public static boolean isEnabled(int n){return false;}
 public static void info(String m,String s,Object... a){}public static void debug(String m,String s,Object... a){}
 public static void trace(String m,String s,Object... a){}public static void entering(String m,String s,Object... a){}
 public static void exiting(String m,String s,Object... a){}public static void exception(String m,String s,Throwable t){}
 public static void exceptionSummary(String m,String s,String t){}
}
""")
stub("top.csituka.youzaiworldcore.skill.PlayerLevelStorage", """
public class PlayerLevelStorage {
 public static final java.util.Map<java.util.UUID,Data> levels=new java.util.HashMap<>();
 public static Data get(java.util.UUID id){return levels.get(id);}public record Data(int getLevel) {}
}
""")
stub("top.csituka.youzaiworldcore.title.TitleManager", """
public class TitleManager {
 public static final java.util.Map<java.util.UUID,net.minecraft.network.chat.Component> titles=new java.util.HashMap<>();
 public static net.minecraft.network.chat.Component getEquippedComponent(net.minecraft.server.level.ServerPlayer p){return titles.getOrDefault(p.getUUID(),net.minecraft.network.chat.Component.empty());}
}
""")
stub("top.csituka.youzaiworldcore.config.ConfigSection", """
public class ConfigSection {
 public final java.util.Map<String,Object> values=new java.util.HashMap<>();
 public boolean isEmpty(){return values.isEmpty();}public void set(String k,Object v){values.put(k,v);}
 public boolean getBoolean(String k,boolean f){return (boolean)values.getOrDefault(k,f);}
 public String getString(String k,String f){return (String)values.getOrDefault(k,f);}
}
""")
stub("top.csituka.youzaiworldcore.config.GlobalSettings", """
public class GlobalSettings {
 public static final String CHAT_FORMAT_MODULE="chat_format_module";public static final ConfigSection SECTION=new ConfigSection();
 public static ConfigSection section(String k){return SECTION;}public static void save(){}
}
""")
stub("top.csituka.youzaiworldcore.client.higherchat.SharedStorage", """
public class SharedStorage { public static int getOptimalChatMargin(){return 40;} }
""")
stub("top.csituka.youzaiworldcore.testing.CoreEmitter", """
public class CoreEmitter {
 public static net.minecraft.network.chat.Component send(net.minecraft.network.chat.Component m,net.minecraft.commands.CommandSourceStack s){return top.csituka.youzaiworldcore.chat.ChatMessageStyle.system(m,s);}
 public static net.minecraft.network.chat.Component vanilla(net.minecraft.network.chat.Component m,net.minecraft.commands.CommandSourceStack s){return net.minecraft.server.commands.GameEmitter.send(m,s);}
}
""")
stub("example.mod.ForeignEmitter", """
public class ForeignEmitter {
 public static net.minecraft.network.chat.Component send(net.minecraft.network.chat.Component m){return top.csituka.youzaiworldcore.chat.ChatMessageStyle.system(m,null);}
}
""")
stub("net.minecraft.server.commands.GameEmitter", """
public class GameEmitter {
 public static net.minecraft.network.chat.Component send(net.minecraft.network.chat.Component m,net.minecraft.commands.CommandSourceStack s){return top.csituka.youzaiworldcore.chat.ChatMessageStyle.system(m,s);}
}
""")
stub("net.minecraft.server.commands.MergedEmitter", """
public class MergedEmitter {
 public static net.minecraft.network.chat.Component handler$foreign$notice(net.minecraft.network.chat.Component m){return top.csituka.youzaiworldcore.chat.ChatMessageStyle.system(m,null);}
}
""")
STUBS["MarkMergedFixture.java"] = """
import java.nio.file.*;
import org.objectweb.asm.*;
public class MarkMergedFixture {
 public static void main(String[] args)throws Exception {
  Path path=Path.of(args[0]);ClassReader reader=new ClassReader(Files.readAllBytes(path));
  ClassWriter writer=new ClassWriter(0);
  reader.accept(new ClassVisitor(Opcodes.ASM9,writer) {
   @Override public MethodVisitor visitMethod(int access,String name,String descriptor,String signature,String[] exceptions) {
    MethodVisitor visitor=super.visitMethod(access,name,descriptor,signature,exceptions);
    if(name.equals("handler$foreign$notice")) {
     AnnotationVisitor annotation=visitor.visitAnnotation("Lorg/spongepowered/asm/mixin/transformer/meta/MixinMerged;",true);
     annotation.visit("mixin","example.mod.mixin.Injector");annotation.visit("priority",1000);annotation.visitEnd();
    }
    return visitor;
   }
  },0);
  Files.write(path,writer.toByteArray());
 }
}
"""
stub("verify.chat.TellRoutingBridge", """
import java.util.*;
import java.lang.reflect.*;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.*;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import top.csituka.youzaiworldcore.mixin.chat.MsgCommandMixin;
public class TellRoutingBridge {
 public static boolean applyFix;
 private static Object hook(String name,Object... args) {
  try {for(Method m:MsgCommandMixin.class.getDeclaredMethods())if(m.getName().equals(name)){m.setAccessible(true);return m.invoke(null,args);}}
  catch(ReflectiveOperationException error){throw new RuntimeException(error);}
  throw new AssertionError(name);
 }
 public static PlayerChatMessage preserve(PlayerChatMessage message){return (PlayerChatMessage)hook("youzaiworldcore$preservePrivateText",message);}
 public static ChatType.Bound bind(ResourceKey<ChatType> type,CommandSourceStack source){return (ChatType.Bound)hook("youzaiworldcore$privateHeader",type,source);}
 public static void senderFeedback(CommandSourceStack feedbackSource,OutgoingChatMessage message,boolean filtered,ChatType.Bound parameters,
   CommandSourceStack commandSource,Collection<ServerPlayer> targets,PlayerChatMessage original) {
  if(applyFix)hook("youzaiworldcore$skipSelfFeedback",feedbackSource,message,filtered,parameters,commandSource,targets,original);
  else feedbackSource.sendChatMessage(message,filtered,parameters);
 }
 public static void send(CommandSourceStack source,Collection<ServerPlayer> targets,PlayerChatMessage message)throws Exception {
  Method send=net.minecraft.server.commands.MsgCommand.class.getDeclaredMethod("sendMessage",CommandSourceStack.class,Collection.class,PlayerChatMessage.class);
  send.setAccessible(true);send.invoke(null,source,targets,message);
 }
}
""")
STUBS["NativeTellFixture.java"] = """
import java.nio.file.*;
import java.util.jar.JarFile;
import org.objectweb.asm.*;
public class NativeTellFixture {
 public static void main(String[] args)throws Exception {
  byte[] bytes;
  try(JarFile jar=new JarFile(args[0])){bytes=jar.getInputStream(jar.getJarEntry("net/minecraft/server/commands/MsgCommand.class")).readAllBytes();}
  ClassReader reader=new ClassReader(bytes);ClassWriter writer=new ClassWriter(ClassWriter.COMPUTE_MAXS);
  writer.visit(Opcodes.V25,Opcodes.ACC_PUBLIC|Opcodes.ACC_SUPER,"net/minecraft/server/commands/MsgCommand",null,"java/lang/Object",null);
  final String bridge="verify/chat/TellRoutingBridge";
  final String source="Lnet/minecraft/commands/CommandSourceStack;";
  final String raw="Lnet/minecraft/network/chat/PlayerChatMessage;";
  final String outgoing="Lnet/minecraft/network/chat/OutgoingChatMessage;";
  final String bound="Lnet/minecraft/network/chat/ChatType$Bound;";
  boolean[] found={false};
  reader.accept(new ClassVisitor(Opcodes.ASM9) {
   @Override public MethodVisitor visitMethod(int access,String name,String descriptor,String signature,String[] exceptions) {
    if(!name.equals("sendMessage"))return null;
    found[0]=true;
    return new MethodVisitor(Opcodes.ASM9,writer.visitMethod(access,name,descriptor,signature,exceptions)) {
     @Override public void visitCode() {
      super.visitCode();super.visitVarInsn(Opcodes.ALOAD,2);
      super.visitMethodInsn(Opcodes.INVOKESTATIC,bridge,"preserve","("+raw+")"+raw,false);
      super.visitVarInsn(Opcodes.ASTORE,2);
     }
     @Override public void visitMethodInsn(int opcode,String owner,String method,String desc,boolean iface) {
      if(owner.equals("net/minecraft/network/chat/ChatType")&&method.equals("bind")) {
       super.visitMethodInsn(Opcodes.INVOKESTATIC,bridge,"bind",desc,false);
      } else if(owner.equals("net/minecraft/commands/CommandSourceStack")&&method.equals("sendChatMessage")) {
       super.visitVarInsn(Opcodes.ALOAD,0);super.visitVarInsn(Opcodes.ALOAD,1);super.visitVarInsn(Opcodes.ALOAD,2);
       super.visitMethodInsn(Opcodes.INVOKESTATIC,bridge,"senderFeedback","("+source+outgoing+"Z"+bound+source+"Ljava/util/Collection;"+raw+")V",false);
      } else super.visitMethodInsn(opcode,owner,method,desc,iface);
     }
    };
   }
  },0);
  if(!found[0])throw new AssertionError("找不到原版私信投递方法");
  writer.visitEnd();Files.write(Path.of(args[1]),writer.toByteArray());
 }
}
"""

CHECKS = r"""
import java.util.*;
import java.lang.reflect.*;
import net.minecraft.network.chat.*;
import net.minecraft.client.multiplayer.chat.*;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
import top.csituka.youzaiworldcore.chat.*;
import top.csituka.youzaiworldcore.client.chat.ChatRepeatTracker;
import top.csituka.youzaiworldcore.mixin.client.ChatComponentMixin;
import top.csituka.youzaiworldcore.mixin.chat.*;
import top.csituka.youzaiworldcore.skill.PlayerLevelStorage;
import top.csituka.youzaiworldcore.title.*;
public class ChatMessageChecks {
 static int checks;
 static void check(boolean v,String s){checks++;if(!v)throw new AssertionError(s);}
 static Object call(Class<?> type,Object instance,String name,Object... args)throws Exception {
  for(Method m:type.getDeclaredMethods())if(m.getName().equals(name)){m.setAccessible(true);return m.invoke(instance,args);}
  throw new AssertionError(name);
 }
 static Object field(Class<?> type,Object obj,String name)throws Exception {Field f=type.getDeclaredField(name);f.setAccessible(true);return f.get(obj);}
 static void set(Class<?> type,Object obj,String name,Object value)throws Exception {Field f=type.getDeclaredField(name);f.setAccessible(true);f.set(obj,value);}
 static GuiMessage message(int n,Component c,GuiMessageSource source){return new GuiMessage(n,c,new MessageSignature("sig-"+n),source,new GuiMessageTag("secure"));}
 static Component line(Component header,Component body){return Component.translatable("%s >> %s",header,body);}
 static class Display {
  final ChatComponentMixin mixin=new ChatComponentMixin(){};
  final List<GuiMessage> history=new ArrayList<>();final List<GuiMessage.Line> lines=new ArrayList<>();
  Display()throws Exception{set(ChatComponentMixin.class,mixin,"allMessages",history);set(ChatComponentMixin.class,mixin,"trimmedMessages",lines);}
  void display(GuiMessage raw)throws Exception {
   GuiMessage shown=(GuiMessage)call(ChatComponentMixin.class,mixin,"youzaiworldcore$collapseRepeatedMessage",raw);
   String text=shown.content().getString();int size=22;
   for(int p=0;p<text.length();p+=size)lines.addFirst(new GuiMessage.Line(shown,text.substring(p,Math.min(text.length(),p+size)),p+size>=text.length()));
   while(lines.size()>100)lines.removeLast();
  }
  void add(GuiMessage raw)throws Exception {
   display(raw);history.addFirst(raw);while(history.size()>100)history.removeLast();
   call(ChatComponentMixin.class,mixin,"youzaiworldcore$retainRepeatHistory",raw,null);
  }
  void replay()throws Exception {
   lines.clear();call(ChatComponentMixin.class,mixin,"youzaiworldcore$beginRepeatReplay",(Object)null);
   for(int n=history.size()-1;n>=0;n--)display(history.get(n));
   call(ChatComponentMixin.class,mixin,"youzaiworldcore$endRepeatReplay",(Object)null);
  }
  GuiMessage latest(){return lines.getFirst().parent();}
  int groups(){return (int)lines.stream().map(GuiMessage.Line::parent).distinct().count();}
 }
 public static void main(String[] args)throws Exception {
  var server=new net.minecraft.server.MinecraftServer();var player=new ServerPlayer(UUID.randomUUID(),"zxabinbina",server);
  var source=new CommandSourceStack(server,player,"zxabinbina");var console=new CommandSourceStack(server,null,"Server");
  var sheep=new CommandSourceStack(server,null,"Sheep");var body=Component.literal("Hello World!");
  PlayerLevelStorage.levels.put(player.getUUID(),new PlayerLevelStorage.Data(16));
  TitleManager.titles.put(player.getUUID(),new TitleDefinition("admin","管理员","管理员",0x55ffff,false,false,1).asComponent());
  var header=ChatFormatHelper.formatChatHeader(player);
  check(line(header,body).getString().equals("[聊天]|Lv.16·{管理员}zxabinbina >> Hello World!"),"普通聊天格式");
  check(ChatMessageStyle.repeated(line(header,body),3).getString().equals("[聊天]|Lv.16·{管理员}zxabinbina >> Hello World! [!3]"),"普通聊天计数");
  check(line(ChatFormatHelper.formatPrivateHeader(source),body).getString().equals("[私信]|Lv.16·{管理员}zxabinbina >> Hello World!"),"私信发送者身份");
  check(ChatMessageStyle.repeated(line(ChatFormatHelper.formatPrivateHeader(source),body),3).getString().equals("[私信]|Lv.16·{管理员}zxabinbina >> Hello World! [!3]"),"私信计数格式");
  check(ChatMessageStyle.game("Server",body).getString().equals("[游戏]|Server >> Hello World!"),"游戏消息格式");
  check(ChatMessageStyle.repeated(ChatMessageStyle.game("Server",body),3).getString().equals("[游戏]|Server >> Hello World! [!3]"),"游戏计数格式");
  check(ChatMessageStyle.mod("YouzaiWorldCore",body).getString().equals("[模组]|YouzaiWorldCore >> Hello World!"),"模组消息格式");
  check(ChatMessageStyle.repeated(ChatMessageStyle.mod("YouzaiWorldCore",body),3).getString().equals("[模组]|YouzaiWorldCore >> Hello World! [!3]"),"模组计数格式");
  check(ChatMessageStyle.repeated(body,1)==body&&ChatMessageStyle.repeated(body,0)==body,"单条消息不添加计数");
  TitleManager.titles.remove(player.getUUID());
  check(line(ChatFormatHelper.formatChatHeader(player),body).getString().equals("[聊天]|Lv.16·zxabinbina >> Hello World!"),"未佩戴称号");
  PlayerLevelStorage.levels.remove(player.getUUID());
  check(ChatFormatHelper.formatChatHeader(player).getString().equals("[聊天]|Lv.1·zxabinbina"),"未创建等级记录的默认等级");
  check(ChatFormatHelper.formatPrivateHeader(console).getString().equals("[私信]|Server"),"非玩家私信不伪造等级和称号");
  String original="100% <red> " + '$' + "{title} 你好\n🙂";
  check(ChatFormatHelper.formatMessageContent(player,original).getString().equals(original),"正文不解析标签和占位符");
  top.csituka.youzaiworldcore.config.GlobalSettings.SECTION.set("chat_format","OLD");
  top.csituka.youzaiworldcore.config.GlobalSettings.SECTION.set("private_message_sent_format","OLD");
  top.csituka.youzaiworldcore.config.ChatFormatSettings.load();
  top.csituka.youzaiworldcore.config.ChatFormatSettings.setEnabled(false);
  check(ChatFormatHelper.formatChatHeader(player).getString().equals("[聊天]|Lv.1·zxabinbina"),"旧模板和事件开关不覆盖固定排版");
  var styled=ChatMessageStyle.game("Sheep",Component.literal("正文").withStyle(net.minecraft.ChatFormatting.RED));
  check(ChatMessageStyle.isFormatted(styled),"结构化游戏消息");
  check(ChatMessageStyle.system(styled,null)==styled,"游戏消息不重复加前缀");
  check(ChatMessageStyle.system(Component.literal("[模组]|pretend"),null).getString().equals("[游戏]|Server >> [模组]|pretend"),"正文前缀不作为来源证据");
  check(top.csituka.youzaiworldcore.testing.CoreEmitter.send(body,null).getString().equals("[模组]|YouzaiWorldCore >> Hello World!"),"Core 调用来源");
  check(top.csituka.youzaiworldcore.testing.CoreEmitter.vanilla(body,sheep).getString().equals("[游戏]|Sheep >> Hello World!"),"原版执行者不被外层 Core 调度器覆盖");
  net.fabricmc.loader.api.FabricLoader.mods.add(new net.fabricmc.loader.api.ModContainer("example","ExampleMod",Set.of("example/mod/ForeignEmitter.class","example/mod/mixin/Injector.class")));
  check(example.mod.ForeignEmitter.send(body).getString().equals("[模组]|ExampleMod >> Hello World!"),"其他 Fabric 模组名称");
  int lookups=net.fabricmc.loader.api.ModContainer.lookups;example.mod.ForeignEmitter.send(body);
  check(net.fabricmc.loader.api.ModContainer.lookups==lookups,"模组类来源缓存");
  check(net.minecraft.server.commands.MergedEmitter.class.getDeclaredMethod("handler$foreign$notice",Component.class).getAnnotation(org.spongepowered.asm.mixin.transformer.meta.MixinMerged.class)!=null,"测试字节码具有真实 Mixin 来源标记");
  var mergedMessage=net.minecraft.server.commands.MergedEmitter.handler$foreign$notice(body).getString();
  check(mergedMessage.equals("[模组]|ExampleMod >> Hello World!"),"已合并 Mixin 的模组来源："+mergedMessage);
  var packetMixin=new SystemChatPacketMixin(){};
  var actionbar=new net.minecraft.network.protocol.game.ClientboundSystemChatPacket(body,true);
  check(call(SystemChatPacketMixin.class,packetMixin,"youzaiworldcore$formatSystemPacket",actionbar)==actionbar,"动作栏保持独立");
  var gamePacket=new net.minecraft.network.protocol.game.ClientboundSystemChatPacket(styled,false);
  check(call(SystemChatPacketMixin.class,packetMixin,"youzaiworldcore$formatSystemPacket",gamePacket)==gamePacket,"已格式化数据包不重复处理");
  var normalized=(net.minecraft.network.protocol.game.ClientboundSystemChatPacket)call(SystemChatPacketMixin.class,packetMixin,"youzaiworldcore$formatSystemPacket",new net.minecraft.network.protocol.game.ClientboundSystemChatPacket(body,false));
  check(normalized.content().getString().equals("[游戏]|Server >> Hello World!"),"未标记的服务器消息");
  var feedback=(Component)call(CommandSourceStackChatMixin.class,sheep,"youzaiworldcore$formatCommandOutput",body);
  check(feedback.getString().equals("[游戏]|Sheep >> Hello World!"),"命令反馈执行者");
  var admin=(Component)call(CommandSourceStackChatMixin.class,sheep,"youzaiworldcore$replaceAdminOutput","chat.type.admin",new Object[]{Component.literal("ignored"),body});
  check(admin.getString().equals("[游戏]|Sheep >> Hello World!"),"管理员广播使用同一排版");
  var tellraw=(Component)call(TellRawCommandMixin.class,null,"youzaiworldcore$formatTellRawMessage",new com.mojang.brigadier.context.CommandContext<>(sheep,body),"message",new net.minecraft.world.entity.Entity());
  check(tellraw.getString().equals("[游戏]|Sheep >> Hello World!"),"tellraw 执行者");
  var playerList=new PlayerListMixin(){};
  var raw=new PlayerChatMessage(original,new MessageSignature("raw"),PlayerChatMessage.Filter.NONE).withUnsignedContent(Component.literal("CHANGED"));
  var kept=(PlayerChatMessage)call(MsgCommandMixin.class,null,"youzaiworldcore$preservePrivateText",raw);
  check(kept.decoratedContent().getString().equals(original)&&kept.signature()==raw.signature(),"私信原文与签名");
  var gameKept=(PlayerChatMessage)call(PlayerListMixin.class,playerList,"youzaiworldcore$preserveGameChatText",raw);
  check(gameKept.decoratedContent().getString().equals(original)&&gameKept.signature()==raw.signature(),"say 原文与签名");
  var bound=ChatType.bind(ChatType.SAY_COMMAND,sheep);
  var gameBound=(ChatType.Bound)call(PlayerListMixin.class,playerList,"youzaiworldcore$gameChatHeader",bound,raw,sheep,bound);
  check(gameBound.decorate(body).getString().equals("[游戏]|Sheep >> Hello World!"),"say 来源前缀");
  var privateBound=(ChatType.Bound)call(MsgCommandMixin.class,null,"youzaiworldcore$privateHeader",ChatType.CHAT,source);
  check(privateBound.decorate(body).getString().equals("[私信]|Lv.1·zxabinbina >> Hello World!"),"私信原版绑定入口");
  var partial=new PlayerChatMessage("secret",new MessageSignature("partial"),PlayerChatMessage.Filter.PARTIAL);
  var receiver=new ServerPlayer(UUID.randomUUID(),"receiver",server);var parameters=ChatType.bind(ChatFormatHelper.MESSAGE_TYPE_ID,server,header);
  new StyledChatMessage(partial,header,parameters).sendToPlayer(receiver,true,parameters);
  check(receiver.connection.displayed.getString().equals("[聊天]|Lv.16·{管理员}zxabinbina >> ###"),"部分过滤不会从前缀泄露原文");
  check(receiver.connection.signature==partial.signature(),"过滤后仍使用原签名");
  int sends=receiver.connection.sends;
  new StyledChatMessage(new PlayerChatMessage("secret",null,PlayerChatMessage.Filter.FULL),header,parameters).sendToPlayer(receiver,true,parameters);
  check(receiver.connection.sends==sends,"完全过滤不投递");
  var self=new ServerPlayer(UUID.randomUUID(),"self",server);
  var selfSource=new CommandSourceStack(server,self,"self");
  verify.chat.TellRoutingBridge.applyFix=false;
  var duplicated=new Display();
  for(int n=1;n<=3;n++) {
   verify.chat.TellRoutingBridge.send(selfSource,List.of(self),new PlayerChatMessage("重复私信",new MessageSignature("before-"+n),PlayerChatMessage.Filter.NONE));
   check(self.connection.sends==n*2,"原版自发自收每次投递两条");
  }
  for(int n=0;n<self.connection.messages.size();n++)duplicated.add(message(n,self.connection.messages.get(n),GuiMessageSource.PLAYER));
  check(duplicated.latest().content().getString().endsWith("[!6]"),"复现修复前私信计数每次加二");
  self.connection.clear();
  verify.chat.TellRoutingBridge.applyFix=true;
  var corrected=new Display();
  for(int n=1;n<=3;n++) {
   var signature=new MessageSignature("after-"+n);
   verify.chat.TellRoutingBridge.send(selfSource,List.of(self),new PlayerChatMessage("重复私信",signature,PlayerChatMessage.Filter.NONE));
   check(self.connection.sends==n,"自发自收只投递一次");
   check(self.connection.signature==signature,"自发自收签名保持原对象");
   corrected.add(message(n,self.connection.displayed,GuiMessageSource.PLAYER));
   check(n==1?!corrected.latest().content().getString().contains("[!"):corrected.latest().content().getString().endsWith("[!"+n+"]"),"自发自收计数逐次加一");
  }
  self.connection.clear();
  verify.chat.TellRoutingBridge.send(selfSource,List.of(self),new PlayerChatMessage("未签名",null,PlayerChatMessage.Filter.NONE));
  check(self.connection.sends==1,"未签名自发自收也只投递一次");
  self.connection.clear();
  var peer=new ServerPlayer(UUID.randomUUID(),"peer",server);
  verify.chat.TellRoutingBridge.send(selfSource,List.of(peer),new PlayerChatMessage("发给别人",null,PlayerChatMessage.Filter.NONE));
  check(self.connection.sends==1&&peer.connection.sends==1,"不同玩家仍分别收到回执和私信");
  self.connection.clear();peer.connection.clear();
  verify.chat.TellRoutingBridge.send(selfSource,List.of(peer,self),new PlayerChatMessage("包含自己的多目标",null,PlayerChatMessage.Filter.NONE));
  check(self.connection.sends==1&&peer.connection.sends==1,"多目标包含自己时仍每人一次");
  self.connection.clear();peer.connection.clear();
  peer.textFiltering=true;
  verify.chat.TellRoutingBridge.send(selfSource,List.of(peer),new PlayerChatMessage("要过滤",null,PlayerChatMessage.Filter.PARTIAL));
  check(self.connection.displayed.getString().endsWith("要过滤")&&peer.connection.displayed.getString().endsWith("###"),"私信收件人过滤不受回执修复影响");
  self.connection.clear();peer.connection.clear();
  self.textFiltering=true;
  verify.chat.TellRoutingBridge.send(selfSource,List.of(self),new PlayerChatMessage("自己的原文",null,PlayerChatMessage.Filter.FULL));
  check(self.connection.sends==1&&self.connection.displayed.getString().endsWith("自己的原文"),"自发自收仍遵循原版自身不过滤规则");
  verify.chat.TellRoutingBridge.send(console,List.of(peer),new PlayerChatMessage("控制台私信",null,PlayerChatMessage.Filter.NONE));
  check(console.feedback.size()==1&&peer.connection.sends==1,"控制台私信反馈和收件仍保留");
  var display=new Display();var content=line(header,body);
  var marker=new GuiMessage(1,Component.literal("消息已删除"),null,GuiMessageSource.SYSTEM_SERVER,new GuiMessageTag("system"));
  var markerResult=new org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<GuiMessage>("deleted",true,marker);
  call(ChatComponentMixin.class,null,"youzaiworldcore$formatDeletedMarker",message(1,content,GuiMessageSource.PLAYER),markerResult);
  check(markerResult.getReturnValue().content().getString().equals("[游戏]|Server >> 消息已删除")&&markerResult.getReturnValue().signature()==null,"删除标记排版与签名");
  display.add(message(1,content,GuiMessageSource.PLAYER));check(display.groups()==1&&!display.latest().content().getString().contains("[!"),"首条显示");
  display.add(message(2,content.copy(),GuiMessageSource.PLAYER));display.add(message(3,content.copy(),GuiMessageSource.PLAYER));
  check(display.groups()==1&&display.latest().content().getString().endsWith(" [!3]"),"三条合并为一条显示");
  check(display.history.size()==3&&display.history.stream().noneMatch(m->m.content().getString().contains("[!")),"每条原文保留");
  check(display.latest().signature().equals(new MessageSignature("sig-3")),"合并显示保留最新签名");
  display.replay();check(display.groups()==1&&display.latest().content().getString().endsWith(" [!3]"),"重排后计数正确");
  display.add(message(4,line(ChatMessageStyle.gameHeader("Server"),body),GuiMessageSource.SYSTEM_SERVER));
  display.add(message(5,content,GuiMessageSource.PLAYER));check(display.groups()==3&&!display.latest().content().getString().contains("[!"),"非连续消息不合并");
  display.add(message(6,line(ChatMessageStyle.playerHeader(false,"other",16,Component.empty()),body),GuiMessageSource.PLAYER));
  check(display.groups()==4,"不同玩家不合并");
  display.add(message(7,line(ChatMessageStyle.playerHeader(true,"other",16,Component.empty()),body),GuiMessageSource.PLAYER));
  check(display.groups()==5,"不同消息分类不合并");
  display.add(message(8,display.latest().content(),GuiMessageSource.SYSTEM_CLIENT));check(display.groups()==6,"不同传输来源不合并");
  display.add(message(9,display.latest().content().copy().withStyle(net.minecraft.ChatFormatting.RED),GuiMessageSource.SYSTEM_CLIENT));
  check(display.groups()==7,"不同样式不合并");
  var longRun=new Display();
  for(int n=1;n<=350;n++)longRun.add(message(n,content,GuiMessageSource.PLAYER));
  check(longRun.history.size()==100&&longRun.groups()==1&&longRun.latest().content().getString().endsWith("[!350]"),"历史裁剪后计数继续累计");
  longRun.replay();check(longRun.latest().content().getString().endsWith("[!350]"),"历史裁剪后重排不丢累计次数");
  check(((Map<?,?>)field(ChatRepeatTracker.class,field(ChatComponentMixin.class,longRun.mixin,"youzaiworldcore$repeats"),"ordinals")).size()<=100,"计数缓存跟随历史裁剪");
  GuiMessage removed=longRun.history.get(5);
  longRun.history.set(5,new GuiMessage(removed.addedTime(),Component.literal("已删除"),null,removed.source(),removed.tag()));
  longRun.replay();check(longRun.latest().content().getString().endsWith("[!5]"),"删除标记后重新分组");
  longRun.add(message(351,content,GuiMessageSource.PLAYER));check(longRun.latest().content().getString().endsWith("[!6]"),"删除后继续累计当前组");
  var sameTick=new Display();
  var untyped=ChatMessageStyle.game("Server",body);
  for(int n=0;n<3;n++)sameTick.add(new GuiMessage(1,untyped,null,GuiMessageSource.SYSTEM_SERVER,new GuiMessageTag("system")));
  sameTick.replay();check(sameTick.latest().content().getString().endsWith("[!3]"),"同一 tick 的等值消息按对象身份保留序号");
  var tracker=new ChatRepeatTracker();tracker.accept(message(1,content,GuiMessageSource.PLAYER));set(ChatRepeatTracker.class,tracker,"count",Integer.MAX_VALUE);
  check(tracker.accept(message(2,content,GuiMessageSource.PLAYER)).displayed().content().getString().endsWith("[!2147483647]"),"计数不溢出");
  call(ChatComponentMixin.class,display.mixin,"youzaiworldcore$clearRepeatState",false,null);display.history.clear();display.lines.clear();
  display.add(message(10,content,GuiMessageSource.PLAYER));check(!display.latest().content().getString().contains("[!"),"清空聊天后重新计数");
  System.out.println("聊天消息离线回归通过："+checks+" 项断言。");
 }
}
"""


def validate_resources():
    def unique(pairs):
        result = {}
        for key, value in pairs:
            assert key not in result, f"重复语言键：{key}"
            result[key] = value
        return result
    expected = {
        "chat.youzaiworldcore.player": "[聊天]|%s%s%s",
        "chat.youzaiworldcore.private": "[私信]|%s%s%s",
        "chat.youzaiworldcore.game_header": "[游戏]|%s",
        "chat.youzaiworldcore.game": "[游戏]|%s >> %s",
        "chat.youzaiworldcore.mod": "[模组]|%s >> %s",
    }
    locales = list((ROOT / "src/main/resources/assets/youzaiworldcore/lang").glob("*.json"))
    assert len(locales) == 10
    for file in locales:
        data = json.loads(file.read_text(), object_pairs_hook=unique)
        assert all(data.get(key) == value for key, value in expected.items()), file
    chat_type = json.loads((ROOT / "src/main/resources/data/youzaiworldcore/chat_type/chat_line.json").read_text())
    for key in ["chat", "narration"]:
        assert chat_type[key] == {"translation_key": "%s >> %s", "parameters": ["sender", "content"]}
    server = json.loads((ROOT / "src/main/resources/youzaiworldcore.server.mixins.json").read_text())
    for name in ["chat.SystemChatPacketMixin", "chat.CommandSourceStackChatMixin", "chat.TellRawCommandMixin"]:
        assert server["mixins"].count(name) == 1
    print("十种语言、聊天类型及新增 Mixin 注册检查通过。")


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
    mixins = [p for p in (Path.home() / ".gradle/caches/modules-2/files-2.1/net.fabricmc/sponge-mixin").glob("*/*/*.jar")
              if not p.name.endswith(("-sources.jar", "-javadoc.jar"))]
    assert mixins, "需要本地缓存的 Mixin 注解库"
    asm = [p for p in (Path.home() / ".gradle/caches/modules-2/files-2.1/org.ow2.asm/asm").glob("*/*/*.jar")
           if not p.name.endswith(("-sources.jar", "-javadoc.jar"))]
    assert asm, "需要本地缓存的 ASM 库来模拟 Mixin 来源标记"
    libraries = os.pathsep.join([str(mixins[0]), str(asm[0])])
    production = [
        "src/main/java/" + P + "/chat/" + name + ".java"
        for name in ["ChatMessageStyle", "ModMessageSource", "ChatFormatHelper", "StyledChatMessage", "ExtPlayerChatMessage"]
    ] + [
        "src/main/java/" + P + "/mixin/chat/" + name + ".java"
        for name in ["MsgCommandMixin", "PlayerListMixin", "SystemChatPacketMixin", "CommandSourceStackChatMixin", "TellRawCommandMixin"]
    ] + [
        "src/main/java/" + P + "/config/ChatFormatSettings.java",
        "src/main/java/" + P + "/title/TitleDefinition.java",
        "src/client/java/" + P + "/client/chat/ChatRepeatTracker.java",
        "src/client/java/" + P + "/mixin/client/ChatComponentMixin.java",
    ]
    validate_resources()
    with tempfile.TemporaryDirectory(prefix="yzwc-chat-check-") as directory:
        temporary = Path(directory)
        files = []
        for path, source in {**STUBS, "ChatMessageChecks.java": CHECKS}.items():
            file = temporary / path
            file.parent.mkdir(parents=True, exist_ok=True)
            file.write_text(source)
            files.append(str(file))
        result = subprocess.run(
            [str(binary / "javac"), "-J-XX:-UsePerfData", "--release", "25", "-proc:none", "-encoding", "UTF-8",
             "-cp", libraries, "-d", str(temporary), *files, *[str(ROOT / p) for p in production]],
            cwd=ROOT, capture_output=True, text=True,
        )
        if result.returncode:
            print(result.stderr)
            raise SystemExit(result.returncode)
        runtime_classpath = str(temporary) + os.pathsep + libraries
        # MixinMerged 禁止直接出现在 Java 源码里，和 Mixin 一样从字节码添加。
        subprocess.run([str(binary / "java"), "-XX:-UsePerfData", "-cp", runtime_classpath, "MarkMergedFixture",
                        str(temporary / "net/minecraft/server/commands/MergedEmitter.class")], cwd=ROOT, check=True)
        minecraft = ROOT / "minecraft_jar/26.3/26.3.jar"
        assert minecraft.is_file(), "需要 Minecraft 26.3 原版 jar 来验证实际私信投递循环"
        # 从原版 jar 提取真实循环，只把现有前缀及回执 Mixin 调用接入测试桥。
        subprocess.run([str(binary / "java"), "-XX:-UsePerfData", "-cp", runtime_classpath, "NativeTellFixture",
                        str(minecraft), str(temporary / "net/minecraft/server/commands/MsgCommand.class")], cwd=ROOT, check=True)
        subprocess.run([str(binary / "java"), "-XX:-UsePerfData", "-cp", runtime_classpath, "ChatMessageChecks"], cwd=ROOT, check=True)


if __name__ == "__main__":
    main()
