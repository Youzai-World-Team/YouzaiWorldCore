package top.csituka.youzaiworldcore.item.tool;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;
import top.csituka.youzaiworldcore.component.ModDataComponents;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

public class FlyCoreItem extends Item {

    private static final Set<UUID> flyingPlayers = new HashSet<>();

    public FlyCoreItem(Properties properties) {
        super(properties.stacksTo(1).durability(600).rarity(Rarity.RARE));
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand usedHand) {
        ItemStack stack = player.getItemInHand(usedHand);
        
        if (level.isClientSide()) {
            return InteractionResult.PASS;
        }
        
        if (player.isCreative()) {
            return InteractionResult.FAIL;
        }
        
        UUID playerId = player.getUUID();
        
        if (flyingPlayers.contains(playerId)) {
            disableFlight(player);
            flyingPlayers.remove(playerId);
            stack.remove(ModDataComponents.FLY_CORE_ACTIVE);
            sendActionBar(player, 
                    Component.translatable("item.youzaiworldcore.fly_core.disabled")
                            .withStyle(ChatFormatting.RED)
            );
        } else {
            enableFlight(player);
            flyingPlayers.add(playerId);
            stack.set(ModDataComponents.FLY_CORE_ACTIVE, true);
            sendActionBar(player, 
                    Component.translatable("item.youzaiworldcore.fly_core.enabled")
                            .withStyle(ChatFormatting.GREEN)
            );
        }
        
        return InteractionResult.SUCCESS;
    }

    private void sendActionBar(Player player, Component message) {
        if (player instanceof ServerPlayer serverPlayer) {
            ClientboundSetActionBarTextPacket packet = new ClientboundSetActionBarTextPacket(message);
            serverPlayer.connection.send(packet);
        }
    }

    public static void enableFlight(Player player) {
        player.getAbilities().mayfly = true;
        player.getAbilities().flying = true;
        player.onUpdateAbilities();
    }

    public static void disableFlight(Player player) {
        player.getAbilities().mayfly = false;
        player.getAbilities().flying = false;
        player.onUpdateAbilities();
    }

    public static boolean isFlying(UUID playerId) {
        return flyingPlayers.contains(playerId);
    }

    public static void setFlying(UUID playerId, boolean flying) {
        if (flying) {
            flyingPlayers.add(playerId);
        } else {
            flyingPlayers.remove(playerId);
        }
    }

    public static boolean hasFlyCoreInHand(Player player) {
        ItemStack mainHand = player.getMainHandItem();
        ItemStack offHand = player.getOffhandItem();
        
        return mainHand.getItem() instanceof FlyCoreItem || offHand.getItem() instanceof FlyCoreItem;
    }

    public static ItemStack getFlyCoreInHand(Player player) {
        ItemStack mainHand = player.getMainHandItem();
        ItemStack offHand = player.getOffhandItem();
        
        if (mainHand.getItem() instanceof FlyCoreItem) {
            return mainHand;
        } else if (offHand.getItem() instanceof FlyCoreItem) {
            return offHand;
        }
        return null;
    }

    public static boolean isPlayerFlying(Player player) {
        return flyingPlayers.contains(player.getUUID());
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip, TooltipFlag flag) {
        tooltip.accept(Component.translatable("item.youzaiworldcore.fly_core.tooltip")
                .withStyle(ChatFormatting.GRAY));
        super.appendHoverText(stack, context, display, tooltip, flag);
    }
}
