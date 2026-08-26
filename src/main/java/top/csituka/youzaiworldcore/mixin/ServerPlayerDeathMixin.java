package top.csituka.youzaiworldcore.mixin;

import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.csituka.youzaiworldcore.item.ModItems;
import top.csituka.youzaiworldcore.respawn.DeferredDeathDropAccess;
import top.csituka.youzaiworldcore.respawn.InPlaceRespawnConfig;
import top.csituka.youzaiworldcore.respawn.InPlaceRespawnManager;
import top.csituka.youzaiworldcore.respawn.InPlaceRespawnPlayerAccess;
import top.csituka.youzaiworldcore.skill.AdventureLevelManager;
import top.csituka.youzaiworldcore.util.DebugLogger;
import top.csituka.youzaiworldcore.util.TrinketHelper;

import java.util.OptionalInt;

/**
 * 混合注入 {@link ServerPlayer} 的死亡与状态恢复流程。
 * <p>
 * 负责守护之心消费、启用维度中的死亡掉落暂存，以及普通/原地重生分流。
 */
@Mixin(ServerPlayer.class)
public class ServerPlayerDeathMixin implements InPlaceRespawnPlayerAccess {

    @Shadow
    private MinecraftServer server;

    @Shadow
    private void transferInventoryXpAndScore(Player player) {
    }

    @Unique
    private boolean youzaiworldcore$hadHeartBeforeDeath;
    @Unique
    private boolean youzaiworldcore$inPlaceRespawnEnabled;
    @Unique
    private boolean youzaiworldcore$inPlaceRespawnSelected;
    @Unique
    private int youzaiworldcore$inPlaceRespawnCost;
    @Unique
    private int youzaiworldcore$deferredExperienceReward;
    @Unique
    private ResourceKey<Level> youzaiworldcore$deathDimension = Level.OVERWORLD;
    @Unique
    private Vec3 youzaiworldcore$deathPosition = Vec3.ZERO;
    @Unique
    private float youzaiworldcore$deathYaw;
    @Unique
    private float youzaiworldcore$deathPitch;

    @Inject(method = "die", at = @At("HEAD"))
    private void youzaiworldcore$onDie(DamageSource damageSource, CallbackInfo ci) {
        ServerPlayer player = (ServerPlayer) (Object) this;
        youzaiworldcore$inPlaceRespawnSelected = false;
        youzaiworldcore$deathDimension = player.level().dimension();
        youzaiworldcore$deathPosition = player.position();
        youzaiworldcore$deathYaw = player.getYRot();
        youzaiworldcore$deathPitch = player.getXRot();

        boolean eligibleDeath = !player.isSpectator() && !server.isHardcore();
        youzaiworldcore$inPlaceRespawnEnabled = eligibleDeath && InPlaceRespawnConfig.isEnabled(player);
        OptionalInt requiredLevel = youzaiworldcore$inPlaceRespawnEnabled
                ? InPlaceRespawnManager.getRequiredLevel(player)
                : OptionalInt.empty();
        if (requiredLevel.isEmpty()) {
            youzaiworldcore$inPlaceRespawnEnabled = false;
            youzaiworldcore$inPlaceRespawnCost = 0;
        } else {
            youzaiworldcore$inPlaceRespawnCost = requiredLevel.getAsInt();
        }

        boolean keepInventory = server.getGameRules().get(GameRules.KEEP_INVENTORY);
        youzaiworldcore$deferredExperienceReward = !keepInventory && !player.isSpectator()
                ? Math.min(player.experienceLevel * 7, 100)
                : 0;
        youzaiworldcore$hadHeartBeforeDeath = !keepInventory
                && !player.isSpectator() && hasHeartInInventory(player);

        InPlaceRespawnManager.syncDeathInfo(player,
                youzaiworldcore$inPlaceRespawnEnabled, youzaiworldcore$inPlaceRespawnCost);
        DebugLogger.info("InPlaceRespawn", "玩家 %s 死亡：enabled=%s, cost=%d, heart=%s, dimension=%s",
                player.getName().getString(), youzaiworldcore$inPlaceRespawnEnabled,
                youzaiworldcore$inPlaceRespawnCost, youzaiworldcore$hadHeartBeforeDeath,
                youzaiworldcore$deathDimension.identifier());
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
                                    "youzaiworldcore.heart_of_guardianship.warning." + count)));
        }
    }

    @Inject(method = "die", at = @At("TAIL"))
    private void youzaiworldcore$afterDie(DamageSource damageSource, CallbackInfo ci) {
        if (youzaiworldcore$inPlaceRespawnEnabled) {
            return;
        }

        ServerPlayer player = (ServerPlayer) (Object) this;
        if (youzaiworldcore$hadHeartBeforeDeath) {
            consumeOneHeart(player);
            handleHeartUsed(player, countHearts(player));
            grantAdventureExperience(player, AdventureLevelManager.EXP_HEART_OF_GUARDIANSHIP);
        } else {
            grantAdventureExperience(player, AdventureLevelManager.EXP_DEATH);
        }
    }

    @SuppressWarnings("null")
    @Inject(method = "restoreFrom", at = @At("RETURN"))
    private void youzaiworldcore$restoreInventory(ServerPlayer oldPlayer, boolean alive, CallbackInfo ci) {
        if (alive) {
            return;
        }

        ServerPlayer newPlayer = (ServerPlayer) (Object) this;
        ServerPlayerDeathMixin oldMixin = (ServerPlayerDeathMixin) (Object) oldPlayer;
        if (!oldMixin.youzaiworldcore$inPlaceRespawnEnabled) {
            if (!server.getGameRules().get(GameRules.KEEP_INVENTORY)
                    && oldMixin.youzaiworldcore$hadHeartBeforeDeath) {
                this.transferInventoryXpAndScore(oldPlayer);
            }
            return;
        }

        if (oldMixin.youzaiworldcore$inPlaceRespawnSelected) {
            if (!server.getGameRules().get(GameRules.KEEP_INVENTORY)) {
                this.transferInventoryXpAndScore(oldPlayer);
            }
            return;
        }

        if (server.getGameRules().get(GameRules.KEEP_INVENTORY)) {
            grantAdventureExperience(newPlayer, AdventureLevelManager.EXP_DEATH);
            return;
        }

        if (oldMixin.youzaiworldcore$hadHeartBeforeDeath) {
            consumeOneHeart(oldPlayer);
            int remaining = countHearts(oldPlayer);
            this.transferInventoryXpAndScore(oldPlayer);
            // Trinkets 的 COPY_FROM 事件与本 Mixin 同处复活流程，执行顺序可能因模组而异。
            // 若饰品状态已先复制到新玩家，则在新玩家侧补做一次消费；否则旧玩家的已消费状态会被复制。
            if (countHearts(newPlayer) > remaining) {
                consumeOneHeart(newPlayer);
            }
            handleHeartUsed(newPlayer, remaining);
            grantAdventureExperience(newPlayer, AdventureLevelManager.EXP_HEART_OF_GUARDIANSHIP);
            return;
        }

        ((DeferredDeathDropAccess) oldPlayer).youzaiworldcore$dropDeferredEquipment(oldPlayer.level());
        if (oldMixin.youzaiworldcore$deferredExperienceReward > 0) {
            ExperienceOrb.award(oldPlayer.level(), oldMixin.youzaiworldcore$deathPosition,
                    oldMixin.youzaiworldcore$deferredExperienceReward);
        }
        grantAdventureExperience(newPlayer, AdventureLevelManager.EXP_DEATH);
    }

    @Unique
    private void handleHeartUsed(ServerPlayer player, int remaining) {
        player.sendSystemMessage(
                Component.translatable("youzaiworldcore.tellraw.format")
                        .append(Component.translatable("item.youzaiworldcore.heart_of_guardianship.consumed")));

        AdvancementHolder advancement = server.getAdvancements().get(
                Identifier.fromNamespaceAndPath("youzaiworldcore", "youzaiworld/used_heart_of_guardianship"));
        if (advancement != null) {
            player.getAdvancements().award(advancement, "manual_grant");
        }
        warnIfThreshold(player, remaining);
    }

    @Unique
    private static void grantAdventureExperience(ServerPlayer player, int amount) {
        try {
            AdventureLevelManager.grantExpSilent(
                    player.getUUID(), player.getName().getString(), amount);
        } catch (Exception e) {
            DebugLogger.exception("InPlaceRespawn", "grantAdventureExperience", e);
        }
    }

    @Override
    public boolean youzaiworldcore$isInPlaceRespawnEnabled() {
        return youzaiworldcore$inPlaceRespawnEnabled;
    }

    @Override
    public boolean youzaiworldcore$isInPlaceRespawnSelected() {
        return youzaiworldcore$inPlaceRespawnSelected;
    }

    @Override
    public void youzaiworldcore$selectInPlaceRespawn(int requiredLevel) {
        youzaiworldcore$inPlaceRespawnSelected = true;
        youzaiworldcore$inPlaceRespawnCost = requiredLevel;
    }

    @Override
    public int youzaiworldcore$getInPlaceRespawnCost() {
        return youzaiworldcore$inPlaceRespawnCost;
    }

    @Override
    public int youzaiworldcore$getDeferredExperienceReward() {
        return youzaiworldcore$deferredExperienceReward;
    }

    @Override
    public ResourceKey<Level> youzaiworldcore$getDeathDimension() {
        return youzaiworldcore$deathDimension;
    }

    @Override
    public Vec3 youzaiworldcore$getDeathPosition() {
        return youzaiworldcore$deathPosition;
    }

    @Override
    public float youzaiworldcore$getDeathYaw() {
        return youzaiworldcore$deathYaw;
    }

    @Override
    public float youzaiworldcore$getDeathPitch() {
        return youzaiworldcore$deathPitch;
    }
}
