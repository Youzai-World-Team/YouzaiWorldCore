package top.csituka.youzaiworldcore.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import top.csituka.youzaiworldcore.afk.AfkManager;
import top.csituka.youzaiworldcore.config.AfkConfig;
import top.csituka.youzaiworldcore.luckperms.LuckPermsHelper;
import top.csituka.youzaiworldcore.util.DebugLogger;

/**
 * AFK 管理命令：{@code /yzwc afk ...}
 * <p>
 * 子命令：
 * <ul>
 * <li>{@code afk} — 手动切换本人 AFK 状态（需
 * {@code youzaiworldcore.command.function.afk}，默认所有人可用）</li>
 * <li>{@code afk status [player]} — 查询本人（或指定玩家，需管理权限）AFK 状态</li>
 * <li>{@code afk list} — 列出所有 AFK 玩家（需管理权限）</li>
 * <li>{@code afk settings <key> <value>} — 运行时修改 AFK 配置（需管理权限）</li>
 * </ul>
 * 管理权限节点：{@code youzaiworldcore.command.admin.afk}，LP 缺失时回退 OP 4。
 * </p>
 */
@SuppressWarnings("null")
public class AfkCommand {

    private static final String MODULE = "AfkCommand";

    private AfkCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        DebugLogger.entering(MODULE, "register");

        dispatcher.register(Commands.literal("yzwc")
                .then(Commands.literal("afk")
                        .requires(src -> LuckPermsHelper.checkPermission(
                                src, LuckPermsHelper.PERMISSION_AFK_USE, Commands.LEVEL_ALL))
                        .executes(AfkCommand::toggle)
                        // /yzwc afk status [player]
                        .then(Commands.literal("status")
                                .executes(AfkCommand::querySelf)
                                .then(Commands.argument("player", EntityArgument.player())
                                        .requires(src -> LuckPermsHelper.checkPermission(
                                                src, LuckPermsHelper.PERMISSION_AFK_ADMIN,
                                                Commands.LEVEL_ADMINS))
                                        .executes(AfkCommand::queryOther)))
                        // /yzwc afk list
                        .then(Commands.literal("list")
                                .requires(src -> LuckPermsHelper.checkPermission(
                                        src, LuckPermsHelper.PERMISSION_AFK_ADMIN,
                                        Commands.LEVEL_ADMINS))
                                .executes(AfkCommand::list))
                        // /yzwc afk settings <key> <value>
                        .then(Commands.literal("settings")
                                .requires(src -> LuckPermsHelper.checkPermission(
                                        src, LuckPermsHelper.PERMISSION_AFK_ADMIN,
                                        Commands.LEVEL_ADMINS))
                                .then(Commands.argument("key", StringArgumentType.word())
                                        .then(Commands.argument("value", StringArgumentType.greedyString())
                                                .executes(AfkCommand::setSetting))))));

        DebugLogger.exiting(MODULE, "register");
    }

    // ==================== 手动切换 ====================

    private static int toggle(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        DebugLogger.entering(MODULE, "toggle");
        ServerPlayer player = ctx.getSource().getPlayerOrException();

        if (!AfkConfig.isManualToggleEnabled()) {
            player.sendSystemMessage(Component.translatable(
                    "youzaiworldcore.message.afk.command.manual_disabled"));
            DebugLogger.exiting(MODULE, "toggle", "manual-disabled");
            return 0;
        }
        boolean nowAfk = AfkManager.toggleManual(player);
        player.sendSystemMessage(Component.translatable(nowAfk
                ? "youzaiworldcore.message.afk.command.toggle_entered"
                : "youzaiworldcore.message.afk.command.toggle_left"));
        DebugLogger.info(MODULE, "%s 手动切换 AFK → %s",
                player.getName().getString(), nowAfk);
        DebugLogger.exiting(MODULE, "toggle", "nowAfk=" + nowAfk);
        return 1;
    }

    // ==================== 查询 ====================

    private static int querySelf(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        DebugLogger.entering(MODULE, "querySelf");
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        boolean afk = AfkManager.isAfk(player);
        player.sendSystemMessage(Component.translatable(afk
                ? "youzaiworldcore.message.afk.command.status_self_afk"
                : "youzaiworldcore.message.afk.command.status_self_active"));
        DebugLogger.exiting(MODULE, "querySelf", "afk=" + afk);
        return 1;
    }

    private static int queryOther(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        DebugLogger.entering(MODULE, "queryOther");
        ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
        boolean afk = AfkManager.isAfk(target);
        ctx.getSource().sendSuccess(() -> Component.translatable(afk
                        ? "youzaiworldcore.message.afk.command.status_other_afk"
                        : "youzaiworldcore.message.afk.command.status_other_active",
                target.getName()), false);
        DebugLogger.exiting(MODULE, "queryOther", "target=" + target.getName().getString() + ", afk=" + afk);
        return 1;
    }

    private static int list(CommandContext<CommandSourceStack> ctx) {
        DebugLogger.entering(MODULE, "list");
        var afkUuids = AfkManager.getAfkPlayers();
        var server = ctx.getSource().getServer();
        var afkPlayers = new java.util.ArrayList<ServerPlayer>();
        for (var uuid : afkUuids) {
            ServerPlayer p = server.getPlayerList().getPlayer(uuid);
            if (p != null) {
                afkPlayers.add(p);
            }
        }
        if (afkPlayers.isEmpty()) {
            ctx.getSource().sendSuccess(() -> Component.translatable(
                    "youzaiworldcore.message.afk.command.list_empty"), false);
        } else {
            ctx.getSource().sendSuccess(() -> Component.translatable(
                    "youzaiworldcore.message.afk.command.list_header", afkPlayers.size()), false);
            for (ServerPlayer p : afkPlayers) {
                ServerPlayer fixed = p;
                ctx.getSource().sendSuccess(() -> Component.translatable(
                        "youzaiworldcore.message.afk.command.list_entry", fixed.getName()), false);
            }
        }
        DebugLogger.info(MODULE, "%s 查询 AFK 列表，共 %d 人",
                ctx.getSource().getTextName(), afkPlayers.size());
        DebugLogger.exiting(MODULE, "list");
        return 1;
    }

    // ==================== 配置修改 ====================

    private static int setSetting(CommandContext<CommandSourceStack> ctx) {
        DebugLogger.entering(MODULE, "setSetting");
        String key = StringArgumentType.getString(ctx, "key");
        String value = StringArgumentType.getString(ctx, "value");
        DebugLogger.info(MODULE, "修改 AFK 配置: %s = %s（执行者 %s）",
                key, value, ctx.getSource().getTextName());

        try {
            switch (key) {
                case "enabled" -> {
                    boolean v = parseBool(value);
                    AfkConfig.setEnabled(v);
                    ctx.getSource().sendSuccess(() -> Component.translatable(
                            "youzaiworldcore.message.afk.command.settings_updated", key, v), true);
                }
                case "detect_mode" -> {
                    AfkConfig.DetectMode mode = AfkConfig.DetectMode.valueOf(value.toUpperCase());
                    AfkConfig.setDetectMode(mode);
                    ctx.getSource().sendSuccess(() -> Component.translatable(
                            "youzaiworldcore.message.afk.command.settings_updated", key, mode), true);
                }
                case "threshold" -> {
                    int seconds = Integer.parseInt(value);
                    AfkConfig.setThresholdSeconds(seconds);
                    ctx.getSource().sendSuccess(() -> Component.translatable(
                            "youzaiworldcore.message.afk.command.settings_updated", key, seconds), true);
                }
                case "tab_prefix" -> {
                    boolean v = parseBool(value);
                    AfkConfig.setTabPrefixEnabled(v);
                    ctx.getSource().sendSuccess(() -> Component.translatable(
                            "youzaiworldcore.message.afk.command.settings_updated", key, v), true);
                }
                case "broadcast" -> {
                    boolean v = parseBool(value);
                    AfkConfig.setBroadcastEnabled(v);
                    ctx.getSource().sendSuccess(() -> Component.translatable(
                            "youzaiworldcore.message.afk.command.settings_updated", key, v), true);
                }
                case "invulnerable" -> {
                    boolean v = parseBool(value);
                    AfkConfig.setInvulnerableEnabled(v);
                    ctx.getSource().sendSuccess(() -> Component.translatable(
                            "youzaiworldcore.message.afk.command.settings_updated", key, v), true);
                }
                case "auto_kick" -> {
                    int seconds = Integer.parseInt(value);
                    AfkConfig.setAutoKickSeconds(seconds);
                    ctx.getSource().sendSuccess(() -> Component.translatable(
                            "youzaiworldcore.message.afk.command.settings_updated", key, seconds), true);
                }
                case "manual_toggle" -> {
                    boolean v = parseBool(value);
                    AfkConfig.setManualToggleEnabled(v);
                    ctx.getSource().sendSuccess(() -> Component.translatable(
                            "youzaiworldcore.message.afk.command.settings_updated", key, v), true);
                }
                default -> {
                    ctx.getSource().sendFailure(Component.translatable(
                            "youzaiworldcore.message.afk.command.settings_unknown_key", key));
                    DebugLogger.exiting(MODULE, "setSetting", "unknown-key");
                    return 0;
                }
            }
        } catch (IllegalArgumentException e) {
            ctx.getSource().sendFailure(Component.translatable(
                    "youzaiworldcore.message.afk.command.settings_invalid_value", key, value));
            DebugLogger.warn(MODULE, "配置值非法: key=%s, value=%s", key, value);
            DebugLogger.exiting(MODULE, "setSetting", "invalid-value");
            return 0;
        }

        DebugLogger.exiting(MODULE, "setSetting", "1");
        return 1;
    }

    private static boolean parseBool(String value) {
        return switch (value.toLowerCase()) {
            case "true", "1", "yes", "on" -> true;
            case "false", "0", "no", "off" -> false;
            default -> throw new IllegalArgumentException("invalid boolean: " + value);
        };
    }
}
