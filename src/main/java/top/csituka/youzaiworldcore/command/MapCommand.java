package top.csituka.youzaiworldcore.command;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import top.csituka.youzaiworldcore.account.util.AuthPlayerHelper;
import top.csituka.youzaiworldcore.map.MapServerManager;
import top.csituka.youzaiworldcore.map.MapWaypoint;
import top.csituka.youzaiworldcore.map.MapWaypointGroup;
import top.csituka.youzaiworldcore.network.MapActionPayload;
import top.csituka.youzaiworldcore.util.DebugLogger;

import java.util.UUID;

/** /yzwc map：位置共享及同步统计。 */
public final class MapCommand {
    private MapCommand() {
    }

    /** 注册服务端地图命令。 */
    @SuppressWarnings("null")
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        var position = Commands.literal("position");
        for (boolean show : new boolean[] { true, false })
            position.then(Commands.literal(show ? "show" : "hide").executes(context -> {
                var player = context.getSource().getPlayerOrException();
                return MapServerManager.action(player,
                        new MapActionPayload(
                                show ? MapActionPayload.Action.SHOW_POSITION : MapActionPayload.Action.HIDE_POSITION,
                                here(player, "位置"))) ? 1 : 0;
            }));
        dispatcher.register(Commands.literal("yzwc").then(Commands.literal("map")
                .requires(source -> !(source.getEntity() instanceof ServerPlayer player)
                        || !AuthPlayerHelper.shouldBlockActions(player))
                .executes(context -> {
                    context.getSource().sendSuccess(() -> text("command_hint"), false);
                    return 1;
                })
                .then(position)
                .then(Commands.literal("stats").executes(context -> {
                    var player = context.getSource().getPlayerOrException();
                    context.getSource().sendSuccess(() -> MapServerManager.performance(player.getUUID()), false);
                    return 1;
                }))));
        DebugLogger.SERVER.debug("MapCommand", "已注册 /yzwc map 服务端命令");
    }

    private static MapWaypoint here(ServerPlayer player, String name) {
        return new MapWaypoint(UUID.randomUUID(), player.getUUID(), name, MapWaypointGroup.UNGROUPED.id(),
                player.level().dimension().identifier().toString(),
                player.getBlockX(), player.getBlockY(), player.getBlockZ(), 0xFF79BDEB, true, false,
                MapWaypoint.Kind.NORMAL, System.currentTimeMillis());
    }

    @SuppressWarnings("null")
    private static Component text(String key, Object... arguments) {
        return Component.translatable("map.youzaiworldcore." + key, arguments);
    }
}
