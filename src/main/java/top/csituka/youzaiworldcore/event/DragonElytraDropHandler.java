package top.csituka.youzaiworldcore.event;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;

/**
 * 末影龙鞘翅掉落事件处理器。
 * <p>
 * 末影龙被击杀时执行逻辑：
 * <ol>
 * <li>确定击杀归属玩家（直接击杀 / 弹射物击杀 / 半径搜索兜底）</li>
 * <li>在归属玩家位置（或龙的位置）生成一个鞘翅掉落物</li>
 * <li>向末地内全体玩家广播提示消息</li>
 * <li>向归属玩家发送位置提示私聊消息</li>
 * </ol>
 * </p>
 * <p>
 * 注册于 {@link ServerLivingEntityEvents#AFTER_DEATH}，与
 * {@link EndPortalHandler}（额外龙蛋）在同一事件上独立工作，互不干扰。
 * </p>
 *
 * @see EndPortalHandler
 * @see 设计参考：Dragon-Drops-Elytra by Serilum (natamus)
 */
@SuppressWarnings("null")
public class DragonElytraDropHandler {

    /** 若无法直接获取击杀玩家，搜索附近玩家的半径（方块） */
    private static final int SEARCH_RADIUS = 30;

    private static final DragonElytraDropHandler INSTANCE = new DragonElytraDropHandler();
    private static final Logger LOGGER = LoggerFactory.getLogger("YouzaiWorldCore/DragonElytra");

    private DragonElytraDropHandler() {
    }

    // ========================================================================
    // 回调方法
    // ========================================================================

    /**
     * {@link ServerLivingEntityEvents#AFTER_DEATH} 回调。
     * 仅当死亡的实体是 {@link EnderDragon} 时触发鞘翅掉落逻辑。
     */
    private void onEntityDeath(LivingEntity entity, DamageSource damageSource) {
        // 仅服务端执行（AFTER_DEATH 本就在服务端事件总线上，防御性检查）
        if (entity.level().isClientSide()) {
            return;
        }
        if (!(entity instanceof EnderDragon)) {
            return;
        }

        LOGGER.info("末影龙被击杀，触发鞘翅掉落逻辑");

        // ===== 1. 确定击杀归属玩家 =====
        Optional<Player> attributionPlayer = findAttributionPlayer((EnderDragon) entity, damageSource);

        // ===== 2. 确定掉落坐标（归属玩家 → 龙自身） =====
        BlockPos dropPos;
        if (attributionPlayer.isPresent()) {
            dropPos = attributionPlayer.get().blockPosition();
        } else {
            dropPos = entity.blockPosition();
        }

        // ===== 3. 生成鞘翅掉落物 =====
        Level level = entity.level();
        ItemStack elytraStack = new ItemStack(Items.ELYTRA, 1);
        ItemEntity elytraEntity = new ItemEntity(
                level,
                dropPos.getX(), dropPos.getY() + 1.0, dropPos.getZ(),
                elytraStack);
        elytraEntity.setDefaultPickUpDelay();
        level.addFreshEntity(elytraEntity);

        LOGGER.info("鞘翅已生成于 [{}, {}, {}]", dropPos.getX(), dropPos.getY() + 1, dropPos.getZ());

        // ===== 4. 广播提示消息（所有在末地世界的玩家） =====
        Component broadcastMsg = Component.translatable(
                "youzaiworldcore.message.dragon_elytra.seems_like_slain").withStyle(ChatFormatting.DARK_GREEN);

        ServerLevel serverLevel = (ServerLevel) entity.level();
        for (ServerPlayer player : serverLevel.getServer().getPlayerList().getPlayers()) {
            if (player.level().dimension() == serverLevel.dimension()) {
                player.sendSystemMessage(broadcastMsg);
            }
        }

        // ===== 5. 归属玩家私聊位置提示 =====
        attributionPlayer.ifPresent(player -> {
            if (player instanceof ServerPlayer serverPlayer) {
                Component positionMsg = Component.translatable(
                        "youzaiworldcore.message.dragon_elytra.elytra_dropped_position",
                        dropPos.getX(), dropPos.getY() + 1, dropPos.getZ()).withStyle(ChatFormatting.DARK_GREEN);
                serverPlayer.sendSystemMessage(positionMsg);
            }
        });

        LOGGER.info("末影龙鞘翅掉落逻辑执行完毕");
    }

    // ========================================================================
    // 玩家归属查找
    // ========================================================================

    /**
     * 确定击杀末影龙的归属玩家，优先级：
     * <ol>
     * <li><b>直接玩家</b>：伤害来源实体本身就是 {@link Player}</li>
     * <li><b>弹射物归属</b>：伤害来源是 {@link Projectile}，取其发射者
     * （覆盖弓 / 弩 / 三叉戟等远程击杀，原 Dragon-Drops-Elytra 未处理此分支）</li>
     * <li><b>半径搜索兜底</b>：在 {@value #SEARCH_RADIUS} 格半径内搜索最近玩家</li>
     * </ol>
     *
     * @param dragon       死亡的末影龙实体
     * @param damageSource 致命一击的伤害来源
     * @return 归属玩家的 Optional（可能为空 = 末地无人）
     */
    private static Optional<Player> findAttributionPlayer(EnderDragon dragon, DamageSource damageSource) {
        Entity sourceEntity = damageSource.getEntity();

        // 优先级 1：直接玩家击杀（近战剑类）
        if (sourceEntity instanceof Player player) {
            return Optional.of(player);
        }

        // 优先级 2：弹射物击杀（弓 / 弩 / 三叉戟 / 雪球等）
        // damageSource.getEntity() 在此场景返回的是弹射物本身，而非发射它的玩家
        if (sourceEntity instanceof Projectile projectile) {
            Entity owner = projectile.getOwner();
            if (owner instanceof Player player) {
                return Optional.of(player);
            }
        }

        // 优先级 3：半径搜索兜底（环境致死 / 他人宠物 / 岩浆等）
        BlockPos dragonPos = dragon.blockPosition();
        AABB searchBox = new AABB(
                dragonPos.getX() - SEARCH_RADIUS,
                dragonPos.getY() - SEARCH_RADIUS,
                dragonPos.getZ() - SEARCH_RADIUS,
                dragonPos.getX() + SEARCH_RADIUS,
                dragonPos.getY() + SEARCH_RADIUS,
                dragonPos.getZ() + SEARCH_RADIUS);

        List<Player> nearbyPlayers = dragon.level().getEntitiesOfClass(Player.class, searchBox);
        if (!nearbyPlayers.isEmpty()) {
            // 按距离排序，取最近的玩家（相比原 Dragon-Drops-Elytra "取第一个" 更合理）
            nearbyPlayers.sort((a, b) -> Double.compare(
                    a.distanceToSqr(dragon),
                    b.distanceToSqr(dragon)));
            Player nearest = nearbyPlayers.getFirst();
            LOGGER.info("通过半径搜索确定归属玩家: {} (距离={})",
                    nearest.getName().getString(),
                    Math.sqrt(nearest.distanceToSqr(dragon)));
            return Optional.of(nearest);
        }

        LOGGER.warn("末地范围内无玩家可接收鞘翅掉落");
        return Optional.empty();
    }

    // ========================================================================
    // 注册入口
    // ========================================================================

    /**
     * 向 Fabric 事件总线注册末影龙鞘翅掉落事件。
     * <p>
     * 在
     * {@link net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents#AFTER_DEATH}
     * 上注册，与 {@link EndPortalHandler} 完全独立。
     * </p>
     */
    public static void register() {
        ServerLivingEntityEvents.AFTER_DEATH.register(INSTANCE::onEntityDeath);
        LOGGER.info("末影龙鞘翅掉落事件处理器已注册 (ServerLivingEntityEvents.AFTER_DEATH)");
    }
}
