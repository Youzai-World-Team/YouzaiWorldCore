package top.csituka.youzaiworldcore;

import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
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
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import top.csituka.youzaiworldcore.account.command.AccountCommands;
import top.csituka.youzaiworldcore.account.data.AccountDataStorage;
import top.csituka.youzaiworldcore.command.ReloadCommand;
import top.csituka.youzaiworldcore.config.ServerExternalSettings;
import top.csituka.youzaiworldcore.luckperms.LuckPermsHelper;
import top.csituka.youzaiworldcore.skill.AdventureLevelManager;
import top.csituka.youzaiworldcore.util.DebugLogger;
import top.csituka.youzaiworldcore.mana.ManaTickHandler;
import top.csituka.youzaiworldcore.block.ModBlocks;
import top.csituka.youzaiworldcore.block.entity.ModBlockEntities;
import top.csituka.youzaiworldcore.command.ExperimentalFeatureCommand;
import top.csituka.youzaiworldcore.command.InvisibilityCommand;
import top.csituka.youzaiworldcore.command.TeleportAnchorCommand;
import top.csituka.youzaiworldcore.component.ModDataComponents;
import top.csituka.youzaiworldcore.dimensionalinventories.DimensionPoolSettings;
import top.csituka.youzaiworldcore.dimensionalinventories.DimensionPoolManager;
import top.csituka.youzaiworldcore.dimensionalinventories.WorldPoolCommand;
import top.csituka.youzaiworldcore.entity.seat.ModSeatEntities;
import top.csituka.youzaiworldcore.event.AnvilRepairHandler;
import top.csituka.youzaiworldcore.event.FlyBeaconTickHandler;
import top.csituka.youzaiworldcore.event.SitHandler;
import top.csituka.youzaiworldcore.event.VoidStaffTickHandler;
import top.csituka.youzaiworldcore.feature.ExperimentalFeatures;
import top.csituka.youzaiworldcore.invisibility.InvisibilityManager;
import top.csituka.youzaiworldcore.invisibility.InvisibilityTickHandler;
import top.csituka.youzaiworldcore.item.ModCreativeModeTabs;
import top.csituka.youzaiworldcore.item.ModItems;
import top.csituka.youzaiworldcore.item.tool.YzChainMiningTool;
import top.csituka.youzaiworldcore.network.ModNetworking;
import top.csituka.youzaiworldcore.network.OpenMenuPayload;
import top.csituka.youzaiworldcore.screen.ModMenuTypes;

import java.util.Collection;
import java.util.Set;

@SuppressWarnings("null")
public class YouzaiworldCore implements ModInitializer {

    public static final String MOD_ID = "youzaiworldcore";

    public static final Logger LOGGER = LoggerFactory.getLogger("YouzaiWorldCore");

    /** logToFile 标志：由 {@code config/youzaiworldcore/server_external_settings.json} 控制，
     * 用于条件化服务端噪音日志（实验性功能注册、配置加载、账户数据详情等） */
    public static boolean logToFile = false;

    /** devModeEnabled 标志：由 {@code config/youzaiworldcore/server_external_settings.json} 控制，
     * 与 {@link #logToFile} 同时启用时激活完整的调试日志输出 */
    public static boolean devModeEnabled = false;

    /** 模组启动 Logo（ASCII 艺术字） */
    public static final String LOGO =
            "-----------------------------------------------------------------------------------------\n" +
            "    __   __                   _    _    _            _     _    _____                \n" +
            "    \\ \\ / /                  (_)  | |  | |          | |   | |  /  __ \\               \n" +
            "     \\ V /___  _   _ ______ _ _   | |  | | ___  _ __| | __| |  | /  \\/ ___  _ __ ___ \n" +
            "      \\ // _ \\| | | |_  / _` | |  | |/\\| |/ _ \\| '__| |/ _` |  | |    / _ \\| '__/ _ \\\n" +
            "      | | (_) | |_| |/ / (_| | |  \\  /\\  / (_) | |  | | (_| |  | \\__/\\ (_) | | |  __/\n" +
            "      \\_/\\___/ \\__,_/___\\__,_|_|   \\/  \\/ \\___/|_|  |_|\\__,_|   \\____/\\___/|_|  \\___|\n" +
            "-----------------------------------------------------------------------------------------\n" +
            "Copyright © YouzaiWorldTeam 2026\n" +
            "Open source repository: https://github.com/Youzai-World-Team/YouzaiWorldCore";

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
        // ===== 加载服务端外部设置（devModeEnabled / logToFile 等） =====
        ServerExternalSettings.load();
        logToFile = ServerExternalSettings.isLogToFile();
        devModeEnabled = ServerExternalSettings.isDevModeEnabled();
        DebugLogger.setDevModeEnabled(devModeEnabled);
        DebugLogger.setLogLevel(logToFile ? 1 : 0);

        // ===== 输出模组 Logo（启动后第一条输出） =====
        LOGGER.info("\n{}", LOGO);

        DebugLogger.entering("YouzaiworldCore", "onInitialize",
                "devMode=" + devModeEnabled + ", logToFile=" + logToFile);

        DebugLogger.info("YouzaiworldCore", "初始化数据组件...");
        ModDataComponents.initialize();
        DebugLogger.info("YouzaiworldCore", "初始化方块...");
        ModBlocks.initialize();
        DebugLogger.info("YouzaiworldCore", "初始化方块实体...");
        ModBlockEntities.initialize();
        DebugLogger.info("YouzaiworldCore", "初始化物品...");
        ModItems.initialize();
        DebugLogger.info("YouzaiworldCore", "初始化创造模式标签页...");
        ModCreativeModeTabs.initialize();
        DebugLogger.info("YouzaiworldCore", "初始化菜单类型...");
        ModMenuTypes.initialize();
        DebugLogger.info("YouzaiworldCore", "初始化网络注册...");
        ModNetworking.initialize();
        DebugLogger.info("YouzaiworldCore", "注册座椅实体...");
        ModSeatEntities.initialize();
        DebugLogger.info("YouzaiworldCore", "注册连锁采集事件...");
        YzChainMiningTool.registerChainMiningEvent();
        DebugLogger.info("YouzaiworldCore", "注册铁砧修复事件...");
        AnvilRepairHandler.register();
        DebugLogger.info("YouzaiworldCore", "注册坐姿交互事件...");
        SitHandler.register();
        DebugLogger.info("YouzaiworldCore", "注册虚空法杖 Tick 事件...");
        VoidStaffTickHandler.register();
        DebugLogger.info("YouzaiworldCore", "注册魔力恢复 Tick 事件...");
        ManaTickHandler.register();
        DebugLogger.info("YouzaiworldCore", "注册飞行信标 Tick 事件...");
        FlyBeaconTickHandler.register();

        // ===== 初始化账户系统 =====
        DebugLogger.entering("YouzaiworldCore", "AccountSystem.init");
        AccountDataStorage.initialize();
        LOGGER.info("账户系统已初始化");
        DebugLogger.exiting("YouzaiworldCore", "AccountSystem.init");

        // ===== 初始化冒险等级系统 =====
        DebugLogger.entering("YouzaiworldCore", "AdventureLevelSystem.init");
        AdventureLevelManager.initialize();
        LOGGER.info("冒险等级系统已初始化");
        DebugLogger.exiting("YouzaiworldCore", "AdventureLevelSystem.init");

        DebugLogger.info("YouzaiworldCore", "注册矿物生成...");
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

        // ===== 初始化隐身功能 =====
        DebugLogger.entering("YouzaiworldCore", "InvisibilitySystem.init");
        InvisibilityTickHandler.register();
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            if (handler.getPlayer() instanceof ServerPlayer serverPlayer) {
                DebugLogger.info("YouzaiworldCore", "玩家断开连接: {}",
                        serverPlayer.getName().getString());
                InvisibilityManager.onPlayerDisconnect(serverPlayer);
                DimensionPoolManager.onPlayerDisconnect(serverPlayer);
            }
        });
        LOGGER.info("隐身功能已初始化");
        DebugLogger.exiting("YouzaiworldCore", "InvisibilitySystem.init");

        // ===== 初始化维度池系统 =====
        DebugLogger.entering("YouzaiworldCore", "DimensionPoolSystem.init");
        DimensionPoolSettings.load();
        LOGGER.info("维度池系统已初始化");
        DebugLogger.exiting("YouzaiworldCore", "DimensionPoolSystem.init");

        // ===== 注册维度池事件 =====
        DebugLogger.entering("YouzaiworldCore", "DimensionPoolEvents.register");
        net.fabricmc.fabric.api.entity.event.v1.ServerEntityLevelChangeEvents.AFTER_PLAYER_CHANGE_LEVEL.register(
            (player, origin, destination) -> {
                DebugLogger.info("DimensionPoolManager", "玩家维度变化事件触发: {} -> {} -> {}",
                        player.getName().getString(),
                        origin.dimension().identifier().toString(),
                        destination.dimension().identifier().toString());
                DimensionPoolManager.onPlayerChangeDimension(player, origin, destination);
            }
        );
        net.fabricmc.fabric.api.entity.event.v1.ServerEntityLevelChangeEvents.AFTER_ENTITY_CHANGE_LEVEL.register(
            (originalEntity, newEntity, origin, destination) -> {
                if (!(newEntity instanceof net.minecraft.server.level.ServerPlayer)) {
                    DimensionPoolManager.onNonPlayerEntityChangeDimension(originalEntity, newEntity, origin, destination);
                }
            }
        );
        net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents.AFTER_RESPAWN.register(
            (oldPlayer, newPlayer, alive) -> {
                DebugLogger.info("DimensionPoolManager", "玩家复活事件触发: {} (alive={})",
                        newPlayer.getName().getString(), alive);
                DimensionPoolManager.onPlayerRespawn(oldPlayer, newPlayer, alive);
            }
        );
        LOGGER.info("维度池事件已注册");
        DebugLogger.exiting("YouzaiworldCore", "DimensionPoolEvents.register");

        // ===== 初始化实验性功能系统 =====
        DebugLogger.entering("YouzaiworldCore", "ExperimentalFeatures.load");
        ExperimentalFeatures.loadDefaults();

        // 加载服务端持久化配置
        ExperimentalFeatures.loadServerSettings();
        DebugLogger.exiting("YouzaiworldCore", "ExperimentalFeatures.load");

        // ===== 注册所有 /yzwc 命令 =====
        DebugLogger.entering("YouzaiworldCore", "CommandRegistration");
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            DebugLogger.info("YouzaiworldCore", "注册命令: ExperimentalFeatureCommand");
            ExperimentalFeatureCommand.register(dispatcher);
            DebugLogger.info("YouzaiworldCore", "注册命令: InvisibilityCommand");
            InvisibilityCommand.register(dispatcher);
            DebugLogger.info("YouzaiworldCore", "注册命令: WorldPoolCommand");
            WorldPoolCommand.register(dispatcher);

            dispatcher.register(Commands.literal("yzwc")
                // === teleport_world ===
                .then(Commands.literal("teleport_world")
                    .requires(source -> LuckPermsHelper.checkPermission(source, LuckPermsHelper.PERMISSION_TELEPORT_WORLD, Commands.LEVEL_ADMINS))
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
                    .requires(source -> LuckPermsHelper.checkPermission(source, LuckPermsHelper.PERMISSION_OPEN_MENU, Commands.LEVEL_ADMINS))
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
                .executes(context -> {
                    DebugLogger.info("YouzaiworldCore", "执行 /yzwc 根命令 (hello)");
                    context.getSource().sendSuccess(() -> Component.translatable("youzaiworldcore.message.command.hello_world"), false);
                    return 1;
                })
            );

            // ===== 注册账户管理命令 =====
            DebugLogger.info("YouzaiworldCore", "注册命令: AccountCommands");
            AccountCommands.register(dispatcher);

            // ===== 注册重载命令 =====
            DebugLogger.info("YouzaiworldCore", "注册命令: ReloadCommand");
            ReloadCommand.register(dispatcher);

            // ===== 注册传送锚点管理命令 =====
            DebugLogger.info("YouzaiworldCore", "注册命令: TeleportAnchorCommand");
            TeleportAnchorCommand.register(dispatcher);

            DebugLogger.info("YouzaiworldCore", "所有 /yzwc 命令注册完成");
        });

        DebugLogger.exiting("YouzaiworldCore", "onInitialize",
                "devMode=" + devModeEnabled + ", logToFile=" + logToFile);
    }

    // ==================== 命令执行方法 ====================

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
        DebugLogger.entering("YouzaiworldCore", "executeTeleportWorld",
                "source=" + source.getTextName() + ", targets=" + players.size()
                        + ", dim=" + dimensionId + ", pos=" + x + "," + y + "," + z);

        int count = 0;

        for (ServerPlayer player : players) {
            DebugLogger.info("YouzaiworldCore", "传送玩家 {} 到维度 {} 坐标 ({}, {}, {})",
                    player.getName().getString(), dimensionId, x, y, z);
            player.teleportTo(dimension, x + 0.5, y, z + 0.5, Set.of(), yRot, xRot, true);
            count++;
        }

        final int finalCount = count;
        source.sendSuccess(() ->
                Component.translatable("youzaiworldcore.message.command.teleport_success",
                        finalCount, dimensionId.toString(), x, y, z),
                true
        );

        DebugLogger.exiting("YouzaiworldCore", "executeTeleportWorld", "count=" + count);
        return finalCount;
    }

    /**
     * 打开指定玩家的 GUI 菜单。
     */
    private static int executeOpenMenu(CommandSourceStack source, String menuName, ServerPlayer player) {
        DebugLogger.entering("YouzaiworldCore", "executeOpenMenu",
                "source=" + source.getTextName() + ", menu=" + menuName + ", player=" + player.getName().getString());

        if (!menuName.equals("main") && !menuName.equals("switch_world")
                && !menuName.equals("settings") && !menuName.equals("about_me")) {
            DebugLogger.branch("YouzaiworldCore", "验证菜单名称是否有效", false,
                    "未知菜单: " + menuName);
            source.sendFailure(Component.translatable("youzaiworldcore.message.command.unknown_menu", menuName));
            return 0;
        }
        DebugLogger.branch("YouzaiworldCore", "验证菜单名称是否有效", true, menuName);

        ServerPlayNetworking.send(player, new OpenMenuPayload(menuName));
        DebugLogger.info("YouzaiworldCore", "已向玩家 {} 发送打开菜单 {} 的数据包",
                player.getName().getString(), menuName);

        source.sendSuccess(() ->
                Component.translatable("youzaiworldcore.message.command.open_menu_success",
                        player.getName().getString(), menuName),
                true
        );

        DebugLogger.exiting("YouzaiworldCore", "executeOpenMenu", "success");
        return 1;
    }
}
