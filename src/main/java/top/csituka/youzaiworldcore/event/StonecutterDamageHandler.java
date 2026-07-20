package top.csituka.youzaiworldcore.event;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.advancements.AdvancementHolder;
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

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * 切石机（Stonecutter）伤害 Tick 事件处理器。
 * <p>
 * <b>设计思路（性能优先）：</b>
 * <ul>
 *   <li><b>不</b>每 tick 扫描全服玩家——改用分时扫描 + 独立计时器</li>
 *   <li>每 10 tick 扫描一次全服玩家，检测是否有新人站上切石机</li>
 *   <li>一旦检测到某玩家站上切石机，将其加入 {@link #playerTimers} 映射</li>
 *   <li>此后每 tick<b>只对该映射中的玩家</b>递增计时器，<b>不再</b>调用 {@code getBlockState()}</li>
 *   <li>每 10 tick 对映射中的玩家做一次位置校验（确认是否还在切石机上）</li>
 *   <li>离开后自动从映射中移除，停止计时和伤害</li>
 * </ul>
 * </p>
 * <p>
 * <b>伤害节奏：</b>
 * <ul>
 *   <li><b>首次站上切石机</b>：立即造成 2 点伤害（1 颗心）</li>
 *   <li><b>持续站在切石机上</b>：每累积 30 tick（1.5 秒）造成一次伤害</li>
 *   <li>死亡时显示自定义死亡消息：{@code <player>试图用身体测试切石机的锋利度}</li>
 * </ul>
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

    /** 新人检测 / 位置校验 间隔（游戏刻）：每 10 tick = 0.5 秒 */
    private static final int SCAN_INTERVAL = 10;

    /** 自定义伤害类型 ResourceKey */
    private static final ResourceKey<DamageType> STONECUTTER_DAMAGE =
            ResourceKey.create(Registries.DAMAGE_TYPE,
                    Identifier.fromNamespaceAndPath("youzaiworldcore", "stonecutter"));

    // ====== 分时扫描计数器 ======
    private static int scanTickCounter = 0;

    // ====== 正在被切石机伤害的玩家及其独立计时器 ======
    // value = 自上次伤害以来经过的 tick 数（或刚入场时的值）
    private static final Map<UUID, Integer> playerTimers = new HashMap<>();

    // 私有构造，确保单例
    private StonecutterDamageHandler() {
    }

    /**
     * 每个游戏刻开始时触发。
     * <p>
     * 逻辑流程（三个步骤）：
     * <ol>
     *   <li><b>处理已追踪玩家</b>：遍历 {@link #playerTimers}，递增计时器，达阈值时伤害 + 重置</li>
     *   <li><b>位置校验</b>（每 {@link #SCAN_INTERVAL} tick 一次）：对已追踪玩家执行 {@link #isStandingOnStonecutter}，
     *       若已离开则移除</li>
     *   <li><b>新人入场扫描</b>（与位置校验同节奏）：扫全服在线玩家，发现新站在切石机上的玩家则
     *       立即伤害 + 加入追踪映射</li>
     * </ol>
     * </p>
     *
     * @param server Minecraft 服务器实例
     */
    @Override
    public void onStartTick(@NonNull MinecraftServer server) {
        DebugLogger.entering("StonecutterDamageHandler", "onStartTick");

        scanTickCounter++;
        boolean doScan = scanTickCounter >= SCAN_INTERVAL;
        if (doScan) {
            scanTickCounter = 0;
        }

        // ============================================================
        // Step 1: 处理已追踪玩家的计时器（每 tick 执行，极轻量）
        // ============================================================
        Iterator<Map.Entry<UUID, Integer>> it = playerTimers.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Integer> entry = it.next();
            UUID playerId = entry.getKey();
            @SuppressWarnings("null")
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);

            // 玩家离线或死亡 → 移除追踪
            if (player == null || !player.isAlive()) {
                DebugLogger.info("StonecutterDamageHandler",
                        "Removing %s from tracker (offline/dead)", playerId);
                it.remove();
                continue;
            }

            // 计数 + 判断是否达到伤害间隔
            int elapsed = entry.getValue() + 1;
            if (elapsed >= DAMAGE_INTERVAL) {
                DamageSource damageSource = getStonecutterDamageSource(player);
                if (damageSource != null) {
                    boolean hurtResult = player.hurtServer(
                            (ServerLevel) player.level(),
                            damageSource,
                            DAMAGE_AMOUNT
                    );
                    DebugLogger.info("StonecutterDamageHandler",
                            "Continual damage: dealt %.1f to %s (timer=%d) -> hurt=%s",
                            DAMAGE_AMOUNT, player.getName().getString(), elapsed, hurtResult);

                    // 若此次伤害导致玩家死亡，授予成就
                    if (!player.isAlive()) {
                        grantStonecutterAdvancement(player);
                    }
                }
                entry.setValue(0); // 重置计时器
            } else {
                entry.setValue(elapsed);
            }
        }

        // ============================================================
        // Step 2: 位置校验 + 新人入场扫描（每 SCAN_INTERVAL tick 一次）
        // ============================================================
        if (doScan) {
            DebugLogger.debug("StonecutterDamageHandler", "Running periodic scan (scanTick=%d)", scanTickCounter);

            // ---- Step 2a: 校验已追踪玩家是否还站在切石机上 ----
            it = playerTimers.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<UUID, Integer> entry = it.next();
                UUID playerId = entry.getKey();
                @SuppressWarnings("null")
                ServerPlayer player = server.getPlayerList().getPlayer(playerId);
                if (player == null) {
                    it.remove();
                    continue;
                }
                if (!isStandingOnStonecutter(player)) {
                    DebugLogger.info("StonecutterDamageHandler",
                            "Player %s left stonecutter (verified on scan)", player.getName().getString());
                    it.remove();
                }
            }

            // ---- Step 2b: 扫描新玩家 ----
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                // 创造/旁观模式跳过
                if (player.isCreative() || player.isSpectator()) {
                    continue;
                }
                // 已在追踪中跳过
                if (playerTimers.containsKey(player.getUUID())) {
                    continue;
                }
                // 检测是否站在切石机上
                if (isStandingOnStonecutter(player)) {
                    // 新入场 → 立即造成伤害
                    DamageSource damageSource = getStonecutterDamageSource(player);
                    if (damageSource != null) {
                        boolean hurtResult = player.hurtServer(
                                (ServerLevel) player.level(),
                                damageSource,
                                DAMAGE_AMOUNT
                        );
                        DebugLogger.info("StonecutterDamageHandler",
                                "New arrival: dealt %.1f to %s -> hurt=%s",
                                DAMAGE_AMOUNT, player.getName().getString(), hurtResult);

                        // 若此次伤害导致玩家死亡，授予成就
                        if (!player.isAlive()) {
                            grantStonecutterAdvancement(player);
                        }
                    }
                    // 加入追踪映射，计时器从 0 开始
                    playerTimers.put(player.getUUID(), 0);
                    DebugLogger.info("StonecutterDamageHandler",
                            "Player %s started being tracked on stonecutter", player.getName().getString());
                }
            }
        }

        DebugLogger.exiting("StonecutterDamageHandler", "onStartTick",
                "tracked=" + playerTimers.size());
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
    private static boolean isStandingOnStonecutter(ServerPlayer player) {
        BlockState stateAt = player.level().getBlockState(player.blockPosition());
        BlockState stateBelow = player.level().getBlockState(player.blockPosition().below());

        return stateAt.getBlock() == Blocks.STONECUTTER
                || stateBelow.getBlock() == Blocks.STONECUTTER;
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
    private static DamageSource getStonecutterDamageSource(ServerPlayer player) {
        try {
            @SuppressWarnings("null")
            Registry<DamageType> damageTypeRegistry = player.level().registryAccess()
                    .lookupOrThrow(Registries.DAMAGE_TYPE);
            @SuppressWarnings("null")
            Holder.Reference<DamageType> holder = damageTypeRegistry.getOrThrow(STONECUTTER_DAMAGE);
            return new DamageSource(holder);
        } catch (Exception e) {
            DebugLogger.exception("StonecutterDamageHandler",
                    "getStonecutterDamageSource", e);
            return null;
        }
    }

    /**
     * 如果玩家因切石机死亡，授予 "我成了建材" 成就。
     * <p>
     * 成就 ID: {@code youzaiworldcore:fun_little_challenge/tested_stonecutter}
     * </p>
     *
     * @param player 已死亡的玩家
     */
    private static void grantStonecutterAdvancement(ServerPlayer player) {
        AdvancementHolder advancement = player.level().getServer().getAdvancements().get(
                Identifier.fromNamespaceAndPath("youzaiworldcore", "fun_little_challenge/tested_stonecutter")
        );
        if (advancement != null) {
            player.getAdvancements().award(advancement, "manual_grant");
            DebugLogger.info("StonecutterDamageHandler",
                    "Granted advancement 'tested_stonecutter' to %s",
                    player.getName().getString());
        } else {
            DebugLogger.warn("StonecutterDamageHandler",
                    "Advancement 'fun_little_challenge/tested_stonecutter' not found!");
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
