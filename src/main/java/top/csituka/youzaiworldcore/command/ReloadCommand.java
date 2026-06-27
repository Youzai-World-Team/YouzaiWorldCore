package top.csituka.youzaiworldcore.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import top.csituka.youzaiworldcore.YouzaiworldCore;
import top.csituka.youzaiworldcore.account.data.AccountDataStorage;
import top.csituka.youzaiworldcore.luckperms.LuckPermsHelper;

/**
 * 模组重载命令 /yzwc reload
 * <p>
 * 用于在运行时重新加载模组的可热重载模块（账户数据、配置等），
 * 无需重启服务器即可应用数据文件变更。
 * </p>
 *
 * <p>权限：需要 {@code youzaiworldcore.command.reload} 节点或 OP 4</p>
 */
public class ReloadCommand {

    /**
     * 注册 /yzwc reload 命令到调度器。
     *
     * @param dispatcher Brigadier 命令调度器
     */
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("yzwc")
                .then(Commands.literal("reload")
                        .requires(source -> LuckPermsHelper.checkPermission(
                                source, LuckPermsHelper.PERMISSION_RELOAD, Commands.LEVEL_ADMINS))
                        .executes(ReloadCommand::executeReload)
                )
        );
    }

    /**
     * 执行重载逻辑：
     * <ul>
     *   <li>重新加载 AccountDataStorage（账户数据 + 配置）</li>
     *   <li>预留扩展点：后续可在此添加配置文件、占位符等模块的重载</li>
     * </ul>
     */
    private static int executeReload(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();

        YouzaiworldCore.LOGGER.info("管理员 {} 正在重载模组...",
                source.getTextName());

        // 重载账户数据存储
        int accountCount = AccountDataStorage.reload();

        // === 预留扩展点 ===
        // 后续如需重载其他模块（如自定义配置文件、缓存等），在此添加调用：
        // ConfigManager.reload();
        // PlaceholderCache.reload();

        source.sendSuccess(() ->
                Component.translatable("youzaiworldcore.message.command.reload_success", accountCount),
                true
        );

        YouzaiworldCore.LOGGER.info("模组重载完成，已重新加载 {} 个账户数据", accountCount);
        return 1;
    }
}
