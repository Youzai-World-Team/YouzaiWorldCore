package top.csituka.youzaiworldcore.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import top.csituka.youzaiworldcore.mail.MailManager;
import top.csituka.youzaiworldcore.mail.MailPermissionHelper;
import top.csituka.youzaiworldcore.util.DebugLogger;

import java.util.UUID;

/**
 * 后台邮件同步命令：{@code /yzwc mail pull <mailId>}。
 * <p>
 * Api 后台发布邮件后通过 MCSM 控制台执行本命令，Minecraft 服务端再主动从 Api
 * 拉取该邮件并即时推送给在线收件人。权限沿用邮件模块权限节点，默认 OP 4。
 * </p>
 */
@SuppressWarnings("null")
public final class MailCommand {

    private static final String MODULE = "MailCommand";

    private MailCommand() {
    }

    /** 注册 {@code /yzwc mail pull <mailId>}。 */
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        DebugLogger.entering(MODULE, "register");
        dispatcher.register(Commands.literal("yzwc")
                .then(Commands.literal("mail")
                        .then(Commands.literal("pull")
                                .requires(MailPermissionHelper::checkPermission)
                                .then(Commands.argument("mailId", StringArgumentType.word())
                                        .executes(MailCommand::pullMail)))));
        DebugLogger.exiting(MODULE, "register");
    }

    private static int pullMail(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        String rawMailId = StringArgumentType.getString(context, "mailId");
        UUID mailId;
        try {
            mailId = UUID.fromString(rawMailId);
        } catch (IllegalArgumentException ignored) {
            source.sendFailure(Component.literal("邮件 ID 格式无效"));
            return 0;
        }

        DebugLogger.info(MODULE, "%s 请求拉取后台邮件: mailId=%s", source.getTextName(), mailId);
        source.sendSuccess(() -> Component.literal("正在从 Api 拉取邮件 " + mailId), false);
        MailManager.pullAndPushMail(source.getServer(), mailId, result -> {
            if (!result.success()) {
                source.sendFailure(Component.literal("邮件拉取失败：" + result.message()));
                return;
            }
            source.sendSuccess(() -> Component.literal(
                    "邮件拉取完成，已即时推送给 " + result.pushedRecipients() + " 位在线收件人"), false);
        });
        return 1;
    }
}
