package top.csituka.youzaiworldcore;

import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
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
import top.csituka.youzaiworldcore.afk.AfkManager;
import top.csituka.youzaiworldcore.afk.AfkTickHandler;
import top.csituka.youzaiworldcore.command.AfkCommand;
import top.csituka.youzaiworldcore.command.EventCommand;
import top.csituka.youzaiworldcore.command.ReloadCommand;
import top.csituka.youzaiworldcore.config.AfkConfig;
import top.csituka.youzaiworldcore.config.ChargedCreeperConfig;
import top.csituka.youzaiworldcore.config.DoubleDoorsState;
import top.csituka.youzaiworldcore.config.EndPortalConfig;
import top.csituka.youzaiworldcore.config.LaowuMemeConfig;
import top.csituka.youzaiworldcore.config.ServerExternalSettings;
import top.csituka.youzaiworldcore.luckperms.LuckPermsHelper;
import top.csituka.youzaiworldcore.trialvault.TrialVaultConfig;
import top.csituka.youzaiworldcore.skill.AdventureLevelManager;
import top.csituka.youzaiworldcore.skill.AttributeManager;
import top.csituka.youzaiworldcore.skill.PlayerAttributeStorage;
import top.csituka.youzaiworldcore.util.DebugLogger;
import top.csituka.youzaiworldcore.mana.ManaTickHandler;
import top.csituka.youzaiworldcore.block.ModBlocks;
import top.csituka.youzaiworldcore.block.entity.ModBlockEntities;
import top.csituka.youzaiworldcore.command.TeleportAnchorCommand;
import top.csituka.youzaiworldcore.command.UpdateCommand;
import top.csituka.youzaiworldcore.update.UpdateChecker;
import top.csituka.youzaiworldcore.config.UpdateCheckerConfig;
import top.csituka.youzaiworldcore.update.UpdateResult;
import top.csituka.youzaiworldcore.component.ModDataComponents;
import top.csituka.youzaiworldcore.dimensionalinventories.DimensionPoolSettings;
import top.csituka.youzaiworldcore.dimensionalinventories.DimensionPoolManager;
import top.csituka.youzaiworldcore.dimensionalinventories.WorldPoolCommand;
import top.csituka.youzaiworldcore.entity.seat.ModSeatEntities;
import top.csituka.youzaiworldcore.event.AnvilRepairHandler;
import top.csituka.youzaiworldcore.event.BoneMealSugarCaneHandler;
import top.csituka.youzaiworldcore.event.BoneMealSugarCaneDispenserBehavior;
import top.csituka.youzaiworldcore.event.ChargedCreeperHandler;
import top.csituka.youzaiworldcore.event.ChorusFruitDropHandler;
import top.csituka.youzaiworldcore.event.ConcretePowderSolidifyHandler;
import top.csituka.youzaiworldcore.event.DragonElytraDropHandler;
import top.csituka.youzaiworldcore.event.DoubleDoorsHandler;
import top.csituka.youzaiworldcore.event.EndPortalHandler;
import top.csituka.youzaiworldcore.event.FlyBeaconTickHandler;
import top.csituka.youzaiworldcore.event.LaowuMemeHandler;
import top.csituka.youzaiworldcore.event.SitHandler;
import top.csituka.youzaiworldcore.event.StonecutterDamageHandler;
import top.csituka.youzaiworldcore.event.TeleportAnchorInteractHandler;
import top.csituka.youzaiworldcore.event.VoidStaffTickHandler;
import top.csituka.youzaiworldcore.event.WardenDeathHandler;
import top.csituka.youzaiworldcore.event.DeathSoundHandler;
import top.csituka.youzaiworldcore.event.LadderExtendHandler;
import top.csituka.youzaiworldcore.event.CropXpHandler;
import top.csituka.youzaiworldcore.event.WitherSkullDropHandler;
import top.csituka.youzaiworldcore.event.TridentVoidHandler;
import top.csituka.youzaiworldcore.invisibility.InvisibilityManager;
import top.csituka.youzaiworldcore.invisibility.InvisibilityTickHandler;
import top.csituka.youzaiworldcore.item.ModCreativeModeTabs;
import top.csituka.youzaiworldcore.item.ModItems;
import top.csituka.youzaiworldcore.item.tool.YzChainMiningTool;
import top.csituka.youzaiworldcore.network.ModNetworking;
import top.csituka.youzaiworldcore.network.OpenMenuPayload;
import top.csituka.youzaiworldcore.pet.PetBackupManager;
import top.csituka.youzaiworldcore.pet.command.PetCommand;
import top.csituka.youzaiworldcore.pet.config.PetModuleConfig;
import top.csituka.youzaiworldcore.pet.event.PetEventHandlers;
import top.csituka.youzaiworldcore.screen.ModMenuTypes;
import top.csituka.youzaiworldcore.worldgen.VillageStructureInjector;

import java.util.Collection;
import java.util.Set;

@SuppressWarnings("null")
public class YouzaiworldCore implements ModInitializer {

    public static final String MOD_ID = "youzaiworldcore";

    public static final Logger LOGGER = LoggerFactory.getLogger("YouzaiWorldCore");

    /**
     * logToFile 标志：由 {@code config/youzaiworldcore/server_external_settings.json}
     * 控制，
     * 用于条件化服务端噪音日志（配置加载、账户数据详情等）
     */
    public static boolean logToFile = false;

    /**
     * devModeEnabled 标志：由
     * {@code config/youzaiworldcore/server_external_settings.json} 控制，
     * 与 {@link #logToFile} 同时启用时激活完整的调试日志输出
     */
    public static boolean devModeEnabled = false;

    /** 模组启动 Logo（ASCII 艺术字） */
    public static final String LOGO = "-----------------------------------------------------------------------------------------\n"
            +
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
            Identifier.fromNamespaceAndPath(MOD_ID, "ore_yz"));

    public static final ResourceKey<PlacedFeature> RAW_YZ_BLOCK_PLACED_KEY = ResourceKey.create(
            Registries.PLACED_FEATURE,
            Identifier.fromNamespaceAndPath(MOD_ID, "ore_raw_yz_block"));

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
        DebugLogger.info("YouzaiworldCore", "注册传送锚点交互转发事件...");
        TeleportAnchorInteractHandler.register();
        DebugLogger.info("YouzaiworldCore", "注册传送石蓄力打断事件...");
        top.csituka.youzaiworldcore.event.TeleportStoneChargeHandler.register();
        DebugLogger.info("YouzaiworldCore", "注册虚空法杖 Tick 事件...");
        VoidStaffTickHandler.register();
        DebugLogger.info("YouzaiworldCore", "注册魔力恢复 Tick 事件...");
        ManaTickHandler.register();
        DebugLogger.info("YouzaiworldCore", "注册飞行信标 Tick 事件...");
        FlyBeaconTickHandler.register();
        DebugLogger.info("YouzaiworldCore", "注册切石机伤害 Tick 事件...");
        StonecutterDamageHandler.register();
        DebugLogger.info("YouzaiworldCore", "注册末影龙鞘翅掉落事件...");
        DragonElytraDropHandler.register();
        DebugLogger.info("YouzaiworldCore", "注册监守者战利品发放事件...");
        WardenDeathHandler.register();
        DebugLogger.info("YouzaiworldCore", "注册死亡音效与消息事件...");
        DeathSoundHandler.register();
        DebugLogger.info("YouzaiworldCore", "注册梯子向下延展事件...");
        LadderExtendHandler.register();
        DebugLogger.info("YouzaiworldCore", "注册农作物收获经验掉落事件...");
        CropXpHandler.register();
        DebugLogger.info("YouzaiworldCore", "注册凋零头颅必定掉落事件...");
        WitherSkullDropHandler.register();
        DebugLogger.info("YouzaiworldCore", "注册三叉戟虚空保护事件...");
        TridentVoidHandler.register();
        DebugLogger.info("YouzaiworldCore", "注册紫颂果就近掉落事件...");
        ChorusFruitDropHandler.register();
        DebugLogger.info("YouzaiworldCore", "加载天然带电苦力怕配置...");
        ChargedCreeperConfig.load();
        DebugLogger.info("YouzaiworldCore", "注册天然带电苦力怕事件...");
        ChargedCreeperHandler.register();

        // ===== 初始化老吴贴贴事件（laowu meme 移植，全局开关由 /yzwc event laowu enable 控制） =====
        DebugLogger.info("YouzaiworldCore", "加载老吴贴贴事件配置...");
        LaowuMemeConfig.load();
        DebugLogger.info("YouzaiworldCore", "注册老吴贴贴事件状态机...");
        LaowuMemeHandler.register();

        // ===== 初始化试炼宝库无限领奖功能（参考 trial-chamber-time-removal，原生重写） =====
        DebugLogger.info("YouzaiworldCore", "加载试炼宝库无限领奖配置...");
        TrialVaultConfig.load();

        // ===== 初始化双开门功能（Double Doors，参考 Serilum 的 Double Doors 设计，原生实现，已精简为按玩家开关）
        // =====
        DebugLogger.info("YouzaiworldCore", "加载双开门玩家状态...");
        DoubleDoorsState.load();
        DebugLogger.info("YouzaiworldCore", "初始化双开门处理器...");
        DoubleDoorsHandler.register();

        // ===== 初始化末地传送门功能（EndPortalRecipe 合并） =====
        DebugLogger.entering("YouzaiworldCore", "EndPortalSystem.init");
        EndPortalConfig.load();
        EndPortalHandler.register();
        LOGGER.info("末地传送门功能已初始化（可合成 / 可搬运 / 额外龙蛋）");
        DebugLogger.exiting("YouzaiworldCore", "EndPortalSystem.init");

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

        // ===== 初始化属性加点系统 =====
        DebugLogger.entering("YouzaiworldCore", "AttributeSystem.init");
        PlayerAttributeStorage.initialize();
        AttributeManager.initialize();
        LOGGER.info("属性加点系统已初始化");
        DebugLogger.exiting("YouzaiworldCore", "AttributeSystem.init");

        // ===== 注册阳光修复附魔处理器 =====
        DebugLogger.info("YouzaiworldCore", "注册阳光修复附魔 Tick 事件...");
        top.csituka.youzaiworldcore.event.SunRepairHandler.register();
        DebugLogger.info("YouzaiWorldCore", "注册乐魂涡轮加速器 Tick 事件...");
        top.csituka.youzaiworldcore.event.HappyGhastTurboHandler.register();
        // ===== Raiyon 附魔移植处理器 =====
        DebugLogger.info("YouzaiworldCore", "注册吸血附魔事件...");
        top.csituka.youzaiworldcore.event.LeechingHandler.register();
        DebugLogger.info("YouzaiworldCore", "注册熔炼附魔事件...");
        top.csituka.youzaiworldcore.event.SmeltingHandler.register();
        DebugLogger.info("YouzaiworldCore", "注册风之充能附魔 Tick 事件...");
        top.csituka.youzaiworldcore.event.WindChargeHandler.register();
        DebugLogger.info("YouzaiworldCore", "注册发光光环附魔 Tick 事件...");
        top.csituka.youzaiworldcore.event.GlowingAuraHandler.register();

        // ===== 注册甘蔗骨粉催熟事件 =====
        DebugLogger.info("YouzaiworldCore", "注册甘蔗骨粉催熟事件...");
        BoneMealSugarCaneHandler.register();
        DebugLogger.info("YouzaiworldCore", "注册发射器骨粉催熟甘蔗行为...");
        BoneMealSugarCaneDispenserBehavior.register();

        // ===== 注册混凝土粉末掉落物遇水固化事件 =====
        DebugLogger.info("YouzaiworldCore", "注册混凝土粉末掉落物遇水固化事件...");
        ConcretePowderSolidifyHandler.initialize();
        ConcretePowderSolidifyHandler.register();

        DebugLogger.info("YouzaiworldCore", "注册矿物生成...");
        BiomeModifications.addFeature(
                BiomeSelectors.foundInOverworld(),
                GenerationStep.Decoration.UNDERGROUND_ORES,
                YZ_ORE_PLACED_KEY);

        BiomeModifications.addFeature(
                BiomeSelectors.foundInOverworld(),
                GenerationStep.Decoration.UNDERGROUND_ORES,
                RAW_YZ_BLOCK_PLACED_KEY);

        // ===== 注册村庄传送锚点结构注入 =====
        DebugLogger.entering("YouzaiworldCore", "VillageStructureInjector.register");
        ServerLifecycleEvents.SERVER_STARTING
                .register(server -> VillageStructureInjector.inject(server.registryAccess()));
        LOGGER.info("村庄传送锚点结构注入已注册");
        DebugLogger.exiting("YouzaiworldCore", "VillageStructureInjector.register");

        // ===== 初始化更新检查器（UpdateChecker）=====
        DebugLogger.entering("YouzaiworldCore", "UpdateChecker.init");
        UpdateCheckerConfig.load();
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            if (UpdateCheckerConfig.isEnabled() && UpdateCheckerConfig.isCheckOnStartupServer()) {
                DebugLogger.info("YouzaiworldCore", "服务器启动后异步检查更新...");
                UpdateChecker.AddressPair addrs = UpdateChecker.resolveServerAddresses(server);
                UpdateChecker.checkAsync(addrs.checkBase, addrs.jumpBase)
                        .thenAccept(YouzaiworldCore::youzaiworldcore$logUpdate);
            }
        });
        DebugLogger.exiting("YouzaiworldCore", "UpdateChecker.init");

        // ===== 初始化隐身功能 =====
        DebugLogger.entering("YouzaiworldCore", "InvisibilitySystem.init");
        InvisibilityTickHandler.register();
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            if (handler.getPlayer() instanceof ServerPlayer serverPlayer) {
                DebugLogger.info("YouzaiworldCore", "玩家断开连接: %s",
                        serverPlayer.getName().getString());
                InvisibilityManager.onPlayerDisconnect(serverPlayer);
                DimensionPoolManager.onPlayerDisconnect(serverPlayer);
                AfkManager.onDisconnect(serverPlayer);
            }
        });
        LOGGER.info("隐身功能已初始化");
        DebugLogger.exiting("YouzaiworldCore", "InvisibilitySystem.init");

        // ===== 初始化 AFK（挂机）功能 =====
        DebugLogger.entering("YouzaiworldCore", "AfkSystem.init");
        AfkConfig.load();
        AfkTickHandler.register();
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            if (handler.getPlayer() instanceof ServerPlayer sp) {
                AfkManager.onJoin(sp, server.getTickCount());
            }
        });
        LOGGER.info("AFK 功能已初始化");
        DebugLogger.exiting("YouzaiworldCore", "AfkSystem.init");

        // ===== 初始化维度池系统 =====
        DebugLogger.entering("YouzaiworldCore", "DimensionPoolSystem.init");
        DimensionPoolSettings.load();
        LOGGER.info("维度池系统已初始化");
        DebugLogger.exiting("YouzaiworldCore", "DimensionPoolSystem.init");

        // ===== 注册维度池事件 =====
        DebugLogger.entering("YouzaiworldCore", "DimensionPoolEvents.register");
        net.fabricmc.fabric.api.entity.event.v1.ServerEntityLevelChangeEvents.AFTER_PLAYER_CHANGE_LEVEL.register(
                (player, origin, destination) -> {
                    DebugLogger.info("DimensionPoolManager", "玩家维度变化事件触发: %s -> %s -> %s",
                            player.getName().getString(),
                            origin.dimension().identifier().toString(),
                            destination.dimension().identifier().toString());
                    DimensionPoolManager.onPlayerChangeDimension(player, origin, destination);
                });
        net.fabricmc.fabric.api.entity.event.v1.ServerEntityLevelChangeEvents.AFTER_ENTITY_CHANGE_LEVEL.register(
                (originalEntity, newEntity, origin, destination) -> {
                    if (!(newEntity instanceof net.minecraft.server.level.ServerPlayer)) {
                        DimensionPoolManager.onNonPlayerEntityChangeDimension(originalEntity, newEntity, origin,
                                destination);
                    }
                });
        net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents.AFTER_RESPAWN.register(
                (oldPlayer, newPlayer, alive) -> {
                    DebugLogger.info("DimensionPoolManager", "玩家复活事件触发: %s (alive=%s)",
                            newPlayer.getName().getString(), alive);
                    DimensionPoolManager.onPlayerRespawn(oldPlayer, newPlayer, alive);
                });
        LOGGER.info("维度池事件已注册");
        DebugLogger.exiting("YouzaiworldCore", "DimensionPoolEvents.register");

        // ===== 初始化邮件系统 =====
        DebugLogger.entering("YouzaiworldCore", "MailSystem.init");
        var mailConfigDir = net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir()
                .resolve("youzaiworldcore");
        var mailDataDir = mailConfigDir.resolve("mail");
        try {
            java.nio.file.Files.createDirectories(mailDataDir);
        } catch (java.io.IOException e) {
            LOGGER.error("创建邮件数据目录失败", e);
        }
        top.csituka.youzaiworldcore.mail.MailSettings.initialize(mailConfigDir);
        top.csituka.youzaiworldcore.mail.SentMailRepository.initialize(mailDataDir);
        top.csituka.youzaiworldcore.mail.MailDataStorage.initialize(mailDataDir);
        LOGGER.info("邮件系统已初始化");
        DebugLogger.exiting("YouzaiworldCore", "MailSystem.init");

        // ===== 注册邮件系统事件 =====
        // 服务端启动完成后清理「已过期且没有任何玩家星标过」的邮件
        ServerLifecycleEvents.SERVER_STARTED.register(
                server -> top.csituka.youzaiworldcore.mail.MailManager.purgeOnServerStart());
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            var player = handler.getPlayer();
            if (player instanceof net.minecraft.server.level.ServerPlayer sp) {
                // 登录时推动未读数 + 权限
                int unread = top.csituka.youzaiworldcore.mail.MailDataStorage.getUnreadCount(sp.getUUID());
                boolean canSend = top.csituka.youzaiworldcore.mail.MailPermissionHelper.hasMailPermission(sp);
                net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(sp,
                        new top.csituka.youzaiworldcore.network.MailUnreadCountPayload(unread, canSend));
                // 触发收件箱清理（从调用 load 时会剔除已撤回/过期条目）
                top.csituka.youzaiworldcore.mail.MailDataStorage.load(sp.getUUID());
            }
        });
        // 周期性过期清理（每 3000 tick = 约 2.5 分钟）
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.START_SERVER_TICK.register(server -> {
            // 使用服务器 Tick 计数器实现定时触发
            if (server.getTickCount()
                    % top.csituka.youzaiworldcore.mail.MailSettings.get().getAutoPurgeIntervalTicks() == 0) {
                top.csituka.youzaiworldcore.mail.MailManager.purge();
            }
        });
        LOGGER.info("邮件系统事件（启动清理 / 登录推送 / 过期清理）已注册");
        DebugLogger.exiting("YouzaiworldCore", "MailSystem.events");

        // ===== 初始化宠物模块 =====
        DebugLogger.entering("YouzaiworldCore", "PetModule.init");
        PetModuleConfig.load();
        PetEventHandlers.register();
        // 宠物备份管理器在服务器启动时由 ServerLifecycleEvents 初始化
        ServerLifecycleEvents.SERVER_STARTING.register(server -> {
            PetBackupManager.initialize(server);
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            PetBackupManager.shutdown();
        });
        LOGGER.info("宠物模块已初始化");
        DebugLogger.exiting("YouzaiworldCore", "PetModule.init");

        // ===== 初始化统计模块 =====
        DebugLogger.entering("YouzaiworldCore", "StatsModule.init");
        top.csituka.youzaiworldcore.status.StatsManager.initialize();
        LOGGER.info("统计模块已初始化");
        DebugLogger.exiting("YouzaiworldCore", "StatsModule.init");

        // ===== 注册所有 /yzwc 命令 =====
        DebugLogger.entering("YouzaiworldCore", "CommandRegistration");
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            DebugLogger.info("YouzaiworldCore", "注册命令: WorldPoolCommand");
            WorldPoolCommand.register(dispatcher);

            dispatcher.register(Commands.literal("yzwc")
                    // === teleport_world ===
                    .then(Commands.literal("teleport_world")
                            .requires(source -> LuckPermsHelper.checkPermission(source,
                                    LuckPermsHelper.PERMISSION_TELEPORT_WORLD, Commands.LEVEL_ADMINS))
                            .then(Commands.argument("targets", EntityArgument.players())
                                    .then(Commands.argument("dimension", DimensionArgument.dimension())
                                            .executes(context -> executeTeleportWorld(
                                                    context.getSource(),
                                                    EntityArgument.getPlayers(context, "targets"),
                                                    DimensionArgument.getDimension(context, "dimension"),
                                                    0, 100, 0, 90.0f, 0.0f))
                                            .then(Commands.argument("x", IntegerArgumentType.integer())
                                                    .executes(context -> executeTeleportWorld(
                                                            context.getSource(),
                                                            EntityArgument.getPlayers(context, "targets"),
                                                            DimensionArgument.getDimension(context, "dimension"),
                                                            IntegerArgumentType.getInteger(context, "x"),
                                                            100, 0, 90.0f, 0.0f))
                                                    .then(Commands.argument("y", IntegerArgumentType.integer())
                                                            .executes(context -> executeTeleportWorld(
                                                                    context.getSource(),
                                                                    EntityArgument.getPlayers(context, "targets"),
                                                                    DimensionArgument.getDimension(context,
                                                                            "dimension"),
                                                                    IntegerArgumentType.getInteger(context, "x"),
                                                                    IntegerArgumentType.getInteger(context, "y"),
                                                                    0, 90.0f, 0.0f))
                                                            .then(Commands.argument("z", IntegerArgumentType.integer())
                                                                    .executes(context -> executeTeleportWorld(
                                                                            context.getSource(),
                                                                            EntityArgument.getPlayers(context,
                                                                                    "targets"),
                                                                            DimensionArgument.getDimension(context,
                                                                                    "dimension"),
                                                                            IntegerArgumentType.getInteger(context,
                                                                                    "x"),
                                                                            IntegerArgumentType.getInteger(context,
                                                                                    "y"),
                                                                            IntegerArgumentType.getInteger(context,
                                                                                    "z"),
                                                                            90.0f, 0.0f))
                                                                    .then(Commands
                                                                            .argument("yRot",
                                                                                    FloatArgumentType.floatArg(-180.0f,
                                                                                            180.0f))
                                                                            .executes(context -> executeTeleportWorld(
                                                                                    context.getSource(),
                                                                                    EntityArgument.getPlayers(context,
                                                                                            "targets"),
                                                                                    DimensionArgument.getDimension(
                                                                                            context, "dimension"),
                                                                                    IntegerArgumentType
                                                                                            .getInteger(context, "x"),
                                                                                    IntegerArgumentType
                                                                                            .getInteger(context, "y"),
                                                                                    IntegerArgumentType
                                                                                            .getInteger(context, "z"),
                                                                                    FloatArgumentType.getFloat(context,
                                                                                            "yRot"),
                                                                                    0.0f))
                                                                            .then(Commands
                                                                                    .argument("xRot",
                                                                                            FloatArgumentType.floatArg(
                                                                                                    -90.0f, 90.0f))
                                                                                    .executes(
                                                                                            context -> executeTeleportWorld(
                                                                                                    context.getSource(),
                                                                                                    EntityArgument
                                                                                                            .getPlayers(
                                                                                                                    context,
                                                                                                                    "targets"),
                                                                                                    DimensionArgument
                                                                                                            .getDimension(
                                                                                                                    context,
                                                                                                                    "dimension"),
                                                                                                    IntegerArgumentType
                                                                                                            .getInteger(
                                                                                                                    context,
                                                                                                                    "x"),
                                                                                                    IntegerArgumentType
                                                                                                            .getInteger(
                                                                                                                    context,
                                                                                                                    "y"),
                                                                                                    IntegerArgumentType
                                                                                                            .getInteger(
                                                                                                                    context,
                                                                                                                    "z"),
                                                                                                    FloatArgumentType
                                                                                                            .getFloat(
                                                                                                                    context,
                                                                                                                    "yRot"),
                                                                                                    FloatArgumentType
                                                                                                            .getFloat(
                                                                                                                    context,
                                                                                                                    "xRot")))))))))))
                    // === open_menu ===
                    .then(Commands.literal("open_menu")
                            .requires(source -> LuckPermsHelper.checkPermission(source,
                                    LuckPermsHelper.PERMISSION_OPEN_MENU, Commands.LEVEL_ADMINS))
                            .then(Commands.argument("menu_name", StringArgumentType.word())
                                    .executes(context -> executeOpenMenu(
                                            context.getSource(),
                                            StringArgumentType.getString(context, "menu_name"),
                                            context.getSource().getPlayerOrException()))
                                    .then(Commands.argument("target", EntityArgument.player())
                                            .executes(context -> executeOpenMenu(
                                                    context.getSource(),
                                                    StringArgumentType.getString(context, "menu_name"),
                                                    EntityArgument.getPlayer(context, "target"))))))
                    .executes(context -> {
                        DebugLogger.info("YouzaiworldCore", "执行 /yzwc 根命令 (hello)");
                        context.getSource().sendSuccess(
                                () -> Component.translatable("youzaiworldcore.message.command.hello_world"), false);
                        return 1;
                    }));

            // ===== 注册账户管理命令 =====
            DebugLogger.info("YouzaiworldCore", "注册命令: AccountCommands");
            AccountCommands.register(dispatcher);

            // ===== 注册重载命令 =====
            DebugLogger.info("YouzaiworldCore", "注册命令: ReloadCommand");
            ReloadCommand.register(dispatcher);

            // ===== 注册传送锚点管理命令 =====
            DebugLogger.info("YouzaiworldCore", "注册命令: TeleportAnchorCommand");
            TeleportAnchorCommand.register(dispatcher);

            // ===== 注册事件管理命令 =====
            DebugLogger.info("YouzaiworldCore", "注册命令: EventCommand");
            EventCommand.register(dispatcher);

            // ===== 注册更新检查命令 =====
            DebugLogger.info("YouzaiworldCore", "注册命令: UpdateCommand");
            UpdateCommand.register(dispatcher);

            // ===== 注册宠物管理命令 =====
            DebugLogger.info("YouzaiworldCore", "注册命令: PetCommand");
            PetCommand.register(dispatcher);

            // ===== 注册 AFK 管理命令 =====
            DebugLogger.info("YouzaiworldCore", "注册命令: AfkCommand");
            AfkCommand.register(dispatcher);

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
            float yRot, float xRot) {
        Identifier dimensionId = dimension.dimension().identifier();
        DebugLogger.entering("YouzaiworldCore", "executeTeleportWorld",
                "source=" + source.getTextName() + ", targets=" + players.size()
                        + ", dim=" + dimensionId + ", pos=" + x + "," + y + "," + z);

        int count = 0;

        for (ServerPlayer player : players) {
            DebugLogger.info("YouzaiworldCore", "传送玩家 %s 到维度 %s 坐标 (%s, %s, %s)",
                    player.getName().getString(), dimensionId, x, y, z);
            player.teleportTo(dimension, x + 0.5, y, z + 0.5, Set.of(), yRot, xRot, true);
            count++;
        }

        final int finalCount = count;
        source.sendSuccess(() -> Component.translatable("youzaiworldcore.message.command.teleport_success",
                finalCount, dimensionId.toString(), x, y, z),
                true);

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
        DebugLogger.info("YouzaiworldCore", "已向玩家 %s 发送打开菜单 %s 的数据包",
                player.getName().getString(), menuName);

        source.sendSuccess(() -> Component.translatable("youzaiworldcore.message.command.open_menu_success",
                player.getName().getString(), menuName),
                true);

        DebugLogger.exiting("YouzaiworldCore", "executeOpenMenu", "success");
        return 1;
    }

    // ==================== 更新检查：控制台日志 ====================

    /**
     * 将更新检查结果输出到服务端控制台。
     * <ul>
     * <li>失败：仅记录调试日志（静默降级，不影响服务器）</li>
     * <li>无更新：调试日志记录已是最新</li>
     * <li>有更新：输出带边框横幅；强制更新使用 WARN 级别，普通更新使用 INFO 级别</li>
     * </ul>
     */
    private static void youzaiworldcore$logUpdate(UpdateResult result) {
        if (result == null) {
            return;
        }
        if (result.errorMessage() != null) {
            DebugLogger.warn("YouzaiworldCore", "更新检查失败: %s", result.errorMessage());
            return;
        }
        if (!result.updateAvailable()) {
            DebugLogger.info("YouzaiworldCore", "更新检查完成：已是最新版本 (%s)", result.currentVersion());
            return;
        }

        String border = "========================================";
        if (result.forcedUpdate()) {
            LOGGER.warn(border);
            LOGGER.warn("[强制更新] 发现新版本: {} -> {} ({})",
                    result.currentVersion(), result.latestVersion(), result.latestType());
        } else {
            LOGGER.info(border);
            LOGGER.info("发现新版本: {} -> {} ({})",
                    result.currentVersion(), result.latestVersion(), result.latestType());
        }

        if (result.releaseDate() != null && !result.releaseDate().isEmpty()) {
            LOGGER.info("发布时间: {} {}", result.releaseDate(),
                    result.releaseTime() == null ? "" : result.releaseTime());
        }
        if (result.changelog() != null) {
            for (String line : result.changelog()) {
                LOGGER.info("  - {}", line);
            }
        }
        LOGGER.info("下载地址: {}", result.downloadUrl());

        if (result.forcedUpdate()) {
            LOGGER.warn(border);
        } else {
            LOGGER.info(border);
        }
    }
}
