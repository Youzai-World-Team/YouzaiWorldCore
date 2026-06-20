package top.csituka.youzaiworldcore;

import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.DimensionArgument;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import top.csituka.youzaiworldcore.account.AccountManager;
import top.csituka.youzaiworldcore.account.LoginState;
import top.csituka.youzaiworldcore.block.ModBlocks;
import top.csituka.youzaiworldcore.block.entity.ModBlockEntities;
import top.csituka.youzaiworldcore.component.ModDataComponents;
import top.csituka.youzaiworldcore.event.AccountEventHandler;
import top.csituka.youzaiworldcore.event.AnvilRepairHandler;
import top.csituka.youzaiworldcore.event.FlyBeaconTickHandler;
import top.csituka.youzaiworldcore.event.VoidStaffTickHandler;
import top.csituka.youzaiworldcore.item.ModCreativeModeTabs;
import top.csituka.youzaiworldcore.item.ModItems;
import top.csituka.youzaiworldcore.item.tool.YzChainMiningTool;
import top.csituka.youzaiworldcore.network.ModNetworking;
import top.csituka.youzaiworldcore.network.OpenMenuPayload;
import top.csituka.youzaiworldcore.screen.ModMenuTypes;

import java.util.Collection;
import java.util.Set;

public class YouzaiworldCore implements ModInitializer {

    public static final String MOD_ID = "youzaiworldcore";

    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static final ResourceKey<PlacedFeature> YZ_ORE_PLACED_KEY = ResourceKey.create(
            Registries.PLACED_FEATURE,
            Identifier.fromNamespaceAndPath(MOD_ID, "ore_yz")
    );

    public static final ResourceKey<PlacedFeature> RAW_YZ_BLOCK_PLACED_KEY = ResourceKey.create(
            Registries.PLACED_FEATURE,
            Identifier.fromNamespaceAndPath(MOD_ID, "ore_raw_yz_block")
    );

    @Override
    public void onInitialize() {
        ModDataComponents.initialize();
        ModBlocks.initialize();
        ModBlockEntities.initialize();
        ModItems.initialize();
        ModCreativeModeTabs.initialize();
        ModMenuTypes.initialize();
        ModNetworking.initialize();
        YzChainMiningTool.registerChainMiningEvent();
        AnvilRepairHandler.register();
        VoidStaffTickHandler.register();
        FlyBeaconTickHandler.register();
        AccountEventHandler.register();

        BiomeModifications.addFeature(
                BiomeSelectors.foundInOverworld(),
                GenerationStep.Decoration.UNDERGROUND_ORES,
                YZ_ORE_PLACED_KEY
        );

        BiomeModifications.addFeature(
                BiomeSelectors.foundInOverworld(),
                GenerationStep.Decoration.UNDERGROUND_ORES,
                RAW_YZ_BLOCK_PLACED_KEY
        );

        // ===== 注册所有 /yzwc 命令 =====
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(Commands.literal("yzwc")
                // === teleport_world ===
                .then(Commands.literal("teleport_world")
                    .requires(Commands.hasPermission(Commands.LEVEL_ADMINS))
                    .then(Commands.argument("targets", EntityArgument.players())
                        .then(Commands.argument("dimension", DimensionArgument.dimension())
                            .executes(context -> executeTeleportWorld(
                                context.getSource(),
                                EntityArgument.getPlayers(context, "targets"),
                                DimensionArgument.getDimension(context, "dimension"),
                                0, 100, 0, 90.0f, 0.0f
                            ))
                            .then(Commands.argument("x", IntegerArgumentType.integer())
                                .executes(context -> executeTeleportWorld(
                                    context.getSource(),
                                    EntityArgument.getPlayers(context, "targets"),
                                    DimensionArgument.getDimension(context, "dimension"),
                                    IntegerArgumentType.getInteger(context, "x"),
                                    100, 0, 90.0f, 0.0f
                                ))
                                .then(Commands.argument("y", IntegerArgumentType.integer())
                                    .executes(context -> executeTeleportWorld(
                                        context.getSource(),
                                        EntityArgument.getPlayers(context, "targets"),
                                        DimensionArgument.getDimension(context, "dimension"),
                                        IntegerArgumentType.getInteger(context, "x"),
                                        IntegerArgumentType.getInteger(context, "y"),
                                        0, 90.0f, 0.0f
                                    ))
                                    .then(Commands.argument("z", IntegerArgumentType.integer())
                                        .executes(context -> executeTeleportWorld(
                                            context.getSource(),
                                            EntityArgument.getPlayers(context, "targets"),
                                            DimensionArgument.getDimension(context, "dimension"),
                                            IntegerArgumentType.getInteger(context, "x"),
                                            IntegerArgumentType.getInteger(context, "y"),
                                            IntegerArgumentType.getInteger(context, "z"),
                                            90.0f, 0.0f
                                        ))
                                        .then(Commands.argument("yRot", FloatArgumentType.floatArg(-180.0f, 180.0f))
                                            .executes(context -> executeTeleportWorld(
                                                context.getSource(),
                                                EntityArgument.getPlayers(context, "targets"),
                                                DimensionArgument.getDimension(context, "dimension"),
                                                IntegerArgumentType.getInteger(context, "x"),
                                                IntegerArgumentType.getInteger(context, "y"),
                                                IntegerArgumentType.getInteger(context, "z"),
                                                FloatArgumentType.getFloat(context, "yRot"),
                                                0.0f
                                            ))
                                            .then(Commands.argument("xRot", FloatArgumentType.floatArg(-90.0f, 90.0f))
                                                .executes(context -> executeTeleportWorld(
                                                    context.getSource(),
                                                    EntityArgument.getPlayers(context, "targets"),
                                                    DimensionArgument.getDimension(context, "dimension"),
                                                    IntegerArgumentType.getInteger(context, "x"),
                                                    IntegerArgumentType.getInteger(context, "y"),
                                                    IntegerArgumentType.getInteger(context, "z"),
                                                    FloatArgumentType.getFloat(context, "yRot"),
                                                    FloatArgumentType.getFloat(context, "xRot")
                                                ))
                                            )
                                        )
                                    )
                                )
                            )
                        )
                    )
                )
                // === open_menu ===
                .then(Commands.literal("open_menu")
                    .requires(Commands.hasPermission(Commands.LEVEL_ADMINS))
                    .then(Commands.argument("menu_name", StringArgumentType.word())
                        .executes(context -> executeOpenMenu(
                            context.getSource(),
                            StringArgumentType.getString(context, "menu_name"),
                            context.getSource().getPlayerOrException()
                        ))
                        .then(Commands.argument("target", EntityArgument.player())
                            .executes(context -> executeOpenMenu(
                                context.getSource(),
                                StringArgumentType.getString(context, "menu_name"),
                                EntityArgument.getPlayer(context, "target")
                            ))
                        )
                    )
                )
                // === account (普通玩家可用) ===
                .then(Commands.literal("account")
                    .then(Commands.literal("register")
                        .then(Commands.argument("password", StringArgumentType.word())
                            .then(Commands.argument("confirmPassword", StringArgumentType.word())
                                .executes(context -> executeAccountRegister(
                                    context.getSource(),
                                    StringArgumentType.getString(context, "password"),
                                    StringArgumentType.getString(context, "confirmPassword")
                                ))
                            )
                        )
                    )
                    .then(Commands.literal("login")
                        .then(Commands.argument("password", StringArgumentType.word())
                            .executes(context -> executeAccountLogin(
                                context.getSource(),
                                StringArgumentType.getString(context, "password")
                            ))
                        )
                    )
                    .then(Commands.literal("logout")
                        .executes(context -> executeAccountLogout(context.getSource()))
                    )
                    .then(Commands.literal("change_password")
                        .then(Commands.argument("oldPassword", StringArgumentType.word())
                            .then(Commands.argument("newPassword", StringArgumentType.word())
                                .then(Commands.argument("confirmPassword", StringArgumentType.word())
                                    .executes(context -> executeChangePassword(
                                        context.getSource(),
                                        StringArgumentType.getString(context, "oldPassword"),
                                        StringArgumentType.getString(context, "newPassword"),
                                        StringArgumentType.getString(context, "confirmPassword")
                                    ))
                                )
                            )
                        )
                    )
                    .then(Commands.literal("cancel_account")
                        .then(Commands.argument("password", StringArgumentType.word())
                            .executes(context -> executeCancelAccount(
                                context.getSource(),
                                StringArgumentType.getString(context, "password")
                            ))
                        )
                    )
                    // === account mgr (OP 权限) ===
                    .then(Commands.literal("mgr")
                        .requires(Commands.hasPermission(Commands.LEVEL_ADMINS))
                        .then(Commands.literal("reset_password")
                            .then(Commands.argument("playerName", StringArgumentType.word())
                                .then(Commands.argument("newPassword", StringArgumentType.word())
                                    .then(Commands.argument("confirmPassword", StringArgumentType.word())
                                        .executes(context -> executeMgrResetPassword(
                                            context.getSource(),
                                            StringArgumentType.getString(context, "playerName"),
                                            StringArgumentType.getString(context, "newPassword"),
                                            StringArgumentType.getString(context, "confirmPassword")
                                        ))
                                    )
                                )
                            )
                        )
                        .then(Commands.literal("delete")
                            .then(Commands.argument("playerName", StringArgumentType.word())
                                .then(Commands.argument("password", StringArgumentType.word())
                                    .executes(context -> executeMgrDeleteAccount(
                                        context.getSource(),
                                        StringArgumentType.getString(context, "playerName"),
                                        StringArgumentType.getString(context, "password")
                                    ))
                                )
                            )
                        )
                        .then(Commands.literal("create")
                            .then(Commands.argument("playerName", StringArgumentType.word())
                                .then(Commands.argument("password", StringArgumentType.word())
                                    .then(Commands.argument("confirmPassword", StringArgumentType.word())
                                        .executes(context -> executeMgrCreateAccount(
                                            context.getSource(),
                                            StringArgumentType.getString(context, "playerName"),
                                            StringArgumentType.getString(context, "password"),
                                            StringArgumentType.getString(context, "confirmPassword")
                                        ))
                                    )
                                )
                            )
                        )
                        .then(Commands.literal("get_account_status")
                            .then(Commands.argument("playerName", StringArgumentType.word())
                                .executes(context -> executeMgrGetAccountStatus(
                                    context.getSource(),
                                    StringArgumentType.getString(context, "playerName")
                                ))
                            )
                        )
                        .then(Commands.literal("unblock")
                            .then(Commands.argument("playerName", StringArgumentType.word())
                                .executes(context -> executeMgrUnblock(
                                    context.getSource(),
                                    StringArgumentType.getString(context, "playerName")
                                ))
                            )
                        )
                    )
                )
                .executes(context -> {
                    context.getSource().sendSuccess(() -> Component.literal("Hello World!"), false);
                    return 1;
                })
            );
        });
    }

    // ==================== 命令执行方法 ====================

    // ===== 普通玩家账户命令 =====

    private static int executeAccountRegister(CommandSourceStack source, String password, String confirmPassword) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();

        if (AccountManager.isLoggedIn(player)) {
            source.sendFailure(Component.literal("您已经登录了！"));
            return 0;
        }
        if (!password.equals(confirmPassword)) {
            source.sendFailure(Component.literal("两次输入的密码不一致！"));
            return 0;
        }
        if (password.isEmpty()) {
            source.sendFailure(Component.literal("密码不能为空！"));
            return 0;
        }

        String playerName = player.getGameProfile().name();
        var result = AccountManager.register(player.getUUID(), playerName, password);
        switch (result) {
            case SUCCESS -> {
                AccountManager.snapshotState(player);
                AccountManager.setLoggedIn(player, true);
                // 移除无敌/飞行
                player.setInvulnerable(false);
                player.getAbilities().flying = false;
                player.getAbilities().mayfly = false;
                player.onUpdateAbilities();
                source.sendSuccess(() -> Component.literal("注册成功！"), false);
                // 传送回主世界
                ServerLevel overworld = player.level().getServer().getLevel(Level.OVERWORLD);
                if (overworld != null) {
                    player.teleportTo(overworld, 0.5, 100, 0.5, Set.of(), 0f, 0f, true);
                }
                return 1;
            }
            case ALREADY_REGISTERED -> {
                source.sendFailure(Component.literal("该账户已经注册了！"));
                return 0;
            }
            case NAME_TAKEN -> {
                source.sendFailure(Component.literal("该玩家代号已被使用！"));
                return 0;
            }
            default -> {
                source.sendFailure(Component.literal("注册失败，未知错误！"));
                return 0;
            }
        }
    }

    private static int executeAccountLogin(CommandSourceStack source, String password) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();

        if (AccountManager.isLoggedIn(player)) {
            source.sendFailure(Component.literal("您已经登录了！"));
            return 0;
        }

        var result = AccountManager.login(player.getUUID(), password);
        switch (result) {
            case SUCCESS -> {
                AccountManager.setLoggedIn(player, true);
                // 移除无敌/飞行
                player.setInvulnerable(false);
                player.getAbilities().flying = false;
                player.getAbilities().mayfly = false;
                player.onUpdateAbilities();
                LoginState state = AccountManager.getLoginState(player);
                state.restorePosition(player);
                source.sendSuccess(() -> Component.literal("登录成功！"), false);
                return 1;
            }
            case NOT_REGISTERED -> {
                source.sendFailure(Component.literal("该账户尚未注册！"));
                return 0;
            }
            case WRONG_PASSWORD -> {
                source.sendFailure(Component.literal("密码错误"));
                return 0;
            }
            case KICK -> {
                player.connection.disconnect(Component.literal("密码错误过多，已被踢出服务器"));
                return 0;
            }
            case BLOCKED -> {
                player.connection.disconnect(Component.literal("由于您过多次数密码验证失败，请前往QQ交流群联系管理员"));
                return 0;
            }
            default -> {
                source.sendFailure(Component.literal("登录失败！"));
                return 0;
            }
        }
    }

    private static int executeAccountLogout(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();

        if (!AccountManager.isLoggedIn(player)) {
            source.sendFailure(Component.literal("您尚未登录！"));
            return 0;
        }

        // 登出：保存状态，传送到登录大厅
        AccountManager.snapshotState(player);
        AccountManager.setLoggedIn(player, false);

        player.setInvulnerable(true);
        player.getAbilities().flying = true;
        player.getAbilities().mayfly = true;
        player.onUpdateAbilities();

        ServerLevel loginHall = player.level().getServer().getLevel(
                ResourceKey.create(Registries.DIMENSION,
                        Identifier.fromNamespaceAndPath(MOD_ID, "login_hall")));
        if (loginHall != null) {
            player.teleportTo(loginHall, 0.5, 100, 0.5, Set.of(), 0f, 0f, true);
        }

        AccountEventHandler.sendOpenLoginScreen(player, "login");
        source.sendSuccess(() -> Component.literal("已登出！"), false);
        return 1;
    }

    private static int executeChangePassword(CommandSourceStack source, String oldPassword, String newPassword, String confirmPassword) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();

        if (!newPassword.equals(confirmPassword)) {
            source.sendFailure(Component.literal("两次输入的新密码不一致！"));
            return 0;
        }

        boolean success = AccountManager.changePassword(player.getUUID(), oldPassword, newPassword);
        if (success) {
            source.sendSuccess(() -> Component.literal("密码修改成功！"), false);
            return 1;
        } else {
            source.sendFailure(Component.literal("旧密码错误或账户不存在！"));
            return 0;
        }
    }

    private static int executeCancelAccount(CommandSourceStack source, String password) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();

        String playerName = player.getGameProfile().name();
        boolean success = AccountManager.deleteAccountByPlayerName(playerName, password);
        if (success) {
            // 注销后踢出服务器
            player.connection.disconnect(Component.literal("账户已注销！"));
            return 1;
        } else {
            source.sendFailure(Component.literal("密码错误或账户不存在！"));
            return 0;
        }
    }

    // ===== 管理员账户管理命令 =====

    private static int executeMgrResetPassword(CommandSourceStack source, String playerName, String newPassword, String confirmPassword) {
        if (!newPassword.equals(confirmPassword)) {
            source.sendFailure(Component.literal("两次输入的密码不一致！"));
            return 0;
        }

        boolean success = AccountManager.resetPassword(playerName, newPassword);
        if (success) {
            source.sendSuccess(() -> Component.literal("已重置玩家 " + playerName + " 的密码！"), true);
            return 1;
        } else {
            source.sendFailure(Component.literal("玩家 " + playerName + " 的账户不存在！"));
            return 0;
        }
    }

    private static int executeMgrDeleteAccount(CommandSourceStack source, String playerName, String password) {
        boolean success = AccountManager.deleteAccountByPlayerName(playerName, password);
        if (success) {
            source.sendSuccess(() -> Component.literal("已删除玩家 " + playerName + " 的账户！"), true);
            return 1;
        } else {
            source.sendFailure(Component.literal("密码错误或玩家 " + playerName + " 的账户不存在！"));
            return 0;
        }
    }

    private static int executeMgrCreateAccount(CommandSourceStack source, String playerName, String password, String confirmPassword) {
        if (!password.equals(confirmPassword)) {
            source.sendFailure(Component.literal("两次输入的密码不一致！"));
            return 0;
        }

        // 查找是否有该玩家名的在线玩家
        ServerPlayer targetPlayer = source.getServer().getPlayerList().getPlayerByName(playerName);
        if (targetPlayer != null) {
            if (AccountManager.isRegistered(targetPlayer.getUUID())) {
                source.sendFailure(Component.literal("该玩家已经拥有账户！"));
                return 0;
            }
            AccountManager.register(targetPlayer.getUUID(), playerName, password);
            source.sendSuccess(() -> Component.literal("已为在线玩家 " + playerName + " 创建账户！"), true);
            return 1;
        }

        // 玩家不在线：尝试通过已有文件判断
        if (AccountManager.isPlayerNameTaken(playerName)) {
            source.sendFailure(Component.literal("该玩家代号已被使用！"));
            return 0;
        }

        source.sendFailure(Component.literal("无法为离线玩家创建账户（需要玩家在线以获取 UUID）！"));
        return 0;
    }

    private static int executeMgrGetAccountStatus(CommandSourceStack source, String playerName) {
        String status = AccountManager.getAccountStatus(playerName);
        source.sendSuccess(() -> Component.literal(status), false);
        return 1;
    }

    private static int executeMgrUnblock(CommandSourceStack source, String playerName) {
        AccountManager.unblockPlayer(playerName);
        source.sendSuccess(() -> Component.literal("已解除玩家 " + playerName + " 的阻止登入状态！"), true);
        return 1;
    }

    // ===== 原有命令 =====

    /**
     * 执行传送玩家到指定维度的逻辑。
     */
    private static int executeTeleportWorld(
            CommandSourceStack source,
            Collection<ServerPlayer> players,
            ServerLevel dimension,
            int x, int y, int z,
            float yRot, float xRot
    ) {
        Identifier dimensionId = dimension.dimension().identifier();
        int count = 0;

        for (ServerPlayer player : players) {
            player.teleportTo(dimension, x + 0.5, y, z + 0.5, Set.of(), yRot, xRot, true);
            count++;
        }

        final int finalCount = count;
        source.sendSuccess(() ->
                Component.literal("已将 " + finalCount + " 名玩家传送到 " + dimensionId +
                        " 的 (" + x + ", " + y + ", " + z + ") 位置"),
                true
        );
        return finalCount;
    }

    /**
     * 打开指定玩家的 GUI 菜单。
     */
    private static int executeOpenMenu(CommandSourceStack source, String menuName, ServerPlayer player) {
        if (!menuName.equals("main") && !menuName.equals("switch_world")
                && !menuName.equals("settings") && !menuName.equals("about_me")) {
            source.sendFailure(Component.literal("未知的菜单名称: " + menuName + "。有效值: main, switch_world, settings, about_me"));
            return 0;
        }

        ServerPlayNetworking.send(player, new OpenMenuPayload(menuName));

        source.sendSuccess(() ->
                Component.literal("已为 " + player.getName().getString() + " 打开 " + menuName + " 菜单"),
                true
        );
        return 1;
    }
}
