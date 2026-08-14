package top.csituka.youzaiworldcore.account.data;

import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.csituka.youzaiworldcore.YouzaiworldCore;
import top.csituka.youzaiworldcore.config.ConfigSection;
import top.csituka.youzaiworldcore.config.GlobalSettings;
import top.csituka.youzaiworldcore.config.JsonFileStore;
import top.csituka.youzaiworldcore.config.ModPaths;
import top.csituka.youzaiworldcore.config.UserSettings;
import top.csituka.youzaiworldcore.util.DebugLogger;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * 账户数据存储系统。
 * <p>
 * 凭据文件：{@code yzwc/server/config/account_module/registerd_users_data.json}，
 * 结构为 {@code {"account_module": {"registered_users": {"<小写玩家代号>": {...}}}}}，
 * 每条记录里的 {@code uuid} 与 {@code yzwc/server/config/user_settings/<UUID>.json}
 * 一一对应：注册时自动建档，注销时自动删档。
 * </p>
 * <p>
 * 会话超时 / 登录冷却这类<b>全局</b>设置写在
 * {@code yzwc/server/config/global_settings.json} 的 {@code account_module} 分节。
 * </p>
 */
@SuppressWarnings("null")
public class AccountDataStorage {

    private static final Logger LOGGER = LoggerFactory.getLogger("YouzaiWorldCore/AccountDataStorage");

    /** 凭据在文件里的键名 */
    private static final String KEY_REGISTERED_USERS = "registered_users";

    /** 注册用户凭据文件容器 */
    private static final JsonFileStore USERS_STORE = new JsonFileStore(ModPaths.registeredUsersFile());

    static {
        // 新开服 / 坏文件恢复时，凭据文件的默认内容是一张空的注册用户表
        USERS_STORE.setDefaultsWriter(() ->
                USERS_STORE.section(GlobalSettings.ACCOUNT_MODULE)
                        .set(KEY_REGISTERED_USERS, new JsonObject()));
    }

    private static final ReadWriteLock LOCK = new ReentrantReadWriteLock();

    /** 内存缓存：小写用户名 -> PlayerAccount */
    private static final ConcurrentHashMap<String, PlayerAccount> CACHE = new ConcurrentHashMap<>();

    /** 脏标记：自上次写入后缓存发生过变更 */
    private static volatile boolean dirty = false;

    /** 写盘延迟 tick 计数器（约 5 秒 = 100 tick），避免高频写入 */
    private static int saveDebounceTicks = 0;
    private static final int SAVE_DEBOUNCE_INTERVAL = 100;

    /** 默认值：不启用会话超时 */
    private static final int DEFAULT_SESSION_TIMEOUT = 0;
    /** 默认值：登录失败锁定冷却 5 分钟 */
    private static final int DEFAULT_LOGIN_COOLDOWN = 300;

    /** 会话认证超时时间（秒），0=关闭 */
    private static int sessionTimeout = DEFAULT_SESSION_TIMEOUT;

    /** 登录失败锁定冷却时间（秒），默认 5 分钟 */
    private static int loginCooldown = DEFAULT_LOGIN_COOLDOWN;

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
     * 初始化存储路径，注册延迟写盘与服务器停止时的强制落盘。
     */
    public static void initialize() {
        DebugLogger.entering("AccountDataStorage", "initialize");
        ModPaths.ensureDir(ModPaths.accountModuleDir());
        ModPaths.ensureDir(ModPaths.userSettingsDir());
        DebugLogger.info("AccountDataStorage", "凭据文件=%s, 个人配置目录=%s",
                USERS_STORE.file().toAbsolutePath(), ModPaths.userSettingsDir().toAbsolutePath());
        if (YouzaiworldCore.logToFile) {
            LOGGER.info("账户凭据文件路径: {}", USERS_STORE.file().toAbsolutePath());
        }
        loadConfig();
        loadFromDisk();

        // 注册 tick 级延迟写盘：有脏数据且距上次写入超过阈值时落盘
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (dirty) {
                saveDebounceTicks++;
                if (saveDebounceTicks >= SAVE_DEBOUNCE_INTERVAL) {
                    saveToDisk();
                }
            }
        });

        // 服务器停止时强制落盘
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            DebugLogger.info("AccountDataStorage", "服务器停止，强制落盘 (dirty=%s)", dirty);
            if (dirty) {
                saveToDisk();
            }
        });

        DebugLogger.exiting("AccountDataStorage", "initialize");
    }

    /** 从全局配置的 {@code account_module} 分节读取会话超时 / 登录冷却 */
    private static void loadConfig() {
        DebugLogger.entering("AccountDataStorage", "loadConfig");
        ConfigSection section = GlobalSettings.section(GlobalSettings.ACCOUNT_MODULE);
        if (section.isEmpty()) {
            DebugLogger.branch("AccountDataStorage", "account_module 分节存在", false);
            saveConfig();
        } else {
            DebugLogger.branch("AccountDataStorage", "account_module 分节存在", true);
            sessionTimeout = section.getInt("session_timeout", sessionTimeout, 0, Integer.MAX_VALUE);
            loginCooldown = section.getInt("login_cooldown", loginCooldown, -1, Integer.MAX_VALUE);
        }
        DebugLogger.exiting("AccountDataStorage", "loadConfig",
                "sessionTimeout=" + sessionTimeout + ", loginCooldown=" + loginCooldown);
    }

    /**
     * 重置会话超时 / 登录冷却为默认值并写入全局配置的 {@code account_module} 分节。
     * <p>供 {@link top.csituka.youzaiworldcore.config.DefaultSettingsWriter} 生成默认配置时调用。</p>
     */
    public static void writeDefaultSettings() {
        sessionTimeout = DEFAULT_SESSION_TIMEOUT;
        loginCooldown = DEFAULT_LOGIN_COOLDOWN;
        saveConfig();
    }

    /** 把会话超时 / 登录冷却写回全局配置的 {@code account_module} 分节 */
    private static void saveConfig() {
        DebugLogger.entering("AccountDataStorage", "saveConfig");
        ConfigSection section = GlobalSettings.section(GlobalSettings.ACCOUNT_MODULE);
        section.set("session_timeout", sessionTimeout);
        section.set("login_cooldown", loginCooldown);
        GlobalSettings.save();
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

            boolean created = USERS_STORE.loadOrCreateDefaults();
            if (created && YouzaiworldCore.logToFile) {
                LOGGER.info("账户凭据文件不存在，已创建默认空表: {}", USERS_STORE.file().toAbsolutePath());
            }
            ConfigSection section = USERS_STORE.section(GlobalSettings.ACCOUNT_MODULE);
            JsonObject users = section.getObject(KEY_REGISTERED_USERS);
            if (users == null) {
                DebugLogger.branch("AccountDataStorage", "registered_users 存在", false);
                writeUsersFile(); // 补齐缺失的空表
                DebugLogger.exiting("AccountDataStorage", "loadFromDisk", "table missing, created empty");
                return;
            }
            DebugLogger.branch("AccountDataStorage", "registered_users 存在", true);

            java.lang.reflect.Type type = new TypeToken<Map<String, PlayerAccount>>() {}.getType();
            Map<String, PlayerAccount> loaded;
            try {
                loaded = PlayerAccount.GSON.fromJson(users, type);
            } catch (RuntimeException e) {
                section.fail(KEY_REGISTERED_USERS, "账户凭据解析失败：" + e.getMessage());
                return; // 不可达
            }
            if (loaded != null) {
                CACHE.putAll(loaded);
            }
            DebugLogger.info("AccountDataStorage", "loaded %d accounts", CACHE.size());
            if (YouzaiworldCore.logToFile) {
                LOGGER.info("已加载 {} 个账户", CACHE.size());
            }
        } finally {
            LOCK.writeLock().unlock();
            DebugLogger.exiting("AccountDataStorage", "loadFromDisk");
        }
    }

    /**
     * 标记缓存已脏，通知延迟写盘机制。
     * <p>
     * 替代原有的每次操作后立即 saveToDisk() 调用。
     * 实际写盘由 tick 级防抖器（{@value #SAVE_DEBOUNCE_INTERVAL} tick = ~5 秒后）或服务器停止时触发。
     * </p>
     */
    private static void markDirty() {
        dirty = true;
        saveDebounceTicks = 0; // 重置倒计时，每次变更重新计时
    }

    /**
     * 强制立即落盘（无视脏标记与防抖倒计时）。
     * 用于 reload、服务器停止等需要立即持久化的场景。
     */
    public static void flushToDisk() {
        DebugLogger.entering("AccountDataStorage", "flushToDisk");
        saveToDisk();
    }

    /**
     * 将缓存保存到磁盘。
     * 由 markDirty/防抖/停止钩子调用，不建议直接调用。
     */
    public static void saveToDisk() {
        if (!dirty) {
            DebugLogger.info("AccountDataStorage", "saveToDisk 跳过（无脏数据）");
            return;
        }
        DebugLogger.entering("AccountDataStorage", "saveToDisk");
        LOCK.writeLock().lock();
        DebugLogger.info("AccountDataStorage", "writeLock acquired for saveToDisk");
        try {
            writeUsersFile();
            dirty = false;
            saveDebounceTicks = 0;
        } finally {
            LOCK.writeLock().unlock();
            DebugLogger.exiting("AccountDataStorage", "saveToDisk");
        }
    }

    /**
     * 无条件把缓存写进凭据文件（忽略脏标记）。
     * <p>调用方需自行持有写锁。</p>
     */
    private static void writeUsersFile() {
        ConfigSection section = USERS_STORE.section(GlobalSettings.ACCOUNT_MODULE);
        section.set(KEY_REGISTERED_USERS, PlayerAccount.GSON.toJsonTree(CACHE));
        USERS_STORE.save();
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
                markDirty();
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
            markDirty();
            createUserSettings(account);
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
            markDirty();
        } finally {
            LOCK.writeLock().unlock();
            DebugLogger.exiting("AccountDataStorage", "update");
        }
    }

    /**
     * 删除玩家账户（同时删除该玩家的个人配置文件）
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
                markDirty();
                deleteUserSettings(removed);
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

    // ===== 个人配置建档 / 删档 =====

    /** 注册成功后为该玩家建立个人配置文件 {@code user_settings/<UUID>.json} */
    private static void createUserSettings(PlayerAccount account) {
        UUID uuid = parseUuid(account);
        if (uuid == null) {
            LOGGER.warn("账户 {} 缺少合法 UUID，跳过创建个人配置文件", account.username);
            return;
        }
        UserSettings.create(uuid);
    }

    /** 注销 / 删除账户后移除该玩家的个人配置文件 */
    private static void deleteUserSettings(PlayerAccount account) {
        UUID uuid = parseUuid(account);
        if (uuid == null) {
            LOGGER.warn("账户 {} 缺少合法 UUID，跳过删除个人配置文件", account.username);
            return;
        }
        UserSettings.delete(uuid);
    }

    /** 从账户记录里解析 UUID，非法时返回 null */
    private static UUID parseUuid(PlayerAccount account) {
        if (account == null || account.uuid == null || account.uuid.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(account.uuid);
        } catch (IllegalArgumentException e) {
            return null;
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
        UserSettings.invalidateAll();
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
     * 通过玩家名称（精确大小写匹配）获取。
     * <p>优化：先走 O(1) 的 {@link #get} 查询，再校验大小写匹配。</p>
     */
    public static PlayerAccount getByExactName(String username) {
        DebugLogger.entering("AccountDataStorage", "getByExactName", "username=" + username);
        PlayerAccount acc = get(username); // O(1) 通过小写键查找
        if (acc != null && acc.username != null && acc.username.equals(username)) {
            DebugLogger.exiting("AccountDataStorage", "getByExactName", "found");
            return acc;
        }
        DebugLogger.exiting("AccountDataStorage", "getByExactName", "not found");
        return null;
    }
}
