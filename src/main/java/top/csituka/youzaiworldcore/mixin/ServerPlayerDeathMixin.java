package top.csituka.youzaiworldcore.mixin;

import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.gamerules.GameRules;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.csituka.youzaiworldcore.item.ModItems;
import top.csituka.youzaiworldcore.skill.AdventureLevelManager;
import top.csituka.youzaiworldcore.util.TrinketHelper;

/**
 * 混合注入 {@link ServerPlayer#die(DamageSource)}，
 * 在玩家死亡时处理「守护之心」的消耗、消息提示、成就授予与数量警告。
 */
@Mixin(ServerPlayer.class)
public class ServerPlayerDeathMixin {

    @Shadow
    private MinecraftServer server;

    @Shadow
    private void transferInventoryXpAndScore(Player player) {
    }

    @Unique
    private boolean yzwc$hadHeartBeforeDeath = false;

    @Inject(method = "die", at = @At("HEAD"))
    private void youzaiworldcore$onDie(DamageSource damageSource, CallbackInfo ci) {
        ServerPlayer player = (ServerPlayer) (Object) this;
        if (player.isSpectator()) {
            yzwc$hadHeartBeforeDeath = false;
            return;
        }
        if (server.getGameRules().get(GameRules.KEEP_INVENTORY)) {
            yzwc$hadHeartBeforeDeath = false;
            return;
        }
        yzwc$hadHeartBeforeDeath = hasHeartInInventory(player);
    }

    @Unique
    private static void consumeOneHeart(ServerPlayer player) {
        // 优先从饰品槽消耗
        if (TrinketHelper.isLoaded() && TrinketHelper.consumeOne(player, ModItems.HEART_OF_GUARDIANSHIP)) {
            return;
        }
        // 回退到背包
        Inventory inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack.getItem() == ModItems.HEART_OF_GUARDIANSHIP) {
                stack.shrink(1);
                break;
            }
        }
    }

    @Unique
    private static int countHearts(ServerPlayer player) {
        int count = 0;
        // 统计饰品槽中的
        if (TrinketHelper.isLoaded()) {
            count += TrinketHelper.countItem(player, ModItems.HEART_OF_GUARDIANSHIP);
        }
        // 统计背包中的
        Inventory inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack.getItem() == ModItems.HEART_OF_GUARDIANSHIP) {
                count += stack.getCount();
            }
        }
        return count;
    }

    @Unique
    private static boolean hasHeartInInventory(ServerPlayer player) {
        // 检查饰品槽
        if (TrinketHelper.isLoaded() && TrinketHelper.isItemEquipped(player, ModItems.HEART_OF_GUARDIANSHIP)) {
            return true;
        }
        // 检查背包
        Inventory inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack.getItem() == ModItems.HEART_OF_GUARDIANSHIP) {
                return true;
            }
        }
        return false;
    }

    @Unique
    private static void warnIfThreshold(ServerPlayer player, int count) {
        if (count == 10 || count == 5 || count == 3 || count == 2 || count == 1) {
            player.sendSystemMessage(
                    Component.translatable("youzaiworldcore.tellraw.format")
                            .append(Component.translatable(
                                    "youzaiworldcore.heart_of_guardianship.warning." + count
                            ))
            );
        }
    }

    @Inject(method = "die", at = @At("TAIL"))
    private void youzaiworldcore$afterDie(DamageSource damageSource, CallbackInfo ci) {
        ServerPlayer player = (ServerPlayer) (Object) this;
        if (yzwc$hadHeartBeforeDeath) {
            consumeOneHeart(player);

            player.sendSystemMessage(
                    Component.translatable("youzaiworldcore.tellraw.format")
                            .append(Component.translatable("item.youzaiworldcore.heart_of_guardianship.consumed"))
            );

            AdvancementHolder advancement = server.getAdvancements().get(
                    Identifier.fromNamespaceAndPath("youzaiworldcore", "youzaiworld/used_heart_of_guardianship")
            );
            if (advancement != null) {
                player.getAdvancements().award(advancement, "manual_grant");
            }

            int remaining = countHearts(player);
            warnIfThreshold(player, remaining);

            try {
                AdventureLevelManager.grantExpSilent(
                        player.getUUID(), player.getName().getString(),
                        AdventureLevelManager.EXP_HEART_OF_GUARDIANSHIP);
            } catch (Exception ignored) {}
        } else {
            try {
                AdventureLevelManager.grantExpSilent(
                        player.getUUID(), player.getName().getString(),
                        AdventureLevelManager.EXP_DEATH);
            } catch (Exception ignored) {}
        }
    }

    @Inject(method = "restoreFrom", at = @At("RETURN"))
    private void youzaiworldcore$restoreInventory(ServerPlayer oldPlayer, boolean alive, CallbackInfo ci) {
        if (alive) return;
        if (server.getGameRules().get(GameRules.KEEP_INVENTORY)) return;
        ServerPlayerDeathMixin oldMixin = (ServerPlayerDeathMixin) (Object) oldPlayer;
        if (oldMixin.yzwc$hadHeartBeforeDeath) {
            this.transferInventoryXpAndScore(oldPlayer);
        }
    }
}
