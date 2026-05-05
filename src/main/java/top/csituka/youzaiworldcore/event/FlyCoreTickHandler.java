package top.csituka.youzaiworldcore.event;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import top.csituka.youzaiworldcore.component.ModDataComponents;
import top.csituka.youzaiworldcore.item.ModItems;
import top.csituka.youzaiworldcore.item.tool.FlyCoreItem;

import java.util.UUID;

public class FlyCoreTickHandler implements ServerTickEvents.StartTick {

    private static int tickCounter = 0;
    private static int hungerTickCounter = 0;
    private static final int TICKS_PER_SECOND = 20;
    private static final int TICKS_PER_HUNGER = 100;

    @Override
    public void onStartTick(MinecraftServer server) {
        tickCounter++;
        hungerTickCounter++;
        
        if (tickCounter >= TICKS_PER_SECOND) {
            tickCounter = 0;
            
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                if (player.isCreative()) {
                    continue;
                }
                
                UUID playerId = player.getUUID();
                
                if (FlyCoreItem.isFlying(playerId)) {
                    if (!FlyCoreItem.hasFlyCoreInHand(player)) {
                        FlyCoreItem.disableFlight(player);
                        FlyCoreItem.setFlying(playerId, false);
                        clearAllFlyCoreActiveState(player);
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
                                clearAllFlyCoreActiveState(player);
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
        
        if (hungerTickCounter >= TICKS_PER_HUNGER) {
            hungerTickCounter = 0;
            
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                if (player.isCreative()) {
                    continue;
                }
                
                UUID playerId = player.getUUID();
                
                if (FlyCoreItem.isFlying(playerId) && player.getAbilities().flying) {
                    float saturation = player.getFoodData().getSaturationLevel();
                    int food = player.getFoodData().getFoodLevel();
                    
                    if (saturation > 0) {
                        player.getFoodData().setSaturation(Math.max(0, saturation - 1));
                    } else if (food > 0) {
                        player.getFoodData().setFoodLevel(food - 1);
                    } else {
                        FlyCoreItem.disableFlight(player);
                        FlyCoreItem.setFlying(playerId, false);
                        clearAllFlyCoreActiveState(player);
                        sendActionBar(player,
                                Component.translatable("item.youzaiworldcore.fly_core.no_hunger")
                                        .withStyle(ChatFormatting.RED)
                        );
                    }
                }
            }
        }
    }

    private void clearAllFlyCoreActiveState(ServerPlayer player) {
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.getItem() == ModItems.FLY_CORE && stack.has(ModDataComponents.FLY_CORE_ACTIVE)) {
                stack.remove(ModDataComponents.FLY_CORE_ACTIVE);
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
