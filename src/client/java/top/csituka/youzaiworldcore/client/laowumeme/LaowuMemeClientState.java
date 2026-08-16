package top.csituka.youzaiworldcore.client.laowumeme;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import top.csituka.youzaiworldcore.util.DebugLogger;

import java.util.HashMap;
import java.util.Map;

/**
 * 客户端状态：收包驱动。记录哪些猫在对头效果中（携带音频 id 与歪头方向），
 * 并管理循环音频的播放/停止。渲染 mixin 通过 {@link #isActive} / {@link #getRollSign} 读取。
 * <p>
 * 服务端选曲：{@link #onTrigger} 携带的 {@code soundId} 会传给
 * {@link LaowuAudioPool#pickForSoundId(int)}，按服务端选定的内置曲播放（全体玩家同听），
 * 仅当该曲被本机禁用或越界时回退本地随机。
 * </p>
 * <p>
 * 愤怒粒子：老吴状态期间，每 {@link #PARTICLE_INTERVAL_TICKS} tick 在两猫中点附近生成
 * {@link ParticleTypes#ANGRY_VILLAGER} 粒子（村民工作方块被挖掉的生气符号），
 * 由 {@link #tick()}（挂接客户端 END_CLIENT_TICK）驱动，直到状态结束（stop 包清空 active）。
 * 采用客户端本地 {@code Level.addParticle}，零网络开销且各端效果一致。
 * </p>
 * <p>
 * 已清理原模组未使用的 {@code SOUND_*} 常量（音频索引统一以
 * {@link LaowuModSounds} / 服务端 {@code LaowuMemeHandler.BUILTIN_SOUND_COUNT} 为准）。
 * </p>
 */
@SuppressWarnings("null")
public final class LaowuMemeClientState {

    private static final String MODULE = "LaowuMemeClientState";

    /** 粒子生成节流：每 N tick 生成一批（每批 1 个），避免粒子过密。
     *  12 tick = 0.6 秒一个，保持"持续冒出"的存在感但不密集（3 tick 实测过于频繁）。 */
    private static final int PARTICLE_INTERVAL_TICKS = 12;
    /** 粒子向上飘动速度（y 分量） */
    private static final double PARTICLE_RISE_SPEED = 0.08;

    private static final LaowuMemeClientState INSTANCE = new LaowuMemeClientState();

    public static LaowuMemeClientState get() {
        return INSTANCE;
    }

    public static final class ActiveCat {
        public int partnerId;
        public int soundId;
        public int rollSign;
    }

    private final Map<Integer, ActiveCat> active = new HashMap<>();
    private final Map<String, SoundInstance> sounds = new HashMap<>();
    private int particleTickCounter = 0;

    private LaowuMemeClientState() {
    }

    public boolean isActive(int entityId) {
        return active.containsKey(entityId);
    }

    public int getRollSign(int entityId) {
        ActiveCat a = active.get(entityId);
        return a == null ? 0 : a.rollSign;
    }

    /** 客户端每 tick 驱动（由 {@code Client.onClientTick} 调用）：生成愤怒粒子 */
    public void tick() {
        if (active.isEmpty()) {
            return;
        }
        particleTickCounter++;
        if (particleTickCounter < PARTICLE_INTERVAL_TICKS) {
            return;
        }
        particleTickCounter = 0;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        // 遍历 active：同一对猫在 map 中有两条记录（catA→catB 与 catB→catA），按 key 去重只生成一次
        java.util.Set<String> emitted = new java.util.HashSet<>();
        for (Map.Entry<Integer, ActiveCat> e : active.entrySet()) {
            int aId = e.getKey();
            int bId = e.getValue().partnerId;
            String k = key(aId, bId);
            if (!emitted.add(k)) {
                continue;
            }
            Entity ea = mc.level.getEntity(aId);
            Entity eb = mc.level.getEntity(bId);
            if (ea == null || eb == null) {
                continue;
            }
            Vec3 mid = ea.position().add(eb.position()).scale(0.5);
            // 粒子生成在两猫中点【头顶上方一点】（y 取脚底 +0.8~1.2）。
            // 猫高约 0.7 格：+0.8 起即高于猫背/猫头，不会被贴脸放大的模型遮挡，也不会过高。
            // x/z 保持中点附近小幅随机偏移（±0.4），粒子缓慢上飘。
            double px = mid.x + (Math.random() - 0.5) * 0.8;
            double py = mid.y + 0.8 + Math.random() * 0.4;
            double pz = mid.z + (Math.random() - 0.5) * 0.8;
            mc.level.addParticle(ParticleTypes.ANGRY_VILLAGER, px, py, pz,
                    0.0, PARTICLE_RISE_SPEED, 0.0);
            DebugLogger.debug(MODULE, "生成愤怒粒子: (%.2f, %.2f, %.2f) 配对 %d<->%d",
                    px, py, pz, aId, bId);
        }
    }

    /** 收到服务端 trigger 包：记录两只猫并按服务端选曲起音乐 */
    public void onTrigger(int catAId, int catBId, int soundId, int rollSign) {
        DebugLogger.info(MODULE, "收到 trigger: %s <-> %s (soundId=%d, rollSign=%d)",
                catAId, catBId, soundId, rollSign);
        ActiveCat sa = new ActiveCat();
        sa.partnerId = catBId;
        sa.soundId = soundId;
        sa.rollSign = rollSign;
        ActiveCat sb = new ActiveCat();
        sb.partnerId = catAId;
        sb.soundId = soundId;
        sb.rollSign = rollSign;
        active.put(catAId, sa);
        active.put(catBId, sb);
        startSound(catAId, catBId, soundId);
    }

    /** 收到服务端 stop 包：清状态 + 停音乐 */
    public void onStop(int catAId, int catBId) {
        DebugLogger.info(MODULE, "收到 stop: %s <-> %s", catAId, catBId);
        active.remove(catAId);
        active.remove(catBId);
        stopSound(catAId, catBId);
    }

    private String key(int a, int b) {
        return Math.min(a, b) + "-" + Math.max(a, b);
    }

    private void startSound(int catAId, int catBId, int soundId) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            return;
        }
        Vec3 mid = midOf(catAId, catBId);
        if (mid == null) {
            return;
        }
        // 同一对猫重复触发时，先停掉旧实例再起新的，避免两实例叠加 / 旧实例泄漏。
        SoundInstance old = sounds.get(key(catAId, catBId));
        if (old != null) {
            mc.getSoundManager().stop(old);
        }
        // 服务端选曲：按 soundId 播放对应内置曲（多人同听）；不可用时回退本地随机
        net.minecraft.sounds.SoundEvent event = LaowuAudioPool.pickForSoundId(soundId);
        if (event == null) {
            DebugLogger.info(MODULE, "音频池为空，跳过播放（所有音频均被禁用）");
            return;
        }
        SoundInstance inst = new LaowuSoundInstance(event, catAId, catBId);
        sounds.put(key(catAId, catBId), inst);
        mc.getSoundManager().play(inst);
        DebugLogger.info(MODULE, "播放音频: %s (服务端 soundId=%d)", event.location(), soundId);
    }

    private void stopSound(int catAId, int catBId) {
        SoundInstance inst = sounds.remove(key(catAId, catBId));
        if (inst != null) {
            Minecraft.getInstance().getSoundManager().stop(inst);
        }
    }

    private Vec3 midOf(int a, int b) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return null;
        }
        Entity ea = mc.level.getEntity(a), eb = mc.level.getEntity(b);
        if (ea == null || eb == null) {
            return null;
        }
        return ea.position().add(eb.position()).scale(0.5);
    }
}
