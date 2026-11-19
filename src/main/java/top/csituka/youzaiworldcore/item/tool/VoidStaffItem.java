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
import org.jspecify.annotations.NonNull;
import top.csituka.youzaiworldcore.component.ModDataComponents;
import top.csituka.youzaiworldcore.util.DebugLogger;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

@SuppressWarnings("null")
public class VoidStaffItem extends Item {

    private static final Set<UUID> flyingPlayers = new HashSet<>();

    public VoidStaffItem(Properties properties) {
        super(properties.stacksTo(1).durability(600).rarity(Rarity.RARE));
        DebugLogger.SERVER.entering("VoidStaffItem", "constructor");
        DebugLogger.SERVER.exiting("VoidStaffItem", "constructor");
    }

    @Override
    @NonNull
    public InteractionResult use(Level level, Player player, @NonNull InteractionHand usedHand) {
        ItemStack stack = player.getItemInHand(usedHand);
        DebugLogger.SERVER.entering("VoidStaffItem", "use",
                "player=" + player.getName().getString() + ", hand=" + usedHand);

        if (level.isClientSide()) {
            DebugLogger.SERVER.branch("VoidStaffItem", "client side", true);
            DebugLogger.SERVER.exiting("VoidStaffItem", "use", "PASS (client)");
            return InteractionResult.PASS;
        }
        DebugLogger.SERVER.branch("VoidStaffItem", "server side", false);

        if (player.isCreative()) {
            DebugLogger.SERVER.branch("VoidStaffItem", "player is creative", true);
            DebugLogger.SERVER.exiting("VoidStaffItem", "use", "FAIL (creative)");
            return InteractionResult.FAIL;
        }
        DebugLogger.SERVER.branch("VoidStaffItem", "player is not creative", false);

        UUID playerId = player.getUUID();

        if (flyingPlayers.contains(playerId)) {
            DebugLogger.SERVER.branch("VoidStaffItem", "disabling flight", true);
            disableFlight(player);
            flyingPlayers.remove(playerId);
            stack.remove(ModDataComponents.VOID_STAFF_ACTIVE);
            sendActionBar(player,
                    Component.translatable("item.youzaiworldcore.void_staff.disabled")
                            .withStyle(ChatFormatting.RED)
            );
        } else {
            DebugLogger.SERVER.branch("VoidStaffItem", "enabling flight", false);
            enableFlight(player);
            flyingPlayers.add(playerId);
            stack.set(ModDataComponents.VOID_STAFF_ACTIVE, true);
            sendActionBar(player,
                    Component.translatable("item.youzaiworldcore.void_staff.enabled")
                            .withStyle(ChatFormatting.GREEN)
            );
        }

        DebugLogger.SERVER.exiting("VoidStaffItem", "use", "SUCCESS");
        return InteractionResult.SUCCESS;
    }

    private void sendActionBar(Player player, Component message) {
        DebugLogger.SERVER.entering("VoidStaffItem", "sendActionBar",
                "player=" + player.getName().getString());
        if (player instanceof ServerPlayer serverPlayer) {
            ClientboundSetActionBarTextPacket packet = new ClientboundSetActionBarTextPacket(message);
            serverPlayer.connection.send(packet);
        }
        DebugLogger.SERVER.exiting("VoidStaffItem", "sendActionBar");
    }

    public static void enableFlight(Player player) {
        DebugLogger.SERVER.entering("VoidStaffItem", "enableFlight",
                "player=" + player.getName().getString());
        player.getAbilities().mayfly = true;
        player.getAbilities().flying = true;
        player.onUpdateAbilities();
        DebugLogger.SERVER.exiting("VoidStaffItem", "enableFlight");
    }

    public static void disableFlight(Player player) {
        DebugLogger.SERVER.entering("VoidStaffItem", "disableFlight",
                "player=" + player.getName().getString());
        player.getAbilities().mayfly = false;
        player.getAbilities().flying = false;
        player.onUpdateAbilities();
        DebugLogger.SERVER.exiting("VoidStaffItem", "disableFlight");
    }

    public static boolean isFlying(UUID playerId) {
        DebugLogger.SERVER.entering("VoidStaffItem", "isFlying", "playerId=" + playerId);
        boolean result = flyingPlayers.contains(playerId);
        DebugLogger.SERVER.exiting("VoidStaffItem", "isFlying", String.valueOf(result));
        return result;
    }

    public static void setFlying(UUID playerId, boolean flying) {
        DebugLogger.SERVER.entering("VoidStaffItem", "setFlying",
                "playerId=" + playerId + ", flying=" + flying);
        if (flying) {
            flyingPlayers.add(playerId);
        } else {
            flyingPlayers.remove(playerId);
        }
        DebugLogger.SERVER.exiting("VoidStaffItem", "setFlying");
    }

    public static boolean hasVoidStaffInHand(Player player) {
        DebugLogger.SERVER.entering("VoidStaffItem", "hasVoidStaffInHand",
                "player=" + player.getName().getString());
        ItemStack mainHand = player.getMainHandItem();
        ItemStack offHand = player.getOffhandItem();

        boolean result = mainHand.getItem() instanceof VoidStaffItem || offHand.getItem() instanceof VoidStaffItem;
        DebugLogger.SERVER.exiting("VoidStaffItem", "hasVoidStaffInHand", String.valueOf(result));
        return result;
    }

    public static ItemStack getVoidStaffInHand(Player player) {
        DebugLogger.SERVER.entering("VoidStaffItem", "getVoidStaffInHand",
                "player=" + player.getName().getString());
        ItemStack mainHand = player.getMainHandItem();
        ItemStack offHand = player.getOffhandItem();

        if (mainHand.getItem() instanceof VoidStaffItem) {
            DebugLogger.SERVER.branch("VoidStaffItem", "found in main hand", true);
            DebugLogger.SERVER.exiting("VoidStaffItem", "getVoidStaffInHand", "main hand");
            return mainHand;
        } else if (offHand.getItem() instanceof VoidStaffItem) {
            DebugLogger.SERVER.branch("VoidStaffItem", "found in off hand", false);
            DebugLogger.SERVER.exiting("VoidStaffItem", "getVoidStaffInHand", "off hand");
            return offHand;
        }
        DebugLogger.SERVER.exiting("VoidStaffItem", "getVoidStaffInHand", "null (not found)");
        return null;
    }

    public static boolean isPlayerFlying(Player player) {
        DebugLogger.SERVER.entering("VoidStaffItem", "isPlayerFlying",
                "player=" + player.getName().getString());
        boolean result = flyingPlayers.contains(player.getUUID());
        DebugLogger.SERVER.exiting("VoidStaffItem", "isPlayerFlying", String.valueOf(result));
        return result;
    }

    @SuppressWarnings("deprecation")
    @Override
    public void appendHoverText(@NonNull ItemStack stack, @NonNull TooltipContext context, @NonNull TooltipDisplay display, Consumer<Component> tooltip, @NonNull TooltipFlag flag) {
        tooltip.accept(Component.translatable("item.youzaiworldcore.void_staff.tooltip")
                .withStyle(ChatFormatting.GRAY));
        super.appendHoverText(stack, context, display, tooltip, flag);
    }
}
