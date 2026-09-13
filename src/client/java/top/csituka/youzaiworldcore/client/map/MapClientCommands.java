package top.csituka.youzaiworldcore.client.map;

import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import top.csituka.youzaiworldcore.client.screen.map.MapImportScreen;
import top.csituka.youzaiworldcore.client.screen.map.MapSettingsScreen;
import top.csituka.youzaiworldcore.client.screen.map.MapWaypointEditScreen;
import top.csituka.youzaiworldcore.client.screen.map.MapWaypointListScreen;
import top.csituka.youzaiworldcore.client.screen.map.YzWorldMapScreen;
import top.csituka.youzaiworldcore.util.DebugLogger;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.argument;

/** 本机地图界面命令；shared、position、stats 仅转发服务端，客户端不判定权威权限。 */
public final class MapClientCommands {
    private MapClientCommands() { }
    public static void register() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, context) -> {
            var map = literal("map").executes(command -> open(new YzWorldMapScreen(null)))
                    .then(literal("settings").executes(command -> open(new MapSettingsScreen(null))))
                    .then(literal("waypoints").executes(command -> open(new MapWaypointListScreen(null))))
                    .then(literal("import").executes(command -> open(new MapImportScreen(null))))
                    .then(literal("add").executes(command -> {
                        var player = Minecraft.getInstance().player;
                        return player == null ? 0 : open(new MapWaypointEditScreen(null, MapClient.newPoint(MapClient.dimension(), player.getBlockX(), player.getBlockY(), player.getBlockZ()), false));
                    }))
                    .then(literal("refresh").executes(command -> { MapClient.refresh(); return 1; }));
            // 沿用项目的无执行器镜像节点：Fabric 会把它们原样交给服务端。
            // 在客户端执行器中再次调用 sendCommand 会被 Fabric 再次拦截并递归执行。
            for (String name : new String[] {"shared", "position", "stats"}) map.then(literal(name)
                    .then(argument("args", StringArgumentType.greedyString())));
            dispatcher.register(literal("yzwc").then(map));
        });
        DebugLogger.info("MapClientCommands", "已注册 /yzwc map 本机入口");
    }
    private static int open(Screen screen) {
        var client = Minecraft.getInstance();
        if (client.player == null) { MapClient.message("world_required"); return 0; }
        // 聊天命令执行结束后才切换界面，避免聊天关闭逻辑覆盖目标屏幕。
        MapClient.openNext(screen); return 1;
    }
}
