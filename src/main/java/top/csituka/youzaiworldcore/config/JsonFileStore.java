package top.csituka.youzaiworldcore.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 「先按功能分类，再写功能配置」的 JSON 文件容器。
 * <p>
 * 一个 {@code JsonFileStore} 对应磁盘上的一个 {@code .json} 文件，
 * 根节点固定是一个对象，其每个键是一个<b>模块名</b>，值是该模块自己的配置对象：
 * </p>
 *
 * <pre>
 * {
 *   "pet_module":  { "auto_backup_enabled": true, "backup_interval_seconds": 600 },
 *   "afk_module":  { "enabled": true, "threshold_seconds": 300 }
 * }
 * </pre>
 *
 * <p>
 * 取模块分节用 {@link #section(String)}，其返回的 {@link ConfigSection} 直接挂在根对象上，
 * 因此对它的所有写入都是「活的」，最后调用一次 {@link #save()} 即整体落盘。
 * </p>
 * <p>
 * <b>默认内容与错误恢复</b>：通过 {@link #setDefaultsWriter} 注册「把默认内容填进本容器」的动作后，
 * 文件缺失时 {@link #loadOrCreateDefaults()} 会直接写出一份完整默认配置；
 * 读到非法内容时 {@link #fail} 会把坏文件改名为 {@code .error}、在原路径重建默认配置，
 * 然后打印现场并崩溃退出。
 * </p>
 */
@SuppressWarnings("null")
public final class JsonFileStore {

    private static final Logger LOGGER = LoggerFactory.getLogger("YouzaiWorldCore/Config");

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final Path file;
    private JsonObject root = new JsonObject();
    private boolean loaded = false;

    /** 把默认内容填进本容器的动作（内部应调用 {@link #section}/{@link #putSection} 写值），可为 null */
    private Runnable defaultsWriter;

    /** 正在做错误恢复：此期间的写盘失败只记日志，不再触发新的崩溃流程 */
    private boolean recovering = false;

    /** 最近一次写盘是否成功 */
    private boolean lastWriteOk = false;

    /**
     * @param file 该容器对应的 JSON 文件路径
     */
    public JsonFileStore(Path file) {
        this.file = file;
    }

    /** @return 该容器对应的文件路径 */
    public Path file() {
        return file;
    }

    /** @return 是否已执行过 {@link #load()} */
    public boolean isLoaded() {
        return loaded;
    }

    /** @return 根对象是否为空（没有任何模块分节） */
    public boolean isEmpty() {
        return root.size() == 0;
    }

    /**
     * 注册「把默认内容填进本容器」的动作。
     * <p>
     * 该动作会在<b>清空根对象之后</b>被调用，只需往里写默认值即可，不必自己落盘。
     * 未注册时，默认内容视为空对象 <code>{}</code>。
     * </p>
     *
     * @param writer 默认内容写入动作
     */
    public void setDefaultsWriter(Runnable writer) {
        this.defaultsWriter = writer;
    }

    // ===== 加载 / 保存 =====

    /**
     * 从磁盘读取。文件不存在时保持空内容（不写盘）。
     * <p>
     * 文件存在但内容不是合法 JSON 对象时，隔离坏文件、重建默认配置，然后崩溃退出。
     * </p>
     */
    public void load() {
        loaded = true;
        if (!Files.isRegularFile(file)) {
            root = new JsonObject();
            return;
        }

        String text;
        try {
            text = Files.readString(file);
        } catch (IOException e) {
            fail(null, "读取文件失败：" + e.getMessage(), e);
            return; // 不可达，fail 一定抛异常
        }

        if (text.isBlank()) {
            root = new JsonObject();
            return;
        }

        JsonElement parsed;
        try {
            parsed = JsonParser.parseString(text);
        } catch (RuntimeException e) {
            fail(null, "不是合法的 JSON：" + e.getMessage(), e);
            return; // 不可达
        }

        if (parsed == null || !parsed.isJsonObject()) {
            fail(null, "根节点必须是 JSON 对象 { ... }，实际是 " + describe(parsed));
            return; // 不可达
        }

        root = parsed.getAsJsonObject();

        // 根节点下每一个键都必须是「模块名 -> 模块配置对象」
        for (String key : new LinkedHashSet<>(root.keySet())) {
            JsonElement value = root.get(key);
            if (value == null || !value.isJsonObject()) {
                fail(key, "模块分节必须是 JSON 对象 { ... }，实际是 " + describe(value)
                        + "。所有配置都要先按功能分类再写配置。");
            }
        }
    }

    /**
     * 从磁盘读取；文件不存在时（新开服）直接写出一份完整的默认配置。
     *
     * @return 本次是否创建了默认文件
     */
    public boolean loadOrCreateDefaults() {
        if (!Files.isRegularFile(file)) {
            LOGGER.info("配置 / 数据文件不存在，写出默认内容：{}", file.toAbsolutePath());
            // 必须先置位：默认内容写入器可能回调 GlobalSettings.section(...)，
            // 那里在未加载时会重新触发 load()，不先置位就会无限递归
            loaded = true;
            writeDefaults();
            return true;
        }
        load();
        return false;
    }

    /** 清空内存中的内容（不落盘）。 */
    public void reset() {
        root = new JsonObject();
    }

    /**
     * 用默认内容覆盖本容器并落盘。
     *
     * @return 是否成功写盘
     */
    public boolean writeDefaults() {
        reset();
        if (defaultsWriter != null) {
            defaultsWriter.run();
        }
        save();
        return lastWriteOk;
    }

    /** 把内存中的内容整体写回磁盘（自动创建父目录）。 */
    public void save() {
        Path parent = file.getParent();
        if (parent != null && !Files.isDirectory(parent)) {
            try {
                Files.createDirectories(parent);
            } catch (IOException e) {
                lastWriteOk = false;
                if (recovering) {
                    LOGGER.error("重建默认配置时无法创建目录 {}：{}", parent, e.getMessage());
                    return;
                }
                fail(null, "无法创建父目录 " + parent + "：" + e.getMessage(), e);
                return; // 不可达
            }
        }
        try {
            Files.writeString(file, GSON.toJson(root));
            lastWriteOk = true;
        } catch (IOException e) {
            lastWriteOk = false;
            if (recovering) {
                LOGGER.error("重建默认配置文件失败 {}：{}", file, e.getMessage());
                return;
            }
            fail(null, "写入文件失败：" + e.getMessage(), e);
        }
    }

    /** 删除该文件（用于注销账户等场景），文件不存在时静默返回。 */
    public void deleteFile() {
        try {
            Files.deleteIfExists(file);
        } catch (IOException e) {
            ConfigCrash.fail(file, null, "删除文件失败：" + e.getMessage(), e);
        }
        root = new JsonObject();
    }

    // ===== 错误处理 =====

    /**
     * 报告本文件的配置错误：隔离坏文件 → 重建默认配置 → 打印现场 → 崩溃退出。
     *
     * @param jsonPath 出问题的位置，形如 {@code afk_module.threshold_seconds}；根节点传 null
     * @param reason   人类可读的原因描述
     */
    public void fail(String jsonPath, String reason) {
        fail(jsonPath, reason, null);
    }

    /**
     * 报告本文件的配置错误（带原始异常）。
     *
     * @param jsonPath 出问题的位置；根节点传 null
     * @param reason   人类可读的原因描述
     * @param cause    触发本次失败的原始异常，可为 null
     */
    public void fail(String jsonPath, String reason, Throwable cause) {
        Path quarantined = ConfigCrash.quarantine(file);
        Path regenerated = null;
        // 只有成功把坏文件挪走，才允许在原路径写默认配置 —— 绝不覆盖管理员的现场
        if (quarantined != null) {
            recovering = true;
            try {
                if (writeDefaults()) {
                    regenerated = file;
                }
            } catch (RuntimeException e) {
                LOGGER.error("重建默认配置文件时出错 {}：{}", file, e.getMessage());
            } finally {
                recovering = false;
            }
        }
        ConfigCrash.failAfterRecovery(file, jsonPath, reason, cause, quarantined, regenerated);
    }

    // ===== 模块分节 =====

    /**
     * 取模块分节；不存在时创建一个空分节并挂到根对象上。
     *
     * @param moduleName 模块名，如 {@code pet_module}
     */
    public ConfigSection section(String moduleName) {
        JsonElement existing = root.get(moduleName);
        if (existing != null && !existing.isJsonObject()) {
            fail(moduleName, "模块分节必须是 JSON 对象 { ... }，实际是 " + describe(existing));
        }
        JsonObject obj;
        if (existing == null) {
            obj = new JsonObject();
            root.add(moduleName, obj);
        } else {
            obj = existing.getAsJsonObject();
        }
        return new ConfigSection(this, moduleName, obj);
    }

    /** @return 该模块分节是否已存在（不会创建） */
    public boolean hasSection(String moduleName) {
        JsonElement existing = root.get(moduleName);
        return existing != null && existing.isJsonObject();
    }

    /**
     * 整体替换某个模块分节（不落盘）。
     * <p>用于把一整张 Gson 序列化出来的表塞进某个分节。</p>
     *
     * @param moduleName 模块名
     * @param value      新的分节内容
     */
    public void putSection(String moduleName, JsonObject value) {
        root.add(moduleName, value == null ? new JsonObject() : value);
    }

    /** 移除某个模块分节（不落盘）。 */
    public void removeSection(String moduleName) {
        root.remove(moduleName);
    }

    /** @return 当前所有模块分节名（快照） */
    public Set<String> moduleNames() {
        return new LinkedHashSet<>(root.keySet());
    }

    // ===== 内部 =====

    /** 人类可读的 JSON 值类型描述，用于报错 */
    static String describe(JsonElement element) {
        if (element == null || element.isJsonNull()) {
            return "null";
        }
        if (element.isJsonObject()) {
            return "对象";
        }
        if (element.isJsonArray()) {
            return "数组";
        }
        var prim = element.getAsJsonPrimitive();
        if (prim.isBoolean()) {
            return "布尔值 " + prim.getAsString();
        }
        if (prim.isNumber()) {
            return "数字 " + prim.getAsString();
        }
        return "字符串 \"" + prim.getAsString() + "\"";
    }
}
