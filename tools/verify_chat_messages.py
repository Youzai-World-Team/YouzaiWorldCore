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
stub("net.minecraft.network.chat.TextColor", "public record TextColor(int getValue) {}")
stub("net.minecraft.network.chat.ClickEvent", "public interface ClickEvent { record RunCommand(String command) implements ClickEvent {} }")
stub("net.minecraft.network.chat.HoverEvent", "public interface HoverEvent { record ShowText(Component value) implements HoverEvent {} }")
stub("net.minecraft.network.chat.FontDescription", "public interface FontDescription { record Resource(net.minecraft.resources.Identifier id) implements FontDescription {} }")
stub("net.minecraft.network.chat.Style", """
public record Style(Integer color,Boolean bold,Boolean italic,ClickEvent click,HoverEvent hover,FontDescription font,String insertion) {
 public static final Style EMPTY=new Style(null,null,null,null,null,null,null);
 public TextColor getColor(){return color==null?null:new TextColor(color);}
 public boolean isBold(){return Boolean.TRUE.equals(bold);}public boolean isItalic(){return Boolean.TRUE.equals(italic);}
 public HoverEvent getHoverEvent(){return hover;}
 public Style withColor(int v){return new Style(v,bold,italic,click,hover,font,insertion);}
 public Style withBold(Boolean v){return new Style(color,v,italic,click,hover,font,insertion);}
 public Style withItalic(Boolean v){return new Style(color,bold,v,click,hover,font,insertion);}
 public Style withClickEvent(ClickEvent v){return new Style(color,bold,italic,v,hover,font,insertion);}
 public Style withHoverEvent(HoverEvent v){return new Style(color,bold,italic,click,v,font,insertion);}
 public Style withFont(FontDescription v){return new Style(color,bold,italic,click,hover,v,insertion);}
 public Style withInsertion(String v){return new Style(color,bold,italic,click,hover,font,v);}
 public Style applyTo(Style p){return new Style(color==null?p.color:color,bold==null?p.bold:bold,italic==null?p.italic:italic,
  click==null?p.click:click,hover==null?p.hover:hover,font==null?p.font:font,insertion==null?p.insertion:insertion);}
}
""")
stub("net.minecraft.network.chat.FormattedText", """
public interface FormattedText {
 interface StyledContentConsumer<T>{java.util.Optional<T> accept(Style style,String text);}
}
""")
stub("net.minecraft.network.chat.ComponentContents", """
public interface ComponentContents {
 String text();
 default <T> java.util.Optional<T> visit(FormattedText.StyledContentConsumer<T> c,Style s){return c.accept(s,text());}
}
""")
stub("net.minecraft.network.chat.contents.TranslatableContents", """
import java.util.*;
import net.minecraft.network.chat.*;
public final class TranslatableContents implements ComponentContents {
 private final String key,fallback; private final Object[] args;
 public TranslatableContents(String k,String f,Object[] a){key=k;fallback=f;args=a.clone();}
 public String getKey(){return key;} public String getFallback(){return fallback;} public Object[] getArgs(){return args;}
 public String text(){Object[] a=Arrays.stream(args).map(v->v instanceof Component c?c.getString():v).toArray();
  return String.format(java.util.Locale.ROOT,fallback==null?key:fallback,a);}
 public <T> Optional<T> visit(FormattedText.StyledContentConsumer<T> c,Style s){
  String format=fallback==null?key:fallback;int offset=0,index=0;
  for(int next;(next=format.indexOf("%s",offset))>=0&&index<args.length;offset=next+2){
   Optional<T> result=c.accept(s,format.substring(offset,next));if(result.isPresent())return result;
   Object value=args[index++];result=value instanceof Component a?a.visit(c,s):c.accept(s,String.valueOf(value));
   if(result.isPresent())return result;
  }
  return c.accept(s,format.substring(offset));
 }
 public boolean equals(Object o){return o instanceof TranslatableContents t&&key.equals(t.key)&&Objects.equals(fallback,t.fallback)&&Arrays.deepEquals(args,t.args);}
 public int hashCode(){return Objects.hash(key,fallback,Arrays.deepHashCode(args));}
}
""")
stub("net.minecraft.network.chat.Component", """
import java.util.*;
import net.minecraft.network.chat.contents.TranslatableContents;
public interface Component extends com.mojang.brigadier.Message {
 ComponentContents getContents(); Style getStyle(); List<Component> getSiblings(); MutableComponent copy();
 default String getString(){StringBuilder b=new StringBuilder(getContents().text());getSiblings().forEach(c->b.append(c.getString()));return b.toString();}
 default <T> Optional<T> visit(FormattedText.StyledContentConsumer<T> c,Style parent){
  Style style=getStyle().applyTo(parent);Optional<T> result=getContents().visit(c,style);if(result.isPresent())return result;
  for(Component sibling:getSiblings()){result=sibling.visit(c,style);if(result.isPresent())return result;}return Optional.empty();
 }
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
 public static MutableComponent create(ComponentContents c){return new MutableComponent(c);}
 public ComponentContents getContents(){return contents;} public Style getStyle(){return style;}
 public List<Component> getSiblings(){return siblings;}
 public MutableComponent append(Component c){siblings.add(c);return this;}
 public MutableComponent setStyle(Style s){style=s;return this;}
 public MutableComponent append(String s){return append(Component.literal(s));}
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
 public PlayerChatMessage filter(boolean b){var r=new PlayerChatMessage(text,content,signature,b?filter:Filter.NONE,b);r.args.putAll(args);return r;}
 public boolean isFullyFiltered(){return filter==Filter.FULL;}
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
public class ServerPlayer extends top.csituka.youzaiworldcore.mixin.chat.ServerPlayerChatMixin {
 private final UUID id;private final String name;private final ServerLevel level;
 public final Connection connection=new Connection();
 public boolean textFiltering;
 public ServerPlayer(UUID id,String n,net.minecraft.server.MinecraftServer s){this.id=id;name=n;level=new ServerLevel(s);}
 public UUID getUUID(){return id;} public String getScoreboardName(){return name;}
 public Component getName(){return Component.literal(name);} public Component getDisplayName(){return Component.literal("DISPLAY "+name);}
 public int leaveGames=1;
 public net.minecraft.world.level.GameType gameType=net.minecraft.world.level.GameType.SURVIVAL;
 public net.minecraft.world.level.GameType gameMode(){return gameType;}
 public ServerLevel level(){return level;} public Stats getStats(){return new Stats();}
 public void sendChatMessage(OutgoingChatMessage message,boolean filtered,ChatType.Bound parameters){message.sendToPlayer(this,filtered,parameters);}
 public class Stats { public int getValue(Object v){return leaveGames;} }
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
 public boolean isSilent(){return silent;}
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
 public static net.minecraft.network.chat.Component getResolvedComponent(com.mojang.brigadier.context.CommandContext<net.minecraft.commands.CommandSourceStack> c,String n,net.minecraft.world.entity.Entity e) throws com.mojang.brigadier.exceptions.CommandSyntaxException {return c.getArgument(n,net.minecraft.network.chat.Component.class);}
}
""")
stub("net.minecraft.server.commands.MsgCommand", "public class MsgCommand {}")
stub("net.minecraft.world.level.GameType", """
public enum GameType { SURVIVAL("生存"),CREATIVE("创造"),ADVENTURE("冒险"),SPECTATOR("旁观");
 private final String display;GameType(String display){this.display=display;}
 public net.minecraft.network.chat.Component getShortDisplayName(){return net.minecraft.network.chat.Component.literal(display);}
}
""")
stub("net.minecraft.server.PlayerAdvancements", "public class PlayerAdvancements {}")
stub("net.minecraft.advancements.AdvancementHolder", "public record AdvancementHolder(net.minecraft.network.chat.Component name) {}")
stub("net.minecraft.advancements.Advancement", "public class Advancement { public static net.minecraft.network.chat.Component name(AdvancementHolder holder){return holder.name();} }")
stub("net.minecraft.advancements.AdvancementType", """
public enum AdvancementType { TASK,GOAL,CHALLENGE;
 public net.minecraft.network.chat.MutableComponent createAnnouncement(AdvancementHolder h,net.minecraft.server.level.ServerPlayer p){return net.minecraft.network.chat.Component.literal("vanilla advancement "+name());}
}
""")
stub("net.minecraft.network.chat.ChatDecorator", "public interface ChatDecorator { Component decorate(net.minecraft.server.level.ServerPlayer p,Component c); }")
stub("net.minecraft.server.network.ServerGamePacketListenerImpl", "public class ServerGamePacketListenerImpl {}")
stub("net.minecraft.world.damagesource.CombatTracker", """
public record CombatTracker(net.minecraft.network.chat.Component message) { public net.minecraft.network.chat.Component getDeathMessage(){return message;} }
""")
stub("net.minecraft.server.commands.TellRawCommand", "public class TellRawCommand {}")
stub("net.minecraft.server.network.ServerCommonPacketListenerImpl", "public class ServerCommonPacketListenerImpl {}")
stub("net.minecraft.network.protocol.Packet", "public interface Packet<T> {}")
stub("net.minecraft.network.protocol.game.ClientboundSystemChatPacket", """
public record ClientboundSystemChatPacket(net.minecraft.network.chat.Component content,boolean overlay) implements net.minecraft.network.protocol.Packet<Object> {}
""")
stub("net.minecraft.client.gui.components.ChatComponent", """
public class ChatComponent {
 public int rescaleCalls;public Runnable onRescale=()->{};
 public void rescaleChat(){rescaleCalls++;onRescale.run();}
}
""")
stub("net.minecraft.client.gui.Hud", """
public class Hud {private final net.minecraft.client.gui.components.ChatComponent chat=new net.minecraft.client.gui.components.ChatComponent();
 public net.minecraft.client.gui.components.ChatComponent getChat(){return chat;} }
""")
stub("net.minecraft.client.gui.Gui", "public class Gui {public final Hud hud=new Hud();}")
stub("net.minecraft.client.Minecraft", "public class Minecraft {public final net.minecraft.client.gui.Gui gui=new net.minecraft.client.gui.Gui();}")
stub("net.minecraft.commands.CommandBuildContext", "public class CommandBuildContext {}")
stub("net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource", """
public interface FabricClientCommandSource {
 void sendFeedback(net.minecraft.network.chat.Component message);net.minecraft.client.Minecraft getClient();
}
""")
stub("net.fabricmc.fabric.api.client.command.v2.ClientCommands", """
public final class ClientCommands {
 public static com.mojang.brigadier.builder.LiteralArgumentBuilder<FabricClientCommandSource> literal(String name){
  return com.mojang.brigadier.builder.LiteralArgumentBuilder.literal(name);
 }
}
""")
stub("net.fabricmc.fabric.impl.command.client.ClientCommandInternals", "public class ClientCommandInternals {}")
stub("net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback", """
public interface ClientCommandRegistrationCallback {
 CallbackEvent EVENT=new CallbackEvent();
 void register(com.mojang.brigadier.CommandDispatcher<FabricClientCommandSource> dispatcher,net.minecraft.commands.CommandBuildContext context);
 class CallbackEvent {
  private final java.util.List<ClientCommandRegistrationCallback> callbacks=new java.util.ArrayList<>();
  public void register(ClientCommandRegistrationCallback callback){callbacks.add(callback);}
  public void fire(com.mojang.brigadier.CommandDispatcher<FabricClientCommandSource> dispatcher){callbacks.forEach(c->c.register(dispatcher,null));}
 }
}
""")
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
public final class DebugLogger { public static final int LEVEL_OFF=0,LEVEL_BASIC=1,LEVEL_DETAILED=2,LEVEL_DEBUG=3; public static final DebugLogger SERVER=new DebugLogger(),CLIENT=new DebugLogger(); private boolean developer; private int level; public void configure(boolean d,int l){developer=d;level=l;} public boolean isDevModeEnabled(){return developer;} public int getLogLevel(){return level;} public static boolean isEnabled(int l){return false;} public static boolean isEnabled(){return false;} public static DebugLogger bootstrap(){return CLIENT;} public static DebugLogger forFile(java.nio.file.Path p){return SERVER;} public ModuleLogger module(String m){return new ModuleLogger();} public static void notice(String m,String s,Object...a){} public static void warn(String m,String s,Object...a){} public static void error(String m,String s,Object...a){} public static void exception(String m,String s,Object...a){} public static void exceptionSummary(String m,String s,Object...a){} public static void info(String m,String s,Object...a){} public static void stateChange(String m,String s,Object...a){} public static void debug(String m,String s,Object...a){} public static void branch(String m,String s,Object...a){} public static void debugStateChange(String m,String s,Object...a){} public static void trace(String m,String s,Object...a){} public static void entering(String m,String s,Object...a){} public static void exiting(String m,String s,Object...a){} public static void traceBranch(String m,String s,Object...a){} public static void traceStateChange(String m,String s,Object...a){} public static final class ModuleLogger { public void notice(String s,Object...a){} public void info(String s,Object...a){} public void debug(String s,Object...a){} public void trace(String s,Object...a){} public void warn(String s,Object...a){} public void error(String s,Object...a){} public boolean isInfoEnabled(){return false;} public boolean isDebugEnabled(){return false;} public boolean isTraceEnabled(){return false;} } }
""")
stub("top.csituka.youzaiworldcore.skill.PlayerLevelStorage", """
public class PlayerLevelStorage {
 public static final java.util.Map<java.util.UUID,Data> levels=new java.util.HashMap<>();
 public static Data get(java.util.UUID id){return levels.get(id);}
 public record Data(int getLevel,int getCurrentLevelExp,int getExpForNextLevel) { public Data(int level){this(level,0,200);} }
}
""")
stub("top.csituka.youzaiworldcore.skill.AdventureLevelManager", "public class AdventureLevelManager {public static int expForNextLevel(int level){return 200;} }")
stub("top.csituka.youzaiworldcore.title.TitleManager", """
public class TitleManager {
 public static final java.util.Map<java.util.UUID,net.minecraft.network.chat.Component> titles=new java.util.HashMap<>();
 public static net.minecraft.network.chat.Component getEquippedComponent(net.minecraft.server.level.ServerPlayer p){return titles.getOrDefault(p.getUUID(),net.minecraft.network.chat.Component.empty());}
}
""")
stub("org.slf4j.Logger", "public interface Logger {default void error(String text,Object... args){}default void warn(String text,Object... args){}default void info(String text,Object... args){} }")
stub("org.slf4j.LoggerFactory", "public class LoggerFactory {public static Logger getLogger(String name){return new Logger(){};} }")
stub("top.csituka.youzaiworldcore.config.ModPaths", """
public final class ModPaths {
 public static java.nio.file.Path clientSettingsFile(){return java.nio.file.Path.of(System.getProperty("yzwc.chat.testDirectory"),"yzwc/client/global_settings.json");}
}
""")
stub("top.csituka.youzaiworldcore.config.GlobalSettings", """
public class GlobalSettings {
 public static final String CHAT_FORMAT_MODULE="chat_format_module";
 private static final JsonFileStore STORE=new JsonFileStore(java.nio.file.Path.of(System.getProperty("yzwc.chat.testDirectory"),"server_settings.json"));
 static {STORE.load();}
 public static final ConfigSection SECTION=STORE.section(CHAT_FORMAT_MODULE);
 public static ConfigSection section(String k){return STORE.section(k);}public static void save(){STORE.save();}
}
""")
for name in ["ClientExternalSettings", "YzHudSettings", "ClientUpdateCheckerConfig", "InventoryItemGroupsConfig"]:
    stub("top.csituka.youzaiworldcore.client.config." + name,
         "public class " + name + " {public static void writeDefaults(){} public static void load(){} }")
stub("top.csituka.youzaiworldcore.highlightitem.Configurator", "public class Configurator {public static void writeDefaults(){} }")
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
 public static void sent(CommandSourceStack source,Collection<ServerPlayer> targets,PlayerChatMessage message){
  if(applyFix)hook("youzaiworldcore$sendPrivateReceipt",source,targets,message,null);
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
     @Override public void visitInsn(int opcode) {
      if(opcode==Opcodes.RETURN){
       super.visitVarInsn(Opcodes.ALOAD,0);super.visitVarInsn(Opcodes.ALOAD,1);super.visitVarInsn(Opcodes.ALOAD,2);
       super.visitMethodInsn(Opcodes.INVOKESTATIC,bridge,"sent","("+source+"Ljava/util/Collection;"+raw+")V",false);
      }
      super.visitInsn(opcode);
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
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.client.multiplayer.chat.*;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
import top.csituka.youzaiworldcore.chat.*;
import top.csituka.youzaiworldcore.client.chat.*;
import top.csituka.youzaiworldcore.client.config.ChatStyleSettings;
import top.csituka.youzaiworldcore.client.config.ClientGlobalSettings;
import top.csituka.youzaiworldcore.command.ChatStyleClientCommand;
import top.csituka.youzaiworldcore.mixin.client.ChatComponentMixin;
import top.csituka.youzaiworldcore.mixin.client.ClientCommandSuggestionsMixin;
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
 record Glyph(String text,Style style) {}
 static List<Glyph> glyphs(Component message){
  List<Glyph> result=new ArrayList<>();message.visit((style,text)->{
   text.codePoints().forEach(cp->result.add(new Glyph(new String(Character.toChars(cp)),style)));return Optional.empty();
  },Style.EMPTY);return result;
 }
 static void color(Component message,String text,int expected,String label){
  String full=message.getString();int offset=full.indexOf(text);List<Glyph> chars=glyphs(message);
  int start=offset<0?0:full.codePointCount(0,offset),length=text.codePointCount(0,text.length());
  check(offset>=0&&java.util.stream.IntStream.range(start,start+length)
   .allMatch(i->chars.get(i).style().getColor()!=null&&chars.get(i).style().getColor().getValue()==expected),label);
 }
 static void gradient(Component message,String text,int startColor,int endColor,String label){
  String full=message.getString();int offset=full.indexOf(text);List<Glyph> chars=glyphs(message);
  int start=offset<0?0:full.codePointCount(0,offset),length=text.codePointCount(0,text.length());
  check(offset>=0&&chars.get(start).style().getColor().getValue()==startColor
   &&chars.get(start+length-1).style().getColor().getValue()==endColor,label);
 }
 static class ClientSource implements net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource {
  final net.minecraft.client.Minecraft client=new net.minecraft.client.Minecraft();final List<Component> feedback=new ArrayList<>();
  public net.minecraft.client.Minecraft getClient(){return client;}public void sendFeedback(Component message){feedback.add(message);}
 }
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
  ClientGlobalSettings.load();ChatStyleSettings.load();
  check(ChatStyleSettings.getMode()==ChatStyleMode.GRADIENT,"默认启用渐变点缀");
  check(com.google.gson.JsonParser.parseString(java.nio.file.Files.readString(ClientGlobalSettings.file())).getAsJsonObject()
   .getAsJsonObject("chat_style_module").get("style").getAsString().equals("gradient"),"首次安装写出聊天配色默认分节");
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
  check(line(ChatFormatHelper.formatChatHeader(player),body).getString().equals("[聊天]|Lv.16·zxabinbina >> Hello World!"),"未佩戴称号时仍保留等级后的点号");
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
  var rawDispatcher=new com.mojang.brigadier.CommandDispatcher<CommandSourceStack>();
  var rawContext=new com.mojang.brigadier.context.CommandContextBuilder<>(rawDispatcher,sheep,rawDispatcher.getRoot(),0)
   .withArgument("message",new com.mojang.brigadier.context.ParsedArgument<>(0,1,body)).build("message");
  var tellraw=(Component)call(TellRawCommandMixin.class,null,"youzaiworldcore$formatTellRawMessage",rawContext,"message",new net.minecraft.world.entity.Entity());
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
   check(selfSource.feedback.size()==n&&selfSource.feedback.getLast().getString().equals("[私信]|Lv.1·self >> 已发送给 self"),"自发自收每次追加明确发送回执");
   corrected.add(new GuiMessage(n,selfSource.feedback.getLast(),null,GuiMessageSource.SYSTEM_SERVER,new GuiMessageTag("system")));
   check(!corrected.latest().content().getString().contains("[!"),"回执自身不追加正文计数");
  }
  corrected.replay();
  check(corrected.lines.stream().map(GuiMessage.Line::parent).filter(m->!ChatMessageStyle.isPrivateReceipt(m.content()))
   .anyMatch(m->m.content().getString().endsWith("[!3]")),"交错回执重排后私信正文仍累计三次");
  check(corrected.history.size()==6,"自发自收原始历史保留三条正文和三条回执");
  var receiptHistory=new Display();
  for(int n=1;n<=150;n++){
   receiptHistory.add(message(n,self.connection.displayed,GuiMessageSource.PLAYER));
   receiptHistory.add(new GuiMessage(n,selfSource.feedback.getLast(),null,GuiMessageSource.SYSTEM_SERVER,new GuiMessageTag("system")));
  }
  receiptHistory.replay();
  check(receiptHistory.history.size()==100&&receiptHistory.lines.stream().map(GuiMessage.Line::parent)
   .anyMatch(m->m.source()==GuiMessageSource.PLAYER&&m.content().getString().endsWith("[!150]")),"交错回执的历史裁剪和重排保留累计次数");
  receiptHistory.add(message(151,self.connection.displayed,GuiMessageSource.PLAYER));
  check(receiptHistory.latest().content().getString().endsWith("[!151]"),"交错回执历史重排后继续累计正文");
  Component receipt=ChatMessageStyle.privateSent(ChatFormatHelper.formatPrivateHeader(selfSource),Component.literal("peer"));
  check(ChatMessageStyle.isPrivateReceipt(receipt)&&ChatMessageStyle.isFormatted(receipt),"发送回执通过结构识别并保留私信类别");
  check(!ChatMessageStyle.isPrivateReceipt(line(ChatFormatHelper.formatPrivateHeader(selfSource),Component.literal("已发送给 peer"))),"玩家输入相同回执文字仍作为真实正文计数");
  for(ChatStyleMode mode:ChatStyleMode.values()){
   Component shown=ChatMessageRenderer.render(receipt,mode);
   check(shown.getString().equals(receipt.getString()),mode+" 回执配色保留收件人和文案");
   color(shown,"已发送给 peer",0xF3EFEF,mode+" 回执沿用正文色板");
   if(mode==ChatStyleMode.GRADIENT)gradient(shown,"self",0xC03AFF,0xDF61FF,"回执发送者使用私信渐变");
  }
  self.connection.clear();
  verify.chat.TellRoutingBridge.send(selfSource,List.of(self),new PlayerChatMessage("未签名",null,PlayerChatMessage.Filter.NONE));
  check(self.connection.sends==1,"未签名自发自收也只投递一次");
  self.connection.clear();
  selfSource.feedback.clear();
  var peer=new ServerPlayer(UUID.randomUUID(),"peer",server);
  verify.chat.TellRoutingBridge.send(selfSource,List.of(peer),new PlayerChatMessage("发给别人",null,PlayerChatMessage.Filter.NONE));
  check(self.connection.sends==1&&peer.connection.sends==1,"不同玩家仍分别收到回执和私信");
  check(selfSource.feedback.size()==1&&selfSource.feedback.getLast().getString().endsWith("已发送给 peer"),"发给别人时发送者收到含目标名称的确认");
  check(!selfSource.feedback.getLast().getString().contains("发给别人"),"独立回执不重复私信正文");
  self.connection.clear();peer.connection.clear();
  selfSource.feedback.clear();
  verify.chat.TellRoutingBridge.send(selfSource,List.of(peer,self),new PlayerChatMessage("包含自己的多目标",null,PlayerChatMessage.Filter.NONE));
  check(self.connection.sends==1&&peer.connection.sends==1,"多目标包含自己时仍每人一次");
  check(selfSource.feedback.size()==1&&selfSource.feedback.getLast().getString().endsWith("已发送给 peer, self"),"多目标发送只追加一条列明目标的确认");
  self.connection.clear();peer.connection.clear();
  peer.textFiltering=true;
  verify.chat.TellRoutingBridge.send(selfSource,List.of(peer),new PlayerChatMessage("要过滤",null,PlayerChatMessage.Filter.PARTIAL));
  check(self.connection.displayed.getString().endsWith("要过滤")&&peer.connection.displayed.getString().endsWith("###"),"私信收件人过滤不受回执修复影响");
  self.connection.clear();peer.connection.clear();
  self.textFiltering=true;
  verify.chat.TellRoutingBridge.send(selfSource,List.of(self),new PlayerChatMessage("自己的原文",null,PlayerChatMessage.Filter.FULL));
  check(self.connection.sends==1&&self.connection.displayed.getString().endsWith("自己的原文"),"自发自收仍遵循原版自身不过滤规则");
  selfSource.feedback.clear();peer.connection.clear();self.connection.clear();
  verify.chat.TellRoutingBridge.send(selfSource,List.of(peer),new PlayerChatMessage("完全过滤",null,PlayerChatMessage.Filter.FULL));
  check(peer.connection.sends==0&&selfSource.feedback.size()==1
   &&selfSource.feedback.getFirst()==net.minecraft.server.players.PlayerList.CHAT_FILTERED_FULL,"完全过滤时只保留原版失败反馈，不报告发送成功");
  selfSource.feedback.clear();peer.connection.clear();self.connection.clear();
  verify.chat.TellRoutingBridge.send(selfSource,List.of(peer,self),new PlayerChatMessage("部分目标被过滤",null,PlayerChatMessage.Filter.FULL));
  check(peer.connection.sends==0&&self.connection.sends==1&&selfSource.feedback.size()==2
   &&selfSource.feedback.getLast().getString().endsWith("已发送给 self"),"回执只列出未被完全过滤的收件人");
  selfSource.feedback.clear();
  verify.chat.TellRoutingBridge.send(selfSource,List.of(),new PlayerChatMessage("空目标",null,PlayerChatMessage.Filter.NONE));
  check(selfSource.feedback.isEmpty(),"空目标不产生成功回执");
  selfSource.silent=true;self.connection.clear();peer.connection.clear();
  verify.chat.TellRoutingBridge.send(selfSource,List.of(peer),new PlayerChatMessage("静默",null,PlayerChatMessage.Filter.NONE));
  check(selfSource.feedback.isEmpty()&&self.connection.sends==0&&peer.connection.sends==1,"静默命令仍投递正文但不产生发送者反馈");
  selfSource.silent=false;
  peer.connection.clear();
  verify.chat.TellRoutingBridge.send(console,List.of(peer),new PlayerChatMessage("控制台私信",null,PlayerChatMessage.Filter.NONE));
  check(console.feedback.size()==2&&peer.connection.sends==1&&console.feedback.getLast().getString().endsWith("已发送给 peer"),"控制台保留原版回执并收到明确目标确认");
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
  verifyPlayerHover(server);
  verifyEvents(player);
  verifyColors(body,header,original);
  verifyStyleCommands(content);
  System.out.println("聊天消息离线回归通过："+checks+" 项断言。");
 }

 static void hover(Component message,String name,Component tooltip,String label){
  String text=message.getString();int offset=text.indexOf(name);check(offset>=0,label+" 找到玩家名");
  int start=text.codePointCount(0,offset),length=name.codePointCount(0,name.length());
  var chars=glyphs(message).subList(start,start+length);
  check(chars.stream().allMatch(g->g.style().getHoverEvent() instanceof HoverEvent.ShowText h&&h.value().equals(tooltip)),label+" 每个码点的资料悬停一致");
 }

 static void verifyPlayerHover(net.minecraft.server.MinecraftServer server)throws Exception {
  var player=new ServerPlayer(UUID.randomUUID(),"HoverPlayer🙂",server);
  var source=new CommandSourceStack(server,player,player.getScoreboardName());
  var body=Component.literal("原色正文").withStyle(net.minecraft.ChatFormatting.RED);
  PlayerLevelStorage.levels.put(player.getUUID(),new PlayerLevelStorage.Data(16,120,500));
  Component title=Component.literal("{管理员}").withStyle(style->style.withColor(0x55FFFF));
  TitleManager.titles.put(player.getUUID(),title);
  Component tooltip=ChatPlayerInfo.tooltip(player);
  check(tooltip.getString().equals("玩家：HoverPlayer🙂\n冒险等级：Lv.16\n升级经验：120 / 500\n佩戴称号：{管理员}\n游戏模式：生存"),"玩家悬停字段来自服务端资料");
  color(tooltip,"{管理员}",0x55FFFF,"悬停称号保留原色");
  Component chat=line(ChatFormatHelper.formatChatHeader(player),body);
  var messages=new ArrayList<Component>(List.of(chat,line(ChatFormatHelper.formatPrivateHeader(source),body),
   ChatMessageStyle.system(body,source),line(ChatMessageStyle.gameHeader(ChatPlayerInfo.sourceName(source)),body)));
  for(ChatEventType type:ChatEventType.values())messages.add(ChatMessageStyle.event(type,ChatPlayerInfo.name(player),16,title,body));
  for(ChatStyleMode mode:ChatStyleMode.values())for(Component original:messages){
   var shown=ChatMessageRenderer.render(original,mode);
   hover(shown,player.getScoreboardName(),tooltip,mode+" 玩家名前缀");
   hover(ChatMessageStyle.repeated(shown,3),player.getScoreboardName(),tooltip,mode+" 重复消息保留资料");
   color(shown,"原色正文",0xFF5555,"资料悬停不影响正文原色");
   var chars=glyphs(shown);check(chars.getFirst().style().getHoverEvent()==null,"资料只附在玩家名，不附在类型标签");
  }
  var gameMixin=new PlayerListMixin(){};
  var commandRaw=new PlayerChatMessage("原色正文",null,PlayerChatMessage.Filter.NONE);
  var originalBound=ChatType.bind(ChatType.MSG_COMMAND_INCOMING,source);
  var gameBound=(ChatType.Bound)call(PlayerListMixin.class,gameMixin,"youzaiworldcore$gameChatHeader",originalBound,commandRaw,source,originalBound);
  hover(gameBound.name(),player.getScoreboardName(),tooltip,"玩家 /say 来源使用资料悬停");
  hover((Component)call(CommandSourceStackChatMixin.class,source,"youzaiworldcore$formatCommandOutput",body),
   player.getScoreboardName(),tooltip,"玩家命令反馈使用资料悬停");
  var peer=new ServerPlayer(UUID.randomUUID(),"Peer",server);
  call(MsgCommandMixin.class,null,"youzaiworldcore$sendPrivateReceipt",source,List.of(player,peer,peer),commandRaw,null);
  Component receipt=source.feedback.getLast();
  check(receipt.getString().endsWith("已发送给 HoverPlayer🙂, Peer"),"私信回执保留收件人顺序并去重");
  Component recipients=(Component)((TranslatableContents)((Component)((TranslatableContents)receipt.getContents()).getArgs()[1]).getContents()).getArgs()[0];
  hover(recipients,player.getScoreboardName(),tooltip,"私信回执自身名称悬停");
  hover(recipients,"Peer",ChatPlayerInfo.tooltip(peer),"私信回执其他收件人悬停");
  var before=top.csituka.youzaiworldcore.client.config.ChatStyleSettings.getMode();
  var history=new Display();history.add(message(1,chat,net.minecraft.client.multiplayer.chat.GuiMessageSource.PLAYER));
  history.add(message(2,chat,net.minecraft.client.multiplayer.chat.GuiMessageSource.PLAYER));
  for(ChatStyleMode mode:ChatStyleMode.values()){
   top.csituka.youzaiworldcore.client.config.ChatStyleSettings.setMode(mode);history.replay();
   hover(history.latest().content(),player.getScoreboardName(),tooltip,"历史重绘保留玩家资料");
   check(history.latest().content().getString().endsWith("[!2]"),"资料悬停保留重复计数");
  }
  top.csituka.youzaiworldcore.client.config.ChatStyleSettings.setMode(before);
  PlayerLevelStorage.levels.remove(player.getUUID());TitleManager.titles.remove(player.getUUID());
  player.gameType=net.minecraft.world.level.GameType.CREATIVE;
  check(ChatPlayerInfo.tooltip(player).getString().contains("冒险等级：Lv.1\n升级经验：0 / 200\n佩戴称号：未佩戴\n游戏模式：创造"),"无等级或称号时使用默认资料，并显示新游戏模式");
  hover(ChatMessageRenderer.render(chat),player.getScoreboardName(),tooltip,"旧消息保留发送时资料快照");
  var console=new CommandSourceStack(server,null,"Server");
  check(ChatPlayerInfo.sourceName(console).getStyle().getHoverEvent()==null,"非玩家来源不显示玩家资料");
 }

 static void verifyEvents(ServerPlayer player)throws Exception {
  top.csituka.youzaiworldcore.config.ChatFormatSettings.writeDefaults();
  Component death=Component.translatableWithFallback("death.attack.arrow","%s 被 %s 射杀",
   Component.literal("zxabinbina"),Component.literal("骷髅").withStyle(net.minecraft.ChatFormatting.RED))
   .withStyle(style->style.withHoverEvent(new HoverEvent.ShowText(Component.literal("死因详情"))));
  Component[] events={ChatFormatHelper.formatJoin(player),ChatFormatHelper.formatJoinFirstTime(player),
   ChatFormatHelper.formatJoinRenamed(player,"OldName"),ChatFormatHelper.formatLeft(player),ChatFormatHelper.formatDeath(player,death)};
  String[] expected={"[加入]|Lv.1·zxabinbina >> 加入了服务器","[加入]|Lv.1·zxabinbina >> 首次加入服务器，欢迎！",
   "[加入]|Lv.1·zxabinbina >> 加入了服务器（原名：OldName）","[退出]|Lv.1·zxabinbina >> 退出了服务器",
   "[死亡]|Lv.1·zxabinbina >> zxabinbina 被 骷髅 射杀"};
  for(int i=0;i<events.length;i++){
   check(events[i].getString().equals(expected[i]),"事件默认排版 "+i);
   check(ChatMessageStyle.isFormatted(events[i])&&ChatMessageStyle.system(events[i],null)==events[i],"事件不叠加游戏或模组前缀");
  }
  var joins=new PlayerListMixin(){};set(PlayerListMixin.class,joins,"youzaiworldcore$tempPlayer",player);
  Component vanillaJoin=Component.translatableWithFallback("multiplayer.player.joined","%s joined",player.getDisplayName());
  check(call(PlayerListMixin.class,joins,"youzaiworldcore$replaceJoinMessage",vanillaJoin).equals(events[0]),"普通加入入口使用加入样式");
  player.leaveGames=0;
  check(call(PlayerListMixin.class,joins,"youzaiworldcore$replaceJoinMessage",vanillaJoin).equals(events[1]),"首次加入入口使用欢迎正文");
  player.leaveGames=1;
  check(call(PlayerListMixin.class,joins,"youzaiworldcore$replaceJoinMessage",
   Component.translatable("multiplayer.player.joined.renamed",player.getDisplayName(),"OldName")).equals(events[2]),"改名加入入口保留旧名");
  var leaves=new ServerGamePacketListenerImplMixin(){};
  set(ServerGamePacketListenerImplMixin.class,leaves,"player",player);
  check(call(ServerGamePacketListenerImplMixin.class,leaves,"youzaiworldcore$replaceLeaveMessage",vanillaJoin).equals(events[3]),"退出入口使用退出样式");
  var combat=new net.minecraft.world.damagesource.CombatTracker(death);
  check(call(ServerPlayerChatMixin.class,player,"youzaiworldcore$replaceDeathMessage",combat).equals(events[4]),"死亡入口保留完整原版死因");
  var advancement=new PlayerAdvancementsChatMixin(){};
  Component award=Component.literal("[钻石！]").withStyle(style->style.withColor(0x55FF55)
   .withHoverEvent(new HoverEvent.ShowText(Component.literal("获得钻石"))));
  var holder=new net.minecraft.advancements.AdvancementHolder(award);
  String[] verbs={"达成进度 ","完成目标 ","完成挑战 "};
  var types=new net.minecraft.advancements.AdvancementType[]{net.minecraft.advancements.AdvancementType.TASK,
   net.minecraft.advancements.AdvancementType.GOAL,net.minecraft.advancements.AdvancementType.CHALLENGE};
  for(int i=0;i<types.length;i++){
   Component event=(Component)call(PlayerAdvancementsChatMixin.class,advancement,"youzaiworldcore$formatAdvancement",types[i],holder,player);
   check(event.getString().equals("[成就]|Lv.1·zxabinbina >> "+verbs[i]+"[钻石！]"),"成就种类正文 "+types[i]);
   for(ChatStyleMode mode:ChatStyleMode.values()){
    var shown=ChatMessageRenderer.render(event,mode);
    color(shown,"[钻石！]",0x55FF55,"成就名称保留原版颜色");
    check(glyphs(shown).stream().filter(g->g.text().equals("钻")).allMatch(g->g.style().hover().equals(award.getStyle().hover())),"成就名称保留悬停说明");
   }
  }
  for(ChatEventType type:ChatEventType.values())for(ChatStyleMode mode:ChatStyleMode.values()){
   Component original=ChatMessageStyle.event(type,"Player",16,Component.literal("{管理员}"),death);
   var shown=ChatMessageRenderer.render(original,mode);
   check(shown.getString().equals(type.label()+"|Lv.16·{管理员}Player >> "+death.getString()),"事件完整等级称号排版");
   if(mode==ChatStyleMode.PLAIN)color(shown,type.label(),type.plainColor(),"事件标签纯色");
   else gradient(shown,type.label(),type.gradientStart(),type.gradientEnd(),"事件标签渐变");
   color(shown,"Lv.16",0xF36B7B,"事件等级色");color(shown,"{管理员}",0xF47B8A,"事件称号色");
   color(shown,"骷髅",0xFF5555,"事件正文保留原色");
   if(mode==ChatStyleMode.PLAIN)color(shown,"Player",0xF7F7F7,"事件玩家名纯色");
   else gradient(shown,"Player",type.gradientStart(),type.gradientEnd(),"事件玩家名渐变");
   color(ChatMessageStyle.repeated(shown,3),"[!3]",0x2DD4BF,"事件重复计数青色");
  }
  top.csituka.youzaiworldcore.config.ChatFormatSettings.setJoinedGameFormat("Custom welcome");
  check(ChatFormatHelper.formatJoin(player).getString().equals("[加入]|Lv.1·zxabinbina >> Custom welcome"),"自定义加入模板仍作为正文解析");
  top.csituka.youzaiworldcore.config.ChatFormatSettings.setEnabled(false);
  check(call(PlayerListMixin.class,joins,"youzaiworldcore$replaceJoinMessage",vanillaJoin)==vanillaJoin,"关闭事件样式保留原版加入消息");
  check(call(ServerGamePacketListenerImplMixin.class,leaves,"youzaiworldcore$replaceLeaveMessage",vanillaJoin)==vanillaJoin,"关闭事件样式保留原版退出消息");
  check(call(ServerPlayerChatMixin.class,player,"youzaiworldcore$replaceDeathMessage",combat)==death,"关闭事件样式保留原版死亡消息");
  check(((Component)call(PlayerAdvancementsChatMixin.class,advancement,"youzaiworldcore$formatAdvancement",types[0],holder,player))
   .getString().equals("vanilla advancement TASK"),"关闭事件样式保留原版成就消息");
  top.csituka.youzaiworldcore.config.ChatFormatSettings.writeDefaults();
 }

 static void verifyColors(Component body,Component header,String original)throws Exception {
  Component[] messages={line(header,body),line(ChatMessageStyle.playerHeader(true,"zxabinbina",16,Component.literal("{管理员}")),body),
   ChatMessageStyle.game("Server",body),ChatMessageStyle.mod("YouzaiWorldCore",body)};
  String[] labels={"[聊天]","[私信]","[游戏]","[模组]"};
  String[] names={"zxabinbina","zxabinbina","Server","YouzaiWorldCore"};
  int[] plain={0xE77280,0xF36B7B,0xF47B8A,0x007AFF};
  int[] start={0xFF3364,0xC03AFF,0xFF8A00,0x007AFF},end={0xD0529E,0xDF61FF,0xE79D0E,0xC03AFF};
  int[] namePlain={0xF7F7F7,0xF7F7F7,0xFF8A00,0x5B8CFF},nameStart={0xFF3364,0xC03AFF,0xFF8A00,0x5B8CFF};
  for(ChatStyleMode mode:ChatStyleMode.values())for(int i=0;i<messages.length;i++){
   Component rendered=ChatMessageRenderer.render(messages[i],mode);
   check(rendered.getString().equals(messages[i].getString()),mode+" "+labels[i]+" 着色保留原文");
   if(mode==ChatStyleMode.PLAIN)color(rendered,labels[i],plain[i],labels[i]+" 纯色标签");
   else gradient(rendered,labels[i],start[i],end[i],labels[i]+" 渐变标签两端");
   if(mode==ChatStyleMode.GRADIENT)gradient(rendered,names[i],nameStart[i],end[i],labels[i]+" 名称渐变两端");
   else color(rendered,names[i],namePlain[i],labels[i]+" 来源或玩家名纯色");
   color(rendered,"|",0x555555,labels[i]+" 竖线颜色");color(rendered," >> ",0x555555,labels[i]+" 箭头与空格颜色");
   color(rendered,"Hello World!",0xF3EFEF,labels[i]+" 正文颜色");
   Component repeated=ChatMessageStyle.repeated(rendered,3);color(repeated," [!3]",0x2DD4BF,labels[i]+" 计数颜色与空格");
   check(ChatMessageStyle.repeated(rendered,1)==rendered&&!rendered.getString().endsWith(" "),labels[i]+" 单条无计数或尾部空格");
   if(i<2){color(rendered,"Lv.16",0xF36B7B,labels[i]+" 等级颜色");color(rendered,"·",0x555555,labels[i]+" 点号颜色");
    color(rendered,"{管理员}",0xF47B8A,labels[i]+" 称号颜色");}
  }
  for(ChatStyleMode mode:ChatStyleMode.values()){
   Component noTitle=ChatMessageRenderer.render(line(ChatMessageStyle.playerHeader(false,"player",16,Component.empty()),body),mode);
   check(noTitle.getString().equals("[聊天]|Lv.16·player >> Hello World!"),mode+" 不佩戴时保留点号，不显示空花括号");
   Component console=ChatMessageRenderer.render(line(ChatMessageStyle.playerHeader(true,"Server",0,Component.empty()),body),mode);
   check(console.getString().equals("[私信]|Server >> Hello World!"),mode+" 非玩家私信不伪造等级");
   Component game=ChatMessageRenderer.render(line(ChatMessageStyle.gameHeader("Sheep"),body),mode);
   if(mode==ChatStyleMode.GRADIENT)gradient(game,"Sheep",0xFF8A00,0xE79D0E,mode+" 原版命令绑定来源渐变");
   else color(game,"Sheep",0xFF8A00,mode+" 原版命令绑定来源橙色");
   color(game," >> ",0x555555,mode+" 原版命令绑定分隔符颜色");
   Component special=ChatMessageRenderer.render(line(header,Component.literal(original)),mode);
   check(special.getString().equals(header.getString()+" >> "+original),mode+" 正文标签与百分号不解析");
   color(special,original,0xF3EFEF,mode+" Unicode 多行正文颜色");
  }
  var emoji=ChatMessageRenderer.render(line(ChatMessageStyle.playerHeader(false,"A🙂B",16,Component.empty()),body),ChatStyleMode.GRADIENT);
  color(emoji,"A",0xFF3364,"Unicode 渐变起点");color(emoji,"🙂",0xE84381,"Unicode 按码点渐变，表情使用精确中间色");
  color(emoji,"B",0xD0529E,"Unicode 渐变终点");
  var single=ChatMessageRenderer.render(line(ChatMessageStyle.playerHeader(false,"🙂",16,Component.empty()),body),ChatStyleMode.GRADIENT);
  color(single,"🙂",0xFF3364,"单码点玩家名使用渐变起点");
  var nestedSingle=ChatMessageStyle.gradient(Component.empty().append(Component.literal("🙂").withStyle(net.minecraft.ChatFormatting.WHITE)),0xFF3364,0xD0529E);
  color(nestedSingle,"🙂",0xFF3364,"单码点子组件的旧白色不能覆盖渐变起点");
  for(boolean privateMessage:List.of(false,true)){
   Component rawHeader=ChatMessageStyle.playerHeader(privateMessage,"zxabinbina",16,Component.empty());
   gradient(rawHeader,"zxabinbina",privateMessage?0xC03AFF:0xFF3364,privateMessage?0xDF61FF:0xD0529E,"服务端发送前已有玩家名渐变");
   color(rawHeader,"Lv.16",0xF36B7B,"服务端等级基础配色");color(rawHeader,"·",0x555555,"服务端分隔符基础配色");
  }
  color(ChatMessageStyle.gameHeader("Server"),"Server",0xFF8A00,"服务端命令前缀来源使用橙色");
  color(ChatMessageStyle.game("Server",body),"Server",0xFF8A00,"服务端游戏来源使用橙色");
  color(ChatMessageStyle.mod("YouzaiWorldCore",body),"YouzaiWorldCore",0x5B8CFF,"服务端模组来源使用蓝色");
  Style rich=Style.EMPTY.withColor(0x112233).withBold(true).withItalic(true)
   .withClickEvent(new ClickEvent.RunCommand("/help")).withHoverEvent(new HoverEvent.ShowText(Component.literal("详情")))
   .withFont(new FontDescription.Resource(new net.minecraft.resources.Identifier("example:font"))).withInsertion("insert me");
  Component parameter=Component.literal("参数").withStyle(net.minecraft.ChatFormatting.RED);
  var translated=Component.translatableWithFallback("example.message","原文 %s",parameter).setStyle(rich)
   .append(Component.literal(" 后文").withStyle(net.minecraft.ChatFormatting.WHITE));
  for(ChatStyleMode mode:ChatStyleMode.values()){
   var rendered=ChatMessageRenderer.render(ChatMessageStyle.game("Sheep",translated),mode);
   Component shownBody=rendered.getSiblings().getLast();
   check(shownBody.getStyle().equals(rich),mode+" 保留正文原色、交互、字体和文字样式");
   check(shownBody.getContents() instanceof TranslatableContents t&&t.getKey().equals("example.message")
    &&t.getFallback().equals("原文 %s"),mode+" 保留翻译内容和回退文本");
   color(rendered,"原文",0x112233,mode+" 保留翻译组件原色");
   color(rendered,"参数",0xFF5555,mode+" 保留翻译参数原色");
   color(rendered," 后文",0xFFFFFF,mode+" 保留子组件显式白色");
   List<Glyph> chars=glyphs(rendered);int offset=rendered.getString().indexOf("原文");
   check(chars.subList(offset,chars.size()).stream().allMatch(g->g.style().withColor(0x112233).equals(rich)),mode+" 翻译参数继承交互与字体");
   Component title=new TitleDefinition("rich","称号","称号",0x55FFFF,true,true,1).asComponent();
   Component titled=ChatMessageRenderer.render(line(ChatMessageStyle.playerHeader(false,"player",16,title),body),mode);
   color(titled,"{称号}",0xF47B8A,mode+" 聊天称号按指定色板覆盖目录颜色");
   check(glyphs(titled).stream().filter(g->g.text().equals("称")||g.text().equals("号"))
    .allMatch(g->g.style().isBold()&&g.style().isItalic()),mode+" 保留称号加粗及斜体");
   Component wrapped=Component.empty().setStyle(rich).append(ChatMessageStyle.game("Server",body));
   Component shown=ChatMessageRenderer.render(wrapped,mode);
   check(shown.getString().equals(wrapped.getString())&&shown.getStyle().equals(rich),mode+" 保留失败反馈包装与非颜色样式");
   color(shown,"Hello World!",0xF3EFEF,mode+" 包装旧色不覆盖正文色");
   Component suffix=ChatMessageRenderer.render(ChatMessageStyle.game("Server",body).copy()
    .append(Component.literal(" tail").withStyle(net.minecraft.ChatFormatting.RED)),mode);
   color(suffix," tail",0xFF5555,mode+" 保留附加正文组件原色");
   color(ChatMessageRenderer.render(ChatMessageStyle.repeated(ChatMessageStyle.mod("Mod",translated),3),mode),
    " [!3]",0x2DD4BF,mode+" 重绘带计数的消息保留后缀颜色");
  }
  check(translated.getStyle().equals(rich)&&parameter.getStyle().getColor().getValue()==0xFF5555
   &&translated.getSiblings().getLast().getStyle().getColor().getValue()==0xFFFFFF,"着色不修改原始样式、参数或子组件");
  ComponentContents object=new ComponentContents(){public String text(){return "[item]";}};
  Component item=MutableComponent.create(object).setStyle(rich);
  Component shownItem=ChatMessageRenderer.render(ChatMessageStyle.game("Server",item),ChatStyleMode.GRADIENT).getSiblings().getLast();
  check(shownItem.getContents()==object&&shownItem.getStyle().equals(rich),"保留非文字内容组件和原色");
  var pretend=Component.literal("[聊天]|pretend");check(ChatMessageRenderer.render(pretend,ChatStyleMode.GRADIENT)==pretend,"正文前缀不被解释为配色来源");
  var malformed=Component.translatableWithFallback(ChatMessageStyle.GAME_MESSAGE,"unusual");
  check(ChatMessageRenderer.render(malformed,ChatStyleMode.GRADIENT)==malformed,"缺少结构参数的组件不导致显示崩溃");
  var distinct=new Display();
  Component red=ChatMessageStyle.game("Server",body.copy().withStyle(net.minecraft.ChatFormatting.RED));
  Component white=ChatMessageStyle.game("Server",body.copy().withStyle(net.minecraft.ChatFormatting.WHITE));
  check(!ChatMessageRenderer.render(red).equals(ChatMessageRenderer.render(white)),"原文颜色不同则显示颜色也不同");
  color(ChatMessageRenderer.render(red),"Hello World!",0xFF5555,"保留红色正文");
  color(ChatMessageRenderer.render(white),"Hello World!",0xFFFFFF,"保留显式白色正文");
  distinct.add(message(1,red,GuiMessageSource.SYSTEM_SERVER));distinct.add(message(2,white,GuiMessageSource.SYSTEM_SERVER));
  check(distinct.groups()==2,"按正文颜色区分重复组");
  var coloredHistory=new Display();
  for(int n=1;n<=3;n++)coloredHistory.add(message(n,red,GuiMessageSource.SYSTEM_SERVER));
  var before=ChatStyleSettings.getMode();
  for(ChatStyleMode mode:ChatStyleMode.values()){
   ChatStyleSettings.setMode(mode);coloredHistory.replay();
   color(coloredHistory.latest().content(),"Hello World!",0xFF5555,mode+" 历史重绘保留正文颜色");
   color(coloredHistory.latest().content()," [!3]",0x2DD4BF,mode+" 历史重绘保留累计次数和计数颜色");
  }
  ChatStyleSettings.setMode(before);
 }

 static void verifyStyleCommands(Component content)throws Exception {
  var source=new ClientSource();
  var dispatcher=new com.mojang.brigadier.CommandDispatcher<net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource>();
  dispatcher.register(net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal("yzwc")
   .then(net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal("existing").executes(c->7)));
  ChatStyleClientCommand.register();net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback.EVENT.fire(dispatcher);
  check(dispatcher.execute("yzwc existing",source)==7,"注册配色子命令保留已有命令");
  check(dispatcher.execute("yzwc chat_style",source)==1&&ChatStyleSettings.getMode()==ChatStyleMode.GRADIENT
   &&source.client.gui.hud.getChat().rescaleCalls==0,"省略名称只查询当前配色");
  var query=(TranslatableContents)source.feedback.getLast().getContents();
  check(query.getKey().equals("chat.youzaiworldcore.style.current")&&query.getArgs()[0].equals("gradient"),"查询反馈包含当前配色名称");
  var suggestions=dispatcher.getCompletionSuggestions(dispatcher.parse("yzwc chat_style ",source)).get().getList();
  check(suggestions.stream().map(com.mojang.brigadier.suggestion.Suggestion::getText).collect(java.util.stream.Collectors.toSet())
   .equals(Set.of("plain","gradient")),"配色名称自动补全");
  var partial=dispatcher.getCompletionSuggestions(dispatcher.parse("yzwc chat_style g",source)).get().getList();
  check(partial.size()==1&&partial.getFirst().getText().equals("gradient"),"名称前缀补全");
  var completions=new com.mojang.brigadier.CommandDispatcher<net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource>();
  completions.register(net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal("yzwc")
   .then(net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal("existing").executes(c->7)));
  call(ClientCommandSuggestionsMixin.class,null,"youzaiworldcore$restoreChatStyleSuggestions",completions,source,null);
  check(completions.getRoot().getChild("yzwc").getChild("chat_style")!=null&&completions.execute("yzwc existing",source)==7,"补全修复将配色子命令接到已有根下");
  int beforeQuery=source.feedback.size();var beforeMode=ChatStyleSettings.getMode();
  check(completions.execute("yzwc chat_style plain",source)==0&&ChatStyleSettings.getMode()==beforeMode&&source.feedback.size()==beforeQuery,"补全子树仅展示，不执行本机设置修改");
  var history=new Display();for(int n=1;n<=350;n++)history.add(message(n,content,GuiMessageSource.PLAYER));
  source.client.gui.hud.getChat().onRescale=()->{try{history.replay();}catch(Exception e){throw new RuntimeException(e);}};
  ClientGlobalSettings.section(ClientGlobalSettings.CORE_MODULE).set("keep",true);ClientGlobalSettings.save();
  List<GuiMessage> originals=List.copyOf(history.history);
  check(dispatcher.execute("yzwc chat_style plain",source)==1&&ChatStyleSettings.getMode()==ChatStyleMode.PLAIN,"命令切换到官网纯色");
  check(source.client.gui.hud.getChat().rescaleCalls==1&&history.groups()==1
   &&history.latest().content().getString().endsWith(" [!350]"),"切换重绘历史且保留裁剪前累计次数");
  color(history.latest().content(),"[聊天]",0xE77280,"旧聊天记录立即使用纯色");
  color(history.latest().content()," [!350]",0x2DD4BF,"旧聊天计数保持指定颜色");
  check(history.history.equals(originals)&&java.util.stream.IntStream.range(0,originals.size())
   .allMatch(i->history.history.get(i)==originals.get(i))&&history.latest().signature()==originals.getFirst().signature(),"切换保留原始历史对象与签名");
  var stored=com.google.gson.JsonParser.parseString(java.nio.file.Files.readString(ClientGlobalSettings.file())).getAsJsonObject();
  check(stored.getAsJsonObject("chat_style_module").get("style").getAsString().equals("plain")
   &&stored.getAsJsonObject("core_module").get("keep").getAsBoolean(),"切换持久化并保留其他模块");
  var changed=(TranslatableContents)source.feedback.getLast().getContents();
  check(changed.getKey().equals("chat.youzaiworldcore.style.changed")&&changed.getArgs()[0].equals("plain"),"切换成功反馈");
  ClientGlobalSettings.section(ClientGlobalSettings.CHAT_STYLE_MODULE).set("style","gradient");
  ClientGlobalSettings.load();ChatStyleSettings.load();check(ChatStyleSettings.getMode()==ChatStyleMode.PLAIN,"重新加载使用磁盘中的配色");
  check(dispatcher.execute("yzwc chat_style gradient",source)==1&&ChatStyleSettings.getMode()==ChatStyleMode.GRADIENT,"命令切换回渐变点缀");
  check(source.client.gui.hud.getChat().rescaleCalls==2&&history.groups()==1&&history.latest().content().getString().endsWith("[!350]"),"再次换色保留同一重复组");
  gradient(history.latest().content(),"[聊天]",0xFF3364,0xD0529E,"旧聊天记录立即使用渐变");
  history.add(message(351,content,GuiMessageSource.PLAYER));check(history.groups()==1
   &&history.latest().content().getString().endsWith("[!351]"),"换色后继续累计并替换实际显示对象");
  String saved=java.nio.file.Files.readString(ClientGlobalSettings.file());int feedbacks=source.feedback.size();
  boolean invalid=false;try{dispatcher.execute("yzwc chat_style rainbow",source);}catch(com.mojang.brigadier.exceptions.CommandSyntaxException e){invalid=true;}
  check(invalid&&ChatStyleSettings.getMode()==ChatStyleMode.GRADIENT&&source.client.gui.hud.getChat().rescaleCalls==2
   &&source.feedback.size()==feedbacks&&java.nio.file.Files.readString(ClientGlobalSettings.file()).equals(saved),"非法名称不改变配色、历史或配置");
  invalid=false;try{dispatcher.execute("yzwc chat_style plain extra",source);}catch(com.mojang.brigadier.exceptions.CommandSyntaxException e){invalid=true;}
  check(invalid&&ChatStyleSettings.getMode()==ChatStyleMode.GRADIENT,"多余参数不触发切换");
  ClientGlobalSettings.section(ClientGlobalSettings.CHAT_STYLE_MODULE).set("style","plain");ChatStyleSettings.load();
  ChatStyleSettings.writeDefaults();check(ChatStyleSettings.getMode()==ChatStyleMode.GRADIENT
   &&ClientGlobalSettings.section(ClientGlobalSettings.CHAT_STYLE_MODULE).getString("style","").equals("gradient"),"默认写入器重置修改后的配色");
  ClientGlobalSettings.section(ClientGlobalSettings.CHAT_STYLE_MODULE).set("style","PLAIN");ClientGlobalSettings.save();
  ChatStyleSettings.load();check(ChatStyleSettings.getMode()==ChatStyleMode.PLAIN,"配置枚举忽略大小写");
  for(Object value:List.of("rainbow",true)){
   if(value instanceof String text)ClientGlobalSettings.section(ClientGlobalSettings.CHAT_STYLE_MODULE).set("style",text);
   else ClientGlobalSettings.section(ClientGlobalSettings.CHAT_STYLE_MODULE).set("style",(boolean)value);
   ClientGlobalSettings.save();String broken=java.nio.file.Files.readString(ClientGlobalSettings.file());boolean failed=false;
   try{ChatStyleSettings.load();}catch(top.csituka.youzaiworldcore.config.ConfigCrash.ConfigFormatException e){failed=e.getMessage().contains("chat_style_module.style");}
   check(failed,"非法配置类型或名称通过统一配置层报告位置");
   try(var files=java.nio.file.Files.list(ClientGlobalSettings.file().getParent())){
    check(files.filter(p->p.getFileName().toString().startsWith("global_settings.json.error"))
     .anyMatch(p->{try{return java.nio.file.Files.readString(p).equals(broken);}catch(java.io.IOException e){return false;}}),"坏配色配置保留原始现场");
   }
   check(com.google.gson.JsonParser.parseString(java.nio.file.Files.readString(ClientGlobalSettings.file())).getAsJsonObject()
    .getAsJsonObject("chat_style_module").get("style").getAsString().equals("gradient"),"坏配置重建完整默认聊天配色");
  }
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
        "chat.youzaiworldcore.player": "[聊天]|%s%s%s%s",
        "chat.youzaiworldcore.private": "[私信]|%s%s%s%s",
        "chat.youzaiworldcore.game_header": "[游戏]|%s",
        "chat.youzaiworldcore.game": "[游戏]|%s >> %s",
        "chat.youzaiworldcore.mod": "[模组]|%s >> %s",
        "chat.youzaiworldcore.event.advancement": "[成就]|%s%s%s%s",
        "chat.youzaiworldcore.event.join": "[加入]|%s%s%s%s",
        "chat.youzaiworldcore.event.death": "[死亡]|%s%s%s%s",
        "chat.youzaiworldcore.event.leave": "[退出]|%s%s%s%s",
    }
    locales = list((ROOT / "src/main/resources/assets/youzaiworldcore/lang").glob("*.json"))
    assert len(locales) == 10
    for file in locales:
        data = json.loads(file.read_text(), object_pairs_hook=unique)
        assert all(data.get(key) == value for key, value in expected.items()), file
        for key in ["chat.youzaiworldcore.style.current", "chat.youzaiworldcore.style.changed", "chat.youzaiworldcore.private.sent_to"]:
            assert data.get(key, "").count("%s") == 1, (file, key)
        for suffix in ["join.body", "join.first", "join.renamed", "leave.body", "advancement.task", "advancement.goal", "advancement.challenge"]:
            key = "chat.youzaiworldcore.event." + suffix
            assert data.get(key), (file, key)
            assert data[key].count("%s") == (1 if suffix in ["join.renamed", "advancement.task", "advancement.goal", "advancement.challenge"] else 0), (file, key)
        for suffix, count in {"player": 1, "level": 1, "experience": 2, "title": 1, "game_mode": 1, "no_title": 0}.items():
            key = "chat.youzaiworldcore.hover." + suffix
            assert data.get(key) and data[key].count("%s") == count, (file, key)
    chat_type = json.loads((ROOT / "src/main/resources/data/youzaiworldcore/chat_type/chat_line.json").read_text())
    for key in ["chat", "narration"]:
        assert chat_type[key] == {"translation_key": "%s >> %s", "parameters": ["sender", "content"], "style": {"color": "#f3efef"}}
    client = json.loads((ROOT / "src/client/resources/youzaiworldcore.client.mixins.json").read_text())
    assert client["client"].count("ClientCommandSuggestionsMixin") == 1
    server = json.loads((ROOT / "src/main/resources/youzaiworldcore.server.mixins.json").read_text())
    for name in ["chat.SystemChatPacketMixin", "chat.CommandSourceStackChatMixin", "chat.TellRawCommandMixin", "chat.PlayerAdvancementsChatMixin"]:
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
    brigadier = list((Path.home() / ".gradle/caches/modules-2/files-2.1/com.mojang/brigadier").glob("*/*/brigadier-*.jar"))
    gson = list((Path.home() / ".gradle/caches/modules-2/files-2.1/com.google.code.gson/gson").glob("*/*/gson-*.jar"))
    assert brigadier and gson, "需要本地缓存的 Brigadier 与 Gson 来验证命令及实际配置存放层"
    libraries = os.pathsep.join(str(p) for p in [mixins[0], asm[0], brigadier[0], gson[0]])
    production = [
        "src/main/java/" + P + "/chat/" + name + ".java"
        for name in ["ChatEventType", "ChatPlayerInfo", "ChatMessageStyle", "ModMessageSource", "ChatFormatHelper", "StyledChatMessage", "ExtPlayerChatMessage"]
    ] + [
        "src/main/java/" + P + "/mixin/chat/" + name + ".java"
        for name in ["MsgCommandMixin", "PlayerListMixin", "SystemChatPacketMixin", "CommandSourceStackChatMixin", "TellRawCommandMixin", "PlayerAdvancementsChatMixin", "ServerPlayerChatMixin", "ServerGamePacketListenerImplMixin"]
    ] + [
        "src/main/java/" + P + "/config/ChatFormatSettings.java",
        "src/main/java/" + P + "/title/TitleDefinition.java",
        "src/client/java/" + P + "/client/chat/ChatRepeatTracker.java",
        "src/client/java/" + P + "/client/chat/ChatMessageRenderer.java",
        "src/client/java/" + P + "/client/chat/ChatStyleMode.java",
        "src/client/java/" + P + "/client/config/ChatStyleSettings.java",
        "src/client/java/" + P + "/client/config/ClientGlobalSettings.java",
        "src/client/java/" + P + "/client/config/ClientDefaultSettingsWriter.java",
        "src/client/java/" + P + "/command/ChatStyleClientCommand.java",
        "src/main/java/" + P + "/config/ConfigSection.java",
        "src/main/java/" + P + "/config/JsonFileStore.java",
        "src/main/java/" + P + "/config/ConfigCrash.java",
        "src/client/java/" + P + "/mixin/client/ChatComponentMixin.java",
        "src/client/java/" + P + "/mixin/client/ClientCommandSuggestionsMixin.java",
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
             "-cp", libraries, "-d", str(temporary), *files, *[str(ROOT / p) for p in production], str(Path(__file__).resolve().parents[1] / "src/main/java/top/csituka/youzaiworldcore/util/LogValues.java")],
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
        subprocess.run([str(binary / "java"), "-XX:-UsePerfData", "-Dyzwc.chat.testDirectory=" + str(temporary / "config-fixture"),
                        "-cp", runtime_classpath, "ChatMessageChecks"], cwd=ROOT, check=True)


if __name__ == "__main__":
    main()
