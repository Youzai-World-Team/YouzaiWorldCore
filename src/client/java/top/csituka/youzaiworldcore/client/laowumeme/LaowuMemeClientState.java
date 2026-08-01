package top.csituka.youzaiworldcore.client.laowumeme;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SoundInstance;
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
 * 已清理原模组未使用的 {@code SOUND_*} 常量（音频索引统一以
 * {@link LaowuModSounds} / 服务端 {@code LaowuMemeHandler.BUILTIN_SOUND_COUNT} 为准）。
 * </p>
 */
@SuppressWarnings("null")
public final class LaowuMemeClientState {

    private static final String MODULE = "LaowuMemeClientState";

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

    private LaowuMemeClientState() {
    }

    public boolean isActive(int entityId) {
        return active.containsKey(entityId);
    }

    public int getRollSign(int entityId) {
        ActiveCat a = active.get(entityId);
        return a == null ? 0 : a.rollSign;
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
        LaowuAudioPool.refreshImported();
        // 服务端选曲：按 soundId 播放对应内置曲（多人同听）；不可用时回退本地随机
        LaowuAudioPool.PlayTarget target = LaowuAudioPool.pickForSoundId(soundId);
        if (target == null) {
            DebugLogger.info(MODULE, "音频池为空，跳过播放（所有音频均被禁用或无可用曲目）");
            return;
        }
        SoundInstance inst;
        if (target.isImported()) {
            inst = new LaowuImportedSoundInstance(target.importedName, catAId, catBId);
        } else {
            inst = new LaowuSoundInstance(target.event, catAId, catBId);
        }
        sounds.put(key(catAId, catBId), inst);
        mc.getSoundManager().play(inst);
        DebugLogger.info(MODULE, "播放音频: %s (服务端 soundId=%d)",
                target.isImported() ? "imported:" + target.importedName : target.event.location(), soundId);
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
