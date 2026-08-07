package top.csituka.youzaiworldcore.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import top.csituka.youzaiworldcore.util.DebugLogger;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal;

/**
 * 服务端 {@code /yzwc} 子命令的<b>客户端占位镜像</b>注册器。
 * <p>
 * <b>为什么需要它：</b>Fabric 客户端命令机制
 * （{@code ClientCommandInternals.executeCommand}）在玩家发送任何
 * {@code /yzwc ...} 时，会先用<b>纯客户端命令树</b>
 * （{@code ClientCommandInternals.getActiveDispatcher()}）解析。由于
 * {@code /yzwc} 根命令已在客户端注册（用于 {@code /yzwc settings} /
 * {@code /yzwc function ...} / {@code /yzwc mail ...} 等本地命令），
 * 客户端树中<b>缺少服务端专属子命令</b>（如 {@code event}、
 * {@code update}、{@code teleport_world} 等）时，Brigadier 会抛出
 * {@code dispatcherUnknownArgument}（"错误的命令参数 at position 5"），
 * 该异常类型<b>不在</b> Fabric 的忽略白名单
 * （{@code dispatcherUnknownCommand} / {@code dispatcherParseException}）内，
 * 导致客户端<b>拦截命令并报错</b>，命令永远无法到达服务端。
 * </p>
 * <p>
 * <b>修复原理：</b>将服务端 {@code /yzwc} 的全部专属子命令在客户端注册为
 * <b>无 {@code executes()} 的占位节点</b>（参数型子命令挂
 * {@code greedyString} 兜底参数，吞掉任意后续参数）。这样客户端解析必然成功；
 * 由于占位节点没有可执行命令，Brigadier 在执行阶段抛出
 * {@code dispatcherUnknownCommand}（无 context 版本），该异常类型在 Fabric
 * 忽略白名单内 → {@code executeCommand} 返回 {@code false} → 命令被<b>原样发送
 * 给服务端</b>，由服务端权威命令树解析执行。
 * </p>
 * <p>
 * <b>维护约定：</b>服务端 {@code YouzaiworldCore} / {@code WorldPoolCommand} /
 * {@code AccountCommands} / {@code ReloadCommand} / {@code TeleportAnchorCommand} /
 * {@code EventCommand} / {@code UpdateCommand} / {@code StatsManager} 中新增
 * {@code /yzwc} 一级子命令时，需同步在此镜像，否则新子命令会被客户端拦截。
 * </p>
 */
@SuppressWarnings("null")
public final class YzwcServerMirrorCommand {

    private static final String MODULE = "YzwcServerMirrorCommand";

    /** 占位参数名：吞掉子命令之后的所有参数（转发给服务端解析） */
    private static final String ARGS_ARG = "args";

    private YzwcServerMirrorCommand() {
    }

    /**
     * 注册服务端 {@code /yzwc} 子命令的客户端占位镜像。
     * <p>
     * 注册时机在 {@code Client.onInitializeClient} 中、现有客户端本地命令
     * （settings / function / pet / mail）之后，Brigadier 会自动与已有
     * {@code yzwc} 节点合并。
     * </p>
     */
    public static void register() {
        DebugLogger.entering(MODULE, "register");

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, context) -> {
            LiteralArgumentBuilder<FabricClientCommandSource> root = literal("yzwc");

            // ===== 参数型一级子命令：一级挂 greedyString 兜底（后续参数整体转发） =====

            // /yzwc teleport_world <targets> <dimension> [x [y [z [yRot [xRot]]]]]
            root.then(literal("teleport_world").then(greedyArgs()));
            // /yzwc open_menu <menu_name> [target]
            root.then(literal("open_menu").then(greedyArgs()));
            // /yzwc status <player> list|delete | /yzwc status rank_export <day|week|month|year|all> [name]
            // 下一级是 <player> 参数而非字面量，故挂一级兜底
            root.then(literal("status").then(greedyArgs()));

            // ===== 字面量型一级子命令：镜像二级字面量，二级挂 greedyString 兜底 =====

            // /yzwc world_pool teleport <targets> <dimension_pool> | /yzwc world_pool list
            root.then(literal("world_pool")
                    .then(literal("teleport").then(greedyArgs()))
                    .then(literal("list").then(greedyArgs())));

            // /yzwc account register|login|logout|deactivate|change_password|mgr ...
            root.then(literal("account")
                    .then(literal("register").then(greedyArgs()))
                    .then(literal("login").then(greedyArgs()))
                    .then(literal("logout").then(greedyArgs()))
                    .then(literal("deactivate").then(greedyArgs()))
                    .then(literal("change_password").then(greedyArgs()))
                    .then(literal("mgr").then(greedyArgs())));

            // /yzwc reload（无参数）
            root.then(literal("reload"));

            // /yzwc teleport_anchor list [player]
            root.then(literal("teleport_anchor")
                    .then(literal("list").then(greedyArgs())));

            // /yzwc event naturally_charged_creepers enable [bool] | ... settings chance [double]
            // | /yzwc event trial_vault enable [bool] | /yzwc event laowu enable [bool]
            // | /yzwc event laowu settings cd [seconds]
            // | /yzwc event death_sound enable [bool] | jukebox_loop | baby_zombie_weak
            // | wither_skull_drop | trident_void_protect
            root.then(literal("event")
                    .then(literal("naturally_charged_creepers").then(greedyArgs()))
                    .then(literal("trial_vault").then(greedyArgs()))
                    .then(literal("laowu").then(greedyArgs()))
                    .then(literal("death_sound").then(greedyArgs()))
                    .then(literal("jukebox_loop").then(greedyArgs()))
                    .then(literal("baby_zombie_weak").then(greedyArgs()))
                    .then(literal("wither_skull_drop").then(greedyArgs()))
                    .then(literal("trident_void_protect").then(greedyArgs())));

            // /yzwc function ladder_extend_downward|... [true|false]
            root.then(literal("function")
                    .then(literal("ladder_extend_downward").then(greedyArgs()))
                    .then(literal("crop_xp_drop").then(greedyArgs()))
                    .then(literal("tool_info_overlay").then(greedyArgs()))
                    .then(literal("block_animation").then(greedyArgs()))
                    .then(literal("crafting_sound").then(greedyArgs()))
                    .then(literal("item_sparkle").then(greedyArgs())));

            // /yzwc update [check]
            root.then(literal("update")
                    .then(literal("check").then(greedyArgs())));

            // /yzwc afk | /yzwc afk status [player] | /yzwc afk list
            // | /yzwc afk settings <key> <value>
            // 无参 /yzwc afk 也会因占位节点无 executes 走 dispatcherUnknownCommand 转发
            root.then(literal("afk").then(greedyArgs()));

            dispatcher.register(root);
            DebugLogger.info(MODULE,
                    "服务端 /yzwc 子命令占位镜像已注册：teleport_world / open_menu / status / world_pool / account / reload / teleport_anchor / event / update / afk");
        });

        DebugLogger.exiting(MODULE, "register");
    }

    /**
     * 构造 greedyString 兜底参数节点（无 executes）。
     * <p>
     * 该节点会消费当前命令之后的所有剩余输入，使解析必然成功；由于没有
     * {@code executes()}，执行阶段会抛出 {@code dispatcherUnknownCommand}，
     * 触发 Fabric 客户端命令的"转发到服务端"路径。
     * </p>
     *
     * @return greedyString 参数构建器
     */
    private static RequiredArgumentBuilder<FabricClientCommandSource, String> greedyArgs() {
        return argument(ARGS_ARG, StringArgumentType.greedyString());
    }
}
