package top.csituka.youzaiworldcore.network;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import top.csituka.youzaiworldcore.block.TeleportAnchorBlock;
import top.csituka.youzaiworldcore.block.entity.FlyBeaconBlockEntity;
import top.csituka.youzaiworldcore.block.entity.TeleportAnchorBlockEntity;
import top.csituka.youzaiworldcore.data.TeleportAnchorManager;
import top.csituka.youzaiworldcore.dimensionalinventories.DimensionPoolManager;
import top.csituka.youzaiworldcore.dimensionalinventories.WorldPoolTeleportPayload;
import top.csituka.youzaiworldcore.screen.DecompositionTableMenu;
import top.csituka.youzaiworldcore.screen.FlyBeaconMenu;
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
        PayloadTypeRegistry.serverboundPlay().register(TeleportAnchorTeleportPayload.TYPE, TeleportAnchorTeleportPayload.STREAM_CODEC);
        DebugLogger.info("ModNetworking", "Registered serverbound packet: TeleportAnchorTeleportPayload");
        PayloadTypeRegistry.serverboundPlay().register(TeleportAnchorDeletePayload.TYPE, TeleportAnchorDeletePayload.STREAM_CODEC);
        DebugLogger.info("ModNetworking", "Registered serverbound packet: TeleportAnchorDeletePayload");
        PayloadTypeRegistry.serverboundPlay().register(TeleportAnchorRenamePayload.TYPE, TeleportAnchorRenamePayload.STREAM_CODEC);
        DebugLogger.info("ModNetworking", "Registered serverbound packet: TeleportAnchorRenamePayload");
        PayloadTypeRegistry.serverboundPlay().register(TeleportAnchorActivatePayload.TYPE, TeleportAnchorActivatePayload.STREAM_CODEC);
        DebugLogger.info("ModNetworking", "Registered serverbound packet: TeleportAnchorActivatePayload");

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
                var points = manager.getPointsForPlayer((net.minecraft.server.level.ServerPlayer) player);
                int index = payload.pointIndex();
                if (index < 0 || index >= points.size()) {
                    DebugLogger.info("ModNetworking", "Invalid teleport point index: " + index);
                    return;
                }
                var target = points.get(index);
                var targetLevel = server.getLevel(target.dimension());
                if (targetLevel == null) {
                    DebugLogger.info("ModNetworking", "Target dimension not loaded: " + target.dimension().identifier());
                    return;
                }
                net.minecraft.server.level.ServerPlayer serverPlayer = (net.minecraft.server.level.ServerPlayer) player;

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
                    manager.removePoint((net.minecraft.server.level.ServerPlayer) player, payload.pointIndex());
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
                    manager.renamePoint((net.minecraft.server.level.ServerPlayer) player, payload.pointIndex(), payload.newName());
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

                // 添加到玩家传送列表
                TeleportAnchorManager manager = TeleportAnchorManager.get(server);
                String finalName = payload.name().isEmpty()
                        ? Component.translatable("screen.youzaiworldcore.teleport_anchor_name.default").getString()
                        : payload.name();
                manager.addPointWithName(player, pos, level.dimension(), finalName);

                player.sendSystemMessage(
                        Component.translatable("message.youzaiworldcore.teleport_anchor.activated")
                );
            });
        });

        DebugLogger.exiting("ModNetworking", "initialize");
    }
}
