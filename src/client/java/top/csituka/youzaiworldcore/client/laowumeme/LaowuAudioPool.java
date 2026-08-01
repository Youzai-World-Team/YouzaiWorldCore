package top.csituka.youzaiworldcore.client.laowumeme;

import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundEvent;
import top.csituka.youzaiworldcore.util.DebugLogger;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 客户端本地音频池：管理「可播放」的音频集合 + 每条音频的启用/禁用状态。
 * <ul>
 *   <li>内置曲：三首注册表 SoundEvent（laowu2 / qiliang / zhanhou），开箱即用。</li>
 *   <li>用户导入：扫描 {@code config/youzaiworldcore/laowu_meme/sounds/*.ogg}，
 *       触发整活时与内置曲一起可被随机播放，直接从磁盘读取（无需 F3+T、不进资源包）。</li>
 *   <li>启用/禁用：每条音频可单独启用或禁用，状态持久化到
 *       {@code config/youzaiworldcore/laowu_meme/enabled.properties}；随机抽取只从启用条目中选。</li>
 * </ul>
 * <p>
 * 服务端选曲：{@link #pickForSoundId(int)} 按服务端下发的 {@code soundId} 返回对应内置曲，
 * 使全体玩家同听同一首（实现多人同听）；仅当该曲被本机禁用或 soundId 越界时，
 * 回退到 {@link #random()} 本地随机（含导入曲，各听各的梗）。
 * </p>
 */
public final class LaowuAudioPool {

    public static final String MODULE = "LaowuAudioPool";

    /** 内置曲：sound 注册名 -> SoundEvent（保持顺序：laowu2 / qiliang / zhanhou，即服务端 soundId 索引） */
    private static final Map<String, SoundEvent> BUILTINS = new LinkedHashMap<>();

    private static final List<String> IMPORTED = new ArrayList<>();
    private static final Set<String> disabledKeys = new LinkedHashSet<>();

    private LaowuAudioPool() {
    }

    public static void init() {
        BUILTINS.clear();
        BUILTINS.put("laowu2", LaowuModSounds.LAOWU2);
        BUILTINS.put("qiliang", LaowuModSounds.QILIANG);
        BUILTINS.put("zhanhou", LaowuModSounds.ZHANHOU);

        disabledKeys.clear();
        refreshImported();  // 先扫磁盘，确保 enabled.properties 能覆盖所有已知 key
        // 从磁盘读回禁用状态：load 只 put 文件里有的 key（即上次禁用的）
        Map<String, Boolean> loaded = new LinkedHashMap<>();
        LaowuEnabledConfig.load(loaded);
        for (Map.Entry<String, Boolean> e : loaded.entrySet()) {
            if (Boolean.FALSE.equals(e.getValue())) {
                disabledKeys.add(e.getKey());
            }
        }
        DebugLogger.info(MODULE, "音频池初始化完成: 内置 %d 首, 导入 %d 条, 禁用 %d 条",
                BUILTINS.size(), IMPORTED.size(), disabledKeys.size());
    }

    /** 重新扫描导入目录（去掉 .ogg 后缀作为显示/匹配名），排序后存入 IMPORTED */
    public static void refreshImported() {
        IMPORTED.clear();
        File dir = getSoundsDir();
        if (!dir.exists()) {
            return;
        }
        File[] files = dir.listFiles((d, n) -> n.toLowerCase().endsWith(".ogg"));
        if (files == null) {
            return;
        }
        for (File f : files) {
            IMPORTED.add(stripExt(f.getName()));
        }
        Collections.sort(IMPORTED);
    }

    public static List<String> importedNames() {
        return new ArrayList<>(IMPORTED);
    }

    public static int importedCount() {
        return IMPORTED.size();
    }

    public static int builtinCount() {
        return BUILTINS.size();
    }

    /** 内置 key 列表（顺序与 BUILTINS 一致，即 soundId 顺序），用于按服务端选曲索引 */
    public static List<String> builtinKeys() {
        return new ArrayList<>(BUILTINS.keySet());
    }

    /** 导入 key 列表（顺序与 IMPORTED 一致） */
    public static List<String> importedKeys() {
        List<String> keys = new ArrayList<>(IMPORTED.size());
        for (String n : IMPORTED) {
            keys.add("imported:" + n);
        }
        return keys;
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

    private static void persist() {
        Map<String, Boolean> map = new LinkedHashMap<>();
        // 把所有 known key 都写一遍（true/false 都写），保证磁盘文件反映完整状态
        for (String k : BUILTINS.keySet()) {
            map.put("builtin:" + k, isEnabled("builtin:" + k));
        }
        for (String n : IMPORTED) {
            map.put("imported:" + n, isEnabled("imported:" + n));
        }
        LaowuEnabledConfig.save(map);
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
     * @return 播放目标；若启用池为空则可能为 null
     */
    public static PlayTarget pickForSoundId(int soundId) {
        List<String> keys = builtinKeys();
        if (soundId >= 0 && soundId < keys.size()) {
            String key = "builtin:" + keys.get(soundId);
            if (isEnabled(key)) {
                return PlayTarget.builtin(BUILTINS.get(keys.get(soundId)));
            }
            DebugLogger.info(MODULE, "服务端选曲 soundId=%d 已被本机禁用，回退本地随机", soundId);
        } else {
            DebugLogger.warn(MODULE, "服务端选曲 soundId=%d 越界（内置 %d 首），回退本地随机", soundId, keys.size());
        }
        return random();
    }

    /** 从 enabled + imported 合并池随机抽一段（只抽启用的）；全空返回 null */
    public static PlayTarget random() {
        List<PlayTarget> pool = new ArrayList<>();
        for (Map.Entry<String, SoundEvent> e : BUILTINS.entrySet()) {
            if (isEnabled("builtin:" + e.getKey())) {
                pool.add(PlayTarget.builtin(e.getValue()));
            }
        }
        for (String n : IMPORTED) {
            if (isEnabled("imported:" + n)) {
                pool.add(PlayTarget.imported(n));
            }
        }
        if (pool.isEmpty()) {
            return null;
        }
        return pool.get((int) (Math.random() * pool.size()));
    }

    /** 一次播放选择：要么一段内置 SoundEvent，要么一个导入音频名（base name，无 .ogg） */
    public static final class PlayTarget {
        public final SoundEvent event;
        public final String importedName;

        private PlayTarget(SoundEvent event, String importedName) {
            this.event = event;
            this.importedName = importedName;
        }

        public static PlayTarget builtin(SoundEvent event) {
            return new PlayTarget(event, null);
        }

        public static PlayTarget imported(String name) {
            return new PlayTarget(null, name);
        }

        public boolean isImported() {
            return importedName != null;
        }
    }

    public static File getSoundsDir() {
        return new File(getConfigDir(), "sounds");
    }

    public static File getConfigDir() {
        return new File(Minecraft.getInstance().gameDirectory, "config/youzaiworldcore/laowu_meme");
    }

    private static String stripExt(String name) {
        if (name.toLowerCase().endsWith(".ogg")) {
            return name.substring(0, name.length() - 4);
        }
        return name;
    }
}
