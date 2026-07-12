package top.csituka.youzaiworldcore.command;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.*;
import net.minecraft.server.level.ServerPlayer;
import top.csituka.youzaiworldcore.data.TeleportAnchorData;
import top.csituka.youzaiworldcore.data.TeleportAnchorManager;
import top.csituka.youzaiworldcore.luckperms.LuckPermsHelper;
import top.csituka.youzaiworldcore.util.DebugLogger;

import java.util.List;

/**
 * 传送锚点管理命令。
 * <p>
 * 子命令：
 * <ul>
 *   <li>{@code /yzwc teleport_anchor list [player]} — 列出玩家（默认自己）的所有传送锚点</li>
 * </ul>
 * 列表中的每个传送点提供可点击的传送链接（使用 26.2 的 ClickEvent.RunCommand / HoverEvent.ShowText 记录类型）。
 */
@SuppressWarnings("null")
public final class TeleportAnchorCommand {

    private TeleportAnchorCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        DebugLogger.entering("TeleportAnchorCommand", "register");

        dispatcher.register(Commands.literal("yzwc")
            .then(Commands.literal("teleport_anchor")
                .requires(source -> LuckPermsHelper.checkPermission(
                        source, LuckPermsHelper.PERMISSION_TELEPORT_ANCHOR, Commands.LEVEL_ADMINS))
                .then(Commands.literal("list")
                    .executes(context -> executeList(context.getSource(), null))
                    .then(Commands.argument("player", EntityArgument.player())
                        .executes(context -> executeList(
                            context.getSource(),
                            EntityArgument.getPlayer(context, "player")))
                    )
                )
            )
        );

        DebugLogger.exiting("TeleportAnchorCommand", "register");
    }

    private static int executeList(CommandSourceStack source, ServerPlayer target) {
        DebugLogger.entering("TeleportAnchorCommand", "executeList");

        // 未指定玩家时使用命令执行者
        ServerPlayer lookupTarget;
        if (target != null) {
            lookupTarget = target;
        } else {
            if (source.getEntity() instanceof ServerPlayer sp) {
                lookupTarget = sp;
            } else {
                source.sendSystemMessage(Component.literal("§c此命令只能由玩家执行，或指定目标玩家。"));
                DebugLogger.exiting("TeleportAnchorCommand", "executeList", "non-player source without target");
                return 0;
            }
        }

        var server = source.getServer();
        TeleportAnchorManager manager = TeleportAnchorManager.get(server);
        List<TeleportAnchorData> points = manager.getPointsForPlayer(lookupTarget);

        String targetName = lookupTarget.getName().getString();
        if (points.isEmpty()) {
            source.sendSystemMessage(
                    Component.literal("§e玩家 " + targetName + " 没有任何传送锚点。"));
            DebugLogger.exiting("TeleportAnchorCommand", "executeList", "no points");
            return 0;
        }

        // 标题
        source.sendSystemMessage(Component.literal(
                "§6=== " + targetName + " 的传送锚点（共 " + points.size() + " 个）===§r"));

        for (int i = 0; i < points.size(); i++) {
            TeleportAnchorData point = points.get(i);
            int x = point.pos().getX();
            int y = point.pos().getY();
            int z = point.pos().getZ();
            String dim = point.dimension().identifier().toString();

            String poolTag = "";
            if (point.poolId() != null) {
                poolTag = " §7[" + point.poolId() + "]§r";
            }

            // 可点击传送链接（26.2 使用记录类型构造）
            MutableComponent tpLink = Component.literal("[▶ 传送]")
                    .withStyle(Style.EMPTY
                            .withColor(TextColor.fromRgb(0x55FF55))
                            .withBold(true)
                            .withClickEvent(new ClickEvent.RunCommand(
                                    "/tp @s " + x + " " + y + " " + z))
                            .withHoverEvent(new HoverEvent.ShowText(
                                    Component.literal("点击传送到 §e" + point.name()
                                            + "§r（" + dim + " @ " + x + ", " + y + ", " + z + "）"))));

            MutableComponent line = Component.literal("  §7#" + (i + 1) + "§r ")
                    .append(Component.literal(point.name() + poolTag))
                    .append(Component.literal("  "))
                    .append(tpLink);

            source.sendSystemMessage(line);
        }

        DebugLogger.exiting("TeleportAnchorCommand", "executeList", "success (" + points.size() + " points)");
        return points.size();
    }
}
