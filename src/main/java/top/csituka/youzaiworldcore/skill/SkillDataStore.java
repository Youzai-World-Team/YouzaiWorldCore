package top.csituka.youzaiworldcore.skill;

import com.google.gson.JsonObject;
import top.csituka.youzaiworldcore.config.GlobalSettings;
import top.csituka.youzaiworldcore.config.JsonFileStore;
import top.csituka.youzaiworldcore.config.ModPaths;

import java.nio.file.Path;

/**
 * 冒险等级 / 属性加点两张表共用的数据文件容器。
 * <p>
 * 文件位置：{@code yzwc/server/data/skill_module/data.json}，内部分两块：
 * </p>
 *
 * <pre>
 * {
 *   "levels":     { "&lt;玩家UUID&gt;": { ... } },
 *   "attributes": { "&lt;玩家UUID&gt;": { ... } }
 * }
 * </pre>
 *
 * <p>
 * {@link PlayerLevelStorage} 与 {@link PlayerAttributeStorage} 各写各的块，
 * 写入时只替换自己那一块，另一块保持内存中已读到的内容不变。
 * 所有方法都是 {@code synchronized} 的，两张表的写盘互不覆盖。
 * </p>
 */
final class SkillDataStore {

    /** 冒险等级数据块 */
    static final String KEY_LEVELS = "levels";
    /** 属性加点数据块 */
    static final String KEY_ATTRIBUTES = "attributes";

    private static final JsonFileStore STORE =
            new JsonFileStore(ModPaths.serverDataFile(GlobalSettings.SKILL_MODULE));

    static {
        // 新开服 / 坏文件恢复时的默认内容：两张空表
        STORE.setDefaultsWriter(() -> {
            STORE.putSection(KEY_LEVELS, new JsonObject());
            STORE.putSection(KEY_ATTRIBUTES, new JsonObject());
        });
    }

    private SkillDataStore() {
    }

    /** @return 数据文件路径 */
    static Path file() {
        return STORE.file();
    }

    /** 强制从磁盘重新读取整份数据文件（不存在则写出默认的空表；供各表的 loadFromDisk 调用）。 */
    static synchronized void refresh() {
        ModPaths.ensureDir(ModPaths.serverData(GlobalSettings.SKILL_MODULE));
        STORE.loadOrCreateDefaults();
    }

    /**
     * 读取某一块数据。
     *
     * @param key {@link #KEY_LEVELS} 或 {@link #KEY_ATTRIBUTES}
     * @return 该块的 JSON 对象；块不存在或为空时返回 null
     */
    static synchronized JsonObject read(String key) {
        if (!STORE.isLoaded()) {
            refresh();
        }
        JsonObject section = STORE.section(key).raw();
        return section.size() == 0 ? null : section;
    }

    /**
     * 覆盖某一块数据并立刻整份落盘。
     *
     * @param key   {@link #KEY_LEVELS} 或 {@link #KEY_ATTRIBUTES}
     * @param value 该块的新内容
     */
    static synchronized void write(String key, JsonObject value) {
        if (!STORE.isLoaded()) {
            refresh();
        }
        STORE.putSection(key, value);
        STORE.save();
    }
}
