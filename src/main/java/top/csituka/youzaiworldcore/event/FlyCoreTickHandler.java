package top.csituka.youzaiworldcore.event;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import top.csituka.youzaiworldcore.item.FlyCoreItem;

import java.util.UUID;

public class FlyCoreTickHandler implements ServerTickEvents.StartTick {

    private static int tickCounter = 0;
    private static final int TICKS_PER_SECOND = 20;

    @Override
    public void onStartTick(MinecraftServer server) {
        tickCounter++;
        
        if (tickCounter >= TICKS_PER_SECOND) {
            tickCounter = 0;
            
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                UUID playerId = player.getUUID();
                
                if (FlyCoreItem.isFlying(playerId)) {
                    if (!FlyCoreItem.hasFlyCoreInHand(player)) {
                        FlyCoreItem.disableFlight(player);
                        FlyCoreItem.setFlying(playerId, false);
                        sendActionBar(player,
                                Component.translatable("item.youzaiworldcore.fly_core.disabled")
                                        .withStyle(ChatFormatting.RED)
                        );
                    } else {
                        ItemStack flyCore = FlyCoreItem.getFlyCoreInHand(player);
                        if (flyCore != null) {
                            int newDamage = flyCore.getDamageValue() + 1;
                            if (newDamage >= flyCore.getMaxDamage()) {
                                flyCore.shrink(1);
                                FlyCoreItem.disableFlight(player);
                                FlyCoreItem.setFlying(playerId, false);
                                sendActionBar(player,
                                        Component.translatable("item.youzaiworldcore.fly_core.disabled")
                                                .withStyle(ChatFormatting.RED)
                                );
                            } else {
                                flyCore.setDamageValue(newDamage);
                            }
                        }
                    }
                }
            }
        }
    }

    private void sendActionBar(ServerPlayer player, Component message) {
        ClientboundSetActionBarTextPacket packet = new ClientboundSetActionBarTextPacket(message);
        player.connection.send(packet);
    }

    public static void register() {
        ServerTickEvents.START_SERVER_TICK.register(new FlyCoreTickHandler());
    }
}
