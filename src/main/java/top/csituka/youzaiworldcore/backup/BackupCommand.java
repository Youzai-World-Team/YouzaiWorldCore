package top.csituka.youzaiworldcore.backup;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import top.csituka.youzaiworldcore.luckperms.LuckPermsHelper;
import top.csituka.youzaiworldcore.util.DebugLogger;

import java.io.IOException;
import java.util.List;

public final class BackupCommand {
    private static final DebugLogger.ModuleLogger LOGGER = DebugLogger.SERVER.module("BackupCommand");
    private static final String MESSAGE = "youzaiworldcore.message.backup.";

    private BackupCommand() {
    }

    @SuppressWarnings("null")
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        var list = Commands.literal("list").executes(context -> list(context.getSource(), null));
        var restore = Commands.literal("restore").then(restoreTarget("all", null));
        for (BackupModule module : BackupModule.values()) {
            list.then(Commands.literal(module.id).executes(context -> list(context.getSource(), module)));
            restore.then(restoreTarget(module.id, module));
        }
        dispatcher.register(Commands.literal("yzwc")
                .then(Commands.literal("backup")
                        .requires(source -> source.isPlayer()
                                ? LuckPermsHelper.checkPermission(source, LuckPermsHelper.PERMISSION_BACKUP_ADMIN,
                                        Commands.LEVEL_ADMINS)
                                : Commands.LEVEL_ADMINS.check(source.permissions()))
                        .then(list)
                        .then(restore)));
    }

    @SuppressWarnings("null")
    private static LiteralArgumentBuilder<CommandSourceStack> restoreTarget(String name, BackupModule module) {
        return Commands.literal(name)
                .executes(context -> restore(context.getSource(), module, null))
                .then(Commands.argument("backup_id", StringArgumentType.word())
                        .executes(context -> restore(context.getSource(), module,
                                StringArgumentType.getString(context, "backup_id"))));
    }

    private static int list(CommandSourceStack source, BackupModule module) {
        try {
            List<BackupPoint> points = PlayerDataBackup.points(source.getServer(), module);
            if (points.isEmpty()) {
                source.sendFailure(Component.translatable(MESSAGE + "empty", target(module)));
                return 0;
            }
            @SuppressWarnings("null")
            String display = String.join(", ", points.stream().limit(20).map(BackupPoint::id).toList());
            source.sendSuccess(() -> Component.translatable(MESSAGE + "list", target(module), display), false);
            return points.size();
        } catch (IOException | RuntimeException e) {
            return failure(source, e);
        }
    }

    private static int restore(CommandSourceStack source, BackupModule module, String requestedId) {
        try {
            List<BackupPoint> points = PlayerDataBackup.points(source.getServer(), module);
            if (points.isEmpty()) {
                source.sendFailure(Component.translatable(MESSAGE + "empty", target(module)));
                return 0;
            }
            BackupPoint point = requestedId == null ? points.getFirst()
                    : points.stream().filter(backup -> requestedId.equals(backup.id())).findFirst().orElse(null);
            if (point == null) {
                source.sendFailure(Component.translatable(MESSAGE + "missing", target(module), requestedId));
                return 0;
            }
            PlayerDataBackup.queueRestore(source.getServer(), module, point);
            source.sendSuccess(() -> Component.translatable(MESSAGE + "queued", target(module), point.id()),
                    false);
            LOGGER.notice("管理员 {} 已安排于下次启动还原 {} 的玩家数据备份（{}）",
                    source.getTextName(), point.id(), target(module));
            return 1;
        } catch (IOException | RuntimeException e) {
            return failure(source, e);
        }
    }

    private static int failure(CommandSourceStack source, Exception error) {
        String reason = error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
        source.sendFailure(Component.translatable(MESSAGE + "failed", reason));
        LOGGER.error("玩家数据备份命令失败", error);
        return 0;
    }

    private static String target(BackupModule module) {
        return module == null ? "all" : module.id;
    }
}
