package top.csituka.youzaiworldcore.event;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Creeper;
import top.csituka.youzaiworldcore.config.ChargedCreeperConfig;
import top.csituka.youzaiworldcore.mixin.chargedcreeper.CreeperChargedAccessor;
import top.csituka.youzaiworldcore.util.DebugLogger;

import java.util.Set;

/**
 * 天然带电苦力怕处理器。
 * <p>
 * 功能移植自 Serilum 的 Naturally Charged Creepers（已取得作者许可，无需署名）。
 * 行为：每当苦力怕进入服务端世界时，以 {@link ChargedCreeperConfig#getChance()} 的概率
 * 将其标记为带电状态。通过数据标签保证每个苦力怕只判定一次，避免区块重载时重复判定。
 * </p>
 * <p>
 * 实现要点：
 * <ul>
 *   <li>仅服务端生效（{@code level.isClientSide()} 守卫），带电状态由服务端权威同步；</li>
 *   <li>监听 {@code ServerEntityEvents.ENTITY_LOAD}，覆盖自然生成、刷怪蛋、指令召唤与区块重载等所有来源；</li>
 *   <li>带电写入 26.2 的 {@code DATA_IS_POWERED} 实体数据（见 {@link CreeperChargedAccessor}），
 *       以确保客户端闪电光环等渲染正确同步；</li>
 *   <li>所有诊断日志经 {@link DebugLogger} 输出，受开发者模式 + 日志级别双维度控制。</li>
 * </ul>
 * </p>
 */
@SuppressWarnings("null")
public class ChargedCreeperHandler {

    private static final String MODULE = "ChargedCreeper";

    /** 用于标记"已判定过"的实体数据标签，避免重复判定 */
    private static final String CHECKED_TAG = "youzaiworldcore.charged_creeper_checked";

    private ChargedCreeperHandler() {
    }

    /**
     * 向 Fabric 事件总线注册天然带电苦力怕处理器。
     */
    public static void register() {
        DebugLogger.entering(MODULE, "register");

        double chance = ChargedCreeperConfig.getChance();
        DebugLogger.info(MODULE, "天然带电苦力怕处理器已注册 (chance=%.4f)", chance);

        ServerEntityEvents.ENTITY_LOAD.register((Entity entity, ServerLevel world) ->
                onEntityJoin(world, entity));

        DebugLogger.exiting(MODULE, "register");
    }

    /**
     * 实体进入世界回调：对苦力怕按概率判定是否带电。
     *
     * @param level  世界实例（此处保证为 {@link ServerLevel}）
     * @param entity 进入世界的实体
     */
    private static void onEntityJoin(ServerLevel level, Entity entity) {
        if (level.isClientSide()) {
            return;
        }

        if (!(entity instanceof Creeper creeper)) {
            return;
        }

        // 功能总开关（由 /yzwc event naturally_charged_creepers enable 控制）
        if (!ChargedCreeperConfig.isEnabled()) {
            DebugLogger.debug(MODULE, "功能已禁用，creeper %s 跳过判定", entity.getUUID());
            return;
        }

        // 已判定过的苦力怕跳过（标签随实体持久化，跨区块重载依然有效）
        Set<String> tags = entity.entityTags();
        if (tags.contains(CHECKED_TAG)) {
            DebugLogger.debug(MODULE, "creeper %s 已判定过，跳过", entity.getUUID());
            return;
        }
        entity.addTag(CHECKED_TAG);

        double chance = ChargedCreeperConfig.getChance();
        if (chance <= 0.0) {
            DebugLogger.debug(MODULE, "chance<=0，creeper %s 不带电", entity.getUUID());
            return;
        }

        double roll = Math.random();
        boolean charged = chance >= 1.0 || roll < chance;

        DebugLogger.debug(MODULE, "creeper %s 判定: roll=%.4f chance=%.4f -> %s",
                entity.getUUID(), roll, chance, charged ? "带电" : "普通");

        if (charged) {
            EntityDataAccessor<Boolean> poweredData =
                    ((CreeperChargedAccessor) creeper).youzaiworldcore$getDataIsPowered();
            creeper.getEntityData().set(poweredData, true);
            DebugLogger.info(MODULE, "creeper %s 已设为带电状态", entity.getUUID());
        }
    }
}
