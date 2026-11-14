"""验证逻辑服务端初始化、实际客户端命令树及 Api 隔离；不构建模组或启动游戏。"""
import argparse
import json
from pathlib import Path
import shutil
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[1]
PACKAGE = "top/csituka/youzaiworldcore"


def method(source, signature):
    start = source.index(signature)
    opening = source.index("{", start)
    depth = 1
    cursor = opening + 1
    while depth:
        depth += (source[cursor] == "{") - (source[cursor] == "}")
        cursor += 1
    return source[start:cursor]


STUBS = {
    "net/fabricmc/api/EnvType.java": """package net.fabricmc.api;
public enum EnvType { CLIENT, SERVER }
""",
    "net/fabricmc/loader/api/FabricLoader.java": """package net.fabricmc.loader.api;
import net.fabricmc.api.EnvType;
public final class FabricLoader {
    public static EnvType environment = EnvType.CLIENT;
    private static final FabricLoader INSTANCE = new FabricLoader();
    public static FabricLoader getInstance() { return INSTANCE; }
    public EnvType getEnvironmentType() { return environment; }
}
""",
    "net/minecraft/commands/Commands.java": """package net.minecraft.commands;
public final class Commands { public enum CommandSelection { ALL, DEDICATED, INTEGRATED } }
""",
    "net/fabricmc/fabric/api/command/v2/CommandRegistrationCallback.java": """package net.fabricmc.fabric.api.command.v2;
import java.util.*;
import net.minecraft.commands.Commands;
public interface CommandRegistrationCallback {
    Event EVENT = new Event();
    void register(Object dispatcher, Object context, Commands.CommandSelection selection);
    class Event {
        private final List<CommandRegistrationCallback> callbacks = new ArrayList<>();
        public void register(CommandRegistrationCallback callback) { callbacks.add(callback); }
        public void fire(Commands.CommandSelection selection) {
            for (var callback : List.copyOf(callbacks)) callback.register(new Object(), null, selection);
        }
        public void clear() { callbacks.clear(); }
    }
}
""",
    "net/fabricmc/fabric/api/client/command/v2/FabricClientCommandSource.java": """package net.fabricmc.fabric.api.client.command.v2;
public interface FabricClientCommandSource {}
""",
    "net/fabricmc/fabric/api/client/command/v2/ClientCommandRegistrationCallback.java": """package net.fabricmc.fabric.api.client.command.v2;
import java.util.*;
import com.mojang.brigadier.CommandDispatcher;
public interface ClientCommandRegistrationCallback {
    Event EVENT = new Event();
    void register(CommandDispatcher<FabricClientCommandSource> dispatcher, Object context);
    class Event {
        private final List<ClientCommandRegistrationCallback> callbacks = new ArrayList<>();
        public void register(ClientCommandRegistrationCallback callback) { callbacks.add(callback); }
        public void fire(CommandDispatcher<FabricClientCommandSource> dispatcher) {
            for (var callback : callbacks) callback.register(dispatcher, null);
        }
    }
}
""",
    "net/fabricmc/fabric/api/client/command/v2/ClientCommands.java": """package net.fabricmc.fabric.api.client.command.v2;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.builder.*;
public final class ClientCommands {
    public static LiteralArgumentBuilder<FabricClientCommandSource> literal(String name) {
        return LiteralArgumentBuilder.literal(name);
    }
    public static <T> RequiredArgumentBuilder<FabricClientCommandSource,T> argument(String name, ArgumentType<T> type) {
        return RequiredArgumentBuilder.argument(name, type);
    }
}
""",
    f"{PACKAGE}/util/DebugLogger.java": """package top.csituka.youzaiworldcore.util;
public final class DebugLogger {
    public static void entering(String module, String method, Object... arguments) {}
    public static void exiting(String module, String method, Object... arguments) {}
    public static void info(String module, String message, Object... arguments) {}
}
""",
    f"{PACKAGE}/config/ConfigSection.java": """package top.csituka.youzaiworldcore.config;
import java.util.*;
public final class ConfigSection {
    public final Map<String,Object> data = new HashMap<>();
    public boolean isEmpty() { return data.isEmpty(); }
    public void set(String key, Object value) { data.put(key, value); }
    public boolean getBoolean(String key, boolean fallback) { return (boolean)data.getOrDefault(key,fallback); }
    public String getString(String key, String fallback) { return (String)data.getOrDefault(key,fallback); }
    public int getInt(String key, int fallback, int min, int max) { return (int)data.getOrDefault(key,fallback); }
}
""",
    f"{PACKAGE}/config/GlobalSettings.java": """package top.csituka.youzaiworldcore.config;
public final class GlobalSettings {
    public static final String API_MODULE = "api_module";
    public static final ConfigSection SECTION = new ConfigSection();
    public static int reads, saves;
    public static ConfigSection section(String name) { reads++; return SECTION; }
    public static Object file() { return new Object(); }
    public static void save() { saves++; }
}
""",
    f"{PACKAGE}/config/ConfigCrash.java": """package top.csituka.youzaiworldcore.config;
public final class ConfigCrash {
    public static void fail(Object file, String key, String message, Object... errors) {
        throw new IllegalArgumentException(key + ": " + message);
    }
}
""",
}

CHECK = """package top.csituka.youzaiworldcore;
import java.util.*;
import javax.tools.*;
import com.sun.source.util.JavacTask;
import com.mojang.brigadier.*;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.*;
import net.minecraft.commands.Commands;
import top.csituka.youzaiworldcore.command.YzwcServerMirrorCommand;
import top.csituka.youzaiworldcore.config.*;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.*;
public final class CommandRegression {
    private static int checks;
    private static final FabricClientCommandSource SOURCE = new FabricClientCommandSource() {};
    private static void require(boolean condition, String message) {
        checks++; if (!condition) throw new AssertionError(message);
    }
    public static void main(String[] args) throws Exception {
        var diagnostics = new DiagnosticCollector<JavaFileObject>();
        var compiler = ToolProvider.getSystemJavaCompiler();
        try (var files = compiler.getStandardFileManager(diagnostics, null, null)) {
            var task = (JavacTask)compiler.getTask(null, files, diagnostics, List.of("-proc:none"), null,
                    files.getJavaFileObjectsFromStrings(List.of(args)));
            task.parse();
            require(diagnostics.getDiagnostics().stream().noneMatch(d -> d.getKind() == Diagnostic.Kind.ERROR),
                    "修改过的 Java 源码语法有效（只解析，不生成模组类文件）");
        }
        FabricLoader.environment = EnvType.CLIENT;
        LifecycleProbe.commands = 0;
        var client = new LifecycleProbe(); client.onInitialize();
        require(client.common == 1 && client.server == 0, "客户端启动只进行公共注册");
        CommandRegistrationCallback.EVENT.fire(Commands.CommandSelection.INTEGRATED);
        require(client.server == 1 && LifecycleProbe.commands == 1, "内置服务器初始化玩法并注册命令");
        CommandRegistrationCallback.EVENT.fire(Commands.CommandSelection.INTEGRATED);
        require(client.server == 1 && LifecycleProbe.commands == 2, "重新入世或资源重载不重复挂载玩法事件");
        CommandRegistrationCallback.EVENT.clear(); LifecycleProbe.commands = 0;
        FabricLoader.environment = EnvType.SERVER;
        var dedicated = new LifecycleProbe(); dedicated.onInitialize();
        require(dedicated.server == 1, "专用服务端保留立即初始化");
        CommandRegistrationCallback.EVENT.fire(Commands.CommandSelection.DEDICATED);
        require(dedicated.server == 1 && LifecycleProbe.commands == 1, "专用服务端命令注册一次");

        GlobalSettings.SECTION.data.clear();
        GlobalSettings.SECTION.set("enabled", true);
        GlobalSettings.SECTION.set("server_key", "0123456789abcdef0123456789abcdef");
        ApiModuleSettings.load();
        require(ApiModuleSettings.isEnabled() && ApiModuleSettings.getServerKey().length() == 32, "专用服务端读取合法密钥");
        FabricLoader.environment = EnvType.CLIENT;
        int reads = GlobalSettings.reads, saves = GlobalSettings.saves;
        ApiModuleSettings.load();
        require(!ApiModuleSettings.isEnabled() && ApiModuleSettings.getServerKey().isEmpty(), "内置服务器清除运行期密钥并关闭 Api");
        require(GlobalSettings.reads == reads && GlobalSettings.saves == saves, "客户端不读取或改写 Api 配置");
        ApiModuleSettings.load();
        require(!ApiModuleSettings.isEnabled() && GlobalSettings.reads == reads, "重载仍隔离 Api");
        FabricLoader.environment = EnvType.SERVER;
        GlobalSettings.SECTION.set("server_key", "short");
        try { ApiModuleSettings.load(); throw new AssertionError("专用服务端必须拒绝短密钥"); }
        catch (IllegalArgumentException expected) { checks++; }

        var dispatcher = new CommandDispatcher<FabricClientCommandSource>();
        for (String name : List.of("double_doors", "invisibility")) {
            dispatcher.register(literal("yzwc").then(literal("function").then(literal(name)
                .executes(context -> 7).then(argument("enabled", BoolArgumentType.bool()).executes(context -> 9)))));
        }
        dispatcher.register(literal("yzwc").then(literal("mail").then(literal("list").executes(context -> 11))));
        YzwcServerMirrorCommand.register();
        ClientCommandRegistrationCallback.EVENT.fire(dispatcher);
        int[] ambiguities = {0};
        dispatcher.findAmbiguities((parent, child, sibling, inputs) -> ambiguities[0]++);
        require(ambiguities[0] == 0, "镜像不与客户端 bool 参数产生歧义");
        for (String command : List.of("yzwc reload", "yzwc teleport_world @s minecraft:overworld",
                "yzwc world_pool list", "yzwc afk", "yzwc afk settings timeout 300",
                "yzwc account change_password old new new", "yzwc mail pull 01234567-89ab-cdef-0123-456789abcdef",
                "yzwc event crop_xp_drop enable", "yzwc event laowu settings cd 60",
                "yzwc function damage_numbers", "yzwc function damage_numbers false", "yzwc update check")) {
            var parsed = dispatcher.parse(command, SOURCE);
            require(!parsed.getReader().canRead(), "服务端命令参数完整消费：" + command);
            try { dispatcher.execute(parsed); throw new AssertionError("镜像不能本地执行：" + command); }
            catch (CommandSyntaxException error) {
                require(error.getType() == CommandSyntaxException.BUILT_IN_EXCEPTIONS.dispatcherUnknownCommand(),
                        "Fabric 可将镜像命令交给服务端：" + command);
            }
        }
        for (String name : List.of("double_doors", "invisibility")) {
            require(dispatcher.execute("yzwc function " + name, SOURCE) == 7, "客户端无参查询保留");
            require(dispatcher.execute("yzwc function " + name + " true", SOURCE) == 9, "客户端 true 保留");
            require(dispatcher.execute("yzwc function " + name + " false", SOURCE) == 9, "客户端 false 保留");
            try { dispatcher.execute("yzwc function " + name + " invalid", SOURCE); throw new AssertionError("错误 bool 必须报错"); }
            catch (CommandSyntaxException error) {
                require(error.getType() == CommandSyntaxException.BUILT_IN_EXCEPTIONS.readerInvalidBool(), "客户端 bool 错误不会被镜像吞掉");
            }
        }
        require(dispatcher.execute("yzwc mail list", SOURCE) == 11, "邮件本地动作保留");
        System.out.println("命令回归通过 " + checks + " 项行为断言");
    }
}
"""


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--java-home", type=Path)
    parser.add_argument("--brigadier", type=Path)
    args = parser.parse_args()
    java = str(args.java_home / "bin/java") if args.java_home else shutil.which("java")
    javac = str(args.java_home / "bin/javac") if args.java_home else shutil.which("javac")
    jars = list((Path.home() / ".gradle/caches/modules-2/files-2.1/com.mojang/brigadier").glob("*/*/brigadier-*.jar"))
    brigadier = args.brigadier or next((p for p in jars if "sources" not in p.name), None)
    if not java or not javac or not brigadier:
        parser.error("需要 JDK 25+ 和缓存的 Brigadier，可指定 --java-home / --brigadier")

    core = (ROOT / f"src/main/java/{PACKAGE}/YouzaiworldCore.java").read_text()
    guard = method(core, "private synchronized void initializeServer()")
    guard = guard[:guard.index("        boolean dedicated")] + "        server++;\n    }"
    lifecycle = """package top.csituka.youzaiworldcore;
import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.Commands;
import top.csituka.youzaiworldcore.util.DebugLogger;
final class LifecycleProbe {
    private boolean serverInitialized;
    int common, server;
    static int commands;
    private void initializeCommonRegistrations() { common++; }
    private static void registerServerCommands(Object dispatcher) { commands++; }
""" + method(core, "public void onInitialize()") + guard + "\n}"

    mod = json.loads((ROOT / "src/main/resources/fabric.mod.json").read_text())
    common = [entry if isinstance(entry, str) else entry["config"] for entry in mod["mixins"]
              if isinstance(entry, str) or "environment" not in entry]
    assert "youzaiworldcore.server.mixins.json" in common
    assert "youzaiworldcore.account-state.mixins.json" in common
    state = json.loads((ROOT / "src/main/resources/youzaiworldcore.account-state.mixins.json").read_text())
    auth = json.loads((ROOT / "src/main/resources/youzaiworldcore.account.mixins.json").read_text())
    assert state["mixins"] == ["AccountServerPlayerMixin"]
    assert "AccountServerPlayerMixin" not in auth["mixins"]
    assert next(entry for entry in mod["mixins"] if isinstance(entry, dict)
                and entry["config"] == "youzaiworldcore.account.mixins.json")["environment"] == "server"
    player = (ROOT / f"src/main/java/{PACKAGE}/account/mixin/AccountServerPlayerMixin.java").read_text()
    assert "!yzwc$player.level().getServer().isDedicatedServer()" in method(player, "public boolean yzwc$canSkipAuth()")
    assert "if (yzwc$canSkipAuth())" in method(player, "private void onPlayerTick(")
    assert core.count("CommandRegistrationCallback.EVENT.register(") == 1
    print("Mixin 环境、认证跳过及回调唯一性检查通过", flush=True)

    with tempfile.TemporaryDirectory(prefix="yzwc-command-check-") as temporary:
        directory = Path(temporary)
        sources = dict(STUBS)
        sources[f"{PACKAGE}/LifecycleProbe.java"] = lifecycle
        sources[f"{PACKAGE}/CommandRegression.java"] = CHECK
        for name, source in sources.items():
            path = directory / name
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(source)
        actual = [ROOT / f"src/client/java/{PACKAGE}/command/YzwcServerMirrorCommand.java",
                  ROOT / f"src/main/java/{PACKAGE}/config/ApiModuleSettings.java"]
        classes = directory / "classes"
        classes.mkdir()
        subprocess.run([javac, "--release", "25", "-encoding", "UTF-8", "-proc:none", "-cp", str(brigadier),
                        "-d", str(classes), *map(str, directory.rglob("*.java")), *map(str, actual)], check=True)
        modified = [ROOT / f"src/main/java/{PACKAGE}/YouzaiworldCore.java",
                    ROOT / f"src/main/java/{PACKAGE}/account/command/AccountCommands.java",
                    ROOT / f"src/main/java/{PACKAGE}/account/mixin/AccountServerPlayerMixin.java", *actual]
        subprocess.run([java, "-cp", f"{classes}:{brigadier}", "top.csituka.youzaiworldcore.CommandRegression",
                        *map(str, modified)], check=True)


if __name__ == "__main__":
    main()
