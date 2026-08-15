package top.csituka.youzaiworldcore.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;
import java.net.URI;
import top.csituka.youzaiworldcore.luckperms.LuckPermsHelper;
import top.csituka.youzaiworldcore.update.UpdateChecker;
import top.csituka.youzaiworldcore.config.UpdateCheckerConfig;
import top.csituka.youzaiworldcore.util.DebugLogger;

/**
 * 更新检查命令：{@code /yzwc update [check]}
 * <p>
 * 触发一次即时更新检查并将结果反馈给执行者（普通更新 INFO、强制更新 WARN 提示，
 * 并附带可点击的下载页链接）。
 * </p>
 * <p>权限：需要 {@code youzaiworldcore.command.update}，默认 OP 4。</p>
 */
@SuppressWarnings("null")
public class UpdateCommand {

    private static final String MODULE = "UpdateCommand";

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        DebugLogger.entering(MODULE, "register");
        dispatcher.register(Commands.literal("yzwc")
                .then(Commands.literal("update")
                        .requires(source -> LuckPermsHelper.checkPermission(
                                source, LuckPermsHelper.PERMISSION_UPDATE_CHECK, Commands.LEVEL_ADMINS))
                        .executes(UpdateCommand::checkUpdate)
                        .then(Commands.literal("check").executes(UpdateCommand::checkUpdate))
                )
        );
        DebugLogger.exiting(MODULE, "register");
    }

    private static int checkUpdate(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        DebugLogger.entering(MODULE, "checkUpdate", "source=" + source.getTextName());

        if (!UpdateCheckerConfig.isEnabled()) {
            source.sendFailure(Component.translatable("youzaiworldcore.message.command.update.disabled"));
            DebugLogger.exiting(MODULE, "checkUpdate", "0 (disabled)");
            return 0;
        }

        source.sendSuccess(() -> Component.translatable("youzaiworldcore.message.command.update.checking"), false);
        CommandSourceStack finalSource = source;

        UpdateChecker.checkAsync().thenAccept(result -> finalSource.getServer().execute(() -> {
            if (result == null || result.errorMessage() != null) {
                finalSource.sendFailure(Component.translatable(
                        "youzaiworldcore.message.command.update.failed",
                        result == null ? "unknown" : result.errorMessage()));
                return;
            }
            if (!result.updateAvailable()) {
                finalSource.sendSuccess(() -> Component.translatable(
                        "youzaiworldcore.message.command.update.latest", result.currentVersion()), true);
                return;
            }

            finalSource.sendSuccess(() -> Component.translatable(result.forcedUpdate()
                            ? "youzaiworldcore.message.command.update.available_forced"
                            : "youzaiworldcore.message.command.update.available",
                    result.currentVersion(), result.latestVersion(),
                    result.latestType() == null ? "" : result.latestType()), true);

            // 可点击的下载页链接
            Component link = Component.literal(result.downloadUrl())
                    .withStyle(Style.EMPTY
                            .withColor(ChatFormatting.GREEN)
                            .withClickEvent(new ClickEvent.OpenUrl(URI.create(result.downloadUrl())))
                            .withHoverEvent(new HoverEvent.ShowText(
                                    Component.literal("点击打开下载页"))));
            finalSource.sendSuccess(() -> link, true);
        }));

        DebugLogger.exiting(MODULE, "checkUpdate", "submitted");
        return 1;
    }
}
