package top.csituka.youzaiworldcore.client.laowumeme;

import net.minecraft.sounds.SoundEvent;
import top.csituka.youzaiworldcore.client.config.ClientGlobalSettings;
import top.csituka.youzaiworldcore.config.ConfigSection;
import top.csituka.youzaiworldcore.util.DebugLogger;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 客户端本地音频池：管理三首内置曲 + 每首的启用/禁用状态。
 * <ul>
 *   <li>内置曲：三首注册表 SoundEvent（laowu2 / qiliang / zhanhou），开箱即用，顺序即服务端 soundId 索引。</li>
 *   <li>启用/禁用：每首可单独开关，禁用列表持久化到
 *       {@code yzwc/client/global_settings.json} 的 {@code laowu_meme_module.disabled_sounds}；
 *       随机抽取只从启用条目中选。</li>
 * </ul>
 * <p>
 * 服务端选曲：{@link #pickForSoundId(int)} 按服务端下发的 {@code soundId} 返回对应内置曲，
 * 使全体玩家同听同一首（实现多人同听）；仅当该曲被本机禁用或 soundId 越界时，
 * 回退到 {@link #random()} 本地随机。
 * </p>
 */
public final class LaowuAudioPool {

    public static final String MODULE = "LaowuAudioPool";

    /** 禁用列表在配置分节里的键名 */
    private static final String KEY_DISABLED_SOUNDS = "disabled_sounds";

    /** 内置曲：sound 注册名 -> SoundEvent（保持顺序：laowu2 / qiliang / zhanhou，即服务端 soundId 索引） */
    private static final Map<String, SoundEvent> BUILTINS = new LinkedHashMap<>();

    /** 被本机禁用的曲目名 */
    private static final Set<String> disabledKeys = new LinkedHashSet<>();

    private LaowuAudioPool() {
    }

    public static void init() {
        BUILTINS.clear();
        BUILTINS.put("laowu2", LaowuModSounds.LAOWU2);
        BUILTINS.put("qiliang", LaowuModSounds.QILIANG);
        BUILTINS.put("zhanhou", LaowuModSounds.ZHANHOU);

        disabledKeys.clear();
        ConfigSection section = section();
        if (section.isEmpty()) {
            writeDefaults();
        } else {
            for (String key : section.getStringSet(KEY_DISABLED_SOUNDS, Set.of())) {
                if (!BUILTINS.containsKey(key)) {
                    section.fail(KEY_DISABLED_SOUNDS,
                            "未知的曲目名 \"" + key + "\"，可用取值：" + String.join(" / ", BUILTINS.keySet()));
                }
                disabledKeys.add(key);
            }
        }

        DebugLogger.info(MODULE, "音频池初始化完成: 内置 %d 首, 禁用 %d 首",
                BUILTINS.size(), disabledKeys.size());
    }

    public static int builtinCount() {
        return BUILTINS.size();
    }

    /** 内置 key 列表（顺序与 BUILTINS 一致，即 soundId 顺序），用于按服务端选曲索引 */
    public static List<String> builtinKeys() {
        return new ArrayList<>(BUILTINS.keySet());
    }

    public static boolean isEnabled(String key) {
        return !disabledKeys.contains(key);
    }

    public static void setEnabled(String key, boolean enabled) {
        if (enabled) {
            disabledKeys.remove(key);
        } else {
            disabledKeys.add(key);
        }
        persist();
    }

    /** 翻转 enabled 状态，返回新值 */
    public static boolean toggleEnabled(String key) {
        boolean now = !isEnabled(key);
        setEnabled(key, now);
        return now;
    }

    /**
     * 按服务端下发的 soundId 选曲（服务端权威选曲，实现全体玩家同听）。
     * <ul>
     *   <li>soundId 合法（0..builtinCount-1）且对应内置曲已启用 → 返回该内置曲；</li>
     *   <li>否则（越界 / 该曲被本机禁用）→ 回退 {@link #random()} 本地随机，
     *       保证整活时总有声音、且尊重玩家个人禁用偏好。</li>
     * </ul>
     *
     * @param soundId 服务端 trigger 包携带的曲目索引（0=laowu2, 1=qiliang, 2=zhanhou）
     * @return 要播放的音效；若全部被禁用则返回 null
     */
    public static SoundEvent pickForSoundId(int soundId) {
        List<String> keys = builtinKeys();
        if (soundId >= 0 && soundId < keys.size()) {
            String key = keys.get(soundId);
            if (isEnabled(key)) {
                return BUILTINS.get(key);
            }
            DebugLogger.info(MODULE, "服务端选曲 soundId=%d 已被本机禁用，回退本地随机", soundId);
        } else {
            DebugLogger.warn(MODULE, "服务端选曲 soundId=%d 越界（内置 %d 首），回退本地随机", soundId, keys.size());
        }
        return random();
    }

    /** 从启用的曲目里随机抽一首；全部禁用时返回 null */
    public static SoundEvent random() {
        List<SoundEvent> pool = new ArrayList<>();
        for (Map.Entry<String, SoundEvent> e : BUILTINS.entrySet()) {
            if (isEnabled(e.getKey())) {
                pool.add(e.getValue());
            }
        }
        if (pool.isEmpty()) {
            return null;
        }
        return pool.get((int) (Math.random() * pool.size()));
    }

    // ===== 持久化 =====

    /** @return 该模块的配置分节 */
    private static ConfigSection section() {
        return ClientGlobalSettings.section(ClientGlobalSettings.LAOWU_MEME_MODULE);
    }

    /** 重置为默认值（全部启用）并写入 {@code laowu_meme_module} 分节（首次安装 / 坏文件恢复用） */
    public static void writeDefaults() {
        disabledKeys.clear();
        persist();
    }

    /** 把当前禁用列表写回配置文件 */
    private static void persist() {
        section().setStringCollection(KEY_DISABLED_SOUNDS, disabledKeys);
        ClientGlobalSettings.save();
    }
}
