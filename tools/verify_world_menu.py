"""世界菜单回归：运行实际网络包、服务端菜单服务及公开池筛选方法。

Minecraft 缓冲区、玩家和传送依赖使用替身；不启动游戏或构建模组。
"""
from pathlib import Path
import argparse
import os
import subprocess
import tempfile
from verify_yzui_menu import method

ROOT = Path(__file__).resolve().parents[1]
MAIN = ROOT / "src/main/java"
P = "top.csituka.youzaiworldcore"
STUBS = {}


def stub(name, body):
    package, _, _ = name.rpartition(".")
    STUBS[name.replace(".", "/") + ".java"] = "package " + package + ";\n" + body


stub("net.minecraft.resources.Identifier", """
public record Identifier(String namespace, String path) {
    public static Identifier fromNamespaceAndPath(String n,String p){return new Identifier(n,p);}
    public String toString(){return namespace+":"+path;}
}
""")
stub("net.minecraft.network.protocol.common.custom.CustomPacketPayload", """
public interface CustomPacketPayload {
    record Type<T>(net.minecraft.resources.Identifier id) {}
    Type<? extends CustomPacketPayload> type();
}
""")
stub("net.minecraft.network.codec.StreamCodec", """
public interface StreamCodec<B,V> {
    void encode(B buffer,V value);
    V decode(B buffer);
    static <B,V> StreamCodec<B,V> of(java.util.function.BiConsumer<B,V> encode,java.util.function.Function<B,V> decode){
        return new StreamCodec<>(){
            public void encode(B b,V v){encode.accept(b,v);}
            public V decode(B b){return decode.apply(b);}
        };
    }
}
""")
stub("net.minecraft.network.RegistryFriendlyByteBuf", """
public class RegistryFriendlyByteBuf {
    private final java.util.List<Object> values=new java.util.ArrayList<>();
    private int cursor;
    public void writeUUID(java.util.UUID v){values.add(v);}
    public java.util.UUID readUUID(){return (java.util.UUID)values.get(cursor++);}
    public void writeUtf(String v,int limit){
        if(v.length()>limit)throw new IllegalArgumentException("字符串越界");
        values.add(v);
    }
    public String readUtf(int limit){
        String v=(String)values.get(cursor++);
        if(v.length()>limit)throw new IllegalArgumentException("字符串越界");
        return v;
    }
    public void writeBoolean(boolean v){values.add(v);}
    public boolean readBoolean(){return (Boolean)values.get(cursor++);}
    public void writeVarInt(int v){values.add(v);}
    public int readVarInt(){return (Integer)values.get(cursor++);}
}
""")
stub("net.minecraft.network.chat.Component", """
public record Component(String key,java.util.List<String> args) {
    public static Component translatable(String key,Object...args){
        return new Component(key,java.util.Arrays.stream(args).map(Object::toString).toList());
    }
}
""")
stub("net.minecraft.network.chat.ComponentSerialization", """
public class ComponentSerialization {
    public static final net.minecraft.network.codec.StreamCodec<net.minecraft.network.RegistryFriendlyByteBuf,Component> STREAM_CODEC=
        net.minecraft.network.codec.StreamCodec.of((buf,v)->{
            buf.writeUtf(v.key(),32767);buf.writeVarInt(v.args().size());
            for(String arg:v.args())buf.writeUtf(arg,32767);
        },buf->{
            String key=buf.readUtf(32767);int count=buf.readVarInt();var args=new java.util.ArrayList<String>();
            for(int i=0;i<count;i++)args.add(buf.readUtf(32767));
            return new Component(key,java.util.List.copyOf(args));
        });
}
""")
stub("net.minecraft.server.level.ServerPlayer", """
public class ServerPlayer {
    public boolean disconnected,blocked;
    public String dimension="minecraft:overworld";
    public boolean hasDisconnected(){return disconnected;}
    public Level level(){return new Level(dimension);}
    public record Level(String id){
        public Level dimension(){return this;}
        public net.minecraft.resources.Identifier identifier(){
            String[] parts=id.split(":");return new net.minecraft.resources.Identifier(parts[0],parts[1]);
        }
    }
}
""")
stub("net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking", """
public class ServerPlayNetworking {
    public static final java.util.List<net.minecraft.network.protocol.common.custom.CustomPacketPayload> sent=new java.util.ArrayList<>();
    public static void send(net.minecraft.server.level.ServerPlayer player,net.minecraft.network.protocol.common.custom.CustomPacketPayload payload){
        if(player.hasDisconnected())throw new AssertionError("给断线玩家发包");
        sent.add(payload);
    }
}
""")
stub(P + ".account.util.AuthPlayerHelper", """
public class AuthPlayerHelper {
    public static boolean shouldBlockActions(net.minecraft.server.level.ServerPlayer player){return player.blocked;}
}
""")
stub(P + ".util.DebugLogger", """
public class DebugLogger {
    public static int exceptions;
    public static void exception(String m,String s,Throwable error){exceptions++;}
    public static void debug(String m,String s,Object...args){}
    public static void entering(String m,String s,Object...args){}
    public static void exiting(String m,String s,Object...args){}
    public static void trace(String m,String s,Object...args){}
}
""")
stub(P + ".dimensionalinventories.DimensionPool", """
public record DimensionPool(String id,String displayName,Mode gameMode,boolean isEmpty) {
    public record Mode(String name){public String getName(){return name;}}
}
""")
settings = (MAIN / P.replace(".", "/") / "dimensionalinventories/DimensionPoolSettings.java").read_text(encoding="utf-8")
stub(P + ".dimensionalinventories.DimensionPoolSettings", """
import java.util.*;
import top.csituka.youzaiworldcore.util.DebugLogger;
public class DimensionPoolSettings {
    public static final Map<String,DimensionPool> POOLS=new LinkedHashMap<>();
    public static final Set<String> HIDDEN_POOL_IDS=new HashSet<>();
    public static DimensionPool current;
    public static Optional<DimensionPool> getPoolByDimension(String id){return Optional.ofNullable(current);}
""" + method(settings, "getAllPools") + method(settings, "isHiddenPool") + "\n}")
stub(P + ".dimensionalinventories.DimensionPoolManager", """
public class DimensionPoolManager {
    public static boolean outcome=true,throwsError,disconnect;
    public static int calls;
    public static String target;
    public static net.minecraft.network.chat.Component feedback;
    public static boolean teleportToPool(net.minecraft.server.level.ServerPlayer player,String id,
            java.util.function.Consumer<net.minecraft.network.chat.Component> callback){
        calls++;target=id;
        if(feedback!=null)callback.accept(feedback);
        if(throwsError)throw new IllegalStateException("模拟服务端传送异常");
        if(disconnect)player.disconnected=true;
        return outcome;
    }
}
""")

CHECK = """
import java.util.*;
import net.minecraft.network.*;
import net.minecraft.network.chat.*;
import net.minecraft.server.level.ServerPlayer;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import top.csituka.youzaiworldcore.network.*;
import top.csituka.youzaiworldcore.dimensionalinventories.*;
public class WorldMenuChecks {
    static int checks;
    static void check(boolean condition,String message){checks++;if(!condition)throw new AssertionError(message);}
    static void invalid(Runnable action){
        checks++;try{action.run();throw new AssertionError("未拒绝越界输入");}catch(IllegalArgumentException expected){}
    }
    static void codecs(){
        UUID id=UUID.randomUUID();
        for(String pool:List.of("","survival_world_pool","世".repeat(256))){
            var request=new WorldPoolMenuRequestPayload(id,pool);var buf=new RegistryFriendlyByteBuf();
            WorldPoolMenuRequestPayload.STREAM_CODEC.encode(buf,request);
            check(WorldPoolMenuRequestPayload.STREAM_CODEC.decode(buf).equals(request),"请求未原样往返");
        }
        invalid(()->WorldPoolMenuRequestPayload.STREAM_CODEC.encode(new RegistryFriendlyByteBuf(),
                new WorldPoolMenuRequestPayload(id,"x".repeat(257))));
        var malformed=new RegistryFriendlyByteBuf();malformed.writeUUID(id);malformed.writeUtf("x".repeat(257),300);
        invalid(()->WorldPoolMenuRequestPayload.STREAM_CODEC.decode(malformed));
        var mutable=new ArrayList<WorldPoolMenuStatePayload.Entry>();
        mutable.add(new WorldPoolMenuStatePayload.Entry("survival","生存世界","survival",true));
        mutable.add(new WorldPoolMenuStatePayload.Entry("empty","未配置","creative",false));
        var state=new WorldPoolMenuStatePayload(id,"survival","生存世界",mutable);
        mutable.clear();check(state.entries().size()==2,"目录列表未防御性复制");
        var buffer=new RegistryFriendlyByteBuf();WorldPoolMenuStatePayload.STREAM_CODEC.encode(buffer,state);
        check(WorldPoolMenuStatePayload.STREAM_CODEC.decode(buffer).equals(state),"世界信息未原样往返");
        for(int count:new int[]{-1,129}){
            var bad=new RegistryFriendlyByteBuf();bad.writeUUID(id);bad.writeUtf("",256);bad.writeUtf("",512);bad.writeVarInt(count);
            invalid(()->WorldPoolMenuStatePayload.STREAM_CODEC.decode(bad));
        }
        for(boolean success:new boolean[]{false,true}){
            var result=new WorldPoolTeleportResultPayload(id,success,Component.translatable("server.reason","目标世界"));
            var b=new RegistryFriendlyByteBuf();WorldPoolTeleportResultPayload.STREAM_CODEC.encode(b,result);
            check(WorldPoolTeleportResultPayload.STREAM_CODEC.decode(b).equals(result),"结果组件/请求关联丢失");
        }
    }
    static void service(){
        var player=new ServerPlayer();UUID id=UUID.randomUUID();
        var publicPool=new DimensionPool("public","生存世界",new DimensionPool.Mode("survival"),false);
        var hidden=new DimensionPool("login","登录大厅",new DimensionPool.Mode("adventure"),false);
        var empty=new DimensionPool("empty","未配置",new DimensionPool.Mode("creative"),true);
        DimensionPoolSettings.POOLS.put("public",publicPool);DimensionPoolSettings.POOLS.put("login",hidden);
        DimensionPoolSettings.POOLS.put("empty",empty);DimensionPoolSettings.HIDDEN_POOL_IDS.add("login");
        DimensionPoolSettings.current=publicPool;
        WorldPoolMenuService.handle(player,new WorldPoolMenuRequestPayload(id,""));
        var state=(WorldPoolMenuStatePayload)ServerPlayNetworking.sent.removeFirst();
        check(state.requestId().equals(id)&&state.currentName().equals("生存世界")&&state.currentPoolId().equals("public"),"当前世界信息失真");
        check(state.entries().size()==2&&state.entries().stream().noneMatch(e->e.poolId().equals("login")),"隐藏池泄漏");
        check(!state.entries().get(1).available()&&state.entries().get(1).gameMode().equals("creative"),"空池/模式信息错误");
        check(DimensionPoolManager.calls==0,"读目录触发传送");
        DimensionPoolSettings.current=hidden;
        WorldPoolMenuService.handle(player,new WorldPoolMenuRequestPayload(id,""));
        check(((WorldPoolMenuStatePayload)ServerPlayNetworking.sent.removeFirst()).currentName().isEmpty(),"当前隐藏池名称泄漏");
        for(int i=0;i<140;i++)DimensionPoolSettings.POOLS.put("pool"+i,publicPool);
        WorldPoolMenuService.handle(player,new WorldPoolMenuRequestPayload(id,""));
        check(((WorldPoolMenuStatePayload)ServerPlayNetworking.sent.removeFirst()).entries().size()==128,"目录超过协议上限");
        player.blocked=true;
        WorldPoolMenuService.handle(player,new WorldPoolMenuRequestPayload(id,"public"));
        var rejected=(WorldPoolTeleportResultPayload)ServerPlayNetworking.sent.removeFirst();
        check(!rejected.success()&&rejected.requestId().equals(id)&&rejected.message().key().endsWith("login_required"),"认证拒绝反馈错误");
        check(DimensionPoolManager.calls==0,"未登录执行了传送");
        player.blocked=false;player.disconnected=true;
        WorldPoolMenuService.handle(player,new WorldPoolMenuRequestPayload(id,"public"));
        check(ServerPlayNetworking.sent.isEmpty()&&DimensionPoolManager.calls==0,"断线请求未忽略");
        player.disconnected=false;
        for(boolean success:new boolean[]{false,true}){
            DimensionPoolManager.outcome=success;
            DimensionPoolManager.feedback=Component.translatable(success?"teleport.success":"pool.not_found","目标");
            WorldPoolMenuService.handle(player,new WorldPoolMenuRequestPayload(id,"public"));
            var result=(WorldPoolTeleportResultPayload)ServerPlayNetworking.sent.removeFirst();
            check(result.success()==success&&result.requestId().equals(id)&&result.message().equals(DimensionPoolManager.feedback),"原始服务端原因/请求关联未保留");
            check(DimensionPoolManager.target.equals("public"),"目标池改变");
            check(ServerPlayNetworking.sent.removeFirst() instanceof WorldPoolMenuStatePayload,"传送后未刷新当前世界");
        }
        DimensionPoolManager.throwsError=true;
        WorldPoolMenuService.handle(player,new WorldPoolMenuRequestPayload(id,"public"));
        var failure=(WorldPoolTeleportResultPayload)ServerPlayNetworking.sent.removeFirst();
        check(!failure.success()&&failure.message().key().endsWith(".failed"),"异常保留了错误的成功文案");
        ServerPlayNetworking.sent.clear();DimensionPoolManager.throwsError=false;DimensionPoolManager.disconnect=true;
        WorldPoolMenuService.handle(player,new WorldPoolMenuRequestPayload(id,"public"));
        check(ServerPlayNetworking.sent.isEmpty(),"传送期间断线仍回发数据");
    }
    public static void main(String[] args){codecs();service();System.out.println("世界菜单服务回归通过："+checks+" 项断言。");}
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
    production = [MAIN / P.replace(".", "/") / path for path in (
        "network/WorldPoolMenuRequestPayload.java", "network/WorldPoolMenuStatePayload.java",
        "network/WorldPoolTeleportResultPayload.java", "dimensionalinventories/WorldPoolMenuService.java")]
    with tempfile.TemporaryDirectory(prefix=".tmp-world-menu-", dir=ROOT) as directory:
        temporary = Path(directory)
        files = []
        for path, source in {**STUBS, "WorldMenuChecks.java": CHECK}.items():
            file = temporary / path
            file.parent.mkdir(parents=True, exist_ok=True)
            file.write_text(source, encoding="utf-8")
            files.append(str(file))
        subprocess.run([javac, "--release", "25", "-encoding", "UTF-8", "-d", str(temporary),
                        *files, *map(str, production)], cwd=ROOT, check=True)
        subprocess.run([java, "-cp", str(temporary), "WorldMenuChecks"], cwd=ROOT, check=True)


if __name__ == "__main__":
    main()
