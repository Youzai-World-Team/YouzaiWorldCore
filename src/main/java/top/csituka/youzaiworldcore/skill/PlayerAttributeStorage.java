package top.csituka.youzaiworldcore.skill;

import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import top.csituka.youzaiworldcore.util.DebugLogger;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * 玩家属性加点数据持久化存储。
 * <p>
 * 数据文件：{@code yzwc/server/data/skill_module/data.json} 的 {@code attributes} 块。
 * 格式：Map&lt;String, PlayerAttributeData&gt;，key 为 UUID 字符串。
 * </p>
 */
@SuppressWarnings("null")
public class PlayerAttributeStorage {

    private static final ReadWriteLock LOCK = new ReentrantReadWriteLock();
    private static final long SAVE_DEBOUNCE_MS = 2000;
    private static volatile long lastSaveTime = 0;

    private static final ConcurrentHashMap<String, PlayerAttributeData> CACHE = new ConcurrentHashMap<>();

    public static void initialize() {
        DebugLogger.entering("PlayerAttributeStorage", "initialize");
        DebugLogger.info("PlayerAttributeStorage", "数据文件=%s", SkillDataStore.file().toAbsolutePath());
        loadFromDisk();
        DebugLogger.exiting("PlayerAttributeStorage", "initialize");
    }

    private static void loadFromDisk() {
        LOCK.writeLock().lock();
        try {
            CACHE.clear();
            SkillDataStore.refresh();
            JsonObject attributes = SkillDataStore.read(SkillDataStore.KEY_ATTRIBUTES);
            if (attributes == null) {
                forceSave(); // 创建空数据块
                return;
            }
            java.lang.reflect.Type type = new TypeToken<Map<String, PlayerAttributeData>>() {}.getType();
            Map<String, PlayerAttributeData> loaded = PlayerAttributeData.GSON.fromJson(attributes, type);
            if (loaded != null) CACHE.putAll(loaded);
            DebugLogger.info("PlayerAttributeStorage", "loaded %d player attribute records", CACHE.size());
        } finally {
            LOCK.writeLock().unlock();
        }
    }

    public static void saveToDisk() {
        LOCK.writeLock().lock();
        try {
            SkillDataStore.write(SkillDataStore.KEY_ATTRIBUTES,
                    PlayerAttributeData.GSON.toJsonTree(CACHE).getAsJsonObject());
        } finally {
            LOCK.writeLock().unlock();
        }
    }

    public static PlayerAttributeData getOrCreate(UUID uuid, String username) {
        String key = uuid.toString();
        LOCK.readLock().lock();
        try {
            PlayerAttributeData data = CACHE.get(key);
            if (data != null) return data;
        } finally {
            LOCK.readLock().unlock();
        }
        LOCK.writeLock().lock();
        try {
            PlayerAttributeData data = CACHE.get(key);
            if (data == null) {
                data = new PlayerAttributeData(uuid, username);
                CACHE.put(key, data);
                forceSave();
            }
            return data;
        } finally {
            LOCK.writeLock().unlock();
        }
    }

    public static PlayerAttributeData get(UUID uuid) {
        LOCK.readLock().lock();
        try {
            return CACHE.get(uuid.toString());
        } finally {
            LOCK.readLock().unlock();
        }
    }

    /**
     * 快速检查玩家是否有属性数据（无锁版本，用于热路径熔断）。
     * <p>不触发任何懒创建或 I/O，仅检查缓存中是否存在。</p>
     */
    public static boolean hasAttributes(UUID uuid) {
        return CACHE.containsKey(uuid.toString());
    }

    public static void markDirty(UUID uuid) {
        long now = System.currentTimeMillis();
        if (now - lastSaveTime < SAVE_DEBOUNCE_MS) return;
        lastSaveTime = now;
        saveToDisk();
    }

    public static void forceSave() {
        lastSaveTime = System.currentTimeMillis();
        saveToDisk();
    }

    public static int reload() {
        LOCK.writeLock().lock();
        try {
            CACHE.clear();
            loadFromDisk();
            return CACHE.size();
        } finally {
            LOCK.writeLock().unlock();
        }
    }
}
