package top.csituka.youzaiworldcore.dimensionalinventories;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import top.csituka.youzaiworldcore.luckperms.LuckPermsHelper;
import top.csituka.youzaiworldcore.util.DebugLogger;

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
@SuppressWarnings("null")
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
        DebugLogger.entering("WorldPoolCmd", "executeTeleport", "poolId=" + poolId + ", targets=" + targets.size());
        DimensionPool pool = DimensionPoolSettings.getPool(poolId);
        // 隐藏池（内部使用）对管理员命令不可见，视为不存在
        boolean hiddenPool = DimensionPoolSettings.isHiddenPool(poolId);
        if (pool == null || hiddenPool) {
            DebugLogger.branch("WorldPoolCmd", "pool found", false,
                    "poolId=" + poolId + " 不存在" + (hiddenPool ? "（隐藏池不可用）" : ""));
            source.sendFailure(Component.translatable(
                    "youzaiworldcore.message.diminv.pool_not_found", poolId));
            DebugLogger.exiting("WorldPoolCmd", "executeTeleport", "pool not found, return 0");
            return 0;
        }
        DebugLogger.branch("WorldPoolCmd", "pool found", true, "poolId=" + poolId + " -> " + pool.displayName());

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

        DebugLogger.info("WorldPoolCmd", "传送完成: %d/%d 玩家已传送到 %s", count, targets.size(), poolId);
        DebugLogger.exiting("WorldPoolCmd", "executeTeleport", "count=" + count);
        return count;
    }

    /**
     * 列出所有维度池及其包含的维度。
     */
    private static int executeList(CommandSourceStack source) {
        DebugLogger.entering("WorldPoolCmd", "executeList");
        Collection<DimensionPool> pools = DimensionPoolSettings.getAllPools();

        if (pools.isEmpty()) {
            DebugLogger.branch("WorldPoolCmd", "pools empty", true);
            source.sendSuccess(() -> Component.translatable(
                    "youzaiworldcore.message.diminv.no_pools"), false);
            DebugLogger.exiting("WorldPoolCmd", "executeList", "no pools, return 0");
            return 0;
        }
        DebugLogger.branch("WorldPoolCmd", "pools empty", false, "found " + pools.size() + " pools");

        source.sendSuccess(() -> Component.translatable(
                "youzaiworldcore.message.diminv.pool_list_header", pools.size()), false);

        for (DimensionPool pool : pools) {
            source.sendSuccess(() -> Component.literal(DimensionPoolSettings.formatPoolInfo(pool)), false);
        }

        DebugLogger.exiting("WorldPoolCmd", "executeList", "listed " + pools.size() + " pools");
        return pools.size();
    }
}
