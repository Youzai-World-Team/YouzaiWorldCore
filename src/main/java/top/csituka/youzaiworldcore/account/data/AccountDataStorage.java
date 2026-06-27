package top.csituka.youzaiworldcore.account.data;

import com.google.gson.reflect.TypeToken;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.csituka.youzaiworldcore.YouzaiworldCore;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * 账户数据存储系统
 * 数据文件：./config/youzaiworldcore/account/data
 * 格式：JSON，Map<String, PlayerAccount>，key 为小写用户名
 */
public class AccountDataStorage {

    private static final Logger LOGGER = LoggerFactory.getLogger(YouzaiworldCore.MOD_ID + "/AccountStorage");

    private static Path STORAGE_FILE;
    private static Path CONFIG_FILE;
    private static final ReadWriteLock LOCK = new ReentrantReadWriteLock();

    /** 内存缓存：小写用户名 -> PlayerAccount */
    private static final ConcurrentHashMap<String, PlayerAccount> CACHE = new ConcurrentHashMap<>();

    /** 会话认证超时时间（秒），0=关闭 */
    private static int sessionTimeout = 0;

    /**
     * 获取会话超时时间
     */
    public static int getSessionTimeout() {
        return sessionTimeout;
    }

    /**
     * 设置会话超时时间
     */
    public static void setSessionTimeout(int seconds) {
        sessionTimeout = Math.max(0, seconds);
        saveConfig();
    }

    /**
     * 初始化存储路径
     */
    public static void initialize() {
        Path configDir = FabricLoader.getInstance().getConfigDir()
                .resolve("youzaiworldcore")
                .resolve("account")
                .normalize();
        try {
            Files.createDirectories(configDir);
        } catch (IOException e) {
            LOGGER.error("无法创建配置目录: {}", configDir, e);
        }
        STORAGE_FILE = configDir.resolve("data");
        CONFIG_FILE = configDir.resolve("config");
        LOGGER.info("账户数据文件路径: {}", STORAGE_FILE.toAbsolutePath());
        loadConfig();
        loadFromDisk();
    }

    private static void loadConfig() {
        try {
            if (Files.exists(CONFIG_FILE)) {
                String json = Files.readString(CONFIG_FILE);
                var obj = PlayerAccount.GSON.fromJson(json, java.util.Map.class);
                if (obj != null && obj.containsKey("session_timeout")) {
                    sessionTimeout = ((Number) obj.get("session_timeout")).intValue();
                }
            }
        } catch (IOException e) {
            LOGGER.error("读取账户配置失败", e);
        }
    }

    private static void saveConfig() {
        try {
            var map = new java.util.HashMap<String, Object>();
            map.put("session_timeout", sessionTimeout);
            Files.writeString(CONFIG_FILE, PlayerAccount.GSON.toJson(map));
        } catch (IOException e) {
            LOGGER.error("保存账户配置失败", e);
        }
    }

    /**
     * 从磁盘加载所有账户数据
     */
    private static void loadFromDisk() {
        LOCK.writeLock().lock();
        try {
            CACHE.clear();
            if (!Files.exists(STORAGE_FILE)) {
                LOGGER.info("账户数据文件不存在，将创建新的: {}", STORAGE_FILE.toAbsolutePath());
                saveToDisk(); // 创建空文件
                return;
            }

            String json = Files.readString(STORAGE_FILE);
            if (json.isBlank()) {
                return;
            }

            java.lang.reflect.Type type = new TypeToken<Map<String, PlayerAccount>>() {}.getType();
            Map<String, PlayerAccount> loaded = PlayerAccount.GSON.fromJson(json, type);
            if (loaded != null) {
                CACHE.putAll(loaded);
            }
            LOGGER.info("已加载 {} 个账户", CACHE.size());
        } catch (IOException e) {
            LOGGER.error("读取账户数据失败", e);
        } finally {
            LOCK.writeLock().unlock();
        }
    }

    /**
     * 将缓存保存到磁盘
     */
    public static void saveToDisk() {
        LOCK.writeLock().lock();
        try {
            String json = PlayerAccount.GSON.toJson(CACHE);
            Files.writeString(STORAGE_FILE, json);
        } catch (IOException e) {
            LOGGER.error("保存账户数据失败", e);
        } finally {
            LOCK.writeLock().unlock();
        }
    }

    /**
     * 获取玩家账户（通过小写名查找）
     */
    public static PlayerAccount get(String username) {
        LOCK.readLock().lock();
        try {
            return CACHE.get(username.toLowerCase(java.util.Locale.ENGLISH));
        } finally {
            LOCK.readLock().unlock();
        }
    }

    /**
     * 获取或创建玩家账户
     */
    public static PlayerAccount getOrCreate(String username, java.util.UUID uuid) {
        LOCK.writeLock().lock();
        try {
            String key = username.toLowerCase(java.util.Locale.ENGLISH);
            PlayerAccount account = CACHE.get(key);
            if (account == null) {
                account = new PlayerAccount(username, uuid);
                CACHE.put(key, account);
                saveToDisk();
            }
            return account;
        } finally {
            LOCK.writeLock().unlock();
        }
    }

    /**
     * 注册玩家（写入密码并保存）
     * @return true 如果注册成功
     */
    public static boolean register(String username, String hashedPassword) {
        LOCK.writeLock().lock();
        try {
            String key = username.toLowerCase(java.util.Locale.ENGLISH);
            PlayerAccount account = CACHE.get(key);
            if (account == null) {
                return false;
            }
            if (account.isRegistered()) {
                return false; // 已注册
            }
            account.password = hashedPassword;
            account.registrationDate = java.time.ZonedDateTime.now();
            account.lastAuthenticatedDate = java.time.ZonedDateTime.now();
            saveToDisk();
            return true;
        } finally {
            LOCK.writeLock().unlock();
        }
    }

    /**
     * 更新玩家数据到磁盘
     */
    public static void update(PlayerAccount account) {
        LOCK.writeLock().lock();
        try {
            String key = account.usernameLowerCase;
            CACHE.put(key, account);
            saveToDisk();
        } finally {
            LOCK.writeLock().unlock();
        }
    }

    /**
     * 删除玩家账户
     * @return true 如果删除成功
     */
    public static boolean delete(String username) {
        LOCK.writeLock().lock();
        try {
            String key = username.toLowerCase(java.util.Locale.ENGLISH);
            PlayerAccount removed = CACHE.remove(key);
            if (removed != null) {
                saveToDisk();
                return true;
            }
            return false;
        } finally {
            LOCK.writeLock().unlock();
        }
    }

    /**
     * 获取所有账户数据
     */
    public static Map<String, PlayerAccount> getAll() {
        LOCK.readLock().lock();
        try {
            return new ConcurrentHashMap<>(CACHE);
        } finally {
            LOCK.readLock().unlock();
        }
    }

    /**
     * 通过玩家名称（精确匹配）获取
     */
    public static PlayerAccount getByExactName(String username) {
        LOCK.readLock().lock();
        try {
            for (PlayerAccount acc : CACHE.values()) {
                if (acc.username != null && acc.username.equals(username)) {
                    return acc;
                }
            }
            return null;
        } finally {
            LOCK.readLock().unlock();
        }
    }
}
