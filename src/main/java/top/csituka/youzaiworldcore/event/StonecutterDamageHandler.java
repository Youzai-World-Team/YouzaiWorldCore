package top.csituka.youzaiworldcore.event;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.NonNull;
import top.csituka.youzaiworldcore.util.DebugLogger;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * 切石机（Stonecutter）伤害 Tick 事件处理器。
 * <p>
 * 每 tick 检测所有在线玩家是否站在切石机方块上。
 * <ul>
 *   <li><b>首次站上切石机</b>：立即造成 2 点伤害（1 颗心）</li>
 *   <li><b>持续站在切石机上</b>：每 30 tick（1.5 秒）继续造成伤害</li>
 *   <li><b>离开切石机</b>：停止造成伤害</li>
 * </ul>
 * 死亡时显示自定义死亡消息：{@code <player>试图用身体测试切石机的锋利度}。
 * </p>
 * 实现 Fabric API 的 {@link ServerTickEvents.StartTick} 接口。
 */
public class StonecutterDamageHandler implements ServerTickEvents.StartTick {

    // 单例实例
    private static final StonecutterDamageHandler INSTANCE = new StonecutterDamageHandler();

    /** 伤害间隔（游戏刻）：30 tick = 1.5 秒 */
    private static final int DAMAGE_INTERVAL = 30;

    /** 每次造成的伤害值：2.0 = 1 颗心 */
    private static final float DAMAGE_AMOUNT = 2.0f;

    /** 自定义伤害类型 ResourceKey */
    private static final ResourceKey<DamageType> STONECUTTER_DAMAGE =
            ResourceKey.create(Registries.DAMAGE_TYPE,
                    Identifier.fromNamespaceAndPath("youzaiworldcore", "stonecutter"));

    // 辅助计次器，用于实现间隔检查
    private static int tickCounter = 0;

    // 上一步检测中站在切石机上的玩家 UUID 集合（用于检测进场/离场）
    private static final Set<UUID> stonecutterPlayers = new HashSet<>();

    // 私有构造，确保单例
    private StonecutterDamageHandler() {
    }

    /**
     * 每个游戏刻开始时触发。
     * <p>
     * 逻辑流程：
     * <ol>
     *   <li>递增 tick 计数器</li>
     *   <li>遍历所有在线非创造/非旁观玩家</li>
     *   <li>若玩家站在切石机上，判断是否为新入场：
     *     <ul>
     *       <li>新入场 → 立即造成伤害（"上脚那一下"）</li>
     *       <li>已在场 → 仅当达到间隔 tick 数时造成伤害</li>
     *     </ul>
     *   </li>
     *   <li>达到间隔时重置计数器</li>
     *   <li>更新历史玩家集合</li>
     * </ol>
     * </p>
     *
     * @param server Minecraft 服务器实例
     */
    @Override
    public void onStartTick(@NonNull MinecraftServer server) {
        DebugLogger.entering("StonecutterDamageHandler", "onStartTick");
        tickCounter++;
        boolean isDamageTick = tickCounter >= DAMAGE_INTERVAL;
        if (isDamageTick) {
            DebugLogger.info("StonecutterDamageHandler", "Interval tick reached");
        }

        // 记录本次 tick 中站在切石机上的玩家 UUID
        Set<UUID> currentAffected = new HashSet<>();

        // 遍历所有在线玩家
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            // 创造模式或旁观者模式的玩家不受影响
            if (player.isCreative() || player.isSpectator()) {
                DebugLogger.debug("StonecutterDamageHandler", "Skipping " + player.getName().getString()
                        + " (creative/spectator)");
                continue;
            }

            // 检测玩家是否站在切石机上
            boolean onStonecutter = isStandingOnStonecutter(player);

            if (onStonecutter) {
                UUID playerId = player.getUUID();
                currentAffected.add(playerId);

                boolean isNewArrival = !stonecutterPlayers.contains(playerId);

                // 判断本 tick 是否应对此玩家造成伤害
                boolean shouldDamage = isNewArrival                // 刚站上去 -> 立即扣血
                        || (isDamageTick && !isNewArrival);        // 已在上面 + 到达间隔 -> 持续扣血

                if (!shouldDamage) {
                    continue;
                }

                // 获取自定义伤害源
                DamageSource damageSource = getStonecutterDamageSource(player);
                if (damageSource == null) {
                    DebugLogger.error("StonecutterDamageHandler",
                            "Failed to obtain stonecutter damage source for " + player.getName().getString());
                    continue;
                }

                // 对玩家造成伤害
                boolean hurtResult = player.hurtServer(
                        (ServerLevel) player.level(),
                        damageSource,
                        DAMAGE_AMOUNT
                );
                DebugLogger.info("StonecutterDamageHandler",
                        "Dealt %.1f damage to %s (on stonecutter, newArrival=%s, isDamageTick=%s) -> hurt=%s",
                        DAMAGE_AMOUNT, player.getName().getString(), isNewArrival, isDamageTick, hurtResult);
            }
        }

        // 重置计数器
        if (isDamageTick) {
            tickCounter = 0;
        }

        // 记录切石机玩家集合变化
        if (!stonecutterPlayers.equals(currentAffected)) {
            Set<UUID> leftPlayers = new HashSet<>(stonecutterPlayers);
            leftPlayers.removeAll(currentAffected);
            for (UUID left : leftPlayers) {
                DebugLogger.info("StonecutterDamageHandler",
                        "Player %s left stonecutter", left);
            }
            Set<UUID> newPlayers = new HashSet<>(currentAffected);
            newPlayers.removeAll(stonecutterPlayers);
            for (UUID arrived : newPlayers) {
                DebugLogger.info("StonecutterDamageHandler",
                        "Player %s stepped onto stonecutter", arrived);
            }
        }

        stonecutterPlayers.clear();
        stonecutterPlayers.addAll(currentAffected);

        DebugLogger.exiting("StonecutterDamageHandler", "onStartTick",
                "affected=" + currentAffected.size());
    }

    /**
     * 检测玩家当前是否站在切石机方块上。
     * <p>
     * 由于切石机是一个部分高度方块（9/16 格高），当玩家站在其上时，
     * 其脚下方块位置可能为切石机本身或上方一格，因此同时检测
     * {@code player.blockPosition()} 和 {@code player.blockPosition().below()}。
     * </p>
     *
     * @param player 目标玩家
     * @return {@code true} 如果玩家站在切石机方块上
     */
    private boolean isStandingOnStonecutter(ServerPlayer player) {
        BlockState stateAt = player.level().getBlockState(player.blockPosition());
        BlockState stateBelow = player.level().getBlockState(player.blockPosition().below());

        boolean result = stateAt.getBlock() == Blocks.STONECUTTER
                || stateBelow.getBlock() == Blocks.STONECUTTER;

        DebugLogger.debug("StonecutterDamageHandler",
                "isStandingOnStonecutter for %s: stateAt=stonecutter=%s, stateBelow=stonecutter=%s -> %s",
                player.getName().getString(),
                stateAt.getBlock() == Blocks.STONECUTTER,
                stateBelow.getBlock() == Blocks.STONECUTTER,
                result);
        return result;
    }

    /**
     * 获取自定义的切石机伤害源。
     * <p>
     * 从注册表中获取 {@code youzaiworldcore:stonecutter} 伤害类型，
     * 并包装为 {@link DamageSource} 实例。该伤害类型的死亡消息
     * 通过语言文件 {@code death.attack.stonecutter} 定义。
     * </p>
     *
     * @param player 用于获取注册表访问的目标玩家
     * @return 切石机伤害源，若注册表查询失败则返回 {@code null}
     */
    private DamageSource getStonecutterDamageSource(ServerPlayer player) {
        try {
            Registry<DamageType> damageTypeRegistry = player.level().registryAccess()
                    .lookupOrThrow(Registries.DAMAGE_TYPE);
            Holder.Reference<DamageType> holder = damageTypeRegistry.getOrThrow(STONECUTTER_DAMAGE);
            return new DamageSource(holder);
        } catch (Exception e) {
            DebugLogger.exception("StonecutterDamageHandler",
                    "getStonecutterDamageSource", e);
            return null;
        }
    }

    /**
     * 向 Fabric 事件总线注册此处理器。
     */
    public static void register() {
        DebugLogger.entering("StonecutterDamageHandler", "register");
        ServerTickEvents.START_SERVER_TICK.register(INSTANCE);
        DebugLogger.exiting("StonecutterDamageHandler", "register");
    }
}
