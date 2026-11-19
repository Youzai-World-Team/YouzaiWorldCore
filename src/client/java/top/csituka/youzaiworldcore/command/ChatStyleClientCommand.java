package top.csituka.youzaiworldcore.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.network.chat.Component;
import top.csituka.youzaiworldcore.client.chat.ChatStyleMode;
import top.csituka.youzaiworldcore.client.config.ChatStyleSettings;
import top.csituka.youzaiworldcore.util.DebugLogger;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal;

/** 本机聊天配色命令，无需服务端权限；切换后立即重绘已有聊天记录。 */
@SuppressWarnings("null")
public final class ChatStyleClientCommand {
    private static final String MODULE = "ChatStyleClientCommand";

    private ChatStyleClientCommand() {
    }

    /** 注册查询和 {@code plain} / {@code gradient} 两个可补全的配色名称。 */
    public static void register() {
        DebugLogger.CLIENT.entering(MODULE, "register");
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, context) -> dispatcher.register(
                literal("yzwc").then(styleNode(
                        command -> {
                            command.getSource().sendFeedback(Component.translatable(
                                    "chat.youzaiworldcore.style.current", ChatStyleSettings.getMode().commandName()));
                            return Command.SINGLE_SUCCESS;
                        }, command -> setStyle(command.getSource(), ChatStyleMode.PLAIN),
                        command -> setStyle(command.getSource(), ChatStyleMode.GRADIENT))
                )));
        DebugLogger.CLIENT.debug(MODULE, "已注册 /yzwc chat_style plain / gradient");
        DebugLogger.CLIENT.exiting(MODULE, "register");
    }

    /**
     * 在 Fabric 合并完成后补上展示用子树，修复服务端已有同名根时子节点未接入的问题。
     * 此树只供原版补全使用，实际执行仍由客户端命令树处理。
     */
    public static void addCompletionNodes(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        Command<FabricClientCommandSource> displayOnly = context -> 0;
        dispatcher.register(literal("yzwc").then(styleNode(displayOnly, displayOnly, displayOnly)));
        DebugLogger.CLIENT.debug(MODULE, "已接入 /yzwc chat_style 的原版命令补全树");
    }

    private static LiteralArgumentBuilder<FabricClientCommandSource> styleNode(Command<FabricClientCommandSource> query,
            Command<FabricClientCommandSource> plain, Command<FabricClientCommandSource> gradient) {
        return literal("chat_style").executes(query)
                .then(literal("plain").executes(plain))
                .then(literal("gradient").executes(gradient));
    }

    private static int setStyle(FabricClientCommandSource source, ChatStyleMode mode) {
        ChatStyleSettings.setMode(mode);
        source.getClient().gui.hud.getChat().rescaleChat();
        source.sendFeedback(Component.translatable("chat.youzaiworldcore.style.changed", mode.commandName()));
        return Command.SINGLE_SUCCESS;
    }
}
