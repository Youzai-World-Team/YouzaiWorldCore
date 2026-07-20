package top.csituka.youzaiworldcore.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import top.csituka.youzaiworldcore.network.PetCommandPayload;
import top.csituka.youzaiworldcore.util.DebugLogger;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal;

/**
 * 宠物管理客户端命令：{@code /yzwc pet <args...>}。
 * <p>
 * 仅做参数解析与转发：客户端将 {@code pet} 之后的全部参数字符串
 * 通过 {@link PetCommandPayload} C2S 数据包发送给服务端执行。
 * 服务端持有 {@code PetCommand} 的完整 Brigadier 命令树与权限验证逻辑。
 * </p>
 */
public class PetClientCommand {

    private static final String MODULE = "PetClientCommand";

    @SuppressWarnings("null")
    public static void register() {
        DebugLogger.entering(MODULE, "register");

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, context) -> dispatcher.register(
                literal("yzwc")
                        .then(literal("pet")
                                .then(argument("args", StringArgumentType.greedyString())
                                        .executes(cmdContext -> {
                                            String args = StringArgumentType.getString(cmdContext, "args");
                                            DebugLogger.info(MODULE, "转发宠物命令: args=%s", args);
                                            ClientPlayNetworking.send(new PetCommandPayload(args));
                                            return Command.SINGLE_SUCCESS;
                                        })
                                )
                        )
        ));

        DebugLogger.info(MODULE, "客户端命令 /yzwc pet 已注册 (greedy forwarding)");
        DebugLogger.exiting(MODULE, "register");
    }
}
