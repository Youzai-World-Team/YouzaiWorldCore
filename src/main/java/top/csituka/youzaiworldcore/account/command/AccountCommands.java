package top.csituka.youzaiworldcore.account.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import top.csituka.youzaiworldcore.YouzaiworldCore;
import top.csituka.youzaiworldcore.account.data.AccountDataStorage;
import top.csituka.youzaiworldcore.account.data.PlayerAccount;
import top.csituka.youzaiworldcore.account.data.PlayerAuthAccess;
import top.csituka.youzaiworldcore.account.util.AuthHelper;
import top.csituka.youzaiworldcore.account.util.AuthLocationData;
import top.csituka.youzaiworldcore.account.util.AuthPlayerHelper;
import top.csituka.youzaiworldcore.account.util.PasswordHasher;
import top.csituka.youzaiworldcore.invisibility.InvisibilityManager;
import top.csituka.youzaiworldcore.util.DebugLogger;

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
@SuppressWarnings({"null", "unused"})
public class AccountCommands {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        DebugLogger.entering("AccountCommands", "register");
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
                                .then(Commands.literal("login_cooldown")
                                        // 无参数：显示当前冷却设置
                                        .executes(ctx -> executeAdminLoginCooldownDisplay(ctx))
                                        // set <秒>：设置冷却时间
                                        .then(Commands.literal("set")
                                                .then(Commands.argument("seconds", com.mojang.brigadier.arguments.IntegerArgumentType.integer(-1, 86400))
                                                        .executes(ctx -> executeAdminLoginCooldownSet(ctx))
                                                )
                                        )
                                        // status <玩家>：查询账户状态
                                        .then(Commands.literal("status")
                                                .then(Commands.argument("player", StringArgumentType.string())
                                                        .executes(ctx -> executeAdminLoginCooldownStatus(ctx))
                                                )
                                        )
                                        // unlock <玩家>：解锁账户
                                        .then(Commands.literal("unlock")
                                                .then(Commands.argument("player", StringArgumentType.string())
                                                        .executes(ctx -> executeAdminLoginCooldownUnlock(ctx))
                                                )
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
                                ctx.getSource().sendSuccess(() -> Component.translatable("youzaiworldcore.message.account.help_admin_login_cooldown"), false);
                                ctx.getSource().sendSuccess(() -> Component.translatable("youzaiworldcore.message.account.help_admin_login_cooldown_status"), false);
                                ctx.getSource().sendSuccess(() -> Component.translatable("youzaiworldcore.message.account.help_admin_login_cooldown_unlock"), false);
                                ctx.getSource().sendSuccess(() -> Component.translatable("youzaiworldcore.message.account.current_login_cooldown", AccountDataStorage.getLoginCooldown()), false);
                            }
                            return 1;
                        })
                )
        );
        DebugLogger.exiting("AccountCommands", "register");
    }

    // ==================== 玩家命令实现 ====================

    /**
     * 注册
     */
    private static int executeRegister(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        DebugLogger.entering("AccountCommands", "executeRegister");
        CommandSourceStack source = ctx.getSource();
        ServerPlayer player = source.getPlayerOrException();
        PlayerAuthAccess authPlayer = (PlayerAuthAccess) (Object) player;

        String password = StringArgumentType.getString(ctx, "password");
        String confirm = StringArgumentType.getString(ctx, "confirm");

        if (!password.equals(confirm)) {
            DebugLogger.branch("AccountCommands", "password matches confirm", false);
            source.sendFailure(Component.translatable("youzaiworldcore.message.account.password_mismatch"));
            DebugLogger.exiting("AccountCommands", "executeRegister", "0 (password mismatch)");
            return 0;
        }
        DebugLogger.branch("AccountCommands", "password matches confirm", true);

        if (password.length() < 4) {
            DebugLogger.branch("AccountCommands", "password length >= 4", false);
            source.sendFailure(Component.translatable("youzaiworldcore.message.account.password_too_short"));
            DebugLogger.exiting("AccountCommands", "executeRegister", "0 (password too short)");
            return 0;
        }
        DebugLogger.branch("AccountCommands", "password length >= 4", true);

        if (password.length() > 128) {
            DebugLogger.branch("AccountCommands", "password length <= 128", false);
            source.sendFailure(Component.translatable("youzaiworldcore.message.account.password_too_long"));
            DebugLogger.exiting("AccountCommands", "executeRegister", "0 (password too long)");
            return 0;
        }
        DebugLogger.branch("AccountCommands", "password length <= 128", true);

        PlayerAccount account = AccountDataStorage.get(player.getScoreboardName());
        if (account == null) {
            DebugLogger.branch("AccountCommands", "account exists in storage", false);
            source.sendFailure(Component.translatable("youzaiworldcore.message.account.account_not_found"));
            DebugLogger.exiting("AccountCommands", "executeRegister", "0 (account not found in storage)");
            return 0;
        }
        DebugLogger.branch("AccountCommands", "account exists in storage", true);

        if (account.isRegistered()) {
            DebugLogger.branch("AccountCommands", "account already registered", true);
            source.sendFailure(Component.translatable("youzaiworldcore.message.account.already_registered"));
            DebugLogger.exiting("AccountCommands", "executeRegister", "0 (already registered)");
            return 0;
        }
        DebugLogger.branch("AccountCommands", "account already registered", false);

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
        if (savedLoc != null && savedLoc.position != null && !AuthPlayerHelper.isVoidLocation(savedLoc)) {
            DebugLogger.branch("AccountCommands", "valid saved location exists", true);
            AuthPlayerHelper.restoreLocation(player);
        } else {
            DebugLogger.branch("AccountCommands", "valid saved location exists", false);
            // 无有效位置（例如注销后重注册），传送到主世界出生点并清除所有重生点
            teleportToWorldSpawn(player);
        }
        // 清除持久化的位置缓存
        if (account.lastPositionJson != null) {
            DebugLogger.branch("AccountCommands", "lastPositionJson not null, clearing", true);
            account.lastPositionJson = null;
            AccountDataStorage.update(account);
        } else {
            DebugLogger.branch("AccountCommands", "lastPositionJson not null", false);
        }

        source.sendSuccess(() -> Component.translatable("youzaiworldcore.message.account.register_success"), true);
        DebugLogger.info("AccountCommands", "玩家 %s 注册成功", player.getScoreboardName());
        YouzaiworldCore.LOGGER.info("玩家 {} 注册成功", player.getScoreboardName());
        DebugLogger.exiting("AccountCommands", "executeRegister", "1 (success)");
        return 1;
    }

    /**
     * 登录
     */
    private static int executeLogin(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        DebugLogger.entering("AccountCommands", "executeLogin");
        CommandSourceStack source = ctx.getSource();
        ServerPlayer player = source.getPlayerOrException();
        PlayerAuthAccess authPlayer = (PlayerAuthAccess) (Object) player;

        if (authPlayer.yzwc$isAuthenticated()) {
            DebugLogger.branch("AccountCommands", "player already authenticated", true);
            source.sendFailure(Component.translatable("youzaiworldcore.message.account.already_logged_in"));
            DebugLogger.exiting("AccountCommands", "executeLogin", "0 (already logged in)");
            return 0;
        }
        DebugLogger.branch("AccountCommands", "player already authenticated", false);

        String password = StringArgumentType.getString(ctx, "password");
        PlayerAccount account = authPlayer.yzwc$getAccount();

        if (account == null || !account.isRegistered()) {
            DebugLogger.branch("AccountCommands", "account exists and registered", false);
            source.sendFailure(Component.translatable("youzaiworldcore.message.account.not_registered"));
            DebugLogger.exiting("AccountCommands", "executeLogin", "0 (not registered)");
            return 0;
        }
        DebugLogger.branch("AccountCommands", "account exists and registered", true);

        long cooldownSeconds = AccountDataStorage.getLoginCooldown();

        // ===== 登录尝试冷却/锁定检查 =====
        // cooldownSeconds == -1 : 永不锁定，跳过检查
        if (cooldownSeconds != -1 && account.loginTries >= 5) {
            DebugLogger.branch("AccountCommands", "login cooldown active", true, "tries=" + account.loginTries + ", cooldown=" + cooldownSeconds);
            if (cooldownSeconds > 0) {
                DebugLogger.branch("AccountCommands", "cooldown > 0 (timed)", true);
                // 限时冷却
                long elapsedSeconds = java.time.Duration.between(account.lastKickedDate, ZonedDateTime.now()).getSeconds();
                if (elapsedSeconds < cooldownSeconds) {
                    DebugLogger.branch("AccountCommands", "cooldown elapsed >= required", false, "elapsed=" + elapsedSeconds + ", required=" + cooldownSeconds);
                    long remaining = cooldownSeconds - elapsedSeconds;
                    source.sendFailure(Component.translatable(
                        "youzaiworldcore.message.account.cooldown_wait", formatCooldown(remaining)));
                    DebugLogger.exiting("AccountCommands", "executeLogin", "0 (cooldown active)");
                    return 0;
                }
                DebugLogger.branch("AccountCommands", "cooldown elapsed >= required", true, "elapsed=" + elapsedSeconds + ", required=" + cooldownSeconds);
                // 冷却已过 → 重置计数，允许重试
                account.loginTries = 0;
                account.lastKickedDate = java.time.ZonedDateTime.of(1970, 1, 1, 0, 0, 0, 0, java.time.ZoneOffset.UTC);
                AccountDataStorage.update(account);
                source.sendSuccess(() -> Component.translatable("youzaiworldcore.message.account.cooldown_expired"), false);
            } else {
                DebugLogger.branch("AccountCommands", "cooldown > 0 (timed)", false);
                // cooldownSeconds == 0 : 永久锁定
                source.sendFailure(Component.translatable("youzaiworldcore.message.account.cooldown_permanent"));
                DebugLogger.exiting("AccountCommands", "executeLogin", "0 (permanently locked)");
                return 0;
            }
        } else {
            DebugLogger.branch("AccountCommands", "login cooldown active", false, "tries=" + account.loginTries + ", cooldown=" + cooldownSeconds);
        }

        AuthHelper.PasswordResult result = AuthHelper.checkPassword(account, password);
        switch (result) {
            case CORRECT -> {
                DebugLogger.branch("AccountCommands", "password check result", true, "CORRECT");
                authPlayer.yzwc$setAuthenticated(true);
                account.lastAuthenticatedDate = ZonedDateTime.now();
                account.lastIp = authPlayer.yzwc$getIpAddress();
                account.loginTries = 0;
                AccountDataStorage.update(account);

                // 传送回原位置
                AuthPlayerHelper.restoreLocation(player);
                // 清除持久化的位置缓存
                if (account.lastPositionJson != null) {
                    DebugLogger.branch("AccountCommands", "lastPositionJson not null, clearing", true);
                    account.lastPositionJson = null;
                    AccountDataStorage.update(account);
                } else {
                    DebugLogger.branch("AccountCommands", "lastPositionJson not null", false);
                }

                source.sendSuccess(() -> Component.translatable("youzaiworldcore.message.account.login_success"), true);
                DebugLogger.info("AccountCommands", "玩家 %s 登录成功", player.getScoreboardName());
                YouzaiworldCore.LOGGER.info("玩家 {} 登录成功", player.getScoreboardName());
                DebugLogger.exiting("AccountCommands", "executeLogin", "1 (success)");
                return 1;
            }
            case WRONG -> {
                DebugLogger.branch("AccountCommands", "password check result", false, "WRONG");
                account.loginTries++;
                // 刚达到 5 次 → 记录锁定时间，以便冷却计时
                if (account.loginTries >= 5) {
                    DebugLogger.branch("AccountCommands", "login tries >= 5, recording lock time", true, "tries=" + account.loginTries);
                    account.lastKickedDate = ZonedDateTime.now();
                } else {
                    DebugLogger.branch("AccountCommands", "login tries >= 5", false, "tries=" + account.loginTries);
                }
                AccountDataStorage.update(account);
                source.sendFailure(Component.translatable("youzaiworldcore.message.account.wrong_password", 5 - account.loginTries));
                DebugLogger.exiting("AccountCommands", "executeLogin", "0 (wrong password, tries left=" + (5 - account.loginTries) + ")");
                return 0;
            }
            case NOT_REGISTERED -> {
                DebugLogger.branch("AccountCommands", "password check result", false, "NOT_REGISTERED");
                source.sendFailure(Component.translatable("youzaiworldcore.message.account.not_registered"));
                DebugLogger.exiting("AccountCommands", "executeLogin", "0 (not registered)");
                return 0;
            }
        }
        DebugLogger.exiting("AccountCommands", "executeLogin", "0 (fallthrough)");
        return 0;
    }

    /**
     * 登出
     */
    private static int executeLogout(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        DebugLogger.entering("AccountCommands", "executeLogout");
        CommandSourceStack source = ctx.getSource();
        ServerPlayer player = source.getPlayerOrException();
        PlayerAuthAccess authPlayer = (PlayerAuthAccess) (Object) player;

        if (InvisibilityManager.isInvisible(player)) {
            DebugLogger.branch("AccountCommands", "player is invisible", true);
            source.sendFailure(Component.translatable("youzaiworldcore.message.account.invisibility_blocked"));
            DebugLogger.exiting("AccountCommands", "executeLogout", "0 (invisible)");
            return 0;
        }
        DebugLogger.branch("AccountCommands", "player is invisible", false);

        if (!authPlayer.yzwc$isAuthenticated()) {
            DebugLogger.branch("AccountCommands", "player is authenticated", false);
            source.sendFailure(Component.translatable("youzaiworldcore.message.account.not_logged_in"));
            DebugLogger.exiting("AccountCommands", "executeLogout", "0 (not logged in)");
            return 0;
        }
        DebugLogger.branch("AccountCommands", "player is authenticated", true);

        // 保存当前位置到 mixin 内存
        authPlayer.yzwc$saveLocation();

        // 标记未认证
        authPlayer.yzwc$setAuthenticated(false);

        // 清除会话数据，防止重连时自动恢复
        PlayerAccount account = authPlayer.yzwc$getAccount();
        if (account != null) {
            DebugLogger.branch("AccountCommands", "account is null", false);
            account.lastIp = "";
            account.lastAuthenticatedDate = java.time.ZonedDateTime.of(1970, 1, 1, 0, 0, 0, 0, java.time.ZoneOffset.UTC);
            // 将保存的位置持久化到磁盘，防止重连后被 void 坐标覆盖
            AuthLocationData savedLoc = authPlayer.yzwc$getLastLocation();
            if (savedLoc != null) {
                DebugLogger.branch("AccountCommands", "saved location exists", true);
                account.lastPositionJson = savedLoc.toJson();
            } else {
                DebugLogger.branch("AccountCommands", "saved location exists", false);
            }
            AccountDataStorage.update(account);
        } else {
            DebugLogger.branch("AccountCommands", "account is null", true);
        }

        // 传送到登录大厅
        ResourceKey<Level> loginHallKey = AuthPlayerHelper.LOGIN_HALL_KEY;
        ServerLevel loginHall = player.level().getServer() != null
                ? player.level().getServer().getLevel(loginHallKey)
                : null;
        if (loginHall == null && player.level().getServer() != null) {
            loginHall = player.level().getServer().overworld();
        }
        ServerLevel finalLoginHall = loginHall;
        if (finalLoginHall != null) {
            player.teleportTo(finalLoginHall, AuthPlayerHelper.LOGIN_HALL_X, AuthPlayerHelper.LOGIN_HALL_Y,
                    AuthPlayerHelper.LOGIN_HALL_Z, Set.of(), 0, 0, true);
        }

        source.sendSuccess(() -> Component.translatable("youzaiworldcore.message.account.logout_success"), true);
        DebugLogger.info("AccountCommands", "玩家 %s 登出", player.getScoreboardName());
        YouzaiworldCore.LOGGER.info("玩家 {} 登出", player.getScoreboardName());
        DebugLogger.exiting("AccountCommands", "executeLogout", "1 (success)");
        return 1;
    }

    /**
     * 注销（删除账户）
     */
    private static int executeDeactivate(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        DebugLogger.entering("AccountCommands", "executeDeactivate");
        CommandSourceStack source = ctx.getSource();
        ServerPlayer player = source.getPlayerOrException();
        PlayerAuthAccess authPlayer = (PlayerAuthAccess) (Object) player;

        if (InvisibilityManager.isInvisible(player)) {
            DebugLogger.branch("AccountCommands", "player is invisible", true);
            source.sendFailure(Component.translatable("youzaiworldcore.message.account.invisibility_blocked"));
            DebugLogger.exiting("AccountCommands", "executeDeactivate", "0 (invisible)");
            return 0;
        }
        DebugLogger.branch("AccountCommands", "player is invisible", false);

        if (!authPlayer.yzwc$isAuthenticated()) {
            DebugLogger.branch("AccountCommands", "player is authenticated", false);
            source.sendFailure(Component.translatable("youzaiworldcore.message.account.login_first_deactivate"));
            DebugLogger.exiting("AccountCommands", "executeDeactivate", "0 (not logged in)");
            return 0;
        }
        DebugLogger.branch("AccountCommands", "player is authenticated", true);

        String password = StringArgumentType.getString(ctx, "password");
        PlayerAccount account = authPlayer.yzwc$getAccount();

        if (account == null || !account.isRegistered()) {
            DebugLogger.branch("AccountCommands", "account exists and registered", false);
            source.sendFailure(Component.translatable("youzaiworldcore.message.account.account_not_exist"));
            DebugLogger.exiting("AccountCommands", "executeDeactivate", "0 (account not exist)");
            return 0;
        }
        DebugLogger.branch("AccountCommands", "account exists and registered", true);

        AuthHelper.PasswordResult result = AuthHelper.checkPassword(account, password);
        if (result != AuthHelper.PasswordResult.CORRECT) {
            DebugLogger.branch("AccountCommands", "password is correct", false);
            source.sendFailure(Component.translatable("youzaiworldcore.message.account.wrong_password_simple"));
            DebugLogger.exiting("AccountCommands", "executeDeactivate", "0 (wrong password)");
            return 0;
        }
        DebugLogger.branch("AccountCommands", "password is correct", true);

        // 删除账户
        AccountDataStorage.delete(player.getScoreboardName());
        authPlayer.yzwc$setAuthenticated(false);
        authPlayer.yzwc$setAccount(new PlayerAccount(player.getScoreboardName()));

        player.connection.disconnect(Component.translatable("youzaiworldcore.message.account.deactivated"));
        DebugLogger.info("AccountCommands", "玩家 %s 注销了账户", player.getScoreboardName());
        YouzaiworldCore.LOGGER.info("玩家 {} 注销了账户", player.getScoreboardName());
        DebugLogger.exiting("AccountCommands", "executeDeactivate", "1 (success)");
        return 1;
    }

    /**
     * 修改密码
     */
    private static int executeChangePassword(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        DebugLogger.entering("AccountCommands", "executeChangePassword");
        CommandSourceStack source = ctx.getSource();
        ServerPlayer player = source.getPlayerOrException();
        PlayerAuthAccess authPlayer = (PlayerAuthAccess) (Object) player;

        if (InvisibilityManager.isInvisible(player)) {
            DebugLogger.branch("AccountCommands", "player is invisible", true);
            source.sendFailure(Component.translatable("youzaiworldcore.message.account.invisibility_blocked"));
            DebugLogger.exiting("AccountCommands", "executeChangePassword", "0 (invisible)");
            return 0;
        }
        DebugLogger.branch("AccountCommands", "player is invisible", false);

        if (!authPlayer.yzwc$isAuthenticated()) {
            DebugLogger.branch("AccountCommands", "player is authenticated", false);
            source.sendFailure(Component.translatable("youzaiworldcore.message.account.login_first_change_password"));
            DebugLogger.exiting("AccountCommands", "executeChangePassword", "0 (not logged in)");
            return 0;
        }
        DebugLogger.branch("AccountCommands", "player is authenticated", true);

        String oldPassword = StringArgumentType.getString(ctx, "oldPassword");
        String newPassword = StringArgumentType.getString(ctx, "newPassword");
        String confirmPassword = StringArgumentType.getString(ctx, "confirmPassword");
        PlayerAccount account = authPlayer.yzwc$getAccount();

        if (account == null || !account.isRegistered()) {
            DebugLogger.branch("AccountCommands", "account exists and registered", false);
            source.sendFailure(Component.translatable("youzaiworldcore.message.account.account_not_exist"));
            DebugLogger.exiting("AccountCommands", "executeChangePassword", "0 (account not exist)");
            return 0;
        }
        DebugLogger.branch("AccountCommands", "account exists and registered", true);

        if (!newPassword.equals(confirmPassword)) {
            DebugLogger.branch("AccountCommands", "new passwords match", false);
            source.sendFailure(Component.translatable("youzaiworldcore.message.account.new_password_mismatch"));
            DebugLogger.exiting("AccountCommands", "executeChangePassword", "0 (new password mismatch)");
            return 0;
        }
        DebugLogger.branch("AccountCommands", "new passwords match", true);

        AuthHelper.PasswordResult result = AuthHelper.checkPassword(account, oldPassword);
        if (result != AuthHelper.PasswordResult.CORRECT) {
            DebugLogger.branch("AccountCommands", "old password check", false);
            source.sendFailure(Component.translatable("youzaiworldcore.message.account.old_password_wrong"));
            DebugLogger.exiting("AccountCommands", "executeChangePassword", "0 (old password wrong)");
            return 0;
        }
        DebugLogger.branch("AccountCommands", "old password check", true);

        if (newPassword.length() < 4) {
            DebugLogger.branch("AccountCommands", "new password length >= 4", false);
            source.sendFailure(Component.translatable("youzaiworldcore.message.account.new_password_too_short"));
            DebugLogger.exiting("AccountCommands", "executeChangePassword", "0 (new password too short)");
            return 0;
        }
        DebugLogger.branch("AccountCommands", "new password length >= 4", true);

        account.password = PasswordHasher.hash(newPassword);
        AccountDataStorage.update(account);

        source.sendSuccess(() -> Component.translatable("youzaiworldcore.message.account.change_password_success"), true);
        DebugLogger.info("AccountCommands", "玩家 %s 修改了密码", player.getScoreboardName());
        YouzaiworldCore.LOGGER.info("玩家 {} 修改了密码", player.getScoreboardName());
        DebugLogger.exiting("AccountCommands", "executeChangePassword", "1 (success)");
        return 1;
    }

    // ==================== 管理员命令实现 ====================

    /**
     * 管理员：创建离线玩家账户（含密码）
     * /yzwc account mgr create <玩家> <新密码> <确认密码>
     */
    private static int executeAdminCreate(CommandContext<CommandSourceStack> ctx) {
        DebugLogger.entering("AccountCommands", "executeAdminCreate");
        CommandSourceStack source = ctx.getSource();
        String playerName = StringArgumentType.getString(ctx, "player");
        String newPassword = StringArgumentType.getString(ctx, "newPassword");
        String confirmPassword = StringArgumentType.getString(ctx, "confirmPassword");

        if (!newPassword.equals(confirmPassword)) {
            DebugLogger.branch("AccountCommands", "passwords match", false);
            source.sendFailure(Component.translatable("youzaiworldcore.message.account.password_mismatch"));
            DebugLogger.exiting("AccountCommands", "executeAdminCreate", "0 (password mismatch)");
            return 0;
        }
        DebugLogger.branch("AccountCommands", "passwords match", true);

        if (newPassword.length() < 4) {
            DebugLogger.branch("AccountCommands", "password length >= 4", false);
            source.sendFailure(Component.translatable("youzaiworldcore.message.account.password_too_short"));
            DebugLogger.exiting("AccountCommands", "executeAdminCreate", "0 (password too short)");
            return 0;
        }
        DebugLogger.branch("AccountCommands", "password length >= 4", true);

        // 检查是否已存在
        PlayerAccount existing = AccountDataStorage.get(playerName);
        if (existing != null && existing.isRegistered()) {
            DebugLogger.branch("AccountCommands", "player already has account", true);
            source.sendFailure(Component.translatable("youzaiworldcore.message.account.player_already_has_account", playerName));
            DebugLogger.exiting("AccountCommands", "executeAdminCreate", "0 (already exists)");
            return 0;
        }
        DebugLogger.branch("AccountCommands", "player already has account", false);

        // 创建账户并设置密码
        PlayerAccount account = AccountDataStorage.getOrCreate(playerName, null);
        account.password = PasswordHasher.hash(newPassword);
        account.registrationDate = ZonedDateTime.now();
        AccountDataStorage.update(account);
        source.sendSuccess(() ->
                Component.translatable("youzaiworldcore.message.account.admin_create_success", playerName),
                true
        );
        DebugLogger.info("AccountCommands", "管理员创建了玩家 %s 的离线账户", playerName);
        YouzaiworldCore.LOGGER.info("管理员创建了玩家 {} 的离线账户", playerName);
        DebugLogger.exiting("AccountCommands", "executeAdminCreate", "1 (success)");
        return 1;
    }

    /**
     * 管理员：重置玩家密码
     */
    private static int executeAdminResetPassword(CommandContext<CommandSourceStack> ctx) {
        DebugLogger.entering("AccountCommands", "executeAdminResetPassword");
        CommandSourceStack source = ctx.getSource();
        String playerName = StringArgumentType.getString(ctx, "player");
        String newPassword = StringArgumentType.getString(ctx, "newPassword");
        String confirmPassword = StringArgumentType.getString(ctx, "confirmPassword");

        if (!newPassword.equals(confirmPassword)) {
            DebugLogger.branch("AccountCommands", "passwords match", false);
            source.sendFailure(Component.translatable("youzaiworldcore.message.account.password_mismatch"));
            DebugLogger.exiting("AccountCommands", "executeAdminResetPassword", "0 (password mismatch)");
            return 0;
        }
        DebugLogger.branch("AccountCommands", "passwords match", true);

        if (newPassword.length() < 4) {
            DebugLogger.branch("AccountCommands", "password length >= 4", false);
            source.sendFailure(Component.translatable("youzaiworldcore.message.account.password_too_short"));
            DebugLogger.exiting("AccountCommands", "executeAdminResetPassword", "0 (password too short)");
            return 0;
        }
        DebugLogger.branch("AccountCommands", "password length >= 4", true);

        PlayerAccount account = AccountDataStorage.get(playerName);
        if (account == null) {
            DebugLogger.branch("AccountCommands", "account exists", false);
            source.sendFailure(Component.translatable("youzaiworldcore.message.account.player_no_account", playerName));
            DebugLogger.exiting("AccountCommands", "executeAdminResetPassword", "0 (no account)");
            return 0;
        }
        DebugLogger.branch("AccountCommands", "account exists", true);

        // 重置密码
        account.password = PasswordHasher.hash(newPassword);
        AccountDataStorage.update(account);

        // 如果玩家在线，使其重新认证
        ServerPlayer onlinePlayer = source.getServer().getPlayerList().getPlayerByName(playerName);
        if (onlinePlayer != null) {
            DebugLogger.branch("AccountCommands", "player is online, forcing re-auth", true);
            PlayerAuthAccess authPlayer = (PlayerAuthAccess) (Object) onlinePlayer;
            authPlayer.yzwc$setAuthenticated(false);
            onlinePlayer.sendSystemMessage(Component.translatable("youzaiworldcore.message.account.admin_reset_notification"));
        } else {
            DebugLogger.branch("AccountCommands", "player is online, forcing re-auth", false);
        }

        source.sendSuccess(() -> Component.translatable("youzaiworldcore.message.account.admin_reset_success", playerName), true);
        DebugLogger.info("AccountCommands", "管理员重置了玩家 %s 的密码", playerName);
        YouzaiworldCore.LOGGER.info("管理员重置了玩家 {} 的密码", playerName);
        DebugLogger.exiting("AccountCommands", "executeAdminResetPassword", "1 (success)");
        return 1;
    }

    /**
     * 管理员：删除玩家账户
     */
    private static int executeAdminDelete(CommandContext<CommandSourceStack> ctx) {
        DebugLogger.entering("AccountCommands", "executeAdminDelete");
        CommandSourceStack source = ctx.getSource();
        String playerName = StringArgumentType.getString(ctx, "player");

        PlayerAccount account = AccountDataStorage.get(playerName);
        if (account == null) {
            DebugLogger.branch("AccountCommands", "account exists", false);
            source.sendFailure(Component.translatable("youzaiworldcore.message.account.player_no_account", playerName));
            DebugLogger.exiting("AccountCommands", "executeAdminDelete", "0 (no account)");
            return 0;
        }
        DebugLogger.branch("AccountCommands", "account exists", true);

        if (!AccountDataStorage.delete(playerName)) {
            DebugLogger.branch("AccountCommands", "delete succeeded", false);
            source.sendFailure(Component.translatable("youzaiworldcore.message.account.delete_failed"));
            DebugLogger.exiting("AccountCommands", "executeAdminDelete", "0 (delete failed)");
            return 0;
        }
        DebugLogger.branch("AccountCommands", "delete succeeded", true);

        // 如果玩家在线，使其断开连接
        ServerPlayer onlinePlayer = source.getServer().getPlayerList().getPlayerByName(playerName);
        if (onlinePlayer != null) {
            DebugLogger.branch("AccountCommands", "player is online, disconnecting", true);
            PlayerAuthAccess authPlayer = (PlayerAuthAccess) (Object) onlinePlayer;
            authPlayer.yzwc$setAuthenticated(false);
            authPlayer.yzwc$setAccount(new PlayerAccount(playerName));
            onlinePlayer.connection.disconnect(Component.translatable("youzaiworldcore.message.account.admin_deleted"));
        } else {
            DebugLogger.branch("AccountCommands", "player is online", false);
        }

        source.sendSuccess(() -> Component.translatable("youzaiworldcore.message.account.admin_delete_success", playerName), true);
        DebugLogger.info("AccountCommands", "管理员删除了玩家 %s 的账户", playerName);
        YouzaiworldCore.LOGGER.info("管理员删除了玩家 {} 的账户", playerName);
        DebugLogger.exiting("AccountCommands", "executeAdminDelete", "1 (success)");
        return 1;
    }

    /**
     * 管理员：查看或设置会话超时时间
     * /yzwc account mgr session_timeout          — 查看当前值
     * /yzwc account mgr session_timeout <秒>     — 设置新值
     */
    private static int executeAdminSessionTimeout(CommandContext<CommandSourceStack> ctx) {
        DebugLogger.entering("AccountCommands", "executeAdminSessionTimeout");
        CommandSourceStack source = ctx.getSource();

        // 尝试获取 seconds 参数（可选）
        int seconds;
        try {
            seconds = IntegerArgumentType.getInteger(ctx, "seconds");
            DebugLogger.branch("AccountCommands", "seconds argument provided", true, "seconds=" + seconds);
        } catch (IllegalArgumentException e) {
            DebugLogger.branch("AccountCommands", "seconds argument provided", false);
            // 无参数 → 仅显示当前值
            int current = AccountDataStorage.getSessionTimeout();
            if (current == 0) {
                DebugLogger.branch("AccountCommands", "session timeout is disabled", true);
                source.sendSuccess(() -> Component.translatable("youzaiworldcore.message.account.session_timeout_disabled"), false);
            } else {
                DebugLogger.branch("AccountCommands", "session timeout is disabled", false, "current=" + current);
                source.sendSuccess(() -> Component.translatable("youzaiworldcore.message.account.session_timeout_value", current), false);
            }
            DebugLogger.exiting("AccountCommands", "executeAdminSessionTimeout", "1 (display only)");
            return 1;
        }

        AccountDataStorage.setSessionTimeout(seconds);

        if (seconds == 0) {
            DebugLogger.branch("AccountCommands", "setting timeout to 0 (disabled)", true);
            source.sendSuccess(() -> Component.translatable("youzaiworldcore.message.account.session_timeout_set_disabled"), true);
        } else {
            DebugLogger.branch("AccountCommands", "setting timeout to 0 (disabled)", false, "seconds=" + seconds);
            source.sendSuccess(() -> Component.translatable("youzaiworldcore.message.account.session_timeout_set", seconds), true);
        }
        DebugLogger.info("AccountCommands", "管理员将会话超时设为 %d 秒", seconds);
        YouzaiworldCore.LOGGER.info("管理员将会话超时设为 {} 秒", seconds);
        DebugLogger.exiting("AccountCommands", "executeAdminSessionTimeout", "1 (set)");
        return 1;
    }

    /**
     * 管理员：显示当前登录冷却设置
     * /yzwc account mgr login_cooldown set
     */
    private static int executeAdminLoginCooldownDisplay(CommandContext<CommandSourceStack> ctx) {
        DebugLogger.entering("AccountCommands", "executeAdminLoginCooldownDisplay");
        CommandSourceStack source = ctx.getSource();
        int current = AccountDataStorage.getLoginCooldown();
        if (current == -1) {
            DebugLogger.branch("AccountCommands", "cooldown == -1 (never lock)", true);
            source.sendSuccess(() -> Component.translatable("youzaiworldcore.message.account.admin_cooldown_never"), false);
        } else if (current == 0) {
            DebugLogger.branch("AccountCommands", "cooldown == 0 (permanent lock)", true);
            source.sendSuccess(() -> Component.translatable("youzaiworldcore.message.account.admin_cooldown_permanent"), false);
        } else {
            DebugLogger.branch("AccountCommands", "cooldown > 0 (timed)", false, "value=" + current);
            source.sendSuccess(() -> Component.translatable("youzaiworldcore.message.account.admin_cooldown_value", formatCooldown(current)), false);
        }
        DebugLogger.exiting("AccountCommands", "executeAdminLoginCooldownDisplay", "1");
        return 1;
    }

    /**
     * 管理员：设置登录失败锁定冷却时间
     * /yzwc account mgr login_cooldown set <秒>
     */
    private static int executeAdminLoginCooldownSet(CommandContext<CommandSourceStack> ctx) {
        DebugLogger.entering("AccountCommands", "executeAdminLoginCooldownSet");
        CommandSourceStack source = ctx.getSource();
        int seconds = IntegerArgumentType.getInteger(ctx, "seconds");
        AccountDataStorage.setLoginCooldown(seconds);

        if (seconds == -1) {
            DebugLogger.branch("AccountCommands", "cooldown set to -1 (never lock)", true);
            source.sendSuccess(() -> Component.translatable("youzaiworldcore.message.account.admin_cooldown_set_never"), true);
        } else if (seconds == 0) {
            DebugLogger.branch("AccountCommands", "cooldown set to 0 (permanent lock)", true);
            source.sendSuccess(() -> Component.translatable("youzaiworldcore.message.account.admin_cooldown_set_permanent"), true);
        } else {
            DebugLogger.branch("AccountCommands", "cooldown set to timed value", false, "seconds=" + seconds);
            source.sendSuccess(() -> Component.translatable("youzaiworldcore.message.account.admin_cooldown_set_value", formatCooldown(seconds)), true);
        }
        DebugLogger.info("AccountCommands", "管理员将登录锁定冷却设为 %d 秒", seconds);
        YouzaiworldCore.LOGGER.info("管理员将登录锁定冷却设为 {} 秒", seconds);
        DebugLogger.exiting("AccountCommands", "executeAdminLoginCooldownSet", "1");
        return 1;
    }

    /**
     * 管理员：查询指定玩家的账户锁定状态
     * /yzwc account mgr login_cooldown status <玩家>
     */
    private static int executeAdminLoginCooldownStatus(CommandContext<CommandSourceStack> ctx) {
        DebugLogger.entering("AccountCommands", "executeAdminLoginCooldownStatus");
        CommandSourceStack source = ctx.getSource();
        String playerName = StringArgumentType.getString(ctx, "player");

        PlayerAccount account = AccountDataStorage.get(playerName);
        if (account == null) {
            DebugLogger.branch("AccountCommands", "account exists", false);
            source.sendFailure(Component.translatable("youzaiworldcore.message.account.admin_player_no_account", playerName));
            DebugLogger.exiting("AccountCommands", "executeAdminLoginCooldownStatus", "0 (no account)");
            return 0;
        }
        DebugLogger.branch("AccountCommands", "account exists", true);

        int cooldownGlobal = AccountDataStorage.getLoginCooldown();
        int tries = account.loginTries;

        source.sendSuccess(() -> Component.translatable("youzaiworldcore.message.account.admin_cooldown_status_title", playerName), false);
        String color = (tries >= 5) ? "c" : "a";
        source.sendSuccess(() -> Component.translatable("youzaiworldcore.message.account.admin_cooldown_status_tries", color, String.valueOf(tries)), false);

        if (cooldownGlobal == -1) {
            DebugLogger.branch("AccountCommands", "global cooldown == -1", true);
            source.sendSuccess(() -> Component.translatable("youzaiworldcore.message.account.admin_cooldown_status_global_never"), false);
        } else if (tries < 5) {
            DebugLogger.branch("AccountCommands", "tries < 5, no lock", true, "tries=" + tries);
            source.sendSuccess(() -> Component.translatable("youzaiworldcore.message.account.admin_cooldown_status_normal"), false);
        } else if (cooldownGlobal == 0) {
            DebugLogger.branch("AccountCommands", "permanently locked", true);
            source.sendSuccess(() -> Component.translatable("youzaiworldcore.message.account.admin_cooldown_status_permanent"), false);
        } else {
            DebugLogger.branch("AccountCommands", "timed lock, checking elapsed", true);
            long elapsedSeconds = java.time.Duration.between(account.lastKickedDate, ZonedDateTime.now()).getSeconds();
            if (elapsedSeconds < cooldownGlobal) {
                DebugLogger.branch("AccountCommands", "lock still active", true, "elapsed=" + elapsedSeconds + ", cooldown=" + cooldownGlobal);
                long remaining = cooldownGlobal - elapsedSeconds;
                source.sendSuccess(() -> Component.translatable("youzaiworldcore.message.account.admin_cooldown_status_blocked", formatCooldown(remaining)), false);
            } else {
                DebugLogger.branch("AccountCommands", "lock expired", false);
                source.sendSuccess(() -> Component.translatable("youzaiworldcore.message.account.admin_cooldown_status_expired"), false);
            }
        }
        String globalDesc = cooldownGlobal == -1 ? "永不锁定" : cooldownGlobal == 0 ? "永久锁定" : formatCooldown(cooldownGlobal);
        source.sendSuccess(() -> Component.translatable("youzaiworldcore.message.account.admin_cooldown_status_global", globalDesc), false);
        DebugLogger.exiting("AccountCommands", "executeAdminLoginCooldownStatus", "1");
        return 1;
    }

    /**
     * 管理员：解锁指定玩家的账户（重置登录尝试计数）
     * /yzwc account mgr login_cooldown unlock <玩家>
     */
    private static int executeAdminLoginCooldownUnlock(CommandContext<CommandSourceStack> ctx) {
        DebugLogger.entering("AccountCommands", "executeAdminLoginCooldownUnlock");
        CommandSourceStack source = ctx.getSource();
        String playerName = StringArgumentType.getString(ctx, "player");

        PlayerAccount account = AccountDataStorage.get(playerName);
        if (account == null) {
            DebugLogger.branch("AccountCommands", "account exists", false);
            source.sendFailure(Component.translatable("youzaiworldcore.message.account.admin_player_no_account", playerName));
            DebugLogger.exiting("AccountCommands", "executeAdminLoginCooldownUnlock", "0 (no account)");
            return 0;
        }
        DebugLogger.branch("AccountCommands", "account exists", true);

        if (account.loginTries < 5) {
            DebugLogger.branch("AccountCommands", "account is locked (tries >= 5)", false, "tries=" + account.loginTries);
            source.sendFailure(Component.translatable("youzaiworldcore.message.account.admin_cooldown_not_locked", playerName, String.valueOf(account.loginTries)));
            DebugLogger.exiting("AccountCommands", "executeAdminLoginCooldownUnlock", "0 (not locked)");
            return 0;
        }
        DebugLogger.branch("AccountCommands", "account is locked (tries >= 5)", true, "tries=" + account.loginTries);

        account.loginTries = 0;
        account.lastKickedDate = java.time.ZonedDateTime.of(1970, 1, 1, 0, 0, 0, 0, java.time.ZoneOffset.UTC);
        AccountDataStorage.update(account);

        // 如果玩家在线，通知他
        ServerPlayer onlinePlayer = source.getServer().getPlayerList().getPlayerByName(playerName);
        if (onlinePlayer != null) {
            DebugLogger.branch("AccountCommands", "player online, notifying", true);
            onlinePlayer.sendSystemMessage(Component.translatable("youzaiworldcore.message.account.admin_cooldown_unlock_notification"));
        } else {
            DebugLogger.branch("AccountCommands", "player online", false);
        }

        source.sendSuccess(() -> Component.translatable("youzaiworldcore.message.account.admin_cooldown_unlock_success", playerName), true);
        DebugLogger.info("AccountCommands", "管理员解锁了玩家 %s 的账户", playerName);
        YouzaiworldCore.LOGGER.info("管理员解锁了玩家 {} 的账户", playerName);
        DebugLogger.exiting("AccountCommands", "executeAdminLoginCooldownUnlock", "1 (success)");
        return 1;
    }

    // ===== 工具方法 =====


    /**
     * 传送玩家到主世界出生点，并清除所有维度设置的重生点
     */
    private static void teleportToWorldSpawn(ServerPlayer player) {
        var server = player.level().getServer();
        if (server == null) return; // dead code guard
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

    /**
     * 将秒数格式化为人类可读的时间字符串。
     * <p>
     * 示例：3661 秒 → "1小时1分钟1秒"
     * </p>
     */
    private static String formatCooldown(long seconds) {
        if (seconds <= 0) return "0秒";
        long days = seconds / 86400;
        long hours = (seconds % 86400) / 3600;
        long minutes = (seconds % 3600) / 60;
        long secs = seconds % 60;
        StringBuilder sb = new StringBuilder();
        if (days > 0) sb.append(days).append("天");
        if (hours > 0) sb.append(hours).append("小时");
        if (minutes > 0) sb.append(minutes).append("分钟");
        if (secs > 0 || sb.isEmpty()) sb.append(secs).append("秒");
        return sb.toString();
    }
}
