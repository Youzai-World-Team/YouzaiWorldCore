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

/**
 * 混合注入 {@link ServerPlayer#die(DamageSource)}，
 * 在玩家死亡时处理「守护之心」的消耗、消息提示、成就授予、数量警告与冒险经验发放。
 *
 * <p>规则：</p>
 * <ul>
 *   <li>当 {@code keepInventory=true} 时，原版已保留物品栏，守护之心<b>不消耗</b>、始终保留物品栏</li>
 *   <li>当 {@code keepInventory=false} 时：
 *     <ul>
 *       <li>玩家背包有守护之心 → 消耗 1 个、发送消息、授予成就，物品栏由 {@link PlayerDropEquipmentMixin} 保留</li>
 *       <li>玩家背包无守护之心 → 正常掉落物品</li>
 *     </ul>
 *   </li>
 * </ul>
 *
 * <p>冒险经验：</p>
 * <ul>
 *   <li>普通死亡 → +10 冒险经验</li>
 *   <li>守护之心保护 → +50 冒险经验（替代普通死亡经验）</li>
 * </ul>
 */
@Mixin(ServerPlayer.class)
public class ServerPlayerDeathMixin {

    @Shadow
    private MinecraftServer server;

    @Shadow
    private void transferInventoryXpAndScore(Player player) {
    }

    /**
     * 标记玩家本次死亡前物品栏中是否拥有守护之心（消耗前快照）。
     * HEAD 注入只记录此标记，TAIL 注入才真正消耗，
     * 以确保 {@link PlayerDropEquipmentMixin} 在 {@code dropEquipment()} 检测时守护之心仍在背包中。
     */
    @Unique
    private boolean yzwc$hadHeartBeforeDeath = false;

    /**
     * 标记本次死亡是否应发放普通死亡经验（+10）。
     * 当玩家没有守护之心保护时为 true。
     */
    @Unique
    private boolean yzwc$grantDeathExp = false;

    /**
     * 在 {@link ServerPlayer#die(DamageSource)} 执行之初注入，
     * 仅记录守护之心状态与经验标记，<b>不消耗</b>（消耗推迟到 TAIL 注入）。
     *
     * <p>执行顺序：HEAD(记录) → {@code dropEquipment()} 检测 → TAIL(消耗)</p>
     */
    @Inject(method = "die", at = @At("HEAD"))
    private void youzaiworldcore$onDie(DamageSource damageSource, CallbackInfo ci) {
        ServerPlayer player = (ServerPlayer) (Object) this;

        // 旁观者模式不处理（不发放经验，不处理守护之心）
        if (player.isSpectator()) {
            yzwc$hadHeartBeforeDeath = false;
            yzwc$grantDeathExp = false;
            return;
        }

        // keepInventory=true 时，原版已保留物品栏，守护之心不消耗
        // 但死亡本身仍应发放经验
        boolean keepInventory = server.getGameRules().get(GameRules.KEEP_INVENTORY);
        if (keepInventory) {
            yzwc$hadHeartBeforeDeath = false;
            yzwc$grantDeathExp = true; // keepInventory 时也发放死亡经验
            return;
        }

        // 记录消耗前是否有守护之心（暂不消耗，PlayerDropEquipmentMixin 需要检测到它）
        yzwc$hadHeartBeforeDeath = hasHeartInInventory(player);
        // 有守护之心 → 发放守护之心经验（+50）；无守护之心 → 发放普通死亡经验（+10）
        yzwc$grantDeathExp = !yzwc$hadHeartBeforeDeath;
    }

    /**
     * 从玩家背包中清除第一个找到的守护之心。
     */
    @Unique
    private static void consumeOneHeart(ServerPlayer player) {
        Inventory inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack.getItem() == ModItems.HEART_OF_GUARDIANSHIP) {
                stack.shrink(1);
                break;
            }
        }
    }

    /**
     * 统计玩家背包中所有守护之心的总数量（所有槽位堆叠之和）。
     */
    @Unique
    private static int countHearts(ServerPlayer player) {
        int count = 0;
        Inventory inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack.getItem() == ModItems.HEART_OF_GUARDIANSHIP) {
                count += stack.getCount();
            }
        }
        return count;
    }

    /**
     * 检查玩家背包中是否有至少一个守护之心。
     */
    @Unique
    private static boolean hasHeartInInventory(ServerPlayer player) {
        Inventory inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack.getItem() == ModItems.HEART_OF_GUARDIANSHIP) {
                return true;
            }
        }
        return false;
    }

    /**
     * 当剩余守护之心数量等于预设阈值（10、5、3、2、1）时发送警告消息。
     */
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

    /**
     * 在 {@link ServerPlayer#die(DamageSource)} 执行完毕后注入，
     * 此时 {@code dropEquipment()} 已执行完毕，可以安全地消耗守护之心并发放经验。
     *
     * <p>推迟到此阶段才消耗，是为了让 {@link PlayerDropEquipmentMixin}
     * 在 {@code dropEquipment()} 检测时仍能发现背包中的守护之心并取消掉落。</p>
     */
    @Inject(method = "die", at = @At("TAIL"))
    private void youzaiworldcore$afterDie(DamageSource damageSource, CallbackInfo ci) {
        ServerPlayer player = (ServerPlayer) (Object) this;

        // 处理守护之心消耗
        if (yzwc$hadHeartBeforeDeath) {
            // 消耗 1 个守护之心
            consumeOneHeart(player);

            // 发送消耗提醒
            player.sendSystemMessage(
                    Component.translatable("youzaiworldcore.tellraw.format")
                            .append(Component.translatable("item.youzaiworldcore.heart_of_guardianship.consumed"))
            );

            // 授予成就（used_heart_of_guardianship）
            AdvancementHolder advancement = server.getAdvancements().get(
                    Identifier.fromNamespaceAndPath("youzaiworldcore", "youzaiworld/used_heart_of_guardianship")
            );
            if (advancement != null) {
                player.getAdvancements().award(advancement, "manual_grant");
            }

            // 消耗后统计剩余守护之心数量，触发相应阈值警告
            int remaining = countHearts(player);
            warnIfThreshold(player, remaining);

            // 发放冒险经验：守护之心保护 → +50
            AdventureLevelManager.grantExp(player, AdventureLevelManager.EXP_HEART_OF_GUARDIANSHIP);
        } else if (yzwc$grantDeathExp) {
            // 发放冒险经验：普通死亡 → +10
            AdventureLevelManager.grantExp(player, AdventureLevelManager.EXP_DEATH);
        }
    }

    /**
     * 在 {@link ServerPlayer#restoreFrom(ServerPlayer, boolean)} 执行完毕后注入，
     * 将死亡老玩家物品栏中的物品转移到重生后的新玩家。
     *
     * <p>原版重生流程中，当 {@code keepInventory=false} 时，
     * {@code restoreFrom()} 不会调用 {@code transferInventoryXpAndScore()}，
     * 但守护之心取消了 {@code dropEquipment()}，物品留在老玩家身上。
     * 此注入补传物品到新玩家。</p>
     */
    @Inject(method = "restoreFrom", at = @At("RETURN"))
    private void youzaiworldcore$restoreInventory(ServerPlayer oldPlayer, boolean alive, CallbackInfo ci) {
        // alive=true 表示维度传送等场景，原版已处理，无需干预
        if (alive) {
            return;
        }

        // keepInventory=true 时原版已调用 transferInventoryXpAndScore，无需重复
        if (server.getGameRules().get(GameRules.KEEP_INVENTORY)) {
            return;
        }

        // 检查老玩家是否有守护之心标记（死亡前拥有的快照）
        ServerPlayerDeathMixin oldMixin = (ServerPlayerDeathMixin) (Object) oldPlayer;
        if (oldMixin.yzwc$hadHeartBeforeDeath) {
            // 补传物品栏、经验与分数到新玩家
            this.transferInventoryXpAndScore(oldPlayer);
        }
    }
}
