package top.csituka.youzaiworldcore.event;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EndPortalFrameBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import org.jspecify.annotations.Nullable;

import top.csituka.youzaiworldcore.config.EndPortalConfig;
import top.csituka.youzaiworldcore.util.DebugLogger;

import java.util.Iterator;
import java.util.List;

/**
 * 末地传送门相关功能事件处理器。
 * <p>
 * 注册两个事件：
 * <ul>
 *   <li><b>方块破坏前置</b>（{@link PlayerBlockBreakEvents#BEFORE}）——拦截末地传送门框的破坏，
 *       要求精准采集镐，掉落传送门框（含已嵌末影之眼）并清除激活的传送门方块</li>
 *   <li><b>生物死亡后置</b>（{@link ServerLivingEntityEvents#AFTER_DEATH}）——末影龙被击杀时，
 *       额外给予附近玩家一个龙蛋（配合合成配方使用）</li>
 * </ul>
 * </p>
 * <p>
 * 三个配置项（{@link EndPortalConfig}）控制行为细节。
 * </p>
 */
@SuppressWarnings("null")
public class EndPortalHandler {

    private static final EndPortalHandler INSTANCE = new EndPortalHandler();

    private EndPortalHandler() {}

    // ========================================================================
    // 事件 1：方块破坏前置 — 末地传送门框的精准采集破坏
    // ========================================================================

    /**
     * 方块破坏前置回调。当玩家即将破坏传送门框时进行拦截。
     * <p>
     * 条件：
     * <ol>
     *   <li>主手持镐（任何镐）</li>
     *   <li>（可选）镐上带有精准采集附魔</li>
     *   <li>目标方块是末地传送门框（{@link Blocks#END_PORTAL_FRAME}）</li>
     * </ol>
     * </p>
     * <p>
     * 满足条件后：掉落传送门框（若已嵌眼则额外掉落末影之眼），清理周围的激活传送门，
     * 并绕过原版破坏逻辑（{@code false} = 取消原版破坏）。
     * </p>
     */
    private boolean onBlockBreak(Level level, Player player, BlockPos pos, BlockState state, @Nullable BlockEntity blockEntity) {
        DebugLogger.entering("EndPortalHandler", "onBlockBreak",
                "player=" + player.getName().getString() + ", pos=" + pos);

        if (level.isClientSide()) {
            DebugLogger.exiting("EndPortalHandler", "onBlockBreak", "PASS (client)");
            return true;
        }

        // ===== 条件 1：主手持镐 =====
        ItemStack hand = player.getItemInHand(InteractionHand.MAIN_HAND);
        if (!hand.is(net.minecraft.tags.ItemTags.PICKAXES)) {
            DebugLogger.branch("EndPortalHandler", "holding pickaxe", false);
            DebugLogger.exiting("EndPortalHandler", "onBlockBreak", "PASS (not pickaxe)");
            return true;
        }

        // ===== 条件 2（可选）：需要精准采集 =====
        if (EndPortalConfig.isMustHaveSilkTouchToBreakPortal()) {
            int silkTouchLevel = EnchantmentHelper.getItemEnchantmentLevel(
                    level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT)
                            .getOrThrow(Enchantments.SILK_TOUCH),
                    hand
            );
            if (silkTouchLevel < 1) {
                DebugLogger.branch("EndPortalHandler", "has silk touch", false, "必须精准采集");
                DebugLogger.exiting("EndPortalHandler", "onBlockBreak", "PASS (no silk touch)");
                return true;
            }
        }

        // ===== 条件 3：目标必须是末地传送门框 =====
        if (!state.is(Blocks.END_PORTAL_FRAME)) {
            DebugLogger.branch("EndPortalHandler", "target is END_PORTAL_FRAME", false,
                    "actual=" + state.getBlock());
            DebugLogger.exiting("EndPortalHandler", "onBlockBreak", "PASS (not frame)");
            return true;
        }

        DebugLogger.info("EndPortalHandler", "Breaking portal frame at %s by %s",
                pos, player.getName().getString());

        // ===== 执行：掉落物品 =====
        ItemStack portalFrameStack = new ItemStack(Blocks.END_PORTAL_FRAME, 1);
        boolean isFilled = state.getValue(EndPortalFrameBlock.HAS_EYE);

        if (EndPortalConfig.isAddBrokenPortalFramesToInventory()) {
            // 直接入背包（满则掉落）
            giveOrDropItemStack(player, portalFrameStack);
            if (isFilled) {
                giveOrDropItemStack(player, new ItemStack(Items.ENDER_EYE, 1));
            }
        } else {
            // 以掉落物实体形式扔出
            level.addFreshEntity(new ItemEntity(level,
                    pos.getX(), pos.getY() + 1, pos.getZ(), portalFrameStack));
            if (isFilled) {
                level.addFreshEntity(new ItemEntity(level,
                        pos.getX(), pos.getY() + 1, pos.getZ(),
                        new ItemStack(Items.ENDER_EYE, 1)));
            }
        }

        // 移除方块（不产生原版掉落物）
        level.destroyBlock(pos, false);

        // 清除周围 7×7 范围内的激活传送门方块（同一 Y 层）
        Iterator<BlockPos> it = BlockPos.betweenClosedStream(
                pos.getX() - 3, pos.getY(), pos.getZ() - 3,
                pos.getX() + 3, pos.getY(), pos.getZ() + 3
        ).iterator();
        int cleared = 0;
        while (it.hasNext()) {
            BlockPos np = it.next();
            if (level.getBlockState(np).is(Blocks.END_PORTAL)) {
                level.setBlockAndUpdate(np, Blocks.AIR.defaultBlockState());
                cleared++;
            }
        }
        DebugLogger.info("EndPortalHandler", "Cleared %d END_PORTAL blocks around %s", cleared, pos);

        DebugLogger.exiting("EndPortalHandler", "onBlockBreak", "SUCCESS (cancelled original break)");
        return false; // 取消原版破坏行为
    }

    // ========================================================================
    // 事件 2：生物死亡后置 — 末影龙额外龙蛋
    // ========================================================================

    /**
     * 生物死亡后置回调。仅关注末影龙死亡：额外给予附近 50 格范围内的玩家一个龙蛋。
     * <p>
     * 原版末影龙首次击杀会在基座上生成龙蛋 {@link Blocks#DRAGON_EGG}；
     * 此回调额外再给予一个龙蛋到玩家背包，使得玩家可以合成新的传送门。
     * </p>
     */
    private void onEntityDeath(LivingEntity entity, DamageSource damageSource) {
        if (entity.level().isClientSide()) {
            return;
        }
        if (!(entity instanceof EnderDragon)) {
            return;
        }

        DebugLogger.info("EndPortalHandler", "EnderDragon killed – granting extra dragon egg");

        Entity source = damageSource.getEntity();
        Player targetPlayer;

        if (source instanceof Player player) {
            targetPlayer = player;
        } else {
            // 寻找 50 格范围内的第一个玩家
            BlockPos pos = entity.blockPosition();
            AABB searchBox = new AABB(
                    pos.getX() - 50, pos.getY() - 50, pos.getZ() - 50,
                    pos.getX() + 50, pos.getY() + 50, pos.getZ() + 50
            );
            List<Entity> nearby = entity.level().getEntities(null, searchBox);
            Player found = null;
            for (Entity e : nearby) {
                if (e instanceof Player p) {
                    found = p;
                    break;
                }
            }
            if (found == null) {
                DebugLogger.warn("EndPortalHandler", "No player within 50 blocks to receive dragon egg");
                return;
            }
            targetPlayer = found;
        }

        // 给予龙蛋（入背包 / 掉落）
        giveOrDropItemStack(targetPlayer, new ItemStack(Blocks.DRAGON_EGG, 1));
        DebugLogger.info("EndPortalHandler", "Extra dragon egg given to %s",
                targetPlayer.getName().getString());

        // 可选：发送提示消息
        if (EndPortalConfig.isSendMessageOnExtraDragonEggDrop()) {
            targetPlayer.sendSystemMessage(
                    Component.translatable(
                            "message.youzaiworldcore.endportal.extra_dragon_egg",
                            targetPlayer.getName().getString()
                    ).withStyle(ChatFormatting.DARK_GREEN)
            );
        }
    }

    // ========================================================================
    // 工具方法
    // ========================================================================

    /**
     * 尝试将物品放入玩家背包；若背包已满则在玩家位置以掉落物实体形式扔出。
     */
    private static void giveOrDropItemStack(Player player, ItemStack stack) {
        if (!player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
    }

    // ========================================================================
    // 注册入口
    // ========================================================================

    /**
     * 向 Fabric 事件总线注册所有末地传送门相关回调。
     * <p>
     * 注册的事件：
     * <ul>
     *   <li>{@link PlayerBlockBreakEvents#BEFORE} — 传送门框破坏拦截</li>
     *   <li>{@link ServerLivingEntityEvents#AFTER_DEATH} — 末影龙额外龙蛋</li>
     * </ul>
     * </p>
     */
    public static void register() {
        DebugLogger.entering("EndPortalHandler", "register");

        // 方块破坏前置
        PlayerBlockBreakEvents.BEFORE.register(INSTANCE::onBlockBreak);
        DebugLogger.info("EndPortalHandler", "注册方块破坏前置事件 (PlayerBlockBreakEvents.BEFORE)");

        // 生物死亡后置
        ServerLivingEntityEvents.AFTER_DEATH.register(INSTANCE::onEntityDeath);
        DebugLogger.info("EndPortalHandler", "注册生物死亡后置事件 (ServerLivingEntityEvents.AFTER_DEATH)");

        DebugLogger.exiting("EndPortalHandler", "register");
    }
}
