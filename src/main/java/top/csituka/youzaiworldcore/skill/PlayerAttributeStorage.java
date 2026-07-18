package top.csituka.youzaiworldcore.skill;

import com.google.gson.reflect.TypeToken;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.csituka.youzaiworldcore.util.DebugLogger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * 玩家属性加点数据持久化存储。
 * <p>
 * 数据文件：./config/youzaiworldcore/skill_module/player_attributes_data.json
 * 格式：JSON，Map{@code <String, PlayerAttributeData>}，key 为 UUID 字符串
 */
@SuppressWarnings("null")
public class PlayerAttributeStorage {

    private static final Logger LOGGER = LoggerFactory.getLogger("YouzaiWorldCore/PlayerAttributeStorage");

    private static Path STORAGE_FILE;
    private static final ReadWriteLock LOCK = new ReentrantReadWriteLock();
    private static final long SAVE_DEBOUNCE_MS = 2000;
    private static volatile long lastSaveTime = 0;

    private static final ConcurrentHashMap<String, PlayerAttributeData> CACHE = new ConcurrentHashMap<>();

    public static void initialize() {
        DebugLogger.entering("PlayerAttributeStorage", "initialize");
        Path configDir = FabricLoader.getInstance().getConfigDir()
                .resolve("youzaiworldcore")
                .resolve("skill_module")
                .normalize();
        try {
            Files.createDirectories(configDir);
        } catch (IOException e) {
            LOGGER.error("无法创建技能模块配置目录: {}", configDir, e);
        }
        STORAGE_FILE = configDir.resolve("player_attributes_data.json");
        DebugLogger.info("PlayerAttributeStorage", "STORAGE_FILE=%s", STORAGE_FILE.toAbsolutePath());
        loadFromDisk();
        DebugLogger.exiting("PlayerAttributeStorage", "initialize");
    }

    private static void loadFromDisk() {
        LOCK.writeLock().lock();
        try {
            CACHE.clear();
            if (!Files.exists(STORAGE_FILE)) {
                forceSave();
                return;
            }
            String json = Files.readString(STORAGE_FILE);
            if (json.isBlank()) return;
            java.lang.reflect.Type type = new TypeToken<Map<String, PlayerAttributeData>>() {}.getType();
            Map<String, PlayerAttributeData> loaded = PlayerAttributeData.GSON.fromJson(json, type);
            if (loaded != null) CACHE.putAll(loaded);
            DebugLogger.info("PlayerAttributeStorage", "loaded %d player attribute records", CACHE.size());
        } catch (IOException e) {
            LOGGER.error("读取玩家属性数据失败", e);
        } finally {
            LOCK.writeLock().unlock();
        }
    }

    public static void saveToDisk() {
        LOCK.writeLock().lock();
        try {
            String json = PlayerAttributeData.GSON.toJson(CACHE);
            Files.writeString(STORAGE_FILE, json);
        } catch (IOException e) {
            LOGGER.error("保存玩家属性数据失败", e);
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
