package top.csituka.youzaiworldcore.network;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import top.csituka.youzaiworldcore.block.entity.FlyBeaconBlockEntity;
import top.csituka.youzaiworldcore.screen.DecompositionTableMenu;
import top.csituka.youzaiworldcore.screen.FlyBeaconMenu;

public class ModNetworking {
    
    public static void initialize() {
        // ===== 注册数据包类型 =====
        PayloadTypeRegistry.serverboundPlay().register(DecomposeItemPayload.ID, DecomposeItemPayload.STREAM_CODEC);
        PayloadTypeRegistry.serverboundPlay().register(FlyBeaconActivePayload.ID, FlyBeaconActivePayload.STREAM_CODEC);

        PayloadTypeRegistry.clientboundPlay().register(OpenMenuPayload.ID, OpenMenuPayload.STREAM_CODEC);
        PayloadTypeRegistry.clientboundPlay().register(FeatureSyncPayload.ID, FeatureSyncPayload.STREAM_CODEC);

        // ===== 服务端接收处理器 =====
        ServerPlayNetworking.registerGlobalReceiver(DecomposeItemPayload.ID, (payload, context) -> {
            if (context.player().containerMenu instanceof DecompositionTableMenu menu) {
                menu.performDecomposition();
            }
        });
        
        ServerPlayNetworking.registerGlobalReceiver(FlyBeaconActivePayload.ID, (payload, context) -> {
            if (context.player().containerMenu instanceof FlyBeaconMenu menu) {
                if (menu.getContainer() instanceof FlyBeaconBlockEntity blockEntity) {
                    blockEntity.setActive(payload.active());
                }
            }
        });
    }
}
