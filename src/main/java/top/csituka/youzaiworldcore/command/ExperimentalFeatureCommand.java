package top.csituka.youzaiworldcore.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import top.csituka.youzaiworldcore.feature.ExperimentalFeatures;
import top.csituka.youzaiworldcore.luckperms.LuckPermsHelper;
import top.csituka.youzaiworldcore.network.FeatureSyncPayload;

/**
 * {@code /yzwc experimental_feature <id> [true/false]}
 * <p>
 * 查询或切换实验性功能开关状态。
 * 仅管理员可执行切换操作，普通玩家仅可查询状态。
 * </p>
 */
public class ExperimentalFeatureCommand {

    /** 实验性功能切换权限节点 */
    public static final String PERMISSION_EXPERIMENTAL_FEATURE = "youzaiworldcore.command.experimental_feature";

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("yzwc")
                .then(Commands.literal("experimental_feature")
                        .then(Commands.argument("id", StringArgumentType.word())
                                // 查询状态 — 所有人可执行
                                .executes(ExperimentalFeatureCommand::queryFeature)
                                // 切换状态 — 仅管理员
                                .then(Commands.argument("enabled", BoolArgumentType.bool())
                                        .requires(src -> LuckPermsHelper.checkPermission(
                                                src, PERMISSION_EXPERIMENTAL_FEATURE, Commands.LEVEL_ADMINS))
                                        .executes(ExperimentalFeatureCommand::toggleFeature)
                                )
                        )
                )
        );
    }

    /**
     * 查询功能状态（所有人可用）
     */
    private static int queryFeature(CommandContext<CommandSourceStack> ctx) {
        String id = StringArgumentType.getString(ctx, "id");
        ExperimentalFeatures.FeatureEntry entry = ExperimentalFeatures.getEntry(id);

        if (entry == null) {
            ctx.getSource().sendFailure(
                    Component.translatable("youzaiworldcore.message.command.experimental_feature.not_found", id)
            );
            return 0;
        }

        boolean enabled = ExperimentalFeatures.isEnabled(id);

        // 构建信息文本
        MutableComponent statusText = Component.literal(
                "§6===== §e实验性功能 §6=====\n"
        );

        // 名称
        statusText.append(Component.literal("§7名称：§f" + entry.name() + "\n"));

        // 内部ID
        statusText.append(Component.literal("§7内部ID：§f" + entry.id() + "\n"));

        // 提供者（可点击）
        MutableComponent providerComp = Component.literal("§7提供者：")
                .append(Component.literal("§b§n" + entry.provider())
                        .withStyle(style -> style
                                .withClickEvent(new ClickEvent.OpenUrl(java.net.URI.create(entry.providerUrl())))
                                .withHoverEvent(new HoverEvent.ShowText(
                                        Component.literal("§a点击打开: " + entry.providerUrl())))
                        )
                );
        statusText.append(providerComp.append(Component.literal("\n")));

        // 描述
        statusText.append(Component.literal("§7描述：§f" + entry.description() + "\n"));

        // 来源（可点击）
        MutableComponent sourceComp = Component.literal("§7来源：")
                .append(Component.literal("§b§n" + entry.source())
                        .withStyle(style -> style
                                .withClickEvent(new ClickEvent.OpenUrl(java.net.URI.create(entry.sourceUrl())))
                                .withHoverEvent(new HoverEvent.ShowText(
                                        Component.literal("§a点击打开: " + entry.sourceUrl())))
                        )
                );
        statusText.append(sourceComp.append(Component.literal("\n")));

        // 当前状态
        String statusStr = enabled ? "§a已启用" : "§c已禁用";
        statusText.append(Component.literal("§7当前状态：" + statusStr + "\n"));

        statusText.append(Component.literal("§6================================"));

        ctx.getSource().sendSuccess(() -> statusText, false);
        return 1;
    }

    /**
     * 切换功能状态（仅管理员）
     */
    private static int toggleFeature(CommandContext<CommandSourceStack> ctx) {
        String id = StringArgumentType.getString(ctx, "id");
        boolean newEnabled = BoolArgumentType.getBool(ctx, "enabled");

        if (ExperimentalFeatures.getEntry(id) == null) {
            ctx.getSource().sendFailure(
                    Component.translatable("youzaiworldcore.message.command.experimental_feature.not_found", id)
            );
            return 0;
        }

        // 切换状态
        boolean changed = ExperimentalFeatures.setEnabled(id, newEnabled);
        if (!changed) {
            ctx.getSource().sendFailure(
                    Component.translatable("youzaiworldcore.message.command.experimental_feature.set_failed", id)
            );
            return 0;
        }

        // 向所有在线玩家广播同步包（更新客户端状态）
        FeatureSyncPayload syncPayload = new FeatureSyncPayload(id, newEnabled);
        for (ServerPlayer player : ctx.getSource().getServer().getPlayerList().getPlayers()) {
            ServerPlayNetworking.send(player, syncPayload);
        }

        // 通知操作者
        String statusKey = newEnabled
                ? "youzaiworldcore.message.command.experimental_feature.enabled"
                : "youzaiworldcore.message.command.experimental_feature.disabled";
        ctx.getSource().sendSuccess(() ->
                Component.translatable("youzaiworldcore.message.command.experimental_feature.toggle_success", id)
                        .append(" ")
                        .append(Component.translatable(statusKey)),
                true
        );

        return 1;
    }
}
