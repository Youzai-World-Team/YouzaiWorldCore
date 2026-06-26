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

import top.csituka.youzaiworldcore.luckperms.LuckPermsHelper;
import top.csituka.youzaiworldcore.block.ModBlocks;
import top.csituka.youzaiworldcore.block.entity.ModBlockEntities;
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
                    context.getSource().sendSuccess(() -> Component.literal("Hello World!"), false);
                    return 1;
                })
            );
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
