package top.csituka.youzaiworldcore.account.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.level.Level;
import top.csituka.youzaiworldcore.YouzaiworldCore;
import top.csituka.youzaiworldcore.account.data.AccountDataStorage;
import top.csituka.youzaiworldcore.account.data.PlayerAccount;
import top.csituka.youzaiworldcore.account.data.PlayerAuthAccess;
import top.csituka.youzaiworldcore.account.util.AuthHelper;
import top.csituka.youzaiworldcore.account.util.AuthLocationData;
import top.csituka.youzaiworldcore.account.util.AuthPlayerHelper;
import top.csituka.youzaiworldcore.account.util.PasswordHasher;

import java.time.ZonedDateTime;
import java.util.Set;

/**
 * 账户管理命令
 *
 * 玩家命令：
 *   /yzwc account register <密码> <确认密码>  — 注册
 *   /yzwc account login <密码>                 — 登录
 *   /yzwc account logout                       — 登出
 *   /yzwc account deactivate <密码>            — 注销（删除账户）
 *   /yzwc account change_password <旧密码> <新密码> <确认密码> — 修改密码
 *
 * 管理员命令：
 *   /yzwc account mgr create <玩家代号>                — 创建离线玩家账户
 *   /yzwc account mgr reset_password <玩家> <新密码> <确认密码> — 重置密码
 *   /yzwc account mgr delete <玩家代号>                — 删除玩家账户
 */
public class AccountCommands {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        // /yzwc account 根命令
        dispatcher.register(Commands.literal("yzwc")
                .then(Commands.literal("account")
                        // ===== 玩家命令 =====
                        .then(Commands.literal("register")
                                .then(Commands.argument("password", StringArgumentType.string())
                                        .then(Commands.argument("confirm", StringArgumentType.string())
                                                .executes(ctx -> executeRegister(ctx))
                                        )
                                )
                        )
                        .then(Commands.literal("login")
                                .then(Commands.argument("password", StringArgumentType.string())
                                        .executes(ctx -> executeLogin(ctx))
                                )
                        )
                        .then(Commands.literal("logout")
                                .executes(ctx -> executeLogout(ctx))
                        )
                        .then(Commands.literal("deactivate")
                                .then(Commands.argument("password", StringArgumentType.string())
                                        .executes(ctx -> executeDeactivate(ctx))
                                )
                        )
                        .then(Commands.literal("change_password")
                                .then(Commands.argument("oldPassword", StringArgumentType.string())
                                        .then(Commands.argument("newPassword", StringArgumentType.string())
                                                .then(Commands.argument("confirmPassword", StringArgumentType.string())
                                                        .executes(ctx -> executeChangePassword(ctx))
                                                )
                                        )
                                )
                        )
                        // ===== 管理命令（需要 OP 权限） =====
                        .then(Commands.literal("mgr")
                                .requires(src -> Commands.LEVEL_ADMINS.check(src.permissions()))
                                .then(Commands.literal("create")
                                        .then(Commands.argument("player", StringArgumentType.string())
                                                .then(Commands.argument("newPassword", StringArgumentType.string())
                                                        .then(Commands.argument("confirmPassword", StringArgumentType.string())
                                                                .executes(ctx -> executeAdminCreate(ctx))
                                                        )
                                                )
                                        )
                                )
                                .then(Commands.literal("reset_password")
                                        .then(Commands.argument("player", StringArgumentType.string())
                                                .then(Commands.argument("newPassword", StringArgumentType.string())
                                                        .then(Commands.argument("confirmPassword", StringArgumentType.string())
                                                                .executes(ctx -> executeAdminResetPassword(ctx))
                                                        )
                                                )
                                        )
                                )
                                .then(Commands.literal("delete")
                                        .then(Commands.argument("player", StringArgumentType.string())
                                                .executes(ctx -> executeAdminDelete(ctx))
                                        )
                                )
                                .then(Commands.literal("session_timeout")
                                        // 无参数：显示当前值
                                        .executes(ctx -> executeAdminSessionTimeout(ctx))
                                        // 有参数：设置新值
                                        .then(Commands.argument("seconds", com.mojang.brigadier.arguments.IntegerArgumentType.integer(0, 86400))
                                                .executes(ctx -> executeAdminSessionTimeout(ctx))
                                        )
                                )
                        )
                        .executes(ctx -> {
                            ctx.getSource().sendSuccess(() -> Component.translatable("youzaiworldcore.message.account.help_title"), false);
                            ctx.getSource().sendSuccess(() -> Component.translatable("youzaiworldcore.message.account.help_register"), false);
                            ctx.getSource().sendSuccess(() -> Component.translatable("youzaiworldcore.message.account.help_login"), false);
                            ctx.getSource().sendSuccess(() -> Component.translatable("youzaiworldcore.message.account.help_logout"), false);
                            ctx.getSource().sendSuccess(() -> Component.translatable("youzaiworldcore.message.account.help_deactivate"), false);
                            ctx.getSource().sendSuccess(() -> Component.translatable("youzaiworldcore.message.account.help_change_password"), false);
                            if (Commands.LEVEL_ADMINS.check(ctx.getSource().permissions())) {
                                ctx.getSource().sendSuccess(() -> Component.literal(""), false);
                                ctx.getSource().sendSuccess(() -> Component.translatable("youzaiworldcore.message.account.help_admin_title"), false);
                                ctx.getSource().sendSuccess(() -> Component.translatable("youzaiworldcore.message.account.help_admin_create"), false);
                                ctx.getSource().sendSuccess(() -> Component.translatable("youzaiworldcore.message.account.help_admin_reset_password"), false);
                                ctx.getSource().sendSuccess(() -> Component.translatable("youzaiworldcore.message.account.help_admin_delete"), false);
                                ctx.getSource().sendSuccess(() -> Component.translatable("youzaiworldcore.message.account.help_admin_session_timeout"), false);
                                ctx.getSource().sendSuccess(() -> Component.translatable("youzaiworldcore.message.account.current_timeout", AccountDataStorage.getSessionTimeout()), false);
                            }
                            return 1;
                        })
                )
        );
    }

    // ==================== 玩家命令实现 ====================

    /**
     * 注册
     */
    private static int executeRegister(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        ServerPlayer player = source.getPlayerOrException();
        PlayerAuthAccess authPlayer = (PlayerAuthAccess) (Object) player;

        String password = StringArgumentType.getString(ctx, "password");
        String confirm = StringArgumentType.getString(ctx, "confirm");

        if (!password.equals(confirm)) {
            source.sendFailure(Component.translatable("youzaiworldcore.message.account.password_mismatch"));
            return 0;
        }

        if (password.length() < 4) {
            source.sendFailure(Component.translatable("youzaiworldcore.message.account.password_too_short"));
            return 0;
        }

        if (password.length() > 128) {
            source.sendFailure(Component.translatable("youzaiworldcore.message.account.password_too_long"));
            return 0;
        }

        PlayerAccount account = AccountDataStorage.get(player.getScoreboardName());
        if (account == null) {
            source.sendFailure(Component.translatable("youzaiworldcore.message.account.account_not_found"));
            return 0;
        }

        if (account.isRegistered()) {
            source.sendFailure(Component.translatable("youzaiworldcore.message.account.already_registered"));
            return 0;
        }

        // 哈希密码并保存
        String hashed = PasswordHasher.hash(password);
        account.password = hashed;
        account.registrationDate = ZonedDateTime.now();
        account.lastAuthenticatedDate = ZonedDateTime.now();
        account.lastIp = authPlayer.yzwc$getIpAddress();
        AccountDataStorage.update(account);

        // 标记已认证
        authPlayer.yzwc$setAuthenticated(true);

        // 传送到原位置（如果有有效位置且不在虚空）
        AuthLocationData savedLoc = authPlayer.yzwc$getLastLocation();
        if (savedLoc != null && savedLoc.position != null && !isVoidLocation(savedLoc)) {
            AuthPlayerHelper.restoreLocation(player);
        } else {
            // 无有效位置（例如注销后重注册），传送到主世界出生点并清除所有重生点
            teleportToWorldSpawn(player);
        }
        // 清除持久化的位置缓存
        if (account.lastPositionJson != null) {
            account.lastPositionJson = null;
            AccountDataStorage.update(account);
        }

        source.sendSuccess(() -> Component.translatable("youzaiworldcore.message.account.register_success"), true);
        YouzaiworldCore.LOGGER.info("玩家 {} 注册成功", player.getScoreboardName());
        return 1;
    }

    /**
     * 登录
     */
    private static int executeLogin(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        ServerPlayer player = source.getPlayerOrException();
        PlayerAuthAccess authPlayer = (PlayerAuthAccess) (Object) player;

        if (authPlayer.yzwc$isAuthenticated()) {
            source.sendFailure(Component.translatable("youzaiworldcore.message.account.already_logged_in"));
            return 0;
        }

        String password = StringArgumentType.getString(ctx, "password");
        PlayerAccount account = authPlayer.yzwc$getAccount();

        if (account == null || !account.isRegistered()) {
            source.sendFailure(Component.translatable("youzaiworldcore.message.account.not_registered"));
            return 0;
        }

        // 检查登录次数限制
        if (account.loginTries >= 5) {
            source.sendFailure(Component.translatable("youzaiworldcore.message.account.login_too_many_attempts"));
            return 0;
        }

        AuthHelper.PasswordResult result = AuthHelper.checkPassword(account, password);
        switch (result) {
            case CORRECT -> {
                authPlayer.yzwc$setAuthenticated(true);
                account.lastAuthenticatedDate = ZonedDateTime.now();
                account.lastIp = authPlayer.yzwc$getIpAddress();
                account.loginTries = 0;
                AccountDataStorage.update(account);

                // 传送回原位置
                AuthPlayerHelper.restoreLocation(player);
                // 清除持久化的位置缓存
                if (account.lastPositionJson != null) {
                    account.lastPositionJson = null;
                    AccountDataStorage.update(account);
                }

                source.sendSuccess(() -> Component.translatable("youzaiworldcore.message.account.login_success"), true);
                YouzaiworldCore.LOGGER.info("玩家 {} 登录成功", player.getScoreboardName());
                return 1;
            }
            case WRONG -> {
                account.loginTries++;
                AccountDataStorage.update(account);
                source.sendFailure(Component.translatable("youzaiworldcore.message.account.wrong_password", 5 - account.loginTries));
                return 0;
            }
            case NOT_REGISTERED -> {
                source.sendFailure(Component.translatable("youzaiworldcore.message.account.not_registered"));
                return 0;
            }
        }
        return 0;
    }

    /**
     * 登出
     */
    private static int executeLogout(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        ServerPlayer player = source.getPlayerOrException();
        PlayerAuthAccess authPlayer = (PlayerAuthAccess) (Object) player;

        if (!authPlayer.yzwc$isAuthenticated()) {
            source.sendFailure(Component.translatable("youzaiworldcore.message.account.not_logged_in"));
            return 0;
        }

        // 保存当前位置到 mixin 内存
        authPlayer.yzwc$saveLocation();

        // 标记未认证
        authPlayer.yzwc$setAuthenticated(false);

        // 清除会话数据，防止重连时自动恢复
        PlayerAccount account = authPlayer.yzwc$getAccount();
        if (account != null) {
            account.lastIp = "";
            account.lastAuthenticatedDate = java.time.ZonedDateTime.of(1970, 1, 1, 0, 0, 0, 0, java.time.ZoneOffset.UTC);
            // 将保存的位置持久化到磁盘，防止重连后被 void 坐标覆盖
            AuthLocationData savedLoc = authPlayer.yzwc$getLastLocation();
            if (savedLoc != null) {
                account.lastPositionJson = savedLoc.toJson();
            }
            AccountDataStorage.update(account);
        }

        // 传送到虚空
        ServerLevel endWorld = player.level().getServer() != null
                ? player.level().getServer().getLevel(Level.END)
                : null;
        if (endWorld == null && player.level().getServer() != null) {
            endWorld = player.level().getServer().overworld();
        }
        ServerLevel finalEndWorld = endWorld;
        if (finalEndWorld != null) {
            player.teleportTo(finalEndWorld, 0, -60, 0, Set.of(), 0, 0, true);
        }

        source.sendSuccess(() -> Component.translatable("youzaiworldcore.message.account.logout_success"), true);
        YouzaiworldCore.LOGGER.info("玩家 {} 登出", player.getScoreboardName());
        return 1;
    }

    /**
     * 注销（删除账户）
     */
    private static int executeDeactivate(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        ServerPlayer player = source.getPlayerOrException();
        PlayerAuthAccess authPlayer = (PlayerAuthAccess) (Object) player;

        if (!authPlayer.yzwc$isAuthenticated()) {
            source.sendFailure(Component.translatable("youzaiworldcore.message.account.login_first_deactivate"));
            return 0;
        }

        String password = StringArgumentType.getString(ctx, "password");
        PlayerAccount account = authPlayer.yzwc$getAccount();

        if (account == null || !account.isRegistered()) {
            source.sendFailure(Component.translatable("youzaiworldcore.message.account.account_not_exist"));
            return 0;
        }

        AuthHelper.PasswordResult result = AuthHelper.checkPassword(account, password);
        if (result != AuthHelper.PasswordResult.CORRECT) {
            source.sendFailure(Component.translatable("youzaiworldcore.message.account.wrong_password_simple"));
            return 0;
        }

        // 删除账户
        AccountDataStorage.delete(player.getScoreboardName());
        authPlayer.yzwc$setAuthenticated(false);
        authPlayer.yzwc$setAccount(new PlayerAccount(player.getScoreboardName()));

        player.connection.disconnect(Component.translatable("youzaiworldcore.message.account.deactivated"));
        YouzaiworldCore.LOGGER.info("玩家 {} 注销了账户", player.getScoreboardName());
        return 1;
    }

    /**
     * 修改密码
     */
    private static int executeChangePassword(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        ServerPlayer player = source.getPlayerOrException();
        PlayerAuthAccess authPlayer = (PlayerAuthAccess) (Object) player;

        if (!authPlayer.yzwc$isAuthenticated()) {
            source.sendFailure(Component.translatable("youzaiworldcore.message.account.login_first_change_password"));
            return 0;
        }

        String oldPassword = StringArgumentType.getString(ctx, "oldPassword");
        String newPassword = StringArgumentType.getString(ctx, "newPassword");
        String confirmPassword = StringArgumentType.getString(ctx, "confirmPassword");
        PlayerAccount account = authPlayer.yzwc$getAccount();

        if (account == null || !account.isRegistered()) {
            source.sendFailure(Component.translatable("youzaiworldcore.message.account.account_not_exist"));
            return 0;
        }

        if (!newPassword.equals(confirmPassword)) {
            source.sendFailure(Component.translatable("youzaiworldcore.message.account.new_password_mismatch"));
            return 0;
        }

        AuthHelper.PasswordResult result = AuthHelper.checkPassword(account, oldPassword);
        if (result != AuthHelper.PasswordResult.CORRECT) {
            source.sendFailure(Component.translatable("youzaiworldcore.message.account.old_password_wrong"));
            return 0;
        }

        if (newPassword.length() < 4) {
            source.sendFailure(Component.translatable("youzaiworldcore.message.account.new_password_too_short"));
            return 0;
        }

        account.password = PasswordHasher.hash(newPassword);
        AccountDataStorage.update(account);

        source.sendSuccess(() -> Component.translatable("youzaiworldcore.message.account.change_password_success"), true);
        YouzaiworldCore.LOGGER.info("玩家 {} 修改了密码", player.getScoreboardName());
        return 1;
    }

    // ==================== 管理员命令实现 ====================

    /**
     * 管理员：创建离线玩家账户（含密码）
     * /yzwc account mgr create <玩家> <新密码> <确认密码>
     */
    private static int executeAdminCreate(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        String playerName = StringArgumentType.getString(ctx, "player");
        String newPassword = StringArgumentType.getString(ctx, "newPassword");
        String confirmPassword = StringArgumentType.getString(ctx, "confirmPassword");

        if (!newPassword.equals(confirmPassword)) {
            source.sendFailure(Component.translatable("youzaiworldcore.message.account.password_mismatch"));
            return 0;
        }

        if (newPassword.length() < 4) {
            source.sendFailure(Component.translatable("youzaiworldcore.message.account.password_too_short"));
            return 0;
        }

        // 检查是否已存在
        PlayerAccount existing = AccountDataStorage.get(playerName);
        if (existing != null && existing.isRegistered()) {
            source.sendFailure(Component.translatable("youzaiworldcore.message.account.player_already_has_account", playerName));
            return 0;
        }

        // 创建账户并设置密码
        PlayerAccount account = AccountDataStorage.getOrCreate(playerName, null);
        account.password = PasswordHasher.hash(newPassword);
        account.registrationDate = ZonedDateTime.now();
        AccountDataStorage.update(account);
        source.sendSuccess(() ->
                Component.translatable("youzaiworldcore.message.account.admin_create_success", playerName),
                true
        );
        YouzaiworldCore.LOGGER.info("管理员创建了玩家 {} 的离线账户", playerName);
        return 1;
    }

    /**
     * 管理员：重置玩家密码
     */
    private static int executeAdminResetPassword(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        String playerName = StringArgumentType.getString(ctx, "player");
        String newPassword = StringArgumentType.getString(ctx, "newPassword");
        String confirmPassword = StringArgumentType.getString(ctx, "confirmPassword");

        if (!newPassword.equals(confirmPassword)) {
            source.sendFailure(Component.translatable("youzaiworldcore.message.account.password_mismatch"));
            return 0;
        }

        if (newPassword.length() < 4) {
            source.sendFailure(Component.translatable("youzaiworldcore.message.account.password_too_short"));
            return 0;
        }

        PlayerAccount account = AccountDataStorage.get(playerName);
        if (account == null) {
            source.sendFailure(Component.translatable("youzaiworldcore.message.account.player_no_account", playerName));
            return 0;
        }

        // 重置密码
        account.password = PasswordHasher.hash(newPassword);
        AccountDataStorage.update(account);

        // 如果玩家在线，使其重新认证
        ServerPlayer onlinePlayer = source.getServer().getPlayerList().getPlayerByName(playerName);
        if (onlinePlayer != null) {
            PlayerAuthAccess authPlayer = (PlayerAuthAccess) (Object) onlinePlayer;
            authPlayer.yzwc$setAuthenticated(false);
            onlinePlayer.sendSystemMessage(Component.translatable("youzaiworldcore.message.account.admin_reset_notification"));
        }

        source.sendSuccess(() -> Component.translatable("youzaiworldcore.message.account.admin_reset_success", playerName), true);
        YouzaiworldCore.LOGGER.info("管理员重置了玩家 {} 的密码", playerName);
        return 1;
    }

    /**
     * 管理员：删除玩家账户
     */
    private static int executeAdminDelete(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        String playerName = StringArgumentType.getString(ctx, "player");

        PlayerAccount account = AccountDataStorage.get(playerName);
        if (account == null) {
            source.sendFailure(Component.translatable("youzaiworldcore.message.account.player_no_account", playerName));
            return 0;
        }

        if (!AccountDataStorage.delete(playerName)) {
            source.sendFailure(Component.translatable("youzaiworldcore.message.account.delete_failed"));
            return 0;
        }

        // 如果玩家在线，使其断开连接
        ServerPlayer onlinePlayer = source.getServer().getPlayerList().getPlayerByName(playerName);
        if (onlinePlayer != null) {
            PlayerAuthAccess authPlayer = (PlayerAuthAccess) (Object) onlinePlayer;
            authPlayer.yzwc$setAuthenticated(false);
            authPlayer.yzwc$setAccount(new PlayerAccount(playerName));
            onlinePlayer.connection.disconnect(Component.translatable("youzaiworldcore.message.account.admin_deleted"));
        }

        source.sendSuccess(() -> Component.translatable("youzaiworldcore.message.account.admin_delete_success", playerName), true);
        YouzaiworldCore.LOGGER.info("管理员删除了玩家 {} 的账户", playerName);
        return 1;
    }

    /**
     * 管理员：查看或设置会话超时时间
     * /yzwc account mgr session_timeout          — 查看当前值
     * /yzwc account mgr session_timeout <秒>     — 设置新值
     */
    private static int executeAdminSessionTimeout(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();

        // 尝试获取 seconds 参数（可选）
        int seconds;
        try {
            seconds = IntegerArgumentType.getInteger(ctx, "seconds");
        } catch (IllegalArgumentException e) {
            // 无参数 → 仅显示当前值
            int current = AccountDataStorage.getSessionTimeout();
            if (current == 0) {
                source.sendSuccess(() -> Component.translatable("youzaiworldcore.message.account.session_timeout_disabled"), false);
            } else {
                source.sendSuccess(() -> Component.translatable("youzaiworldcore.message.account.session_timeout_value", current), false);
            }
            return 1;
        }

        AccountDataStorage.setSessionTimeout(seconds);

        if (seconds == 0) {
            source.sendSuccess(() -> Component.translatable("youzaiworldcore.message.account.session_timeout_set_disabled"), true);
        } else {
            source.sendSuccess(() -> Component.translatable("youzaiworldcore.message.account.session_timeout_set", seconds), true);
        }
        YouzaiworldCore.LOGGER.info("管理员将会话超时设为 {} 秒", seconds);
        return 1;
    }

    // ===== 工具方法 =====

    /**
     * 判断是否为虚空坐标（The End 的 (0, -60, 0)）
     */
    private static boolean isVoidLocation(AuthLocationData loc) {
        if (loc.dimension == null) return false;
        String dim = loc.dimension.identifier().toString();
        if (!"minecraft:the_end".equals(dim)) return false;
        return Math.abs(loc.position.x) < 1 && Math.abs(loc.position.y + 60) < 1 && Math.abs(loc.position.z) < 1;
    }

    /**
     * 传送玩家到主世界出生点，并清除所有维度设置的重生点
     */
    private static void teleportToWorldSpawn(ServerPlayer player) {
        var server = player.level().getServer();
        if (server == null) return;
        var overworld = server.overworld();
        var spawnPos = overworld.getRespawnData().pos();

        // 传送到主世界出生点
        player.teleportTo(overworld, spawnPos.getX(), spawnPos.getY(), spawnPos.getZ(), java.util.Set.of(), player.getYRot(), player.getXRot(), true);

        // 清除所有重生点（设为默认）
        player.setRespawnPosition(new net.minecraft.server.level.ServerPlayer.RespawnConfig(
                net.minecraft.world.level.storage.LevelData.RespawnData.DEFAULT, false
        ), false);

        YouzaiworldCore.LOGGER.info("已将玩家 {} 传送至主世界出生点并清除重生点", player.getScoreboardName());
    }
}
