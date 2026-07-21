package top.csituka.youzaiworldcore.mail;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.PermissionCheck;
import top.csituka.youzaiworldcore.luckperms.LuckPermsHelper;
import top.csituka.youzaiworldcore.util.DebugLogger;

/**
 * 邮件系统服务端权限辅助类。
 * <p>
 * 本类仅为服务端使用（LuckPermsHelper 仅服务端可用）。
 * 客户端权限显隐由 {@code MailUnreadCountPayload.canSend} 驱动。
 * </p>
 */
@SuppressWarnings("null")
public class MailPermissionHelper {

    private static final String MODULE = "MailPermissionHelper";

    /**
     * 判断玩家是否有邮件系统权限（服务端 C2S 包处理器中使用）。
     * <ul>
     *   <li>LP 已安装 → 检查 {@code mail_permission_node}</li>
     *   <li>LP 未安装 → 回退到原版 OP 等级 >= {@code mail_permission_level}</li>
     * </ul>
     *
     * @param player 服务端玩家
     * @return true 如果玩家有邮件系统操作权限
     */
    public static boolean hasMailPermission(ServerPlayer player) {
        DebugLogger.entering(MODULE, "hasMailPermission", "player=" + player.getScoreboardName());
        String node = MailSettings.get().getMailPermissionNode();
        int level = MailSettings.get().getMailPermissionLevel();

        // 1. 检查 LuckPerms 节点
        boolean lpResult = LuckPermsHelper.checkLuckPermsOnly(player.getUUID(), node);
        if (lpResult) {
            DebugLogger.exiting(MODULE, "hasMailPermission", "LP node granted");
            return true;
        }

        // 2. 回退到原版 OP 等级检查（使用 26.2 PermissionCheck API）
        boolean opResult = Commands.hasPermission(levelToCheck(level)).test(player.createCommandSourceStack());
        DebugLogger.exiting(MODULE, "hasMailPermission", "OP level check=" + opResult);
        return opResult;
    }

    /**
     * 将旧版 OP 等级（0-4）映射到 26.2 的 PermissionCheck 常量。
     */
    private static PermissionCheck levelToCheck(int level) {
        return switch (level) {
            case 0 -> Commands.LEVEL_ALL;
            case 1 -> Commands.LEVEL_MODERATORS;
            case 2 -> Commands.LEVEL_GAMEMASTERS;
            case 3 -> Commands.LEVEL_ADMINS;
            case 4 -> Commands.LEVEL_OWNERS;
            default -> Commands.LEVEL_ALL;
        };
    }

    /**
     * 判断命令执行源是否有邮件系统权限（Brigadier {@code .requires()} 使用，
     * 但邮件均为客户端命令，此方法预留供服务端命令上下文使用）。
     *
     * @param source 命令执行源
     * @return true 如果执行者有邮件系统权限
     */
    public static boolean checkPermission(CommandSourceStack source) {
        DebugLogger.entering(MODULE, "checkPermission");
        if (!source.isPlayer()) {
            // 控制台/命令方块始终放行
            DebugLogger.exiting(MODULE, "checkPermission", "console=true");
            return true;
        }
        String node = MailSettings.get().getMailPermissionNode();
        boolean result = LuckPermsHelper.checkPermission(
                source, node, Commands.LEVEL_ADMINS);
        DebugLogger.exiting(MODULE, "checkPermission", "result=" + result);
        return result;
    }
}
