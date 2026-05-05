package top.csituka.youzaiworldcore.network;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

public class ClientNetworking {
    
    public static void sendDecomposePacket() {
        ClientPlayNetworking.send(new DecomposeItemPayload());
    }
}
