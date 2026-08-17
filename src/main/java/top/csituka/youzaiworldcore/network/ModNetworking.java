package top.csituka.youzaiworldcore.network;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.advancements.triggers.CriteriaTriggers;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import top.csituka.youzaiworldcore.block.TeleportAnchorBlock;
import top.csituka.youzaiworldcore.block.entity.FlyBeaconBlockEntity;
import top.csituka.youzaiworldcore.block.entity.TeleportAnchorBlockEntity;
import top.csituka.youzaiworldcore.data.TeleportAnchorManager;
import top.csituka.youzaiworldcore.data.TeleportAnchorData;
import top.csituka.youzaiworldcore.item.tool.TeleportStoneItem;
import top.csituka.youzaiworldcore.item.tool.WarpScrollItem;
import top.csituka.youzaiworldcore.network.TeleportAnchorListPayload.EntryType;
import top.csituka.youzaiworldcore.config.DoubleDoorsState;
import top.csituka.youzaiworldcore.afk.AfkManager;
import top.csituka.youzaiworldcore.dimensionalinventories.DimensionPoolManager;
import top.csituka.youzaiworldcore.dimensionalinventories.WorldPoolTeleportPayload;
import top.csituka.youzaiworldcore.invisibility.InvisibilityManager;
import top.csituka.youzaiworldcore.screen.DecompositionTableMenu;
import top.csituka.youzaiworldcore.screen.FlyBeaconMenu;
import top.csituka.youzaiworldcore.skill.AttributeManager;
import top.csituka.youzaiworldcore.respawn.InPlaceRespawnManager;
import top.csituka.youzaiworldcore.cosmetic.CosmeticManager;
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
        PayloadTypeRegistry.serverboundPlay().register(InPlaceRespawnRequestPayload.ID,
                InPlaceRespawnRequestPayload.STREAM_CODEC);
        DebugLogger.info("ModNetworking", "Registered serverbound packet: InPlaceRespawnRequestPayload");

        PayloadTypeRegistry.clientboundPlay().register(OpenMenuPayload.ID, OpenMenuPayload.STREAM_CODEC);
        DebugLogger.info("ModNetworking", "Registered clientbound packet: OpenMenuPayload");
        PayloadTypeRegistry.clientboundPlay().register(ManaSyncPayload.ID, ManaSyncPayload.STREAM_CODEC);
        DebugLogger.info("ModNetworking", "Registered clientbound packet: ManaSyncPayload");
        PayloadTypeRegistry.clientboundPlay().register(OpenAuthScreenPayload.ID, OpenAuthScreenPayload.STREAM_CODEC);
        DebugLogger.info("ModNetworking", "Registered clientbound packet: OpenAuthScreenPayload");
        PayloadTypeRegistry.clientboundPlay().register(InPlaceRespawnInfoPayload.ID,
                InPlaceRespawnInfoPayload.STREAM_CODEC);
        DebugLogger.info("ModNetworking", "Registered clientbound packet: InPlaceRespawnInfoPayload");
        PayloadTypeRegistry.clientboundPlay().register(InPlaceRespawnResultPayload.ID,
                InPlaceRespawnResultPayload.STREAM_CODEC);
        DebugLogger.info("ModNetworking", "Registered clientbound packet: InPlaceRespawnResultPayload");

        PayloadTypeRegistry.clientboundPlay().register(TeleportAnchorListPayload.TYPE, TeleportAnchorListPayload.STREAM_CODEC);
        DebugLogger.info("ModNetworking", "Registered clientbound packet: TeleportAnchorListPayload");
        PayloadTypeRegistry.clientboundPlay().register(TeleportAnchorOpenNamePayload.TYPE, TeleportAnchorOpenNamePayload.STREAM_CODEC);
        DebugLogger.info("ModNetworking", "Registered clientbound packet: TeleportAnchorOpenNamePayload");
        PayloadTypeRegistry.clientboundPlay().register(TeleportStoneInterruptPayload.TYPE, TeleportStoneInterruptPayload.STREAM_CODEC);
        DebugLogger.info("ModNetworking", "Registered clientbound packet: TeleportStoneInterruptPayload");
        PayloadTypeRegistry.clientboundPlay().register(LevelExpSyncPayload.ID, LevelExpSyncPayload.STREAM_CODEC);
        DebugLogger.info("ModNetworking", "Registered clientbound packet: LevelExpSyncPayload");
        PayloadTypeRegistry.clientboundPlay().register(DamageNumberPayload.ID, DamageNumberPayload.STREAM_CODEC);
        DebugLogger.info("ModNetworking", "Registered clientbound packet: DamageNumberPayload");
        PayloadTypeRegistry.clientboundPlay().register(FunctionToggleSyncPayload.TYPE, FunctionToggleSyncPayload.STREAM_CODEC);
        DebugLogger.info("ModNetworking", "Registered clientbound packet: FunctionToggleSyncPayload");
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

        // ===== 老吴贴贴事件 S2C 数据包（trigger / stop） =====
        PayloadTypeRegistry.clientboundPlay().register(LaowuMemeTriggerPayload.ID, LaowuMemeTriggerPayload.STREAM_CODEC);
        DebugLogger.info("ModNetworking", "Registered clientbound packet: LaowuMemeTriggerPayload");
        PayloadTypeRegistry.clientboundPlay().register(LaowuMemeStopPayload.ID, LaowuMemeStopPayload.STREAM_CODEC);
        DebugLogger.info("ModNetworking", "Registered clientbound packet: LaowuMemeStopPayload");

        // ===== 宠物管理命令转发数据包 =====
        PayloadTypeRegistry.serverboundPlay().register(PetCommandPayload.ID, PetCommandPayload.STREAM_CODEC);
        DebugLogger.info("ModNetworking", "Registered serverbound packet: PetCommandPayload");

        // ===== Trinkets 饰品槽交互数据包 =====
        PayloadTypeRegistry.serverboundPlay().register(TrinketInteractPayload.ID, TrinketInteractPayload.STREAM_CODEC);
        DebugLogger.info("ModNetworking", "Registered serverbound packet: TrinketInteractPayload");

        // ===== YZUI 物品栏 Mouse Tweaks 数据包 =====
        PayloadTypeRegistry.serverboundPlay().register(InventoryCollectPayload.ID, InventoryCollectPayload.STREAM_CODEC);
        DebugLogger.info("ModNetworking", "Registered serverbound packet: InventoryCollectPayload");

        // ===== AFK 客户端心跳数据包 =====
        PayloadTypeRegistry.serverboundPlay().register(AfkHeartbeatPayload.ID, AfkHeartbeatPayload.STREAM_CODEC);
        DebugLogger.info("ModNetworking", "Registered serverbound packet: AfkHeartbeatPayload");

        // ===== 自定义皮肤与披风 =====
        PayloadTypeRegistry.serverboundPlay().register(CosmeticUploadPayload.ID, CosmeticUploadPayload.STREAM_CODEC);
        PayloadTypeRegistry.serverboundPlay().register(CosmeticRequestPayload.ID, CosmeticRequestPayload.STREAM_CODEC);
        PayloadTypeRegistry.clientboundPlay().register(CosmeticReadyPayload.ID, CosmeticReadyPayload.STREAM_CODEC);
        PayloadTypeRegistry.clientboundPlay().register(CosmeticInfoPayload.ID, CosmeticInfoPayload.STREAM_CODEC);
        PayloadTypeRegistry.clientboundPlay().register(CosmeticDataPayload.ID, CosmeticDataPayload.STREAM_CODEC);
        PayloadTypeRegistry.clientboundPlay().register(
                CosmeticUploadResultPayload.ID, CosmeticUploadResultPayload.STREAM_CODEC);
        DebugLogger.info("ModNetworking", "Registered cosmetic payloads");

        // ===== 服务端接收处理器 =====
        ServerPlayNetworking.registerGlobalReceiver(CosmeticUploadPayload.ID, (payload, context) -> {
            var player = context.player();
            var server = player.level().getServer();
            if (server != null) {
                server.execute(() -> CosmeticManager.applySnapshot(player, payload));
            }
        });
        ServerPlayNetworking.registerGlobalReceiver(CosmeticRequestPayload.ID, (payload, context) -> {
            var player = context.player();
            var server = player.level().getServer();
            if (server != null) {
                server.execute(() -> CosmeticManager.handleRequest(player, payload.targetUuid()));
            }
        });

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

        // ===== 原地重生请求处理器 =====
        ServerPlayNetworking.registerGlobalReceiver(InPlaceRespawnRequestPayload.ID, (payload, context) -> {
            DebugLogger.entering("ModNetworking", "InPlaceRespawnRequestPayload handler");
            var player = context.player();
            player.level().getServer().execute(() -> InPlaceRespawnManager.handleRequest(player));
            DebugLogger.exiting("ModNetworking", "InPlaceRespawnRequestPayload handler");
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
                // 仅传送锚点方块 / 传送石入口需要经验，传送卷轴一次性使用故免经验
                TeleportAnchorManager.TeleportOpenerSource source = manager.consumeTeleportSourceMark(serverPlayer);
                EntryType entryType = source != null ? source.type() : EntryType.ANCHOR;
                InteractionHand entryHand = source != null ? source.hand() : null;
                boolean isScrollEntry = entryType == EntryType.SCROLL;
                boolean itemEntry = source != null;

                boolean isCreative = serverPlayer.getAbilities().instabuild;
                int cost = target.dimension().equals(serverPlayer.level().dimension()) ? 1 : 2;
                if (!isScrollEntry && !isCreative && serverPlayer.experienceLevel < cost) {
                    serverPlayer.sendSystemMessage(
                            Component.translatable("message.youzaiworldcore.teleport_anchor.no_xp", cost));
                    DebugLogger.info("ModNetworking", "Player lacks XP: needs " + cost + " but has " + serverPlayer.experienceLevel);
                    return;
                }

                // ===== 传送物品入口结算（耐久 / 卷轴均在传送真正成功时扣，避免先扣后拒） =====
                // 标记无论成败都在此消费；校验放在扣经验之前，避免校验失败却已扣掉经验。
                ItemStack itemStack = null;
                InteractionHand itemHand = null;
                int durabilityCost = 0;
                if (itemEntry && !isCreative && entryHand != null) {
                    itemStack = serverPlayer.getItemInHand(entryHand);
                    var entryItem = itemStack.getItem();
                    if (entryType == EntryType.STONE && !(entryItem instanceof TeleportStoneItem)) {
                        // 兜底：玩家在开着界面时换了手，另一只手再找一次传送石
                        InteractionHand otherHand = entryHand == InteractionHand.MAIN_HAND
                                ? InteractionHand.OFF_HAND
                                : InteractionHand.MAIN_HAND;
                        ItemStack alternative = serverPlayer.getItemInHand(otherHand);
                        if (alternative.getItem() instanceof TeleportStoneItem) {
                            itemStack = alternative;
                            itemHand = otherHand;
                            entryHand = otherHand;
                        } else {
                            // 传送石已不在手上：拒绝传送，避免丢弃传送石白嫖免耐久传送
                            serverPlayer.sendSystemMessage(
                                    Component.translatable("message.youzaiworldcore.teleport_stone.not_held"));
                            DebugLogger.info("ModNetworking", "Teleport stone no longer held, teleport cancelled");
                            return;
                        }
                    } else if (entryType == EntryType.SCROLL && !(entryItem instanceof WarpScrollItem)) {
                        InteractionHand otherHand = entryHand == InteractionHand.MAIN_HAND
                                ? InteractionHand.OFF_HAND
                                : InteractionHand.MAIN_HAND;
                        ItemStack alternative = serverPlayer.getItemInHand(otherHand);
                        if (alternative.getItem() instanceof WarpScrollItem) {
                            itemStack = alternative;
                            itemHand = otherHand;
                            entryHand = otherHand;
                        } else {
                            serverPlayer.sendSystemMessage(
                                    Component.translatable("message.youzaiworldcore.warp_scroll.not_held"));
                            DebugLogger.info("ModNetworking", "Warp scroll no longer held, teleport cancelled");
                            return;
                        }
                    } else {
                        itemHand = entryHand;
                    }

                    // 传送石：按距离计算耐久消耗并校验
                    if (entryType == EntryType.STONE) {
                        durabilityCost = TeleportStoneItem.computeDurabilityCost(serverPlayer, target);
                        int remainingDurability = itemStack.getMaxDamage() - itemStack.getDamageValue();
                        if (remainingDurability < durabilityCost) {
                            serverPlayer.sendSystemMessage(
                                    Component.translatable("message.youzaiworldcore.teleport_stone.no_durability",
                                            durabilityCost, remainingDurability));
                            DebugLogger.info("ModNetworking", "Teleport stone durability insufficient: needs "
                                    + durabilityCost + " but has " + remainingDurability);
                            return;
                        }
                    } else if (entryType == EntryType.SCROLL) {
                        // 卷轴：始终消耗 1 张（叠堆减 1，耗尽则该组物品销毁），不涉及耐久
                        if (itemStack.getCount() < WarpScrollItem.SCROLL_CONSUME_AMOUNT) {
                            serverPlayer.sendSystemMessage(
                                    Component.translatable("message.youzaiworldcore.warp_scroll.not_held"));
                            DebugLogger.info("ModNetworking", "Warp scroll count insufficient, teleport cancelled");
                            return;
                        }
                    }
                }

                if (!isScrollEntry && !isCreative) {
                    serverPlayer.giveExperienceLevels(-cost);
                }

                // 记录传送锚点自身的 3 秒入门冷却
                manager.recordTeleport(serverPlayer, gameTime);

                // 传送物品结算：先上冷却（堆对象还在），再扣耐久 / 缩卷轴
                if (itemStack != null && itemHand != null) {
                    if (entryType == EntryType.STONE) {
                        serverPlayer.getCooldowns().addCooldown(itemStack, TeleportStoneItem.COOLDOWN_TICKS);
                        itemStack.hurtAndBreak(durabilityCost, serverPlayer, itemHand);
                        DebugLogger.info("ModNetworking", "Teleport stone consumed %d durability (hand=%s)",
                                durabilityCost, itemHand);
                    } else if (entryType == EntryType.SCROLL) {
                        serverPlayer.getCooldowns().addCooldown(itemStack, WarpScrollItem.COOLDOWN_TICKS);
                        itemStack.shrink(WarpScrollItem.SCROLL_CONSUME_AMOUNT);
                        DebugLogger.info("ModNetworking", "Warp scroll consumed 1 (hand=%s, remaining=%s)",
                                itemHand, itemStack.isEmpty() ? "destroyed" : itemStack.getCount());
                    }
                }

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
                // 权限检查
                if (!InvisibilityManager.hasPermission(player)) {
                    player.sendSystemMessage(Component.literal("§c你没有权限使用隐身功能"));
                    DebugLogger.exiting("ModNetworking", "InvisibilityPayload handler", "no permission");
                    return;
                }
                Boolean enabled = payload.enabled();
                if (enabled == null) {
                    // 查询当前状态
                    boolean cur = InvisibilityManager.isInvisible(player);
                    player.sendSystemMessage(Component.translatable(
                            cur
                                    ? "youzaiworldcore.message.command.function.invisibility.query_enabled"
                                    : "youzaiworldcore.message.command.function.invisibility.query_disabled"));
                    return;
                }
                // 创造模式检查（设置隐身才需要）
                if (player.gameMode() != GameType.CREATIVE) {
                    player.sendSystemMessage(Component.literal(
                            "§c只有创造模式才能使用隐身功能（请先切换到创造模式）"));
                    DebugLogger.exiting("ModNetworking", "InvisibilityPayload handler", "not creative");
                    return;
                }
                if (enabled) {
                    DebugLogger.branch("ModNetworking", "InvisibilityPayload", true, "enabling");
                    InvisibilityManager.enable(player);
                } else {
                    DebugLogger.branch("ModNetworking", "InvisibilityPayload", false, "disabling");
                    InvisibilityManager.disable(player);
                }
                player.sendSystemMessage(Component.translatable(enabled
                        ? "youzaiworldcore.message.command.function.invisibility.set_enabled"
                        : "youzaiworldcore.message.command.function.invisibility.set_disabled"));
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

        // ===== AFK 客户端心跳处理器（服务端权威：更新客户端通道活动时间） =====
        ServerPlayNetworking.registerGlobalReceiver(AfkHeartbeatPayload.ID, (payload, context) -> {
            var player = (net.minecraft.server.level.ServerPlayer) context.player();
            DebugLogger.trace("ModNetworking", "AfkHeartbeatPayload handler: player=%s, idleTicks=%d",
                    player.getName().getString(), payload.idleTicks());
            var server = player.level().getServer();
            if (server == null) {
                return;
            }
            server.execute(() -> {
                AfkManager.onHeartbeat(player, server.getTickCount(), payload.idleTicks());
            });
        });
        DebugLogger.info("ModNetworking", "Registered receiver: AfkHeartbeatPayload");

        // ===== YZUI 生存物品栏左键拖拽收集（服务端权威操作） =====
        ServerPlayNetworking.registerGlobalReceiver(InventoryCollectPayload.ID, (payload, context) -> {
            var player = context.player();
            var server = player.level().getServer();
            if (server == null) {
                return;
            }
            server.execute(() -> {
                var menu = player.containerMenu;
                boolean validMenu = menu == player.inventoryMenu
                        && menu.containerId == payload.containerId()
                        && !player.hasInfiniteMaterials();
                if (!validMenu || payload.slotIndex() < 0 || payload.slotIndex() >= menu.slots.size()) {
                    DebugLogger.info("InventoryCollect", "拒绝无效收集请求：container=%d, slot=%d",
                            payload.containerId(), payload.slotIndex());
                    menu.broadcastFullState();
                    return;
                }

                var slot = menu.getSlot(payload.slotIndex());
                ItemStack carried = menu.getCarried();
                ItemStack slotStack = slot.getItem();
                int capacity = carried.isEmpty() ? 0 : carried.getMaxStackSize() - carried.getCount();
                if (!slot.isActive() || !slot.mayPickup(player) || slotStack.isEmpty() || capacity <= 0
                        || !ItemStack.isSameItemSameComponents(carried, slotStack)) {
                    DebugLogger.info("InventoryCollect", "忽略不满足条件的收集请求：slot=%d",
                            payload.slotIndex());
                    menu.broadcastFullState();
                    return;
                }

                int requested = Math.min(capacity, slotStack.getCount());
                ItemStack taken = slot.safeTake(requested, requested, player);
                if (taken.isEmpty() || !ItemStack.isSameItemSameComponents(carried, taken)) {
                    DebugLogger.info("InventoryCollect", "槽位拒绝取出物品：slot=%d", payload.slotIndex());
                    menu.broadcastFullState();
                    return;
                }

                ItemStack updatedCarried = carried.copy();
                updatedCarried.grow(taken.getCount());
                menu.setCarried(updatedCarried);
                menu.broadcastChanges();
                DebugLogger.info("InventoryCollect", "已从 slot %d 收集 %d 个 %s",
                        payload.slotIndex(), taken.getCount(), taken.getHoverName().getString());
            });
        });
        DebugLogger.info("ModNetworking", "Registered receiver: InventoryCollectPayload");

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
                    net.minecraft.world.item.ItemStack reported = payload.cursor() != null ? payload.cursor() : net.minecraft.world.item.ItemStack.EMPTY;
                    // 生存模式不得信任客户端上报的任意 ItemStack；只有创造模式允许把本地创造栏
                    // 生成的虚拟光标物品作为兜底。标准生存物品栏点击会先同步服务端 carried。
                    boolean creativeCursor = player.hasInfiniteMaterials();
                    net.minecraft.world.item.ItemStack cursor = creativeCursor ? reported : carried;
                    net.minecraft.world.item.ItemStack slotStack = access.get();

                    DebugLogger.info("ModNetworking", "TrinketReq group=%s[%d] action=%d serverCarried=%s cursor=%s(%d) slot=%s",
                            payload.groupKey(), payload.slotIndex(), payload.action(),
                            carried.isEmpty() ? "empty" : carried.getHoverName().getString(),
                            cursor.isEmpty() ? "empty" : cursor.getHoverName().getString(), cursor.getCount(),
                            slotStack.isEmpty() ? "empty" : slotStack.getHoverName().getString());

                    switch (payload.action()) {
                        case TrinketInteractPayload.ACTION_PLACE:
                            if (cursor.isEmpty()) {
                                DebugLogger.info("ModNetworking", "PLACE skipped: no item to place (serverCarried empty, clientCursor empty)");
                                break;
                            }
                            if (!slotStack.isEmpty()) {
                                DebugLogger.info("ModNetworking", "PLACE rejected: slot %s[%d] is not empty",
                                        payload.groupKey(), payload.slotIndex());
                                break;
                            }
                            if (!access.slotType().validatorCheck(cursor, access, player)) {
                                DebugLogger.info("ModNetworking", "Validator rejected %s -> %s[%d]",
                                        cursor.getHoverName().getString(), payload.groupKey(), payload.slotIndex());
                                break;
                            }
                            int placeCount = Math.min(cursor.getCount(), access.maxStackSize(cursor));
                            if (placeCount <= 0) {
                                DebugLogger.info("ModNetworking", "PLACE rejected: slot %s[%d] has no capacity",
                                        payload.groupKey(), payload.slotIndex());
                                break;
                            }
                            net.minecraft.world.item.ItemStack placed = cursor.copyWithCount(placeCount);
                            if (!access.set(placed)) {
                                DebugLogger.info("ModNetworking", "PLACE rejected by slot access: %s[%d]",
                                        payload.groupKey(), payload.slotIndex());
                                break;
                            }
                            inv.setChanged();
                            TrinketUtilities.callTrinketEquipmentChange(slotStack, placed, access, player);
                            CriteriaTriggers.INVENTORY_CHANGED.trigger(player, player.getInventory(), placed);
                            net.minecraft.world.item.ItemStack remainder = cursor.copy();
                            remainder.shrink(placeCount);
                            player.containerMenu.setCarried(creativeCursor || remainder.isEmpty()
                                    ? net.minecraft.world.item.ItemStack.EMPTY : remainder);
                            DebugLogger.info("ModNetworking", "Trinket PLACE: %s x%d -> %s[%d], remainder=%d",
                                    placed.getHoverName().getString(), placed.getCount(), payload.groupKey(),
                                    payload.slotIndex(), remainder.getCount());
                            break;
                        case TrinketInteractPayload.ACTION_TAKE:
                            if (slotStack.isEmpty()) {
                                DebugLogger.info("ModNetworking", "TAKE skipped: slot %s[%d] is empty",
                                        payload.groupKey(), payload.slotIndex());
                                break;
                            }
                            player.containerMenu.setCarried(creativeCursor
                                    ? net.minecraft.world.item.ItemStack.EMPTY : slotStack.copy());
                            access.set(net.minecraft.world.item.ItemStack.EMPTY);
                            inv.setChanged();
                            TrinketUtilities.callTrinketEquipmentChange(slotStack, net.minecraft.world.item.ItemStack.EMPTY, access, player);
                            DebugLogger.info("ModNetworking", "Trinket TAKE: %s[%d] -> cursor", payload.groupKey(), payload.slotIndex());
                            break;
                        case TrinketInteractPayload.ACTION_SWAP:
                            if (cursor.isEmpty()) {
                                DebugLogger.info("ModNetworking", "SWAP skipped: no item to swap (serverCarried empty, clientCursor empty)");
                                break;
                            }
                            if (!access.slotType().validatorCheck(cursor, access, player)) {
                                DebugLogger.info("ModNetworking", "Validator rejected %s -> %s[%d] (SWAP)",
                                        cursor.getHoverName().getString(), payload.groupKey(), payload.slotIndex());
                                break;
                            }
                            if (cursor.getCount() > access.maxStackSize(cursor)) {
                                DebugLogger.info("ModNetworking", "SWAP rejected: %s x%d exceeds slot limit %d",
                                        cursor.getHoverName().getString(), cursor.getCount(), access.maxStackSize(cursor));
                                break;
                            }
                            if (!access.set(cursor.copy())) {
                                DebugLogger.info("ModNetworking", "SWAP rejected by slot access: %s[%d]",
                                        payload.groupKey(), payload.slotIndex());
                                break;
                            }
                            player.containerMenu.setCarried(creativeCursor
                                    ? net.minecraft.world.item.ItemStack.EMPTY : slotStack.copy());
                            inv.setChanged();
                            TrinketUtilities.callTrinketEquipmentChange(slotStack, cursor, access, player);
                            CriteriaTriggers.INVENTORY_CHANGED.trigger(player, player.getInventory(), cursor);
                            DebugLogger.info("ModNetworking", "Trinket SWAP: cursor <-> %s[%d]", payload.groupKey(), payload.slotIndex());
                            break;
                        case TrinketInteractPayload.ACTION_QUICK_MOVE:
                            // 快捷移动：饰品槽物品 → 主物品栏/快捷栏（玩家背包 0-35）。
                            // 手动转移不依赖菜单类型（CreativeModeMenu 无 Trinkets 注入槽，
                            // 不能用标准 QUICK_MOVE 点击）；物品栏由 menu 广播同步，
                            // 饰品槽由 Trinkets 的 tick 级脏检查自动 SYNC_INVENTORY 同步。
                            if (slotStack.isEmpty()) {
                                DebugLogger.info("ModNetworking", "QUICK_MOVE skipped: slot %s[%d] is empty",
                                        payload.groupKey(), payload.slotIndex());
                                break;
                            }
                            {
                                net.minecraft.world.item.ItemStack toMove = slotStack.copy();
                                net.minecraft.world.entity.player.Inventory playerInv = player.getInventory();
                                for (int i = 0; i < playerInv.getContainerSize(); i++) {
                                    net.minecraft.world.item.ItemStack existing = playerInv.getItem(i);
                                    if (existing.isEmpty()) {
                                        playerInv.setItem(i, toMove);
                                        toMove = net.minecraft.world.item.ItemStack.EMPTY;
                                        break;
                                    } else if (net.minecraft.world.item.ItemStack.isSameItemSameComponents(existing, toMove)
                                            && existing.getCount() < existing.getMaxStackSize()) {
                                        int space = existing.getMaxStackSize() - existing.getCount();
                                        int take = Math.min(space, toMove.getCount());
                                        existing.grow(take);
                                        toMove.shrink(take);
                                        if (toMove.isEmpty()) {
                                            break;
                                        }
                                    }
                                }
                                if (toMove.isEmpty()) {
                                    access.set(net.minecraft.world.item.ItemStack.EMPTY);
                                    inv.setChanged();
                                    TrinketUtilities.callTrinketEquipmentChange(slotStack, net.minecraft.world.item.ItemStack.EMPTY, access, player);
                                    DebugLogger.info("ModNetworking", "Trinket QUICK_MOVE: %s -> %s[%d] -> inventory",
                                            slotStack.getHoverName().getString(), payload.groupKey(), payload.slotIndex());
                                } else {
                                    access.set(toMove);
                                    inv.setChanged();
                                    TrinketUtilities.callTrinketEquipmentChange(slotStack, toMove, access, player);
                                    DebugLogger.info("ModNetworking", "Trinket QUICK_MOVE partial: %s -> %s[%d] (inventory full, %d left)",
                                            slotStack.getHoverName().getString(), payload.groupKey(), payload.slotIndex(), toMove.getCount());
                                }
                            }
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
        PayloadTypeRegistry.serverboundPlay().register(MailPlayerListRequestPayload.ID, MailPlayerListRequestPayload.STREAM_CODEC);
        DebugLogger.info("ModNetworking", "Registered serverbound packet: MailPlayerListRequestPayload");

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
        PayloadTypeRegistry.clientboundPlay().register(MailPlayerListPayload.ID, MailPlayerListPayload.STREAM_CODEC);
        DebugLogger.info("ModNetworking", "Registered clientbound packet: MailPlayerListPayload");

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
                syncUnread(player);
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
                            top.csituka.youzaiworldcore.mail.MailDataStorage.removeRef(online.getUUID(), payload.mailId());
                            ServerPlayNetworking.send(online, MailUpdatePayload.createRemove(payload.mailId()));
                            syncUnread(online);
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
                            // 领取即视为已读，避免领完奖励红点仍在
                            ref.setRead(true);
                            top.csituka.youzaiworldcore.mail.MailDataStorage.updateRef(uuid, ref);
                            // 回推权威条目，客户端据此把按钮刷新为「已领取」
                            var claimedMail = top.csituka.youzaiworldcore.mail.SentMailRepository.get(payload.mailId());
                            if (claimedMail != null) {
                                ServerPlayNetworking.send(player, MailUpdatePayload.createUpdate(ref, claimedMail));
                            }
                            ServerPlayNetworking.send(player, MailOpResultPayload.success(payload.mailId(), "已领取奖励"));
                        } else {
                            ServerPlayNetworking.send(player, MailOpResultPayload.failure(payload.mailId(), "领取失败"));
                            // 领取失败时把权威条目推回去，撤销客户端的乐观更新
                            var failedMail = top.csituka.youzaiworldcore.mail.SentMailRepository.get(payload.mailId());
                            if (failedMail != null) {
                                ServerPlayNetworking.send(player, MailUpdatePayload.createUpdate(ref, failedMail));
                            }
                        }
                        break;
                    case MailActionPayload.ACTION_DELETE:
                        top.csituka.youzaiworldcore.mail.MailDataStorage.removeRef(uuid, payload.mailId());
                        ServerPlayNetworking.send(player, MailUpdatePayload.createRemove(payload.mailId()));
                        break;
                }
                // 任何操作都可能改变未读数，统一回推权威值（修复红点不消失 / 空信箱仍显示数量）
                syncUnread(player);
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
                                } else {
                                    // 接收范围收窄后该玩家已不再持有引用，通知其移除
                                    ServerPlayNetworking.send(online, MailUpdatePayload.createRemove(payload.mailId()));
                                }
                                syncUnread(online);
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

        // 11. 请求已注册玩家名单（发布页「选取玩家」弹窗）
        ServerPlayNetworking.registerGlobalReceiver(MailPlayerListRequestPayload.ID, (payload, context) -> {
            DebugLogger.entering("ModNetworking", "MailPlayerListRequestPayload handler");
            var player = (net.minecraft.server.level.ServerPlayer) context.player();
            var server = player.level().getServer();
            if (server == null) return;
            server.execute(() -> {
                if (!top.csituka.youzaiworldcore.mail.MailPermissionHelper.hasMailPermission(player)) {
                    ServerPlayNetworking.send(player, MailOpResultPayload.failure(null, "没有权限"));
                    DebugLogger.exiting("ModNetworking", "MailPlayerListRequestPayload handler", "no permission");
                    return;
                }
                java.util.List<String> names = top.csituka.youzaiworldcore.account.data.AccountDataStorage.getAll()
                        .values().stream()
                        .map(account -> account.username)
                        .filter(name -> name != null && !name.isBlank())
                        .distinct()
                        .sorted(String.CASE_INSENSITIVE_ORDER)
                        .toList();
                ServerPlayNetworking.send(player, new MailPlayerListPayload(names));
                DebugLogger.info("ModNetworking", "Sent registered player list to %s: %d entries",
                        player.getScoreboardName(), names.size());
            });
            DebugLogger.exiting("ModNetworking", "MailPlayerListRequestPayload handler");
        });

        DebugLogger.exiting("ModNetworking", "initialize");
    }

    /**
     * 重算并回推该玩家的未读邮件数与发布权限。
     * <p>
     * 读 / 领取 / 删除 / 星标 / 撤回 / 编辑之后都要调用：客户端徽标与界面红点完全依赖这个权威值，
     * 少推一次就会出现「已读但红点还在」「信箱空了仍显示数量」。
     * </p>
     */
    private static void syncUnread(net.minecraft.server.level.ServerPlayer player) {
        int unread = top.csituka.youzaiworldcore.mail.MailDataStorage.getUnreadCount(player.getUUID());
        boolean canSend = top.csituka.youzaiworldcore.mail.MailPermissionHelper.hasMailPermission(player);
        ServerPlayNetworking.send(player, new MailUnreadCountPayload(unread, canSend));
        DebugLogger.trace("ModNetworking", "同步未读数: player=%s, unread=%d", player.getScoreboardName(), unread);
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
