package top.csituka.youzaiworldcore.client.laowumeme;

import net.minecraft.sounds.SoundEvent;
import top.csituka.youzaiworldcore.util.DebugLogger;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 客户端本地音频池：管理三首内置曲。
 * <ul>
 *   <li>内置曲：三首注册表 SoundEvent（laowu2 / qiliang / zhanhou），开箱即用，顺序即服务端 soundId 索引。</li>
 *   <li>曲目<b>不可被玩家单独禁用</b>（已移除 {@code laowu_meme_module.disabled_sounds} 配置）；
 *       服务端选曲时所有玩家同听同一首。</li>
 * </ul>
 * <p>
 * 服务端选曲：{@link #pickForSoundId(int)} 按服务端下发的 {@code soundId} 返回对应内置曲，
 * 使全体玩家同听同一首（实现多人同听）；仅当 soundId 越界时回退 {@link #random()} 本地随机。
 * </p>
 */
public final class LaowuAudioPool {

    public static final String MODULE = "LaowuAudioPool";

    /** 内置曲：sound 注册名 -> SoundEvent（保持顺序：laowu2 / qiliang / zhanhou，即服务端 soundId 索引） */
    private static final Map<String, SoundEvent> BUILTINS = new LinkedHashMap<>();

    private LaowuAudioPool() {
    }

    /** 注册三首内置曲（幂等；多次调用先清空再重建） */
    public static void init() {
        BUILTINS.clear();
        BUILTINS.put("laowu2", LaowuModSounds.LAOWU2);
        BUILTINS.put("qiliang", LaowuModSounds.QILIANG);
        BUILTINS.put("zhanhou", LaowuModSounds.ZHANHOU);

        DebugLogger.info(MODULE, "音频池初始化完成: 内置 %d 首（曲目不可被玩家禁用）", BUILTINS.size());
    }

    public static int builtinCount() {
        return BUILTINS.size();
    }

    /** 内置 key 列表（顺序与 BUILTINS 一致，即 soundId 顺序），用于按服务端选曲索引 */
    public static List<String> builtinKeys() {
        return new ArrayList<>(BUILTINS.keySet());
    }

    /**
     * 按服务端下发的 soundId 选曲（服务端权威选曲，实现全体玩家同听）。
     * <ul>
     *   <li>soundId 合法（0..builtinCount-1）→ 直接返回该内置曲；</li>
     *   <li>越界 → 回退 {@link #random()} 本地随机，保证整活时总有声音。</li>
     * </ul>
     *
     * @param soundId 服务端 trigger 包携带的曲目索引（0=laowu2, 1=qiliang, 2=zhanhou）
     * @return 要播放的音效；若音频池未初始化则返回 null
     */
    public static SoundEvent pickForSoundId(int soundId) {
        List<String> keys = builtinKeys();
        if (soundId >= 0 && soundId < keys.size()) {
            return BUILTINS.get(keys.get(soundId));
        }
        DebugLogger.warn(MODULE, "服务端选曲 soundId=%d 越界（内置 %d 首），回退本地随机", soundId, keys.size());
        return random();
    }

    /** 从全部内置曲里随机抽一首；音频池为空时返回 null（仅未初始化时出现） */
    public static SoundEvent random() {
        if (BUILTINS.isEmpty()) {
            return null;
        }
        List<SoundEvent> pool = new ArrayList<>(BUILTINS.values());
        return pool.get((int) (Math.random() * pool.size()));
    }
}
