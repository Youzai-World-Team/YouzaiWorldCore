package top.csituka.youzaiworldcore.network;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import top.csituka.youzaiworldcore.account.AccountManager;
import top.csituka.youzaiworldcore.account.LoginState;
import top.csituka.youzaiworldcore.block.entity.FlyBeaconBlockEntity;
import top.csituka.youzaiworldcore.event.AccountEventHandler;
import top.csituka.youzaiworldcore.network.account.LoginPayload;
import top.csituka.youzaiworldcore.network.account.LoginResultPayload;
import top.csituka.youzaiworldcore.network.account.OpenLoginScreenPayload;
import top.csituka.youzaiworldcore.network.account.RegisterPayload;
import top.csituka.youzaiworldcore.network.account.RegisterResultPayload;
import top.csituka.youzaiworldcore.screen.DecompositionTableMenu;
import top.csituka.youzaiworldcore.screen.FlyBeaconMenu;

public class ModNetworking {
    
    public static void initialize() {
        // ===== 注册数据包类型 =====
        PayloadTypeRegistry.serverboundPlay().register(DecomposeItemPayload.ID, DecomposeItemPayload.STREAM_CODEC);
        PayloadTypeRegistry.serverboundPlay().register(FlyBeaconActivePayload.ID, FlyBeaconActivePayload.STREAM_CODEC);
        PayloadTypeRegistry.serverboundPlay().register(LoginPayload.TYPE, LoginPayload.STREAM_CODEC);
        PayloadTypeRegistry.serverboundPlay().register(RegisterPayload.TYPE, RegisterPayload.STREAM_CODEC);

        PayloadTypeRegistry.clientboundPlay().register(OpenMenuPayload.ID, OpenMenuPayload.STREAM_CODEC);
        PayloadTypeRegistry.clientboundPlay().register(LoginResultPayload.TYPE, LoginResultPayload.STREAM_CODEC);
        PayloadTypeRegistry.clientboundPlay().register(RegisterResultPayload.TYPE, RegisterResultPayload.STREAM_CODEC);
        PayloadTypeRegistry.clientboundPlay().register(OpenLoginScreenPayload.TYPE, OpenLoginScreenPayload.STREAM_CODEC);

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

        ServerPlayNetworking.registerGlobalReceiver(LoginPayload.TYPE, (payload, context) -> {
            context.player().level().getServer().execute(() -> {
                AccountEventHandler.handleLogin(context.player(), payload.password());
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(RegisterPayload.TYPE, (payload, context) -> {
            context.player().level().getServer().execute(() -> {
                AccountEventHandler.handleRegister(context.player(), payload.password(), payload.confirmPassword());
            });
        });
    }
}
