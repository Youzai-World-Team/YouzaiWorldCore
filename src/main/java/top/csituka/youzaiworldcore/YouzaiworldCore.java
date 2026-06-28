package top.csituka.youzaiworldcore;

import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
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
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import top.csituka.youzaiworldcore.account.command.AccountCommands;
import top.csituka.youzaiworldcore.account.data.AccountDataStorage;
import top.csituka.youzaiworldcore.command.ReloadCommand;
import top.csituka.youzaiworldcore.luckperms.LuckPermsHelper;
import top.csituka.youzaiworldcore.block.ModBlocks;
import top.csituka.youzaiworldcore.block.entity.ModBlockEntities;
import top.csituka.youzaiworldcore.command.ExperimentalFeatureCommand;
import top.csituka.youzaiworldcore.component.ModDataComponents;
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

        // ===== 初始化账户系统 =====
        AccountDataStorage.initialize();
        LOGGER.info("账户系统已初始化");

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

        // ===== 初始化实验性功能系统 =====
        top.csituka.youzaiworldcore.feature.ExperimentalFeatures.register(
                "chicken_warden_model",
                "鸡管者模型",
                "终end",
                "https://space.bilibili.com/397147959",
                "本功能修改了坚守者的材质与模型为坤坤。",
                "苦力怕论坛",
                "https://klpbbs.com/thread-52966-1-1.html",
                false
        );
        top.csituka.youzaiworldcore.feature.ExperimentalFeatures.loadDefaults();

        // 加载服务端持久化配置
        top.csituka.youzaiworldcore.feature.ExperimentalFeatures.loadServerSettings();

        // ===== 注册所有 /yzwc 命令 =====
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            ExperimentalFeatureCommand.register(dispatcher);

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
                    context.getSource().sendSuccess(() -> Component.translatable("youzaiworldcore.message.command.hello_world"), false);
                    return 1;
                })
            );

            // ===== 注册账户管理命令 =====
            AccountCommands.register(dispatcher);

            // ===== 注册重载命令 =====
            ReloadCommand.register(dispatcher);
        });
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
        int count = 0;

        for (ServerPlayer player : players) {
            player.teleportTo(dimension, x + 0.5, y, z + 0.5, Set.of(), yRot, xRot, true);
            count++;
        }

        final int finalCount = count;
        source.sendSuccess(() ->
                Component.translatable("youzaiworldcore.message.command.teleport_success",
                        finalCount, dimensionId, x, y, z),
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
            source.sendFailure(Component.translatable("youzaiworldcore.message.command.unknown_menu", menuName));
            return 0;
        }

        ServerPlayNetworking.send(player, new OpenMenuPayload(menuName));

        source.sendSuccess(() ->
                Component.translatable("youzaiworldcore.message.command.open_menu_success",
                        player.getName().getString(), menuName),
                true
        );
        return 1;
    }
}
