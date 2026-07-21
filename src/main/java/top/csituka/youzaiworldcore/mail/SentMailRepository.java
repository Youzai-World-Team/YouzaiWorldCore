package top.csituka.youzaiworldcore.mail;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import top.csituka.youzaiworldcore.YouzaiworldCore;
import top.csituka.youzaiworldcore.util.DebugLogger;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * 全局邮件仓库：{@code config/youzaiworldcore/mail/sent.json}。
 * <p>
 * 存储所有已发送的完整 {@link Mail} 对象，键为 {@link UUID mailId}。
 * 修改操作带读写锁保护。
 * </p>
 */
@SuppressWarnings("null")
public class SentMailRepository {

    private static final String MODULE = "SentMailRepository";

    private static final ConcurrentHashMap<UUID, Mail> REPO = new ConcurrentHashMap<>();
    private static final ReentrantReadWriteLock LOCK = new ReentrantReadWriteLock();
    private static Path STORAGE_FILE;

    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .create();

    // ===== 初始化 =====

    /**
     * 初始化仓库（设置存储路径并从磁盘加载）。
     *
     * @param dataDir 数据目录 {@code config/youzaiworldcore/mail}
     */
    public static void initialize(Path dataDir) {
        DebugLogger.entering(MODULE, "initialize", "dataDir=" + dataDir);
        STORAGE_FILE = dataDir.resolve("sent.json");
        loadFromDisk();
        DebugLogger.exiting(MODULE, "initialize", "loaded=" + REPO.size());
    }

    // ===== 核心操作 =====

    /**
     * 存入一封邮件。
     *
     * @param mail 邮件对象
     */
    public static void put(Mail mail) {
        DebugLogger.entering(MODULE, "put", "mailId=" + mail.getId());
        LOCK.writeLock().lock();
        try {
            REPO.put(mail.getId(), mail);
            saveToDisk();
        } finally {
            LOCK.writeLock().unlock();
        }
        DebugLogger.exiting(MODULE, "put");
    }

    /**
     * 获取一封邮件。
     *
     * @param mailId 邮件 ID
     * @return 邮件对象，不存在返回 null
     */
    public static Mail get(UUID mailId) {
        LOCK.readLock().lock();
        try {
            return REPO.get(mailId);
        } finally {
            LOCK.readLock().unlock();
        }
    }

    /**
     * 删除一封邮件。
     *
     * @param mailId 邮件 ID
     * @return 被删除的邮件；不存在返回 null
     */
    public static Mail remove(UUID mailId) {
        DebugLogger.entering(MODULE, "remove", "mailId=" + mailId);
        LOCK.writeLock().lock();
        try {
            Mail removed = REPO.remove(mailId);
            if (removed != null) {
                saveToDisk();
            }
            DebugLogger.exiting(MODULE, "remove", "removed=" + (removed != null));
            return removed;
        } finally {
            LOCK.writeLock().unlock();
        }
    }

    /**
     * 检查邮件是否存在。
     *
     * @param mailId 邮件 ID
     * @return true 如果存在
     */
    public static boolean exists(UUID mailId) {
        LOCK.readLock().lock();
        try {
            return REPO.containsKey(mailId);
        } finally {
            LOCK.readLock().unlock();
        }
    }

    /**
     * 获取所有邮件的只读视图。
     *
     * @return 邮件集合
     */
    public static Collection<Mail> getAll() {
        LOCK.readLock().lock();
        try {
            return REPO.values();
        } finally {
            LOCK.readLock().unlock();
        }
    }

    /**
     * 获取仓库大小。
     */
    public static int size() {
        return REPO.size();
    }

    // ===== 持久化 =====

    private static void loadFromDisk() {
        DebugLogger.entering(MODULE, "loadFromDisk");
        LOCK.writeLock().lock();
        try {
            REPO.clear();
            if (!Files.exists(STORAGE_FILE)) {
                DebugLogger.branch(MODULE, "STORAGE_FILE exists", false);
                saveToDisk(); // 创建空文件
                DebugLogger.exiting(MODULE, "loadFromDisk", "file not found, created empty");
                return;
            }
            String json = Files.readString(STORAGE_FILE);
            if (json.isBlank()) {
                DebugLogger.exiting(MODULE, "loadFromDisk", "blank file");
                return;
            }
            Type type = new TypeToken<Map<UUID, Mail>>() {}.getType();
            Map<UUID, Mail> loaded = GSON.fromJson(json, type);
            if (loaded != null) {
                REPO.putAll(loaded);
            }
            DebugLogger.info(MODULE, "已从磁盘加载 %d 封邮件", REPO.size());
        } catch (IOException e) {
            YouzaiworldCore.LOGGER.error("读取邮件仓库失败", e);
            DebugLogger.exception(MODULE, "loadFromDisk", e);
        } finally {
            LOCK.writeLock().unlock();
            DebugLogger.exiting(MODULE, "loadFromDisk");
        }
    }

    private static void saveToDisk() {
        DebugLogger.entering(MODULE, "saveToDisk", "size=" + REPO.size());
        LOCK.readLock().lock();
        try {
            Files.createDirectories(STORAGE_FILE.getParent());
            String json = GSON.toJson(REPO);
            Files.writeString(STORAGE_FILE, json);
            DebugLogger.info(MODULE, "已保存 %d 封邮件到磁盘", REPO.size());
        } catch (IOException e) {
            YouzaiworldCore.LOGGER.error("保存邮件仓库失败", e);
            DebugLogger.exception(MODULE, "saveToDisk", e);
        } finally {
            LOCK.readLock().unlock();
            DebugLogger.exiting(MODULE, "saveToDisk");
        }
    }
}
