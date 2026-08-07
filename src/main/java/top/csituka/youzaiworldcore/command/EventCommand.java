package top.csituka.youzaiworldcore.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import top.csituka.youzaiworldcore.config.ChargedCreeperConfig;
import top.csituka.youzaiworldcore.config.EventSettings;
import top.csituka.youzaiworldcore.config.LaowuMemeConfig;
import top.csituka.youzaiworldcore.luckperms.LuckPermsHelper;
import top.csituka.youzaiworldcore.trialvault.TrialVaultConfig;
import top.csituka.youzaiworldcore.util.DebugLogger;

import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * 事件管理命令：{@code /yzwc event ...}
 * <p>
 * 子命令：
 * <ul>
 * <li>{@code naturally_charged_creepers enable [true|false]} — 开启 /
 * 关闭天然带电苦力怕事件</li>
 * <li>{@code naturally_charged_creepers settings chance [double]} — 调整带电概率</li>
 * <li>{@code trial_vault enable [true|false]} — 开启 / 关闭试炼宝库无限领奖</li>
 * <li>{@code laowu enable [true|false]} — 开启 / 关闭老吴贴贴事件（全局开关，对全体玩家生效）</li>
 * <li>{@code laowu settings cd [seconds]} — 查询 / 调整老吴贴贴释放后的冷却时长（秒，至少 60，默认 180）</li>
 * </ul>
 * 权限：
 * <ul>
 * <li>查询（省略参数）需 {@code youzaiworldcore.command.event.query}，默认所有人可查询；</li>
 * <li>修改（带参数）需 {@code youzaiworldcore.command.event.set}，默认 OP 4。</li>
 * </ul>
 * 参考 Serilum 的 Naturally Charged Creepers 的设计与行为实现（原生重写，不依赖其前置 Collective）
 * </p>
 */
@SuppressWarnings("null")
public class EventCommand {

        private static final String MODULE = "EventCommand";
        private static final String EVENT_NCC = "naturally_charged_creepers";
        private static final String EVENT_TRIAL_VAULT = "trial_vault";
        /** 老吴贴贴事件（全局开关，直接挂在 {@code /yzwc event} 下） */
        private static final String EVENT_LAOWU = "laowu";

        public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
                DebugLogger.entering(MODULE, "register");

                // ===== naturally_charged_creepers =====

                // /yzwc event naturally_charged_creepers enable [true|false]
                var nccEnableNode = Commands.literal("enable")
                                .requires(src -> LuckPermsHelper.checkPermission(
                                                src, LuckPermsHelper.PERMISSION_EVENT_QUERY, Commands.LEVEL_ALL))
                                .executes(EventCommand::nccQueryEnable)
                                .then(Commands.argument("enabled", BoolArgumentType.bool())
                                                .requires(src -> LuckPermsHelper.checkPermission(
                                                                src, LuckPermsHelper.PERMISSION_EVENT_SET,
                                                                Commands.LEVEL_ADMINS))
                                                .executes(ctx -> nccSetEnable(ctx,
                                                                BoolArgumentType.getBool(ctx, "enabled"))));

                // /yzwc event naturally_charged_creepers settings chance [double]
                var chanceNode = Commands.literal("chance")
                                .requires(src -> LuckPermsHelper.checkPermission(
                                                src, LuckPermsHelper.PERMISSION_EVENT_QUERY, Commands.LEVEL_ALL))
                                .executes(EventCommand::nccQueryChance)
                                .then(Commands.argument("chance", DoubleArgumentType.doubleArg())
                                                .requires(src -> LuckPermsHelper.checkPermission(
                                                                src, LuckPermsHelper.PERMISSION_EVENT_SET,
                                                                Commands.LEVEL_ADMINS))
                                                .executes(ctx -> nccSetChance(ctx,
                                                                DoubleArgumentType.getDouble(ctx, "chance"))));

                var nccSettingsNode = Commands.literal("settings")
                                .then(chanceNode);

                // ===== trial_vault =====

                // /yzwc event trial_vault enable [true|false]
                var tvEnableNode = Commands.literal("enable")
                                .requires(src -> LuckPermsHelper.checkPermission(
                                                src, LuckPermsHelper.PERMISSION_EVENT_QUERY, Commands.LEVEL_ALL))
                                .executes(EventCommand::tvQueryEnable)
                                .then(Commands.argument("enabled", BoolArgumentType.bool())
                                                .requires(src -> LuckPermsHelper.checkPermission(
                                                                src, LuckPermsHelper.PERMISSION_EVENT_SET,
                                                                Commands.LEVEL_ADMINS))
                                                .executes(ctx -> tvSetEnable(ctx,
                                                                BoolArgumentType.getBool(ctx, "enabled"))));

                // ===== laowu（老吴贴贴全局开关）=====

                // /yzwc event laowu enable [true|false]
                // 省略参数 = 查询当前状态；带 true/false = 对全体玩家启用/禁用该功能
                var laowuEnableNode = Commands.literal("enable")
                                .requires(src -> LuckPermsHelper.checkPermission(
                                                src, LuckPermsHelper.PERMISSION_EVENT_QUERY, Commands.LEVEL_ALL))
                                .executes(EventCommand::laowuQueryEnable)
                                .then(Commands.argument("enabled", BoolArgumentType.bool())
                                                .requires(src -> LuckPermsHelper.checkPermission(
                                                                src, LuckPermsHelper.PERMISSION_EVENT_SET,
                                                                Commands.LEVEL_ADMINS))
                                                .executes(ctx -> laowuSetEnable(ctx,
                                                                BoolArgumentType.getBool(ctx, "enabled"))));

                // /yzwc event laowu settings cd [seconds]
                // 省略参数 = 查询当前冷却时长；带秒数（>=60）= 设置释放后的冷却时长
                var laowuCdNode = Commands.literal("cd")
                                .requires(src -> LuckPermsHelper.checkPermission(
                                                src, LuckPermsHelper.PERMISSION_EVENT_QUERY, Commands.LEVEL_ALL))
                                .executes(EventCommand::laowuQueryCd)
                                .then(Commands.argument("seconds",
                                                IntegerArgumentType.integer(LaowuMemeConfig.MIN_COOLDOWN_SECONDS))
                                                .requires(src -> LuckPermsHelper.checkPermission(
                                                                src, LuckPermsHelper.PERMISSION_EVENT_SET,
                                                                Commands.LEVEL_ADMINS))
                                                .executes(ctx -> laowuSetCd(ctx,
                                                                IntegerArgumentType.getInteger(ctx, "seconds"))));

                var laowuSettingsNode = Commands.literal("settings")
                                .then(laowuCdNode);

                dispatcher.register(Commands.literal("yzwc")
                                .then(Commands.literal("event")
                                                .then(Commands.literal(EVENT_NCC)
                                                                .then(nccEnableNode)
                                                                .then(nccSettingsNode))
                                                .then(Commands.literal(EVENT_TRIAL_VAULT)
                                                                .then(tvEnableNode))
                                                .then(Commands.literal(EVENT_LAOWU)
                                                                .then(laowuEnableNode)
                                                                .then(laowuSettingsNode))
                                                .then(simpleEventNode("death_sound",
                                                                EventSettings::isDeathSoundEnabled,
                                                                EventSettings::setDeathSound))
                                                .then(simpleEventNode("jukebox_loop",
                                                                EventSettings::isJukeboxLoopEnabled,
                                                                EventSettings::setJukeboxLoop))
                                                .then(simpleEventNode("baby_zombie_weak",
                                                                EventSettings::isBabyZombieWeakEnabled,
                                                                EventSettings::setBabyZombieWeak))
                                                .then(simpleEventNode("wither_skull_drop",
                                                                EventSettings::isWitherSkullDropEnabled,
                                                                EventSettings::setWitherSkullDrop))
                                                .then(simpleEventNode("trident_void_protect",
                                                                EventSettings::isTridentVoidProtectEnabled,
                                                                EventSettings::setTridentVoidProtect))));

                DebugLogger.exiting(MODULE, "register");
        }

        // ==================== naturally_charged_creepers enable：查询 / 设置
        // ====================

        private static int nccQueryEnable(CommandContext<CommandSourceStack> ctx) {
                DebugLogger.entering(MODULE, "nccQueryEnable");
                boolean enabled = ChargedCreeperConfig.isEnabled();
                ctx.getSource().sendSuccess(() -> Component.translatable(enabled
                                ? "youzaiworldcore.message.command.event.ncc.query_enable_enabled"
                                : "youzaiworldcore.message.command.event.ncc.query_enable_disabled"),
                                false);
                DebugLogger.exiting(MODULE, "nccQueryEnable", "1 (enabled=" + enabled + ")");
                return 1;
        }

        private static int nccSetEnable(CommandContext<CommandSourceStack> ctx, boolean enabled) {
                DebugLogger.entering(MODULE, "nccSetEnable", "enabled=" + enabled);
                ChargedCreeperConfig.setEnabled(enabled);
                ctx.getSource().sendSuccess(() -> Component.translatable(enabled
                                ? "youzaiworldcore.message.command.event.ncc.set_enable_enabled"
                                : "youzaiworldcore.message.command.event.ncc.set_enable_disabled"),
                                true);
                DebugLogger.exiting(MODULE, "nccSetEnable", "1");
                return 1;
        }

        // ==================== naturally_charged_creepers settings chance：查询 / 设置
        // ====================

        private static int nccQueryChance(CommandContext<CommandSourceStack> ctx) {
                DebugLogger.entering(MODULE, "nccQueryChance");
                double chance = ChargedCreeperConfig.getChance();
                ctx.getSource().sendSuccess(() -> Component.translatable(
                                "youzaiworldcore.message.command.event.ncc.query_chance",
                                String.format("%.4f", chance),
                                String.format("%.1f", chance * 100.0)),
                                false);
                DebugLogger.exiting(MODULE, "nccQueryChance", "1 (chance=" + chance + ")");
                return 1;
        }

        private static int nccSetChance(CommandContext<CommandSourceStack> ctx, double chance) {
                DebugLogger.entering(MODULE, "nccSetChance", "chance=" + chance);
                if (Double.isNaN(chance) || chance < 0.0 || chance > 1.0) {
                        DebugLogger.branch(MODULE, "chance out of range", true, "chance=" + chance);
                        ctx.getSource().sendFailure(Component.translatable(
                                        "youzaiworldcore.message.command.event.ncc.chance_invalid"));
                        DebugLogger.exiting(MODULE, "nccSetChance", "0 (invalid)");
                        return 0;
                }
                ChargedCreeperConfig.setChance(chance);
                ctx.getSource().sendSuccess(() -> Component.translatable(
                                "youzaiworldcore.message.command.event.ncc.set_chance",
                                String.format("%.4f", chance),
                                String.format("%.1f", chance * 100.0)),
                                true);
                DebugLogger.exiting(MODULE, "nccSetChance", "1");
                return 1;
        }

        // ==================== trial_vault enable：查询 / 设置 ====================

        private static int tvQueryEnable(CommandContext<CommandSourceStack> ctx) {
                DebugLogger.entering(MODULE, "tvQueryEnable");
                boolean enabled = TrialVaultConfig.isEnabled();
                ctx.getSource().sendSuccess(() -> Component.translatable(enabled
                                ? "youzaiworldcore.message.command.event.trial_vault.query_enable_enabled"
                                : "youzaiworldcore.message.command.event.trial_vault.query_enable_disabled"),
                                false);
                DebugLogger.exiting(MODULE, "tvQueryEnable", "1 (enabled=" + enabled + ")");
                return 1;
        }

        private static int tvSetEnable(CommandContext<CommandSourceStack> ctx, boolean enabled) {
                DebugLogger.entering(MODULE, "tvSetEnable", "enabled=" + enabled);
                TrialVaultConfig.setEnabled(enabled);
                ctx.getSource().sendSuccess(() -> Component.translatable(enabled
                                ? "youzaiworldcore.message.command.event.trial_vault.set_enable_enabled"
                                : "youzaiworldcore.message.command.event.trial_vault.set_enable_disabled"),
                                true);
                DebugLogger.exiting(MODULE, "tvSetEnable", "1");
                return 1;
        }

        // ==================== laowu enable：查询 / 设置（全局开关） ====================

        private static int laowuQueryEnable(CommandContext<CommandSourceStack> ctx) {
                DebugLogger.entering(MODULE, "laowuQueryEnable");
                boolean enabled = LaowuMemeConfig.isEnabled();
                ctx.getSource().sendSuccess(() -> Component.translatable(enabled
                                ? "youzaiworldcore.message.command.event.laowu.query_enable_enabled"
                                : "youzaiworldcore.message.command.event.laowu.query_enable_disabled"),
                                false);
                DebugLogger.exiting(MODULE, "laowuQueryEnable", "1 (enabled=" + enabled + ")");
                return 1;
        }

        private static int laowuSetEnable(CommandContext<CommandSourceStack> ctx, boolean enabled) {
                DebugLogger.entering(MODULE, "laowuSetEnable", "enabled=" + enabled);
                LaowuMemeConfig.setEnabled(enabled);
                ctx.getSource().sendSuccess(() -> Component.translatable(enabled
                                ? "youzaiworldcore.message.command.event.laowu.set_enable_enabled"
                                : "youzaiworldcore.message.command.event.laowu.set_enable_disabled"),
                                true);
                DebugLogger.exiting(MODULE, "laowuSetEnable", "1");
                return 1;
        }

        // ==================== laowu settings cd：查询 / 设置（冷却时长，秒） ====================

        private static int laowuQueryCd(CommandContext<CommandSourceStack> ctx) {
                DebugLogger.entering(MODULE, "laowuQueryCd");
                int seconds = LaowuMemeConfig.getCooldownSeconds();
                ctx.getSource().sendSuccess(() -> Component.translatable(
                                "youzaiworldcore.message.command.event.laowu.query_cd",
                                seconds),
                                false);
                DebugLogger.exiting(MODULE, "laowuQueryCd", "1 (seconds=" + seconds + ")");
                return 1;
        }

        private static int laowuSetCd(CommandContext<CommandSourceStack> ctx, int seconds) {
                DebugLogger.entering(MODULE, "laowuSetCd", "seconds=" + seconds);
                LaowuMemeConfig.setCooldownSeconds(seconds);
                ctx.getSource().sendSuccess(() -> Component.translatable(
                                "youzaiworldcore.message.command.event.laowu.set_cd",
                                seconds),
                                true);
                DebugLogger.exiting(MODULE, "laowuSetCd", "1");
                return 1;
        }

        // ==================== 简单全局事件（仅 enable/disable）的通用工厂 ====================

        /**
         * 构造一个只有 enable [true|false] 的简单事件节点。
         * @param name   事件名（用于命令字面量和翻译键）
         * @param getter 查询当前开关状态
         * @param setter 设置开关状态（同时持久化）
         */
        private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> simpleEventNode(
                        String name, Supplier<Boolean> getter, Consumer<Boolean> setter) {
                return Commands.literal(name)
                                .then(Commands.literal("enable")
                                                .requires(src -> LuckPermsHelper.checkPermission(
                                                                src, LuckPermsHelper.PERMISSION_EVENT_QUERY,
                                                                Commands.LEVEL_ALL))
                                                .executes(ctx -> {
                                                        boolean enabled = getter.get();
                                                        ctx.getSource().sendSuccess(
                                                                        () -> Component.translatable(enabled
                                                                                        ? "youzaiworldcore.message.command.event.simple.query_enabled"
                                                                                        : "youzaiworldcore.message.command.event.simple.query_disabled",
                                                                                        name),
                                                                        false);
                                                        return 1;
                                                })
                                                .then(Commands.argument("enabled", BoolArgumentType.bool())
                                                                .requires(src -> LuckPermsHelper.checkPermission(
                                                                                src,
                                                                                LuckPermsHelper.PERMISSION_EVENT_SET,
                                                                                Commands.LEVEL_ADMINS))
                                                                .executes(ctx -> {
                                                                        boolean v = BoolArgumentType.getBool(ctx,
                                                                                        "enabled");
                                                                        setter.accept(v);
                                                                        ctx.getSource().sendSuccess(
                                                                                        () -> Component.translatable(v
                                                                                                        ? "youzaiworldcore.message.command.event.simple.set_enabled"
                                                                                                        : "youzaiworldcore.message.command.event.simple.set_disabled",
                                                                                                        name),
                                                                                        true);
                                                                        return 1;
                                                                })));
        }
}
