package top.csituka.youzaiworldcore.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import top.csituka.youzaiworldcore.invisibility.InvisibilityManager;
import top.csituka.youzaiworldcore.luckperms.LuckPermsHelper;
import top.csituka.youzaiworldcore.util.DebugLogger;

/**
 * 隐身功能命令：{@code /yzwc function invisibility <true/false>}
 * <p>
 * 权限要求：</p>
 * <ul>
 *   <li>OP 4 级 或 LuckPerms 节点 {@code youzaiworldcore.command.function.invisibility}</li>
 *   <li>玩家必须处于创造模式</li>
 * </ul>
 */
public class InvisibilityCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        DebugLogger.entering("InvisibilityCommand", "register");
        dispatcher.register(Commands.literal("yzwc")
                .then(Commands.literal("function")
                        .then(Commands.literal("invisibility")
                                .requires(src -> {
                                    // 命令权限：OP 4 或 LuckPerms 节点
                                    return LuckPermsHelper.checkPermission(
                                            src,
                                            InvisibilityManager.PERMISSION_INVISIBILITY,
                                            Commands.LEVEL_ADMINS
                                    );
                                })
                                .then(Commands.argument("enabled", BoolArgumentType.bool())
                                        .executes(InvisibilityCommand::execute)
                                )
                        )
                )
        );
        DebugLogger.exiting("InvisibilityCommand", "register");
    }

    private static int execute(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        boolean enabled = BoolArgumentType.getBool(ctx, "enabled");
        DebugLogger.entering("InvisibilityCommand", "execute",
                "player=" + player.getName().getString() + ", enabled=" + enabled);

        // 检查是否处于创造模式
        if (player.gameMode() != GameType.CREATIVE) {
            DebugLogger.branch("InvisibilityCommand", "not in creative mode", true,
                    "player=" + player.getName().getString());
            player.sendSystemMessage(Component.literal(
                    "§c只有创造模式才能使用隐身功能（请先切换到创造模式）"
            ));
            DebugLogger.exiting("InvisibilityCommand", "execute", "0 (not creative)");
            return 0;
        }
        DebugLogger.branch("InvisibilityCommand", "is in creative mode", false);

        if (enabled) {
            DebugLogger.branch("InvisibilityCommand", "enabling invisibility", true);
            InvisibilityManager.enable(player);
        } else {
            DebugLogger.branch("InvisibilityCommand", "disabling invisibility", false);
            InvisibilityManager.disable(player);
        }

        DebugLogger.exiting("InvisibilityCommand", "execute", "1 (success)");
        return 1;
    }
}
