"""加点与宠物菜单回归：实际包编码、服务端宠物入口、加点方法和权限边界。"""
from pathlib import Path
import argparse,copy,subprocess,tempfile
import verify_world_menu as world
from verify_yzui_menu import method
ROOT=Path(__file__).resolve().parents[1];MAIN=ROOT/'src/main/java/top/csituka/youzaiworldcore';P='top.csituka.youzaiworldcore'
STUBS=copy.deepcopy(world.STUBS)
def stub(name,body):
 STUBS[name.replace('.','/')+'.java']='package '+name.rpartition('.')[0]+';\n'+body
stub(P+'.YouzaiworldCore','public class YouzaiworldCore {public static final String MOD_ID="youzaiworldcore";}')
key='net/minecraft/network/RegistryFriendlyByteBuf.java'
STUBS[key]=STUBS[key].replace('public class RegistryFriendlyByteBuf {','''public class RegistryFriendlyByteBuf {
 public void writeLong(long value){values.add(value);}public long readLong(){return (Long)values.get(cursor++);}
 public void writeUtf(String value){writeUtf(value,32767);}public String readUtf(){return readUtf(32767);}
''')
key='net/minecraft/network/chat/Component.java'
STUBS[key]=STUBS[key].replace('    public static Component translatable','    public String getString(){return key;}\n    public static Component translatable')
key='net/minecraft/network/chat/Component.java'
STUBS[key]=STUBS[key].replace('    public String getString()', '    public static Component literal(String value){return new Component(value,java.util.List.of());} public static Component empty(){return literal("");} public String getString()')
stub('net.minecraft.commands.CommandSource','public interface CommandSource {void sendSystemMessage(net.minecraft.network.chat.Component message);boolean acceptsSuccess();boolean acceptsFailure();boolean shouldInformAdmins();}')
stub('net.minecraft.commands.CommandSourceStack','public class CommandSourceStack {public CommandSource collector;public CommandSourceStack withSource(CommandSource value){collector=value;return this;}}')
stub('net.minecraft.commands.Commands','''public class Commands {
 public static final int LEVEL_ALL=0;public static String last;public static int result=1,calls;
 public Commands getDispatcher(){return this;}
 public int execute(String command,CommandSourceStack source){calls++;last=command;source.collector.sendSystemMessage(net.minecraft.network.chat.Component.translatable(result>0?"success":"permission_denied"));return result;}
}''')
stub('net.minecraft.server.MinecraftServer','public class MinecraftServer {public net.minecraft.commands.Commands getCommands(){return new net.minecraft.commands.Commands();}}')
stub('net.minecraft.server.level.ServerPlayer','''public class ServerPlayer {
 public boolean disconnected,blocked;public java.util.UUID id=new java.util.UUID(1,2);
 public boolean hasDisconnected(){return disconnected;}public Level level(){return new Level();}public java.util.UUID getUUID(){return id;}
 public net.minecraft.network.chat.Component getName(){return net.minecraft.network.chat.Component.translatable("Player");}
 public net.minecraft.commands.CommandSourceStack createCommandSourceStack(){return new net.minecraft.commands.CommandSourceStack();}
 public static class Level {public net.minecraft.server.MinecraftServer getServer(){return new net.minecraft.server.MinecraftServer();}}
}''')
stub(P+'.luckperms.LuckPermsHelper','public class LuckPermsHelper {public static boolean allowed=true;public static boolean checkPermission(net.minecraft.commands.CommandSourceStack src,String permission,int level){return allowed;}}')
stub(P+'.pet.command.PetCommand','public class PetCommand {public static final String PERMISSION_PET_LIST="list";}')
stub(P+'.pet.PetEntry','public record PetEntry(String internalName,String displayName,java.util.UUID ownerUUID,PetMode mode){public boolean isOwner(java.util.UUID id){return ownerUUID.equals(id);}}')
stub(P+'.pet.PetMode','public enum PetMode {COMPANIONSHIP,HUNTING,ATTACK,GUARD}')
stub(P+'.pet.PetGlobalState','''public class PetGlobalState {
 public static java.util.List<PetEntry> pets=new java.util.ArrayList<>();public static PetGlobalState get(Object server){return new PetGlobalState();}
 public java.util.Optional<PetEntry> findByInternalName(String id){return pets.stream().filter(p->p.internalName().equals(id)).findFirst();}
 public java.util.List<PetEntry> findByOwner(java.util.UUID id){return pets.stream().filter(p->p.isOwner(id)).toList();}
}''')
# Execute the actual upgrade method; persistence and Minecraft modifiers are isolated.
upgrade=(MAIN/'skill/AttributeManager.java').read_text()
stub(P+'.skill.AttributeManager','''import net.minecraft.server.level.ServerPlayer;import java.util.UUID;import top.csituka.youzaiworldcore.util.DebugLogger;
public class AttributeManager {
 public static void applySingleAttribute(ServerPlayer player,String key,int points){}public static void syncToClient(ServerPlayer player){}
'''+method(upgrade,'handleUpgrade')+'\n}')
stub(P+'.skill.PlayerAttributeStorage','public class PlayerAttributeStorage {public static PlayerAttributeData data=new PlayerAttributeData();public static PlayerAttributeData getOrCreate(java.util.UUID id,String name){return data;}public static void markDirty(java.util.UUID id){}}')
stub(P+'.skill.AdventureLevelManager','public class AdventureLevelManager {public static int getLevelFromExp(int exp){return exp;}}')
stub(P+'.skill.PlayerLevelStorage','public class PlayerLevelStorage {public static int level=30;public static Entry getOrCreate(java.util.UUID id,String name){return new Entry();}public static class Entry {public int totalExp=level;}}')
key=(P+'.util.DebugLogger').replace('.','/')+'.java'
STUBS[key]=STUBS[key].replace('public class DebugLogger {','public class DebugLogger {public static void info(String a,String b,Object...args){}public static void stateChange(String a,String b,Object...args){}')
CHECK='''import java.util.*;
import net.minecraft.server.level.ServerPlayer;import net.minecraft.commands.Commands;import net.minecraft.network.RegistryFriendlyByteBuf;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import top.csituka.youzaiworldcore.network.*;import top.csituka.youzaiworldcore.pet.*;import top.csituka.youzaiworldcore.skill.*;
public class MenuRequestChecks {
 static int checks;static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
 static PetMenuStatePayload invoke(ServerPlayer player,String action,String id,String value){PetMenuHandler.handle(player,new PetMenuRequestPayload(1,action,id,value));return (PetMenuStatePayload)ServerPlayNetworking.sent.removeFirst();}
 public static void main(String[] args){
  var buf=new RegistryFriendlyByteBuf();var request=new AttributeUpgradePayload("damageResistance",7);
  AttributeUpgradePayload.STREAM_CODEC.encode(buf,request);check(AttributeUpgradePayload.STREAM_CODEC.decode(buf).equals(request),"加点请求序号丢失");
  buf=new RegistryFriendlyByteBuf();var result=new AttributeUpgradeResultPayload(7,false);AttributeUpgradeResultPayload.STREAM_CODEC.encode(buf,result);check(AttributeUpgradeResultPayload.STREAM_CODEC.decode(buf).equals(result),"加点失败结果丢失");
  buf=new RegistryFriendlyByteBuf();var petRequest=new PetMenuRequestPayload(5,"rename","DOG1","Name");PetMenuRequestPayload.STREAM_CODEC.encode(buf,petRequest);check(PetMenuRequestPayload.STREAM_CODEC.decode(buf).equals(petRequest),"宠物请求字段丢失");
  buf=new RegistryFriendlyByteBuf();var petResult=new PetMenuStatePayload(5,true,net.minecraft.network.chat.Component.literal("done"),List.of(new PetMenuStatePayload.Entry("DOG1","Name","guard")));PetMenuStatePayload.STREAM_CODEC.encode(buf,petResult);check(PetMenuStatePayload.STREAM_CODEC.decode(buf).equals(petResult),"宠物列表字段丢失");
  var player=new ServerPlayer();PetGlobalState.pets.add(new PetEntry("DOG1","Name",player.id,PetMode.GUARD));PetGlobalState.pets.add(new PetEntry("DOG2","Other",UUID.randomUUID(),PetMode.GUARD));
  var response=invoke(player,"list","","");check(response.pets().size()==1&&Commands.calls==0,"列表泄漏其他玩家宠物或执行了命令");
  player.blocked=true;check(!invoke(player,"mode","DOG1","attack").success()&&Commands.calls==0,"未认证执行宠物操作");player.blocked=false;
  top.csituka.youzaiworldcore.luckperms.LuckPermsHelper.allowed=false;check(invoke(player,"list","","").pets().isEmpty(),"无权限泄漏列表");top.csituka.youzaiworldcore.luckperms.LuckPermsHelper.allowed=true;
  check(!invoke(player,"mode","DOG2","attack").success()&&Commands.calls==0,"操作其他玩家宠物");
  check(!invoke(player,"admin","DOG1","").success()&&Commands.calls==0,"菜单执行未开放命令");
  check(!invoke(player,"mode","DOG1","attack release_life").success()&&Commands.calls==0,"模式参数注入其他操作");
  check(invoke(player,"mode","DOG1","attack").success()&&Commands.last.equals("yzwc pet set DOG1 mode attack"),"模式命令不正确");
  invoke(player,"rename","DOG1","A \\\" B\\\\C");check(Commands.last.equals("yzwc pet set DOG1 rename \\\"A \\\\\\\" B\\\\\\\\C\\\""),"改名字符串未安全引用");
  Commands.result=0;response=invoke(player,"highlight","DOG1","");check(!response.success()&&response.message().getString().equals("permission_denied"),"命令权限拒绝反馈被覆盖");
  player.disconnected=true;PetMenuHandler.handle(player,new PetMenuRequestPayload(2,"list","",""));check(ServerPlayNetworking.sent.isEmpty(),"给断线玩家回包");player.disconnected=false;
  var data=PlayerAttributeStorage.data;data.skillPointsAvailable=2;
  check(!AttributeManager.handleUpgrade(player,"invalid")&&data.skillPointsAvailable==2,"未知属性消耗点数");
  data.damageResistance=40;check(!AttributeManager.handleUpgrade(player,"damageResistance")&&data.skillPointsAvailable==2,"满抗性消耗点数");
  data.damageResistance=39;check(AttributeManager.handleUpgrade(player,"damageResistance")&&data.damageResistance==40&&data.skillPointsAvailable==1,"有效加点没有扣除恰好一点");
  PlayerLevelStorage.level=19;check(!AttributeManager.handleUpgrade(player,"luck")&&data.skillPointsAvailable==1,"低等级限制被绕过");
  data.damageResistance=0;check(AttributeManager.handleUpgrade(player,"damageResistance")&&data.skillPointsAvailable==0,"低等级抗性例外失效");
  check(!AttributeManager.handleUpgrade(player,"damageResistance"),"零点数继续加点");
  System.out.println("加点/宠物请求回归通过："+checks+" 项断言。");
 }
}
'''
def main():
 parser=argparse.ArgumentParser();parser.add_argument('--java-home');args=parser.parse_args();binary=Path(args.java_home)/'bin' if args.java_home else Path('/usr/bin')
 gson=list((Path.home()/'.gradle/caches/modules-2/files-2.1/com.google.code.gson/gson').glob('*/*/*.jar'));assert gson
 prod=[MAIN/'network'/f'{name}.java' for name in ['AttributeUpgradePayload','AttributeUpgradeResultPayload','PetMenuRequestPayload','PetMenuStatePayload']]+[MAIN/'pet/PetMenuHandler.java',MAIN/'skill/PlayerAttributeData.java']
 with tempfile.TemporaryDirectory(prefix='.tmp-menu-requests-',dir=ROOT) as directory:
  temp=Path(directory);files=[]
  for path,source in {**STUBS,'MenuRequestChecks.java':CHECK}.items():
   file=temp/path;file.parent.mkdir(parents=True,exist_ok=True);file.write_text(source);files.append(str(file))
  result=subprocess.run([str(binary/'javac'),'--release','25','-encoding','UTF-8','-cp',str(gson[0]),'-d',str(temp),*files,*map(str,prod)],cwd=ROOT,capture_output=True,text=True)
  if result.returncode:print(result.stderr);raise SystemExit(result.returncode)
  subprocess.run([str(binary/'java'),'-cp',str(temp)+':'+str(gson[0]),'MenuRequestChecks'],cwd=ROOT,check=True)
if __name__=='__main__':main()
