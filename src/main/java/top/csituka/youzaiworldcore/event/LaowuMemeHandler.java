package top.csituka.youzaiworldcore.event;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.animal.feline.Cat;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.Vec3;
import top.csituka.youzaiworldcore.config.LaowuMemeConfig;
import top.csituka.youzaiworldcore.network.LaowuMemeStopPayload;
import top.csituka.youzaiworldcore.network.LaowuMemeTriggerPayload;
import top.csituka.youzaiworldcore.util.DebugLogger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 「老吴贴贴」事件状态机（服务端权威，移植自 laowu meme 模组的 ServerMemeManager）。
 * <p>
 * 玩法：命名「老吴」的猫在 6 格内遇到任意另一只猫 → 双方被禁用 AI、平滑走到贴脸点
 * （中心距 2.0）并镜像面对面 → 广播 {@link LaowuMemeTriggerPayload}（客户端歪头 + 放大 +
 * 按服务端选曲播放 BGM）→ 右键任一只即释放（恢复 AI + 外推 + 冷却，时长默认 3 分钟、
 * 可由 {@code /yzwc event laowu settings cd} 调整）。
 * <p>
 * 与全局开关的联动：事件被 {@code /yzwc event laowu enable false} 禁用时，
 * 立即释放全部活跃配对（恢复 AI、广播 stop）并清空冷却，重新启用即可再次触发。
 * <p>
 * 死代码清理：不再保留从未使用的 {@code ROLL_ANGLE}（歪头角度唯一来源为客户端
 * {@code CatModelLaowuMixin.HEAD_ROLL}）；服务端选曲 {@code soundId} 真正下发并生效。
 */
@SuppressWarnings("null")
public final class LaowuMemeHandler {

    private static final String MODULE = "LaowuMemeHandler";

    /** 触发主体：自定义名称必须精确等于此名 */
    public static final String LAOWU_NAME = "老吴";
    /** 触发扫描距离（格） */
    public static final double TRIGGER_DISTANCE = 6.0;
    /** 锁定时两猫中心距（格）：头对头、身体明显分开 */
    public static final double LOCK_DISTANCE = 2.0;
    /** 各自离两猫中点的距离 */
    public static final double SPLIT = LOCK_DISTANCE / 2.0;
    /** 接近阶段每 tick 前进距离（≈走路） */
    public static final double APPROACH_SPEED = 0.14;
    /** 内置曲目数量（服务端随机选曲范围）：0=laowu2, 1=qiliang, 2=zhanhou */
    public static final int BUILTIN_SOUND_COUNT = 3;
    /** 扫描节流：每 N tick 扫描一次全部维度 */
    private static final int SCAN_INTERVAL = 10;

    private static final List<MemePair> activePairs = new ArrayList<>();
    private static final Map<UUID, Long> cooldownExpire = new HashMap<>();
    private static int scanCounter = 0;

    /** 猫类型筛选器（常量化：{@code EntityTypeTest.forClass} 每次调用都会新建匿名实现） */
    private static final EntityTypeTest<net.minecraft.world.entity.Entity, Cat> CAT_TEST =
            EntityTypeTest.forClass(Cat.class);

    /**
     * {@link #scan} 的「本轮已配对」集合，复用以免每维度每次扫描都新建 HashSet。
     * <p>仅在服务端主线程的 tick 回调中使用，每次使用前 clear，无跨调用状态。</p>
     */
    private static final Set<UUID> SCAN_USED = new HashSet<>();
    private static boolean registered = false;

    private LaowuMemeHandler() {
    }

    /** 注册服务端 tick 与右键事件（幂等，由 {@code YouzaiworldCore.onInitialize} 调用） */
    public static void register() {
        if (registered) {
            return;
        }
        DebugLogger.entering(MODULE, "register");
        ServerTickEvents.END_SERVER_TICK.register(LaowuMemeHandler::serverTick);
        UseEntityCallback.EVENT
                .register((player, world, hand, entity, hitResult) -> onRightClick(entity instanceof Cat c ? c : null));
        registered = true;
        DebugLogger.info(MODULE, "老吴贴贴事件已注册（服务端权威架构）");
        DebugLogger.exiting(MODULE, "register");
    }

    /** 每个服务端 tick 推进一次（END_SERVER_TICK） */
    private static void serverTick(MinecraftServer server) {
        if (!LaowuMemeConfig.isEnabled()) {
            // 事件被禁用：立即释放全部活跃配对（恢复 AI + 广播 stop）并清空冷却，
            // 保证重新启用后可立即再次触发，且不残留任何锁定的猫。
            if (!activePairs.isEmpty()) {
                DebugLogger.info(MODULE, "事件被禁用，释放全部活跃配对 (%d)", activePairs.size());
                for (MemePair p : new ArrayList<>(activePairs)) {
                    abort(p);
                }
                activePairs.clear();
                cooldownExpire.clear();
            }
            return;
        }

        scanCounter++;
        if (scanCounter % SCAN_INTERVAL == 0) {
            scan(server);
        }

        Iterator<MemePair> it = activePairs.iterator();
        while (it.hasNext()) {
            MemePair p = it.next();
            if (!p.alive()) {
                // 猫被移除/死亡：必须恢复存活猫的 AI 并广播 stop。
                // 否则存活猫仍带着 setNoAi(true) 永久定在原地（原实现的卡死 bug）。
                abort(p);
                it.remove();
                continue;
            }
            if (!p.grounded()) {
                // 配对期间任一只猫脚下悬空（虚空/无支撑）：释放配对恢复 AI 与物理，
                // 猫自然下落，不再被 setOnGround(true)+setPos 强行悬浮。
                DebugLogger.info(MODULE, "配对 %s <-> %s 脚下悬空，释放配对", p.catAId, p.catBId);
                abort(p);
                it.remove();
                continue;
            }
            p.tick();
        }

        long now = server.getTickCount();
        cooldownExpire.entrySet().removeIf(e -> e.getValue() <= now);
    }

    /** 右键猫 → 若在某配对中则释放（服务端权威） */
    private static InteractionResult onRightClick(Cat cat) {
        if (cat == null) {
            return InteractionResult.PASS;
        }
        MemePair p = findPair(cat.getUUID());
        if (p == null) {
            return InteractionResult.PASS;
        }
        DebugLogger.info(MODULE, "玩家右键释放配对: %s <-> %s", p.catAId, p.catBId);
        release(p, true);
        activePairs.remove(p);
        return InteractionResult.SUCCESS;
    }

    // ---- 内部 ----

    private static void scan(MinecraftServer server) {
        for (ServerLevel level : server.getAllLevels()) {
            // 注意：不能把 isLaowu 下推进筛选谓词。外层要求发起方是「老吴」，
            // 但内层找搭档时<b>不</b>要求对方也是老吴——老吴可以和普通猫配对。
            // 下推会改变这一既有行为，故仅做常量化与容器复用。
            List<? extends Cat> cats = level.getEntities(CAT_TEST, c -> true);
            if (cats.isEmpty()) {
                continue;
            }
            Set<UUID> used = SCAN_USED;
            used.clear();
            for (Cat laowu : cats) {
                if (!isLaowu(laowu)) {
                    continue;
                }
                UUID id = laowu.getUUID();
                if (used.contains(id) || isActive(id) || onCooldown(id)) {
                    continue;
                }

                Cat partner = null;
                double best = TRIGGER_DISTANCE * TRIGGER_DISTANCE;
                for (Cat c : cats) {
                    if (c == laowu) {
                        continue;
                    }
                    UUID cid = c.getUUID();
                    if (used.contains(cid) || isActive(cid) || onCooldown(cid)) {
                        continue;
                    }
                    double d = laowu.distanceToSqr(c);
                    if (d <= best) {
                        best = d;
                        partner = c;
                    }
                }
                if (partner != null) {
                    startPair(laowu, partner);
                    used.add(id);
                    used.add(partner.getUUID());
                }
            }
        }
    }

    private static void startPair(Cat a, Cat b) {
        int rollSign = a.getRandom().nextBoolean() ? 1 : -1;
        // 服务端选曲：三首内置曲等概率；soundId 随 trigger 包下发，
        // 客户端必须按它播放，实现全体玩家同听。
        int soundId = a.getRandom().nextInt(BUILTIN_SOUND_COUNT);
        activePairs.add(new MemePair(a, b, rollSign, soundId));
        DebugLogger.info(MODULE, "配对锁定：%s <-> %s (soundId=%d, rollSign=%d)",
                a.getUUID(), b.getUUID(), soundId, rollSign);
    }

    /** 右键释放：恢复 AI + 向外速度 + 写入冷却 + 广播 stop */
    private static void release(MemePair p, boolean giveKnockback) {
        long expire = p.server().getTickCount() + LaowuMemeConfig.getCooldownSeconds() * 20L;
        for (Cat c : new Cat[] { p.catA, p.catB }) {
            if (c == null || c.isRemoved()) {
                continue;
            }
            c.setNoAi(false);
            if (giveKnockback) {
                Vec3 away = new Vec3(c.getX() - p.other(c).getX(), 0, c.getZ() - p.other(c).getZ());
                if (away.lengthSqr() < 1e-4) {
                    away = new Vec3(c.getRandom().nextDouble() - 0.5, 0, c.getRandom().nextDouble() - 0.5);
                }
                away = away.normalize().scale(0.35);
                c.setDeltaMovement(away);
            }
            // 冷却只记录在带「老吴」标签的猫身上（每只老吴猫个体计时）：
            // 普通邻猫不被拖累、可立即参与下一次配对；多只老吴猫之间互不影响。
            if (isLaowu(c)) {
                cooldownExpire.put(c.getUUID(), expire);
            }
        }
        broadcastStop(p);
    }

    /** 中止（猫消失 / 事件禁用 / 脚下悬空）：恢复 AI + 广播 stop，不写冷却、不给外推 */
    private static void abort(MemePair p) {
        for (Cat c : new Cat[] { p.catA, p.catB }) {
            if (c == null || c.isRemoved()) {
                continue;
            }
            c.setNoAi(false);
        }
        broadcastStop(p);
    }

    private static void broadcastStop(MemePair p) {
        LaowuMemeStopPayload pkt = new LaowuMemeStopPayload(p.catAId, p.catBId);
        for (ServerPlayer sp : p.server().getPlayerList().getPlayers()) {
            ServerPlayNetworking.send(sp, pkt);
        }
    }

    private static void broadcastTrigger(MemePair p) {
        LaowuMemeTriggerPayload pkt = new LaowuMemeTriggerPayload(p.catAId, p.catBId, p.soundId, p.rollSign);
        for (ServerPlayer sp : p.server().getPlayerList().getPlayers()) {
            ServerPlayNetworking.send(sp, pkt);
        }
    }

    private static boolean isLaowu(Cat c) {
        return c.getCustomName() != null && LAOWU_NAME.equals(c.getCustomName().getString());
    }

    private static boolean isActive(UUID id) {
        for (MemePair p : activePairs) {
            if (p.has(id)) {
                return true;
            }
        }
        return false;
    }

    private static boolean onCooldown(UUID id) {
        return cooldownExpire.containsKey(id);
    }

    /** 猫脚下 1 格是否为非空气方块（虚空 / 深坑视为无支撑） */
    private static boolean hasGroundBelow(Cat c) {
        return !c.level().getBlockState(c.blockPosition().below()).isAir();
    }

    private static MemePair findPair(UUID id) {
        for (MemePair p : activePairs) {
            if (p.has(id)) {
                return p;
            }
        }
        return null;
    }

    // ---- 配对 ----

    static final class MemePair {
        final Cat catA, catB;
        final int catAId, catBId;
        final int rollSign, soundId;
        boolean locked = false;

        MemePair(Cat a, Cat b, int rollSign, int soundId) {
            this.catA = a;
            this.catB = b;
            this.catAId = a.getId();
            this.catBId = b.getId();
            this.rollSign = rollSign;
            this.soundId = soundId;
        }

        MinecraftServer server() {
            return catA.level().getServer();
        }

        boolean has(UUID id) {
            return catA.getUUID().equals(id) || catB.getUUID().equals(id);
        }

        boolean alive() {
            return !catA.isRemoved() && !catB.isRemoved() && catA.isAlive() && catB.isAlive();
        }

        /** 两只猫脚下是否都有支撑方块（任一只悬空应释放配对，恢复物理下落） */
        boolean grounded() {
            return hasGroundBelow(catA) && hasGroundBelow(catB);
        }

        Cat other(Cat c) {
            return c == catA ? catB : catA;
        }

        void tick() {
            if (!locked) {
                approachTick();
            } else {
                lockTick();
            }
        }

        private void approachTick() {
            catA.setNoAi(true);
            catB.setNoAi(true);
            catA.setOnGround(true);
            catB.setOnGround(true);

            Vec3 pa = catA.position(), pb = catB.position();
            Vec3 mid = pa.add(pb).scale(0.5);
            Vec3 dirAB = new Vec3(pb.x - pa.x, 0, pb.z - pa.z);
            if (dirAB.lengthSqr() < 1e-4) {
                dirAB = new Vec3(1, 0, 0);
            } else {
                dirAB = dirAB.normalize();
            }

            Vec3 targetA = mid.add(dirAB.scale(-SPLIT));
            Vec3 targetB = mid.add(dirAB.scale(SPLIT));

            moveToward(catA, targetA);
            moveToward(catB, targetB);
            faceEachOther();

            if (catA.distanceTo(catB) <= LOCK_DISTANCE + 0.05) {
                locked = true;
                broadcastTrigger(this);
                DebugLogger.info(MODULE, "进入锁定：%s <-> %s (soundId=%d)", catAId, catBId, soundId);
            }
        }

        private void lockTick() {
            catA.setNoAi(true);
            catB.setNoAi(true);
            catA.setOnGround(true);
            catB.setOnGround(true);

            Vec3 pa = catA.position(), pb = catB.position();
            Vec3 mid = pa.add(pb).scale(0.5);
            Vec3 dirAB = new Vec3(pb.x - pa.x, 0, pb.z - pa.z);
            if (dirAB.lengthSqr() < 1e-4) {
                dirAB = new Vec3(1, 0, 0);
            } else {
                dirAB = dirAB.normalize();
            }

            Vec3 targetA = mid.add(dirAB.scale(-SPLIT));
            Vec3 targetB = mid.add(dirAB.scale(SPLIT));

            // 轻微吸附，避免漂移
            if (catA.position().distanceToSqr(targetA) > 0.0025) {
                catA.setPos(targetA.x, catA.getY(), targetA.z);
            }
            if (catB.position().distanceToSqr(targetB) > 0.0025) {
                catB.setPos(targetB.x, catB.getY(), targetB.z);
            }
            faceEachOther();
        }

        private void moveToward(Cat c, Vec3 target) {
            Vec3 cur = c.position();
            double dx = target.x - cur.x, dz = target.z - cur.z;
            double dist = Math.hypot(dx, dz);
            if (dist <= APPROACH_SPEED) {
                c.setPos(target.x, cur.y, target.z);
            } else {
                c.setPos(cur.x + dx / dist * APPROACH_SPEED, cur.y, cur.z + dz / dist * APPROACH_SPEED);
            }
        }

        private void faceEachOther() {
            float yawA = facingYaw(catA.position(), catB.position());
            float yawB = facingYaw(catB.position(), catA.position());
            catA.setYRot(yawA);
            catA.setYHeadRot(yawA);
            catB.setYRot(yawB);
            catB.setYHeadRot(yawB);
        }
    }

    private static float facingYaw(Vec3 from, Vec3 to) {
        double dx = to.x - from.x, dz = to.z - from.z;
        return (float) Math.toDegrees(Math.atan2(-dx, dz));
    }
}
