package top.csituka.youzaiworldcore.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.world.entity.player.Player;
import top.csituka.youzaiworldcore.network.ExperimentalFeaturePayload;
import top.csituka.youzaiworldcore.util.DebugLogger;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal;

/**
 * 实验性功能客户端命令：{@code /yzwc experimental_feature <id> [true|false [all|only <玩家>]]}。
 * <p>
 * 与双开门、隐身命令同理，该命令<b>仅做解析与转发</b>：客户端解析出
 * 功能 ID、作用域与目标玩家名，通过 {@link ExperimentalFeaturePayload}
 * 发送给服务端执行（服务端持有 {@code ExperimentalFeatures} 的权威状态）。
 * </p>
 * <p>
 * 注意：{@code only <玩家>} 在客户端使用字符串参数承载玩家名，
 * 由服务端按名解析为 {@code ServerPlayer}，避免在客户端侧解析实体参数带来的兼容性问题。
 * </p>
 */
public class ExperimentalFeatureClientCommand {

    private static final String MODULE = "ExperimentalFeatureClientCommand";

    @SuppressWarnings("null")
    public static void register() {
        DebugLogger.entering(MODULE, "register");

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, context) -> dispatcher.register(
                literal("yzwc")
                        .then(literal("experimental_feature"))
                                .then(argument("id", StringArgumentType.word())
                                        // /yzwc experimental_feature <id>  -> 查询
                                        .executes(cmdContext -> {
                                            send(cmdContext.getSource().getPlayer(),
                                                    StringArgumentType.getString(cmdContext, "id"),
                                                    ExperimentalFeaturePayload.MODE_QUERY, false, null);
                                            return Command.SINGLE_SUCCESS;
                                        })
                                        // /yzwc experimental_feature <id> <bool>  -> 为自己切换
                                        .then(argument("enabled", BoolArgumentType.bool())
                                                .executes(cmdContext -> {
                                                    send(cmdContext.getSource().getPlayer(),
                                                            StringArgumentType.getString(cmdContext, "id"),
                                                            ExperimentalFeaturePayload.MODE_SELF,
                                                            BoolArgumentType.getBool(cmdContext, "enabled"), null);
                                                    return Command.SINGLE_SUCCESS;
                                                })
                                                // /yzwc experimental_feature <id> <bool> all  -> 全服切换
                                                .then(literal("all"))
                                                // /yzwc experimental_feature <id> <bool> only <玩家>  -> 为指定玩家切换
                                                .then(literal("only"))
                                                        .then(argument("target", StringArgumentType.word())
                                                                .executes(cmdContext -> {
                                                                    send(cmdContext.getSource().getPlayer(),
                                                                            StringArgumentType.getString(cmdContext, "id"),
                                                                            ExperimentalFeaturePayload.MODE_ONLY,
                                                                            BoolArgumentType.getBool(cmdContext, "enabled"),
                                                                            StringArgumentType.getString(cmdContext, "target"));
                                                                    return Command.SINGLE_SUCCESS;
                                                                        }))
                        ))
));

        DebugLogger.info(MODULE, "客户端命令 /yzwc experimental_feature 已注册");
        DebugLogger.exiting(MODULE, "register");
    }

    /** 向服务端发送实验性功能命令数据包 */
    private static void send(Player player, String id, byte mode, boolean enabled, String targetName) {
        if (player == null) {
            DebugLogger.warn(MODULE, "send 跳过：玩家为 null（非玩家上下文）");
            return;
        }
        DebugLogger.info(MODULE, "发送实验性功能数据包：id=%s, mode=%d, enabled=%s, target=%s",
                id, mode, enabled, targetName);
        ClientPlayNetworking.send(new ExperimentalFeaturePayload(id, mode, enabled, targetName));
    }
}
