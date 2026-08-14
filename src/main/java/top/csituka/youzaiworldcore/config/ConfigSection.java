package top.csituka.youzaiworldcore.config;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * {@link JsonFileStore} 中的一个模块分节，形如 {@code "pet_module": { ... }}。
 * <p>
 * 所有 {@code getXxx} 都是<b>强类型</b>的：键缺失时返回调用方给的默认值，
 * 键存在但类型不对时直接走 {@link ConfigCrash#fail}（打印文件 + 具体键名后崩溃退出），
 * 绝不做「读不出来就当默认值」的静默降级。
 * </p>
 * <p>
 * 本对象直接挂在所属文件的根对象上，因此 {@code setXxx} 是即时生效的内存写入；
 * 落盘需要调用所属 {@link JsonFileStore#save()}。
 * </p>
 */
@SuppressWarnings("null")
public final class ConfigSection {

    private final JsonFileStore owner;
    private final String moduleName;
    private final JsonObject json;

    ConfigSection(JsonFileStore owner, String moduleName, JsonObject json) {
        this.owner = owner;
        this.moduleName = moduleName;
        this.json = json;
    }

    /** @return 模块名，如 {@code pet_module} */
    public String moduleName() {
        return moduleName;
    }

    /** @return 底层 JSON 对象（谨慎使用，绕过类型检查） */
    public JsonObject raw() {
        return json;
    }

    /** @return 该分节是否没有任何键 */
    public boolean isEmpty() {
        return json.size() == 0;
    }

    /** @return 指定键是否存在且不为 null */
    public boolean has(String key) {
        JsonElement e = json.get(key);
        return e != null && !e.isJsonNull();
    }

    /** 移除指定键。 */
    public void remove(String key) {
        json.remove(key);
    }

    /** @return 该分节下的所有键（快照） */
    public Set<String> keys() {
        return new LinkedHashSet<>(json.keySet());
    }

    // ===== 强类型读取 =====

    /** 读布尔值；缺失返回 {@code def}，类型不符则崩溃退出。 */
    public boolean getBoolean(String key, boolean def) {
        JsonElement e = json.get(key);
        if (e == null || e.isJsonNull()) {
            return def;
        }
        if (!e.isJsonPrimitive() || !e.getAsJsonPrimitive().isBoolean()) {
            fail(key, "期望布尔值 true / false，实际是 " + JsonFileStore.describe(e));
        }
        return e.getAsBoolean();
    }

    /** 读整数；缺失返回 {@code def}，类型不符则崩溃退出。 */
    public int getInt(String key, int def) {
        JsonElement e = json.get(key);
        if (e == null || e.isJsonNull()) {
            return def;
        }
        if (!e.isJsonPrimitive() || !e.getAsJsonPrimitive().isNumber()) {
            fail(key, "期望整数，实际是 " + JsonFileStore.describe(e));
        }
        return e.getAsInt();
    }

    /**
     * 读整数并校验范围；越界视为配置错误，崩溃退出。
     *
     * @param min 允许的最小值（含）
     * @param max 允许的最大值（含）
     */
    public int getInt(String key, int def, int min, int max) {
        int value = getInt(key, def);
        if (value < min || value > max) {
            fail(key, "取值 " + value + " 超出允许范围 [" + min + ", " + max + "]");
        }
        return value;
    }

    /** 读长整数；缺失返回 {@code def}，类型不符则崩溃退出。 */
    public long getLong(String key, long def) {
        JsonElement e = json.get(key);
        if (e == null || e.isJsonNull()) {
            return def;
        }
        if (!e.isJsonPrimitive() || !e.getAsJsonPrimitive().isNumber()) {
            fail(key, "期望整数，实际是 " + JsonFileStore.describe(e));
        }
        return e.getAsLong();
    }

    /** 读浮点数；缺失返回 {@code def}，类型不符则崩溃退出。 */
    public double getDouble(String key, double def) {
        JsonElement e = json.get(key);
        if (e == null || e.isJsonNull()) {
            return def;
        }
        if (!e.isJsonPrimitive() || !e.getAsJsonPrimitive().isNumber()) {
            fail(key, "期望数字，实际是 " + JsonFileStore.describe(e));
        }
        return e.getAsDouble();
    }

    /**
     * 读浮点数并校验范围；越界视为配置错误，崩溃退出。
     *
     * @param min 允许的最小值（含）
     * @param max 允许的最大值（含）
     */
    public double getDouble(String key, double def, double min, double max) {
        double value = getDouble(key, def);
        if (Double.isNaN(value) || value < min || value > max) {
            fail(key, "取值 " + value + " 超出允许范围 [" + min + ", " + max + "]");
        }
        return value;
    }

    /** 读字符串；缺失返回 {@code def}，类型不符则崩溃退出。 */
    public String getString(String key, String def) {
        JsonElement e = json.get(key);
        if (e == null || e.isJsonNull()) {
            return def;
        }
        if (!e.isJsonPrimitive() || !e.getAsJsonPrimitive().isString()) {
            fail(key, "期望字符串，实际是 " + JsonFileStore.describe(e));
        }
        return e.getAsString();
    }

    /**
     * 读枚举（忽略大小写）；缺失返回 {@code def}，不是合法枚举名则崩溃退出。
     *
     * @param type 枚举类型
     */
    public <E extends Enum<E>> E getEnum(String key, E def, Class<E> type) {
        String raw = getString(key, null);
        if (raw == null) {
            return def;
        }
        try {
            return Enum.valueOf(type, raw.toUpperCase(Locale.ENGLISH));
        } catch (IllegalArgumentException ex) {
            StringBuilder allowed = new StringBuilder();
            for (E constant : type.getEnumConstants()) {
                if (allowed.length() > 0) {
                    allowed.append(" / ");
                }
                allowed.append(constant.name());
            }
            fail(key, "取值 \"" + raw + "\" 不是合法选项，允许的取值：" + allowed);
            return def; // 不可达
        }
    }

    /** 读字符串数组；缺失返回 {@code def}，元素类型不符则崩溃退出。 */
    public List<String> getStringList(String key, List<String> def) {
        JsonElement e = json.get(key);
        if (e == null || e.isJsonNull()) {
            return def;
        }
        if (!e.isJsonArray()) {
            fail(key, "期望字符串数组 [ ... ]，实际是 " + JsonFileStore.describe(e));
        }
        JsonArray array = e.getAsJsonArray();
        List<String> result = new ArrayList<>(array.size());
        for (int i = 0; i < array.size(); i++) {
            JsonElement item = array.get(i);
            if (item == null || !item.isJsonPrimitive() || !item.getAsJsonPrimitive().isString()) {
                fail(key + "[" + i + "]", "数组元素必须是字符串，实际是 " + JsonFileStore.describe(item));
            }
            result.add(item.getAsString());
        }
        return result;
    }

    /** 读字符串集合（保持顺序、自动去重去空）；缺失返回 {@code def}。 */
    public Set<String> getStringSet(String key, Set<String> def) {
        List<String> list = getStringList(key, null);
        if (list == null) {
            return def;
        }
        Set<String> result = new LinkedHashSet<>();
        for (String item : list) {
            if (item != null && !item.isBlank()) {
                result.add(item);
            }
        }
        return result;
    }

    /** 读子对象；缺失返回 null，类型不符则崩溃退出。 */
    public JsonObject getObject(String key) {
        JsonElement e = json.get(key);
        if (e == null || e.isJsonNull()) {
            return null;
        }
        if (!e.isJsonObject()) {
            fail(key, "期望 JSON 对象 { ... }，实际是 " + JsonFileStore.describe(e));
        }
        return e.getAsJsonObject();
    }

    /** 读数组；缺失返回 null，类型不符则崩溃退出。 */
    public JsonArray getArray(String key) {
        JsonElement e = json.get(key);
        if (e == null || e.isJsonNull()) {
            return null;
        }
        if (!e.isJsonArray()) {
            fail(key, "期望 JSON 数组 [ ... ]，实际是 " + JsonFileStore.describe(e));
        }
        return e.getAsJsonArray();
    }

    // ===== 写入（内存，需调用所属 JsonFileStore.save() 落盘）=====

    public void set(String key, boolean value) {
        json.addProperty(key, value);
    }

    public void set(String key, int value) {
        json.addProperty(key, value);
    }

    public void set(String key, long value) {
        json.addProperty(key, value);
    }

    public void set(String key, double value) {
        json.addProperty(key, value);
    }

    public void set(String key, String value) {
        json.addProperty(key, value);
    }

    public void set(String key, Enum<?> value) {
        json.addProperty(key, value == null ? null : value.name());
    }

    public void set(String key, JsonElement value) {
        json.add(key, value);
    }

    /** 写入字符串集合。 */
    public void setStringCollection(String key, Iterable<String> values) {
        JsonArray array = new JsonArray();
        for (String value : values) {
            array.add(new JsonPrimitive(value));
        }
        json.add(key, array);
    }

    // ===== 报错 =====

    /**
     * 以本分节为上下文报告配置错误：隔离坏文件 → 重建默认配置 → 打印现场 → 崩溃退出。
     *
     * @param key    出问题的键名（可带下标，如 {@code list[2]}）
     * @param reason 原因描述
     */
    public void fail(String key, String reason) {
        owner.fail(moduleName + "." + key, reason);
    }
}
