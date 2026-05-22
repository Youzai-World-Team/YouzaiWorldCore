package top.csituka.youzaiworldcore.event;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.NonNull;
import top.csituka.youzaiworldcore.component.ModDataComponents;
import top.csituka.youzaiworldcore.item.ModItems;
import top.csituka.youzaiworldcore.item.tool.VoidStaffItem;

import java.util.UUID;

public class VoidStaffTickHandler implements ServerTickEvents.StartTick {

    private static final VoidStaffTickHandler INSTANCE = new VoidStaffTickHandler();
    private static int tickCounter = 0;
    private static int hungerTickCounter = 0;
    private static final int TICKS_PER_SECOND = 20;
    private static final int TICKS_PER_HUNGER = 100;

    private VoidStaffTickHandler() {
    }

    @Override
    public void onStartTick(@NonNull MinecraftServer server) {
        tickCounter++;
        hungerTickCounter++;

        if (tickCounter >= TICKS_PER_SECOND) {
            tickCounter = 0;

            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                if (player.isCreative()) {
                    continue;
                }

                UUID playerId = player.getUUID();

                if (VoidStaffItem.isFlying(playerId)) {
                    if (!VoidStaffItem.hasVoidStaffInHand(player)) {
                        VoidStaffItem.setFlying(playerId, false);
                        clearAllVoidStaffActiveState(player);
                        if (FlyBeaconTickHandler.isBeaconFlying(playerId)) {
                            VoidStaffItem.disableFlight(player);
                        }
                        sendActionBar(player,
                                Component.translatable("item.youzaiworldcore.void_staff.disabled")
                                        .withStyle(ChatFormatting.RED)
                        );
                    } else if (player.getAbilities().flying && !player.onGround()) {
                        ItemStack flyCore = VoidStaffItem.getVoidStaffInHand(player);
                        if (flyCore != null) {
                            int newDamage = flyCore.getDamageValue() + 1;
                            if (newDamage >= flyCore.getMaxDamage()) {
                                flyCore.shrink(1);
                                VoidStaffItem.setFlying(playerId, false);
                                clearAllVoidStaffActiveState(player);
                                if (FlyBeaconTickHandler.isBeaconFlying(playerId)) {
                                    VoidStaffItem.disableFlight(player);
                                }
                                sendActionBar(player,
                                        Component.translatable("item.youzaiworldcore.void_staff.disabled")
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

                if (VoidStaffItem.isFlying(playerId) && player.getAbilities().flying && !player.onGround()) {
                    float saturation = player.getFoodData().getSaturationLevel();
                    int food = player.getFoodData().getFoodLevel();

                    if (saturation > 0) {
                        player.getFoodData().setSaturation(Math.max(0, saturation - 1));
                    } else if (food > 0) {
                        player.getFoodData().setFoodLevel(food - 1);
                    } else {
                        VoidStaffItem.setFlying(playerId, false);
                        clearAllVoidStaffActiveState(player);
                        if (FlyBeaconTickHandler.isBeaconFlying(playerId)) {
                            VoidStaffItem.disableFlight(player);
                        }
                        sendActionBar(player,
                                Component.translatable("item.youzaiworldcore.void_staff.no_hunger")
                                        .withStyle(ChatFormatting.RED)
                        );
                    }
                }
            }
        }
    }

    private void clearAllVoidStaffActiveState(ServerPlayer player) {
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.getItem() == ModItems.VOID_STAFF && stack.has(ModDataComponents.VOID_STAFF_ACTIVE)) {
                stack.remove(ModDataComponents.VOID_STAFF_ACTIVE);
            }
        }
    }

    private void sendActionBar(ServerPlayer player, Component message) {
        ClientboundSetActionBarTextPacket packet = new ClientboundSetActionBarTextPacket(message);
        player.connection.send(packet);
    }

    public static void register() {
        ServerTickEvents.START_SERVER_TICK.register(INSTANCE);

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            VoidStaffItem.setFlying(handler.player.getUUID(), false);
        });

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayer player = handler.player;
            for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                ItemStack stack = player.getInventory().getItem(i);
                if (stack.getItem() == ModItems.VOID_STAFF && stack.has(ModDataComponents.VOID_STAFF_ACTIVE)) {
                    stack.remove(ModDataComponents.VOID_STAFF_ACTIVE);
                }
            }
        });
    }
}
