package top.csituka.youzaiworldcore.network;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import top.csituka.youzaiworldcore.screen.DecompositionTableMenu;
import top.csituka.youzaiworldcore.screen.FlyBeaconMenu;
import top.csituka.youzaiworldcore.block.entity.FlyBeaconBlockEntity;

public class ModNetworking {
    
    public static void initialize() {
        PayloadTypeRegistry.serverboundPlay().register(DecomposeItemPayload.ID, DecomposeItemPayload.STREAM_CODEC);
        PayloadTypeRegistry.serverboundPlay().register(FlyBeaconActivePayload.ID, FlyBeaconActivePayload.STREAM_CODEC);
        
        // 注册服务端监听的数据包（来自客户端）
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

        // 注册客户端监听的数据包（来自服务端）
        PayloadTypeRegistry.clientboundPlay().register(OpenMenuPayload.ID, OpenMenuPayload.STREAM_CODEC);
    }
}
