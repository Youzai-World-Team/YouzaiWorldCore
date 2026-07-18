package top.csituka.youzaiworldcore.network;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import top.csituka.youzaiworldcore.block.TeleportAnchorBlock;
import top.csituka.youzaiworldcore.block.entity.FlyBeaconBlockEntity;
import top.csituka.youzaiworldcore.block.entity.TeleportAnchorBlockEntity;
import top.csituka.youzaiworldcore.data.TeleportAnchorManager;
import top.csituka.youzaiworldcore.data.TeleportAnchorData;
import top.csituka.youzaiworldcore.config.DoubleDoorsState;
import top.csituka.youzaiworldcore.dimensionalinventories.DimensionPoolManager;
import top.csituka.youzaiworldcore.dimensionalinventories.WorldPoolTeleportPayload;
import top.csituka.youzaiworldcore.feature.ExperimentalFeatures;
import top.csituka.youzaiworldcore.invisibility.InvisibilityManager;
import top.csituka.youzaiworldcore.luckperms.LuckPermsHelper;
import top.csituka.youzaiworldcore.screen.DecompositionTableMenu;
import top.csituka.youzaiworldcore.screen.FlyBeaconMenu;
import top.csituka.youzaiworldcore.skill.AttributeManager;
import top.csituka.youzaiworldcore.util.DebugLogger;

import java.util.UUID;

@SuppressWarnings({"null", "unused"})
public class ModNetworking {
    
    public static void initialize() {
        DebugLogger.entering("ModNetworking", "initialize");

        // ===== 注册数据包类型 =====
        PayloadTypeRegistry.serverboundPlay().register(DecomposeItemPayload.ID, DecomposeItemPayload.STREAM_CODEC);
        DebugLogger.info("ModNetworking", "Registered serverbound packet: DecomposeItemPayload");
        PayloadTypeRegistry.serverboundPlay().register(FlyBeaconActivePayload.ID, FlyBeaconActivePayload.STREAM_CODEC);
        DebugLogger.info("ModNetworking", "Registered serverbound packet: FlyBeaconActivePayload");
        PayloadTypeRegistry.serverboundPlay().register(WorldPoolTeleportPayload.ID, WorldPoolTeleportPayload.STREAM_CODEC);
        DebugLogger.info("ModNetworking", "Registered serverbound packet: WorldPoolTeleportPayload");

        PayloadTypeRegistry.clientboundPlay().register(OpenMenuPayload.ID, OpenMenuPayload.STREAM_CODEC);
        DebugLogger.info("ModNetworking", "Registered clientbound packet: OpenMenuPayload");
        PayloadTypeRegistry.clientboundPlay().register(ManaSyncPayload.ID, ManaSyncPayload.STREAM_CODEC);
        DebugLogger.info("ModNetworking", "Registered clientbound packet: ManaSyncPayload");
        PayloadTypeRegistry.clientboundPlay().register(FeatureSyncPayload.ID, FeatureSyncPayload.STREAM_CODEC);
        DebugLogger.info("ModNetworking", "Registered clientbound packet: FeatureSyncPayload");
        PayloadTypeRegistry.clientboundPlay().register(OpenAuthScreenPayload.ID, OpenAuthScreenPayload.STREAM_CODEC);
        DebugLogger.info("ModNetworking", "Registered clientbound packet: OpenAuthScreenPayload");

        PayloadTypeRegistry.clientboundPlay().register(TeleportAnchorListPayload.TYPE, TeleportAnchorListPayload.STREAM_CODEC);
        DebugLogger.info("ModNetworking", "Registered clientbound packet: TeleportAnchorListPayload");
        PayloadTypeRegistry.clientboundPlay().register(TeleportAnchorOpenNamePayload.TYPE, TeleportAnchorOpenNamePayload.STREAM_CODEC);
        DebugLogger.info("ModNetworking", "Registered clientbound packet: TeleportAnchorOpenNamePayload");
        PayloadTypeRegistry.clientboundPlay().register(LevelExpSyncPayload.ID, LevelExpSyncPayload.STREAM_CODEC);
        DebugLogger.info("ModNetworking", "Registered clientbound packet: LevelExpSyncPayload");
        PayloadTypeRegistry.serverboundPlay().register(TeleportAnchorTeleportPayload.TYPE, TeleportAnchorTeleportPayload.STREAM_CODEC);
        DebugLogger.info("ModNetworking", "Registered serverbound packet: TeleportAnchorTeleportPayload");
        PayloadTypeRegistry.serverboundPlay().register(TeleportAnchorDeletePayload.TYPE, TeleportAnchorDeletePayload.STREAM_CODEC);
        DebugLogger.info("ModNetworking", "Registered serverbound packet: TeleportAnchorDeletePayload");
        PayloadTypeRegistry.serverboundPlay().register(TeleportAnchorRenamePayload.TYPE, TeleportAnchorRenamePayload.STREAM_CODEC);
        DebugLogger.info("ModNetworking", "Registered serverbound packet: TeleportAnchorRenamePayload");
        PayloadTypeRegistry.serverboundPlay().register(TeleportAnchorActivatePayload.TYPE, TeleportAnchorActivatePayload.STREAM_CODEC);
        DebugLogger.info("ModNetworking", "Registered serverbound packet: TeleportAnchorActivatePayload");
        PayloadTypeRegistry.serverboundPlay().register(TeleportAnchorReorderPayload.TYPE, TeleportAnchorReorderPayload.STREAM_CODEC);
        DebugLogger.info("ModNetworking", "Registered serverbound packet: TeleportAnchorReorderPayload");

        // ===== 双开门功能切换 / 查询数据包 =====
        PayloadTypeRegistry.serverboundPlay().register(DoubleDoorsTogglePayload.ID, DoubleDoorsTogglePayload.STREAM_CODEC);
        DebugLogger.info("ModNetworking", "Registered serverbound packet: DoubleDoorsTogglePayload");

        // ===== 隐身功能切换数据包 =====
        PayloadTypeRegistry.serverboundPlay().register(InvisibilityPayload.ID, InvisibilityPayload.STREAM_CODEC);
        DebugLogger.info("ModNetworking", "Registered serverbound packet: InvisibilityPayload");

        // ===== 实验性功能命令转发数据包 =====
        PayloadTypeRegistry.serverboundPlay().register(ExperimentalFeaturePayload.ID, ExperimentalFeaturePayload.STREAM_CODEC);
        DebugLogger.info("ModNetworking", "Registered serverbound packet: ExperimentalFeaturePayload");

        // ===== 服务端接收处理器 =====
        ServerPlayNetworking.registerGlobalReceiver(DecomposeItemPayload.ID, (payload, context) -> {
            DebugLogger.entering("ModNetworking", "DecomposeItemPayload handler");
            boolean isDecompositionTable = context.player().containerMenu instanceof DecompositionTableMenu;
            DebugLogger.branch("ModNetworking", "containerMenu instanceof DecompositionTableMenu", isDecompositionTable);
            if (isDecompositionTable) {
                DecompositionTableMenu menu = (DecompositionTableMenu) context.player().containerMenu;
                menu.performDecomposition();
            }
            DebugLogger.exiting("ModNetworking", "DecomposeItemPayload handler");
        });
        
        ServerPlayNetworking.registerGlobalReceiver(FlyBeaconActivePayload.ID, (payload, context) -> {
            DebugLogger.entering("ModNetworking", "FlyBeaconActivePayload handler");
            boolean isFlyBeaconMenu = context.player().containerMenu instanceof FlyBeaconMenu;
            DebugLogger.branch("ModNetworking", "containerMenu instanceof FlyBeaconMenu", isFlyBeaconMenu);
            if (isFlyBeaconMenu) {
                FlyBeaconMenu menu = (FlyBeaconMenu) context.player().containerMenu;
                boolean isFlyBeaconBlockEntity = menu.getContainer() instanceof FlyBeaconBlockEntity;
                DebugLogger.branch("ModNetworking", "getContainer() instanceof FlyBeaconBlockEntity", isFlyBeaconBlockEntity);
                if (isFlyBeaconBlockEntity) {
                    FlyBeaconBlockEntity blockEntity = (FlyBeaconBlockEntity) menu.getContainer();
                    blockEntity.setActive(payload.active());
                }
            }
            DebugLogger.exiting("ModNetworking", "FlyBeaconActivePayload handler");
        });

        // ===== 维度池传送处理器 =====
        ServerPlayNetworking.registerGlobalReceiver(WorldPoolTeleportPayload.ID, (payload, context) -> {
            DebugLogger.entering("ModNetworking", "WorldPoolTeleportPayload handler");
            DebugLogger.info("ModNetworking", "Teleporting player to pool: " + payload.poolId());
            context.player().level().getServer().execute(() -> {
                DimensionPoolManager.teleportToPool(context.player(), payload.poolId());
            });
            DebugLogger.exiting("ModNetworking", "WorldPoolTeleportPayload handler");
        });

        // ===== 传送锚点传送处理器 =====
        ServerPlayNetworking.registerGlobalReceiver(TeleportAnchorTeleportPayload.TYPE, (payload, context) -> {
            DebugLogger.entering("ModNetworking", "TeleportAnchorTeleportPayload handler");
            var player = context.player();
            var server = player.level().getServer();
            if (server == null) {
                DebugLogger.exiting("ModNetworking", "TeleportAnchorTeleportPayload handler", "server is null");
                return;
            }
            server.execute(() -> {
                TeleportAnchorManager manager = TeleportAnchorManager.get(server);
                net.minecraft.server.level.ServerPlayer serverPlayer = (net.minecraft.server.level.ServerPlayer) player;

                // 按坐标查找传送点（不再依赖索引，避免过滤列表与原始列表的索引错位）
                TeleportAnchorData target =
                        manager.findPoint(serverPlayer, payload.pos(), payload.dimension());
                if (target == null) {
                    DebugLogger.info("ModNetworking", "Teleport point not found at " + payload.pos());
                    return;
                }

                var targetLevel = server.getLevel(target.dimension());
                if (targetLevel == null) {
                    DebugLogger.info("ModNetworking", "Target dimension not loaded: " + target.dimension().identifier());
                    return;
                }

                // 重新校验目标方块仍存在且处于激活状态
                BlockState anchorState = targetLevel.getBlockState(target.pos());
                if (!(anchorState.getBlock() instanceof TeleportAnchorBlock)
                        || !anchorState.getValue(TeleportAnchorBlock.ACTIVE)) {
                    // 目标锚点已失效，从玩家列表中清理
                    manager.removePointByPos(serverPlayer, target.pos(), target.dimension());
                    serverPlayer.sendSystemMessage(
                            Component.translatable("message.youzaiworldcore.teleport_anchor.invalid"));
                    DebugLogger.info("ModNetworking", "Target anchor invalid at " + target.pos());
                    return;
                }

                // 维度池隔离校验：源维度与目标维度必须同池，或至少一方未加入任何池
                String sourceDim = serverPlayer.level().dimension().identifier().toString();
                String targetDim = target.dimension().identifier().toString();
                if (!sourceDim.equals(targetDim)) {
                    boolean samePool = top.csituka.youzaiworldcore.dimensionalinventories.DimensionPoolSettings
                            .dimensionsInSamePool(sourceDim, targetDim);
                    var sourcePool = top.csituka.youzaiworldcore.dimensionalinventories.DimensionPoolSettings
                            .getPoolByDimension(sourceDim);
                    var targetPool = top.csituka.youzaiworldcore.dimensionalinventories.DimensionPoolSettings
                            .getPoolByDimension(targetDim);
                    // 仅当双方都有池但不同池时拒绝；任一方无池则放行
                    if (sourcePool.isPresent() && targetPool.isPresent() && !samePool) {
                        serverPlayer.sendSystemMessage(
                                Component.translatable("message.youzaiworldcore.teleport_anchor.pool_mismatch"));
                        DebugLogger.info("ModNetworking", "Cross-pool teleport blocked: " + sourceDim + " -> " + targetDim);
                        return;
                    }
                }

                // 冷却检查
                long gameTime = serverPlayer.level().getGameTime();
                if (!manager.canTeleport(serverPlayer, gameTime)) {
                    int remaining = manager.getRemainingCooldownSeconds(serverPlayer, gameTime);
                    serverPlayer.sendSystemMessage(
                            Component.translatable("message.youzaiworldcore.teleport_anchor.cooldown", remaining));
                    DebugLogger.info("ModNetworking", "Player on cooldown: " + remaining + "s remaining");
                    return;
                }

                // 经验等级消耗：同维度 1 级，跨维度 2 级；创造模式免费
                boolean isCreative = serverPlayer.getAbilities().instabuild;
                int cost = target.dimension().equals(serverPlayer.level().dimension()) ? 1 : 2;
                if (!isCreative && serverPlayer.experienceLevel < cost) {
                    serverPlayer.sendSystemMessage(
                            Component.translatable("message.youzaiworldcore.teleport_anchor.no_xp", cost));
                    DebugLogger.info("ModNetworking", "Player lacks XP: needs " + cost + " but has " + serverPlayer.experienceLevel);
                    return;
                }
                if (!isCreative) {
                    serverPlayer.giveExperienceLevels(-cost);
                }

                // 记录冷却
                manager.recordTeleport(serverPlayer, gameTime);

                serverPlayer.teleportTo(targetLevel,
                        target.pos().getX() + 0.5,
                        target.pos().getY() + 1.0,
                        target.pos().getZ() + 0.5,
                        java.util.Set.of(),
                        serverPlayer.getYRot(),
                        serverPlayer.getXRot(),
                        true);
                DebugLogger.info("ModNetworking", "Teleported player to " + target.pos() + " in " + target.dimension().identifier());
            });
            DebugLogger.exiting("ModNetworking", "TeleportAnchorTeleportPayload handler");
        });

        // ===== 传送锚点删除处理器 =====
        ServerPlayNetworking.registerGlobalReceiver(TeleportAnchorDeletePayload.TYPE, (payload, context) -> {
            var player = context.player();
            var server = player.level().getServer();
            if (server != null) {
                server.execute(() -> {
                    TeleportAnchorManager manager = TeleportAnchorManager.get(server);
                    manager.removePointByPos((net.minecraft.server.level.ServerPlayer) player, payload.pos(), payload.dimension());
                });
            }
        });

        // ===== 传送锚点重命名处理器 =====
        ServerPlayNetworking.registerGlobalReceiver(TeleportAnchorRenamePayload.TYPE, (payload, context) -> {
            var player = context.player();
            var server = player.level().getServer();
            if (server != null) {
                server.execute(() -> {
                    TeleportAnchorManager manager = TeleportAnchorManager.get(server);
                    manager.renamePointByPos((net.minecraft.server.level.ServerPlayer) player,
                            payload.pos(), payload.dimension(), payload.newName());
                });
            }
        });

        // ===== 传送锚点激活处理器（玩家从命名界面确认后） =====
        ServerPlayNetworking.registerGlobalReceiver(TeleportAnchorActivatePayload.TYPE, (payload, context) -> {
            var player = (net.minecraft.server.level.ServerPlayer) context.player();
            var server = player.level().getServer();
            if (server == null) return;
            server.execute(() -> {
                BlockPos pos = payload.pos();
                net.minecraft.world.level.Level level = player.level();
                BlockState state = level.getBlockState(pos);
                if (!(state.getBlock() instanceof TeleportAnchorBlock)) return;
                BlockEntity be = level.getBlockEntity(pos);
                if (!(be instanceof TeleportAnchorBlockEntity anchorBE)) return;

                UUID playerUuid = player.getUUID();
                if (anchorBE.isActivatedBy(playerUuid)) return; // 已激活过

                boolean wasEmpty = anchorBE.addActivator(playerUuid);
                if (wasEmpty) {
                    level.setBlock(pos, state.setValue(TeleportAnchorBlock.ACTIVE, true), 3);
                    level.sendBlockUpdated(pos, state, state.setValue(TeleportAnchorBlock.ACTIVE, true), 3);
                }

                // 粒子效果（仅本人）
                {
                    var particle = net.minecraft.core.particles.ParticleTypes.END_ROD;
                    var pkt = new net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket(
                            particle, false, false,
                            pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                            0.6f, 0.6f, 0.6f, 0.08f, 40
                    );
                    player.connection.send(pkt);
                }

                // 音效（在锚点位置播放，激活者能听见）
                level.playSound(null, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                        net.minecraft.sounds.SoundEvents.PLAYER_LEVELUP,
                        net.minecraft.sounds.SoundSource.PLAYERS, 1.0f, 1.0f);

                // 添加到玩家传送列表（附加维度池标识）
                TeleportAnchorManager manager = TeleportAnchorManager.get(server);
                String finalName = payload.name().isEmpty()
                        ? Component.translatable("screen.youzaiworldcore.teleport_anchor_name.default").getString()
                        : payload.name();
                String poolId = top.csituka.youzaiworldcore.dimensionalinventories.DimensionPoolSettings
                        .getPoolByDimension(level.dimension().identifier().toString())
                        .map(p -> p.id())
                        .orElse(null);
                manager.addPointWithName(player, pos, level.dimension(), finalName, poolId);

                player.sendSystemMessage(
                        Component.translatable("message.youzaiworldcore.teleport_anchor.activated")
                );
            });
        });

        // ===== 传送锚点排序处理器 =====
        ServerPlayNetworking.registerGlobalReceiver(TeleportAnchorReorderPayload.TYPE, (payload, context) -> {
            var player = context.player();
            var server = player.level().getServer();
            if (server != null) {
                server.execute(() -> {
                    TeleportAnchorManager manager = TeleportAnchorManager.get(server);
                    manager.movePoint((net.minecraft.server.level.ServerPlayer) player,
                            payload.fromIndex(), payload.toIndex());
                });
            }
        });

        // ===== 双开门功能切换 / 查询处理器 =====
        ServerPlayNetworking.registerGlobalReceiver(DoubleDoorsTogglePayload.ID, (payload, context) -> {
            DebugLogger.entering("ModNetworking", "DoubleDoorsTogglePayload handler");
            var player = (net.minecraft.server.level.ServerPlayer) context.player();
            var server = player.level().getServer();
            if (server == null) {
                DebugLogger.exiting("ModNetworking", "DoubleDoorsTogglePayload handler", "server is null");
                return;
            }
            server.execute(() -> {
                UUID uuid = player.getUUID();
                Boolean enabled = payload.enabled();
                if (enabled != null) {
                    DoubleDoorsState.setEnabled(uuid, enabled);
                }
                boolean cur = DoubleDoorsState.isEnabled(uuid);
                if (enabled != null) {
                    // 设置结果反馈
                    player.sendSystemMessage(Component.translatable(
                            enabled
                                    ? "youzaiworldcore.message.command.function.double_doors.set_enabled"
                                    : "youzaiworldcore.message.command.function.double_doors.set_disabled",
                            player.getName().getString()));
                } else {
                    // 查询反馈（区分默认启用 / 显式启用 / 已禁用）
                    player.sendSystemMessage(Component.translatable(
                            DoubleDoorsState.isExplicitlySet(uuid)
                                    ? "youzaiworldcore.message.command.function.double_doors.query_enabled"
                                    : (cur
                                            ? "youzaiworldcore.message.command.function.double_doors.query_default"
                                            : "youzaiworldcore.message.command.function.double_doors.query_disabled")));
                }
            });
            DebugLogger.exiting("ModNetworking", "DoubleDoorsTogglePayload handler");
        });

        // ===== 隐身功能切换处理器 =====
        ServerPlayNetworking.registerGlobalReceiver(InvisibilityPayload.ID, (payload, context) -> {
            DebugLogger.entering("ModNetworking", "InvisibilityPayload handler");
            var player = (net.minecraft.server.level.ServerPlayer) context.player();
            var server = player.level().getServer();
            if (server == null) {
                DebugLogger.exiting("ModNetworking", "InvisibilityPayload handler", "server is null");
                return;
            }
            server.execute(() -> {
                // 权限检查：OP4 或 LuckPerms 节点
                if (!InvisibilityManager.hasPermission(player)) {
                    player.sendSystemMessage(Component.literal("§c你没有权限使用隐身功能"));
                    DebugLogger.exiting("ModNetworking", "InvisibilityPayload handler", "no permission");
                    return;
                }
                // 创造模式检查（隐身功能仅创造模式可用）
                if (player.gameMode() != GameType.CREATIVE) {
                    player.sendSystemMessage(Component.literal(
                            "§c只有创造模式才能使用隐身功能（请先切换到创造模式）"));
                    DebugLogger.exiting("ModNetworking", "InvisibilityPayload handler", "not creative");
                    return;
                }
                if (payload.enabled()) {
                    DebugLogger.branch("ModNetworking", "InvisibilityPayload", true, "enabling");
                    InvisibilityManager.enable(player);
                } else {
                    DebugLogger.branch("ModNetworking", "InvisibilityPayload", false, "disabling");
                    InvisibilityManager.disable(player);
                }
            });
            DebugLogger.exiting("ModNetworking", "InvisibilityPayload handler");
        });

        // ===== 实验性功能命令转发处理器 =====
        ServerPlayNetworking.registerGlobalReceiver(ExperimentalFeaturePayload.ID, (payload, context) -> {
            DebugLogger.entering("ModNetworking", "ExperimentalFeaturePayload handler");
            var player = (net.minecraft.server.level.ServerPlayer) context.player();
            var server = player.level().getServer();
            if (server == null) {
                DebugLogger.exiting("ModNetworking", "ExperimentalFeaturePayload handler", "server is null");
                return;
            }
            server.execute(() -> {
                String id = payload.id();
                ExperimentalFeatures.FeatureEntry entry = ExperimentalFeatures.getEntry(id);
                if (entry == null) {
                    player.sendSystemMessage(Component.translatable(
                            "youzaiworldcore.message.command.experimental_feature.not_found", id));
                    DebugLogger.exiting("ModNetworking", "ExperimentalFeaturePayload handler", "entry not found");
                    return;
                }

                byte mode = payload.mode();
                boolean enabled = payload.enabled();

                switch (mode) {
                    case ExperimentalFeaturePayload.MODE_QUERY -> {
                        DebugLogger.branch("ModNetworking", "ExperimentalFeaturePayload mode", true, "query");
                        if (!LuckPermsHelper.checkPermission(player.createCommandSourceStack(),
                                LuckPermsHelper.PERMISSION_EXPERIMENTAL_FEATURE_QUERY, Commands.LEVEL_ALL)) {
                            player.sendSystemMessage(Component.literal("§c你没有权限查询实验性功能"));
                            DebugLogger.exiting("ModNetworking", "ExperimentalFeaturePayload handler", "no query permission");
                            return;
                        }
                        sendExperimentalFeatureQuery(player, entry);
                    }
                    case ExperimentalFeaturePayload.MODE_SELF -> {
                        DebugLogger.branch("ModNetworking", "ExperimentalFeaturePayload mode", false, "self");
                        if (!LuckPermsHelper.checkPermission(player.createCommandSourceStack(),
                                LuckPermsHelper.PERMISSION_EXPERIMENTAL_FEATURE_SELF, Commands.LEVEL_ALL)) {
                            player.sendSystemMessage(Component.literal("§c你没有权限切换实验性功能"));
                            DebugLogger.exiting("ModNetworking", "ExperimentalFeaturePayload handler", "no self permission");
                            return;
                        }
                        ExperimentalFeatures.setForPlayer(id, player.getUUID(), enabled);
                        FeatureSyncPayload sync = new FeatureSyncPayload(id, enabled, player.getUUID());
                        ServerPlayNetworking.send(player, sync);
                        player.sendSystemMessage(Component.literal(
                                "§a已为自己" + (enabled ? "§a启用" : "§c禁用") + "实验性功能: §f" + id));
                    }
                    case ExperimentalFeaturePayload.MODE_ALL -> {
                        DebugLogger.branch("ModNetworking", "ExperimentalFeaturePayload mode", false, "all");
                        if (!LuckPermsHelper.checkPermission(player.createCommandSourceStack(),
                                LuckPermsHelper.PERMISSION_EXPERIMENTAL_FEATURE_ADMIN, Commands.LEVEL_ADMINS)) {
                            player.sendSystemMessage(Component.literal("§c你没有权限全服切换实验性功能"));
                            DebugLogger.exiting("ModNetworking", "ExperimentalFeaturePayload handler", "no admin permission");
                            return;
                        }
                        ExperimentalFeatures.setGlobal(id, enabled);
                        FeatureSyncPayload sync = new FeatureSyncPayload(id, enabled, null);
                        for (net.minecraft.server.level.ServerPlayer p : server.getPlayerList().getPlayers()) {
                            ServerPlayNetworking.send(p, sync);
                        }
                        player.sendSystemMessage(Component.literal(
                                "§a已全服" + (enabled ? "§a启用" : "§c禁用") + "实验性功能: §f" + id));
                    }
                    case ExperimentalFeaturePayload.MODE_ONLY -> {
                        DebugLogger.branch("ModNetworking", "ExperimentalFeaturePayload mode", false, "only");
                        if (!LuckPermsHelper.checkPermission(player.createCommandSourceStack(),
                                LuckPermsHelper.PERMISSION_EXPERIMENTAL_FEATURE_ADMIN, Commands.LEVEL_ADMINS)) {
                            player.sendSystemMessage(Component.literal("§c你没有权限为他人切换实验性功能"));
                            DebugLogger.exiting("ModNetworking", "ExperimentalFeaturePayload handler", "no admin permission");
                            return;
                        }
                        String targetName = payload.targetName();
                        if (targetName == null || targetName.isEmpty()) {
                            player.sendSystemMessage(Component.literal("§c未指定目标玩家"));
                            DebugLogger.exiting("ModNetworking", "ExperimentalFeaturePayload handler", "target is null");
                            return;
                        }
                        net.minecraft.server.level.ServerPlayer target =
                                server.getPlayerList().getPlayerByName(targetName);
                        if (target == null) {
                            player.sendSystemMessage(Component.literal("§c找不到玩家: " + targetName));
                            DebugLogger.exiting("ModNetworking", "ExperimentalFeaturePayload handler", "target not found");
                            return;
                        }
                        ExperimentalFeatures.setForPlayer(id, target.getUUID(), enabled);
                        FeatureSyncPayload sync = new FeatureSyncPayload(id, enabled, target.getUUID());
                        ServerPlayNetworking.send(target, sync);
                        player.sendSystemMessage(Component.literal(
                                "§a已为目标玩家 §f" + target.getName().getString() + " §a"
                                        + (enabled ? "§a启用" : "§c禁用") + " §f实验性功能: " + id));
                    }
                    default -> {
                        DebugLogger.warn("ModNetworking", "ExperimentalFeaturePayload 未知 mode=%d", mode);
                    }
                }
            });
            DebugLogger.exiting("ModNetworking", "ExperimentalFeaturePayload handler");
        });

        // ===== 属性加点系统数据包 =====
        PayloadTypeRegistry.clientboundPlay().register(AttributeSyncPayload.TYPE, AttributeSyncPayload.STREAM_CODEC);
        DebugLogger.info("ModNetworking", "Registered clientbound packet: AttributeSyncPayload");
        PayloadTypeRegistry.serverboundPlay().register(AttributeUpgradePayload.TYPE, AttributeUpgradePayload.STREAM_CODEC);
        DebugLogger.info("ModNetworking", "Registered serverbound packet: AttributeUpgradePayload");

        // ===== 属性加点 C2S 处理器 =====
        ServerPlayNetworking.registerGlobalReceiver(AttributeUpgradePayload.TYPE, (payload, context) -> {
            var player = (net.minecraft.server.level.ServerPlayer) context.player();
            player.level().getServer().execute(() -> {
                AttributeManager.handleUpgrade(player, payload.attributeKey());
            });
        });

        DebugLogger.exiting("ModNetworking", "initialize");
    }

    /**
     * 向玩家发送实验性功能查询信息面板（含名称、ID、提供者、来源、全局状态）。
     * 等价于原 {@code ExperimentalFeatureCommand.queryFeature} 的展示逻辑，
     * 现由数据包处理器在权限校验通过后调用。
     */
    private static void sendExperimentalFeatureQuery(net.minecraft.server.level.ServerPlayer player,
                                                  ExperimentalFeatures.FeatureEntry entry) {
        DebugLogger.entering("ModNetworking", "sendExperimentalFeatureQuery", "id=" + entry.id());
        boolean globalEnabled = ExperimentalFeatures.isGlobalEnabled(entry.id());
        MutableComponent text = Component.literal(
                "§6===== §e实验性功能 §6=====\n"
        );
        text.append(Component.literal("§7名称：§f" + entry.name() + "\n"));
        text.append(Component.literal("§7内部ID：§f" + entry.id() + "\n"));

        // 提供者
        text.append(Component.literal("§7提供者：")
                .append(Component.literal("§b§n" + entry.provider())
                        .withStyle(style -> style
                                .withClickEvent(new ClickEvent.OpenUrl(java.net.URI.create(entry.providerUrl())))
                                .withHoverEvent(new HoverEvent.ShowText(
                                        Component.literal("§a点击打开: " + entry.providerUrl())))
                        )
                ).append(Component.literal("\n")));

        text.append(Component.literal("§7描述：§f" + entry.description() + "\n"));

        // 来源
        text.append(Component.literal("§7来源：")
                .append(Component.literal("§b§n" + entry.source())
                        .withStyle(style -> style
                                .withClickEvent(new ClickEvent.OpenUrl(java.net.URI.create(entry.sourceUrl())))
                                .withHoverEvent(new HoverEvent.ShowText(
                                        Component.literal("§a点击打开: " + entry.sourceUrl())))
                        )
                ).append(Component.literal("\n")));

        text.append(Component.literal("§7全局状态：" + (globalEnabled ? "§a已启用" : "§c已禁用") + "\n"));
        text.append(Component.literal("§6================================"));

        player.sendSystemMessage(text);
        DebugLogger.exiting("ModNetworking", "sendExperimentalFeatureQuery");
    }
}
