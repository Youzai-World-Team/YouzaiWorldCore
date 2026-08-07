package top.csituka.youzaiworldcore.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import top.csituka.youzaiworldcore.config.FunctionToggleManager;
import top.csituka.youzaiworldcore.luckperms.LuckPermsHelper;
import top.csituka.youzaiworldcore.network.FunctionToggleSyncPayload;
import top.csituka.youzaiworldcore.util.DebugLogger;

/**
 * 单玩家功能开关命令：{@code /yzwc function <功能名> [true|false]}
 * <p>
 * 5 项可开关功能（crop_xp_drop 已移至 /yzwc event）：
 * <ul>
 * <li>{@code ladder_extend_downward} — 梯子向下延展</li>
 * <li>{@code tool_info_overlay} — 时钟/指南针/追溯指针信息显示</li>
 * <li>{@code block_animation} — 方块动画粒子</li>
 * <li>{@code crafting_sound} — 合成音效</li>
 * <li>{@code item_sparkle} — 物品闪烁粒子</li>
 * </ul>
 * </p>
 */
@SuppressWarnings("null")
public class FunctionCommand {

    private static final String MODULE = "FunctionCommand";

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        DebugLogger.entering(MODULE, "register");

        dispatcher.register(Commands.literal("yzwc")
                .then(Commands.literal("function")
                        .then(functionNode("ladder_extend_downward"))
                        .then(functionNode("tool_info_overlay"))
                        .then(functionNode("block_animation"))
                        .then(functionNode("crafting_sound"))
                        .then(functionNode("item_sparkle"))));

        DebugLogger.exiting(MODULE, "register");
    }

    private static LiteralArgumentBuilder<CommandSourceStack> functionNode(String key) {
        return Commands.literal(key)
                .requires(src -> LuckPermsHelper.checkPermission(src,
                        LuckPermsHelper.PERMISSION_FUNCTION_QUERY, Commands.LEVEL_ALL))
                .executes(ctx -> queryToggle(ctx, key))
                .then(Commands.argument("enabled", BoolArgumentType.bool())
                        .requires(src -> LuckPermsHelper.checkPermission(src,
                                LuckPermsHelper.PERMISSION_FUNCTION_SET, Commands.LEVEL_ADMINS))
                        .executes(ctx -> setToggle(ctx, key, BoolArgumentType.getBool(ctx, "enabled"))));
    }

    private static int queryToggle(CommandContext<CommandSourceStack> ctx, String key) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        boolean enabled = FunctionToggleManager.isEnabled(player.getUUID(), key);
        String label = getLabel(key);
        ctx.getSource().sendSuccess(() -> Component.translatable(enabled
                ? "youzaiworldcore.message.command.function.query_enabled"
                : "youzaiworldcore.message.command.function.query_disabled", label),
                false);
        return 1;
    }

    private static int setToggle(CommandContext<CommandSourceStack> ctx, String key, boolean enabled) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        FunctionToggleManager.setEnabled(player.getUUID(), key, enabled);

        // 同步到客户端
        syncToClient(player);

        String label = getLabel(key);
        ctx.getSource().sendSuccess(() -> Component.translatable(enabled
                ? "youzaiworldcore.message.command.function.set_enabled"
                : "youzaiworldcore.message.command.function.set_disabled", label),
                true);
        return 1;
    }

    /**
     * 向客户端发送完整的开关状态。
     */
    public static void syncToClient(ServerPlayer player) {
        var uuid = player.getUUID();
        ServerPlayNetworking.send(player, new FunctionToggleSyncPayload(
                FunctionToggleManager.isEnabled(uuid, FunctionToggleManager.KEY_LADDER),
                false, // crop_xp_drop 已移至全局事件，不再按玩家控制
                FunctionToggleManager.isEnabled(uuid, FunctionToggleManager.KEY_TOOL_INFO),
                FunctionToggleManager.isEnabled(uuid, FunctionToggleManager.KEY_BLOCK_ANIM),
                FunctionToggleManager.isEnabled(uuid, FunctionToggleManager.KEY_CRAFT_SOUND),
                FunctionToggleManager.isEnabled(uuid, FunctionToggleManager.KEY_ITEM_SPARKLE)));
    }

    private static String getLabel(String key) {
        return switch (key) {
            case "ladder_extend_downward" -> "梯子向下延展";
            case "tool_info_overlay" -> "时钟/指南针/追溯指针信息";
            case "block_animation" -> "方块动画粒子";
            case "crafting_sound" -> "合成音效";
            case "item_sparkle" -> "物品闪烁粒子";
            default -> key;
        };
    }
}
