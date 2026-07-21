package top.csituka.youzaiworldcore.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import top.csituka.youzaiworldcore.config.ChargedCreeperConfig;
import top.csituka.youzaiworldcore.luckperms.LuckPermsHelper;
import top.csituka.youzaiworldcore.util.DebugLogger;

/**
 * 事件管理命令：{@code /yzwc event naturally_charged_creepers ...}
 * <p>
 * 子命令：
 * <ul>
 * <li>{@code enable [true|false]} — 开启 / 关闭天然带电苦力怕事件；省略参数则查询当前状态</li>
 * <li>{@code settings chance [double]} — 调整带电概率（0.0 ~ 1.0）；省略参数则查询当前概率</li>
 * </ul>
 * 权限：
 * <ul>
 * <li>查询（省略参数）需 {@code youzaiworldcore.command.event.query}，默认所有人可查询；</li>
 * <li>修改（带参数）需 {@code youzaiworldcore.command.event.set}，默认 OP 4。</li>
 * </ul>
 * 功能移植自 Serilum 的 Naturally Charged Creepers
 * </p>
 */
@SuppressWarnings("null")
public class EventCommand {

        private static final String MODULE = "EventCommand";
        private static final String EVENT_ID = "naturally_charged_creepers";

        public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
                DebugLogger.entering(MODULE, "register");

                // /yzwc event naturally_charged_creepers enable [true|false]
                var enableNode = Commands.literal("enable")
                                .requires(src -> LuckPermsHelper.checkPermission(
                                                src, LuckPermsHelper.PERMISSION_EVENT_QUERY, Commands.LEVEL_ALL))
                                .executes(EventCommand::queryEnable)
                                .then(Commands.argument("enabled", BoolArgumentType.bool())
                                                .requires(src -> LuckPermsHelper.checkPermission(
                                                                src, LuckPermsHelper.PERMISSION_EVENT_SET,
                                                                Commands.LEVEL_ADMINS))
                                                .executes(ctx -> setEnable(ctx,
                                                                BoolArgumentType.getBool(ctx, "enabled"))));

                // /yzwc event naturally_charged_creepers settings chance [double]
                var chanceNode = Commands.literal("chance")
                                .requires(src -> LuckPermsHelper.checkPermission(
                                                src, LuckPermsHelper.PERMISSION_EVENT_QUERY, Commands.LEVEL_ALL))
                                .executes(EventCommand::queryChance)
                                .then(Commands.argument("chance", DoubleArgumentType.doubleArg())
                                                .requires(src -> LuckPermsHelper.checkPermission(
                                                                src, LuckPermsHelper.PERMISSION_EVENT_SET,
                                                                Commands.LEVEL_ADMINS))
                                                .executes(ctx -> setChance(ctx,
                                                                DoubleArgumentType.getDouble(ctx, "chance"))));

                var settingsNode = Commands.literal("settings")
                                .then(chanceNode);

                dispatcher.register(Commands.literal("yzwc")
                                .then(Commands.literal("event")
                                                .then(Commands.literal(EVENT_ID)
                                                                .then(enableNode)
                                                                .then(settingsNode))));

                DebugLogger.exiting(MODULE, "register");
        }

        // ==================== enable：查询 / 设置 ====================

        private static int queryEnable(CommandContext<CommandSourceStack> ctx) {
                DebugLogger.entering(MODULE, "queryEnable");
                boolean enabled = ChargedCreeperConfig.isEnabled();
                ctx.getSource().sendSuccess(() -> Component.translatable(enabled
                                ? "youzaiworldcore.message.command.event.ncc.query_enable_enabled"
                                : "youzaiworldcore.message.command.event.ncc.query_enable_disabled"),
                                false);
                DebugLogger.exiting(MODULE, "queryEnable", "1 (enabled=" + enabled + ")");
                return 1;
        }

        private static int setEnable(CommandContext<CommandSourceStack> ctx, boolean enabled) {
                DebugLogger.entering(MODULE, "setEnable", "enabled=" + enabled);
                ChargedCreeperConfig.setEnabled(enabled);
                ctx.getSource().sendSuccess(() -> Component.translatable(enabled
                                ? "youzaiworldcore.message.command.event.ncc.set_enable_enabled"
                                : "youzaiworldcore.message.command.event.ncc.set_enable_disabled"),
                                true);
                DebugLogger.exiting(MODULE, "setEnable", "1");
                return 1;
        }

        // ==================== settings chance：查询 / 设置 ====================

        private static int queryChance(CommandContext<CommandSourceStack> ctx) {
                DebugLogger.entering(MODULE, "queryChance");
                double chance = ChargedCreeperConfig.getChance();
                ctx.getSource().sendSuccess(() -> Component.translatable(
                                "youzaiworldcore.message.command.event.ncc.query_chance",
                                String.format("%.4f", chance),
                                String.format("%.1f", chance * 100.0)),
                                false);
                DebugLogger.exiting(MODULE, "queryChance", "1 (chance=" + chance + ")");
                return 1;
        }

        private static int setChance(CommandContext<CommandSourceStack> ctx, double chance) {
                DebugLogger.entering(MODULE, "setChance", "chance=" + chance);
                if (Double.isNaN(chance) || chance < 0.0 || chance > 1.0) {
                        DebugLogger.branch(MODULE, "chance out of range", true, "chance=" + chance);
                        ctx.getSource().sendFailure(Component.translatable(
                                        "youzaiworldcore.message.command.event.ncc.chance_invalid"));
                        DebugLogger.exiting(MODULE, "setChance", "0 (invalid)");
                        return 0;
                }
                ChargedCreeperConfig.setChance(chance);
                ctx.getSource().sendSuccess(() -> Component.translatable(
                                "youzaiworldcore.message.command.event.ncc.set_chance",
                                String.format("%.4f", chance),
                                String.format("%.1f", chance * 100.0)),
                                true);
                DebugLogger.exiting(MODULE, "setChance", "1");
                return 1;
        }
}
