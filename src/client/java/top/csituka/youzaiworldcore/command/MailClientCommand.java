package top.csituka.youzaiworldcore.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import top.csituka.youzaiworldcore.network.*;
import top.csituka.youzaiworldcore.util.DebugLogger;

import java.util.UUID;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal;

/**
 * 邮件系统客户端命令：{@code /yzwc mail <sub> [args]}。
 * <p>
 * 仅做参数解析与转发：所有子命令均通过 C2S 数据包发送给服务端执行。
 * </p>
 */
@SuppressWarnings("null")
public class MailClientCommand {

    private static final String MODULE = "MailClientCommand";

    public static void register() {
        DebugLogger.entering(MODULE, "register");

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, context) -> dispatcher.register(
                literal("yzwc")
                        .then(literal("mail")
                                // send_mail — 打开发布 GUI
                                .then(literal("send_mail")
                                        .executes(cmdContext -> {
                                            DebugLogger.info(MODULE, "转发命令: send_mail");
                                            ClientPlayNetworking.send(new MailComposeOpenPayload());
                                            return Command.SINGLE_SUCCESS;
                                        })
                                )
                                // sent — 打开已发送邮件列表
                                .then(literal("sent")
                                        .executes(cmdContext -> {
                                            DebugLogger.info(MODULE, "转发命令: sent");
                                            ClientPlayNetworking.send(new MailSentListRequestPayload());
                                            return Command.SINGLE_SUCCESS;
                                        })
                                )
                                // recall <mailId> — 撤回邮件
                                .then(literal("recall")
                                        .then(argument("mailId", StringArgumentType.word())
                                                .executes(cmdContext -> {
                                                    String mailIdStr = StringArgumentType.getString(cmdContext, "mailId");
                                                    DebugLogger.info(MODULE, "转发命令: recall mailId=%s", mailIdStr);
                                                    try {
                                                        UUID mailId = UUID.fromString(mailIdStr);
                                                        ClientPlayNetworking.send(new MailRecallPayload(mailId));
                                                    } catch (IllegalArgumentException e) {
                                                        DebugLogger.warn(MODULE, "无效的 mailId: %s", mailIdStr);
                                                    }
                                                    return Command.SINGLE_SUCCESS;
                                                })
                                        )
                                )
                                // purge [player|all] — 清理过期邮件
                                .then(literal("purge")
                                        .executes(cmdContext -> {
                                            DebugLogger.info(MODULE, "转发命令: purge (default all)");
                                            ClientPlayNetworking.send(new MailPurgePayload("all"));
                                            return Command.SINGLE_SUCCESS;
                                        })
                                        .then(argument("target", StringArgumentType.greedyString())
                                                .executes(cmdContext -> {
                                                    String target = StringArgumentType.getString(cmdContext, "target");
                                                    DebugLogger.info(MODULE, "转发命令: purge target=%s", target);
                                                    ClientPlayNetworking.send(new MailPurgePayload(target));
                                                    return Command.SINGLE_SUCCESS;
                                                })
                                        )
                                )
                                // list [player] — 查看信箱
                                .then(literal("list")
                                        .executes(cmdContext -> {
                                            DebugLogger.info(MODULE, "转发命令: list (self)");
                                            ClientPlayNetworking.send(new MailListRequestPayload(""));
                                            return Command.SINGLE_SUCCESS;
                                        })
                                        .then(argument("player", StringArgumentType.greedyString())
                                                .executes(cmdContext -> {
                                                    String playerName = StringArgumentType.getString(cmdContext, "player");
                                                    DebugLogger.info(MODULE, "转发命令: list player=%s", playerName);
                                                    ClientPlayNetworking.send(new MailListRequestPayload(playerName));
                                                    return Command.SINGLE_SUCCESS;
                                                })
                                        )
                                )
                        )
        ));

        DebugLogger.info(MODULE, "客户端命令 /yzwc mail 已注册 (send_mail / sent / recall / purge / list)");
        DebugLogger.exiting(MODULE, "register");
    }
}
