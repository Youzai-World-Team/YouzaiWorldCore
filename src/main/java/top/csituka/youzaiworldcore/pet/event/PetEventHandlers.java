package top.csituka.youzaiworldcore.pet.event;

import net.fabricmc.fabric.api.entity.event.v1.ServerEntityLevelChangeEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import top.csituka.youzaiworldcore.pet.PetEventHandler;
import top.csituka.youzaiworldcore.util.DebugLogger;

/**
 * 宠物模块 Fabric 事件注册中心。
 * <p>
 * 统一注册所有宠物模块需要的 Fabric 事件回调，
 * 包括交互、伤害、死亡、实体加载、维度变化等。
 * </p>
 */
public final class PetEventHandlers {

    private static final String MODULE = "PetEventHandlers";
    private static boolean registered = false;

    private PetEventHandlers() {
    }

    /**
     * 注册所有宠物模块事件处理器。
     * 应在模组初始化期间调用。
     */
    public static void register() {
        if (registered) {
            return;
        }
        DebugLogger.entering(MODULE, "register");

        // ===== 交互事件（右键点击实体）=====
        registerInteractHandler();

        // ===== 伤害事件 =====
        registerDamageHandler();

        // ===== 死亡事件 =====
        registerDeathHandler();

        // ===== 实体加载事件 =====
        registerEntityLoadHandler();

        // ===== 维度变化事件 =====
        registerDimensionChangeHandler();

        registered = true;
        DebugLogger.info(MODULE, "宠物模块 Fabric 事件已注册");
        DebugLogger.exiting(MODULE, "register");
    }

    // ===== 交互事件（右键点击）=====

    private static void registerInteractHandler() {
        UseEntityCallback.EVENT.register((Player player, Level level, InteractionHand hand,
                                          Entity entity, EntityHitResult hitResult) -> {
            if (level.isClientSide()) {
                return InteractionResult.PASS;
            }

            if (!(entity instanceof Wolf wolf)) {
                return InteractionResult.PASS;
            }

            // 检查是否使用命名牌
            if (player.getItemInHand(hand).is(Items.NAME_TAG)) {
                if (PetEventHandler.onNameTagUse(player, wolf)) {
                    return InteractionResult.FAIL;
                }
                return InteractionResult.PASS;
            }

            // 处理信任玩家交互权限
            if (PetEventHandler.onInteract(player, wolf)) {
                // 非信任玩家 → 取消交互
                return InteractionResult.FAIL;
            }

            // 信任玩家 → 放行原版交互
            return InteractionResult.PASS;
        });
    }

    // ===== 伤害事件（拦截误伤）=====

    private static void registerDamageHandler() {
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
            if (entity.level().isClientSide()) {
                return true;
            }

            if (!(entity instanceof Wolf wolf)) {
                return true;
            }

            ServerLevel level = (ServerLevel) entity.level();
            boolean cancelled = PetEventHandler.onDamage(wolf, source, level);

            if (cancelled) {
                // 强制清空愤怒状态
                wolf.setPersistentAngerEndTime(0);
                if (wolf.getPersistentAngerTarget() != null) {
                    wolf.setPersistentAngerTarget(null);
                }
                return false; // 取消伤害
            }

            return true; // 允许伤害
        });
    }

    // ===== 死亡事件 =====

    private static void registerDeathHandler() {
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (entity.level().isClientSide()) {
                return;
            }

            if (!(entity instanceof Wolf wolf)) {
                return;
            }

            ServerLevel level = (ServerLevel) entity.level();
            PetEventHandler.onDeath(wolf, level);
        });
    }

    // ===== 实体加载事件 =====

    private static void registerEntityLoadHandler() {
        ServerEntityEvents.ENTITY_LOAD.register((Entity entity, ServerLevel world) -> {
            if (world.isClientSide()) {
                return;
            }

            if (!(entity instanceof Wolf wolf)) {
                return;
            }

            PetEventHandler.onEntityLoad(wolf, world);
        });
    }

    // ===== 维度变化事件 =====

    private static void registerDimensionChangeHandler() {
        // 玩家维度切换
        ServerEntityLevelChangeEvents.AFTER_PLAYER_CHANGE_LEVEL.register(
                (ServerPlayer player, ServerLevel origin, ServerLevel destination) -> {
                    PetEventHandler.onChangeDimension(player, destination);
                }
        );
    }
}
