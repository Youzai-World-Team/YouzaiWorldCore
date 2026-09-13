package top.csituka.youzaiworldcore.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import top.csituka.youzaiworldcore.account.util.AuthPlayerHelper;
import top.csituka.youzaiworldcore.map.MapServerManager;
import top.csituka.youzaiworldcore.map.MapServerSettings;
import top.csituka.youzaiworldcore.map.MapWaypoint;
import top.csituka.youzaiworldcore.network.MapActionPayload;
import top.csituka.youzaiworldcore.util.DebugLogger;

import java.util.Locale;
import java.util.UUID;

/** /yzwc map：公共点、位置共享和同步统计；权限由地图权威入口再次核验。 */
public final class MapCommand {
    private MapCommand() { }

    /** 注册服务端命令，管理/传送权限分别使用 map.manage 与 map.teleport 节点。 */
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        var shared = Commands.literal("shared").executes(context -> list(context.getSource()));
        shared.then(Commands.literal("list").executes(context -> list(context.getSource())));
        shared.then(Commands.literal("add").then(Commands.argument("name", StringArgumentType.greedyString()).executes(context -> {
            var player = context.getSource().getPlayerOrException();
            try {
                return MapServerManager.action(player, new MapActionPayload(MapActionPayload.Action.ADD, here(player, StringArgumentType.getString(context, "name")))) ? 1 : 0;
            } catch (IllegalArgumentException error) { context.getSource().sendFailure(text("invalid_point")); return 0; }
        })));
        for (var action : new MapActionPayload.Action[] {MapActionPayload.Action.DELETE, MapActionPayload.Action.LOCK, MapActionPayload.Action.UNLOCK}) {
            shared.then(Commands.literal(action.name().toLowerCase(Locale.ROOT)).then(Commands.argument("id", StringArgumentType.word())
                    .suggests((context, builder) -> { MapServerManager.points().forEach(point -> builder.suggest(point.id().toString())); return builder.buildFuture(); })
                    .executes(context -> {
                        var player = context.getSource().getPlayerOrException();
                        try {
                            UUID id = UUID.fromString(StringArgumentType.getString(context, "id"));
                            var point = MapServerManager.points().stream().filter(value -> value.id().equals(id)).findFirst().orElse(null);
                            if (point != null) return MapServerManager.action(player, new MapActionPayload(action, point)) ? 1 : 0;
                        } catch (IllegalArgumentException ignored) { }
                        context.getSource().sendFailure(text("missing")); return 0;
                    })));
        }
        var position = Commands.literal("position");
        for (boolean show : new boolean[] {true, false}) position.then(Commands.literal(show ? "show" : "hide").executes(context -> {
            var player = context.getSource().getPlayerOrException();
            return MapServerManager.action(player, new MapActionPayload(show ? MapActionPayload.Action.SHOW_POSITION : MapActionPayload.Action.HIDE_POSITION,
                    here(player, "位置"))) ? 1 : 0;
        }));
        dispatcher.register(Commands.literal("yzwc").then(Commands.literal("map")
                .requires(source -> !(source.getEntity() instanceof ServerPlayer player) || !AuthPlayerHelper.shouldBlockActions(player))
                .executes(context -> { context.getSource().sendSuccess(() -> text("command_hint"), false); return 1; })
                .then(shared).then(position)
                .then(Commands.literal("stats").executes(context -> {
                    var player = context.getSource().getPlayerOrException();
                    context.getSource().sendSuccess(() -> MapServerManager.performance(player.getUUID()), false); return 1;
                }))));
        DebugLogger.info("MapCommand", "已注册 /yzwc map 服务端命令");
    }

    private static int list(CommandSourceStack source) {
        if (!MapServerSettings.enabled || !MapServerSettings.shareWaypoints) { source.sendFailure(text("denied")); return 0; }
        var points = MapServerManager.points().stream().filter(point -> point.kind() != MapWaypoint.Kind.STRUCTURE || MapServerSettings.shareStructures).toList();
        source.sendSuccess(() -> text("shared_count", points.size()), false);
        points.stream().limit(64).forEach(point -> source.sendSuccess(() -> text("shared_entry", point.id().toString(), point.name(), point.dimension(), point.x(), point.y(), point.z()), false));
        return points.size();
    }

    private static MapWaypoint here(ServerPlayer player, String name) {
        return new MapWaypoint(UUID.randomUUID(), player.getUUID(), name, "", player.level().dimension().identifier().toString(),
                player.getBlockX(), player.getBlockY(), player.getBlockZ(), 0xFF79BDEB, true, true, MapWaypoint.Kind.NORMAL, System.currentTimeMillis());
    }
    private static Component text(String key, Object... arguments) { return Component.translatable("map.youzaiworldcore." + key, arguments); }
}
