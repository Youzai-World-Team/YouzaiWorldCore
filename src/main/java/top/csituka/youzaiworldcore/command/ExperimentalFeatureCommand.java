package top.csituka.youzaiworldcore.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import top.csituka.youzaiworldcore.feature.ExperimentalFeatures;
import top.csituka.youzaiworldcore.luckperms.LuckPermsHelper;
import top.csituka.youzaiworldcore.network.FeatureSyncPayload;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * {@code /yzwc experimental_feature <id> [true/false [all|only <player>]]}
 * <p>
 * 作用域规则：
 * <ul>
 *   <li>{@code <bool>} 省略 — 查询状态（所有人）</li>
 *   <li>{@code <bool>} 无作用域 — 为自己切换（所有人可执行）</li>
 *   <li>{@code <bool> all} — 全服切换（OP 4）</li>
 *   <li>{@code <bool> only <player>} — 为指定玩家切换（OP 4）</li>
 * </ul>
 * </p>
 */
public class ExperimentalFeatureCommand {

    public static final String PERMISSION_EXPERIMENTAL_FEATURE = "youzaiworldcore.command.experimental_feature";
    public static final String PERMISSION_QUERY = "youzaiworldcore.command.experimental_feature.query";
    public static final String PERMISSION_SELF = "youzaiworldcore.command.experimental_feature.self";
    public static final String PERMISSION_ADMIN = "youzaiworldcore.command.experimental_feature.admin";

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        // /yzwc experimental_feature <id> — 查询（所有人，但可通过 LuckPerms 控制）
        var queryNode = Commands.argument("id", StringArgumentType.word())
                .requires(src -> LuckPermsHelper.checkPermission(
                        src, PERMISSION_QUERY, Commands.LEVEL_ALL))
                .executes(ExperimentalFeatureCommand::queryFeature)
                // /yzwc experimental_feature <id> <bool> — 自切换
                .then(Commands.argument("enabled", BoolArgumentType.bool())
                        .requires(src -> LuckPermsHelper.checkPermission(
                                src, PERMISSION_SELF, Commands.LEVEL_ALL))
                        .executes(ctx -> setFeatureSelf(ctx, BoolArgumentType.getBool(ctx, "enabled")))
                        // /yzwc experimental_feature <id> <bool> all — 全服
                        .then(Commands.literal("all")
                                .requires(src -> LuckPermsHelper.checkPermission(
                                        src, PERMISSION_ADMIN, Commands.LEVEL_ADMINS))
                                .executes(ctx -> setFeatureAll(ctx, BoolArgumentType.getBool(ctx, "enabled")))
                        )
                        // /yzwc experimental_feature <id> <bool> only <player> — 指定玩家
                        .then(Commands.literal("only")
                                .requires(src -> LuckPermsHelper.checkPermission(
                                        src, PERMISSION_ADMIN, Commands.LEVEL_ADMINS))
                                .then(Commands.argument("target", EntityArgument.player())
                                        .executes(ctx -> setFeatureForPlayer(
                                                ctx, BoolArgumentType.getBool(ctx, "enabled"),
                                                EntityArgument.getPlayer(ctx, "target")))
                                )
                        )
                );

        dispatcher.register(Commands.literal("yzwc")
                .then(Commands.literal("experimental_feature")
                        .then(queryNode)
                )
        );
    }

    // ==================== 查询 ====================

    private static int queryFeature(CommandContext<CommandSourceStack> ctx) {
        String id = StringArgumentType.getString(ctx, "id");
        ExperimentalFeatures.FeatureEntry entry = ExperimentalFeatures.getEntry(id);

        if (entry == null) {
            ctx.getSource().sendFailure(
                    Component.translatable("youzaiworldcore.message.command.experimental_feature.not_found", id)
            );
            return 0;
        }

        boolean globalEnabled = ExperimentalFeatures.isGlobalEnabled(id);
        MutableComponent text = Component.literal(
                "§6===== §e实验性功能 §6=====\n"
        );
        text.append(Component.literal("§7名称：§f" + entry.name() + "\n"));
        text.append(Component.literal("§7内部ID：§f" + entry.id() + "\n"));

        // 提供者
        text.append(Component.literal("§7提供者：")
                .append(Component.literal("§b§n" + entry.provider())
                        .withStyle(style -> style
                                .withClickEvent(new ClickEvent.OpenUrl(java.net.URI.create(entry.providerUrl())))
                                .withHoverEvent(new HoverEvent.ShowText(
                                        Component.literal("§a点击打开: " + entry.providerUrl())))
                        )
                ).append(Component.literal("\n")));

        text.append(Component.literal("§7描述：§f" + entry.description() + "\n"));

        // 来源
        text.append(Component.literal("§7来源：")
                .append(Component.literal("§b§n" + entry.source())
                        .withStyle(style -> style
                                .withClickEvent(new ClickEvent.OpenUrl(java.net.URI.create(entry.sourceUrl())))
                                .withHoverEvent(new HoverEvent.ShowText(
                                        Component.literal("§a点击打开: " + entry.sourceUrl())))
                        )
                ).append(Component.literal("\n")));

        text.append(Component.literal("§7全局状态：" + (globalEnabled ? "§a已启用" : "§c已禁用") + "\n"));
        text.append(Component.literal("§6================================"));

        ctx.getSource().sendSuccess(() -> text, false);
        return 1;
    }

    // ==================== 为自己切换（self）====================

    private static int setFeatureSelf(CommandContext<CommandSourceStack> ctx, boolean enabled)
            throws CommandSyntaxException {
        String id = StringArgumentType.getString(ctx, "id");
        if (ExperimentalFeatures.getEntry(id) == null) {
            ctx.getSource().sendFailure(
                    Component.translatable("youzaiworldcore.message.command.experimental_feature.not_found", id)
            );
            return 0;
        }

        ServerPlayer player = ctx.getSource().getPlayerOrException();
        UUID playerUuid = player.getUUID();

        // 设置玩家覆写
        ExperimentalFeatures.setForPlayer(id, playerUuid, enabled);

        // 只向该玩家发送同步包
        FeatureSyncPayload payload = new FeatureSyncPayload(id, enabled, playerUuid);
        ServerPlayNetworking.send(player, payload);

        String statusKey = enabled ? "§a启用" : "§c禁用";
        ctx.getSource().sendSuccess(() ->
                Component.literal("§a已为自己" + statusKey + "实验性功能: §f" + id),
                false
        );
        return 1;
    }

    // ==================== 全服切换（all）====================

    private static int setFeatureAll(CommandContext<CommandSourceStack> ctx, boolean enabled) {
        String id = StringArgumentType.getString(ctx, "id");
        if (ExperimentalFeatures.getEntry(id) == null) {
            ctx.getSource().sendFailure(
                    Component.translatable("youzaiworldcore.message.command.experimental_feature.not_found", id)
            );
            return 0;
        }

        // 设置全局状态并清空所有玩家覆写
        ExperimentalFeatures.setGlobal(id, enabled);

        // 向所有在线玩家广播全局同步包（targetPlayer = null 表示全局）
        FeatureSyncPayload payload = new FeatureSyncPayload(id, enabled, null);
        for (ServerPlayer player : ctx.getSource().getServer().getPlayerList().getPlayers()) {
            ServerPlayNetworking.send(player, payload);
        }

        String statusKey = enabled ? "§a启用" : "§c禁用";
        ctx.getSource().sendSuccess(() ->
                Component.literal("§a已全服" + statusKey + "实验性功能: §f" + id),
                true
        );
        return 1;
    }

    // ==================== 为指定玩家切换（only）====================

    private static int setFeatureForPlayer(CommandContext<CommandSourceStack> ctx, boolean enabled,
                                            ServerPlayer target) {
        String id = StringArgumentType.getString(ctx, "id");
        if (ExperimentalFeatures.getEntry(id) == null) {
            ctx.getSource().sendFailure(
                    Component.translatable("youzaiworldcore.message.command.experimental_feature.not_found", id)
            );
            return 0;
        }

        UUID targetUuid = target.getUUID();
        ExperimentalFeatures.setForPlayer(id, targetUuid, enabled);

        // 只向目标玩家发送同步包
        FeatureSyncPayload payload = new FeatureSyncPayload(id, enabled, targetUuid);
        ServerPlayNetworking.send(target, payload);

        String statusKey = enabled ? "§a启用" : "§c禁用";
        ctx.getSource().sendSuccess(() ->
                Component.literal("§a已为目标玩家 §f" + target.getName().getString()
                        + " §a" + statusKey + " §f实验性功能: " + id),
                true
        );
        return 1;
    }
}
