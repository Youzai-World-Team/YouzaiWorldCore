package top.csituka.youzaiworldcore.network;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import top.csituka.youzaiworldcore.screen.DecompositionTableMenu;

public class ModNetworking {
    
    public static void initialize() {
        PayloadTypeRegistry.serverboundPlay().register(DecomposeItemPayload.ID, DecomposeItemPayload.STREAM_CODEC);
        
        ServerPlayNetworking.registerGlobalReceiver(DecomposeItemPayload.ID, (payload, context) -> {
            if (context.player().containerMenu instanceof DecompositionTableMenu menu) {
                menu.performDecomposition();
            }
        });
    }
}
