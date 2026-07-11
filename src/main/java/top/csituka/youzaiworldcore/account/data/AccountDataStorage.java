package top.csituka.youzaiworldcore.account.data;

import com.google.gson.reflect.TypeToken;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.csituka.youzaiworldcore.YouzaiworldCore;
import top.csituka.youzaiworldcore.util.DebugLogger;

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
@SuppressWarnings("null")
public class AccountDataStorage {

    private static final Logger LOGGER = LoggerFactory.getLogger("YouzaiWorldCore/AccountDataStorage");

    private static Path STORAGE_FILE;
    private static Path CONFIG_FILE;
    private static final ReadWriteLock LOCK = new ReentrantReadWriteLock();

    /** 内存缓存：小写用户名 -> PlayerAccount */
    private static final ConcurrentHashMap<String, PlayerAccount> CACHE = new ConcurrentHashMap<>();

    /** 会话认证超时时间（秒），0=关闭 */
    private static int sessionTimeout = 0;

    /** 登录失败锁定冷却时间（秒），默认 5 分钟 */
    private static int loginCooldown = 300;

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
        int oldValue = sessionTimeout;
        sessionTimeout = Math.max(0, seconds);
        DebugLogger.stateChange("AccountDataStorage", "AccountDataStorage", "sessionTimeout", oldValue, sessionTimeout);
        saveConfig();
    }

    /**
     * 获取登录失败锁定冷却时间（秒）
     */
    public static int getLoginCooldown() {
        return loginCooldown;
    }

    /**
     * 设置登录失败锁定冷却时间（秒）
     */
    public static void setLoginCooldown(int seconds) {
        int oldValue = loginCooldown;
        loginCooldown = Math.max(-1, seconds);
        DebugLogger.stateChange("AccountDataStorage", "AccountDataStorage", "loginCooldown", oldValue, loginCooldown);
        saveConfig();
    }

    /**
     * 初始化存储路径
     */
    public static void initialize() {
        DebugLogger.entering("AccountDataStorage", "initialize");
        Path configDir = FabricLoader.getInstance().getConfigDir()
                .resolve("youzaiworldcore")
                .resolve("account")
                .normalize();
        try {
            Files.createDirectories(configDir);
        } catch (IOException e) {
            LOGGER.error("无法创建配置目录: {}", configDir, e);
            DebugLogger.exception("AccountDataStorage", "initialize-createDirectories", e);
        }
        STORAGE_FILE = configDir.resolve("data");
        CONFIG_FILE = configDir.resolve("config");
        DebugLogger.info("AccountDataStorage", "STORAGE_FILE=%s, CONFIG_FILE=%s",
                STORAGE_FILE.toAbsolutePath(), CONFIG_FILE.toAbsolutePath());
        if (YouzaiworldCore.logToFile) {
            LOGGER.info("账户数据文件路径: {}", STORAGE_FILE.toAbsolutePath());
        }
        loadConfig();
        loadFromDisk();
        DebugLogger.exiting("AccountDataStorage", "initialize");
    }

    private static void loadConfig() {
        DebugLogger.entering("AccountDataStorage", "loadConfig");
        try {
            if (Files.exists(CONFIG_FILE)) {
                DebugLogger.branch("AccountDataStorage", "CONFIG_FILE exists", true);
                String json = Files.readString(CONFIG_FILE);
                var obj = PlayerAccount.GSON.fromJson(json, java.util.Map.class);
                if (obj != null) {
                    if (obj.containsKey("session_timeout")) {
                        sessionTimeout = ((Number) obj.get("session_timeout")).intValue();
                    }
                    if (obj.containsKey("login_cooldown")) {
                        loginCooldown = ((Number) obj.get("login_cooldown")).intValue();
                    }
                }
            } else {
                DebugLogger.branch("AccountDataStorage", "CONFIG_FILE exists", false);
            }
        } catch (IOException e) {
            LOGGER.error("读取账户配置失败", e);
            DebugLogger.exception("AccountDataStorage", "loadConfig-readConfig", e);
        }
        DebugLogger.exiting("AccountDataStorage", "loadConfig",
                "sessionTimeout=" + sessionTimeout + ", loginCooldown=" + loginCooldown);
    }

    private static void saveConfig() {
        DebugLogger.entering("AccountDataStorage", "saveConfig");
        try {
            var map = new java.util.HashMap<String, Object>();
            map.put("session_timeout", sessionTimeout);
            map.put("login_cooldown", loginCooldown);
            Files.writeString(CONFIG_FILE, PlayerAccount.GSON.toJson(map));
        } catch (IOException e) {
            LOGGER.error("保存账户配置失败", e);
            DebugLogger.exception("AccountDataStorage", "saveConfig-writeConfig", e);
        }
        DebugLogger.exiting("AccountDataStorage", "saveConfig");
    }

    /**
     * 从磁盘加载所有账户数据
     */
    private static void loadFromDisk() {
        DebugLogger.entering("AccountDataStorage", "loadFromDisk");
        LOCK.writeLock().lock();
        try {
            CACHE.clear();
            DebugLogger.stateChange("AccountDataStorage", "AccountDataStorage", "CACHE", "clear");
            if (!Files.exists(STORAGE_FILE)) {
                DebugLogger.branch("AccountDataStorage", "STORAGE_FILE exists", false);
                if (YouzaiworldCore.logToFile) {
                    LOGGER.info("账户数据文件不存在，将创建新的: {}", STORAGE_FILE.toAbsolutePath());
                }
                saveToDisk(); // 创建空文件
                DebugLogger.exiting("AccountDataStorage", "loadFromDisk", "file not found, created empty");
                return;
            }
            DebugLogger.branch("AccountDataStorage", "STORAGE_FILE exists", true);

            String json = Files.readString(STORAGE_FILE);
            if (json.isBlank()) {
                DebugLogger.branch("AccountDataStorage", "json is blank", true);
                DebugLogger.exiting("AccountDataStorage", "loadFromDisk", "blank file");
                return;
            }
            DebugLogger.branch("AccountDataStorage", "json is blank", false);

            java.lang.reflect.Type type = new TypeToken<Map<String, PlayerAccount>>() {}.getType();
            Map<String, PlayerAccount> loaded = PlayerAccount.GSON.fromJson(json, type);
            if (loaded != null) {
                CACHE.putAll(loaded);
            }
            DebugLogger.info("AccountDataStorage", "loaded %d accounts", CACHE.size());
            if (YouzaiworldCore.logToFile) {
                LOGGER.info("已加载 {} 个账户", CACHE.size());
            }
        } catch (IOException e) {
            LOGGER.error("读取账户数据失败", e);
            DebugLogger.exception("AccountDataStorage", "loadFromDisk-readFile", e);
        } finally {
            LOCK.writeLock().unlock();
            DebugLogger.exiting("AccountDataStorage", "loadFromDisk");
        }
    }

    /**
     * 将缓存保存到磁盘
     */
    public static void saveToDisk() {
        DebugLogger.entering("AccountDataStorage", "saveToDisk");
        LOCK.writeLock().lock();
        DebugLogger.info("AccountDataStorage", "writeLock acquired for saveToDisk");
        try {
            String json = PlayerAccount.GSON.toJson(CACHE);
            Files.writeString(STORAGE_FILE, json);
        } catch (IOException e) {
            LOGGER.error("保存账户数据失败", e);
            DebugLogger.exception("AccountDataStorage", "saveToDisk-writeFile", e);
        } finally {
            LOCK.writeLock().unlock();
            DebugLogger.exiting("AccountDataStorage", "saveToDisk");
        }
    }

    /**
     * 获取玩家账户（通过小写名查找）
     */
    public static PlayerAccount get(String username) {
        DebugLogger.entering("AccountDataStorage", "get", "username=" + username);
        LOCK.readLock().lock();
        try {
            return CACHE.get(username.toLowerCase(java.util.Locale.ENGLISH));
        } finally {
            LOCK.readLock().unlock();
            DebugLogger.exiting("AccountDataStorage", "get");
        }
    }

    /**
     * 获取或创建玩家账户
     */
    public static PlayerAccount getOrCreate(String username, java.util.UUID uuid) {
        DebugLogger.entering("AccountDataStorage", "getOrCreate", "username=" + username + ", uuid=" + uuid);
        LOCK.writeLock().lock();
        try {
            String key = username.toLowerCase(java.util.Locale.ENGLISH);
            PlayerAccount account = CACHE.get(key);
            if (account == null) {
                DebugLogger.branch("AccountDataStorage", "existing account found", false);
                account = new PlayerAccount(username, uuid);
                CACHE.put(key, account);
                saveToDisk();
                DebugLogger.exiting("AccountDataStorage", "getOrCreate", "created new account");
            } else {
                DebugLogger.branch("AccountDataStorage", "existing account found", true);
                DebugLogger.exiting("AccountDataStorage", "getOrCreate", "returning existing account");
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
        DebugLogger.entering("AccountDataStorage", "register", "username=" + username);
        LOCK.writeLock().lock();
        try {
            String key = username.toLowerCase(java.util.Locale.ENGLISH);
            PlayerAccount account = CACHE.get(key);
            if (account == null) {
                DebugLogger.branch("AccountDataStorage", "account is null", true);
                DebugLogger.exiting("AccountDataStorage", "register", "false (account not found)");
                return false;
            }
            DebugLogger.branch("AccountDataStorage", "account is null", false);
            if (account.isRegistered()) {
                DebugLogger.branch("AccountDataStorage", "account already registered", true);
                DebugLogger.exiting("AccountDataStorage", "register", "false (already registered)");
                return false;
            }
            DebugLogger.branch("AccountDataStorage", "account already registered", false);
            String oldPassword = account.password;
            account.password = hashedPassword;
            account.registrationDate = java.time.ZonedDateTime.now();
            account.lastAuthenticatedDate = java.time.ZonedDateTime.now();
            DebugLogger.stateChange("AccountDataStorage", key, "password", oldPassword, hashedPassword);
            DebugLogger.stateChange("AccountDataStorage", key, "registrationDate",
                    java.time.ZonedDateTime.now());
            DebugLogger.stateChange("AccountDataStorage", key, "lastAuthenticatedDate",
                    java.time.ZonedDateTime.now());
            saveToDisk();
            DebugLogger.exiting("AccountDataStorage", "register", "true (registered successfully)");
            return true;
        } finally {
            LOCK.writeLock().unlock();
        }
    }

    /**
     * 更新玩家数据到磁盘
     */
    public static void update(PlayerAccount account) {
        DebugLogger.entering("AccountDataStorage", "update", "username=" + account.usernameLowerCase);
        LOCK.writeLock().lock();
        try {
            String key = account.usernameLowerCase;
            CACHE.put(key, account);
            saveToDisk();
        } finally {
            LOCK.writeLock().unlock();
            DebugLogger.exiting("AccountDataStorage", "update");
        }
    }

    /**
     * 删除玩家账户
     * @return true 如果删除成功
     */
    public static boolean delete(String username) {
        DebugLogger.entering("AccountDataStorage", "delete", "username=" + username);
        LOCK.writeLock().lock();
        try {
            String key = username.toLowerCase(java.util.Locale.ENGLISH);
            PlayerAccount removed = CACHE.remove(key);
            if (removed != null) {
                DebugLogger.branch("AccountDataStorage", "account found to delete", true);
                saveToDisk();
                DebugLogger.exiting("AccountDataStorage", "delete", "true");
                return true;
            }
            DebugLogger.branch("AccountDataStorage", "account found to delete", false);
            DebugLogger.exiting("AccountDataStorage", "delete", "false (not found)");
            return false;
        } finally {
            LOCK.writeLock().unlock();
        }
    }

    /**
     * 从磁盘重新加载所有数据（账户数据 + 配置）。
     * <p>
     * 此方法会清空内存缓存并从磁盘文件重新读取，适用于 {@code /yzwc reload} 命令。
     * 操作受读写锁保护，线程安全。
     * </p>
     *
     * @return 重新加载后的账户数量
     */
    public static int reload() {
        DebugLogger.entering("AccountDataStorage", "reload");
        if (YouzaiworldCore.logToFile) {
            LOGGER.info("正在从磁盘重新加载账户数据...");
        }
        loadConfig();
        loadFromDisk();
        int count = CACHE.size();
        DebugLogger.info("AccountDataStorage", "reload complete, %d accounts", count);
        if (YouzaiworldCore.logToFile) {
            LOGGER.info("账户数据重载完成，共 {} 个账户", count);
        }
        DebugLogger.exiting("AccountDataStorage", "reload", "count=" + count);
        return count;
    }

    /**
     * 获取所有账户数据
     */
    public static Map<String, PlayerAccount> getAll() {
        DebugLogger.entering("AccountDataStorage", "getAll");
        LOCK.readLock().lock();
        try {
            return new ConcurrentHashMap<>(CACHE);
        } finally {
            LOCK.readLock().unlock();
            DebugLogger.exiting("AccountDataStorage", "getAll", "size=" + CACHE.size());
        }
    }

    /**
     * 通过玩家名称（精确匹配）获取
     */
    public static PlayerAccount getByExactName(String username) {
        DebugLogger.entering("AccountDataStorage", "getByExactName", "username=" + username);
        LOCK.readLock().lock();
        try {
            for (PlayerAccount acc : CACHE.values()) {
                if (acc.username != null && acc.username.equals(username)) {
                    DebugLogger.exiting("AccountDataStorage", "getByExactName", "found");
                    return acc;
                }
            }
            DebugLogger.exiting("AccountDataStorage", "getByExactName", "not found");
            return null;
        } finally {
            LOCK.readLock().unlock();
        }
    }
}
