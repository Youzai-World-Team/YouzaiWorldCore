package top.csituka.youzaiworldcore.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.BoolArgumentType;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.world.entity.player.Player;
import top.csituka.youzaiworldcore.network.DoubleDoorsTogglePayload;
import top.csituka.youzaiworldcore.util.DebugLogger;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal;

/**
 * 双开门功能客户端命令：{@code /yzwc function double_doors [true|false]}。
 * <p>
 * 该命令<b>仅做解析与转发</b>：客户端并不掌握权威开关状态
 * （{@code DoubleDoorsState} 由服务端持有），故通过
 * {@link DoubleDoorsTogglePayload} 将请求发送给服务端执行。
 * </p>
 * <p>
 * 之所以需要独立的客户端命令，是因为 {@code /yzwc} 根命令已在客户端注册
 * （用于 {@code /yzwc settings}），导致客户端在解析阶段只认得客户端子树，
 * 直接把 {@code /yzwc function double_doors} 当作服务端命令解析会失败（卡在第 5 个字符）。
 * 改为客户端解析 + 数据包转发后，两种环境（单人 / 专用服务端）均可正常使用。
 * </p>
 */
public class DoubleDoorsClientCommand {

    private static final String MODULE = "DoubleDoorsClientCommand";

    public static void register() {
        DebugLogger.entering(MODULE, "register");

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, context) -> dispatcher.register(
                literal("yzwc")
                        .then(literal("function"))
                                .then(literal("double_doors"))
                                        // /yzwc function double_doors  -> 查询自身
                                        .executes(cmdContext -> {
                                            sendToggle(cmdContext.getSource().getPlayer(), null);
                                            return Command.SINGLE_SUCCESS;
                                        })
                                        // /yzwc function double_doors <true|false>  -> 设置自身
                                        .then(argument("enabled", BoolArgumentType.bool())
                                                .executes(cmdContext -> {
                                                    sendToggle(
                                                            cmdContext.getSource().getPlayer(),
                                                            BoolArgumentType.getBool(cmdContext, "enabled"));
                                                    return Command.SINGLE_SUCCESS;
                                                }))
        ));

        DebugLogger.info(MODULE, "客户端命令 /yzwc function double_doors 已注册");
        DebugLogger.exiting(MODULE, "register");
    }

    /** 向服务端发送双开门切换 / 查询数据包 */
    private static void sendToggle(Player player, Boolean enabled) {
        if (player == null) {
            DebugLogger.warn(MODULE, "sendToggle 跳过：玩家为 null（非玩家上下文）");
            return;
        }
        DebugLogger.info(MODULE, "发送双开门切换数据包：enabled=%s", enabled);
        ClientPlayNetworking.send(new DoubleDoorsTogglePayload(enabled));
    }
}
