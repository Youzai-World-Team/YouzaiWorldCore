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
import java.time.LocalDate;
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
                .then(Commands.argument("date", StringArgumentType.word())
                        .executes(context -> restore(context.getSource(), module,
                                StringArgumentType.getString(context, "date"))));
    }

    private static int list(CommandSourceStack source, BackupModule module) {
        try {
            List<LocalDate> dates = PlayerDataBackup.dates(source.getServer(), module);
            if (dates.isEmpty()) {
                source.sendFailure(Component.translatable(MESSAGE + "empty", target(module)));
                return 0;
            }
            @SuppressWarnings("null")
            String display = String.join(", ", dates.stream().limit(20).map(LocalDate::toString).toList());
            source.sendSuccess(() -> Component.translatable(MESSAGE + "list", target(module), display), false);
            return dates.size();
        } catch (IOException | RuntimeException e) {
            return failure(source, e);
        }
    }

    private static int restore(CommandSourceStack source, BackupModule module, String requestedDate) {
        try {
            List<LocalDate> dates = PlayerDataBackup.dates(source.getServer(), module);
            if (dates.isEmpty()) {
                source.sendFailure(Component.translatable(MESSAGE + "empty", target(module)));
                return 0;
            }
            LocalDate date = requestedDate == null ? dates.getFirst() : LocalDate.parse(requestedDate);
            if (!dates.contains(date)) {
                source.sendFailure(Component.translatable(MESSAGE + "missing", target(module), date.toString()));
                return 0;
            }
            PlayerDataBackup.queueRestore(source.getServer(), module, date);
            source.sendSuccess(() -> Component.translatable(MESSAGE + "queued", target(module), date.toString()),
                    false);
            LOGGER.notice("管理员 {} 已安排于下次启动还原 {} 的玩家数据备份（{}）",
                    source.getTextName(), date, target(module));
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
