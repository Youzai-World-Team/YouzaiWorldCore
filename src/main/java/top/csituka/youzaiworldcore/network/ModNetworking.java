package top.csituka.youzaiworldcore.network;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
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
import top.csituka.youzaiworldcore.invisibility.InvisibilityManager;
import top.csituka.youzaiworldcore.screen.DecompositionTableMenu;
import top.csituka.youzaiworldcore.screen.FlyBeaconMenu;
import top.csituka.youzaiworldcore.skill.AttributeManager;
import top.csituka.youzaiworldcore.util.DebugLogger;
import eu.pb4.trinkets.api.TrinketsApi;
import eu.pb4.trinkets.api.TrinketAttachment;
import eu.pb4.trinkets.api.TrinketInventory;
import eu.pb4.trinkets.api.TrinketSlotAccess;
import eu.pb4.trinkets.impl.TrinketUtilities;
import top.csituka.youzaiworldcore.util.TrinketHelper;

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

        // ===== 宠物管理命令转发数据包 =====
        PayloadTypeRegistry.serverboundPlay().register(PetCommandPayload.ID, PetCommandPayload.STREAM_CODEC);
        DebugLogger.info("ModNetworking", "Registered serverbound packet: PetCommandPayload");

        // ===== Trinkets 饰品槽交互数据包 =====
        PayloadTypeRegistry.serverboundPlay().register(TrinketInteractPayload.ID, TrinketInteractPayload.STREAM_CODEC);
        DebugLogger.info("ModNetworking", "Registered serverbound packet: TrinketInteractPayload");

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

        // ===== 宠物管理命令 C2S 转发处理器 =====
        ServerPlayNetworking.registerGlobalReceiver(PetCommandPayload.ID, (payload, context) -> {
            var player = (net.minecraft.server.level.ServerPlayer) context.player();
            DebugLogger.entering("ModNetworking", "PetCommandPayload handler", "args=" + payload.args());
            var server = player.level().getServer();
            if (server == null) {
                DebugLogger.exiting("ModNetworking", "PetCommandPayload handler", "server is null");
                return;
            }
            server.execute(() -> {
                try {
                    // 重建完整命令字符串：yzwc pet <args>
                    String fullCommand = "yzwc pet " + payload.args();
                    DebugLogger.info("ModNetworking", "执行宠物命令: /%s", fullCommand);
                    server.getCommands().performPrefixedCommand(player.createCommandSourceStack(), fullCommand);
                } catch (Exception e) {
                    DebugLogger.error("ModNetworking", "执行宠物命令失败: %s", e.getMessage());
                }
            });
            DebugLogger.exiting("ModNetworking", "PetCommandPayload handler");
        });

        // ===== Trinkets 饰品槽交互处理器（服务端权威操作） =====
        ServerPlayNetworking.registerGlobalReceiver(TrinketInteractPayload.ID, (payload, context) -> {
            DebugLogger.entering("ModNetworking", "TrinketInteractPayload handler");
            var player = context.player();
            var server = player.level().getServer();
            if (server == null) {
                DebugLogger.exiting("ModNetworking", "TrinketInteractPayload handler", "server is null");
                return;
            }
            server.execute(() -> {
                try {
                    eu.pb4.trinkets.api.TrinketAttachment attachment = eu.pb4.trinkets.api.TrinketsApi.getAttachment(player);
                    if (attachment == null) {
                        DebugLogger.warn("ModNetworking", "TrinketAttachment is null for player %s", player.getName().getString());
                        return;
                    }
                    eu.pb4.trinkets.api.TrinketInventory inv = attachment.getInventories().get(payload.groupKey());
                    if (inv == null) {
                        DebugLogger.warn("ModNetworking", "TrinketInventory '%s' not found", payload.groupKey());
                        return;
                    }
                    eu.pb4.trinkets.api.TrinketSlotAccess access = inv.getSlotAccess(payload.slotIndex());
                    if (access == null) {
                        DebugLogger.warn("ModNetworking", "TrinketSlotAccess at %s[%d] not found", payload.groupKey(), payload.slotIndex());
                        return;
                    }

                    net.minecraft.world.item.ItemStack carried = player.containerMenu.getCarried();
                    net.minecraft.world.item.ItemStack slotStack = access.get();

                    switch (payload.action()) {
                        case TrinketInteractPayload.ACTION_PLACE:
                            if (!carried.isEmpty()) {
                                if (!slotStack.isEmpty()) {
                                    DebugLogger.info("ModNetworking", "PLACE rejected: slot %s[%d] is not empty",
                                            payload.groupKey(), payload.slotIndex());
                                    return;
                                }
                                if (!access.slotType().validatorCheck(carried, access, player)) {
                                    DebugLogger.info("ModNetworking", "Validator rejected %s -> %s[%d]",
                                            carried.getHoverName().getString(), payload.groupKey(), payload.slotIndex());
                                    return;
                                }
                                access.set(carried.copy());
                                inv.setChanged();
                                TrinketUtilities.callTrinketEquipmentChange(slotStack, carried, access, player);
                                player.containerMenu.setCarried(net.minecraft.world.item.ItemStack.EMPTY);
                                DebugLogger.info("ModNetworking", "Trinket PLACE: %s -> %s[%d]", carried.getHoverName().getString(), payload.groupKey(), payload.slotIndex());
                            }
                            break;
                        case TrinketInteractPayload.ACTION_TAKE:
                            if (!slotStack.isEmpty()) {
                                player.containerMenu.setCarried(slotStack.copy());
                                access.set(net.minecraft.world.item.ItemStack.EMPTY);
                                inv.setChanged();
                                TrinketUtilities.callTrinketEquipmentChange(slotStack, net.minecraft.world.item.ItemStack.EMPTY, access, player);
                                DebugLogger.info("ModNetworking", "Trinket TAKE: %s[%d] -> cursor", payload.groupKey(), payload.slotIndex());
                            }
                            break;
                        case TrinketInteractPayload.ACTION_SWAP:
                            if (!carried.isEmpty()) {
                                if (!access.slotType().validatorCheck(carried, access, player)) {
                                    DebugLogger.info("ModNetworking", "Validator rejected %s -> %s[%d] (SWAP)",
                                            carried.getHoverName().getString(), payload.groupKey(), payload.slotIndex());
                                    return;
                                }
                            }
                            access.set(carried.copy());
                            player.containerMenu.setCarried(slotStack.copy());
                            inv.setChanged();
                            TrinketUtilities.callTrinketEquipmentChange(slotStack, carried, access, player);
                            DebugLogger.info("ModNetworking", "Trinket SWAP: cursor <-> %s[%d]", payload.groupKey(), payload.slotIndex());
                            break;
                        default:
                            DebugLogger.warn("ModNetworking", "Unknown trinket action: %d", payload.action());
                            break;
                    }
                    player.containerMenu.broadcastChanges();
                } catch (Exception e) {
                    DebugLogger.error("ModNetworking", "Trinket interact handler error: %s", e.getMessage());
                }
            });
            DebugLogger.exiting("ModNetworking", "TrinketInteractPayload handler");
        });

        // ======================================================================
        // 邮件系统（Mail）—— 数据包注册
        // ======================================================================

        // C2S 注册
        PayloadTypeRegistry.serverboundPlay().register(MailComposeOpenPayload.ID, MailComposeOpenPayload.STREAM_CODEC);
        DebugLogger.info("ModNetworking", "Registered serverbound packet: MailComposeOpenPayload");
        PayloadTypeRegistry.serverboundPlay().register(MailOpenPayload.ID, MailOpenPayload.STREAM_CODEC);
        DebugLogger.info("ModNetworking", "Registered serverbound packet: MailOpenPayload");
        PayloadTypeRegistry.serverboundPlay().register(MailSentListRequestPayload.ID, MailSentListRequestPayload.STREAM_CODEC);
        DebugLogger.info("ModNetworking", "Registered serverbound packet: MailSentListRequestPayload");
        PayloadTypeRegistry.serverboundPlay().register(MailRecallPayload.ID, MailRecallPayload.STREAM_CODEC);
        DebugLogger.info("ModNetworking", "Registered serverbound packet: MailRecallPayload");
        PayloadTypeRegistry.serverboundPlay().register(MailPurgePayload.ID, MailPurgePayload.STREAM_CODEC);
        DebugLogger.info("ModNetworking", "Registered serverbound packet: MailPurgePayload");
        PayloadTypeRegistry.serverboundPlay().register(MailListRequestPayload.ID, MailListRequestPayload.STREAM_CODEC);
        DebugLogger.info("ModNetworking", "Registered serverbound packet: MailListRequestPayload");
        PayloadTypeRegistry.serverboundPlay().register(MailFetchPayload.ID, MailFetchPayload.STREAM_CODEC);
        DebugLogger.info("ModNetworking", "Registered serverbound packet: MailFetchPayload");
        PayloadTypeRegistry.serverboundPlay().register(MailActionPayload.ID, MailActionPayload.STREAM_CODEC);
        DebugLogger.info("ModNetworking", "Registered serverbound packet: MailActionPayload");
        PayloadTypeRegistry.serverboundPlay().register(MailAdminSendPayload.ID, MailAdminSendPayload.STREAM_CODEC);
        DebugLogger.info("ModNetworking", "Registered serverbound packet: MailAdminSendPayload");
        PayloadTypeRegistry.serverboundPlay().register(MailAdminEditPayload.ID, MailAdminEditPayload.STREAM_CODEC);
        DebugLogger.info("ModNetworking", "Registered serverbound packet: MailAdminEditPayload");

        // S2C 注册
        PayloadTypeRegistry.clientboundPlay().register(OpenMailComposePayload.ID, OpenMailComposePayload.STREAM_CODEC);
        DebugLogger.info("ModNetworking", "Registered clientbound packet: OpenMailComposePayload");
        PayloadTypeRegistry.clientboundPlay().register(MailListPayload.ID, MailListPayload.STREAM_CODEC);
        DebugLogger.info("ModNetworking", "Registered clientbound packet: MailListPayload");
        PayloadTypeRegistry.clientboundPlay().register(MailSentListPayload.ID, MailSentListPayload.STREAM_CODEC);
        DebugLogger.info("ModNetworking", "Registered clientbound packet: MailSentListPayload");
        PayloadTypeRegistry.clientboundPlay().register(MailUpdatePayload.ID, MailUpdatePayload.STREAM_CODEC);
        DebugLogger.info("ModNetworking", "Registered clientbound packet: MailUpdatePayload");
        PayloadTypeRegistry.clientboundPlay().register(MailOpResultPayload.ID, MailOpResultPayload.STREAM_CODEC);
        DebugLogger.info("ModNetworking", "Registered clientbound packet: MailOpResultPayload");
        PayloadTypeRegistry.clientboundPlay().register(MailUnreadCountPayload.ID, MailUnreadCountPayload.STREAM_CODEC);
        DebugLogger.info("ModNetworking", "Registered clientbound packet: MailUnreadCountPayload");

        // ======================================================================
        // 邮件系统（Mail）—— 服务端接收处理器
        // ======================================================================

        // 1. 打开发布 GUI
        ServerPlayNetworking.registerGlobalReceiver(MailComposeOpenPayload.ID, (payload, context) -> {
            DebugLogger.entering("ModNetworking", "MailComposeOpenPayload handler");
            var player = (net.minecraft.server.level.ServerPlayer) context.player();
            var server = player.level().getServer();
            if (server == null) return;
            server.execute(() -> {
                if (top.csituka.youzaiworldcore.mail.MailPermissionHelper.hasMailPermission(player)) {
                    ServerPlayNetworking.send(player, new OpenMailComposePayload());
                    DebugLogger.info("ModNetworking", "Opened mail compose GUI for %s", player.getScoreboardName());
                } else {
                    player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§c你没有权限发布邮件"));
                }
            });
            DebugLogger.exiting("ModNetworking", "MailComposeOpenPayload handler");
        });

        // 2. 打开收件箱
        ServerPlayNetworking.registerGlobalReceiver(MailOpenPayload.ID, (payload, context) -> {
            DebugLogger.entering("ModNetworking", "MailOpenPayload handler");
            var player = (net.minecraft.server.level.ServerPlayer) context.player();
            var server = player.level().getServer();
            if (server == null) return;
            server.execute(() -> {
                java.util.UUID uuid = player.getUUID();
                var box = top.csituka.youzaiworldcore.mail.MailDataStorage.load(uuid);
                java.util.List<MailStreamCodecs.MailRefAndMail> entries = new java.util.ArrayList<>();
                for (var ref : box.getMails()) {
                    var mail = top.csituka.youzaiworldcore.mail.SentMailRepository.get(ref.getMailId());
                    if (mail != null && !mail.isHidden()) {
                        entries.add(new MailStreamCodecs.MailRefAndMail(ref, mail));
                    }
                }
                ServerPlayNetworking.send(player, new MailListPayload(entries));
                DebugLogger.info("ModNetworking", "Sent inbox for %s: %d entries", player.getScoreboardName(), entries.size());
            });
            DebugLogger.exiting("ModNetworking", "MailOpenPayload handler");
        });

        // 3. 请求已发送列表
        ServerPlayNetworking.registerGlobalReceiver(MailSentListRequestPayload.ID, (payload, context) -> {
            DebugLogger.entering("ModNetworking", "MailSentListRequestPayload handler");
            var player = (net.minecraft.server.level.ServerPlayer) context.player();
            var server = player.level().getServer();
            if (server == null) return;
            server.execute(() -> {
                if (!top.csituka.youzaiworldcore.mail.MailPermissionHelper.hasMailPermission(player)) {
                    DebugLogger.exiting("ModNetworking", "MailSentListRequestPayload handler", "no permission");
                    return;
                }
                java.util.List<MailStreamCodecs.MailSummary> summaries = new java.util.ArrayList<>();
                for (var mail : top.csituka.youzaiworldcore.mail.SentMailRepository.getAll()) {
                    summaries.add(new MailStreamCodecs.MailSummary(
                            mail.getId(), mail.getType(), mail.getTitle(),
                            mail.getScopeSummary(), mail.getCreatedTime(), mail.getExpireTime(), mail.getSender()));
                }
                ServerPlayNetworking.send(player, new MailSentListPayload(summaries));
                DebugLogger.info("ModNetworking", "Sent mail sent list for %s: %d entries", player.getScoreboardName(), summaries.size());
            });
            DebugLogger.exiting("ModNetworking", "MailSentListRequestPayload handler");
        });

        // 4. 撤回邮件
        ServerPlayNetworking.registerGlobalReceiver(MailRecallPayload.ID, (payload, context) -> {
            DebugLogger.entering("ModNetworking", "MailRecallPayload handler");
            var player = (net.minecraft.server.level.ServerPlayer) context.player();
            var server = player.level().getServer();
            if (server == null) return;
            server.execute(() -> {
                if (!top.csituka.youzaiworldcore.mail.MailPermissionHelper.hasMailPermission(player)) {
                    DebugLogger.exiting("ModNetworking", "MailRecallPayload handler", "no permission");
                    return;
                }
                boolean success = top.csituka.youzaiworldcore.mail.MailManager.recall(payload.mailId());
                if (success) {
                    // 推送移除到所有在线接收者
                    for (var online : server.getPlayerList().getPlayers()) {
                        var box = top.csituka.youzaiworldcore.mail.MailDataStorage.load(online.getUUID());
                        boolean hasRef = box.getMails().stream().anyMatch(r -> r.getMailId().equals(payload.mailId()));
                        if (hasRef) {
                            ServerPlayNetworking.send(online, MailUpdatePayload.createRemove(payload.mailId()));
                        }
                    }
                    ServerPlayNetworking.send(player, MailOpResultPayload.success(payload.mailId(), "已撤回"));
                } else {
                    ServerPlayNetworking.send(player, MailOpResultPayload.failure(payload.mailId(), "邮件不存在或已撤回"));
                }
            });
            DebugLogger.exiting("ModNetworking", "MailRecallPayload handler");
        });

        // 5. 清理过期邮件
        ServerPlayNetworking.registerGlobalReceiver(MailPurgePayload.ID, (payload, context) -> {
            DebugLogger.entering("ModNetworking", "MailPurgePayload handler");
            var player = (net.minecraft.server.level.ServerPlayer) context.player();
            var server = player.level().getServer();
            if (server == null) return;
            server.execute(() -> {
                if (!top.csituka.youzaiworldcore.mail.MailPermissionHelper.hasMailPermission(player)) {
                    DebugLogger.exiting("ModNetworking", "MailPurgePayload handler", "no permission");
                    return;
                }
                top.csituka.youzaiworldcore.mail.MailManager.purge();
                ServerPlayNetworking.send(player, MailOpResultPayload.success(null, "已清理过期邮件"));
            });
            DebugLogger.exiting("ModNetworking", "MailPurgePayload handler");
        });

        // 6. 查看指定玩家信箱
        ServerPlayNetworking.registerGlobalReceiver(MailListRequestPayload.ID, (payload, context) -> {
            DebugLogger.entering("ModNetworking", "MailListRequestPayload handler");
            var player = (net.minecraft.server.level.ServerPlayer) context.player();
            var server = player.level().getServer();
            if (server == null) return;
            server.execute(() -> {
                if (!top.csituka.youzaiworldcore.mail.MailPermissionHelper.hasMailPermission(player)) {
                    DebugLogger.exiting("ModNetworking", "MailListRequestPayload handler", "no permission");
                    return;
                }
                String targetName = payload.targetPlayer();
                java.util.UUID targetUuid;
                if (targetName.isEmpty()) {
                    targetUuid = player.getUUID();
                } else {
                    var acc = top.csituka.youzaiworldcore.account.data.AccountDataStorage.get(targetName);
                    if (acc == null) {
                        ServerPlayNetworking.send(player, MailOpResultPayload.failure(null, "找不到玩家: " + targetName));
                        DebugLogger.exiting("ModNetworking", "MailListRequestPayload handler", "player not found");
                        return;
                    }
                    targetUuid = java.util.UUID.fromString(acc.uuid);
                }
                var box = top.csituka.youzaiworldcore.mail.MailDataStorage.load(targetUuid);
                java.util.List<MailStreamCodecs.MailRefAndMail> entries = new java.util.ArrayList<>();
                for (var ref : box.getMails()) {
                    var mail = top.csituka.youzaiworldcore.mail.SentMailRepository.get(ref.getMailId());
                    if (mail != null && !mail.isHidden()) {
                        entries.add(new MailStreamCodecs.MailRefAndMail(ref, mail));
                    }
                }
                ServerPlayNetworking.send(player, new MailListPayload(entries));
                DebugLogger.info("ModNetworking", "Sent inbox for target %s: %d entries", targetName.isEmpty() ? "self" : targetName, entries.size());
            });
            DebugLogger.exiting("ModNetworking", "MailListRequestPayload handler");
        });

        // 7. 编辑预填
        ServerPlayNetworking.registerGlobalReceiver(MailFetchPayload.ID, (payload, context) -> {
            DebugLogger.entering("ModNetworking", "MailFetchPayload handler");
            var player = (net.minecraft.server.level.ServerPlayer) context.player();
            var server = player.level().getServer();
            if (server == null) return;
            server.execute(() -> {
                if (!top.csituka.youzaiworldcore.mail.MailPermissionHelper.hasMailPermission(player)) {
                    DebugLogger.exiting("ModNetworking", "MailFetchPayload handler", "no permission");
                    return;
                }
                var mail = top.csituka.youzaiworldcore.mail.SentMailRepository.get(payload.mailId());
                if (mail == null) {
                    ServerPlayNetworking.send(player, MailOpResultPayload.failure(payload.mailId(), "邮件不存在"));
                    DebugLogger.exiting("ModNetworking", "MailFetchPayload handler", "mail not found");
                    return;
                }
                var canEditResult = top.csituka.youzaiworldcore.mail.MailManager.computeCanEdit(payload.mailId());
                // 获取玩家自己的 ref（用于 claimed/starred/read 状态）
                var box = top.csituka.youzaiworldcore.mail.MailDataStorage.load(player.getUUID());
                var optRef = box.getMails().stream().filter(r -> r.getMailId().equals(payload.mailId())).findFirst();
                var ref = optRef.orElse(new top.csituka.youzaiworldcore.mail.MailRef(payload.mailId()));

                if (canEditResult.canEdit()) {
                    // 有附件且无人领取：编辑期间隐藏
                    if (!canEditResult.needHidden()) {
                        // 无附件：直接返回可编辑
                        ServerPlayNetworking.send(player, MailUpdatePayload.createEditPrefill(ref, mail, true));
                    } else {
                        // 需要隐藏
                        mail.setHidden(true);
                        top.csituka.youzaiworldcore.mail.SentMailRepository.put(mail);
                        // 向所有在线接收者推送隐藏
                        for (var online : server.getPlayerList().getPlayers()) {
                            var onlineBox = top.csituka.youzaiworldcore.mail.MailDataStorage.load(online.getUUID());
                            boolean hasRef = onlineBox.getMails().stream().anyMatch(r -> r.getMailId().equals(payload.mailId()));
                            if (hasRef) {
                                ServerPlayNetworking.send(online, MailUpdatePayload.createRemove(payload.mailId()));
                            }
                        }
                        ServerPlayNetworking.send(player, MailUpdatePayload.createEditPrefill(ref, mail, true));
                    }
                } else {
                    // 不可编辑（已有人领取附件）
                    ServerPlayNetworking.send(player, MailUpdatePayload.createEditPrefill(ref, mail, false));
                }
                DebugLogger.info("ModNetworking", "Fetched mail %s for edit (canEdit=%s)", payload.mailId(), canEditResult.canEdit());
            });
            DebugLogger.exiting("ModNetworking", "MailFetchPayload handler");
        });

        // 8. 邮件操作（OPEN/READ/STAR/UNSTAR/CLAIM/DELETE）
        ServerPlayNetworking.registerGlobalReceiver(MailActionPayload.ID, (payload, context) -> {
            DebugLogger.entering("ModNetworking", "MailActionPayload handler", "action=" + payload.action());
            var player = (net.minecraft.server.level.ServerPlayer) context.player();
            var server = player.level().getServer();
            if (server == null) return;
            server.execute(() -> {
                java.util.UUID uuid = player.getUUID();
                var box = top.csituka.youzaiworldcore.mail.MailDataStorage.load(uuid);
                var optRef = box.getMails().stream().filter(r -> r.getMailId().equals(payload.mailId())).findFirst();
                if (optRef.isEmpty()) {
                    DebugLogger.exiting("ModNetworking", "MailActionPayload handler", "ref not found");
                    return;
                }
                var ref = optRef.get();
                switch (payload.action()) {
                    case MailActionPayload.ACTION_OPEN:
                    case MailActionPayload.ACTION_READ:
                        if (!ref.isRead()) {
                            ref.setRead(true);
                            top.csituka.youzaiworldcore.mail.MailDataStorage.updateRef(uuid, ref);
                        }
                        break;
                    case MailActionPayload.ACTION_STAR:
                        ref.setStarred(true);
                        top.csituka.youzaiworldcore.mail.MailDataStorage.updateRef(uuid, ref);
                        break;
                    case MailActionPayload.ACTION_UNSTAR:
                        ref.setStarred(false);
                        top.csituka.youzaiworldcore.mail.MailDataStorage.updateRef(uuid, ref);
                        break;
                    case MailActionPayload.ACTION_CLAIM:
                        boolean claimResult = top.csituka.youzaiworldcore.mail.MailManager.claim(player, payload.mailId());
                        if (claimResult) {
                            ref.setClaimed(true);
                            top.csituka.youzaiworldcore.mail.MailDataStorage.updateRef(uuid, ref);
                            ServerPlayNetworking.send(player, MailOpResultPayload.success(payload.mailId(), "已领取奖励"));
                        } else {
                            ServerPlayNetworking.send(player, MailOpResultPayload.failure(payload.mailId(), "领取失败"));
                        }
                        break;
                    case MailActionPayload.ACTION_DELETE:
                        top.csituka.youzaiworldcore.mail.MailDataStorage.removeRef(uuid, payload.mailId());
                        ServerPlayNetworking.send(player, MailUpdatePayload.createRemove(payload.mailId()));
                        break;
                }
                DebugLogger.info("ModNetworking", "Mail action %s processed for %s", payload.action(), player.getScoreboardName());
            });
            DebugLogger.exiting("ModNetworking", "MailActionPayload handler");
        });

        // 9. 发布邮件
        ServerPlayNetworking.registerGlobalReceiver(MailAdminSendPayload.ID, (payload, context) -> {
            DebugLogger.entering("ModNetworking", "MailAdminSendPayload handler", "title=" + payload.title());
            var player = (net.minecraft.server.level.ServerPlayer) context.player();
            var server = player.level().getServer();
            if (server == null) return;
            server.execute(() -> {
                if (!top.csituka.youzaiworldcore.mail.MailPermissionHelper.hasMailPermission(player)) {
                    ServerPlayNetworking.send(player, MailOpResultPayload.failure(null, "没有权限"));
                    DebugLogger.exiting("ModNetworking", "MailAdminSendPayload handler", "no permission");
                    return;
                }
                // 校验必填项
                if (payload.targets().isEmpty()) {
                    ServerPlayNetworking.send(player, MailOpResultPayload.failure(null, "请至少选择一个接收范围"));
                    DebugLogger.exiting("ModNetworking", "MailAdminSendPayload handler", "no targets");
                    return;
                }
                if (payload.title().isEmpty()) {
                    ServerPlayNetworking.send(player, MailOpResultPayload.failure(null, "主题不能为空"));
                    DebugLogger.exiting("ModNetworking", "MailAdminSendPayload handler", "empty title");
                    return;
                }
                // 转换 AttachmentData -> MailAttachment（将 ItemStack 序列化为 NBT 字符串）
                java.util.List<top.csituka.youzaiworldcore.mail.MailAttachment> mailAtts = convertAttachments(payload.attachments(), server);
                java.util.UUID mailId = top.csituka.youzaiworldcore.mail.MailManager.send(
                        player, payload.targets(), payload.mailType(),
                        payload.title(), payload.body(), payload.expireOption(), mailAtts);
                ServerPlayNetworking.send(player, MailOpResultPayload.success(mailId, "已发布"));
                DebugLogger.info("ModNetworking", "Mail published: id=%s, title=%s, targets=%d", mailId, payload.title(), payload.targets().size());
            });
            DebugLogger.exiting("ModNetworking", "MailAdminSendPayload handler");
        });

        // 10. 编辑/取消编辑邮件
        ServerPlayNetworking.registerGlobalReceiver(MailAdminEditPayload.ID, (payload, context) -> {
            DebugLogger.entering("ModNetworking", "MailAdminEditPayload handler", "mailId=" + payload.mailId());
            var player = (net.minecraft.server.level.ServerPlayer) context.player();
            var server = player.level().getServer();
            if (server == null) return;
            server.execute(() -> {
                if (!top.csituka.youzaiworldcore.mail.MailPermissionHelper.hasMailPermission(player)) {
                    ServerPlayNetworking.send(player, MailOpResultPayload.failure(payload.mailId(), "没有权限"));
                    DebugLogger.exiting("ModNetworking", "MailAdminEditPayload handler", "no permission");
                    return;
                }
                if (payload.cancel()) {
                    // 取消编辑：恢复 hidden=false
                    var mail = top.csituka.youzaiworldcore.mail.SentMailRepository.get(payload.mailId());
                    if (mail != null && mail.isHidden()) {
                        mail.setHidden(false);
                        top.csituka.youzaiworldcore.mail.SentMailRepository.put(mail);
                        // 向所有在线接收者推送恢复
                        for (var online : server.getPlayerList().getPlayers()) {
                            var onlineBox = top.csituka.youzaiworldcore.mail.MailDataStorage.load(online.getUUID());
                            boolean hasRef = onlineBox.getMails().stream().anyMatch(r -> r.getMailId().equals(payload.mailId()));
                            if (hasRef) {
                                var optRef = onlineBox.getMails().stream().filter(r -> r.getMailId().equals(payload.mailId())).findFirst();
                                optRef.ifPresent(ref -> ServerPlayNetworking.send(online,
                                        MailUpdatePayload.createUpdate(ref, mail)));
                            }
                        }
                    }
                    ServerPlayNetworking.send(player, MailOpResultPayload.success(payload.mailId(), "已取消编辑"));
                } else {
                    // 实际编辑
                    var canEditResult = top.csituka.youzaiworldcore.mail.MailManager.computeCanEdit(payload.mailId());
                    if (!canEditResult.canEdit()) {
                        ServerPlayNetworking.send(player,
                                MailOpResultPayload.failure(payload.mailId(), canEditResult.denyReason().isEmpty() ? "不可编辑" : canEditResult.denyReason()));
                        DebugLogger.exiting("ModNetworking", "MailAdminEditPayload handler", "cannot edit");
                        return;
                    }
                    java.util.List<top.csituka.youzaiworldcore.mail.MailAttachment> mailAtts = convertAttachments(payload.attachments(), server);
                    boolean success = top.csituka.youzaiworldcore.mail.MailManager.edit(
                            payload.mailId(), payload.targets(), payload.mailType(),
                            payload.title(), payload.body(), payload.expireOption(), mailAtts);
                    if (success) {
                        ServerPlayNetworking.send(player, MailOpResultPayload.success(payload.mailId(), "已保存修改"));
                        // 向在线接收者推送更新
                        var mail = top.csituka.youzaiworldcore.mail.SentMailRepository.get(payload.mailId());
                        if (mail != null) {
                            mail.setHidden(false);
                            top.csituka.youzaiworldcore.mail.SentMailRepository.put(mail);
                            for (var online : server.getPlayerList().getPlayers()) {
                                var onlineBox = top.csituka.youzaiworldcore.mail.MailDataStorage.load(online.getUUID());
                                boolean hasRef = onlineBox.getMails().stream().anyMatch(r -> r.getMailId().equals(payload.mailId()));
                                if (hasRef) {
                                    var optRef = onlineBox.getMails().stream().filter(r -> r.getMailId().equals(payload.mailId())).findFirst();
                                    optRef.ifPresent(ref -> ServerPlayNetworking.send(online,
                                            MailUpdatePayload.createUpdate(ref, mail)));
                                }
                            }
                        }
                    } else {
                        ServerPlayNetworking.send(player, MailOpResultPayload.failure(payload.mailId(), "编辑失败，邮件可能已被撤回"));
                    }
                }
                DebugLogger.info("ModNetworking", "MailAdminEditPayload processed: mailId=%s, cancel=%s", payload.mailId(), payload.cancel());
            });
            DebugLogger.exiting("ModNetworking", "MailAdminEditPayload handler");
        });

        DebugLogger.exiting("ModNetworking", "initialize");
    }

    /**
     * 将网络传输的 AttachmentData（含 ItemStack）转换为磁盘存储的 MailAttachment（NBT 字符串）。
     */
    private static java.util.List<top.csituka.youzaiworldcore.mail.MailAttachment> convertAttachments(
            java.util.List<AttachmentData> dataList, net.minecraft.server.MinecraftServer server) {
        if (dataList == null) return java.util.List.of();
        var lookup = server.registryAccess();
        return dataList.stream().map(ad -> {
            String itemNbt = null;
            if (ad.itemStack() != null && !ad.itemStack().isEmpty()) {
                try {
                    net.minecraft.nbt.CompoundTag tag = (net.minecraft.nbt.CompoundTag)
                            net.minecraft.world.item.ItemStack.CODEC.encodeStart(
                                    lookup.createSerializationContext(net.minecraft.nbt.NbtOps.INSTANCE),
                                    ad.itemStack()).getOrThrow();
                    itemNbt = tag.toString();
                } catch (Exception e) {
                    DebugLogger.error("ModNetworking", "序列化物品附件失败: %s", e.getMessage());
                }
            }
            return new top.csituka.youzaiworldcore.mail.MailAttachment(ad.type(), ad.data(), ad.amount(), itemNbt);
        }).collect(java.util.stream.Collectors.toList());
    }
}
