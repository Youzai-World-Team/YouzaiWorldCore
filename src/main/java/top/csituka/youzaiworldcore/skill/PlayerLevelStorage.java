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
 * 冒险等级数据持久化存储。
 * 数据文件：./config/youzaiworldcore/skill_module/player_level_data.json
 * 格式：JSON，Map<String, PlayerLevelData>，key 为 UUID 字符串
 */
@SuppressWarnings("null")
public class PlayerLevelStorage {

    private static final Logger LOGGER = LoggerFactory.getLogger("YouzaiWorldCore/PlayerLevelStorage");

    private static Path STORAGE_FILE;
    private static final ReadWriteLock LOCK = new ReentrantReadWriteLock();

    /** 防抖：距上次保存至少间隔的毫秒数 */
    private static final long SAVE_DEBOUNCE_MS = 2000;
    private static volatile long lastSaveTime = 0;

    /** 内存缓存：UUID 字符串 -> PlayerLevelData */
    private static final ConcurrentHashMap<String, PlayerLevelData> CACHE = new ConcurrentHashMap<>();

    /**
     * 初始化存储路径并加载数据。
     */
    public static void initialize() {
        DebugLogger.entering("PlayerLevelStorage", "initialize");
        Path configDir = FabricLoader.getInstance().getConfigDir()
                .resolve("youzaiworldcore")
                .resolve("skill_module")
                .normalize();
        try {
            Files.createDirectories(configDir);
        } catch (IOException e) {
            LOGGER.error("无法创建技能模块配置目录: {}", configDir, e);
            DebugLogger.exception("PlayerLevelStorage", "initialize-createDirectories", e);
        }
        STORAGE_FILE = configDir.resolve("player_level_data.json");
        DebugLogger.info("PlayerLevelStorage", "STORAGE_FILE=%s", STORAGE_FILE.toAbsolutePath());
        loadFromDisk();
        DebugLogger.exiting("PlayerLevelStorage", "initialize");
    }

    /**
     * 从磁盘加载所有玩家等级数据。
     */
    private static void loadFromDisk() {
        DebugLogger.entering("PlayerLevelStorage", "loadFromDisk");
        LOCK.writeLock().lock();
        try {
            CACHE.clear();
            if (!Files.exists(STORAGE_FILE)) {
                DebugLogger.branch("PlayerLevelStorage", "STORAGE_FILE exists", false);
                forceSave(); // 创建空文件
                return;
            }
            DebugLogger.branch("PlayerLevelStorage", "STORAGE_FILE exists", true);
            String json = Files.readString(STORAGE_FILE);
            if (json.isBlank()) return;

            java.lang.reflect.Type type = new TypeToken<Map<String, PlayerLevelData>>() {}.getType();
            Map<String, PlayerLevelData> loaded = PlayerLevelData.GSON.fromJson(json, type);
            if (loaded != null) {
                CACHE.putAll(loaded);
            }
            DebugLogger.info("PlayerLevelStorage", "loaded %d player level records", CACHE.size());
        } catch (IOException e) {
            LOGGER.error("读取玩家等级数据失败", e);
            DebugLogger.exception("PlayerLevelStorage", "loadFromDisk-readFile", e);
        } finally {
            LOCK.writeLock().unlock();
            DebugLogger.exiting("PlayerLevelStorage", "loadFromDisk");
        }
    }

    /**
     * 将缓存保存到磁盘。
     */
    public static void saveToDisk() {
        DebugLogger.entering("PlayerLevelStorage", "saveToDisk");
        LOCK.writeLock().lock();
        try {
            String json = PlayerLevelData.GSON.toJson(CACHE);
            Files.writeString(STORAGE_FILE, json);
        } catch (IOException e) {
            LOGGER.error("保存玩家等级数据失败", e);
            DebugLogger.exception("PlayerLevelStorage", "saveToDisk-writeFile", e);
        } finally {
            LOCK.writeLock().unlock();
            DebugLogger.exiting("PlayerLevelStorage", "saveToDisk");
        }
    }

    /**
     * 获取或创建玩家等级数据。
     */
    public static PlayerLevelData getOrCreate(UUID uuid, String username) {
        String key = uuid.toString();
        LOCK.readLock().lock();
        try {
            PlayerLevelData data = CACHE.get(key);
            if (data != null) {
                // 更新用户名（可能已更改）
                if (!username.equals(data.username)) {
                    data.username = username;
                }
                return data;
            }
        } finally {
            LOCK.readLock().unlock();
        }

        // 需要创建新记录
        LOCK.writeLock().lock();
        try {
            PlayerLevelData data = CACHE.get(key);
            if (data == null) {
                data = new PlayerLevelData(uuid, username);
                CACHE.put(key, data);
                forceSave(); // 新记录立即保存
            }
            return data;
        } finally {
            LOCK.writeLock().unlock();
        }
    }

    /**
     * 获取玩家等级数据（不创建）。
     */
    public static PlayerLevelData get(UUID uuid) {
        LOCK.readLock().lock();
        try {
            return CACHE.get(uuid.toString());
        } finally {
            LOCK.readLock().unlock();
        }
    }

    /**
     * 标记数据已变更（防抖：至少间隔 2 秒才写盘一次）。
     * 多次修改会合并为一次写操作。
     */
    public static void markDirty(UUID uuid) {
        long now = System.currentTimeMillis();
        if (now - lastSaveTime < SAVE_DEBOUNCE_MS) return;
        lastSaveTime = now;
        saveToDisk();
    }

    /**
     * 强制立即保存到磁盘（无视防抖）。
     */
    public static void forceSave() {
        lastSaveTime = System.currentTimeMillis();
        saveToDisk();
    }

    /**
     * 重新加载所有数据。
     */
    public static int reload() {
        DebugLogger.entering("PlayerLevelStorage", "reload");
        loadFromDisk();
        int count = CACHE.size();
        DebugLogger.exiting("PlayerLevelStorage", "reload", "count=" + count);
        return count;
    }
}
