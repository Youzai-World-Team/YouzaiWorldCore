package top.csituka.youzaiworldcore.network;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import top.csituka.youzaiworldcore.block.entity.FlyBeaconBlockEntity;
import top.csituka.youzaiworldcore.dimensionalinventories.DimensionPoolManager;
import top.csituka.youzaiworldcore.dimensionalinventories.WorldPoolTeleportPayload;
import top.csituka.youzaiworldcore.screen.DecompositionTableMenu;
import top.csituka.youzaiworldcore.screen.FlyBeaconMenu;
import top.csituka.youzaiworldcore.util.DebugLogger;

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
        PayloadTypeRegistry.clientboundPlay().register(FeatureSyncPayload.ID, FeatureSyncPayload.STREAM_CODEC);
        DebugLogger.info("ModNetworking", "Registered clientbound packet: FeatureSyncPayload");
        PayloadTypeRegistry.clientboundPlay().register(OpenAuthScreenPayload.ID, OpenAuthScreenPayload.STREAM_CODEC);
        DebugLogger.info("ModNetworking", "Registered clientbound packet: OpenAuthScreenPayload");

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

        DebugLogger.exiting("ModNetworking", "initialize");
    }
}
