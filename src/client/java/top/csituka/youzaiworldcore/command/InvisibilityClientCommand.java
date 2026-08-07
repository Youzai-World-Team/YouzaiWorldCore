package top.csituka.youzaiworldcore.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.BoolArgumentType;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.world.entity.player.Player;
import top.csituka.youzaiworldcore.network.InvisibilityPayload;
import top.csituka.youzaiworldcore.util.DebugLogger;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal;

/**
 * 隐身功能客户端命令：{@code /yzwc function invisibility [true|false]}。
 * <p>
 * 省略参数 = 查询当前隐身状态，带 true/false = 设置。
 * 与双开门命令同理，该命令仅做解析与转发。
 * </p>
 */
public class InvisibilityClientCommand {

    private static final String MODULE = "InvisibilityClientCommand";

    @SuppressWarnings("null")
    public static void register() {
        DebugLogger.entering(MODULE, "register");

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, context) -> dispatcher.register(
                literal("yzwc")
                        .then(literal("function")
                                .then(literal("invisibility")
                                        // /yzwc function invisibility  -> 查询
                                        .executes(cmdContext -> {
                                            sendToggle(cmdContext.getSource().getPlayer(), null);
                                            return Command.SINGLE_SUCCESS;
                                        })
                                        // /yzwc function invisibility <true|false>  -> 设置
                                        .then(argument("enabled", BoolArgumentType.bool())
                                                .executes(cmdContext -> {
                                                    sendToggle(
                                                            cmdContext.getSource().getPlayer(),
                                                            BoolArgumentType.getBool(cmdContext, "enabled"));
                                                    return Command.SINGLE_SUCCESS;
                                                })
                                        )
                                )
                        )
        ));

        DebugLogger.info(MODULE, "客户端命令 /yzwc function invisibility 已注册");
        DebugLogger.exiting(MODULE, "register");
    }

    /** 向服务端发送隐身切换 / 查询数据包 */
    private static void sendToggle(Player player, Boolean enabled) {
        if (player == null) {
            DebugLogger.warn(MODULE, "sendToggle 跳过：玩家为 null（非玩家上下文）");
            return;
        }
        DebugLogger.info(MODULE, "发送隐身切换数据包：enabled=%s", enabled);
        ClientPlayNetworking.send(new InvisibilityPayload(enabled));
    }
}
