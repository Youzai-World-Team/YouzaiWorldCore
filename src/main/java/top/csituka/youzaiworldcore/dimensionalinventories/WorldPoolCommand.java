package top.csituka.youzaiworldcore.dimensionalinventories;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import top.csituka.youzaiworldcore.luckperms.LuckPermsHelper;

import java.util.Collection;

/**
 * /yzwc world_pool 命令注册与执行。
 * <p>
 * 子命令：
 * <ul>
 *   <li>{@code /yzwc world_pool teleport <targets> <dimension_pool>} — 传送玩家到指定维度池</li>
 *   <li>{@code /yzwc world_pool list} — 列出所有维度池及其包含的维度</li>
 * </ul>
 */
public final class WorldPoolCommand {

    private static final String POOL_ARG = "dimension_pool";
    private static final String PERMISSION_WORLD_POOL = "youzaiworldcore.command.world_pool";

    private WorldPoolCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("yzwc")
            .then(Commands.literal("world_pool")
                .requires(source -> LuckPermsHelper.checkPermission(
                        source, PERMISSION_WORLD_POOL, Commands.LEVEL_ADMINS))

                // === teleport ===
                .then(Commands.literal("teleport")
                    .then(Commands.argument("targets", EntityArgument.players())
                        .then(Commands.argument(POOL_ARG, StringArgumentType.word())
                            .suggests((context, builder) -> {
                                for (DimensionPool pool : DimensionPoolSettings.getAllPools()) {
                                    builder.suggest(pool.id());
                                }
                                return builder.buildFuture();
                            })
                            .executes(context -> executeTeleport(
                                context.getSource(),
                                EntityArgument.getPlayers(context, "targets"),
                                StringArgumentType.getString(context, POOL_ARG)
                            ))
                        )
                    )
                )

                // === list ===
                .then(Commands.literal("list")
                    .executes(context -> executeList(context.getSource()))
                )
            )
        );
    }

    // ===== 执行方法 =====

    /**
     * 执行传送玩家到指定维度池。
     */
    private static int executeTeleport(CommandSourceStack source,
                                        Collection<ServerPlayer> targets,
                                        String poolId) {
        DimensionPool pool = DimensionPoolSettings.getPool(poolId);
        if (pool == null) {
            source.sendFailure(Component.translatable(
                    "youzaiworldcore.message.diminv.pool_not_found", poolId));
            return 0;
        }

        int count = 0;
        for (ServerPlayer player : targets) {
            if (DimensionPoolManager.teleportToPool(player, poolId)) {
                count++;
            }
        }

        final int finalCount = count;
        source.sendSuccess(() -> Component.translatable(
                "youzaiworldcore.message.diminv.teleport_command_success",
                finalCount, pool.displayName()), true);

        return count;
    }

    /**
     * 列出所有维度池及其包含的维度。
     */
    private static int executeList(CommandSourceStack source) {
        Collection<DimensionPool> pools = DimensionPoolSettings.getAllPools();

        if (pools.isEmpty()) {
            source.sendSuccess(() -> Component.translatable(
                    "youzaiworldcore.message.diminv.no_pools"), false);
            return 0;
        }

        source.sendSuccess(() -> Component.translatable(
                "youzaiworldcore.message.diminv.pool_list_header", pools.size()), false);

        for (DimensionPool pool : pools) {
            source.sendSuccess(() -> Component.literal(DimensionPoolSettings.formatPoolInfo(pool)), false);
        }

        return pools.size();
    }
}
