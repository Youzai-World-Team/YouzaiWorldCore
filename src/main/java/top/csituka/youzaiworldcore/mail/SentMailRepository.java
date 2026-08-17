package top.csituka.youzaiworldcore.mail;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import top.csituka.youzaiworldcore.config.GlobalSettings;
import top.csituka.youzaiworldcore.config.JsonFileStore;
import top.csituka.youzaiworldcore.config.ModPaths;
import top.csituka.youzaiworldcore.util.DebugLogger;

import java.lang.reflect.Type;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * 全局邮件仓库：{@code yzwc/server/data/mail_module/data.json} 的 {@code sent_mails} 块。
 * <p>
 * 存储所有已发送的完整 {@link Mail} 对象，键为 {@link UUID mailId}。
 * 修改操作带读写锁保护。
 * </p>
 */
@SuppressWarnings("null")
public class SentMailRepository {

    private static final String MODULE = "SentMailRepository";

    /** 邮件正文在数据文件里的块名 */
    private static final String KEY_SENT_MAILS = "sent_mails";

    private static final ConcurrentHashMap<UUID, Mail> REPO = new ConcurrentHashMap<>();
    private static final ReentrantReadWriteLock LOCK = new ReentrantReadWriteLock();

    private static final JsonFileStore STORE =
            new JsonFileStore(ModPaths.serverDataFile(GlobalSettings.MAIL_MODULE));

    /** 仅用于 {@link Mail} 对象与 JSON 树之间的转换 */
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    static {
        // 新开服 / 坏文件恢复时的默认内容：一张空的邮件正文表
        STORE.setDefaultsWriter(() -> STORE.putSection(KEY_SENT_MAILS, new JsonObject()));
    }

    // ===== 初始化 =====

    /**
     * 初始化仓库（准备数据目录并从磁盘加载）。
     */
    public static void initialize() {
        DebugLogger.entering(MODULE, "initialize", "file=" + STORE.file());
        ModPaths.ensureDir(ModPaths.serverData(GlobalSettings.MAIL_MODULE));
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
     * 批量删除邮件正文，并在整批操作完成后只重写一次仓库文件。
     *
     * @param mailIds 待删除的邮件 ID
     * @return 实际删除数量
     */
    public static int removeAll(Collection<UUID> mailIds) {
        if (mailIds.isEmpty()) {
            return 0;
        }
        DebugLogger.entering(MODULE, "removeAll", "count=" + mailIds.size());
        LOCK.writeLock().lock();
        try {
            int removed = 0;
            for (UUID mailId : mailIds) {
                if (REPO.remove(mailId) != null) {
                    removed++;
                }
            }
            if (removed > 0) {
                saveToDisk();
            }
            DebugLogger.exiting(MODULE, "removeAll", "removed=" + removed);
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
            STORE.loadOrCreateDefaults();
            JsonObject sent = STORE.section(KEY_SENT_MAILS).raw();
            if (sent.size() == 0) {
                DebugLogger.branch(MODULE, "sent_mails 块存在", false);
                saveToDisk(); // 补齐缺失的空表
                DebugLogger.exiting(MODULE, "loadFromDisk", "no data, created empty");
                return;
            }
            Type type = new TypeToken<Map<UUID, Mail>>() {}.getType();
            Map<UUID, Mail> loaded;
            try {
                loaded = GSON.fromJson(sent, type);
            } catch (RuntimeException e) {
                STORE.section(KEY_SENT_MAILS).fail("<邮件表>", "邮件正文解析失败：" + e.getMessage());
                return; // 不可达
            }
            if (loaded != null) {
                REPO.putAll(loaded);
            }
            DebugLogger.info(MODULE, "已从磁盘加载 %d 封邮件", REPO.size());
        } finally {
            LOCK.writeLock().unlock();
            DebugLogger.exiting(MODULE, "loadFromDisk");
        }
    }

    private static void saveToDisk() {
        DebugLogger.entering(MODULE, "saveToDisk", "size=" + REPO.size());
        LOCK.readLock().lock();
        try {
            STORE.putSection(KEY_SENT_MAILS, GSON.toJsonTree(REPO).getAsJsonObject());
            STORE.save();
            DebugLogger.info(MODULE, "已保存 %d 封邮件到磁盘", REPO.size());
        } finally {
            LOCK.readLock().unlock();
            DebugLogger.exiting(MODULE, "saveToDisk");
        }
    }
}
